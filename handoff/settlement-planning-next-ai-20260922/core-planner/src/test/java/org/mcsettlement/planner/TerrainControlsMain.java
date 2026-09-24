package org.mcsettlement.planner;

import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.mcsettlement.planner.simulation.*;
import org.mcsettlement.planner.simulation.SimulatedVoxelWorld.VoxelType;
import org.mcsettlement.planner.regression.TerrainAudit;

/** Executable regression tests. Uses real generator, scanner, planner and Gson. */
public final class TerrainControlsMain {
    private static final List<Map<String,Object>> checks=new ArrayList<>();
    private static final List<Map<String,Object>> effects=new ArrayList<>();
    private interface Test { void run() throws Exception; }
    private static void check(String name,Test test){
        try{test.run();checks.add(Map.of("name",name,"passed",true));System.out.println("PASS "+name);}
        catch(Throwable e){checks.add(Map.of("name",name,"passed",false,"error",e.toString()));System.out.println("FAIL "+name+" "+e);}
    }
    private static void require(boolean b,String message){if(!b)throw new AssertionError(message);}
    private static void rejects(Test test)throws Exception{
        try{test.run();}catch(IllegalArgumentException e){return;}throw new AssertionError("Expected argument rejection");
    }
    private static TerrainParameters params(String type,String key,String value){return TerrainParameters.fromOverrides(type,Map.of(key,value));}
    private static String hash(SimulatedVoxelWorld world){return SimulatedSettlementPipeline.hashWorld(world);}
    private static int[] heights(String type,TerrainParameters p){
        int n=192;int[] h=new int[n*n];for(int z=0;z<n;z++)for(int x=0;x<n;x++)h[z*n+x]=TerrainBlockGenerator.sampleSurfaceY(x,z,n,n,58,24,type,42,p);return h;
    }
    private static double meanStep(int[] h){long sum=0,n=0;int w=192;
        for(int z=0;z<w;z++)for(int x=0;x<w;x++){int i=z*w+x;if(x+1<w){sum+=Math.abs(h[i]-h[i+1]);n++;}if(z+1<w){sum+=Math.abs(h[i]-h[i+w]);n++;}}return sum/(double)n;}
    private static int countTrees(SimulatedVoxelWorld w){int count=0;
        for(int x=0;x<w.getSizeX();x++)for(int z=0;z<w.getSizeZ();z++){
            boolean found=false;for(int y=w.getMinY();y<w.getMinY()+w.getSizeY();y++)if(w.getBlock(x,y,z)==VoxelType.OAK_LOG){found=true;break;}if(found)count++;
        }return count;}
    public static void main(String[] args)throws Exception{
        Path out=Paths.get(args.length>0?args[0]:"build/terrain-controls");Files.createDirectories(out);
        // Golden hashes were captured from the unmodified input generator, not regenerated in the test.
        for(String line:Files.readAllLines(Paths.get("core-planner/src/test/resources/terrain-v041-golden.csv"))){
            String[] t=line.split(",");check("legacy-golden/"+String.join("/",Arrays.copyOf(t,6)),()->{
                int w=Integer.parseInt(t[1]),d=Integer.parseInt(t[2]),b=Integer.parseInt(t[3]),r=Integer.parseInt(t[4]);long seed=Long.parseLong(t[5]);
                var oldApi=TerrainBlockGenerator.generateWorld(w,d,b,r,t[0],seed);
                require(hash(oldApi).equals(t[6]),"Original defaults changed");
                var explicit=TerrainBlockGenerator.generateWorld(w,d,b,r,t[0],seed,TerrainParameters.defaults(t[0]));
                require(hash(explicit).equals(t[6]),"Explicit defaults changed");
            });
        }
        check("hillside-entry-remains-equivalent",()->require(hash(TerrainBlockGenerator.generateHillsideWorld(64,96,58,24,42))
                .equals(hash(TerrainBlockGenerator.generateWorld(64,96,58,24,"rolling_hills",42))),"Legacy hillside entry diverged"));
        check("hillside-controlled-overload",()->{
            var p=params("rolling_hills","horizontalScale","1.5");
            require(hash(TerrainBlockGenerator.generateHillsideWorld(64,96,58,24,42,p))
                    .equals(hash(TerrainBlockGenerator.generateWorld(64,96,58,24,"rolling_hills",42,p))),"Controlled hillside entry diverged");
        });
        check("null-override-entry-rejected",()->{
            var values=new HashMap<String,String>();values.put("treeDensity",null);
            rejects(()->TerrainParameters.fromOverrides("rolling_hills",values));
        });
        JsonObject schema;
        try(var stream=TerrainControlsMain.class.getResourceAsStream("/terrain-controls.json")){
            require(stream!=null,"Missing parameter schema");schema=JsonParser.parseString(new String(stream.readAllBytes(),StandardCharsets.UTF_8)).getAsJsonObject();
        }
        Set<String> schemaKeys=new HashSet<>();
        for(var item:schema.getAsJsonArray("controls")){
            var s=item.getAsJsonObject();String key=s.get("key").getAsString();schemaKeys.add(key);
            boolean integral=s.get("integer").getAsBoolean();String type=s.getAsJsonArray("appliesTo").get(0).getAsString();
            check("schema-default-and-bounds/"+key,()->{
                for(String kind:new String[]{"rolling_hills","mountain","valley","plateau"}){
                    double documented=s.get("default").isJsonObject()?s.getAsJsonObject("default").get(kind).getAsDouble():s.get("default").getAsDouble();
                    double actual=((Number)TerrainParameters.class.getMethod(key).invoke(TerrainParameters.defaults(kind))).doubleValue();
                    require(documented==actual,"Schema default drift");
                }
                String lo=s.get("min").getAsString(),hi=s.get("max").getAsString();params(type,key,lo);params(type,key,hi);
                String under=integral?Integer.toString(s.get("min").getAsInt()-1):Double.toString(s.get("min").getAsDouble()-.001);
                String over=integral?Integer.toString(s.get("max").getAsInt()+1):Double.toString(s.get("max").getAsDouble()+.001);
                rejects(()->params(type,key,under));rejects(()->params(type,key,over));
                rejects(()->params(type,key,"NaN"));rejects(()->params(type,key,"Infinity"));rejects(()->params(type,key,"-Infinity"));
                if(integral)rejects(()->params(type,key,"4.5"));
                require(!s.get("low").getAsString().isBlank()&&!s.get("high").getAsString().isBlank(),"Missing low/high explanation");
            });
            check("low-high-control-changes-world/"+key,()->{
                String lo=s.get("min").getAsString(),hi=s.get("max").getAsString();
                var low=TerrainBlockGenerator.generateWorld(192,192,58,24,type,42,params(type,key,lo));
                var high=TerrainBlockGenerator.generateWorld(192,192,58,24,type,42,params(type,key,hi));
                require(!hash(low).equals(hash(high)),"Control has no effect in its applicable terrain");
                int[] lh=heights(type,params(type,key,lo)),hh=heights(type,params(type,key,hi));
                effects.add(Map.of("parameter",key,"terrainType",type,"low",lo,"high",hi,"lowTrees",countTrees(low),"highTrees",countTrees(high),
                        "lowMeanNeighborStep",meanStep(lh),"highMeanNeighborStep",meanStep(hh),"lowHash",hash(low),"highHash",hash(high)));
            });
        }
        check("schema-keys-match-java",()->require(schemaKeys.equals(TerrainParameters.KEYS),"Schema/Java keys differ"));
        check("unknown-key-rejected",()->rejects(()->params("valley","notAParameter","1")));
        for(String type:new String[]{"rolling_hills","mountain","valley","plateau"}){
            check("sampler-equals-real-ground/"+type,()->{
                var p=TerrainParameters.fromOverrides(type,Map.of("horizontalScale",".7","detailStrength","1.8","warpStrength","1.4","waterLevelRatio",".5","treeSpacing","6"));
                var r=SimulatedSettlementPipeline.generateTerrain(48,72,58,24,type,-731,p);
                for(int z=0;z<72;z++)for(int x=0;x<48;x++)require(r.heightfield.getSurfaceY(x,z)==TerrainBlockGenerator.sampleSurfaceY(x,z,48,72,58,24,type,-731,p),"Sample differs from actual voxel ground");
                require(r.plan==null&&r.worldAfter==null&&r.constructionEdits==0,"Terrain-only path invoked downstream work");
                require(r.originalTerrainHash.equals(hash(TerrainBlockGenerator.generateWorld(48,72,58,24,type,-731,p))),"Repeat diverged");
            });
        }
        check("tree-controls-do-not-change-heights-or-water",()->{
            var a=SimulatedSettlementPipeline.generateTerrain(128,128,58,24,"valley",42,params("valley","treeDensity","0"));
            var b=SimulatedSettlementPipeline.generateTerrain(128,128,58,24,"valley",42,TerrainParameters.fromOverrides("valley",Map.of("treeDensity","2","treeSpacing","2")));
            for(int z=0;z<128;z++)for(int x=0;x<128;x++){
                require(a.heightfield.getSurfaceY(x,z)==b.heightfield.getSurfaceY(x,z),"Tree controls changed height");
                require(a.heightfield.getWaterY(x,z)==b.heightfield.getWaterY(x,z),"Tree controls changed water");
            }
            require(countTrees(a.worldBefore)==0,"Density 0 did not disable trees");
        });
        check("water-coverage-monotonic-and-height-independent",()->{
            var low=SimulatedSettlementPipeline.generateTerrain(192,192,58,24,"valley",42,params("valley","waterLevelRatio","0"));
            var high=SimulatedSettlementPipeline.generateTerrain(192,192,58,24,"valley",42,params("valley","waterLevelRatio",".6"));
            int wet=0;for(int z=0;z<192;z++)for(int x=0;x<192;x++){
                require(low.heightfield.getSurfaceY(x,z)==high.heightfield.getSurfaceY(x,z),"Water changes ground");
                require(low.heightfield.getWaterY(x,z)<0,"Water at ratio0");if(high.heightfield.getWaterY(x,z)>=0)wet++;
            }require(wet>0,"No wet cells at increased water level");
        });
        check("ridge-narrowing-lowers-flanks-not-peaks",()->{
            int[] a=heights("mountain",params("mountain","ridgeSharpness",".5")),b=heights("mountain",params("mountain","ridgeSharpness","3"));
            for(int i=0;i<a.length;i++)require(b[i]<=a[i],"Narrowing unexpectedly raised a surface");
        });
        check("valley-widening-lowers-surrounding-ground",()->{
            int[] a=heights("valley",params("valley","valleyWidth",".5")),b=heights("valley",params("valley","valleyWidth","2"));
            for(int i=0;i<a.length;i++)require(b[i]<=a[i],"Widening unexpectedly raised ground");
        });
        check("terrace-level-count-with-detail-disabled",()->{
            int[] a=heights("plateau",TerrainParameters.fromOverrides("plateau",Map.of("detailStrength","0","terraceLevels","2")));
            int[] b=heights("plateau",TerrainParameters.fromOverrides("plateau",Map.of("detailStrength","0","terraceLevels","12")));
            require(Arrays.stream(a).distinct().count()<=3,"Unexpected extra terrace levels");
            require(Arrays.stream(b).distinct().count()>Arrays.stream(a).distinct().count(),"No extra levels");
        });
        check("inapplicable-controls-are-inert-not-other-type-switches",()->{
            var d=TerrainParameters.defaults("rolling_hills");var p=TerrainParameters.fromOverrides("rolling_hills",Map.of("ridgeSharpness","3","valleyWidth","2","terraceLevels","12"));
            require(hash(TerrainBlockGenerator.generateWorld(96,96,58,24,"rolling_hills",42,d))
                    .equals(hash(TerrainBlockGenerator.generateWorld(96,96,58,24,"rolling_hills",42,p))),"Inactive controls changed terrain");
        });
        check("sample-dimension-bounds-and-null-validation",()->{
            var p=TerrainParameters.defaults("rolling_hills");
            rejects(()->TerrainBlockGenerator.sampleSurfaceY(-1,0,64,64,58,24,"rolling_hills",42,p));
            rejects(()->TerrainBlockGenerator.sampleSurfaceY(0,64,64,64,58,24,"rolling_hills",42,p));
            rejects(()->TerrainBlockGenerator.generateWorld(513,64,58,24,"rolling_hills",42,p));
            rejects(()->TerrainBlockGenerator.generateWorld(64,64,58,24,"rolling_hills",42,null));
        });
        check("custom-parameters-reach-real-planner-and-construction",()->{
            var p=TerrainParameters.fromOverrides("rolling_hills",Map.of("horizontalScale","1.5","waterLevelRatio","0","treeDensity",".5"));
            var budget=new SettlementPlanner.SearchBudget();
            var r=SimulatedSettlementPipeline.run(128,128,58,12,"rolling_hills",42,44,3,8,false,budget,"classic","square_cabin",p);
            require(r.plan.plots.size()==3&&r.plan.status.equals("COMPLETE"),"Fixture incomplete");
            require(r.terrainParameters.equals(p),"Parameters not echoed");
            var req=new SettlementPlanner.PlanRequest();req.seed=44;req.targetPlots=3;req.roadWidth=3;req.roadDirections=8;
            var audit=TerrainAudit.audit(r.heightfield,req,r.plan);require(audit.violations()==0,"Audit violations "+audit.violations());
            require(r.constructionEdits>0,"No construction was executed");
            var terrain=SimulatedSettlementPipeline.generateTerrain(128,128,58,12,"rolling_hills",42,p);
            require(terrain.originalTerrainHash.equals(r.originalTerrainHash),"Planner generated different original terrain");
        });
        boolean passed=checks.stream().allMatch(c->Boolean.TRUE.equals(c.get("passed")));
        Files.writeString(out.resolve("results.json"),new GsonBuilder().setPrettyPrinting().create().toJson(Map.of("passed",passed,"count",checks.size(),"checks",checks,"effects",effects)));
        if(!passed)throw new AssertionError("One or more terrain controls tests failed");
        System.out.println("RESULT "+checks.size()+" terrain controls tests passed");
    }
}
