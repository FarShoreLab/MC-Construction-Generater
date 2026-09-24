package org.mcsettlement.planner.cli;

import org.mcsettlement.planner.SettlementPlanner;
import org.mcsettlement.planner.SettlementPlanner.PlanRequest;
import org.mcsettlement.planner.ir.PlanningIR;
import org.mcsettlement.planner.render.PlanPreviewRenderer;
import org.mcsettlement.planner.terrain.HeightfieldMap;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

/**
 * Command-line runner for offline planning, verification, and preview generation.
 */
public class PlannerCli {

    public static void main(String[] args) {
        System.out.println("=================================================");
        System.out.println(" Minecraft Terrain-Adaptive Settlement Planner");
        System.out.println("=================================================\n");

        int width = 96;
        int depth = 96;
        int maxRelief = 18; // 18 blocks of elevation relief (matching the 10-20 block relief spec)
        long seed = 12345L;

        System.out.printf("Generating synthetic terrain: [%dx%d], relief: %d blocks, seed: %d...\n",
                width, depth, maxRelief, seed);

        HeightfieldMap map = HeightfieldMap.createSynthetic(0, 0, width, depth, 64, maxRelief, seed);

        PlanRequest req = new PlanRequest();
        req.seed = seed;
        req.targetPlots = 6;
        req.roadWidth = 3;

        System.out.println("Running terrain-adaptive planning solver...");
        long startTime = System.currentTimeMillis();
        PlanningIR ir = SettlementPlanner.plan(map, req);
        long elapsed = System.currentTimeMillis() - startTime;

        System.out.printf("Planning completed in %d ms.\n", elapsed);
        System.out.printf(" - Roads Planned: %d (Steps: %d, Max Slope: %.1f)\n",
                ir.transportNetwork.edges.size(),
                ir.transportNetwork.edges.isEmpty() ? 0 : ir.transportNetwork.edges.get(0).steps.size(),
                ir.transportNetwork.edges.isEmpty() ? 0 : ir.transportNetwork.edges.get(0).maxSlope);
        System.out.printf(" - Building Plots Allocated: %d\n", ir.plots.size());
        System.out.printf(" - Earthwork Cut Volume: %d blocks\n", ir.earthworks.totalCutVolume);
        System.out.printf(" - Earthwork Fill Volume: %d blocks\n", ir.earthworks.totalFillVolume);
        System.out.printf(" - Status: %s; unmet requirement groups: %d; candidates: %d; path expansions: %d%n",
                ir.status, ir.unmetRequirements.size(), ir.search.candidateChecks, ir.search.pathExpanded);
        for (var unmet : ir.unmetRequirements) System.out.printf("   %s: %d/%d (%s)%n",
                unmet.requirementId, unmet.allocated, unmet.requested, unmet.reason);

        // Render preview
        String ascii = PlanPreviewRenderer.renderAscii(map, ir);
        System.out.println(ascii);

        // Save output files
        try {
            File outDir = new File("output");
            if (!outDir.exists()) outDir.mkdirs();

            File jsonFile = new File(outDir, "plan.json");
            try (FileWriter fw = new FileWriter(jsonFile)) {
                fw.write(ir.toJson(true));
            }
            System.out.println("Saved Planning IR JSON: " + jsonFile.getAbsolutePath());

            File svgFile = new File(outDir, "preview.svg");
            try (FileWriter fw = new FileWriter(svgFile)) {
                fw.write(PlanPreviewRenderer.renderSvg(map, ir));
            }
            System.out.println("Saved 2D SVG Preview:   " + svgFile.getAbsolutePath());

        } catch (IOException e) {
            System.err.println("Failed to write output files: " + e.getMessage());
        }
    }
}
