package org.mcsettlement.planner.simulation;

import com.google.gson.Gson;
import org.mcsettlement.planner.ir.PlanningIR;
import org.mcsettlement.planner.ir.PlanningIR.GroundColumn;
import org.mcsettlement.planner.ir.PlanningIR.Plot;
import org.mcsettlement.planner.ir.PlanningIR.RoadEdge;
import org.mcsettlement.planner.ir.PlanningIR.RoadStep;
import org.mcsettlement.planner.simulation.SimulatedSettlementPipeline.PipelineResult;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Small stdout JSON adapter used by the local Python rehearsal server.
 * Planning remains entirely inside the Java pipeline; this class only shapes
 * a bounded response for the browser.
 */
public final class SimulationApiRunner {
    private static final Gson GSON = new Gson();

    private SimulationApiRunner() {}

    public static void main(String[] args) {
        Request request = Request.parse(args);
        PrintStream jsonOut = System.out;
        try {
            // The preset registry has a human-readable startup line. Keep the
            // machine-facing stdout channel strictly JSON for the local API.
            System.setOut(new PrintStream(System.err, true));
            PipelineResult result = SimulatedSettlementPipeline.run(
                    request.width,
                    request.depth,
                    request.baseElevation,
                    request.relief,
                    request.terrainType,
                    request.terrainSeed,
                    request.planSeed,
                    request.targetPlots);
            jsonOut.print(GSON.toJson(new Payload(result)));
        } finally {
            System.setOut(jsonOut);
        }
    }

    private static final class Request {
        int width = 96;
        int depth = 96;
        int baseElevation = 58;
        int relief = 24;
        String terrainType = "rolling_hills";
        long terrainSeed = 42L;
        long planSeed = 42L;
        int targetPlots = 7;

        static Request parse(String[] args) {
            Request request = new Request();
            for (String arg : args) {
                if (!arg.startsWith("--") || !arg.contains("=")) continue;
                String[] pair = arg.substring(2).split("=", 2);
                switch (pair[0]) {
                    case "width" -> request.width = Integer.parseInt(pair[1]);
                    case "depth" -> request.depth = Integer.parseInt(pair[1]);
                    case "baseElevation" -> request.baseElevation = Integer.parseInt(pair[1]);
                    case "relief" -> request.relief = Integer.parseInt(pair[1]);
                    case "terrainType" -> request.terrainType = pair[1];
                    case "terrainSeed", "seed" -> request.terrainSeed = Long.parseLong(pair[1]);
                    case "planSeed" -> request.planSeed = Long.parseLong(pair[1]);
                    case "targetPlots" -> request.targetPlots = Integer.parseInt(pair[1]);
                    default -> { }
                }
            }
            if (request.width < 32 || request.width > 128
                    || request.depth < 32 || request.depth > 128) {
                throw new IllegalArgumentException("width/depth must be between 32 and 128");
            }
            if (request.baseElevation < 50 || request.baseElevation > 75) {
                throw new IllegalArgumentException("baseElevation must be between 50 and 75");
            }
            if (request.relief < 4 || request.relief > 36) {
                throw new IllegalArgumentException("relief must be between 4 and 36");
            }
            if (request.targetPlots < 1 || request.targetPlots > 12) {
                throw new IllegalArgumentException("targetPlots must be between 1 and 12");
            }
            return request;
        }
    }

    private static final class Payload {
        final boolean ok = true;
        final Config config;
        final Metrics metrics;
        final Layer original;
        final Layer constructed;
        final PlanPreview plan;

        Payload(PipelineResult result) {
            config = new Config(result);
            metrics = new Metrics(result);
            original = new Layer(result.worldBefore);
            constructed = new Layer(result.worldAfter);
            plan = new PlanPreview(result.plan, result.planHash);
        }
    }

    private static final class Config {
        final int width;
        final int depth;
        final int baseElevation;
        final int relief;
        final String terrainType;
        final long terrainSeed;
        final long planSeed;
        final int targetPlots;
        final String originalTerrainHash;

        Config(PipelineResult result) {
            width = result.width;
            depth = result.depth;
            baseElevation = result.baseElevation;
            relief = result.relief;
            terrainType = result.terrainType;
            terrainSeed = result.terrainSeed;
            planSeed = result.planSeed;
            targetPlots = result.targetPlots;
            originalTerrainHash = result.originalTerrainHash;
        }
    }

    private static final class Metrics {
        final String status;
        final int plotCount;
        final int roadColumnCount;
        final int roadEdgeCount;
        final int clearedBlocks;
        final int cutBlocks;
        final int fillBlocks;
        final int buildingBlocks;
        final int stairBlocks;
        final String planHash;

        Metrics(PipelineResult result) {
            status = result.plan.status;
            plotCount = result.plan.plots.size();
            roadColumnCount = (int) result.plan.groundColumns.stream()
                    .filter(column -> "road".equals(column.kind)).count();
            roadEdgeCount = result.plan.transportNetwork.edges.size();
            clearedBlocks = result.clearedBlocks;
            cutBlocks = result.cutBlocks;
            fillBlocks = result.fillBlocks;
            buildingBlocks = result.buildingBlocks;
            stairBlocks = result.stairBlocks;
            planHash = result.planHash;
        }
    }

    private static final class Layer {
        final int minY;
        final int sizeX;
        final int sizeY;
        final int sizeZ;
        final List<int[]> voxels;

        Layer(SimulatedVoxelWorld world) {
            minY = world.getMinY();
            sizeX = world.getSizeX();
            sizeY = world.getSizeY();
            sizeZ = world.getSizeZ();
            voxels = visibleVoxels(world);
        }
    }

    private static final class PlanPreview {
        final String status;
        final String hash;
        final List<PlotPreview> plots = new ArrayList<>();
        final List<ColumnPreview> groundColumns = new ArrayList<>();
        final List<PathPreview> roadPaths = new ArrayList<>();
        final List<UnmetPreview> unmetRequirements = new ArrayList<>();
        final List<String> warnings = new ArrayList<>();

        PlanPreview(PlanningIR ir, String hash) {
            status = ir.status;
            this.hash = hash;
            for (Plot plot : ir.plots) plots.add(new PlotPreview(plot));
            for (GroundColumn column : ir.groundColumns) groundColumns.add(new ColumnPreview(column));
            for (RoadEdge edge : ir.transportNetwork.corridors) roadPaths.add(new PathPreview(edge));
            for (PlanningIR.UnmetRequirement unmet : ir.unmetRequirements) unmetRequirements.add(new UnmetPreview(unmet));
            warnings.addAll(ir.auditLog.warnings);
        }
    }

    private static final class UnmetPreview {
        final String requirementId;
        final String purpose;
        final String reason;
        final int requested;
        final int allocated;

        UnmetPreview(PlanningIR.UnmetRequirement unmet) {
            requirementId = unmet.requirementId;
            purpose = unmet.purpose;
            reason = unmet.reason;
            requested = unmet.requested;
            allocated = unmet.allocated;
        }
    }

    private static final class PlotPreview {
        final String id;
        final String requirementId;
        final List<int[]> polygon;
        final int baseElevation;
        final String presetId;

        PlotPreview(Plot plot) {
            id = plot.id;
            requirementId = plot.requirementId;
            polygon = plot.polygon2D;
            baseElevation = plot.elevation.baseElevation;
            presetId = plot.builder.presetId;
        }
    }

    private static final class ColumnPreview {
        final int x;
        final int z;
        final int originalY;
        final int targetY;
        final String kind;

        ColumnPreview(GroundColumn column) {
            x = column.x;
            z = column.z;
            originalY = column.originalY;
            targetY = column.targetY;
            kind = column.kind;
        }
    }

    private static final class PathPreview {
        final String id;
        final List<int[]> steps = new ArrayList<>();

        PathPreview(RoadEdge edge) {
            id = edge.id;
            for (RoadStep step : edge.steps) steps.add(new int[]{step.x, step.y, step.z});
        }
    }

    private static List<int[]> visibleVoxels(SimulatedVoxelWorld world) {
        List<int[]> out = new ArrayList<>();
        for (int x = 0; x < world.getSizeX(); x++) {
            for (int z = 0; z < world.getSizeZ(); z++) {
                for (int y = world.getMinY(); y < world.getMinY() + world.getSizeY(); y++) {
                    SimulatedVoxelWorld.VoxelType block = world.getBlock(x, y, z);
                    if (block == SimulatedVoxelWorld.VoxelType.AIR || !isExposed(world, x, y, z)) continue;
                    out.add(new int[]{x, y - world.getMinY(), z, block.id});
                }
            }
        }
        return out;
    }

    private static boolean isExposed(SimulatedVoxelWorld world, int x, int y, int z) {
        int[][] dirs = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        for (int[] dir : dirs) {
            SimulatedVoxelWorld.VoxelType neighbor = world.getBlock(x + dir[0], y + dir[1], z + dir[2]);
            if (neighbor == SimulatedVoxelWorld.VoxelType.AIR
                    || neighbor == SimulatedVoxelWorld.VoxelType.WATER
                    || neighbor == SimulatedVoxelWorld.VoxelType.GLASS_PANE) return true;
        }
        return false;
    }
}
