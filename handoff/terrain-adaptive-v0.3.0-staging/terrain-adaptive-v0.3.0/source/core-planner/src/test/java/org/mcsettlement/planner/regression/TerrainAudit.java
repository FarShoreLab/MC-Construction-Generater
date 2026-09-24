package org.mcsettlement.planner.regression;

import java.util.*;
import org.mcsettlement.planner.*;
import org.mcsettlement.planner.SettlementPlanner.*;
import org.mcsettlement.planner.ir.PlanningIR;
import org.mcsettlement.planner.ir.PlanningIR.*;
import org.mcsettlement.planner.preset.*;
import org.mcsettlement.planner.civil.PlanConstruction;
import org.mcsettlement.planner.terrain.HeightfieldMap;
import static org.mcsettlement.planner.RoadGeometry.key;
import static org.mcsettlement.planner.RoadGeometry.x;
import static org.mcsettlement.planner.RoadGeometry.z;

/** Independent request, swept geometry, raw block state, topology and whole-pavement flood audit. */
public final class TerrainAudit {
    private static final int[][] DIR={{1,0},{0,1},{-1,0},{0,-1}};
    public static Set<Long> swept(int ax,int az,int bx,int bz,int width){
        // Lattice headings have components <=2: all cell-boundary events occur on eighths.
        // Include BOTH cells of boundary contacts; this differs from production slab intersection.
        Set<Long> out=new HashSet<>();int lo=-width/2;
        for(int t=0;t<=8;t++){double px=ax+(bx-ax)*t/8.0,pz=az+(bz-az)*t/8.0;
            int x0=(int)Math.floor(px+.5-1e-8),x1=(int)Math.floor(px+.5+1e-8),z0=(int)Math.floor(pz+.5-1e-8),z1=(int)Math.floor(pz+.5+1e-8);
            for(int xx=x0;xx<=x1;xx++)for(int zz=z0;zz<=z1;zz++)for(int dx=lo;dx<lo+width;dx++)for(int dz=lo;dz<lo+width;dz++)out.add(key(xx+dx,zz+dz));}
        return out;
    }
    private static String state(Map<PlanConstruction.Cell,String> blocks,GroundColumn c){return blocks.get(new PlanConstruction.Cell(c.x,c.targetY,c.z));}
    private static boolean physical(GroundColumn a,GroundColumn b,Map<PlanConstruction.Cell,String> blocks){
        if(a==null||b==null||Math.abs(a.x-b.x)+Math.abs(a.z-b.z)!=1)return false;if(a.targetY==b.targetY)return true;if(Math.abs(a.targetY-b.targetY)!=1)return false;
        GroundColumn high=a.targetY>b.targetY?a:b,low=high==a?b:a;
        String direction=high.x>low.x?"east":high.x<low.x?"west":high.z>low.z?"south":"north";
        String hs=state(blocks,high),ls=state(blocks,low);
        return hs!=null&&ls!=null&&hs.contains("cobblestone_stairs[")&&hs.contains("facing="+direction)&&hs.contains("half=bottom")&&hs.contains("shape=straight")&&(!ls.contains("stairs")||ls.contains("facing="+direction));
    }
    private static boolean headroom(GroundColumn c,Map<PlanConstruction.Cell,String> blocks){String floor=state(blocks,c);return floor!=null&&(floor.equals("minecraft:cobblestone")||floor.startsWith("minecraft:cobblestone_stairs["))&&"minecraft:air".equals(blocks.get(new PlanConstruction.Cell(c.x,c.targetY+1,c.z)))&&"minecraft:air".equals(blocks.get(new PlanConstruction.Cell(c.x,c.targetY+2,c.z)));}
    public static RegressionMain.Metrics audit(HeightfieldMap map,PlanRequest req,PlanningIR p){
        Set<String> errors=new LinkedHashSet<>();Map<Long,GroundColumn> cols=new HashMap<>();int cut=0,fill=0;
        Set<Long> roads=new HashSet<>(),walk=new HashSet<>();
        for(GroundColumn c:p.groundColumns){long k=key(c.x,c.z);if(cols.put(k,c)!=null)errors.add("duplicate_column");
            if(!map.inBounds(c.x,c.z)){errors.add("out_of_bounds");continue;}var o=map.getObstacle(c.x,c.z);
            if(o!=HeightfieldMap.ObstacleType.NONE&&o!=HeightfieldMap.ObstacleType.VEGETATION&&o!=HeightfieldMap.ObstacleType.TREE_TRUNK)errors.add("protected_or_water_column");
            if(c.originalY!=map.getSurfaceY(c.x,c.z))errors.add("wrong_original_height");int delta=c.originalY-c.targetY;
            int mc="foundation".equals(c.kind)?req.parcelConfig.maxCutBudget:req.roadMaxCut,mf="foundation".equals(c.kind)?req.parcelConfig.maxFillBudget:req.roadMaxFill;
            if(delta>mc||-delta>mf)errors.add("cell_cut_fill_limit");cut+=Math.max(0,delta);fill+=Math.max(0,-delta);
            if("road".equals(c.kind))roads.add(k);if(!"foundation".equals(c.kind))walk.add(k);
        }
        if(cut!=p.earthworks.totalCutVolume||fill!=p.earthworks.totalFillVolume)errors.add("earthwork_totals");
        Set<Long> sweptUnion=new HashSet<>();double length=0;Set<String> segments=new HashSet<>();
        var entry=p.transportNetwork.nodes.stream().filter(n->"entry".equals(n.type)).findFirst();if(entry.isPresent()){int[] e=entry.get().pos;sweptUnion.addAll(swept(e[0],e[2],e[0],e[2],req.roadWidth));}
        for(RoadEdge route:p.transportNetwork.corridors){if(route.width!=req.roadWidth)errors.add("changed_width");
            for(int i=0;i<route.steps.size();i++){RoadStep a=route.steps.get(i),b=route.steps.get(Math.min(i+1,route.steps.size()-1));GroundColumn c=cols.get(key(a.x,a.z));
                if(c==null||c.targetY!=a.y||!c.structure.equals(a.structure))errors.add("road_anchor_height");
                Set<Long> stencil=swept(a.x,a.z,b.x,b.z,req.roadWidth);sweptUnion.addAll(stencil);if(!roads.containsAll(stencil))errors.add("missing_complete_swept_width");
                if(i+1<route.steps.size()){int dx=Math.abs(b.x-a.x),dz=Math.abs(b.z-a.z);boolean allowed=req.roadDirections==8?dx<=1&&dz<=1&&dx+dz>0:dx+dz==1||(dx==1&&dz==2)||(dx==2&&dz==1);
                    if(!allowed||Math.abs(b.y-a.y)>1)errors.add("invalid_height_heading_state");long ka=key(a.x,a.z),kb=key(b.x,b.z);if(segments.add(Math.min(ka,kb)+":"+Math.max(ka,kb)))length+=Math.hypot(dx,dz);}}
        }
        if(!sweptUnion.equals(roads))errors.add("orphan_or_unbuilt_road_area");
        Map<String,BuildingRequirement> demands=new HashMap<>();for(var q:req.requirements)demands.put(q.id,q);
        List<Set<Long>> plotMasks=new ArrayList<>();Set<Long> foundations=new HashSet<>();
        for(Plot plot:p.plots){BuildingShape shape=PlannedBuilding.shape(plot);int ox=PlannedBuilding.originX(plot),oz=PlannedBuilding.originZ(plot);Set<Long> occupied=new HashSet<>();var demand=demands.get(plot.requirementId);
            if(plot.builder.diagonal45&&!req.diagonalBuildings)errors.add("diagonal_toggle_ignored");
            if(demand!=null){if(shape.sizeX<demand.minWidth||shape.sizeX>demand.maxWidth||shape.sizeZ<demand.minDepth||shape.sizeZ>demand.maxDepth||shape.sizeY>demand.heightLimit)errors.add("size_or_height_requirement");
                if(!plot.tags.contains(demand.purpose)||demand.presetId!=null&&!demand.presetId.equals(plot.builder.presetId))errors.add("purpose_or_preset_requirement");
                if(demand.nearPurpose!=null){boolean near=false;for(Plot other:p.plots)if(other!=plot&&other.tags.contains(demand.nearPurpose)){
                    int dx=2*ox+shape.sizeX-1-(2*PlannedBuilding.originX(other)+other.builder.footprintSize[0]-1),dz=2*oz+shape.sizeZ-1-(2*PlannedBuilding.originZ(other)+other.builder.footprintSize[1]-1);if(Math.abs(dx)+Math.abs(dz)<=2*demand.maxDistance)near=true;}if(!near)errors.add("near_purpose_relation");}
            }
            int water=Integer.MAX_VALUE;
            for(int[] cell:shape.cells){int xx=ox+cell[0],zz=oz+cell[1];long k=key(xx,zz);occupied.add(k);if(!foundations.add(k))errors.add("building_overlap");GroundColumn c=cols.get(k);
                if(c==null||!"foundation".equals(c.kind)||c.targetY!=plot.elevation.baseElevation)errors.add("foundation_missing_or_grade");
                if(map.getSlope(xx,zz)>req.parcelConfig.maxGroundSlope)errors.add("ground_slope_constraint");
                for(int dx=-req.parcelConfig.roadSetback;dx<=req.parcelConfig.roadSetback;dx++)for(int dz=-req.parcelConfig.roadSetback;dz<=req.parcelConfig.roadSetback;dz++)if(roads.contains(key(xx+dx,zz+dz)))errors.add("road_plot_setback");
                if(demand!=null&&"riverbank".equals(demand.placement)){int limit=demand.maxWaterDistance;for(int dx=-limit;dx<=limit;dx++)for(int dz=-limit+Math.abs(dx);dz<=limit-Math.abs(dx);dz++){int x1=xx+dx,z1=zz+dz;if(map.inBounds(x1,z1)){var o=map.getObstacle(x1,z1);if(o==HeightfieldMap.ObstacleType.WATER||o==HeightfieldMap.ObstacleType.WATER_DEEP)water=Math.min(water,Math.abs(dx)+Math.abs(dz));}}}}
            if(demand!=null&&"riverbank".equals(demand.placement)&&water>demand.maxWaterDistance)errors.add("riverbank_relation");
            plotMasks.add(occupied);for(RoadStep s:plot.entrance.path)walk.add(key(s.x,s.z));
        }
        for(int i=0;i<plotMasks.size();i++)for(int j=i+1;j<plotMasks.size();j++)for(long k:plotMasks.get(i))for(int dx=-req.parcelConfig.minPlotSpacing;dx<=req.parcelConfig.minPlotSpacing;dx++)for(int dz=-req.parcelConfig.minPlotSpacing;dz<=req.parcelConfig.minPlotSpacing;dz++)if(plotMasks.get(j).contains(key(x(k)+dx,z(k)+dz)))errors.add("plot_spacing");
        for(int i=0;i<p.plots.size();i++)for(int j=0;j<p.plots.size();j++)if(i!=j)for(RoadStep s:p.plots.get(i).entrance.path)if(plotMasks.get(j).contains(key(s.x,s.z)))errors.add("private_access_crosses_other_building");
        Map<PlanConstruction.Cell,String> blocks=new HashMap<>();if(!p.plots.isEmpty()){var edits=PlanConstruction.prepare(p,req.settlementStyle);PlanConstruction.apply(edits,(x,y,z,id)->blocks.put(new PlanConstruction.Cell(x,y,z),id));for(var e:edits)if(!cols.containsKey(key(e.x(),e.z())))errors.add("write_outside_manifest");if(edits.size()>req.searchBudget.constructionEdits)errors.add("edit_budget");}
        Map<Long,RoadNode> nodes=new HashMap<>();for(RoadNode n:p.transportNetwork.nodes){long k=key(n.pos[0],n.pos[2]);if(nodes.put(k,n)!=null||!cols.containsKey(k)||cols.get(k).targetY!=n.pos[1])errors.add("node_elevation");}
        if(!nodes.keySet().equals(walk))errors.add("topology_nodes_not_actual_pavement");
        Set<String> expected=new HashSet<>(),actual=new HashSet<>();for(long k:walk){GroundColumn a=cols.get(k);if(a==null||!headroom(a,blocks))errors.add("actual_written_headroom_or_floor");for(int[] d:DIR){long next=key(x(k)+d[0],z(k)+d[1]);if(walk.contains(next)&&physical(a,cols.get(next),blocks))expected.add(Math.min(k,next)+":"+Math.max(k,next));}}
        for(RoadEdge e:p.transportNetwork.edges){if(e.steps.size()!=2){errors.add("unsplit_topology");continue;}RoadStep a=e.steps.getFirst(),b=e.steps.getLast();long ak=key(a.x,a.z),bk=key(b.x,b.z);if(!actual.add(Math.min(ak,bk)+":"+Math.max(ak,bk)))errors.add("duplicate_edge");if(!e.fromNodeId.equals("n_"+a.x+"_"+a.z)||!e.toNodeId.equals("n_"+b.x+"_"+b.z))errors.add("topology_endpoint");}
        if(!actual.equals(expected))errors.add("false_or_missing_physical_edge");
        Set<Long> visited=new HashSet<>();ArrayDeque<Long> q=new ArrayDeque<>();if(entry.isPresent()){int[] e=entry.get().pos;long k=key(e[0],e[2]);visited.add(k);q.add(k);}
        while(!q.isEmpty()){long k=q.remove();for(int[] d:DIR){long n=key(x(k)+d[0],z(k)+d[1]);if(walk.contains(n)&&!visited.contains(n)&&physical(cols.get(k),cols.get(n),blocks)){visited.add(n);q.add(n);}}}
        int reachable=0;for(Plot plot:p.plots){long door=key(plot.entrance.accessPoint[0],plot.entrance.accessPoint[2]);if(visited.contains(door))reachable++;else errors.add("unreachable_actual_door");
            for(int i=1;i<plot.entrance.path.size();i++){RoadStep a=plot.entrance.path.get(i-1),b=plot.entrance.path.get(i);if(!physical(cols.get(key(a.x,a.z)),cols.get(key(b.x,b.z)),blocks))errors.add("private_access_step");}}
        if(!visited.containsAll(walk))errors.add("disconnected_pavement_fringe");
        if(p.search.pathExpanded>req.searchBudget.pathExpanded||p.search.candidateChecks>req.searchBudget.candidateChecks||p.search.gradeRelaxations>req.searchBudget.gradeRelaxations||p.search.peakPathStates>req.searchBudget.pathStates||cols.size()>req.searchBudget.groundColumns)errors.add("search_or_column_budget");
        if(!Set.of("COMPLETE","INVALID_REQUEST").contains(p.status)&&p.unmetRequirements.isEmpty())errors.add("missing_failure_explanation");
        for(var u:p.unmetRequirements)if(u.reason==null||u.reason.isBlank()||u.allocated>=u.requested)errors.add("invalid_failure_explanation");
        if(!errors.isEmpty())System.out.println("AUDIT "+errors);
        return new RegressionMain.Metrics(reachable,errors.size(),cut,fill,length,roads.size());
    }
}
