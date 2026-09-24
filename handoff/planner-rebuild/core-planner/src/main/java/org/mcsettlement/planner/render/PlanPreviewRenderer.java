package org.mcsettlement.planner.render;

import org.mcsettlement.planner.ir.PlanningIR;
import org.mcsettlement.planner.ir.PlanningIR.Plot;
import org.mcsettlement.planner.ir.PlanningIR.RoadEdge;
import org.mcsettlement.planner.ir.PlanningIR.RoadStep;
import org.mcsettlement.planner.terrain.HeightfieldMap;
import org.mcsettlement.planner.terrain.HeightfieldMap.ObstacleType;

import java.util.HashSet;
import java.util.Set;

/**
 * 2D Top-down Preview Renderer.
 * Generates ASCII text maps, SVG vector graphics, and pixel buffers for in-game GUIs.
 */
public class PlanPreviewRenderer {

    public static String renderAscii(HeightfieldMap map, PlanningIR ir) {
        int width = map.getWidth();
        int depth = map.getDepth();
        char[][] canvas = new char[depth][width];

        // 1. Draw base terrain & obstacles
        for (int z = 0; z < depth; z++) {
            for (int x = 0; x < width; x++) {
                ObstacleType obs = map.getLocalObstacle(x, z);
                float slope = map.getLocalSlope(x, z);

                if (obs == ObstacleType.WATER || obs == ObstacleType.WATER_DEEP) {
                    canvas[z][x] = '~'; // Water
                } else if (obs == ObstacleType.TREE_TRUNK) {
                    canvas[z][x] = 'T'; // Tree
                } else if (obs == ObstacleType.STEEP_CLIFF || slope > 1.2f) {
                    canvas[z][x] = '^'; // Cliff / Mountain ridge
                } else if (slope > 0.4f) {
                    canvas[z][x] = ':'; // Moderate slope
                } else {
                    canvas[z][x] = '.'; // Flat / Gentle ground
                }
            }
        }

        // 2. Draw roads
        for (RoadEdge edge : ir.transportNetwork.edges) {
            for (RoadStep step : edge.steps) {
                int lx = step.x - map.getMinX();
                int lz = step.z - map.getMinZ();
                if (map.inLocalBounds(lx, lz)) {
                    if ("bridge".equals(step.structure)) canvas[lz][lx] = '=';
                    else if ("stair".equals(step.structure)) canvas[lz][lx] = '#';
                    else canvas[lz][lx] = '*';
                }
            }
        }

        // 3. Draw building plots
        for (Plot p : ir.plots) {
            int minX = p.polygon2D.get(0)[0] - map.getMinX();
            int minZ = p.polygon2D.get(0)[1] - map.getMinZ();
            int maxX = p.polygon2D.get(2)[0] - map.getMinX();
            int maxZ = p.polygon2D.get(2)[1] - map.getMinZ();

            for (int z = minZ; z <= maxZ; z++) {
                for (int x = minX; x <= maxX; x++) {
                    if (map.inLocalBounds(x, z)) {
                        if (x == minX || x == maxX || z == minZ || z == maxZ) {
                            canvas[z][x] = 'B'; // Plot border
                        } else {
                            canvas[z][x] = ' '; // Plot interior
                        }
                    }
                }
            }

            // Draw entrance marker
            int ex = p.entrance.accessPoint[0] - map.getMinX();
            int ez = p.entrance.accessPoint[2] - map.getMinZ();
            if (map.inLocalBounds(ex, ez)) {
                canvas[ez][ex] = 'E';
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("=== Settlement Preview [%dx%d] Seed: %d ===\n",
                width, depth, ir.metadata.randomSeed));
        sb.append("Legend: [.] Ground  [:] Slope  [^] Cliff  [~] Water  [*] Road  [#] Stair  [=] Bridge  [B] Plot  [E] Entrance\n");
        for (int z = 0; z < depth; z++) {
            sb.append(new String(canvas[z])).append("\n");
        }
        return sb.toString();
    }

    public static String renderSvg(HeightfieldMap map, PlanningIR ir) {
        int width = map.getWidth();
        int depth = map.getDepth();
        int scale = 8;
        int svgWidth = width * scale;
        int svgHeight = depth * scale;

        StringBuilder svg = new StringBuilder();
        svg.append(String.format("<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 %d %d' width='%d' height='%d'>\n",
                svgWidth, svgHeight, svgWidth, svgHeight));
        svg.append("<style>\n");
        svg.append(".water { fill: #4a90e2; }\n");
        svg.append(".ground { fill: #7cb342; }\n");
        svg.append(".slope { fill: #c0ca33; }\n");
        svg.append(".cliff { fill: #8d6e63; }\n");
        svg.append(".tree { fill: #2e7d32; }\n");
        svg.append(".road { stroke: #d7ccc8; stroke-width: 4; fill: none; stroke-linecap: round; }\n");
        svg.append(".stair { stroke: #ff9800; stroke-width: 4; fill: none; stroke-dasharray: 2,2; }\n");
        svg.append(".bridge { stroke: #8d6e63; stroke-width: 6; fill: none; }\n");
        svg.append(".plot { fill: rgba(255, 235, 59, 0.4); stroke: #fbc02d; stroke-width: 2; }\n");
        svg.append("</style>\n");

        // Background terrain rects
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < depth; z++) {
                ObstacleType obs = map.getLocalObstacle(x, z);
                float slope = map.getLocalSlope(x, z);
                String css = "ground";
                if (obs == ObstacleType.WATER || obs == ObstacleType.WATER_DEEP) css = "water";
                else if (obs == ObstacleType.TREE_TRUNK) css = "tree";
                else if (obs == ObstacleType.STEEP_CLIFF || slope > 1.2f) css = "cliff";
                else if (slope > 0.4f) css = "slope";

                svg.append(String.format("<rect x='%d' y='%d' width='%d' height='%d' class='%s' />\n",
                        x * scale, z * scale, scale, scale, css));
            }
        }

        // Roads
        for (RoadEdge edge : ir.transportNetwork.edges) {
            for (int i = 1; i < edge.steps.size(); i++) {
                RoadStep s0 = edge.steps.get(i - 1);
                RoadStep s1 = edge.steps.get(i);
                int x0 = (s0.x - map.getMinX()) * scale + scale / 2;
                int z0 = (s0.z - map.getMinZ()) * scale + scale / 2;
                int x1 = (s1.x - map.getMinX()) * scale + scale / 2;
                int z1 = (s1.z - map.getMinZ()) * scale + scale / 2;

                String rClass = "road";
                if ("stair".equals(s1.structure)) rClass = "stair";
                else if ("bridge".equals(s1.structure)) rClass = "bridge";

                svg.append(String.format("<line x1='%d' y1='%d' x2='%d' y2='%d' class='%s' />\n",
                        x0, z0, x1, z1, rClass));
            }
        }

        // Plots
        for (Plot p : ir.plots) {
            int minX = (p.polygon2D.get(0)[0] - map.getMinX()) * scale;
            int minZ = (p.polygon2D.get(0)[1] - map.getMinZ()) * scale;
            int pw = (p.polygon2D.get(2)[0] - p.polygon2D.get(0)[0] + 1) * scale;
            int pd = (p.polygon2D.get(2)[1] - p.polygon2D.get(0)[1] + 1) * scale;

            svg.append(String.format("<rect x='%d' y='%d' width='%d' height='%d' class='plot' />\n",
                    minX, minZ, pw, pd));
            svg.append(String.format("<text x='%d' y='%d' font-size='10' fill='#333'>%s (Y=%d)</text>\n",
                    minX + 4, minZ + 14, p.id, p.elevation.baseElevation));
        }

        svg.append("</svg>\n");
        return svg.toString();
    }

    /**
     * Generates a 32-bit ARGB pixel buffer representing the tactical bird's eye map.
     */
    public static int[] renderPixelBuffer(HeightfieldMap map, PlanningIR ir) {
        int width = map.getWidth();
        int depth = map.getDepth();
        int[] pixels = new int[width * depth];

        for (int z = 0; z < depth; z++) {
            for (int x = 0; x < width; x++) {
                ObstacleType obs = map.getLocalObstacle(x, z);
                float slope = map.getLocalSlope(x, z);
                int y = map.getLocalSurfaceY(x, z);

                int color;
                if (obs == ObstacleType.WATER || obs == ObstacleType.WATER_DEEP) {
                    color = 0xFF2B5B84; // Water blue
                } else if (obs == ObstacleType.TREE_TRUNK) {
                    color = 0xFF1E5128; // Forest dark green
                } else if (obs == ObstacleType.STEEP_CLIFF || slope > 1.2f) {
                    color = 0xFF5D4037; // Cliff brown
                } else if (slope > 0.4f) {
                    color = 0xFF9E9D24; // Slope yellow-green
                } else {
                    // Elevation-shaded grass
                    int shade = Math.min(255, Math.max(100, 140 + y * 2));
                    color = 0xFF000000 | ((shade / 2) << 16) | (shade << 8) | (shade / 3);
                }
                pixels[z * width + x] = color;
            }
        }

        // Overlay roads
        if (ir != null) {
            for (RoadEdge edge : ir.transportNetwork.edges) {
                for (RoadStep step : edge.steps) {
                    int lx = step.x - map.getMinX();
                    int lz = step.z - map.getMinZ();
                    if (map.inLocalBounds(lx, lz)) {
                        int rColor = 0xFFEDE0D4; // Gravel white
                        if ("stair".equals(step.structure)) rColor = 0xFFFFA000; // Orange stair
                        else if ("bridge".equals(step.structure)) rColor = 0xFF795548; // Wood bridge
                        pixels[lz * width + lx] = rColor;
                    }
                }
            }

            // Overlay plots
            for (Plot p : ir.plots) {
                int minX = p.polygon2D.get(0)[0] - map.getMinX();
                int minZ = p.polygon2D.get(0)[1] - map.getMinZ();
                int maxX = p.polygon2D.get(2)[0] - map.getMinX();
                int maxZ = p.polygon2D.get(2)[1] - map.getMinZ();

                for (int pz = minZ; pz <= maxZ; pz++) {
                    for (int px = minX; px <= maxX; px++) {
                        if (map.inLocalBounds(px, pz)) {
                            if (px == minX || px == maxX || pz == minZ || pz == maxZ) {
                                pixels[pz * width + px] = 0xFFFFEB3B; // Yellow border
                            } else {
                                int existing = pixels[pz * width + px];
                                // Blend yellow overlay
                                pixels[pz * width + px] = existing ^ 0x00404000;
                            }
                        }
                    }
                }
            }
        }

        return pixels;
    }
}
