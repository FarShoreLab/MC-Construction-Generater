package org.mcsettlement.mod.client.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.mcsettlement.llm.LlmStrategyManager;
import org.mcsettlement.llm.PlanningIntent;
import org.mcsettlement.mod.ModConfig;
import org.mcsettlement.mod.constructor.WorldConstructor;
import org.mcsettlement.mod.constructor.WorldConstructor.ConstructionResult;
import org.mcsettlement.mod.scanner.WorldTerrainScanner;
import org.mcsettlement.planner.SettlementPlanner;
import org.mcsettlement.planner.SettlementPlanner.PlanRequest;
import org.mcsettlement.planner.ir.PlanningIR;
import org.mcsettlement.planner.ir.PlanningIR.Plot;
import org.mcsettlement.planner.ir.PlanningIR.RoadEdge;
import org.mcsettlement.planner.ir.PlanningIR.RoadStep;
import org.mcsettlement.planner.terrain.HeightfieldMap;
import org.mcsettlement.planner.terrain.HeightfieldMap.ObstacleType;

/**
 * In-game Tactical Map Screen supporting both 2D Bird's-Eye View and 3D Isometric View,
 * with AI-driven architectural intent generation and physical construction.
 */
public class TacticalMapScreen extends Screen {

    private final int minX, minZ, maxX, maxZ;
    private HeightfieldMap heightfield;
    private PlanningIR currentPlan;
    private String currentTheme = "medieval_rustic";
    private String statusMessage = "就绪：可切换【2D鸟瞰/3D轴测】，输入提示词由AI生成建筑群。";

    private boolean is3dMode = false;
    private float isoAngle = 0.0f; // Rotation angle for 3D view
    private int dragStartX = 0;
    private boolean isDragging = false;

    private TextFieldWidget promptField;
    private ButtonWidget planButton;
    private ButtonWidget constructButton;
    private ButtonWidget viewModeButton;

    public TacticalMapScreen(int minX, int minZ, int maxX, int maxZ) {
        super(Text.literal("聚落规划战术地图 (2D/3D Tactical View)"));
        this.minX = minX;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxZ = maxZ;
    }

    @Override
    protected void init() {
        super.init();

        // 1. Scan the selected region
        if (this.client != null && this.client.world != null) {
            this.heightfield = WorldTerrainScanner.scanRegion(this.client.world, minX, minZ, maxX, maxZ);
        }

        int sidePanelX = this.width - 210;

        // Prompt input
        promptField = new TextFieldWidget(this.textRenderer, sidePanelX, 50, 190, 20, Text.literal("设计提示词"));
        promptField.setMaxLength(120);
        promptField.setText(ModConfig.get().defaultThemePrompt);
        this.addDrawableChild(promptField);

        // Plan button (invokes AI Intent + Spatial Solver)
        planButton = ButtonWidget.builder(Text.literal("AI 智能规划 (Plan)").formatted(Formatting.GOLD), b -> runPlanning())
                .dimensions(sidePanelX, 80, 190, 20)
                .build();
        this.addDrawableChild(planButton);

        // View Mode Toggle (2D Top-down vs 3D Isometric)
        viewModeButton = ButtonWidget.builder(Text.literal("视图切换: 2D 鸟瞰").formatted(Formatting.AQUA), b -> toggleViewMode())
                .dimensions(sidePanelX, 110, 190, 20)
                .build();
        this.addDrawableChild(viewModeButton);

        // Construct button
        constructButton = ButtonWidget.builder(Text.literal("确认施工 (Construct)").formatted(Formatting.GREEN), b -> runConstruction())
                .dimensions(sidePanelX, 140, 190, 20)
                .build();
        constructButton.active = false;
        this.addDrawableChild(constructButton);

        // Close button
        this.addDrawableChild(ButtonWidget.builder(Text.literal("关闭 (Close)"), b -> this.close())
                .dimensions(sidePanelX, this.height - 35, 190, 20)
                .build());
    }

    private void toggleViewMode() {
        is3dMode = !is3dMode;
        if (viewModeButton != null) {
            viewModeButton.setMessage(Text.literal(is3dMode ? "视图切换: 3D 轴测" : "视图切换: 2D 鸟瞰")
                    .formatted(is3dMode ? Formatting.LIGHT_PURPLE : Formatting.AQUA));
        }
    }

    private void runPlanning() {
        if (this.heightfield == null || this.client == null) return;
        this.statusMessage = "正在读取需求并进行有预算的几何搜索...";
        this.currentPlan = null;
        planButton.active = false;
        constructButton.active = false;
        final String promptText = promptField != null ? promptField.getText() : "";
        final HeightfieldMap snapshot = this.heightfield;
        new Thread(() -> {
            try {
                LlmStrategyManager.LlmConfig llmCfg = new LlmStrategyManager.LlmConfig();
                ModConfig cfg = ModConfig.get();
                if (cfg != null) {
                    llmCfg.apiEndpoint = cfg.llmApiEndpoint;
                    llmCfg.apiKey = cfg.llmApiKey;
                    llmCfg.modelName = cfg.llmModelName;
                    llmCfg.preferLocalAgy = cfg.preferLocalAgy;
                }
                PlanningIntent intent = LlmStrategyManager.resolveIntent(snapshot, promptText, llmCfg);
                // Stable seed: identical terrain, intent and budget produce identical geometry.
                PlanningIR ir = SettlementPlanner.plan(snapshot, intent.toPlanRequest(42L));
                ir.search.modelCalls = intent.modelCalls;
                this.client.execute(() -> {
                    this.currentTheme = intent.settlementTheme;
                    this.currentPlan = ir;
                    this.statusMessage = String.format("%s | 地块 %d | 未满足 %d 项%s", ir.status,
                            ir.plots.size(), ir.unmetRequirements.size(),
                            ir.unmetRequirements.isEmpty() ? "" : " | " + ir.unmetRequirements.get(0).reason);
                    planButton.active = true;
                    constructButton.active = ("COMPLETE".equals(ir.status) || "PARTIAL".equals(ir.status)) && !ir.plots.isEmpty();
                    constructButton.setMessage(Text.literal("PARTIAL".equals(ir.status) ? "确认施工已满足部分" : "确认施工"));
                });
            } catch (Exception e) {
                this.client.execute(() -> { this.statusMessage = "规划失败: " + e.getMessage(); planButton.active = true; });
            }
        }, "settlement-planning").start();
    }

    private void runConstruction() {
        if (currentPlan == null || client == null || client.world == null) return;
        var server = client.getServer();
        if (server == null) {
            statusMessage = "此版本仅支持本地整合服务器施工；远程服务器需要服务端协议，未实施写入。";
            return;
        }
        var dimension = client.world.getRegistryKey();
        final PlanningIR plan = currentPlan;
        final String theme = currentTheme;
        planButton.active = false;
        constructButton.active = false;
        statusMessage = "正在服务端复核地形并施工...";
        server.execute(() -> {
            try {
                var world = server.getWorld(dimension);
                if (world == null) throw new IllegalStateException("目标维度已卸载");
                ConstructionResult result = WorldConstructor.execute(world, plan, theme, "");
                client.execute(() -> {
                    statusMessage = String.format("%s 施工完成: 建筑 %d，挖 %d / 填 %d；重新扫描后才能再施工",
                            plan.status, result.buildingsConstructed, result.cutBlocks, result.fillBlocks);
                    currentPlan = null;
                    heightfield = WorldTerrainScanner.scanRegion(client.world, minX, minZ, maxX, maxZ);
                    planButton.active = true;
                });
            } catch (Exception e) {
                client.execute(() -> { statusMessage = "施工停止: " + e.getMessage(); planButton.active = true; });
            }
        });
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && mouseX < this.width - 220) {
            isDragging = true;
            dragStartX = (int) mouseX;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            isDragging = false;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (isDragging && is3dMode) {
            isoAngle += (float) deltaX * 0.02f;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context);

        // Header Title
        context.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("Minecraft 地形自适应聚落规划系统 (2D 鸟瞰 / 3D 轴测)").formatted(Formatting.BOLD, Formatting.AQUA),
                this.width / 2, 12, 0xFFFFFF);

        int mapStartX = 20;
        int mapStartY = 40;
        int maxViewWidth = this.width - 240;
        int maxViewHeight = this.height - 70;

        if (this.heightfield != null) {
            if (is3dMode) {
                render3dIsometric(context, mapStartX, mapStartY, maxViewWidth, maxViewHeight);
            } else {
                render2dBirdsEye(context, mapStartX, mapStartY, maxViewWidth, maxViewHeight, mouseX, mouseY);
            }
        }

        // Side panel labels
        int sidePanelX = this.width - 210;
        context.drawTextWithShadow(this.textRenderer, Text.literal("设计提示词 (Prompt):"), sidePanelX, 38, 0xAAAAAA);

        // Status bar
        context.drawTextWithShadow(this.textRenderer, Text.literal(this.statusMessage).formatted(Formatting.GREEN),
                20, this.height - 20, 0xFFFFFF);

        super.render(context, mouseX, mouseY, delta);
    }

    private void render2dBirdsEye(DrawContext context, int mapStartX, int mapStartY, int maxViewWidth, int maxViewHeight, int mouseX, int mouseY) {
        int w = heightfield.getWidth();
        int d = heightfield.getDepth();
        int pixelScale = Math.max(1, Math.min(maxViewWidth / w, maxViewHeight / d));

        context.fill(mapStartX - 2, mapStartY - 2, mapStartX + w * pixelScale + 2, mapStartY + d * pixelScale + 2, 0xFF101010);

        for (int z = 0; z < d; z++) {
            for (int x = 0; x < w; x++) {
                int px = mapStartX + x * pixelScale;
                int pz = mapStartY + z * pixelScale;

                ObstacleType obs = heightfield.getLocalObstacle(x, z);
                float slope = heightfield.getLocalSlope(x, z);
                int y = heightfield.getLocalSurfaceY(x, z);

                int color;
                if (obs == ObstacleType.WATER || obs == ObstacleType.WATER_DEEP) color = 0xFF2B5B84;
                else if (obs == ObstacleType.TREE_TRUNK) color = 0xFF1E5128;
                else if (obs == ObstacleType.STEEP_CLIFF || slope > 1.2f) color = 0xFF5D4037;
                else if (slope > 0.4f) color = 0xFF8C8A36;
                else {
                    int shade = Math.min(255, Math.max(100, 140 + y * 2));
                    color = 0xFF000000 | ((shade / 2) << 16) | (shade << 8) | (shade / 3);
                }
                context.fill(px, pz, px + pixelScale, pz + pixelScale, color);
            }
        }

        // Draw Plan Overlay
        if (this.currentPlan != null) {
            for (RoadEdge edge : currentPlan.transportNetwork.edges) {
                for (RoadStep step : edge.steps) {
                    int lx = step.x - heightfield.getMinX();
                    int lz = step.z - heightfield.getMinZ();
                    if (heightfield.inLocalBounds(lx, lz)) {
                        int px = mapStartX + lx * pixelScale;
                        int pz = mapStartY + lz * pixelScale;
                        int rColor = "stair".equals(step.structure) ? 0xFFFFA000 : 0xFFEDE0D4;
                        context.fill(px, pz, px + pixelScale, pz + pixelScale, rColor);
                    }
                }
            }

            for (Plot plot : currentPlan.plots) {
                int px1 = mapStartX + (plot.polygon2D.get(0)[0] - heightfield.getMinX()) * pixelScale;
                int pz1 = mapStartY + (plot.polygon2D.get(0)[1] - heightfield.getMinZ()) * pixelScale;
                int px2 = mapStartX + (plot.polygon2D.get(2)[0] - heightfield.getMinX() + 1) * pixelScale;
                int pz2 = mapStartY + (plot.polygon2D.get(2)[1] - heightfield.getMinZ() + 1) * pixelScale;
                context.fill(px1, pz1, px2, pz2, 0x44FFFF00);
                context.drawBorder(px1, pz1, px2 - px1, pz2 - pz1, 0xFFFFEB3B);
            }
        }

        // Mouse hover inspector
        if (mouseX >= mapStartX && mouseX < mapStartX + w * pixelScale && mouseY >= mapStartY && mouseY < mapStartY + d * pixelScale) {
            int hx = (mouseX - mapStartX) / pixelScale;
            int hz = (mouseY - mapStartY) / pixelScale;
            if (heightfield.inLocalBounds(hx, hz)) {
                int wx = heightfield.getMinX() + hx;
                int wz = heightfield.getMinZ() + hz;
                int wy = heightfield.getLocalSurfaceY(hx, hz);
                float sl = heightfield.getLocalSlope(hx, hz);
                context.drawTextWithShadow(this.textRenderer,
                        Text.literal(String.format("坐标: (%d, %d, %d) 坡度: %.2f", wx, wy, wz, sl)).formatted(Formatting.YELLOW),
                        mapStartX + 6, mapStartY + 6, 0xFFFFFF);
            }
        }
    }

    private void render3dIsometric(DrawContext context, int mapStartX, int mapStartY, int maxViewWidth, int maxViewHeight) {
        int w = heightfield.getWidth();
        int d = heightfield.getDepth();
        int originX = mapStartX + maxViewWidth / 2;
        int originY = mapStartY + maxViewHeight / 2 + 50;
        int isoScale = Math.max(2, maxViewWidth / (w + d));

        context.fill(mapStartX - 2, mapStartY - 2, mapStartX + maxViewWidth, mapStartY + maxViewHeight, 0xFF0A0A0A);
        context.drawTextWithShadow(this.textRenderer, Text.literal("3D 轴测透视视图 (按住鼠标左键横向拖拽旋转视角)"), mapStartX + 8, mapStartY + 8, 0xAAAAAA);

        float cosA = (float) Math.cos(isoAngle);
        float sinA = (float) Math.sin(isoAngle);
        int baseY = 60;

        // Render back-to-front in isometric projection
        for (int z = 0; z < d; z += 2) {
            for (int x = 0; x < w; x += 2) {
                float rx = (x - w / 2f) * cosA - (z - d / 2f) * sinA;
                float rz = (x - w / 2f) * sinA + (z - d / 2f) * cosA;

                int y = heightfield.getLocalSurfaceY(x, z);
                int sx = originX + (int) ((rx - rz) * isoScale);
                int sy = originY + (int) ((rx + rz) * isoScale * 0.5f) - (y - baseY) * (isoScale + 1);

                if (sx >= mapStartX && sx < mapStartX + maxViewWidth && sy >= mapStartY && sy < mapStartY + maxViewHeight) {
                    ObstacleType obs = heightfield.getLocalObstacle(x, z);
                    int color = 0xFF4CAF50;
                    if (obs == ObstacleType.WATER) color = 0xFF1E88E5;
                    else if (obs == ObstacleType.TREE_TRUNK) color = 0xFF3E2723;
                    else if (heightfield.getLocalSlope(x, z) > 0.8f) color = 0xFF795548;

                    // Plot and road highlights in 3D
                    if (this.currentPlan != null) {
                        for (Plot p : currentPlan.plots) {
                            if (x >= p.polygon2D.get(0)[0] - heightfield.getMinX() && x <= p.polygon2D.get(2)[0] - heightfield.getMinX() &&
                                z >= p.polygon2D.get(0)[1] - heightfield.getMinZ() && z <= p.polygon2D.get(2)[1] - heightfield.getMinZ()) {
                                color = 0xFFFFEB3B; // Yellow plot
                                // Draw 3D building column
                                context.fill(sx - isoScale, sy - 8, sx + isoScale, sy, 0xFF8D6E63);
                                break;
                            }
                        }
                    }

                    context.fill(sx - isoScale, sy - isoScale / 2, sx + isoScale, sy + isoScale / 2, color);
                }
            }
        }
    }
}
