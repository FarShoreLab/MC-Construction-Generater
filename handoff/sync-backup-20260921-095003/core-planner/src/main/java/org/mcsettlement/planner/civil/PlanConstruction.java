package org.mcsettlement.planner.civil;

import org.mcsettlement.planner.ir.PlanningIR;
import org.mcsettlement.planner.ir.PlanningIR.*;
import org.mcsettlement.planner.preset.*;
import java.util.*;
import static org.mcsettlement.planner.RoadGeometry.*;

/** Shared all-or-nothing edit program. Only groundColumns authorize terrain / paving edits. */
public final class PlanConstruction {
    private PlanConstruction() {}
    public record Cell(int x,int y,int z) {}
    public record Edit(int x,int y,int z,String block) {}
    @FunctionalInterface public interface BlockWriter {void set(int x,int y,int z,String block);}
    public static String pavementBlock(GroundColumn c){
        if(!stair(c))return "minecraft:cobblestone";
        if(c.facing==null||!Set.of("NORTH","SOUTH","WEST","EAST").contains(c.facing))throw new IllegalArgumentException("INVALID_STAIR_FACING");
        return "minecraft:cobblestone_stairs[facing="+c.facing.toLowerCase(Locale.ROOT)+",half=bottom,shape=straight,waterlogged=false]";
    }
    /** Prepares and validates the WHOLE program before the first world mutation. */
    public static List<Edit> prepare(PlanningIR ir,String theme){
        if(!Set.of("COMPLETE","PARTIAL").contains(ir.status))throw new IllegalArgumentException("PLAN_NOT_CONSTRUCTIBLE: "+ir.status);
        if(ir.plots.isEmpty())return List.of();
        if(ir.transportNetwork.directionCount!=8&&ir.transportNetwork.directionCount!=12)throw new IllegalArgumentException("INVALID_DIRECTION_COUNT");
        if(ir.plots.size()>32||ir.groundColumns.isEmpty()||ir.groundColumns.size()>100000)throw new IllegalArgumentException("INVALID_MANIFEST_SIZE");
        int limit=ir.search.editLimit==0?1000000:ir.search.editLimit;if(limit<1||limit>1000000)throw new IllegalArgumentException("INVALID_EDIT_LIMIT");
        Map<Long,GroundColumn> columns=new HashMap<>();long editCount=0;
        for(GroundColumn c:ir.groundColumns){
            if(!Set.of("road","access","foundation").contains(c.kind)||!Set.of("surface","stair").contains(c.structure)||
                    c.targetY < -2032 || c.targetY>2032 || c.clearToY<c.targetY+2||c.clearToY-c.targetY>64||Math.abs(c.targetY-c.originalY)>16||
                    "foundation".equals(c.kind)&&stair(c))throw new IllegalArgumentException("INVALID_CONSTRUCTION_COLUMN");
            pavementBlock(c);
            if(columns.put(key(c.x,c.z),c)!=null)throw new IllegalArgumentException("DUPLICATE_COLUMN");
            editCount+=c.clearToY-Math.min(c.targetY,c.originalY+1)+1;
        }
        if(editCount>limit)throw new IllegalArgumentException("CONSTRUCTION_EDIT_BUDGET_EXCEEDED");
        if(ir.search.columnLimit>0&&columns.size()>ir.search.columnLimit)throw new IllegalArgumentException("GROUND_COLUMN_BUDGET_EXCEEDED");
        validateWalk(ir,columns);
        Map<Cell,Edit> edits=new LinkedHashMap<>();
        for(GroundColumn c:ir.groundColumns){
            for(int y=c.originalY+1;y<c.targetY;y++)put(edits,c.x,y,c.z,"minecraft:cobblestone");
            put(edits,c.x,c.targetY,c.z,pavementBlock(c));
            for(int y=c.targetY+1;y<=c.clearToY;y++)put(edits,c.x,y,c.z,"minecraft:air");
        }
        Set<Long> foundations=new HashSet<>();
        for(Plot p:ir.plots){
            BuildingShape shape=PlannedBuilding.shape(p);String[][][] grid=shape.grid(theme);
            int ox=PlannedBuilding.originX(p),oz=PlannedBuilding.originZ(p),oy=p.elevation.baseElevation;
            for(int[] point:shape.cells){int x=point[0],z=point[1];long k=key(ox+x,oz+z);GroundColumn c=columns.get(k);
                if(!foundations.add(k)||c==null||!"foundation".equals(c.kind)||c.targetY!=oy||oy+shape.sizeY-1>c.clearToY)
                    throw new IllegalArgumentException("BUILDING_OUTSIDE_FOUNDATION_MANIFEST: "+p.id);
                for(int y=0;y<shape.sizeY;y++){String block=grid[x][y][z];if(y==0&&"minecraft:air".equals(block))continue;put(edits,ox+x,oy+y,oz+z,block);}}
            if(p.entrance.width!=1||p.entrance.path.isEmpty()||p.entrance.connectedEdgeId==null)throw new IllegalArgumentException("MISSING_ENTRANCE_CONNECTION: "+p.id);
            RoadStep first=p.entrance.path.getFirst();int[] door=p.entrance.accessPoint;
            if(first.x!=door[0]||first.y!=door[1]||first.z!=door[2])throw new IllegalArgumentException("DOOR_PATH_MISMATCH");
            GroundColumn previous=null;for(RoadStep s:p.entrance.path){GroundColumn c=columns.get(key(s.x,s.z));
                if(c==null||c.targetY!=s.y||!Objects.equals(c.structure,s.structure)||previous!=null&&!canWalk(previous,c))throw new IllegalArgumentException("UNBUILT_ENTRANCE: "+p.id);previous=c;}
        }
        if(foundations.size()!=columns.values().stream().filter(c->"foundation".equals(c.kind)).count())throw new IllegalArgumentException("ORPHAN_FOUNDATION_COLUMN");
        if(edits.size()!=editCount)throw new IllegalArgumentException("EDIT_PROGRAM_EXCEEDS_COLUMN_MANIFEST");
        List<Edit> ordered=new ArrayList<>(edits.values());
        ordered.sort(Comparator.comparing(e->{GroundColumn c=columns.get(key(e.x(),e.z()));return c!=null&&stair(c)&&e.y()==c.targetY;}));
        return List.copyOf(ordered);
    }
    private static void validateWalk(PlanningIR ir,Map<Long,GroundColumn> cols){
        if(ir.transportNetwork.nodes.size()>100000||ir.transportNetwork.edges.size()>200000||ir.transportNetwork.corridors.size()>32)throw new IllegalArgumentException("INVALID_TOPOLOGY_SIZE");
        Map<String,GroundColumn> nodes=new HashMap<>();Map<Long,String> position=new HashMap<>();String entry=null;
        for(RoadNode n:ir.transportNetwork.nodes){long k=key(n.pos[0],n.pos[2]);GroundColumn c=cols.get(k);
            if(c==null||c.targetY!=n.pos[1]||nodes.put(n.id,c)!=null||position.put(k,n.id)!=null)throw new IllegalArgumentException("NODE_WITHOUT_COLUMN");
            if("entry".equals(n.type)){if(entry!=null)throw new IllegalArgumentException("DUPLICATE_ENTRY");entry=n.id;}}
        if(entry==null)throw new IllegalArgumentException("MISSING_ENTRY");
        Map<String,List<String>> adjacent=new HashMap<>();Map<String,RoadEdge> edges=new HashMap<>();
        for(RoadEdge e:ir.transportNetwork.edges){GroundColumn a=nodes.get(e.fromNodeId),b=nodes.get(e.toNodeId);
            if(edges.put(e.id,e)!=null||e.steps.size()!=2||!canWalk(a,b)||!matches(e.steps.getFirst(),a)||!matches(e.steps.getLast(),b))throw new IllegalArgumentException("UNWALKABLE_TOPOLOGY_EDGE");
            adjacent.computeIfAbsent(e.fromNodeId,k->new ArrayList<>()).add(e.toNodeId);adjacent.computeIfAbsent(e.toNodeId,k->new ArrayList<>()).add(e.fromNodeId);}
        Set<String> reached=new HashSet<>();ArrayDeque<String> queue=new ArrayDeque<>();queue.add(entry);reached.add(entry);
        while(!queue.isEmpty())for(String next:adjacent.getOrDefault(queue.remove(),List.of()))if(reached.add(next))queue.add(next);
        if(reached.size()!=nodes.size())throw new IllegalArgumentException("DISCONNECTED_PAVEMENT");
        for(GroundColumn c:cols.values())if(!"foundation".equals(c.kind)&&!position.containsKey(key(c.x,c.z)))throw new IllegalArgumentException("PAVEMENT_WITHOUT_NODE");
        for(Plot p:ir.plots){String node=position.get(key(p.entrance.accessPoint[0],p.entrance.accessPoint[2]));RoadEdge e=edges.get(p.entrance.connectedEdgeId);
            if(!reached.contains(node)||e==null||!Objects.equals(e.fromNodeId,node)&&!Objects.equals(e.toNodeId,node))throw new IllegalArgumentException("DISCONNECTED_DOOR");}
        for(RoadEdge route:ir.transportNetwork.corridors){
            if(route.width<1||route.width>5||route.steps.isEmpty()||route.steps.size()>100000)throw new IllegalArgumentException("INVALID_CORRIDOR");
            for(RoadStep s:route.steps)if(!matches(s,cols.get(key(s.x,s.z))))throw new IllegalArgumentException("CORRIDOR_HEIGHT_MISMATCH");
            for(long k:footprint(route.steps,route.width)){GroundColumn c=cols.get(k);if(c==null||!"road".equals(c.kind))throw new IllegalArgumentException("UNBUILT_SWEPT_ROAD_FOOTPRINT");}
            for(int i=1;i<route.steps.size();i++){
                RoadStep a=route.steps.get(i-1),b=route.steps.get(i);int dx=b.x-a.x,dz=b.z-a.z;
                boolean supported=false;for(int[] d:directions(ir.transportNetwork.directionCount))if(dx==d[0]&&dz==d[1])supported=true;
                if(!supported||Math.abs(b.y-a.y)>1)throw new IllegalArgumentException("INVALID_HEIGHT_STATE_TRANSITION");
                Set<Long> allowed=new HashSet<>();for(int[] d:stencil(dx,dz,route.width))allowed.add(key(a.x+d[0],a.z+d[1]));
                Set<Long> seen=new HashSet<>();ArrayDeque<Long> q=new ArrayDeque<>();long start=key(a.x,a.z),goal=key(b.x,b.z);seen.add(start);q.add(start);
                while(!q.isEmpty()&&!seen.contains(goal)){long k=q.remove();for(int[] d:CARDINAL){long next=key(x(k)+d[0],z(k)+d[1]);if(allowed.contains(next)&&!seen.contains(next)&&canWalk(cols.get(k),cols.get(next))){seen.add(next);q.add(next);}}}
                if(!seen.contains(goal))throw new IllegalArgumentException("UNCONSTRUCTIBLE_DIAGONAL_STEP");
            }
        }
    }
    private static boolean matches(RoadStep s,GroundColumn c){return c!=null&&s.x==c.x&&s.z==c.z&&s.y==c.targetY&&Objects.equals(s.structure,c.structure);}
    private static void put(Map<Cell,Edit> out,int x,int y,int z,String block){out.put(new Cell(x,y,z),new Edit(x,y,z,block==null?"minecraft:air":block));if(out.size()>1000000)throw new IllegalArgumentException("CONSTRUCTION_EXCEEDS_ONE_MILLION_EDITS");}
    public static void apply(List<Edit> edits,BlockWriter writer){for(Edit e:edits)writer.set(e.x,e.y,e.z,e.block);}
}
