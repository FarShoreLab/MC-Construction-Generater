package org.mcsettlement.mod.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import org.mcsettlement.mod.building.AdaptiveBuildingPlacer;
import org.mcsettlement.planner.preset.BuildingPreset;
import org.mcsettlement.planner.preset.BuildingPresetRegistry;

import java.util.List;

/**
 * In-game Minecraft commands for inspecting and placing Single-Building Presets.
 * Commands:
 *   /mcsettlement help
 *   /mcsettlement preset list
 *   /mcsettlement preset place <preset_id>
 */
public class PresetCommands {

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            registerCommands(dispatcher);
        });
    }

    public static void registerCommands(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(
            CommandManager.literal("mcsettlement")
                .then(CommandManager.literal("help").executes(ctx -> {
                    ServerCommandSource src = ctx.getSource();
                    src.sendMessage(Text.literal("§6==== [地形自适应建筑群生成系统] 指令帮助 ===="));
                    src.sendMessage(Text.literal("§e/mcsettlement preset list §7- 查看所有单体建筑预设"));
                    src.sendMessage(Text.literal("§e/mcsettlement preset place <id> §7- 在玩家当前位置生成指定单体建筑"));
                    src.sendMessage(Text.literal("§e按键 B §7- 呼出战术鸟瞰图规划视口"));
                    return 1;
                }))
                .then(CommandManager.literal("preset")
                    .then(CommandManager.literal("list").executes(ctx -> {
                        ServerCommandSource src = ctx.getSource();
                        List<BuildingPreset> presets = BuildingPresetRegistry.getInstance().getAllPresets();
                        src.sendMessage(Text.literal("§6==== 单体建筑预设清单 (" + presets.size() + "款) ===="));
                        for (BuildingPreset p : presets) {
                            MutableText line = Text.literal("§a▪ §f" + p.name + " §7[" + p.id + "] §8(" + p.sizeX + "x" + p.sizeZ + "x" + p.sizeY + ", " + p.category + ") ");
                            MutableText btn = Text.literal("§b[一键放置]")
                                    .styled(style -> style
                                            .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/mcsettlement preset place " + p.id))
                                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("点击立即在当前位置建造 " + p.name)))
                                    );
                            src.sendMessage(line.append(btn));
                        }
                        return presets.size();
                    }))
                    .then(CommandManager.literal("place")
                        .then(CommandManager.argument("preset_id", StringArgumentType.word())
                            .suggests((ctx, builder) -> {
                                for (String id : BuildingPresetRegistry.BUILTIN_PRESET_IDS) {
                                    if (id.startsWith(builder.getRemaining().toLowerCase())) {
                                        builder.suggest(id);
                                    }
                                }
                                return builder.buildFuture();
                            })
                            .executes(ctx -> {
                                ServerCommandSource src = ctx.getSource();
                                ServerPlayerEntity player = src.getPlayer();
                                if (player == null) {
                                    src.sendError(Text.literal("该指令仅支持玩家在游戏内执行！"));
                                    return 0;
                                }

                                String presetId = StringArgumentType.getString(ctx, "preset_id");
                                BuildingPreset preset = BuildingPresetRegistry.getInstance().getPreset(presetId);
                                if (preset == null) {
                                    src.sendError(Text.literal("未找到 ID 为 [" + presetId + "] 的单体建筑预设！使用 /mcsettlement preset list 查看可用预设。"));
                                    return 0;
                                }

                                BlockPos origin = player.getBlockPos().add(1, 0, 1);
                                String playerFacing = player.getHorizontalFacing().getOpposite().asString().toUpperCase();

                                boolean ok = AdaptiveBuildingPlacer.placeStandalonePreset(
                                        player.getServerWorld(), origin, presetId, playerFacing, "medieval_rustic"
                                );

                                if (ok) {
                                    src.sendMessage(Text.literal("§a[MCSettlement] 成功建造单体建筑预设: §f" + preset.name + " §a(朝向: " + playerFacing + ", 坐标: " + origin.toShortString() + ")"));
                                    return 1;
                                } else {
                                    src.sendError(Text.literal("建造单体建筑失败！"));
                                    return 0;
                                }
                            })
                        )
                    )
                )
        );
    }
}
