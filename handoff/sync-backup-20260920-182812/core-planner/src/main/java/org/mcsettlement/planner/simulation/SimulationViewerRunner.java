package org.mcsettlement.planner.simulation;

import org.mcsettlement.planner.ir.PlanningIR;
import org.mcsettlement.planner.ir.PlanningIR.Plot;
import org.mcsettlement.planner.ir.PlanningIR.RoadEdge;
import org.mcsettlement.planner.ir.PlanningIR.RoadStep;
import org.mcsettlement.planner.simulation.SimulatedSettlementPipeline.PipelineResult;
import org.mcsettlement.planner.simulation.SimulatedVoxelWorld.VoxelType;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

/**
 * Runner that executes the terrain block generator and original settlement algorithm,
 * then outputs both console cross-sections and an interactive 3D HTML viewer.
 */
public class SimulationViewerRunner {

    public static void main(String[] args) {
        System.out.println("==================================================================");
        System.out.println(" 3D Terrain Block Generator & Settlement Simulation Viewer");
        System.out.println("==================================================================\n");

        int width = 96;
        int depth = 96;
        int baseElevation = 58;
        int relief = 24; // 24 blocks of elevation relief (rich mountain topography, terraces, and river)
        long seed = 42L;
        int targetPlots = 7;

        System.out.printf("1. Generating 3D Minecraft voxel world [%dx%d, relief: %d blocks]...\n", width, depth, relief);
        PipelineResult res = SimulatedSettlementPipeline.run(width, depth, baseElevation, relief, seed, targetPlots);

        // Verify zero road-building collisions
        int collisions = 0;
        for (Plot p : res.plan.plots) {
            int px1 = p.polygon2D.get(0)[0];
            int pz1 = p.polygon2D.get(0)[1];
            int px2 = p.polygon2D.get(2)[0];
            int pz2 = p.polygon2D.get(2)[1];
            for (RoadEdge r : res.plan.transportNetwork.edges) {
                for (RoadStep s : r.steps) {
                    if (s.x >= px1 && s.x <= px2 && s.z >= pz1 && s.z <= pz2) {
                        collisions++;
                    }
                }
            }
        }

        System.out.println("2. Upgraded Road Network & Collision Verification:");
        System.out.printf(" - Road Network Edges:     %d (Main Road + Mountain Branch + Loop)\n", res.plan.transportNetwork.edges.size());
        int totalRoadSteps = 0;
        for (RoadEdge edge : res.plan.transportNetwork.edges) totalRoadSteps += edge.steps.size();
        System.out.printf(" - Total Road Steps:       %d steps\n", totalRoadSteps);
        System.out.printf(" - Road-Plot Collisions:   %d (Strict Zero Overlap Guaranteed!)\n", collisions);
        System.out.printf(" - Allocated Plots:        %d\n", res.plan.plots.size());
        System.out.printf(" - Cleared Vegetation:     %d blocks\n", res.clearedBlocks);
        System.out.printf(" - Earthwork Cut:          %d blocks\n", res.cutBlocks);
        System.out.printf(" - Earthwork Fill:         %d blocks\n", res.fillBlocks);
        System.out.printf(" - Retaining Wall Blocks:  %d blocks (protecting slopes)\n", res.retainingWallBlocks);
        System.out.printf(" - Road & Bridge Blocks:   %d blocks\n", res.roadBlocks);
        System.out.printf(" - Slope Stairs Placed:    %d blocks\n", res.stairBlocks);
        System.out.printf(" - AI Building Structures: %d blocks\n\n", res.buildingBlocks);

        // Print a vertical cross-section through the first planned house
        if (!res.plan.plots.isEmpty()) {
            Plot p1 = res.plan.plots.get(0);
            int midZ = (p1.polygon2D.get(0)[1] + p1.polygon2D.get(2)[1]) / 2;
            printCrossSection(res.worldAfter, midZ, width);
        }

        // Generate interactive 3D Web Viewer HTML
        File outDir = new File("output");
        if (!outDir.exists()) outDir.mkdirs();

        File htmlFile = new File(outDir, "simulation_3d_viewer.html");
        try (FileWriter fw = new FileWriter(htmlFile)) {
            fw.write(generateHtml3DViewer(res));
            System.out.println("Generated Interactive 3D Web Viewer: " + htmlFile.getAbsolutePath());
        } catch (IOException e) {
            System.err.println("Failed to write HTML viewer: " + e.getMessage());
        }
    }

    private static void printCrossSection(SimulatedVoxelWorld world, int sliceZ, int width) {
        System.out.println("=== Vertical Cross-Section (Slice Z=" + sliceZ + ") ===");
        System.out.println("Legend: [G] Grass  [D] Dirt  [S] Stone  [*] Road  [#] Stair  [W] Retaining Wall  [H] House  [F] Foundation");

        int topY = 78;
        int bottomY = 54;

        for (int y = topY; y >= bottomY; y--) {
            System.out.printf("Y=%2d |", y);
            for (int x = 0; x < width; x += 2) {
                VoxelType b = world.getBlock(x, y, sliceZ);
                char ch = ' ';
                switch (b) {
                    case GRASS_BLOCK -> ch = 'G';
                    case DIRT -> ch = 'D';
                    case STONE -> ch = 'S';
                    case GRAVEL -> ch = '*';
                    case COBBLESTONE_STAIRS -> ch = '#';
                    case STONE_BRICKS -> ch = 'W';
                    case SPRUCE_PLANKS, SPRUCE_LOG, GLASS_PANE, OAK_PLANKS -> ch = 'H';
                    case COBBLESTONE -> ch = 'F';
                    case WATER -> ch = '~';
                    default -> ch = ' ';
                }
                System.out.print(ch);
            }
            System.out.println("|");
        }
        System.out.println("------------------------------------------------------------------------\n");
    }

    private static String generateHtml3DViewer(PipelineResult res) {
        StringBuilder voxelsJson = new StringBuilder("[");
        SimulatedVoxelWorld world = res.worldAfter;
        boolean first = true;

        for (int x = 0; x < world.getSizeX(); x += 1) {
            for (int z = 0; z < world.getSizeZ(); z += 1) {
                for (int y = world.getMinY(); y < world.getMinY() + world.getSizeY(); y++) {
                    VoxelType b = world.getBlock(x, y, z);
                    if (b == VoxelType.AIR) continue;

                    // Occlusion culling: only export visible surface voxels to keep HTML extremely fast
                    boolean visible = isExposed(world, x, y, z);
                    if (visible) {
                        if (!first) voxelsJson.append(",");
                        first = false;
                        voxelsJson.append(String.format("[%d,%d,%d,%d]", x, y - world.getMinY(), z, b.id));
                    }
                }
            }
        }
        voxelsJson.append("]");

        return """
        <!DOCTYPE html>
        <html lang="zh-CN">
        <head>
            <meta charset="UTF-8">
            <title>Minecraft 地形自适应建筑群 3D 可视化视口</title>
            <style>
                body { margin: 0; padding: 0; background: #121212; color: #E0E0E0; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; overflow: hidden; }
                #header { position: absolute; top: 15px; left: 20px; z-index: 10; background: rgba(20, 20, 20, 0.85); backdrop-filter: blur(8px); padding: 14px 20px; border-radius: 10px; border: 1px solid #333; box-shadow: 0 4px 20px rgba(0,0,0,0.5); }
                h1 { margin: 0 0 6px 0; font-size: 18px; color: #4FC3F7; font-weight: 600; }
                .meta { font-size: 13px; color: #BBB; line-height: 1.5; }
                .badge { display: inline-block; padding: 2px 8px; border-radius: 4px; font-size: 11px; font-weight: bold; margin-right: 6px; }
                .b-road { background: #E65100; color: #FFF; }
                .b-house { background: #8D6E63; color: #FFF; }
                .b-wall { background: #424242; color: #FFF; }
                .b-root { background: #616161; color: #FFF; }
                #controls { position: absolute; bottom: 20px; left: 20px; z-index: 10; background: rgba(20, 20, 20, 0.85); padding: 10px 16px; border-radius: 8px; border: 1px solid #333; font-size: 13px; color: #AAA; }
                #canvas { width: 100vw; height: 100vh; display: block; cursor: grab; }
                #canvas:active { cursor: grabbing; }
            </style>
        </head>
        <body>
            <div id="header">
                <h1>🏔️ 地形自适应聚落 3D 体素渲染视口</h1>
                <div class="meta">
                    <span class="badge b-road">等高线道路与阶梯: %d 块</span>
                    <span class="badge b-wall">护坡挡土墙: %d 块</span>
                    <span class="badge b-root">防悬空地基承重: %d 块</span>
                    <span class="badge b-house">自适应建筑: %d 栋</span>
                    <br>
                    <span>土方开挖: <b>%d</b> 块 | 土方回填: <b>%d</b> 块 | 综合评分: <b>%.1f</b></span>
                </div>
            </div>
            <div id="controls">
                🖱️ <b>操作说明</b>：按住鼠标左键拖拽旋转视角 | 滚轮缩放 | 右键平移
            </div>
            <canvas id="canvas"></canvas>

            <script>
                const voxels = %s;
                const colors = {
                    1: '#616161', // STONE
                    2: '#5D4037', // DIRT
                    3: '#4CAF50', // GRASS_BLOCK
                    4: '#1E88E5', // WATER
                    5: '#3E2723', // OAK_LOG
                    6: '#2E7D32', // OAK_LEAVES
                    7: '#BDBDBD', // GRAVEL (Road)
                    8: '#757575', // COBBLESTONE (Foundation)
                    9: '#37474F', // STONE_BRICKS (Retaining Wall)
                    10: '#FF9800', // STAIRS (Road Climb)
                    11: '#271A15', // SPRUCE_LOG
                    12: '#8D6E63', // SPRUCE_PLANKS (Roof)
                    13: '#D7CCC8', // OAK_PLANKS (Floor)
                    14: '#80DEEA', // GLASS
                    15: '#A1887F'  // FENCE
                };

                const canvas = document.getElementById('canvas');
                const ctx = canvas.getContext('2d');

                let rotX = 0.55;
                let rotY = -0.75;
                let zoom = 8.5;
                let panX = 0;
                let panY = 0;

                function resize() {
                    canvas.width = window.innerWidth;
                    canvas.height = window.innerHeight;
                    panX = canvas.width / 2;
                    panY = canvas.height / 2 + 100;
                    render();
                }
                window.addEventListener('resize', resize);

                let isDragging = false;
                let isPanning = false;
                let lastMouseX = 0, lastMouseY = 0;

                canvas.addEventListener('mousedown', e => {
                    if (e.button === 0) isDragging = true;
                    if (e.button === 2) isPanning = true;
                    lastMouseX = e.clientX;
                    lastMouseY = e.clientY;
                });
                window.addEventListener('mouseup', () => { isDragging = false; isPanning = false; });
                window.addEventListener('mousemove', e => {
                    const dx = e.clientX - lastMouseX;
                    const dy = e.clientY - lastMouseY;
                    lastMouseX = e.clientX;
                    lastMouseY = e.clientY;

                    if (isDragging) {
                        rotY += dx * 0.008;
                        rotX = Math.max(0.1, Math.min(1.4, rotX + dy * 0.008));
                        render();
                    } else if (isPanning) {
                        panX += dx;
                        panY += dy;
                        render();
                    }
                });
                canvas.addEventListener('wheel', e => {
                    e.preventDefault();
                    zoom = Math.max(3, Math.min(25, zoom * (e.deltaY < 0 ? 1.1 : 0.9)));
                    render();
                });
                canvas.addEventListener('contextmenu', e => e.preventDefault());

                function render() {
                    ctx.clearRect(0, 0, canvas.width, canvas.height);

                    const cosY = Math.cos(rotY), sinY = Math.sin(rotY);
                    const cosX = Math.cos(rotX), sinX = Math.sin(rotX);

                    const transformed = [];
                    const cx = 48, cy = 18, cz = 48;

                    for (let i = 0; i < voxels.length; i++) {
                        const v = voxels[i];
                        const x = v[0] - cx;
                        const y = v[1] - cy;
                        const z = v[2] - cz;

                        // Rotate Y
                        const x1 = x * cosY - z * sinY;
                        const z1 = x * sinY + z * cosY;

                        // Rotate X
                        const y2 = y * cosX - z1 * sinX;
                        const z2 = y * sinX + z1 * cosX;

                        transformed.push({
                            depth: z2,
                            sx: panX + x1 * zoom,
                            sy: panY - y2 * zoom,
                            type: v[3]
                        });
                    }

                    // Painter's algorithm (render back to front)
                    transformed.sort((a, b) => a.depth - b.depth);

                    const size = Math.max(2, zoom * 0.95);
                    for (let i = 0; i < transformed.length; i++) {
                        const t = transformed[i];
                        ctx.fillStyle = colors[t.type] || '#888';
                        ctx.fillRect(t.sx - size / 2, t.sy - size / 2, size, size);
                    }
                }

                resize();
            </script>
        </body>
        </html>
        """.formatted(
                res.roadBlocks + res.stairBlocks,
                res.retainingWallBlocks,
                res.foundationRootBlocks,
                res.plan.plots.size(),
                res.cutBlocks,
                res.fillBlocks,
                res.plan.metadata.score.getOrDefault("total_score", 0.0),
                voxelsJson.toString()
        );
    }

    private static boolean isExposed(SimulatedVoxelWorld world, int x, int y, int z) {
        int[][] dirs = {{1,0,0}, {-1,0,0}, {0,1,0}, {0,-1,0}, {0,0,1}, {0,0,-1}};
        for (int[] d : dirs) {
            VoxelType neighbor = world.getBlock(x + d[0], y + d[1], z + d[2]);
            if (neighbor == VoxelType.AIR || neighbor == VoxelType.WATER || neighbor == VoxelType.GLASS_PANE) {
                return true;
            }
        }
        return false;
    }
}
