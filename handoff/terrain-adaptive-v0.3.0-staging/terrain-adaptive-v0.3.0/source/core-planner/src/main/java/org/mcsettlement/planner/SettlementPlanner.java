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
        public String settlementStyle = "medieval_rustic";
        public String landmarkPlacement = "center";
        public ParcelConfig parcelConfig = new ParcelConfig();
        public List<BuildingRequirement> requirements = new ArrayList<>();
        public SearchBudget searchBudget = new SearchBudget();
        public int roadMaxCut = 3, roadMaxFill = 3;
        public int roadDirections = 8; // 8: cardinal+45 degrees; 12: cardinal+eight (2,1) headings
        public boolean diagonalBuildings = false; // add whole-footprint 45-degree raster variants
        public int[] entry; // optional [worldX, top-solid-Y, worldZ]; never silently moved
    }
    public static PlanningIR plan(HeightfieldMap map, PlanRequest request) {
        return BoundedSettlementPlanner.plan(map, request);
    }
}
