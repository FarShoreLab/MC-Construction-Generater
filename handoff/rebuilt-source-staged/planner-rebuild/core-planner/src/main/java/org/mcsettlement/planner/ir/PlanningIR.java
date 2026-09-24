package org.mcsettlement.planner.ir;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Standard Intermediate Representation (Planning IR v0.2) for settlement generation.
 * Completely decouples geometric/spatial planning from in-world Minecraft block construction.
 */
public class PlanningIR {
    public static final String SCHEMA_VERSION = "0.2.0";

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

    public static class UnmetRequirement {
        public String requirementId, purpose, reason;
        public int requested, allocated;
    }
    public static class SearchStats {
        public int candidateChecks, pathExpanded, attempts;
        public int candidateLimit, pathLimit;
        public boolean budgetExhausted;
        public int modelCalls;
        public long elapsedNanos;
    }
    /** A unique (x,z) construction column. All Y values are TOP SOLID BLOCK coordinates. */
    public static class GroundColumn {
        public int x, z, originalY, targetY, clearToY;
        public String kind; // road, access, foundation
        public GroundColumn() {}
        public GroundColumn(int x, int z, int originalY, int targetY, int clearToY, String kind) {
            this.x=x; this.z=z; this.originalY=originalY; this.targetY=targetY;
            this.clearToY=clearToY; this.kind=kind;
        }
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
        public int corridorWidth;
        // Construction corridors. nodes/edges below describe actual paved-cell adjacency.
        public List<RoadEdge> corridors = new ArrayList<>();
        public List<RoadNode> nodes = new ArrayList<>();
        public List<RoadEdge> edges = new ArrayList<>();
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
        public List<int[]> polygon2D = new ArrayList<>(); // List of [x, z] vertices
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
        // Door threshold (floor block) to outside road center, inclusive, 4-connected.
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
        public String generatorType = "nbt_structure_pool";
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
