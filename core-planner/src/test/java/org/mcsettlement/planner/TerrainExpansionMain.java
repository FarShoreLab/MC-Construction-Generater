package org.mcsettlement.planner;

import java.util.*;
import org.mcsettlement.planner.ir.PlanningIR;
import org.mcsettlement.planner.civil.PlanConstruction;
import org.mcsettlement.planner.simulation.*;
import org.mcsettlement.planner.simulation.SimulatedVoxelWorld.VoxelType;
import org.mcsettlement.planner.terrain.HeightfieldMap;

/** Real terrain materials, deterministic sampling and bounded field earthworks. */
public final class TerrainExpansionMain {
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private static int[] snowHighland(long seed,double scale){
        var p=TerrainParameters.fromOverrides("snow_peak",Map.of("horizontalScale",Double.toString(scale)));
        int width=128,area=0,square=0;int[][] interior=new int[width+1][width+1];
        for(int z=0;z<width;z++)for(int x=0;x<width;x++){
            int y=TerrainBlockGenerator.sampleSurfaceY(x,z,width,width,58,128,"snow_peak",seed,p);
            if(y<58+128*.55)continue;
            area++;interior[z+1][x+1]=1+Math.min(interior[z][x],Math.min(interior[z][x+1],interior[z+1][x]));
            square=Math.max(square,interior[z+1][x+1]);
        }
        return new int[]{area,square};
    }
    public static void main(String[] args){
        for(long seed:new long[]{42,137,-731}){
            int[] normal=snowHighland(seed,1),wide=snowHighland(seed,1.8);
            check(normal[0]>128*128*.12&&normal[1]>=24,"Snow massif remains a thin ridge: "+Arrays.toString(normal));
            check(wide[0]>normal[0]*1.3,"Horizontal scale does not widen snow massif");
            System.out.println("PASS broad snow massif seed="+seed+" highland area="+normal[0]+" interior width="+normal[1]+" widened area="+wide[0]);
        }
        for(var type:TerrainBlockGenerator.TerrainType.values()){
            var p=TerrainParameters.fromOverrides(type.id,Map.of("waterLevelRatio","0"));
            var r=SimulatedSettlementPipeline.generateTerrain(48,64,75,128,type.id,42,p);
            int max=0;
            for(int z=0;z<64;z++)for(int x=0;x<48;x++){
                int y=r.heightfield.getSurfaceY(x,z);max=Math.max(max,y);
                check(y>=75&&y<=203&&r.heightfield.getWaterY(x,z)<0,"Relief128 bounds/no-water failed: "+type);
                check(y==TerrainBlockGenerator.sampleSurfaceY(x,z,48,64,75,128,type.id,42,p),"High terrain sampler mismatch");
            }
            check(r.worldBefore.getMinY()+r.worldBefore.getSizeY()-1>=203+27,"Missing construction headroom");
            check(max>111,"Terrain still clipped to old height limit: "+type);
        }
        try{TerrainBlockGenerator.generateWorld(32,32,58,129,"snow_peak",42);throw new AssertionError("Relief129 accepted");}catch(IllegalArgumentException expected){}
        try{TerrainBlockGenerator.waterLevel(58,129,TerrainParameters.defaults("snow_peak"));throw new AssertionError("Water level accepted relief129");}catch(IllegalArgumentException expected){}
        System.out.println("PASS relief128 across every terrain type and relief129 rejection");
        Set<String> hashes=new HashSet<>();
        for(String type:List.of("desert","canyon","basin","cliff","mountain_range","snow_peak")){
            for(long seed:new long[]{42,137}){
                var p=TerrainParameters.defaults(type);
                var r=SimulatedSettlementPipeline.generateTerrain(128,96,58,36,type,seed,p);
                check(hashes.add(r.originalTerrainHash),"Duplicate terrain: "+type);
                check(r.originalTerrainHash.equals(SimulatedSettlementPipeline.hashWorld(TerrainBlockGenerator.generateWorld(128,96,58,36,type,seed,p))),"Not deterministic");
                int special=0,min=999,max=0;
                for(int x=0;x<128;x++)for(int z=0;z<96;z++){
                    int y=r.heightfield.getSurfaceY(x,z);min=Math.min(min,y);max=Math.max(max,y);
                    check(y==TerrainBlockGenerator.sampleSurfaceY(x,z,128,96,58,36,type,seed,p),"Sampler/scanner mismatch");
                    var b=r.worldBefore.getBlock(x,y,z);
                    if(b==VoxelType.SAND||b==VoxelType.TERRACOTTA||b==VoxelType.SNOW_BLOCK)special++;
                    if(type.equals("desert"))check(b==VoxelType.SAND&&r.heightfield.getWaterY(x,z)<0,"Desert substrate/water");
                }
                check(max-min>=5,"Missing relief "+type);
                if(Set.of("desert","canyon","snow_peak").contains(type))check(special>0,"Missing biome material: "+type+" seed "+seed);
            }
            System.out.println("PASS terrain "+type);
        }
        var map=new HeightfieldMap(0,0,96,96);
        for(int x=0;x<96;x++)for(int z=0;z<96;z++)map.setSurfaceY(x,z,58+x/4+z/16);
        map.computeSlopes();
        var req=new SettlementPlanner.PlanRequest();req.expert=new ExpertSettings();
        var ir=new PlanningIR();ir.sitePlanning=new PlanningIR.SitePlanning();
        var plot=new PlanningIR.Plot();plot.origin2D=new int[]{44,44};plot.builder.footprintSize=new int[]{5,5};ir.plots.add(plot);
        TerrainLandUsePlanner.add(map,req,ir);
        check(ir.landUses.stream().filter(a->a.type.equals("farmland")&&!a.terraced).count()==2,"Missing multiple surface fields");
        check(ir.landUses.stream().anyMatch(a->a.terraced),"Missing terraces");
        check(ir.groundColumns.stream().anyMatch(c->c.terracedFarmland&&c.targetY<c.originalY),"No terrace cut");
        check(ir.groundColumns.stream().anyMatch(c->c.terracedFarmland&&c.targetY>c.originalY),"No terrace fill");
        Set<Long> cells=new HashSet<>();int edits=0;
        for(var c:ir.groundColumns){
            check(cells.add(RoadGeometry.key(c.x,c.z)),"Overlapping land uses");
            check(c.originalY==map.getSurfaceY(c.x,c.z),"Wrong original surface");
            check(c.terracedFarmland?Math.abs(c.targetY-c.originalY)<=1:c.targetY==c.originalY,"Unbounded field earthwork");
            edits+=PlanConstruction.columnEditCount(c);
        }
        check(edits==ir.search.constructionEdits,"Wrong field edit budget");
        for(var a:ir.landUses){
            int minX=999,minZ=999,maxX=-1,maxZ=-1;
            for(int[] c:a.cells){minX=Math.min(minX,c[0]);maxX=Math.max(maxX,c[0]);minZ=Math.min(minZ,c[1]);maxZ=Math.max(maxZ,c[1]);}
            check(a.type.equals("farm_hut")||a.cells.size()<(maxX-minX+1)*(maxZ-minZ+1),"Rectangular field");
        }
        System.out.println("PASS irregular surface fields and terrace cut/fill; cells="+ir.sitePlanning.farmlandCells);
        var expert=new ExpertSettings();expert.sitePreference="clearings";
        var canyon=SimulatedSettlementPipeline.run(128,128,58,24,"canyon",42,42,7,16,false,
                new SettlementPlanner.SearchBudget(),"estates",null,TerrainParameters.defaults("canyon"),expert,"dirt_path");
        check("COMPLETE".equals(canyon.plan.status),"Default canyon failed: "+canyon.plan.sitePlanning.reasons);
        check(canyon.plan.groundColumns.stream().anyMatch(c->c.suspensionBridge),"Default canyon has no suspension crossing");
        check(canyon.plan.groundColumns.stream().filter(c->"bridge_anchor".equals(c.kind)).count()>=4,"Missing real canyon anchors");
        check(canyon.plan.sitePlanning.farmlandCells>96,"Missing expanded canyon fields");
        System.out.println("PASS default canyon: seven connected buildings, suspension crossings and farmland");
    }
}
