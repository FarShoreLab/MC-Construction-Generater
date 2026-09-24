package org.mcsettlement.planner;

import org.mcsettlement.planner.SettlementPlanner.*;
import org.mcsettlement.planner.ir.PlanningIR;
import org.mcsettlement.planner.ir.PlanningIR.*;
import org.mcsettlement.planner.preset.BuildingPreset;
import org.mcsettlement.planner.preset.BuildingPresetRegistry;
import org.mcsettlement.planner.preset.PlannedBuilding;
import org.mcsettlement.planner.terrain.HeightfieldMap;

import java.time.Instant;
import java.util.*;
import static org.mcsettlement.planner.RoadGeometry.*;

/** Finite multi-start greedy search. Hard constraints precede lexicographic ranking. */
final class BoundedSettlementPlanner {
    private record Demand(BuildingRequirement requirement, String id, List<BuildingPreset> variants) {}
    private record Candidate(Demand demand, BuildingPreset preset, int x, int z,
                             List<RoadStep> access, long preference, int soil, int distance, long tie) {}
    private record QueueNode(int index, int g, int f) {}
    private static final int[][] DIRS = {{1,0},{0,1},{-1,0},{0,-1}};
    private static final Comparator<Candidate> ORDER = Comparator.comparingLong(Candidate::preference)
            .thenComparingInt(Candidate::soil).thenComparingInt(Candidate::distance)
            .thenComparingLong(Candidate::tie).thenComparingInt(Candidate::x).thenComparingInt(Candidate::z)
            .thenComparing(c -> c.preset.id).thenComparing(c -> c.preset.entrance.facing);
    private static final class Layout {
        final int[] entry;
        final List<Candidate> placed = new ArrayList<>();
        final List<List<RoadStep>> routes = new ArrayList<>();
        final Set<Long> roadCells = new HashSet<>();
        final Set<Long> accessCells = new HashSet<>();
        final Map<String,String> failure = new HashMap<>();
        final Set<Long> accessibleCenters = new HashSet<>();
        Layout(int[] entry, int width) { this.entry=entry; stamp(roadCells,entry[0],entry[2],width); }
    }

    static PlanningIR plan(HeightfieldMap map, PlanRequest req) {
        long started = System.nanoTime();
        PlanningIR ir = new PlanningIR();
        ir.metadata.timestamp = Instant.now().toString();
        if (map != null) {
            ir.metadata.minBounds = new int[]{map.getMinX(), -2048, map.getMinZ()};
            ir.metadata.maxBounds = new int[]{map.getMinX()+map.getWidth()-1, 2048, map.getMinZ()+map.getDepth()-1};
        }
        String error = validate(map,req);
        if (error != null) {
            ir.status="INVALID_REQUEST"; ir.auditLog.warnings.add(error);
            ir.search.elapsedNanos=System.nanoTime()-started; return ir;
        }
        ir.metadata.randomSeed=req.seed;
        ir.search.candidateLimit=req.searchBudget.candidateChecks;
        ir.search.pathLimit=req.searchBudget.pathExpanded;
        List<Demand> demands = demands(req);
        List<int[]> gates = gates(map,req);
        if (demands.isEmpty()) {
            ir.status="COMPLETE"; ir.metadata.score.put("demand_satisfaction",1.0);
            ir.search.elapsedNanos=System.nanoTime()-started; return ir;
        }
        Layout best = null;
        int attempts = gates.isEmpty() ? 0 : Math.min(req.searchBudget.attempts, req.entry == null ? gates.size() : req.searchBudget.attempts);
        for (int attempt=0; attempt<attempts; attempt++) {
            if (ir.search.candidateChecks >= ir.search.candidateLimit || ir.search.pathExpanded >= ir.search.pathLimit) break;
            int[] gate = gates.get(attempt % gates.size());
            Layout layout = new Layout(gate,req.roadWidth);
            ir.search.attempts++;
            List<Demand> ordered = orderDemands(demands, req.seed + attempt);
            int remainingSlots = demands.stream().mapToInt(d -> d.requirement.count).sum();
            // Each attempt/remaining slot gets a finite share, so the first slot cannot consume everything.
            int attemptEnd = ir.search.candidateChecks + (ir.search.candidateLimit-ir.search.candidateChecks)/(attempts-attempt);
            int pathEnd = ir.search.pathExpanded + (ir.search.pathLimit-ir.search.pathExpanded)/(attempts-attempt);
            terrainReachability(map,req,layout,ir.search,pathEnd);
            for (Demand demand : ordered) for (int slot=0; slot<demand.requirement.count; slot++) {
                int allowance=Math.max(0,(attemptEnd-ir.search.candidateChecks)/Math.max(1,remainingSlots--));
                if (demand.variants.isEmpty()) {
                    layout.failure.put(demand.id,"NO_PRESET_MATCHES_PURPOSE_SIZE_HEIGHT"); continue;
                }
                if (demand.requirement.nearPurpose != null && layout.placed.stream().noneMatch(
                        p -> p.demand.requirement.purpose.equals(demand.requirement.nearPurpose))) {
                    layout.failure.put(demand.id,"SPATIAL_REFERENCE_NOT_PLACED_OR_CYCLIC"); continue;
                }
                long salt=mix(req.seed ^ ((long)attempt<<40) ^ ((long)demand.id.hashCode()<<8) ^ slot);
                List<Candidate> candidates = candidates(map,req,layout,demand,allowance,ir.search,salt);
                boolean placed=false;
                for (Candidate candidate : candidates) {
                    List<RoadStep> route = route(map,req,layout,candidate,ir.search,pathEnd);
                    if (route.isEmpty()) continue;
                    layout.placed.add(candidate); layout.routes.add(route);
                    for (RoadStep s : candidate.access) layout.accessCells.add(key(s.x,s.z));
                    for (RoadStep s : route) stamp(layout.roadCells,s.x,s.z,req.roadWidth);
                    placed=true; break;
                }
                if (!placed) layout.failure.put(demand.id,
                        candidates.isEmpty() ? "NO_FEASIBLE_CANDIDATE_IN_BUDGET" :
                        ir.search.pathExpanded>=pathEnd ? "PATH_SEARCH_BUDGET_EXHAUSTED" : "NO_FULL_WIDTH_ROUTE_AT_ENTRY_GRADE");
            }
            if (best == null || better(map,req,layout,best)) best=layout;
        }
        ir.search.budgetExhausted=ir.search.candidateChecks>=ir.search.candidateLimit || ir.search.pathExpanded>=ir.search.pathLimit;
        if (best != null && !best.placed.isEmpty()) emit(map,req,best,ir);
        int requested=0;
        for (Demand demand : demands) {
            requested+=demand.requirement.count;
            int allocated=(int)ir.plots.stream().filter(p -> demand.id.equals(p.requirementId)).count();
            if (allocated<demand.requirement.count) {
                UnmetRequirement u=new UnmetRequirement(); u.requirementId=demand.id;
                u.purpose=demand.requirement.purpose; u.requested=demand.requirement.count; u.allocated=allocated;
                u.reason=best==null ? "NO_VALID_ENTRY_FULL_WIDTH_OR_GRADE" :
                        best.failure.getOrDefault(demand.id,"SEARCH_BUDGET_EXHAUSTED");
                ir.unmetRequirements.add(u);
            }
        }
        if (!req.requirements.isEmpty() && req.targetPlots != requested)
            ir.auditLog.warnings.add("Explicit requirements are authoritative; targetPlots="+req.targetPlots+" differs from total="+requested);
        ir.status=ir.unmetRequirements.isEmpty()?"COMPLETE":ir.plots.isEmpty()?"INFEASIBLE":"PARTIAL";
        ir.metadata.score.put("demand_satisfaction",ir.plots.size()/(double)requested);
        ir.metadata.score.put("plots_allocated",(double)ir.plots.size());
        if (!ir.unmetRequirements.isEmpty()) ir.auditLog.warnings.add(
                "Unmet requirements are not a proof of global infeasibility; search and supported road grade are bounded.");
        ir.search.elapsedNanos=System.nanoTime()-started;
        return ir;
    }

    private static String validate(HeightfieldMap m, PlanRequest r) {
        if (m==null || r==null || r.parcelConfig==null || r.searchBudget==null || r.requirements==null) return "NULL_INPUT";
        if (m.getWidth()<1 || m.getDepth()<1 || m.getWidth()>256 || m.getDepth()>256) return "MAP_DIMENSIONS_MUST_BE_1_TO_256";
        if (r.targetPlots<0 || r.targetPlots>32 || r.roadWidth<1 || r.roadWidth>5) return "COUNT_OR_ROAD_WIDTH_OUT_OF_RANGE";
        if (r.entry!=null && (r.entry.length!=3 || !m.inBounds(r.entry[0],r.entry[2]))) return "INVALID_ENTRY";
        if (r.roadMaxCut<0 || r.roadMaxCut>16 || r.roadMaxFill<0 || r.roadMaxFill>16 ||
                r.parcelConfig.maxCutBudget<0 || r.parcelConfig.maxCutBudget>16 ||
                r.parcelConfig.maxFillBudget<0 || r.parcelConfig.maxFillBudget>16 ||
                r.parcelConfig.minPlotSpacing<0 || r.parcelConfig.minPlotSpacing>16 ||
                r.parcelConfig.roadSetback<0 || r.parcelConfig.roadSetback>16 ||
                r.parcelConfig.defaultWidth<3 || r.parcelConfig.defaultWidth>32 ||
                r.parcelConfig.defaultDepth<3 || r.parcelConfig.defaultDepth>32 ||
                !Float.isFinite(r.parcelConfig.maxGroundSlope) || r.parcelConfig.maxGroundSlope<0) return "INVALID_CIVIL_LIMITS";
        SearchBudget b=r.searchBudget;
        if (b.attempts<1 || b.attempts>8 || b.candidateChecks<1 || b.candidateChecks>200000 ||
                b.pathExpanded<1 || b.pathExpanded>2000000 || b.shortlist<1 || b.shortlist>32 ||
                b.candidateStride<1 || b.candidateStride>16) return "INVALID_SEARCH_BUDGET";
        if (!Set.of("ridge","valley","center","any","riverbank").contains(String.valueOf(r.landmarkPlacement))) return "INVALID_LANDMARK_PLACEMENT";
        Set<String> ids=new HashSet<>(); int total=0;
        for (int i=0;i<r.requirements.size();i++) {
            BuildingRequirement q=r.requirements.get(i);
            if (q==null || q.purpose==null || q.purpose.isBlank() || q.purpose.length()>64 || q.count<1 || q.count>32 ||
                    q.minWidth<3 || q.maxWidth>32 || q.minWidth>q.maxWidth ||
                    q.minDepth<3 || q.maxDepth>32 || q.minDepth>q.maxDepth || q.heightLimit<3 || q.heightLimit>48 ||
                    !Set.of("ridge","valley","center","any","riverbank").contains(String.valueOf(q.placement)) ||
                    q.maxWaterDistance<1 || q.maxWaterDistance>32 || q.maxDistance<1 || q.maxDistance>512)
                return "INVALID_REQUIREMENT_"+i;
            String id=q.id==null?"requirement_"+i:q.id;
            if (id.isBlank() || id.length()>80 || !ids.add(id)) return "INVALID_OR_DUPLICATE_REQUIREMENT_ID";
            total+=q.count;
        }
        if (total>32 || r.requirements.size()>32) return "TOTAL_DEMAND_EXCEEDS_32";
        for (int x=0;x<m.getWidth();x++) for(int z=0;z<m.getDepth();z++) {
            if (m.getLocalSurfaceY(x,z)<-2000 || m.getLocalSurfaceY(x,z)>2000 || m.getLocalObstacle(x,z)==null) return "INVALID_HEIGHTFIELD_CELL";
        }
        return null;
    }

    private static List<Demand> demands(PlanRequest req) {
        List<BuildingRequirement> requirements=new ArrayList<>(req.requirements);
        if (requirements.isEmpty()) {
            if (req.targetPlots>0) {
                BuildingRequirement q=new BuildingRequirement(); q.id="landmark";q.purpose="government";
                q.placement=req.landmarkPlacement;requirements.add(q);
            }
            if (req.targetPlots>1) { BuildingRequirement q=new BuildingRequirement();q.id="workshop";q.purpose="workshop";requirements.add(q); }
            if (req.targetPlots>2) { BuildingRequirement q=new BuildingRequirement();q.id="housing";q.count=req.targetPlots-2;requirements.add(q); }
        }
        if (req.requirements.isEmpty()) for (BuildingRequirement q : requirements) {
            // Legacy desired dimensions become lower bounds; no preset is ever clipped to fit them.
            q.minWidth=req.parcelConfig.defaultWidth;q.maxWidth=Math.max(24,q.minWidth);
            q.minDepth=req.parcelConfig.defaultDepth;q.maxDepth=Math.max(24,q.minDepth);
        }
        List<Demand> result=new ArrayList<>();
        for (int i=0;i<requirements.size();i++) {
            BuildingRequirement q=requirements.get(i);
            List<BuildingPreset> variants=new ArrayList<>();
            List<BuildingPreset> presets=BuildingPresetRegistry.getInstance().getAllPresets();
            presets.sort(Comparator.comparing(p->p.id));
            for (BuildingPreset p : presets) {
                if (q.presetId!=null && !q.presetId.equals(p.id)) continue;
                if (!q.purpose.equals(p.category) && !p.tags.contains(q.purpose) &&
                        !(q.purpose.equals("landmark") && Set.of("government","military","culture").contains(p.category))) continue;
                for (String facing : List.of("NORTH","EAST","SOUTH","WEST")) {
                    BuildingPreset v=p.rotateToFacing(facing);
                    if (v.sizeX>=q.minWidth && v.sizeX<=q.maxWidth && v.sizeZ>=q.minDepth && v.sizeZ<=q.maxDepth && v.sizeY<=q.heightLimit) {
                        try { PlannedBuilding.grid(v,null); variants.add(v); }
                        catch (IllegalArgumentException ignored) { /* Rejected, not silently substituted. */ }
                    }
                }
            }
            result.add(new Demand(q,q.id==null?"requirement_"+i:q.id,variants));
        }
        return result;
    }
    private static List<Demand> orderDemands(List<Demand> input,long seed) {
        List<Demand> rest=new ArrayList<>(input),out=new ArrayList<>();
        rest.sort(Comparator.<Demand>comparingInt(d->-d.variants.stream().mapToInt(p->p.sizeX*p.sizeZ).max().orElse(0))
                .thenComparingLong(d->mix(seed^d.id.hashCode())).thenComparing(Demand::id));
        while (!rest.isEmpty()) {
            Demand next=null;
            for (Demand d : rest) if (d.requirement.nearPurpose==null || out.stream().anyMatch(
                    placed->placed.requirement.purpose.equals(d.requirement.nearPurpose))) { next=d;break; }
            if (next==null) { out.addAll(rest);break; }
            out.add(next);rest.remove(next);
        }
        return out;
    }
    private static List<int[]> gates(HeightfieldMap map,PlanRequest req) {
        if (req.entry!=null) {
            if (req.entry[1]!=map.getSurfaceY(req.entry[0],req.entry[2]) || !roadCell(map,req,null,null,req.entry[0],req.entry[2],req.entry[1])) return List.of();
            return Collections.singletonList(req.entry.clone());
        }
        List<int[]> possible=new ArrayList<>();
        int ax=map.getMinX()-low(req.roadWidth),az=map.getMinZ()-low(req.roadWidth);
        int bx=map.getMinX()+map.getWidth()-1-high(req.roadWidth),bz=map.getMinZ()+map.getDepth()-1-high(req.roadWidth);
        if (ax>bx || az>bz) return possible;
        for (int x=ax;x<=bx;x+=2) { addGate(possible,map,req,x,az); if(bz!=az)addGate(possible,map,req,x,bz); }
        for (int z=az+1;z<bz;z+=2) { addGate(possible,map,req,ax,z); if(bx!=ax)addGate(possible,map,req,bx,z); }
        possible.sort(Comparator.<int[]>comparingLong(e->mix(req.seed^key(e[0],e[2]))).thenComparingInt(e->e[0]).thenComparingInt(e->e[2]));
        return possible;
    }
    private static void addGate(List<int[]> out,HeightfieldMap map,PlanRequest req,int x,int z) {
        int y=map.getSurfaceY(x,z);
        if(roadCell(map,req,null,null,x,z,y)) out.add(new int[]{x,y,z});
    }
    private static List<Candidate> candidates(HeightfieldMap map,PlanRequest req,Layout l,Demand d,
                                             int allowance,SearchStats stats,long salt) {
        List<Candidate> top=new ArrayList<>();
        int stride=req.searchBudget.candidateStride;
        int nx=(map.getWidth()+stride-1)/stride,nz=(map.getDepth()+stride-1)/stride;
        long total=(long)nx*nz*d.variants.size();
        long start=Math.floorMod(salt,total),jump=Math.floorMod(mix(salt),total)|1;
        while (gcd(jump,total)!=1) jump+=2;
        int limit=(int)Math.min(allowance,total);
        for (int i=0;i<limit && stats.candidateChecks<stats.candidateLimit;i++) {
            stats.candidateChecks++;
            long index=(start+i*jump)%total;
            BuildingPreset v=d.variants.get((int)(index % d.variants.size()));
            long cell=index/d.variants.size();
            int x=map.getMinX()+(int)(cell%nx)*stride,z=map.getMinZ()+(int)(cell/nx)*stride;
            Candidate c=candidate(map,req,l,d,v,x,z,mix(salt+i));
            if(c==null) continue;
            top.add(c);top.sort(ORDER);
            if(top.size()>req.searchBudget.shortlist) top.removeLast();
        }
        return top;
    }
    private static Candidate candidate(HeightfieldMap map,PlanRequest req,Layout l,Demand d,BuildingPreset v,int x,int z,long tie) {
        int bx=x+v.sizeX-1,bz=z+v.sizeZ-1,y=l.entry[1];
        if (!map.inBounds(x,z) || !map.inBounds(bx,bz)) return null;
        for(Candidate p:l.placed) if(overlap(x,z,bx,bz,p,req.parcelConfig.minPlotSpacing)) return null;
        for(int xx=x-req.parcelConfig.roadSetback;xx<=bx+req.parcelConfig.roadSetback;xx++)
            for(int zz=z-req.parcelConfig.roadSetback;zz<=bz+req.parcelConfig.roadSetback;zz++)
                if(l.roadCells.contains(key(xx,zz))) return null;
        int soil=0;
        for(int xx=x;xx<=bx;xx++) for(int zz=z;zz<=bz;zz++) {
            if (l.accessCells.contains(key(xx,zz))) return null;
            if(!gradeFits(map,xx,zz,y,req.parcelConfig.maxCutBudget,req.parcelConfig.maxFillBudget)
                    || map.getSlope(xx,zz)>req.parcelConfig.maxGroundSlope) return null;
            soil+=Math.abs(map.getSurfaceY(xx,zz)-y);
        }
        BuildingRequirement q=d.requirement;
        if("riverbank".equals(q.placement) && waterDistance(map,x,z,bx,bz,q.maxWaterDistance)>q.maxWaterDistance) return null;
        if(q.nearPurpose!=null && l.placed.stream().filter(p->q.nearPurpose.equals(p.demand.requirement.purpose))
                .noneMatch(p->Math.abs((x+bx)-(2*p.x+p.preset.sizeX-1))+Math.abs((z+bz)-(2*p.z+p.preset.sizeZ-1))<=2*q.maxDistance)) return null;
        int[] dir=PlannedBuilding.direction(v.entrance.facing);
        int ex=x+v.entrance.x,ez=z+v.entrance.z;
        int tx=ex,tz=ez;
        if(dir[0]<0) tx=x-req.parcelConfig.roadSetback-high(req.roadWidth)-1;
        if(dir[0]>0) tx=bx+req.parcelConfig.roadSetback-low(req.roadWidth)+1;
        if(dir[1]<0) tz=z-req.parcelConfig.roadSetback-high(req.roadWidth)-1;
        if(dir[1]>0) tz=bz+req.parcelConfig.roadSetback-low(req.roadWidth)+1;
        if (!l.accessibleCenters.contains(key(tx,tz))) return null;
        List<RoadStep> access=new ArrayList<>();
        for(int ax=ex,az=ez;;ax+=dir[0],az+=dir[1]) {
            boolean inside=ax>=x&&ax<=bx&&az>=z&&az<=bz;
            if(!gradeFits(map,ax,az,y,inside?req.parcelConfig.maxCutBudget:req.roadMaxCut,inside?req.parcelConfig.maxFillBudget:req.roadMaxFill))return null;
            for(Candidate p:l.placed) if(overlap(ax,az,ax,az,p,0)) return null;
            access.add(new RoadStep(ax,y,az,"surface"));
            if(ax==tx&&az==tz)break;
        }
        long pref=switch(q.placement) {
            case "center" -> Math.abs(2*x+v.sizeX-1-(2*map.getMinX()+map.getWidth()-1))+
                    Math.abs(2*z+v.sizeZ-1-(2*map.getMinZ()+map.getDepth()-1));
            case "ridge" -> -map.getSurfaceY(x+v.sizeX/2,z+v.sizeZ/2);
            case "valley" -> map.getSurfaceY(x+v.sizeX/2,z+v.sizeZ/2);
            case "riverbank" -> waterDistance(map,x,z,bx,bz,q.maxWaterDistance);
            default -> 0;
        };
        return new Candidate(d,v,x,z,access,pref,soil,Math.abs(tx-l.entry[0])+Math.abs(tz-l.entry[2]),tie);
    }
    private static int waterDistance(HeightfieldMap m,int x,int z,int bx,int bz,int limit) {
        int best=limit+1;
        for(int xx=Math.max(m.getMinX(),x-limit);xx<=Math.min(m.getMinX()+m.getWidth()-1,bx+limit);xx++)
            for(int zz=Math.max(m.getMinZ(),z-limit);zz<=Math.min(m.getMinZ()+m.getDepth()-1,bz+limit);zz++) {
                var o=m.getObstacle(xx,zz);
                if(o==HeightfieldMap.ObstacleType.WATER || o==HeightfieldMap.ObstacleType.WATER_DEEP)
                    best=Math.min(best,Math.max(0,Math.max(x-xx,xx-bx))+Math.max(0,Math.max(z-zz,zz-bz)));
            }
        return best;
    }
    private static boolean overlap(int x,int z,int bx,int bz,Candidate p,int spacing) {
        return x-spacing<=p.x+p.preset.sizeX-1 && bx+spacing>=p.x && z-spacing<=p.z+p.preset.sizeZ-1 && bz+spacing>=p.z;
    }
    private static boolean roadCell(HeightfieldMap map,PlanRequest req,Layout l,Candidate c,int x,int z,int y) {
        int lo=low(req.roadWidth),hi=high(req.roadWidth);
        if(c!=null && overlap(x+lo,z+lo,x+hi,z+hi,c,req.parcelConfig.roadSetback))return false;
        if(l!=null)for(Candidate p:l.placed)if(overlap(x+lo,z+lo,x+hi,z+hi,p,req.parcelConfig.roadSetback))return false;
        for(int dx=lo;dx<=hi;dx++)for(int dz=lo;dz<=hi;dz++)
            if(!gradeFits(map,x+dx,z+dz,y,req.roadMaxCut,req.roadMaxFill)) return false;
        return true;
    }
    // Filter disconnected banks BEFORE shortlisting: otherwise attractive but unreachable sites
    // can monopolize a small shortlist. Expansions count against the same total search budget.
    private static void terrainReachability(HeightfieldMap map,PlanRequest req,Layout l,SearchStats stats,int end) {
        ArrayDeque<Long> queue=new ArrayDeque<>();Set<Long> seen=new HashSet<>();
        long root=key(l.entry[0],l.entry[2]);queue.add(root);seen.add(root);
        while(!queue.isEmpty() && stats.pathExpanded<end && stats.pathExpanded<stats.pathLimit) {
            long k=queue.remove();stats.pathExpanded++;l.accessibleCenters.add(k);
            for(int[] d:DIRS) {
                int nx=x(k)+d[0],nz=z(k)+d[1];long next=key(nx,nz);
                if(!seen.add(next))continue;
                if(roadCell(map,req,null,null,nx,nz,l.entry[1]))queue.add(next);
            }
        }
    }
    private static List<RoadStep> route(HeightfieldMap map,PlanRequest req,Layout l,Candidate c,SearchStats stats,int end) {
        RoadStep from=c.access.getLast();int y=l.entry[1],w=map.getWidth(),depth=map.getDepth();
        if(!roadCell(map,req,l,c,from.x,from.z,y))return List.of();
        int start=(from.z-map.getMinZ())*w+from.x-map.getMinX();
        int goal=(l.entry[2]-map.getMinZ())*w+l.entry[0]-map.getMinX();
        int[] g=new int[w*depth],parent=new int[w*depth];Arrays.fill(g,Integer.MAX_VALUE);Arrays.fill(parent,-1);
        byte[] allowed=new byte[w*depth];
        PriorityQueue<QueueNode> queue=new PriorityQueue<>(Comparator.comparingInt(QueueNode::f).thenComparingInt(QueueNode::g).thenComparingInt(QueueNode::index));
        g[start]=0;queue.add(new QueueNode(start,0,c.distance));
        while(!queue.isEmpty() && stats.pathExpanded<end && stats.pathExpanded<stats.pathLimit) {
            QueueNode n=queue.remove();if(n.g!=g[n.index])continue;
            stats.pathExpanded++;
            int x=n.index%w+map.getMinX(),z=n.index/w+map.getMinZ();
            if(n.index==goal) {
                List<RoadStep> path=new ArrayList<>();
                for(int i=goal;i!=-1;i=parent[i])path.add(new RoadStep(i%w+map.getMinX(),y,i/w+map.getMinZ(),"surface"));
                Collections.reverse(path);return path;
            }
            for(int[] dir:DIRS) {
                int nx=x+dir[0],nz=z+dir[1];if(!map.inBounds(nx,nz))continue;
                int next=(nz-map.getMinZ())*w+nx-map.getMinX();
                if(n.g+1>=g[next])continue;
                if(allowed[next]==0)allowed[next]=(byte)(roadCell(map,req,l,c,nx,nz,y)?1:-1);
                if(allowed[next]<0)continue;
                g[next]=n.g+1;parent[next]=n.index;
                int h=Math.abs(nx-l.entry[0])+Math.abs(nz-l.entry[2]);
                queue.add(new QueueNode(next,g[next],g[next]+h));
            }
        }
        return List.of();
    }
    private static boolean better(HeightfieldMap map,PlanRequest req,Layout a,Layout b) {
        if(a.placed.size()!=b.placed.size())return a.placed.size()>b.placed.size();
        long typesA=a.placed.stream().map(c->c.demand.id).distinct().count(),typesB=b.placed.stream().map(c->c.demand.id).distinct().count();
        if(typesA!=typesB)return typesA>typesB;
        long soilA=soil(map,columns(map,req,a)),soilB=soil(map,columns(map,req,b));
        if(soilA!=soilB)return soilA<soilB;
        return a.roadCells.size()<b.roadCells.size();
    }
    private static long soil(HeightfieldMap map,Map<Long,GroundColumn> columns) {
        return columns.values().stream().mapToLong(c->Math.abs(c.originalY-c.targetY)).sum();
    }
    private static Map<Long,GroundColumn> columns(HeightfieldMap map,PlanRequest req,Layout l) {
        Map<Long,GroundColumn> columns=new TreeMap<>();int y=l.entry[1];
        for(long k:l.roadCells)columns.put(k,new GroundColumn(x(k),z(k),map.getSurfaceY(x(k),z(k)),y,y+3,"road"));
        for(Candidate c:l.placed) {
            for(RoadStep p:c.access)columns.putIfAbsent(key(p.x,p.z),new GroundColumn(p.x,p.z,map.getSurfaceY(p.x,p.z),y,y+3,"access"));
            for(int x=c.x;x<c.x+c.preset.sizeX;x++)for(int z=c.z;z<c.z+c.preset.sizeZ;z++)
                columns.put(key(x,z),new GroundColumn(x,z,map.getSurfaceY(x,z),y,Math.max(y+c.preset.sizeY-1,map.getSurfaceY(x,z)),"foundation"));
        }
        for(GroundColumn g:columns.values())g.clearToY=Math.max(g.clearToY,g.originalY);
        return columns;
    }
    private static void emit(HeightfieldMap map,PlanRequest req,Layout l,PlanningIR ir) {
        ir.transportNetwork.corridorWidth=req.roadWidth;
        Map<Long,GroundColumn> cols=columns(map,req,l);ir.groundColumns.addAll(cols.values());
        for(GroundColumn c:cols.values()) {
            ir.earthworks.totalCutVolume+=Math.max(0,c.originalY-c.targetY);
            ir.earthworks.totalFillVolume+=Math.max(0,c.targetY-c.originalY);
        }
        ir.earthworks.cutFillBalance=ir.earthworks.totalCutVolume-ir.earthworks.totalFillVolume;
        Set<Long> walk=new TreeSet<>(l.roadCells);
        for(int i=0;i<l.placed.size();i++) {
            Candidate c=l.placed.get(i);BuildingPreset v=c.preset;Plot p=new Plot();
            p.id="plot_"+(i+1);p.requirementId=c.demand.id;p.tags.add(c.demand.requirement.purpose);
            p.polygon2D=List.of(new int[]{c.x,c.z},new int[]{c.x+v.sizeX-1,c.z},new int[]{c.x+v.sizeX-1,c.z+v.sizeZ-1},new int[]{c.x,c.z+v.sizeZ-1});
            p.elevation.baseElevation=l.entry[1];p.elevation.entranceElevation=l.entry[1];
            p.elevation.maxCutDepth=req.parcelConfig.maxCutBudget;p.elevation.maxFillHeight=req.parcelConfig.maxFillBudget;
            p.entrance.accessPoint=new int[]{c.x+v.entrance.x,l.entry[1],c.z+v.entrance.z};p.entrance.facing=v.entrance.facing;
            p.entrance.path=c.access;p.foundation.type="bounded_slab";
            p.builder.generatorType="locked_preset";p.builder.presetId=v.id;p.builder.templateCategory=c.demand.requirement.purpose;
            p.builder.footprintSize=new int[]{v.sizeX,v.sizeZ};p.builder.heightLimit=v.sizeY;p.builder.subSeed=mix(req.seed+i);
            ir.plots.add(p);
            for(RoadStep s:c.access)walk.add(key(s.x,s.z));
            RoadEdge corridor=new RoadEdge();corridor.id="corridor_"+(i+1);corridor.width=req.roadWidth;corridor.steps=l.routes.get(i);
            ir.transportNetwork.corridors.add(corridor);
        }
        // Canonical cell-adjacency graph: no junction may merely float on an unsplit edge.
        for(long k:walk)ir.transportNetwork.nodes.add(new RoadNode(nodeId(k),x(k),l.entry[1],z(k),k==key(l.entry[0],l.entry[2])?"entry":"walkable"));
        Map<Long,String> incident=new HashMap<>();
        for(long k:walk)for(int[] dir:List.of(DIRS[0],DIRS[1])) {
            long n=key(x(k)+dir[0],z(k)+dir[1]);if(!walk.contains(n))continue;
            RoadEdge e=new RoadEdge();e.id="e_"+ir.transportNetwork.edges.size();e.fromNodeId=nodeId(k);e.toNodeId=nodeId(n);
            e.roadType="walkable_adjacency";e.width=1;e.steps=List.of(new RoadStep(x(k),l.entry[1],z(k),"surface"),new RoadStep(x(n),l.entry[1],z(n),"surface"));
            ir.transportNetwork.edges.add(e);incident.putIfAbsent(k,e.id);incident.putIfAbsent(n,e.id);
        }
        for(Plot p:ir.plots)p.entrance.connectedEdgeId=incident.get(key(p.entrance.accessPoint[0],p.entrance.accessPoint[2]));
    }
    private static String nodeId(long k){return "n_"+x(k)+"_"+z(k);}
    private static void stamp(Set<Long> cells,int x,int z,int width){for(int dx=low(width);dx<=high(width);dx++)for(int dz=low(width);dz<=high(width);dz++)cells.add(key(x+dx,z+dz));}
    private static long gcd(long a,long b){while(b!=0){long t=a%b;a=b;b=t;}return a;}
    private static long mix(long v){v=(v^(v>>>30))*0xbf58476d1ce4e5b9L;v=(v^(v>>>27))*0x94d049bb133111ebL;return v^(v>>>31);}
}
