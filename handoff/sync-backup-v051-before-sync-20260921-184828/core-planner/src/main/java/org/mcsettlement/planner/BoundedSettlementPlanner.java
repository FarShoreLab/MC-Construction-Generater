package org.mcsettlement.planner;

import org.mcsettlement.planner.SettlementPlanner.*;
import org.mcsettlement.planner.ir.PlanningIR;
import org.mcsettlement.planner.ir.PlanningIR.*;
import org.mcsettlement.planner.preset.*;
import org.mcsettlement.planner.terrain.HeightfieldMap;
import java.time.Instant;
import java.util.*;
import static org.mcsettlement.planner.RoadGeometry.*;

/** Finite multistart placement, local platforms, and height-state full-footprint routing. */
final class BoundedSettlementPlanner {
    record Demand(BuildingRequirement requirement,String id,List<BuildingShape> variants) {}
    record Candidate(Demand demand,BuildingShape preset,int x,int z,int y,List<RoadStep> access,
                             long preference,int soil,int distance,long tie) {}
    static final Comparator<Candidate> ORDER=Comparator.comparingLong(Candidate::preference)
            .thenComparingInt(c->c.soil+c.distance/2).thenComparingInt(Candidate::distance).thenComparingLong(Candidate::tie)
            .thenComparingInt(Candidate::x).thenComparingInt(Candidate::z).thenComparing(c->c.preset.preset.id)
            .thenComparing(c->c.preset.sourceFacing).thenComparing(c->c.preset.diagonal);
    static final class Layout {
        final int[] entry;
        final List<Candidate> placed=new ArrayList<>();
        final List<List<RoadStep>> routes=new ArrayList<>();
        Set<Long> roadCells=new TreeSet<>();
        Map<Long,GroundColumn> walk=new TreeMap<>();
        final Map<String,String> failure=new HashMap<>();
        final Set<Long> accessibleCenters=new HashSet<>();
        boolean reachabilityComplete;
        int[] distances, waterDistances;
        final Set<Long> plotCells=new HashSet<>();
        Layout(HeightfieldMap m,int[] e,int width){entry=e;
            for(int[] d:stencil(0,0,width)){int x=e[0]+d[0],z=e[2]+d[1];long k=key(x,z);roadCells.add(k);
                walk.put(k,new GroundColumn(x,z,m.getSurfaceY(x,z),e[1],Math.max(m.getSurfaceY(x,z),e[1]+3),"road"));}}
    }
    static PlanningIR plan(HeightfieldMap map,PlanRequest req){
        long started=System.nanoTime();PlanningIR ir=new PlanningIR();ir.metadata.timestamp=Instant.now().toString();
        if(map!=null){ir.metadata.minBounds=new int[]{map.getMinX(),-2048,map.getMinZ()};ir.metadata.maxBounds=new int[]{map.getMinX()+map.getWidth()-1,2048,map.getMinZ()+map.getDepth()-1};}
        String error=validate(map,req);if(error!=null){ir.status="INVALID_REQUEST";ir.auditLog.warnings.add(error);ir.search.elapsedNanos=System.nanoTime()-started;return ir;}
        ir.metadata.randomSeed=req.seed;SearchStats st=ir.search;
        st.candidateLimit=req.searchBudget.candidateChecks;st.pathLimit=req.searchBudget.pathExpanded;
        st.stateLimit=req.searchBudget.pathStates;st.gradeLimit=req.searchBudget.gradeRelaxations;
        st.columnLimit=req.searchBudget.groundColumns;st.editLimit=req.searchBudget.constructionEdits;
        List<Demand> demands=demands(req);List<int[]> gates=gates(map,req);
        if(demands.isEmpty()){ir.status="COMPLETE";ir.metadata.score.put("demand_satisfaction",1.0);st.elapsedNanos=System.nanoTime()-started;return ir;}
        Layout best=null;int[] water=waterDistances(map);
        int placementCandidateLimit=req.organicRoads?st.candidateLimit-Math.min(1920,st.candidateLimit/6):st.candidateLimit;
        int placementPathLimit=req.organicRoads?Math.max(1,st.pathLimit*3/5):st.pathLimit;
        int attempts=gates.isEmpty()?0:Math.min(req.searchBudget.attempts,req.entry==null?gates.size():req.searchBudget.attempts);
        for(int attempt=0;attempt<attempts;attempt++){
            if(st.candidateChecks>=st.candidateLimit||st.pathExpanded>=st.pathLimit||st.gradeRelaxations>=st.gradeLimit)break;
            Layout l=new Layout(map,gates.get(attempt%gates.size()),req.roadWidth);l.waterDistances=water;st.attempts++;
            int remaining=demands.stream().mapToInt(d->d.requirement.count).sum();
            int candidateEnd=st.candidateChecks+(placementCandidateLimit-st.candidateChecks)/(attempts-attempt);
            int pathEnd=st.pathExpanded+Math.max(0,(placementPathLimit-st.pathExpanded)/(attempts-attempt));
            terrainReachability(map,req,l,st,Math.min(pathEnd,st.pathExpanded+Math.min(12000,Math.max(1,(pathEnd-st.pathExpanded)/8))));
            l.distances=distanceField(map,l.roadCells);
            allocation: for(Demand d:orderDemands(demands,req.seed+attempt))for(int slot=0;slot<d.requirement.count;slot++){
                int allowance=Math.max(0,(candidateEnd-st.candidateChecks)/Math.max(1,remaining));
                int pathAllowance=Math.max(0,(pathEnd-st.pathExpanded)/Math.max(1,remaining));
                if(req.organicRoads&&l.placed.isEmpty()&&remaining>=3)pathAllowance=Math.min(pathEnd-st.pathExpanded,pathAllowance*3);
                int slotEnd=st.pathExpanded+pathAllowance;remaining--;
                if(d.variants.isEmpty()){l.failure.put(d.id,"NO_PRESET_MATCHES_PURPOSE_SIZE_HEIGHT");continue;}
                if(d.requirement.nearPurpose!=null&&l.placed.stream().noneMatch(p->p.demand.requirement.purpose.equals(d.requirement.nearPurpose))){l.failure.put(d.id,"SPATIAL_REFERENCE_NOT_PLACED_OR_CYCLIC");continue;}
                long salt=mix(req.seed^((long)attempt<<40)^((long)d.id.hashCode()<<8)^slot);
                List<Candidate> cs=candidates(map,req,l,d,allowance,st,salt);boolean placed=false;
                for(int ci=0;ci<cs.size();ci++){
                    Candidate c=cs.get(ci);Set<Long> forbidden=blocked(l,c,req.parcelConfig.roadSetback);
                    List<List<RoadStep>> accesses=new ArrayList<>();for(Candidate old:l.placed)accesses.add(old.access);accesses.add(c.access);
                    int routeEnd=req.organicRoads&&l.placed.isEmpty()?slotEnd:st.pathExpanded+Math.max(1,(slotEnd-st.pathExpanded)/Math.max(1,Math.min(3,cs.size()-ci)));
                    TerrainRoadRouter.Result r=TerrainRoadRouter.route(map,req,forbidden,l.roadCells,l.walk,c.access,accesses,l.routes,l.entry,l.distances,st,Math.min(slotEnd,routeEnd));
                    if(r==null)continue;
                    List<Candidate> proposed=new ArrayList<>(l.placed);proposed.add(c);
                    Map<Long,GroundColumn> cols=columns(map,r.walk(),proposed);
                    if(cols.size()>st.columnLimit){exhausted(st,"GROUND_COLUMNS");continue;}
                    if(editCount(cols)>st.editLimit){exhausted(st,"CONSTRUCTION_EDITS");continue;}
                    l.placed.add(c);stampPlot(l.plotCells,c,0);l.routes.add(r.centers());l.roadCells=r.roadCells();l.walk=r.walk();
                    l.distances=distanceField(map,l.roadCells);placed=true;break;
                }
                if(!placed)l.failure.put(d.id,cs.isEmpty()?"NO_FEASIBLE_CANDIDATE_IN_BUDGET":st.pathExpanded>=slotEnd?"PATH_SEARCH_BUDGET_EXHAUSTED":"NO_CONSTRUCTIBLE_FULL_WIDTH_HEIGHT_ROUTE_IN_SHORTLIST");
                // Do not spend an entire gate's budget on houses after its mandatory central anchor failed.
                // Later attempts retain the full partial-layout fallback and the same hard resource limits.
                if(!placed&&req.organicRoads&&l.placed.isEmpty()&&attempt+1<attempts&&"center".equals(d.requirement.placement))break allocation;
            }
            if(best==null||better(map,l,best))best=l;
            // A complete feasibility witness is enough; reserve work for the semantic reroute.
            if(req.organicRoads && l.placed.size()==demands.stream().mapToInt(d->d.requirement.count).sum())break;
        }
        if(st.candidateChecks>=st.candidateLimit)exhausted(st,"CANDIDATE_CHECKS");
        if(st.pathExpanded>=st.pathLimit)exhausted(st,"PATH_EXPANSIONS");
        if(best!=null&&!best.placed.isEmpty()) {
            emit(map,req,best,ir);
            if(req.organicRoads)OrganicRoadNetwork.rebuild(map,req,ir);
        }
        int requested=0;for(Demand d:demands){requested+=d.requirement.count;int n=(int)ir.plots.stream().filter(p->d.id.equals(p.requirementId)).count();
            if(n<d.requirement.count){UnmetRequirement u=new UnmetRequirement();u.requirementId=d.id;u.purpose=d.requirement.purpose;u.requested=d.requirement.count;u.allocated=n;
                u.reason=best==null?"NO_VALID_ENTRY_FULL_WIDTH_OR_GRADE":best.failure.getOrDefault(d.id,"SEARCH_BUDGET_EXHAUSTED");ir.unmetRequirements.add(u);}}
        if(!req.requirements.isEmpty()&&req.targetPlots!=requested)ir.auditLog.warnings.add("Explicit requirements are authoritative; targetPlots="+req.targetPlots+" differs from total="+requested);
        ir.status=ir.unmetRequirements.isEmpty()?"COMPLETE":ir.plots.isEmpty()?"INFEASIBLE":"PARTIAL";
        ir.metadata.score.put("demand_satisfaction",ir.plots.size()/(double)requested);ir.metadata.score.put("plots_allocated",(double)ir.plots.size());
        if(!ir.unmetRequirements.isEmpty())ir.auditLog.warnings.add("Bounded search / stair-orientation failure is not a proof of global infeasibility.");
        st.elapsedNanos=System.nanoTime()-started;return ir;
    }
    static String validate(HeightfieldMap m, PlanRequest r) {
        if (m==null || r==null || r.parcelConfig==null || r.searchBudget==null || r.requirements==null) return "NULL_INPUT";
        if (m.getWidth()<1 || m.getDepth()<1 || m.getWidth()>512 || m.getDepth()>512) return "MAP_DIMENSIONS_MUST_BE_1_TO_512";
        if(!PresetPalette.NAMES.contains(String.valueOf(r.presetPalette)))return "INVALID_PRESET_PALETTE";
        if("single".equals(r.presetPalette)&&(r.singlePresetId==null||BuildingPresetRegistry.getInstance().getPreset(r.singlePresetId)==null))return "UNKNOWN_SINGLE_PRESET";
        if (r.roadDirections!=8 && r.roadDirections!=12) return "ROAD_DIRECTIONS_MUST_BE_8_OR_12";
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
                b.candidateStride<1 || b.candidateStride>16 || b.pathStates<1 || b.pathStates>200000 ||
                b.gradeRelaxations<1 || b.gradeRelaxations>10000000 || b.groundColumns<1 || b.groundColumns>100000 ||
                b.constructionEdits<1 || b.constructionEdits>1000000) return "INVALID_SEARCH_BUDGET";
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

    static List<Demand> demands(PlanRequest req) {
        List<BuildingRequirement> requirements=new ArrayList<>(req.requirements);
        if(requirements.isEmpty()&&!"classic".equals(req.presetPalette))
            requirements.addAll(PresetPalette.requirements(req.presetPalette,req.singlePresetId,req.targetPlots));
        if (requirements.isEmpty()) {
            if (req.targetPlots>0) {
                BuildingRequirement q=new BuildingRequirement(); q.id="landmark";q.purpose="government";
                q.placement=req.landmarkPlacement;requirements.add(q);
            }
            if (req.targetPlots>1) { BuildingRequirement q=new BuildingRequirement();q.id="workshop";q.purpose="workshop";requirements.add(q); }
            if (req.targetPlots>2) { BuildingRequirement q=new BuildingRequirement();q.id="housing";q.count=req.targetPlots-2;requirements.add(q); }
        }
        if (req.requirements.isEmpty()&&"classic".equals(req.presetPalette)) for (BuildingRequirement q : requirements) {
            // Legacy desired dimensions become lower bounds; no preset is ever clipped to fit them.
            q.minWidth=req.parcelConfig.defaultWidth;q.maxWidth=Math.max(24,q.minWidth);
            q.minDepth=req.parcelConfig.defaultDepth;q.maxDepth=Math.max(24,q.minDepth);
        }
        List<Demand> result=new ArrayList<>();
        for (int i=0;i<requirements.size();i++) {
            BuildingRequirement q=requirements.get(i);
            List<BuildingShape> variants=new ArrayList<>();
            List<BuildingPreset> presets=BuildingPresetRegistry.getInstance().getAllPresets();
            presets.sort(Comparator.comparing(p->p.id));
            for (BuildingPreset p : presets) {
                if("classic".equals(req.presetPalette)&&q.presetId==null&&!BuildingPresetRegistry.CLASSIC_IDS.contains(p.id))continue;
                if (q.presetId!=null && !q.presetId.equals(p.id)) continue;
                if (!q.purpose.equals(p.category) && !p.tags.contains(q.purpose) &&
                        !(q.purpose.equals("landmark") && Set.of("government","military","culture").contains(p.category))) continue;
                for (String facing : List.of("NORTH","EAST","SOUTH","WEST")) {
                    BuildingPreset v=p.rotateToFacing(facing);
                    for(boolean diagonal : req.diagonalBuildings?new boolean[]{false,true}:new boolean[]{false}) {
                        try {
                            BuildingShape shape=new BuildingShape(v,diagonal);
                            if(shape.sizeX>=q.minWidth && shape.sizeX<=q.maxWidth && shape.sizeZ>=q.minDepth && shape.sizeZ<=q.maxDepth && shape.sizeY<=q.heightLimit) {
                                shape.grid(null); variants.add(shape);
                            }
                        } catch(IllegalArgumentException ignored) { /* Rejected, never clipped or substituted. */ }
                    }
                }
            }
            result.add(new Demand(q,q.id==null?"requirement_"+i:q.id,variants));
        }
        return result;
    }
    static List<Demand> orderDemands(List<Demand> input,long seed) {
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
    static List<int[]> gates(HeightfieldMap m,PlanRequest r){
        if(r.entry!=null){int[] e=r.entry;if(e[1]!=m.getSurfaceY(e[0],e[2])||!entryFits(m,r,e[0],e[2],e[1]))return List.of();return Collections.singletonList(e.clone());}
        List<int[]> out=new ArrayList<>();int ax=m.getMinX()-low(r.roadWidth),az=m.getMinZ()-low(r.roadWidth),bx=m.getMinX()+m.getWidth()-1-high(r.roadWidth),bz=m.getMinZ()+m.getDepth()-1-high(r.roadWidth);
        for(int x=ax;x<=bx;x+=2){addGate(out,m,r,x,az);if(bz!=az)addGate(out,m,r,x,bz);}
        for(int z=az+1;z<bz;z+=2){addGate(out,m,r,ax,z);if(bx!=ax)addGate(out,m,r,bx,z);}
        out.sort(Comparator.<int[]>comparingLong(e->mix(r.seed^key(e[0],e[2]))).thenComparingInt(e->e[0]).thenComparingInt(e->e[2]));return out;
    }
    static boolean entryFits(HeightfieldMap m,PlanRequest r,int x,int z,int y){for(int[] d:stencil(0,0,r.roadWidth))if(!gradeFits(m,x+d[0],z+d[1],y,r.roadMaxCut,r.roadMaxFill))return false;return true;}
    static void addGate(List<int[]> out,HeightfieldMap m,PlanRequest r,int x,int z){if(m.inBounds(x,z)&&entryFits(m,r,x,z,m.getSurfaceY(x,z)))out.add(new int[]{x,m.getSurfaceY(x,z),z});}
    static void terrainReachability(HeightfieldMap m,PlanRequest r,Layout l,SearchStats st,int end){
        ArrayDeque<Long> q=new ArrayDeque<>();Set<Long> seen=new HashSet<>();long root=key(l.entry[0],l.entry[2]);q.add(root);seen.add(root);
        while(!q.isEmpty()&&st.pathExpanded<end){long k=q.remove();st.pathExpanded++;st.prefilterExpanded++;l.accessibleCenters.add(k);
            for(int[] d:CARDINAL){int x=x(k)+d[0],z=z(k)+d[1];long next=key(x,z);if(!m.inBounds(x,z)||!seen.add(next))continue;
                boolean allowed=true;for(int[] b:stencil(0,0,r.roadWidth))if(!buildable(m,x+b[0],z+b[1])){allowed=false;break;}if(allowed)q.add(next);}}
        l.reachabilityComplete=q.isEmpty(); // Unknown cells are NOT rejected when the prefilter budget ends.
    }
    static Set<Long> blocked(Layout l,Candidate extra,int spacing){Set<Long> out=new HashSet<>();for(Candidate c:l.placed)stampPlot(out,c,spacing);if(extra!=null)stampPlot(out,extra,spacing);return out;}
    static void stampPlot(Set<Long> out,Candidate c,int spacing){for(int[] p:c.preset.cells)for(int dx=-spacing;dx<=spacing;dx++)for(int dz=-spacing;dz<=spacing;dz++)out.add(key(c.x+p[0]+dx,c.z+p[1]+dz));}
    static List<Candidate> candidates(HeightfieldMap m,PlanRequest r,Layout l,Demand d,int allowance,SearchStats st,long salt){
        List<Candidate> top=new ArrayList<>();Set<Long> occupied=blocked(l,null,r.parcelConfig.minPlotSpacing);Set<Long> roadNoPlot=new HashSet<>();
        for(long k:l.roadCells)for(int dx=-r.parcelConfig.roadSetback;dx<=r.parcelConfig.roadSetback;dx++)for(int dz=-r.parcelConfig.roadSetback;dz<=r.parcelConfig.roadSetback;dz++)roadNoPlot.add(key(x(k)+dx,z(k)+dz));
        int stride=r.searchBudget.candidateStride,nx=(m.getWidth()+stride-1)/stride,nz=(m.getDepth()+stride-1)/stride;
        long total=(long)nx*nz*d.variants.size(),start=Math.floorMod(salt,total),jump=Math.floorMod(mix(salt),total)|1;while(gcd(jump,total)!=1)jump+=2;
        int count=(int)Math.min(allowance,total);for(int i=0;i<count&&st.candidateChecks<st.candidateLimit;i++){
            st.candidateChecks++;long index=(start+i*jump)%total;BuildingShape v=d.variants.get((int)(index%d.variants.size()));long cell=index/d.variants.size();
            int x=m.getMinX()+(int)(cell%nx)*stride,z=m.getMinZ()+(int)(cell/nx)*stride;
            Candidate c=candidate(m,r,l,d,v,x,z,mix(salt+i),occupied,roadNoPlot);if(c==null)continue;top.add(c);top.sort(ORDER);if(top.size()>r.searchBudget.shortlist)top.removeLast();}
        return top;
    }
    static Candidate candidate(HeightfieldMap m,PlanRequest r,Layout l,Demand d,BuildingShape v,int x,int z,long tie,Set<Long> occupied,Set<Long> roadNoPlot){
        if(!m.inBounds(x,z)||!m.inBounds(x+v.sizeX-1,z+v.sizeZ-1))return null;
        int lo=-2032,hi=2032,index=0;int[] hs=new int[v.cells.size()];
        for(int[] p:v.cells){int xx=x+p[0],zz=z+p[1];long k=key(xx,zz);
            if(occupied.contains(k)||roadNoPlot.contains(k)||l.walk.containsKey(k)||!buildable(m,xx,zz)||m.getSlope(xx,zz)>r.parcelConfig.maxGroundSlope)return null;
            int h=m.getSurfaceY(xx,zz);hs[index++]=h;lo=Math.max(lo,h-r.parcelConfig.maxCutBudget);hi=Math.min(hi,h+r.parcelConfig.maxFillBudget);}
        if(lo>hi)return null;Arrays.sort(hs);int median=Math.max(lo,Math.min(hi,hs[hs.length/2]));
        BuildingRequirement q=d.requirement;int water="riverbank".equals(q.placement)?waterDistance(m,l.waterDistances,v,x,z,q.maxWaterDistance):0;if(water>q.maxWaterDistance)return null;
        if(q.nearPurpose!=null&&l.placed.stream().filter(p->q.nearPurpose.equals(p.demand.requirement.purpose)).noneMatch(p->Math.abs(2*x+v.sizeX-1-(2*p.x+p.preset.sizeX-1))+Math.abs(2*z+v.sizeZ-1-(2*p.z+p.preset.sizeZ-1))<=2*q.maxDistance))return null;
        long pref=switch(q.placement){case "center"->Math.abs(2*x+v.sizeX-1-(2*m.getMinX()+m.getWidth()-1))+Math.abs(2*z+v.sizeZ-1-(2*m.getMinZ()+m.getDepth()-1));case "ridge"->-m.getSurfaceY(x+v.sizeX/2,z+v.sizeZ/2);case "valley"->m.getSurfaceY(x+v.sizeX/2,z+v.sizeZ/2);case "riverbank"->water;default->0;};
        for(int delta=0;delta<=hi-lo;delta++)for(int sign:delta==0?new int[]{1}:new int[]{-1,1}){int y=median+delta*sign;if(y<lo||y>hi)continue;
            List<RoadStep> access=access(m,r,l,v,x,z,y);if(access==null)continue;RoadStep end=access.getLast();
            if(l.reachabilityComplete&&!l.accessibleCenters.contains(key(end.x,end.z)))return null;
            int soil=0;for(int h:hs)soil+=Math.abs(h-y);for(RoadStep s:access)soil+=Math.abs(m.getSurfaceY(s.x,s.z)-s.y);
            int distance=l.distances[(end.z-m.getMinZ())*m.getWidth()+end.x-m.getMinX()]/10;
            return new Candidate(d,v,x,z,y,access,pref,soil,distance,tie);}
        return null;
    }
    record AccessNode(int y,int cost,AccessNode parent) {}
    static List<RoadStep> access(HeightfieldMap m,PlanRequest r,Layout l,BuildingShape v,int x,int z,int y){
        int[] dir=PlannedBuilding.direction(v.facing);List<int[]> cells=new ArrayList<>();Set<Long> others=l.plotCells;boolean exit=false;
        for(int i=0;i<64;i++){int xx=x+v.entranceX+i*dir[0],zz=z+v.entranceZ+i*dir[1];
            if(!RoadTerrain.allowed(m,r,xx,zz)||others.contains(key(xx,zz)))return null;cells.add(new int[]{xx,zz});
            int lo=low(r.roadWidth)-r.parcelConfig.roadSetback,hi=high(r.roadWidth)+r.parcelConfig.roadSetback;boolean clear=i>=3&&!v.intersects(xx-x+lo,zz-z+lo,xx-x+hi,zz-z+hi);if(clear){exit=true;break;}}
        if(!exit)return null;
        Map<Integer,AccessNode> prev=new TreeMap<>();prev.put(y,new AccessNode(y,0,null));
        for(int i=1;i<cells.size();i++){int[] p=cells.get(i);int h=r.expert==null?m.getSurfaceY(p[0],p[1]):RoadTerrain.natural(m,p[0],p[1]);GroundColumn existing=l.walk.get(key(p[0],p[1]));Map<Integer,AccessNode> next=new TreeMap<>();
            for(AccessNode old:prev.values())for(int rise:new int[]{0,-1,1}){int yy=old.y+rise;if(r.expert!=null&&i>=cells.size()-2&&rise!=0)continue;if((i<=2||v.contains(p[0]-x,p[1]-z))&&yy!=y||!RoadTerrain.fits(m,r,p[0],p[1],yy)||existing!=null&&existing.targetY!=yy)continue;
                // A one-cell valley needs a landing; do not flip a stair's high exit immediately.
                if(old.parent!=null&&(old.y-old.parent.y)*rise<0)continue;
                int cost=old.cost+Math.abs(h-yy)*3+Math.abs(rise);AccessNode current=next.get(yy);if(current==null||cost<current.cost)next.put(yy,new AccessNode(yy,cost,old));}
            if(next.isEmpty())return null;prev=next;}
        AccessNode end=prev.values().stream().min(Comparator.comparingInt(AccessNode::cost).thenComparingInt(AccessNode::y)).orElseThrow();
        List<RoadStep> out=new ArrayList<>();for(int i=cells.size()-1;i>=0;i--){int[] p=cells.get(i);out.add(new RoadStep(p[0],end.y,p[1],"surface"));end=end.parent;}Collections.reverse(out);return out;
    }
    static int[] waterDistances(HeightfieldMap m){
        int w=m.getWidth(),depth=m.getDepth();int[] ds=new int[w*depth];Arrays.fill(ds,1000000);ArrayDeque<Integer> q=new ArrayDeque<>();
        for(int z=0;z<depth;z++)for(int x=0;x<w;x++){var o=m.getLocalObstacle(x,z);if(o==HeightfieldMap.ObstacleType.WATER||o==HeightfieldMap.ObstacleType.WATER_DEEP){ds[z*w+x]=0;q.add(z*w+x);}}
        while(!q.isEmpty()){int i=q.remove(),x=i%w,z=i/w;for(int[] d:CARDINAL){int xx=x+d[0],zz=z+d[1];if(xx>=0&&xx<w&&zz>=0&&zz<depth&&ds[zz*w+xx]>ds[i]+1){ds[zz*w+xx]=ds[i]+1;q.add(zz*w+xx);}}}return ds;
    }
    static int waterDistance(HeightfieldMap m,int[] distances,BuildingShape v,int x,int z,int limit){int best=limit+1;for(int[] p:v.cells)best=Math.min(best,distances[(z+p[1]-m.getMinZ())*m.getWidth()+x+p[0]-m.getMinX()]);return best;}
    static Map<Long,GroundColumn> columns(HeightfieldMap m,Map<Long,GroundColumn> walk,List<Candidate> placed){
        Map<Long,GroundColumn> cols=new TreeMap<>();for(var e:walk.entrySet())cols.put(e.getKey(),copy(e.getValue()));
        for(Candidate c:placed)for(int[] p:c.preset.cells){int x=c.x+p[0],z=c.z+p[1],h=m.getSurfaceY(x,z);GroundColumn f=new GroundColumn(x,z,h,c.y,Math.max(h,c.y+c.preset.sizeY-1),"foundation"); RoadTerrain.markWet(m,f); cols.put(key(x,z),f);}return cols;
    }
    static int editCount(Map<Long,GroundColumn> cols){long n=0;for(GroundColumn c:cols.values())n+=org.mcsettlement.planner.civil.PlanConstruction.columnEditCount(c);return (int)Math.min(Integer.MAX_VALUE,n);}
    static boolean better(HeightfieldMap m,Layout a,Layout b){if(a.placed.size()!=b.placed.size())return a.placed.size()>b.placed.size();long ta=a.placed.stream().map(c->c.demand.id).distinct().count(),tb=b.placed.stream().map(c->c.demand.id).distinct().count();if(ta!=tb)return ta>tb;
        long sa=soil(columns(m,a.walk,a.placed)),sb=soil(columns(m,b.walk,b.placed));return sa!=sb?sa<sb:a.roadCells.size()<b.roadCells.size();}
    static long soil(Map<Long,GroundColumn> c){return c.values().stream().mapToLong(v->Math.abs(v.targetY-v.originalY)).sum();}
    static void emit(HeightfieldMap m,PlanRequest r,Layout l,PlanningIR ir){
        ir.transportNetwork.corridorWidth=r.roadWidth;ir.transportNetwork.directionCount=r.roadDirections;Map<Long,GroundColumn> cols=columns(m,l.walk,l.placed);ir.groundColumns.addAll(cols.values());ir.search.constructionEdits=editCount(cols);
        for(GroundColumn c:cols.values()){ir.earthworks.totalCutVolume+=Math.max(0,c.originalY-c.targetY);ir.earthworks.totalFillVolume+=RoadTerrain.raised(c)?0:Math.max(0,c.targetY-c.originalY);}
        ir.earthworks.cutFillBalance=ir.earthworks.totalCutVolume-ir.earthworks.totalFillVolume;
        for(int i=0;i<l.placed.size();i++){Candidate c=l.placed.get(i);BuildingShape v=c.preset;Plot p=new Plot();p.id="plot_"+(i+1);p.requirementId=c.demand.id;p.tags.add(c.demand.requirement.purpose);
            p.origin2D=new int[]{c.x,c.z};p.footprint=v.cells;for(int[] point:v.hull)p.polygon2D.add(new int[]{c.x+point[0],c.z+point[1]});
            p.elevation.baseElevation=c.y;p.elevation.entranceElevation=c.y;p.elevation.maxCutDepth=r.parcelConfig.maxCutBudget;p.elevation.maxFillHeight=r.parcelConfig.maxFillBudget;
            p.entrance.accessPoint=new int[]{c.x+v.entranceX,c.y,c.z+v.entranceZ};p.entrance.facing=v.facing;p.entrance.path=canonical(c.access,cols);p.foundation.type="bounded_slab";
            p.builder.footprintShape=v.preset.footprintShape;p.builder.sizeTier=v.preset.sizeTier;p.builder.footprintArea=v.cells.size();
            p.builder.generatorType="locked_preset";p.builder.presetId=v.preset.id;p.builder.templateCategory=c.demand.requirement.purpose;p.builder.footprintSize=new int[]{v.sizeX,v.sizeZ};p.builder.heightLimit=v.sizeY;p.builder.subSeed=mix(r.seed+i);p.builder.diagonal45=v.diagonal;p.builder.sourceFacing=v.sourceFacing;ir.plots.add(p);
            RoadEdge corridor=new RoadEdge();corridor.id="corridor_"+(i+1);corridor.width=r.roadWidth;corridor.steps=canonical(l.routes.get(i),cols);ir.transportNetwork.corridors.add(corridor);}
        Set<Long> walk=new TreeSet<>(l.walk.keySet());Map<Long,String> incident=new HashMap<>();
        for(long k:walk){GroundColumn c=cols.get(k);ir.transportNetwork.nodes.add(new RoadNode(nodeId(k),c.x,c.targetY,c.z,k==key(l.entry[0],l.entry[2])?"entry":"walkable"));}
        for(long k:walk)for(int[] d:List.of(CARDINAL[0],CARDINAL[1])){long next=key(x(k)+d[0],z(k)+d[1]);if(!walk.contains(next)||!canWalk(cols.get(k),cols.get(next)))continue;
            RoadEdge e=new RoadEdge();e.id="e_"+ir.transportNetwork.edges.size();e.fromNodeId=nodeId(k);e.toNodeId=nodeId(next);e.roadType="walkable_adjacency";e.width=1;e.steps=List.of(step(cols.get(k)),step(cols.get(next)));ir.transportNetwork.edges.add(e);incident.putIfAbsent(k,e.id);incident.putIfAbsent(next,e.id);}
        for(Plot p:ir.plots)p.entrance.connectedEdgeId=incident.get(key(p.entrance.accessPoint[0],p.entrance.accessPoint[2]));
    }
    static List<RoadStep> canonical(List<RoadStep> path,Map<Long,GroundColumn> cols){List<RoadStep> out=new ArrayList<>();for(RoadStep s:path)out.add(step(cols.get(key(s.x,s.z))));return out;}
    static String nodeId(long k){return "n_"+x(k)+"_"+z(k);}
    static long gcd(long a,long b){while(b!=0){long t=a%b;a=b;b=t;}return a;}
    static long mix(long v){v=(v^(v>>>30))*0xbf58476d1ce4e5b9L;v=(v^(v>>>27))*0x94d049bb133111ebL;return v^(v>>>31);}
}
