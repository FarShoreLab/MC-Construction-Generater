package org.mcsettlement.planner.ir;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Standard Intermediate Representation (Planning IR v0.4) for settlement generation.
 * Completely decouples geometric/spatial planning from in-world Minecraft block construction.
 */
public class PlanningIR {
    public static final String SCHEMA_VERSION = "0.4.0";
    public static final String EXPERT_SCHEMA_VERSION = "0.5.0";

    public Metadata metadata = new Metadata();
    public TransportNetwork transportNetwork = new TransportNetwork();
    public List<Plot> plots = new ArrayList<>();
    public EarthworkReport earthworks = new EarthworkReport();
    public ClearanceManifest clearance = new ClearanceManifest();
    public AuditLog auditLog = new AuditLog();
    // A legacy/deserialized plan must never be assumed construction-validated.
    public String status = "UNVALIDATED";
    public List<UnmetRequirement> unmetRequirements = new ArrayList<>();
    public SearchStats search = new SearchStats();
    public List<GroundColumn> groundColumns = new ArrayList<>();
    public List<LandUseArea> landUses = new ArrayList<>();
    public SitePlanning sitePlanning; // optional v0.5 expert provenance; no independent construction authority


    public static class UnmetRequirement {
        public String requirementId, purpose, reason;
        public int requested, allocated;
    }
    public static class SearchStats {
        public int candidateChecks, pathExpanded, attempts;
        public int candidateLimit, pathLimit;
        public boolean budgetExhausted;
        public int modelCalls;
        public Map<String,Integer> gradeRejections=new java.util.TreeMap<>();
        public int terrainCellsAnalyzed, siteLayoutsTried, siteLayoutsRouted;
        public int siteRepairsTried, siteRepairsAccepted, routeQuotaExhaustions;
        public int roadReuseExpanded, reusedRouteCount, reusedCenterlineSteps;
        public long roadReuseGraphVisits, terrainGuideCellVisits, roadProximityCellVisits;
        public long networkWalkVisits, enclosureCellVisits;
        public int heuristicExpanded=0;
        public int topologyEdgesTried;
        public int routeRefinementAttempts, refinementExpanded, rhythmWindowsChecked;
        public int guideCandidatesRanked, guideProbeCells, guideFieldBuilds;
        public long guideFieldCellVisits;
        public int prefilterExpanded, peakPathStates, peakOpenNodes, gradeRelaxations, routeAttempts;
        public int stateLimit, gradeLimit, columnLimit, editLimit, constructionEdits;
        public List<String> exhaustedBudgets = new ArrayList<>();
        public long elapsedNanos;
    }
    /** A unique (x,z) construction column. All Y values are TOP SOLID BLOCK coordinates. */
    public static class GroundColumn {
        public int x, z, originalY, targetY, clearToY;
        public Integer waterY; // original water surface: deck target must be waterY+1
        public boolean support; // pile from original bed+1 to deck-1, never a solid embankment
        public String surfaceMaterial; // optional road material or farmland border; geometry remains authoritative
        public List<String> aboveBlocks = new ArrayList<>(); // farm hut voxels at targetY+1 onward
        public String kind; // road, access, foundation, farmland, pasture
        public String structure = "surface"; // surface or a bottom, straight stair at targetY
        public String facing; // cardinal uphill facing; required iff structure == stair
        public GroundColumn() {}
        public GroundColumn(int x, int z, int originalY, int targetY, int clearToY, String kind) {
            this.x=x; this.z=z; this.originalY=originalY; this.targetY=targetY;
            this.clearToY=clearToY; this.kind=kind;
        }
    }


    public static class PlanRollAttempt {
        public int round;
        public long planSeed;
        public String status;
        public List<String> reasons=new ArrayList<>();
    }
    /** Display/provenance region. Construction authority remains the matching groundColumns. */
    public static class LandUseArea {
        public String id,type; // farmland or pasture
        public List<int[]> cells=new ArrayList<>(); // absolute [x,z] cells, terrain-adaptive flood region
        public List<int[]> boundary2D=new ArrayList<>(); // boundary cells, never a fabricated rectangle
        public int minY,maxY;
    }


    public static class SitePlanning {
        public String algorithm="site_expert/0.6.0", status="SEARCHING", settlementMode="village";
        public long requestedPlanSeed,effectivePlanSeed,effectiveSiteSeed;
        public List<PlanRollAttempt> planRollAttempts=new ArrayList<>();
        public double bboxCoverage, hullCoverage, minorAxisRatio, spanX, spanZ, coveragePenalty;
        public double minBBoxCoverage, minMinorAxisRatio, minHullCoverage;
        public double spacingReference, nearestNeighborMin, nearestNeighborMean, crowdingPenalty;
        public int closePairs, crowdedBuildings, macroLoops, crossLinksTried, crossLinksBuilt;
        public List<Integer> enclosureAreas=new ArrayList<>();
        public int[] bounds; // inclusive building-occupied-cell bounds; excludes roads and docks
        public int coveredSectors, bridgeColumns, pileColumns, waterBuildingCount, docks;
        public int maxRoadClearance,maxLandAirborneRun,farmlandCells,pastureCells,maxBuildingHeight;
        public double meanRoadClearance,meanBuildingHeight,meanBuildingVolume,buildingHeightStdDev;
        public List<SiteDecision> decisions=new ArrayList<>();
        public List<String> reasons=new ArrayList<>();
        public List<String> attempts=new ArrayList<>();
        public List<RequiredConnection> requiredConnections=new ArrayList<>();
    }
    public static class SiteDecision {
        public String id, plotId, role, presetId, medium;
        public int x,z,y,cluster;
        public boolean pinned;
        public double score, terrainCost, anchorDistance, shoreDistance, repulsionPenalty;
        public List<String> rules=new ArrayList<>();
    }
    public static class RequiredConnection {
        public String from, to, corridorId, status, reason;
    }

    public static class Metadata {
        public String version = SCHEMA_VERSION;
        public String timestamp;
        public long randomSeed;
        public int[] minBounds = new int[3]; // [x, y, z]
        public int[] maxBounds = new int[3];
        public Map<String, Double> score = new HashMap<>();
    }

    public static class TransportNetwork {
        public String algorithm = "incremental_feasibility";
        public List<SemanticNode> semanticNodes = new ArrayList<>();
        public List<SemanticLink> semanticLinks = new ArrayList<>();
        public NetworkMetrics metrics = new NetworkMetrics();
        public int corridorWidth;
        public int directionCount = 8;
        // Construction corridors. nodes/edges below describe actual paved-cell adjacency.
        public List<RoadEdge> corridors = new ArrayList<>();
        public List<RoadNode> nodes = new ArrayList<>();
        public List<RoadEdge> edges = new ArrayList<>();
    }

    public static class SemanticNode {
        public String id, type; // entry, hub/plaza, building
        public int[] pos;
        public List<String> plotIds = new ArrayList<>();
        public SemanticNode() {}
        public SemanticNode(String id,String type,int[] pos){this.id=id;this.type=type;this.pos=pos;}
    }
    public static class SemanticLink {
        public String id, fromNodeId, toNodeId, roadType, corridorId, accessPlotId;
        public boolean verifiedLoop;
    }
    public static class NetworkMetrics {
        public String status="NOT_BUILT";
        public int hubs, branchNodes, semanticNodes, semanticEdges, components, cycleRank, pavedEnclosures;
        public int verifiedLoops, topologyEdgeLimit=128;
        public double longestStraightRatio, microZigzagRatio;
        public double uniqueCenterlineLength, sharedCenterlineLength, nearParallelLength;
        public int cardinalSteps, diagonal45Steps, obliqueSteps;
        public long proximityMetricComparisons;
        // Additive diagnostics; construction schema and column authority remain v0.4.0.
        public int routeQualityVersion, maximumStraightRun, majorMaxStraightRun, longStraightWarnings;
        public int unguidedFallbackCount, conservativeRouteCount, unguidedFallbackAttempts, failedRouteAttempts;
        public List<RouteAttempt> routingAttempts=new ArrayList<>();
        public Map<String,Integer> roadTypes=new java.util.TreeMap<>();
        public List<String> reasons=new ArrayList<>();
    }

    /** Recomputable from committed centers; distances are in horizontal blocks, runs in moves. */
    public static class RouteQuality {
        public int stepCount, maxStraightRun, rawTurns, effectiveBends, shortRuns, microZigzagWindows, rhythmZigzagWindows;
        public double length, endpointDistance, sinuosity, maxStraightLength, longStraightFraction;
        public double microZigzagRatio, meanBendSpacing;
        public List<Integer> straightRunSteps=new ArrayList<>();
        public List<Double> bendSpacings=new ArrayList<>();
        public String straightException;
    }
    /** Ordered bounded trace. A candidate may be feasible but superseded without being committed. */
    public static class RouteAttempt {
        public int number, pathExpanded, gradeRelaxations;
        public String fromNodeId, toNodeId, roadType, candidateId, stage, status, reason;
        public double estimatedCost;
        public Double routeScore;
        public boolean selected, centerlineRefined;
        public int refinementPathExpanded, refinementVariants, rhythmWindowsChecked, rhythmReorderings;
        public String refinementOutcome;
        public Map<String,Integer> refinementRejections=new java.util.TreeMap<>();
    }

    public static class RoadNode {
        public String id;
        public int[] pos = new int[3]; // [x, y, z]
        public String type; // "entry", "junction", "terminus", "stair"
        public float elevation;

        public RoadNode() {}
        public RoadNode(String id, int x, int y, int z, String type) {
            this.id = id;
            this.pos = new int[]{x, y, z};
            this.elevation = y;
            this.type = type;
        }
    }

    public static class RoadEdge {
        public String id;
        public List<int[]> guideControls = new ArrayList<>();
        public String routingStyle = "terrain_astar";
        public String guideCandidateId, fallbackReason, refinementMethod;
        public RouteQuality quality; // populated only on construction corridors, not adjacency edges
        public String fromNodeId;
        public String toNodeId;
        public String roadType = "primary_road"; // "primary_road", "secondary_road", "pathway"
        public int width = 3;
        public float maxSlope = 0.0f;
        public List<RoadStep> steps = new ArrayList<>();
    }

    public static class RoadStep {
        public int x;
        public int y;
        public int z;
        public String structure = "surface"; // "surface", "stair", "bridge", "embankment"

        public RoadStep() {}
        public RoadStep(int x, int y, int z, String structure) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.structure = structure;
        }
    }

    public static class Plot {
        public String id;
        public String requirementId;
        public List<String> tags = new ArrayList<>();
        public List<int[]> polygon2D = new ArrayList<>(); // display hull; not a construction rectangle
        public int[] origin2D; // explicit raster origin [x,z]; old 0.2 plans fall back to first vertex
        public List<int[]> footprint = new ArrayList<>(); // exact LOCAL occupied cells, including diagonal raster
        public ElevationSpec elevation = new ElevationSpec();
        public EntranceSpec entrance = new EntranceSpec();
        public FoundationStrategy foundation = new FoundationStrategy();
        public BuilderSpec builder = new BuilderSpec();
    }

    public static class ElevationSpec {
        public int baseElevation;
        public int entranceElevation;
        public int maxCutDepth;
        public int maxFillHeight;
    }

    public static class EntranceSpec {
        public int width = 1; // Private pedestrian connection, distinct from the full-width road.
        public int[] accessPoint = new int[3]; // [x, y, z]
        public String facing = "NORTH"; // "NORTH", "SOUTH", "EAST", "WEST"
        public String connectedEdgeId;
        // Door threshold (floor block) to outside road center, inclusive, 4-connected with constructed stairs / level landings.
        public List<RoadStep> path = new ArrayList<>();
    }

    public static class FoundationStrategy {
        public String type = "horizontal_slab_with_retaining_wall"; // "slab", "stilt", "terrace"
        public List<RetainingWallSpec> retainingWalls = new ArrayList<>();
    }

    public static class RetainingWallSpec {
        public String side; // "NORTH", "SOUTH", "EAST", "WEST"
        public int startX;
        public int startZ;
        public int endX;
        public int endZ;
        public int baseElevation;
        public int topElevation;
        public int height;
        public String materialTag = "stone_brick";
    }

    public static class BuilderSpec {
        public String footprintShape = "rectangle", sizeTier = "standard";
        public int footprintArea;
        public String generatorType = "nbt_structure_pool";
        public boolean diagonal45;
        public int entranceIndex; // chosen candidate, persisted for exact construction replay
        public String sourceFacing; // cardinal preset orientation BEFORE optional 45-degree rasterization
        public String presetId; // Locked BEFORE geometric placement; no post-hoc replacement/cropping.
        public String templateCategory = "residential";
        public int[] footprintSize = new int[]{9, 9}; // [width, depth]
        public int heightLimit = 12;
        public long subSeed;
    }

    public static class EarthworkReport {
        public int totalCutVolume;
        public int totalFillVolume;
        public int cutFillBalance;
    }

    public static class ClearanceManifest {
        public List<int[]> clearVegetationBoxes = new ArrayList<>(); // [minX, minY, minZ, maxX, maxY, maxZ]
    }

    public static class AuditLog {
        public List<String> warnings = new ArrayList<>();
        public List<String> rejectedCandidates = new ArrayList<>();
    }

    public String toJson(boolean pretty) {
        Gson gson = pretty ? new GsonBuilder().setPrettyPrinting().create() : new Gson();
        return gson.toJson(this);
    }

    public static PlanningIR fromJson(String json) {
        return new Gson().fromJson(json, PlanningIR.class);
    }
}
