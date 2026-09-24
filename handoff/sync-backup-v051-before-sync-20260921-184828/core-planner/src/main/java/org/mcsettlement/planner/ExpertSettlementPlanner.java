package org.mcsettlement.planner;

import java.time.Instant;
import java.util.*;
import org.mcsettlement.planner.SettlementPlanner.*;
import org.mcsettlement.planner.BoundedSettlementPlanner.*;
import org.mcsettlement.planner.ir.PlanningIR;
import org.mcsettlement.planner.ir.PlanningIR.*;
import org.mcsettlement.planner.preset.*;
import org.mcsettlement.planner.terrain.HeightfieldMap;
import org.mcsettlement.planner.civil.PlanConstruction;
import static org.mcsettlement.planner.RoadGeometry.*;

/** Terrain-aware bounded site selection BEFORE routing. Failed pins/coverage never become COMPLETE.
 * Reuses the same preset raster, access-height DP, height-state router and construction manifest.
 * Three macro alternatives, deterministic stratified candidates, shared original global budgets.
 */
final class ExpertSettlementPlanner {
    private record Choice(Layout layout,SitePlanning report,double score) {}
    private record Link(int from,int to,double distance) {}
    private record Extra(String id,String type,List<RoadStep> path) {}
    private record Evaluation(Candidate candidate,double score,double terrain,double anchor,int water) {}
    private ExpertSettlementPlanner() {}

    static PlanningIR plan(HeightfieldMap map,PlanRequest r) {
        long started=System.nanoTime();PlanningIR ir=new PlanningIR();ir.sitePlanning=new SitePlanning();
        ir.metadata.timestamp=Instant.now().toString();ir.metadata.version=PlanningIR.EXPERT_SCHEMA_VERSION;
        String invalid=BoundedSettlementPlanner.validate(map,r);
        if(invalid==null)try {r.expert.validate(map.getWidth(),map.getDepth(),map.getMinX(),map.getMinZ(),r.targetPlots);
            if(!r.requirements.isEmpty()&&r.expert.pins.stream().anyMatch(p->"building".equals(p.kind)))invalid="EXPLICIT_REQUIREMENTS_AND_PIN_BUILDINGS_MUST_NOT_BE_MIXED";
        } catch(IllegalArgumentException ex){invalid=ex.getMessage();}
        if(invalid!=null){ir.status="INVALID_REQUEST";ir.sitePlanning.status=ir.status;ir.auditLog.warnings.add(invalid);ir.search.elapsedNanos=System.nanoTime()-started;return ir;}
        ir.metadata.randomSeed=r.seed;ir.metadata.minBounds=new int[]{map.getMinX(),-2048,map.getMinZ()};ir.metadata.maxBounds=new int[]{map.getMinX()+map.getWidth()-1,2048,map.getMinZ()+map.getDepth()-1};
        SearchStats st=ir.search;st.candidateLimit=r.searchBudget.candidateChecks;st.pathLimit=r.searchBudget.pathExpanded;st.stateLimit=r.searchBudget.pathStates;
        st.gradeLimit=r.searchBudget.gradeRelaxations;st.columnLimit=r.searchBudget.groundColumns;st.editLimit=r.searchBudget.constructionEdits;
        List<int[]> gates=BoundedSettlementPlanner.gates(map,r);
        if(gates.isEmpty())return reject(ir,"NO_VALID_FULL_WIDTH_ENTRY",started);
        int[] water=BoundedSettlementPlanner.waterDistances(map);st.terrainCellsAnalyzed=map.getWidth()*map.getDepth();
        List<Demand> demands=demands(r);Map<String,ExpertSettings.Pin> pins=new LinkedHashMap<>();for(var p:r.expert.pins)pins.put(p.id,p);
        List<Choice> choices=new ArrayList<>();List<String> attempts=new ArrayList<>();int count=r.expert.pins.stream().filter(p->"building".equals(p.kind)).count()==demands.size()?1:Math.min(3,r.searchBudget.attempts);
        for(int a=0;a<count&&st.candidateChecks<st.candidateLimit;a++) {
            st.siteLayoutsTried++;st.attempts++;Layout l=new Layout(map,gates.getFirst(),r.roadWidth);l.waterDistances=water;l.distances=distanceField(map,l.roadCells);
            SitePlanning report=new SitePlanning();boolean fail=false;double sum=0;
            for(int i=0;i<demands.size();i++) {
                Demand d=demands.get(i);ExpertSettings.Pin pin=pins.get(d.id());
                int remaining=(count-a)*demands.size()-i,quota=pin!=null?Math.max(1,d.variants().size()):Math.max(1,(st.candidateLimit-st.candidateChecks)/Math.max(1,remaining));
                int cluster=i==0&&pin==null&&demands.size()>=4? -1:Math.floorMod(i-(demands.size()>=4?1:0),3);
                double theta=(a*2*Math.PI/9)+unit(mix(r.seed))*Math.PI/7;
                double angle=cluster*2*Math.PI/3-Math.PI/2+theta;
                double radius=.36+(a==2?.035:0);
                double tx=cluster<0?.5:.5+radius*Math.cos(angle),tz=cluster<0?.5:.5+radius*Math.sin(angle);
                Evaluation selected=select(map,r,l,d,pin,tx,tz,quota,st,mix(r.seed^((long)a<<36)^d.id().hashCode()));
                if(selected==null){report.reasons.add((pin==null?"SITE_NOT_FOUND: ":"PIN_CANNOT_FIT_EXACT_POSITION: ")+d.id());fail=true;break;}
                Candidate c=selected.candidate;l.placed.add(c);BoundedSettlementPlanner.stampPlot(l.plotCells,c,0);sum+=selected.score;
                SiteDecision decision=new SiteDecision();decision.id=d.id();decision.plotId="plot_"+l.placed.size();decision.role=role(d);decision.presetId=c.preset().preset.id;
                decision.medium=pin==null?"land":pin.medium;decision.x=c.x();decision.z=c.z();decision.y=c.y();decision.pinned=pin!=null;decision.cluster=cluster;
                decision.score=selected.score;decision.terrainCost=selected.terrain;decision.anchorDistance=selected.anchor;decision.shoreDistance=selected.water;
                decision.rules.add(pin==null?"REGIONAL_ANCHOR_BEFORE_ROADS":"EXACT_ORIGIN_FACING_AND_OPTIONAL_Y_LOCK");
                decision.rules.add("FULL_PRESET_FOOTPRINT_AND_ACCESS_RESERVED");decision.rules.add("LOCAL_PLATFORM_MIN_CUT_FILL");
                if("trade".equals(decision.role))decision.rules.add("SHORE_ACCESS_SOFT_PREFERENCE");
                if("highland".equals(decision.role))decision.rules.add("HIGH_GROUND_SOFT_PREFERENCE");report.decisions.add(decision);
            }
            SiteMetrics.measure(map,metricPlots(l.placed),r.expert,report);
            report.reasons.addAll(SiteMetrics.failures(l.placed.size(),report));
            attempts.add("sites_"+a+": buildings="+l.placed.size()+" bbox="+round(report.bboxCoverage)+" minor="+round(report.minorAxisRatio)+" hull="+round(report.hullCoverage)+" reasons="+report.reasons);
            if(ir.sitePlanning.decisions.isEmpty()||report.decisions.size()>ir.sitePlanning.decisions.size()||report.coveragePenalty<ir.sitePlanning.coveragePenalty)ir.sitePlanning=report;
            if(!fail&&report.reasons.isEmpty())choices.add(new Choice(l,report,sum));
        }
        choices.sort(Comparator.comparingDouble(Choice::score));
        if(choices.isEmpty()){ir.sitePlanning.attempts=attempts;return reject(ir,ir.sitePlanning.reasons.stream().anyMatch(s->s.startsWith("PIN_"))?"PIN_CONSTRAINT_FAILED":"LAYOUT_QUALITY_REJECTED",started);}
        for(int index=0;index<choices.size();index++) {
            Choice choice=choices.get(index);ir.sitePlanning=choice.report;ir.sitePlanning.attempts=attempts;st.siteLayoutsRouted++;
            Layout sites=choice.layout;
            int[] gate=chooseGate(map,r,gates,sites);
            if(gate==null){attempts.add("routing_"+index+": NO_ENTRY_OUTSIDE_RESERVED_SITES");continue;}
            Layout l=new Layout(map,gate,r.roadWidth);l.waterDistances=water;l.placed.addAll(sites.placed);l.plotCells.addAll(sites.plotCells);for(int i=0;i<l.placed.size();i++)l.routes.add(List.of());
            int end=st.pathExpanded+Math.max(0,(st.pathLimit-st.pathExpanded)/(choices.size()-index));
            int reserve=Math.min(12000,Math.max(0,(end-st.pathExpanded)/6));
            List<Extra> extras=new ArrayList<>();Map<String,RoadStep> endpoints=new LinkedHashMap<>();endpoints.put("entry",new RoadStep(gate[0],gate[1],gate[2],"surface"));
            String failure=connectBuildings(map,r,l,st,end-reserve,endpoints,ir.sitePlanning,attempts);
            if(failure!=null){attempts.add("routing_"+index+": "+failure);continue;}
            Set<Long> forbidden=BoundedSettlementPlanner.blocked(l,null,r.parcelConfig.roadSetback);
            for(var pin:r.expert.pins)if("dock".equals(pin.kind)) {
                var extra=dock(map,r,l,st,end,forbidden,pin.x,pin.z,pin.y,"dock:"+pin.id);
                if(extra==null){failure="MANDATORY_DOCK_UNREACHABLE: "+pin.id;break;}
                extras.add(extra);l.routes.add(extra.path);endpoints.put(pin.id,extra.path.getFirst());ir.sitePlanning.docks++;
            }
            if(failure==null)failure=forcedConnections(map,r,l,st,end,forbidden,endpoints,extras,ir.sitePlanning);
            if(failure!=null){attempts.add("routing_"+index+": "+failure);continue;}
            if(r.expert.allowBridges&&r.expert.autoDock&&ir.sitePlanning.docks==0&&st.pathExpanded<end) {
                Extra extra=autoDock(map,r,l,st,Math.min(end,st.pathExpanded+6000),forbidden);
                if(extra!=null){extras.add(extra);l.routes.add(extra.path);ir.sitePlanning.docks++;}else ir.sitePlanning.reasons.add("OPTIONAL_DOCK_NOT_FOUND_IN_BUDGET");
            }
            // Optional extra links never take the budget reserved for mandatory pins.
            if(st.pathExpanded<end&&st.gradeRelaxations<st.gradeLimit) {
                Extra extra=secondary(map,r,l,st,Math.min(end,st.pathExpanded+6000),forbidden);
                if(extra!=null){extras.add(extra);l.routes.add(extra.path);}
            }
            if(!checkBudgets(map,r,l,st)){attempts.add("routing_"+index+": COLUMN_OR_EDIT_LIMIT");continue;}
            BoundedSettlementPlanner.emit(map,r,l,ir);
            for(int i=0;i<ir.transportNetwork.corridors.size();i++){RoadEdge e=ir.transportNetwork.corridors.get(i);e.roadType=i<4?"collector":"local";e.routingStyle="expert_anchor_first";e.quality=RouteQualityMetrics.measure(e.steps);}
            Map<Long,GroundColumn> cols=new TreeMap<>();for(GroundColumn c:ir.groundColumns)cols.put(key(c.x,c.z),c);
            for(Extra extra:extras){RoadEdge e=new RoadEdge();e.id=extra.id;e.width=r.roadWidth;e.roadType=extra.type;e.routingStyle="expert_required_or_secondary";e.steps=BoundedSettlementPlanner.canonical(extra.path,cols);e.quality=RouteQualityMetrics.measure(e.steps);ir.transportNetwork.corridors.add(e);}
            ir.transportNetwork.algorithm="expert_sites_then_roads";NetworkMetrics nm=ir.transportNetwork.metrics;nm.status="CONNECTED";nm.components=1;
            for(RoadEdge e:ir.transportNetwork.corridors)nm.roadTypes.merge(e.roadType,1,Integer::sum);
            ir.sitePlanning.bridgeColumns=(int)ir.groundColumns.stream().filter(c->"bridge".equals(c.structure)).count();ir.sitePlanning.pileColumns=(int)ir.groundColumns.stream().filter(c->c.support).count();
            ir.sitePlanning.waterBuildingCount=(int)ir.sitePlanning.decisions.stream().filter(d->"water".equals(d.medium)).count();
            SiteMetrics.measure(map,ir.plots,r.expert,ir.sitePlanning);ir.sitePlanning.reasons.addAll(SiteMetrics.failures(ir.plots.size(),ir.sitePlanning));
            if(!SiteMetrics.failures(ir.plots.size(),ir.sitePlanning).isEmpty())return reject(ir,"FINAL_LAYOUT_QUALITY_REJECTED",started);
            ir.status="COMPLETE";ir.sitePlanning.status="ACCEPTED";
            try {ExpertTerrainAudit.validate(map,r,ir);PlanConstruction.prepare(ir,r.settlementStyle);}catch(IllegalArgumentException ex){return reject(ir,"CONSTRUCTION_AUDIT_FAILED: "+ex.getMessage(),started);}
            ir.metadata.score.put("demand_satisfaction",1.0);ir.metadata.score.put("building_bbox_coverage",ir.sitePlanning.bboxCoverage);ir.metadata.score.put("building_minor_axis_ratio",ir.sitePlanning.minorAxisRatio);
            ir.search.elapsedNanos=System.nanoTime()-started;return ir;
        }
        ir.sitePlanning.attempts=attempts;return reject(ir,"REQUIRED_NETWORK_NOT_FOUND_IN_BUDGET",started);
    }

    private static List<Demand> demands(PlanRequest r) {
        List<Demand> result=new ArrayList<>(),auto=new ArrayList<>();
        for(var pin:r.expert.pins)if("building".equals(pin.kind)) {
            BuildingPreset b=BuildingPresetRegistry.getInstance().getPreset(pin.presetId);BuildingRequirement q=new BuildingRequirement();q.id=pin.id;q.purpose=b.category;q.presetId=b.id;q.minWidth=q.minDepth=3;q.maxWidth=q.maxDepth=32;q.heightLimit=b.sizeY;
            result.add(new Demand(q,pin.id,List.of(new BuildingShape(b.rotateToFacing(pin.facing),false))));
        }
        for(Demand d:BoundedSettlementPlanner.demands(r))for(int k=0;k<d.requirement().count;k++)auto.add(new Demand(d.requirement(),"auto:"+d.id()+(k==0?"":"_"+k),d.variants()));
        auto.sort(Comparator.<Demand>comparingInt(d->"center".equals(d.requirement().placement)?0:1).thenComparingInt(d->"civic".equals(role(d))?0:1));
        int total=r.requirements.isEmpty()?r.targetPlots:auto.size();
        for(Demand d:auto){if(result.size()>=total)break;result.add(d);}return result;
    }
    private static String role(Demand d) {return switch(d.requirement().purpose){case "government","culture","landmark"->"civic";case "workshop","commercial","trade"->"trade";case "military"->"highland";default->"residential";};}
    private static Evaluation select(HeightfieldMap m,PlanRequest r,Layout l,Demand d,ExpertSettings.Pin pin,double tx,double tz,int quota,SearchStats st,long salt) {
        Set<Long> occupied=BoundedSettlementPlanner.blocked(l,null,r.parcelConfig.minPlotSpacing);
        // Reserve every already selected private access and its full road landing BEFORE other buildings.
        for(Candidate old:l.placed){for(RoadStep p:old.access())for(int dx=-r.parcelConfig.roadSetback;dx<=r.parcelConfig.roadSetback;dx++)for(int dz=-r.parcelConfig.roadSetback;dz<=r.parcelConfig.roadSetback;dz++)occupied.add(key(p.x+dx,p.z+dz));RoadStep p=old.access().getLast();for(int[] v:stencil(0,0,r.roadWidth))for(int dx=-r.parcelConfig.roadSetback;dx<=r.parcelConfig.roadSetback;dx++)for(int dz=-r.parcelConfig.roadSetback;dz<=r.parcelConfig.roadSetback;dz++)occupied.add(key(p.x+v[0]+dx,p.z+v[1]+dz));}
        Evaluation best=null;
        for(int i=0;i<quota&&st.candidateChecks<st.candidateLimit;i++) {
            if(d.variants().isEmpty())break;if(pin!=null&&i>=d.variants().size())break;
            st.candidateChecks++;long random=mix(salt+i*0x9e3779b97f4a7c15L);BuildingShape shape=d.variants().get(Math.floorMod(i,d.variants().size()));
            double ux=unit(random),uz=unit(mix(random));
            if(i%2==0){ux=Math.max(0,Math.min(.999999,tx+(ux-.5)*.48));uz=Math.max(0,Math.min(.999999,tz+(uz-.5)*.48));}
            int x=pin!=null?pin.x:m.getMinX()+(int)(ux*m.getWidth()/r.searchBudget.candidateStride)*r.searchBudget.candidateStride;
            int z=pin!=null?pin.z:m.getMinZ()+(int)(uz*m.getDepth()/r.searchBudget.candidateStride)*r.searchBudget.candidateStride;
            Candidate c=site(m,r,l,d,shape,x,z,occupied,pin,random);if(c==null)continue;
            double cx=(x+shape.sizeX*.5-m.getMinX())/m.getWidth(),cz=(z+shape.sizeZ*.5-m.getMinZ())/m.getDepth();
            double anchor=(cx-tx)*(cx-tx)+(cz-tz)*(cz-tz),terrain=c.soil()/(double)shape.cells.size();
            int wd=BoundedSettlementPlanner.waterDistance(m,l.waterDistances,shape,x,z,1000000);
            double score=anchor*18+terrain*.12;
            if("trade".equals(role(d))&&wd<1000000)score+=Math.min(1,wd/(double)Math.min(m.getWidth(),m.getDepth()))*.65;
            if("highland".equals(role(d))||"ridge".equals(d.requirement().placement))score-=c.y()*.015;
            if("valley".equals(d.requirement().placement))score+=c.y()*.015;
            if(best==null||score<best.score)best=new Evaluation(c,score,terrain,anchor,wd);
        }return best;
    }
    private static Candidate site(HeightfieldMap m,PlanRequest r,Layout l,Demand d,BuildingShape shape,int x,int z,Set<Long> occupied,ExpertSettings.Pin pin,long tie) {
        if(!m.inBounds(x,z)||!m.inBounds(x+shape.sizeX-1,z+shape.sizeZ-1))return null;
        boolean waterAllowed=pin!=null&&"water".equals(pin.medium);int lo=-2032,hi=2032,wet=0,n=0;int[] heights=new int[shape.cells.size()];
        for(int[] v:shape.cells) {int xx=x+v[0],zz=z+v[1];if(occupied.contains(key(xx,zz))||l.walk.containsKey(key(xx,zz)))return null;
            boolean w=RoadTerrain.wet(m,xx,zz);
            if(w){if(!waterAllowed||!RoadTerrain.allowed(m,r,xx,zz))return null;wet++;int y=m.getWaterY(xx,zz)+1;lo=Math.max(lo,y);hi=Math.min(hi,y);heights[n++]=y;}
            else {if(!buildable(m,xx,zz)||m.getSlope(xx,zz)>r.parcelConfig.maxGroundSlope)return null;int h=m.getSurfaceY(xx,zz);lo=Math.max(lo,h-r.parcelConfig.maxCutBudget);hi=Math.min(hi,h+r.parcelConfig.maxFillBudget);heights[n++]=h;}
        }
        if(waterAllowed&&wet==0||lo>hi)return null;if(pin!=null&&pin.y!=null){lo=Math.max(lo,pin.y);hi=Math.min(hi,pin.y);}if(lo>hi)return null;
        BuildingRequirement q=d.requirement();if("riverbank".equals(q.placement)&&BoundedSettlementPlanner.waterDistance(m,l.waterDistances,shape,x,z,q.maxWaterDistance)>q.maxWaterDistance)return null;
        if(q.nearPurpose!=null&&l.placed.stream().filter(c->q.nearPurpose.equals(c.demand().requirement().purpose)).noneMatch(c->Math.abs(x+shape.sizeX*.5-c.x()-c.preset().sizeX*.5)+Math.abs(z+shape.sizeZ*.5-c.z()-c.preset().sizeZ*.5)<=q.maxDistance))return null;
        Arrays.sort(heights);int median=Math.max(lo,Math.min(hi,heights[n/2]));
        for(int delta=0;delta<=hi-lo;delta++)for(int sign:delta==0?new int[]{1}:new int[]{-1,1}) {int y=median+delta*sign;if(y<lo||y>hi)continue;
            List<RoadStep> access=BoundedSettlementPlanner.access(m,r,l,shape,x,z,y);if(access==null||!PavementGrades.validAccess(m,r,access))continue;
            boolean collision=false;for(RoadStep p:access)if(occupied.contains(key(p.x,p.z))){collision=true;break;}if(collision)continue;
            RoadStep end=access.getLast();for(int[] v:stencil(0,0,r.roadWidth))if(occupied.contains(key(end.x+v[0],end.z+v[1]))||!RoadTerrain.fits(m,r,end.x+v[0],end.z+v[1],end.y)){collision=true;break;}if(collision)continue;
            int soil=0;for(int h:heights)soil+=Math.abs(h-y);return new Candidate(d,shape,x,z,y,access,0,soil,0,tie);
        }return null;
    }
    private static int[] chooseGate(HeightfieldMap m,PlanRequest r,List<int[]> gates,Layout sites) {
        Set<Long> forbidden=BoundedSettlementPlanner.blocked(sites,null,r.parcelConfig.roadSetback);int[] best=null;double score=Double.POSITIVE_INFINITY;
        for(int[] g:gates) {boolean ok=true;for(int[] v:stencil(0,0,r.roadWidth))if(forbidden.contains(key(g[0]+v[0],g[2]+v[1]))){ok=false;break;}if(!ok)continue;
            double distance=sites.placed.stream().mapToDouble(c->{var p=c.access().getLast();return Math.abs(p.x-g[0])+Math.abs(p.z-g[2])+Math.abs(p.y-g[1])*6;}).min().orElse(0);
            if(distance<score){score=distance;best=g;}
        }return best;
    }
    private static String connectBuildings(HeightfieldMap m,PlanRequest r,Layout l,SearchStats st,int end,Map<String,RoadStep> endpoints,SitePlanning report,List<String> trace) {
        Set<Integer> connected=new LinkedHashSet<>();connected.add(-1);Set<Long> forbidden=BoundedSettlementPlanner.blocked(l,null,r.parcelConfig.roadSetback);
        List<List<RoadStep>> access=new ArrayList<>();
        while(connected.size()<=l.placed.size()) {
            List<Link> proposals=new ArrayList<>();for(int i=0;i<l.placed.size();i++)if(!connected.contains(i))for(int parent:connected){RoadStep a=l.placed.get(i).access().getLast(),b=endpoint(l,parent);proposals.add(new Link(i,parent,Math.abs(a.x-b.x)+Math.abs(a.z-b.z)+Math.abs(a.y-b.y)*6));}
            proposals.sort(Comparator.comparingDouble(Link::distance).thenComparingInt(Link::from).thenComparingInt(Link::to));boolean success=false;
            int slots=l.placed.size()+1-connected.size(),slotEnd=Math.min(end,st.pathExpanded+Math.max(6000,(end-st.pathExpanded)*2/Math.max(1,slots+1)));
            Set<Integer> triedChildren=new HashSet<>();int tried=0;
            for(Link link:proposals) {
                if(tried>=6||st.pathExpanded>=slotEnd||st.gradeRelaxations>=st.gradeLimit)break;if(!triedChildren.add(link.from))continue;tried++;
                Set<Long> routeBlocked=new HashSet<>(forbidden);
                for(int j=0;j<l.placed.size();j++)if(j!=link.from&&!connected.contains(j)){
                    Candidate pending=l.placed.get(j);for(RoadStep p:pending.access())routeBlocked.add(key(p.x,p.z));
                    var p=pending.access().getLast();for(int[] v:stencil(0,0,r.roadWidth))routeBlocked.add(key(p.x+v[0],p.z+v[1]));}
                Candidate c=l.placed.get(link.from);RoadStep target=endpoint(l,link.to);Set<Long> goals=new TreeSet<>(l.roadCells);List<List<RoadStep>> allAccess=new ArrayList<>(access);allAccess.add(c.access());
                int allowance=Math.max(3000,slotEnd-st.pathExpanded);TerrainRoadRouter.Trace rt=new TerrainRoadRouter.Trace();
                var result=TerrainRoadRouter.routeTo(m,r,routeBlocked,l.roadCells,l.walk,c.access(),allAccess,usedRoutes(l),l.entry,distanceField(m,goals),st,Math.min(slotEnd,st.pathExpanded+allowance),goals,null,rt);
                if(result==null){trace.add("route "+c.demand().id()+" -> "+(link.to<0?"entry":l.placed.get(link.to).demand().id())+": "+rt.failure);continue;}
                l.walk=result.walk();l.roadCells=result.roadCells();l.routes.set(link.from,result.centers());connected.add(link.from);access.add(c.access());endpoints.put(c.demand().id(),c.access().getLast());
                if(!checkBudgets(m,r,l,st))return "CONSTRUCTION_BUDGET";success=true;break;
            }
            if(!success)return "ANCHOR_CONNECTION_FAILED_WITHIN_SHARED_BUDGET";
        }return null;
    }
    private static List<List<RoadStep>> usedRoutes(Layout l){return l.routes.stream().filter(p->!p.isEmpty()).toList();}
    private static List<List<RoadStep>> accesses(Layout l){return l.placed.stream().map(Candidate::access).toList();}
    private static RoadStep endpoint(Layout l,int index){return index<0?new RoadStep(l.entry[0],l.entry[1],l.entry[2],"surface"):l.placed.get(index).access().getLast();}
    private static Extra dock(HeightfieldMap m,PlanRequest r,Layout l,SearchStats st,int end,Set<Long> blocked,int x,int z,Integer y,String id) {
        if(!RoadTerrain.wet(m,x,z)||y!=null&&y!=m.getWaterY(x,z)+1)return null;
        int yy=m.getWaterY(x,z)+1;List<RoadStep> start=List.of(new RoadStep(x,yy,z,"bridge"));Set<Long> goals=new TreeSet<>(l.roadCells);
        var result=TerrainRoadRouter.routeTo(m,r,blocked,l.roadCells,l.walk,start,accesses(l),usedRoutes(l),l.entry,distanceField(m,goals),st,end,goals,null);
        if(result==null||result.centers().isEmpty())return null;l.walk=result.walk();l.roadCells=result.roadCells();return new Extra(id,"dock",result.centers());
    }
    private static Extra autoDock(HeightfieldMap m,PlanRequest r,Layout l,SearchStats st,int end,Set<Long> blocked) {
        int[] distances=distanceField(m,l.roadCells);List<int[]> options=new ArrayList<>();
        for(int z=m.getMinZ()+3;z<m.getMinZ()+m.getDepth()-3;z+=2)for(int x=m.getMinX()+3;x<m.getMinX()+m.getWidth()-3;x+=2) {
            if(!RoadTerrain.wet(m,x,z)||blocked.contains(key(x,z)))continue;int dist=distances[(z-m.getMinZ())*m.getWidth()+x-m.getMinX()];if(dist>500)continue;
            boolean waterBrush=true;for(int[] v:stencil(0,0,r.roadWidth))if(!RoadTerrain.wet(m,x+v[0],z+v[1])){waterBrush=false;break;}if(!waterBrush)continue;
            options.add(new int[]{x,z,dist});
        }
        options.sort(Comparator.<int[]>comparingInt(p->p[2]).thenComparingInt(p->p[0]).thenComparingInt(p->p[1]));
        for(int i=0;i<Math.min(6,options.size())&&st.pathExpanded<end;i++){int[] p=options.get(i);var e=dock(m,r,l,st,Math.min(end,st.pathExpanded+1500),blocked,p[0],p[1],null,"dock:auto");if(e!=null)return e;}return null;
    }
    private static String forcedConnections(HeightfieldMap m,PlanRequest r,Layout l,SearchStats st,int end,Set<Long> blocked,Map<String,RoadStep> endpoints,List<Extra> extras,SitePlanning report) {
        for(var pin:r.expert.pins) {
            RequiredConnection c=new RequiredConnection();c.from=pin.id;c.to=pin.connectTo;report.requiredConnections.add(c);
            if("network".equals(pin.connectTo)){c.status="VERIFIED_CONNECTED";c.corridorId="dock".equals(pin.kind)?"dock:"+pin.id:"corridor_"+(indexOf(l,pin.id)+1);continue;}
            RoadStep a=endpoints.get(pin.id),b=endpoints.get(pin.connectTo);if(a==null||b==null){c.status="FAILED";c.reason="ENDPOINT_NOT_PLACED";return c.reason;}
            Set<Long> goals=Set.of(key(b.x,b.z));var result=TerrainRoadRouter.routeTo(m,r,blocked,l.roadCells,l.walk,List.of(a),accesses(l),usedRoutes(l),l.entry,distanceField(m,goals),st,Math.min(end,st.pathExpanded+12000),goals,null);
            if(result==null){c.status="FAILED";c.reason="FORCED_ENDPOINT_ROUTE_FAILED: "+pin.id+" -> "+pin.connectTo;return c.reason;}
            l.walk=result.walk();l.roadCells=result.roadCells();String id="forced:"+pin.id;extras.add(new Extra(id,"required",result.centers()));l.routes.add(result.centers());c.corridorId=id;c.status="VERIFIED_ENDPOINT_ROUTE";
        }return null;
    }
    private static int indexOf(Layout l,String id){for(int i=0;i<l.placed.size();i++)if(l.placed.get(i).demand().id().equals(id))return i;return -1;}
    private static Extra secondary(HeightfieldMap m,PlanRequest r,Layout l,SearchStats st,int end,Set<Long> blocked) {
        if(l.placed.size()<3)return null;List<Link> pairs=new ArrayList<>();
        for(int i=0;i<l.placed.size();i++)for(int j=i+1;j<l.placed.size();j++){RoadStep a=endpoint(l,i),b=endpoint(l,j);double distance=Math.hypot(a.x-b.x,a.z-b.z);if(distance<Math.min(m.getWidth(),m.getDepth())*.18)continue;pairs.add(new Link(i,j,distance));}
        pairs.sort(Comparator.comparingDouble(Link::distance).thenComparingInt(Link::from).thenComparingInt(Link::to));
        for(int i=0;i<Math.min(2,pairs.size())&&st.pathExpanded<end;i++){Link p=pairs.get(i);RoadStep a=endpoint(l,p.from),b=endpoint(l,p.to);Set<Long> goals=Set.of(key(b.x,b.z));
            var result=TerrainRoadRouter.routeTo(m,r,blocked,l.roadCells,l.walk,List.of(a),accesses(l),usedRoutes(l),l.entry,distanceField(m,goals),st,Math.min(end,st.pathExpanded+3000),goals,null);
            if(result==null||result.roadCells().size()-l.roadCells.size()<r.roadWidth*12)continue;l.walk=result.walk();l.roadCells=result.roadCells();return new Extra("secondary:1","secondary",result.centers());}
        return null;
    }
    private static boolean checkBudgets(HeightfieldMap m,PlanRequest r,Layout l,SearchStats st) {
        Map<Long,GroundColumn> cols=BoundedSettlementPlanner.columns(m,l.walk,l.placed);
        if(cols.size()>st.columnLimit){exhausted(st,"GROUND_COLUMNS");return false;}if(BoundedSettlementPlanner.editCount(cols)>st.editLimit){exhausted(st,"CONSTRUCTION_EDITS");return false;}return true;
    }
    private static List<Plot> metricPlots(List<Candidate> sites) {List<Plot> out=new ArrayList<>();for(Candidate c:sites){Plot p=new Plot();p.origin2D=new int[]{c.x(),c.z()};p.footprint=c.preset().cells;out.add(p);}return out;}
    private static PlanningIR reject(PlanningIR ir,String reason,long started) {
        ir.status="REJECTED";ir.sitePlanning.status="REJECTED";ir.sitePlanning.reasons.add(reason);ir.auditLog.warnings.add(reason+"; no construction committed; bounded rejection is not proof of global infeasibility");
        ir.plots.clear();ir.groundColumns.clear();ir.transportNetwork=new TransportNetwork();ir.earthworks=new EarthworkReport();ir.search.constructionEdits=0;
        UnmetRequirement u=new UnmetRequirement();u.requirementId="expert_layout";u.purpose="mandatory_sites_coverage_and_connections";u.requested=1;u.allocated=0;u.reason=reason;ir.unmetRequirements.add(u);
        ir.search.elapsedNanos=System.nanoTime()-started;return ir;
    }
    private static long mix(long v){return BoundedSettlementPlanner.mix(v);}
    private static double unit(long v){return (v>>>11)*0x1.0p-53;}
    private static double round(double d){return Math.rint(d*10000)/10000;}
}
