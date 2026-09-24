package org.mcsettlement.planner.simulation;

import com.google.gson.Gson;
import org.mcsettlement.planner.SettlementPlanner.SearchBudget;
import org.mcsettlement.planner.ir.PlanningIR;
import org.mcsettlement.planner.preset.*;
import org.mcsettlement.planner.ir.PlanningIR.*;
import org.mcsettlement.planner.simulation.SimulatedSettlementPipeline.PipelineResult;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.mcsettlement.planner.RoadGeometry.key;

/** Local browser adapter. Compact heightfield + exposed above-ground voxels, never an unbounded voxel dump. */
public final class SimulationApiRunner {
    public static final int MAX_RESPONSE_BYTES=32*1024*1024, MAX_EXTRAS_PER_LAYER=400000;
    private static final Gson GSON=new Gson();
    private SimulationApiRunner() {}
    public static void main(String[] args)throws IOException{
        Request request=Request.parse(args);PrintStream out=System.out;
        try{System.setOut(new PrintStream(System.err,true));
            PipelineResult result=SimulatedSettlementPipeline.run(request.width,request.depth,request.baseElevation,request.relief,request.terrainType,request.terrainSeed,request.planSeed,request.targetPlots,request.roadDirections,request.diagonalBuildings,request.budget,request.presetPalette,request.singlePresetId);
            byte[] data=encode(new Payload(result));out.write(data);
        }finally{System.setOut(out);}
    }
    private static final class LimitedBuffer extends ByteArrayOutputStream{
        @Override public synchronized void write(int value){if(count>=MAX_RESPONSE_BYTES)throw new IllegalArgumentException("RESPONSE_BYTE_LIMIT");super.write(value);}
        @Override public synchronized void write(byte[] b,int off,int len){if(count+len>MAX_RESPONSE_BYTES)throw new IllegalArgumentException("RESPONSE_BYTE_LIMIT");super.write(b,off,len);}
    }
    public static byte[] encode(Object payload)throws IOException{LimitedBuffer bytes=new LimitedBuffer();try(Writer writer=new OutputStreamWriter(bytes,StandardCharsets.UTF_8)){GSON.toJson(payload,writer);}return bytes.toByteArray();}
    private static final class Request{
        int width=128,depth=128,baseElevation=58,relief=24,targetPlots=7,roadDirections=8;
        long terrainSeed=42,planSeed=42;String terrainType="rolling_hills",presetPalette="classic",singlePresetId="square_cabin";boolean diagonalBuildings;
        SearchBudget budget=new SearchBudget();
        static Request parse(String[] args){Request r=new Request();
            for(String arg:args){if(!arg.startsWith("--")||!arg.contains("="))throw new IllegalArgumentException("INVALID_ARGUMENT");String[] pair=arg.substring(2).split("=",2);String v=pair[1];
                switch(pair[0]){case "width"->r.width=Integer.parseInt(v);case "depth"->r.depth=Integer.parseInt(v);case "baseElevation"->r.baseElevation=Integer.parseInt(v);case "relief"->r.relief=Integer.parseInt(v);
                    case "targetPlots"->r.targetPlots=Integer.parseInt(v);case "roadDirections"->r.roadDirections=Integer.parseInt(v);case "terrainType"->r.terrainType=v;
                    case "presetPalette"->r.presetPalette=v;case "singlePresetId"->r.singlePresetId=v;
                    case "terrainSeed","seed"->r.terrainSeed=Long.parseLong(v);case "planSeed"->r.planSeed=Long.parseLong(v);
                    case "diagonalBuildings"->{if(!Set.of("true","false").contains(v))throw new IllegalArgumentException("INVALID_BOOLEAN");r.diagonalBuildings=Boolean.parseBoolean(v);}
                    case "candidateChecks"->r.budget.candidateChecks=Integer.parseInt(v);case "pathExpanded"->r.budget.pathExpanded=Integer.parseInt(v);case "attempts"->r.budget.attempts=Integer.parseInt(v);
                    case "pathStates"->r.budget.pathStates=Integer.parseInt(v);case "gradeRelaxations"->r.budget.gradeRelaxations=Integer.parseInt(v);case "groundColumns"->r.budget.groundColumns=Integer.parseInt(v);case "constructionEdits"->r.budget.constructionEdits=Integer.parseInt(v);
                    default->throw new IllegalArgumentException("UNKNOWN_ARGUMENT: "+pair[0]);}}
            if(r.width<32||r.width>512||r.depth<32||r.depth>512||r.baseElevation<50||r.baseElevation>75||r.relief<4||r.relief>36||r.targetPlots<1||r.targetPlots>12||(r.roadDirections!=8&&r.roadDirections!=12))throw new IllegalArgumentException("INVALID_BROWSER_PARAMETERS");
            if(r.budget.candidateChecks<1||r.budget.candidateChecks>200000||r.budget.pathExpanded<1||r.budget.pathExpanded>2000000||r.budget.attempts<1||r.budget.attempts>8||r.budget.pathStates<1||r.budget.pathStates>200000||r.budget.gradeRelaxations<1||r.budget.gradeRelaxations>10000000||r.budget.groundColumns<1||r.budget.groundColumns>100000||r.budget.constructionEdits<1||r.budget.constructionEdits>1000000)throw new IllegalArgumentException("INVALID_BROWSER_BUDGET");
            if(!PresetPalette.NAMES.contains(r.presetPalette)||!Arrays.asList(BuildingPresetRegistry.BUILTIN_PRESET_IDS).contains(r.singlePresetId))throw new IllegalArgumentException("INVALID_PRESET_SELECTION");
            TerrainBlockGenerator.TerrainType.from(r.terrainType);return r;
        }
    }
    private static final class Payload{
        final boolean ok=true;final String schemaVersion=PlanningIR.SCHEMA_VERSION;final List<PresetInfo> presets=catalog();final Config config;final Metrics metrics;final Layer original,constructed;final PlanPreview plan;
        Payload(PipelineResult r){config=new Config(r);metrics=new Metrics(r);original=new Layer(r,false);constructed=new Layer(r,true);plan=new PlanPreview(r.plan,r.planHash);}
    }
    private record Config(int width,int depth,int baseElevation,int relief,String terrainType,long terrainSeed,long planSeed,int targetPlots,int roadDirections,boolean diagonalBuildings,String presetPalette,String singlePresetId,String originalTerrainHash){
        Config(PipelineResult r){this(r.width,r.depth,r.baseElevation,r.relief,r.terrainType,r.terrainSeed,r.planSeed,r.targetPlots,r.roadDirections,r.diagonalBuildings,r.presetPalette,r.singlePresetId,r.originalTerrainHash);}}
    private static final class Metrics{
        final String status,planHash;final int plotCount,roadColumnCount,roadEdgeCount,clearedBlocks,cutBlocks,fillBlocks,buildingBlocks,stairBlocks,constructionEdits,minPlatform,maxPlatform,diagonalPlots;
        final SearchStats search;final NetworkMetrics network;final String networkAlgorithm;final int responseByteLimit=MAX_RESPONSE_BYTES,extraVoxelLimit=MAX_EXTRAS_PER_LAYER;
        Metrics(PipelineResult r){status=r.plan.status;planHash=r.planHash;plotCount=r.plan.plots.size();roadColumnCount=(int)r.plan.groundColumns.stream().filter(c->"road".equals(c.kind)).count();roadEdgeCount=r.plan.transportNetwork.edges.size();clearedBlocks=r.clearedBlocks;cutBlocks=r.cutBlocks;fillBlocks=r.fillBlocks;buildingBlocks=r.buildingBlocks;stairBlocks=r.stairBlocks;constructionEdits=r.constructionEdits;search=r.plan.search;network=r.plan.transportNetwork.metrics;networkAlgorithm=r.plan.transportNetwork.algorithm;minPlatform=r.plan.plots.stream().mapToInt(p->p.elevation.baseElevation).min().orElse(0);maxPlatform=r.plan.plots.stream().mapToInt(p->p.elevation.baseElevation).max().orElse(0);diagonalPlots=(int)r.plan.plots.stream().filter(p->p.builder.diagonal45).count();}}
    /** Row-major arrays (z*width+x). Heights are top FULL support blocks; stair voxels sit above. */
    private static final class Layer{
        final int minY,sizeX,sizeY,sizeZ;final int[] heights,materials,waters;final List<int[]> voxels=new ArrayList<>();
        Layer(PipelineResult r,boolean after){SimulatedVoxelWorld world=after?r.worldAfter:r.worldBefore;minY=world.getMinY();sizeX=world.getSizeX();sizeY=world.getSizeY();sizeZ=world.getSizeZ();
            heights=new int[sizeX*sizeZ];materials=new int[heights.length];waters=new int[heights.length];Arrays.fill(waters,-1);
            Map<Long,GroundColumn> columns=new HashMap<>();if(after)for(GroundColumn c:r.plan.groundColumns)columns.put(key(c.x,c.z),c);
            for(int z=0;z<sizeZ;z++)for(int x=0;x<sizeX;x++){int index=z*sizeX+x;GroundColumn c=columns.get(key(x,z));
                int surface=c==null?r.heightfield.getSurfaceY(x,z):c.targetY-("stair".equals(c.structure)?1:0);
                heights[index]=surface;materials[index]=world.getBlock(x,surface,z).id;
                for(int y=surface+1;y<minY+sizeY;y++){var b=world.getBlock(x,y,z);if(b==SimulatedVoxelWorld.VoxelType.AIR)continue;
                    if(b==SimulatedVoxelWorld.VoxelType.WATER){waters[index]=y;continue;}
                    if(!exposed(world,x,y,z))continue;
                    int facing=c!=null&&y==c.targetY&&"stair".equals(c.structure)?switch(c.facing){case "EAST"->1;case "SOUTH"->2;case "WEST"->3;default->0;}:0;
                    if(voxels.size()>=MAX_EXTRAS_PER_LAYER)throw new IllegalArgumentException("EXTRA_VOXEL_LIMIT_NO_TRUNCATION");
                    voxels.add(new int[]{x,y-minY,z,b.id,facing});}
            }
        }
    }
    private record PresetInfo(String id,String name,String shape,String sizeTier,int width,int depth,int height,int area,List<String> mask) {}
    private static List<PresetInfo> catalog(){List<PresetInfo> out=new ArrayList<>();for(var p:BuildingPresetRegistry.getInstance().getAllPresets()){
        List<String> mask=new ArrayList<>();for(int z=0;z<p.sizeZ;z++){StringBuilder row=new StringBuilder();for(int x=0;x<p.sizeX;x++)row.append(p.occupies(x,z)?'#':'.');mask.add(row.toString());}
        out.add(new PresetInfo(p.id,p.name,p.footprintShape,p.sizeTier,p.sizeX,p.sizeZ,p.sizeY,p.footprintArea(),mask));}return out;}
    private static boolean exposed(SimulatedVoxelWorld w,int x,int y,int z){for(int[] d:SIDES){var b=w.getBlock(x+d[0],y+d[1],z+d[2]);if(b==SimulatedVoxelWorld.VoxelType.AIR||b==SimulatedVoxelWorld.VoxelType.WATER||b==SimulatedVoxelWorld.VoxelType.GLASS_PANE)return true;}return false;}
    private static final int[][] SIDES={{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}};
    private static final class PlanPreview{
        final String status,hash;final List<PlotPreview> plots=new ArrayList<>();final List<GroundColumn> groundColumns;
        final List<SemanticNode> semanticNodes;final List<SemanticLink> semanticLinks;final List<PathPreview> roadPaths=new ArrayList<>();final List<UnmetRequirement> unmetRequirements;final List<String> warnings;
        PlanPreview(PlanningIR ir,String hash){status=ir.status;this.hash=hash;semanticNodes=ir.transportNetwork.semanticNodes;semanticLinks=ir.transportNetwork.semanticLinks;for(Plot p:ir.plots)plots.add(new PlotPreview(p));groundColumns=ir.groundColumns;for(RoadEdge e:ir.transportNetwork.corridors)roadPaths.add(new PathPreview(e));unmetRequirements=ir.unmetRequirements;warnings=ir.auditLog.warnings;}
    }
    private record PlotPreview(String id,String requirementId,List<int[]> polygon,int[] origin,List<int[]> footprint,int baseElevation,String presetId,boolean diagonal45,String footprintShape,String sizeTier,int footprintArea){
        PlotPreview(Plot p){this(p.id,p.requirementId,p.polygon2D,p.origin2D,p.footprint,p.elevation.baseElevation,p.builder.presetId,p.builder.diagonal45,p.builder.footprintShape,p.builder.sizeTier,p.footprint.size());}}
    private static final class PathPreview{final String id,roadType,routingStyle;final List<int[]> steps=new ArrayList<>();PathPreview(RoadEdge e){id=e.id;roadType=e.roadType;routingStyle=e.routingStyle;for(RoadStep s:e.steps)steps.add(new int[]{s.x,s.y,s.z});}}
}
