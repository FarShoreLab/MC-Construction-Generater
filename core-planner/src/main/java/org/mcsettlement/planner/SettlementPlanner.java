package org.mcsettlement.planner;

import org.mcsettlement.planner.ir.PlanningIR;
import org.mcsettlement.planner.parcel.PlotPlanner.ParcelConfig;
import org.mcsettlement.planner.terrain.HeightfieldMap;
import java.util.ArrayList;
import java.util.List;

/** Bounded, deterministic settlement planner. Legacy algorithm lives only in test/baseline. */
public final class SettlementPlanner {
    public static class BuildingRequirement {
        public String id;
        public String purpose = "residential";
        public String presetId;
        public int count = 1;
        public int minWidth = 4, maxWidth = 24, minDepth = 4, maxDepth = 24;
        public int heightLimit = 24;
        public String placement = "any"; // any, center, ridge, valley, riverbank
        public int maxWaterDistance = 8; // hard L1 footprint-to-water distance for riverbank
        public String nearPurpose;       // hard L1 center distance; referenced purpose is placed first
        public int maxDistance = 24;
    }
    public static class SearchBudget {
        public int attempts = 3;
        public int candidateChecks = 24000;
        public int pathExpanded = 180000;
        public int shortlist = 8;
        public int candidateStride = 2;
        public int pathStates = 100000;       // live states per route, independent of expansions
        public int gradeRelaxations = 2000000; // total pavement-height propagation work
        public int groundColumns = 40000;
        public int constructionEdits = 500000;
    }
    public static class PlanRequest {
        public long seed = 42;
        public int targetPlots = 7;
        public int roadWidth = 3;
        public String roadSurface = "cobblestone";
        public String settlementStyle = "medieval_rustic";
        public String landmarkPlacement = "center";
        public ParcelConfig parcelConfig = new ParcelConfig();
        public List<BuildingRequirement> requirements = new ArrayList<>();
        public SearchBudget searchBudget = new SearchBudget();
        public int roadMaxCut = 3, roadMaxFill = 3;
        public int roadDirections = 8; // 8: cardinal+45 degrees; 12: cardinal+eight (2,1) headings
        public boolean organicRoads = true;
        public String presetPalette = "classic";
        public String singlePresetId;
        public boolean diagonalBuildings = false; // add whole-footprint 45-degree raster variants
        public ExpertSettings expert; // null preserves the v0.4.x API; preview defaults to expert mode
        public int[] entry; // optional [worldX, top-solid-Y, worldZ]; never silently moved
    }
    public static PlanningIR plan(HeightfieldMap map, PlanRequest request) {
        if(request!=null&&!java.util.Set.of("dirt_path","gravel","cobblestone","stone_bricks").contains(request.roadSurface))throw new IllegalArgumentException("INVALID_ROAD_SURFACE");
        if(request==null||request.expert==null)return surfaces(BoundedSettlementPlanner.plan(map,request),request);
        int limit=Math.max(1,request.expert.maxPlanAttempts);long requested=request.seed;
        List<PlanningIR.PlanRollAttempt> history=new ArrayList<>();PlanningIR last=null;
        for(int round=1;round<=limit;round++){
            PlanRequest current=copyForRetry(request,requested+round-1);
            PlanningIR ir=ExpertSettlementPlanner.plan(map,current);last=ir;
            PlanningIR.PlanRollAttempt a=new PlanningIR.PlanRollAttempt();a.round=round;a.planSeed=current.seed;a.status=ir.status;
            if(ir.sitePlanning!=null){a.reasons.addAll(ir.sitePlanning.reasons);a.reasons.addAll(ir.sitePlanning.attempts);}
            a.reasons.addAll(ir.auditLog.warnings);a.reasons=new ArrayList<>(new java.util.LinkedHashSet<>(a.reasons));history.add(a);
            if(ir.sitePlanning!=null){ir.sitePlanning.requestedPlanSeed=requested;ir.sitePlanning.effectivePlanSeed=current.seed;ir.sitePlanning.effectiveSiteSeed=request.expert.effectiveSiteSeed(current.seed);ir.sitePlanning.planRollAttempts=new ArrayList<>(history);ir.sitePlanning.settlementMode=request.expert.settlementMode;}
            if(!"REJECTED".equals(ir.status))return surfaces(ir,request);
        }
        if(last!=null&&last.sitePlanning!=null){last.sitePlanning.requestedPlanSeed=requested;last.sitePlanning.effectivePlanSeed=last.metadata.randomSeed;last.sitePlanning.effectiveSiteSeed=request.expert.effectiveSiteSeed(last.metadata.randomSeed);last.sitePlanning.planRollAttempts=new ArrayList<>(history);last.sitePlanning.settlementMode=request.expert.settlementMode;}
        return surfaces(last,request);
    }
    private static PlanningIR surfaces(PlanningIR ir,PlanRequest request){
        if(ir!=null&&request!=null)for(var c:ir.groundColumns)if("road".equals(c.kind)||"access".equals(c.kind))c.surfaceMaterial=request.roadSurface;
        return ir;
    }
    private static PlanRequest copyForRetry(PlanRequest src,long seed){
        PlanRequest r=new PlanRequest();r.seed=seed;r.targetPlots=src.targetPlots;r.roadWidth=src.roadWidth;r.settlementStyle=src.settlementStyle;r.landmarkPlacement=src.landmarkPlacement;
        r.roadSurface=src.roadSurface;r.parcelConfig=src.parcelConfig;r.requirements=new ArrayList<>(src.requirements);r.searchBudget=src.searchBudget;r.roadMaxCut=src.roadMaxCut;r.roadMaxFill=src.roadMaxFill;
        r.roadDirections=src.roadDirections;r.organicRoads=src.organicRoads;r.presetPalette=src.presetPalette;r.singlePresetId=src.singlePresetId;r.diagonalBuildings=src.diagonalBuildings;r.expert=src.expert;r.entry=src.entry==null?null:src.entry.clone();return r;
    }
}
