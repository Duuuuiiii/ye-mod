package com.ye.mod.modules.render;

import com.ye.mod.core.Category;
import com.ye.mod.core.Module;
import com.ye.mod.core.setting.BoolSetting;
import com.ye.mod.core.setting.DoubleSetting;
import com.ye.mod.ui.AppleUiRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Smart hybrid render limiter / circuit breaker (Option A):
 * - Keeps the nearest N entities / block entities per type when counts exceed threshold.
 * - Triggers emergency circuit-breaking for offending types when FPS drops below threshold.
 * - Optionally culls sign text rendering when signs are excessive, or skips particles at low FPS.
 */
public class AntiLagModule extends Module {
    public final BoolSetting limitEntities = addSetting(new BoolSetting(
            "limitEntities", "限制过多实体", "同类生物/掉落物/盔甲架过多时，只渲染最近的 N 个或低 FPS 熔断", true
    ));

    public final BoolSetting limitBlockEntities = addSetting(new BoolSetting(
            "limitBlockEntities", "限制过多方块实体", "告示牌/箱子/旗帜/头颅等同类型过多时限流或熔断", true
    ));

    public final BoolSetting cullSignText = addSetting(new BoolSetting(
            "cullSignText", "告示牌超限隐藏文字", "告示牌过多时只保留木板，跳过文字渲染以大幅降低卡顿", true
    ));

    public final BoolSetting limitParticles = addSetting(new BoolSetting(
            "limitParticles", "低 FPS 跳过粒子", "当 FPS 低于熔断阈值时暂时禁用新粒子生成与渲染", true
    ));

    public final DoubleSetting maxPerType = addSetting(new DoubleSetting(
            "maxPerType", "同类数量阈值", "只有当 FPS 跌破熔断阈值且某类数量超过此值时才暂停渲染", 128.0, 32.0, 500.0, 16.0
    ));

    public final DoubleSetting panicFpsThreshold = addSetting(new DoubleSetting(
            "panicFpsThreshold", "卡死熔断 FPS", "只有 FPS 真正跌破此值（真卡死）时才触发暂停渲染", 10.0, 3.0, 30.0, 1.0
    ));

    public final BoolSetting showHudAlert = addSetting(new BoolSetting(
            "showHudAlert", "显示防卡死提示", "触发限流或熔断时在屏幕顶部显示状态胶囊", true
    ));

    public final BoolSetting panicFreeze = addSetting(new BoolSetting(
            "panicFreeze", "紧急全冻结", "立即隐藏周围所有非玩家实体与方块实体（建议配合快捷键）", false
    ));

    private final Map<EntityType<?>, Double> entityCutoffSqr = new HashMap<>();
    private final Set<EntityType<?>> brokenEntityTypes = new HashSet<>();
    private final Map<EntityType<?>, Integer> entityCounts = new HashMap<>();

    private final Map<BlockEntityType<?>, List<Double>> blockEntitySampleDists = new HashMap<>();
    private final Map<BlockEntityType<?>, Double> blockEntityCutoffSqr = new HashMap<>();
    private final Set<BlockEntityType<?>> brokenBlockEntityTypes = new HashSet<>();
    private final Map<BlockEntityType<?>, Integer> blockEntityCounts = new HashMap<>();

    private int tickCounter = 0;
    private int brokenCooldownTicks = 0;
    private int culledThisInterval = 0;
    private boolean signTextCulled = false;
    private String activeAlertText = "";

    public AntiLagModule() {
        super("anti_lag", "防卡保护", "AntiLag", "过多实体与低帧率自动熔断保护", Category.UTILITY);
        setEnabled(true);
    }

    @Override
    protected void onDisable() {
        clearState();
    }

    private void clearState() {
        entityCutoffSqr.clear();
        brokenEntityTypes.clear();
        entityCounts.clear();
        blockEntitySampleDists.clear();
        blockEntityCutoffSqr.clear();
        brokenBlockEntityTypes.clear();
        blockEntityCounts.clear();
        signTextCulled = false;
        activeAlertText = "";
        culledThisInterval = 0;
    }

    @Override
    public void onTick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            clearState();
            return;
        }

        tickCounter++;
        if (brokenCooldownTicks > 0) {
            brokenCooldownTicks--;
        }

        // Recompute cutoffs every 4 ticks (~5 times per second) to keep overhead near zero
        if (tickCounter % 4 != 0) {
            return;
        }

        int maxN = Math.max(32, (int) Math.round(maxPerType.get()));
        int fps = mc.getFps();
        int panicFps = (int) Math.round(panicFpsThreshold.get());
        boolean lowFps = fps > 0 && fps < panicFps;
        boolean recoveredFps = fps >= panicFps + 6;

        if (recoveredFps && brokenCooldownTicks <= 0) {
            brokenEntityTypes.clear();
            brokenBlockEntityTypes.clear();
        }

        // Do NOT throttle anything while FPS is healthy!
        if (!lowFps && brokenEntityTypes.isEmpty() && brokenBlockEntityTypes.isEmpty() && !panicFreeze.get()) {
            entityCutoffSqr.clear();
            blockEntityCutoffSqr.clear();
            blockEntitySampleDists.clear();
            signTextCulled = false;
            activeAlertText = "";
            return;
        }

        int throttledTypes = 0;
        int brokenTypes = 0;
        String topOffenderName = "";
        int topOffenderCount = 0;

        // 1. Evaluate Entities
        entityCutoffSqr.clear();
        entityCounts.clear();
        if (limitEntities.get()) {
            Map<EntityType<?>, List<Double>> grouped = new HashMap<>();
            for (Entity e : mc.level.entitiesForRendering()) {
                if (e == null || e instanceof Player || e == mc.player.getVehicle()) {
                    continue;
                }
                double d2 = e.distanceToSqr(mc.player);
                grouped.computeIfAbsent(e.getType(), k -> new ArrayList<>()).add(d2);
            }

            for (Map.Entry<EntityType<?>, List<Double>> entry : grouped.entrySet()) {
                EntityType<?> type = entry.getKey();
                List<Double> dists = entry.getValue();
                int count = dists.size();
                entityCounts.put(type, count);

                if (count > maxN) {
                    throttledTypes++;
                    if (count > topOffenderCount) {
                        topOffenderCount = count;
                        topOffenderName = type.getDescription().getString();
                    }
                    if (lowFps) {
                        brokenEntityTypes.add(type);
                        brokenCooldownTicks = 40;
                    }
                    if (brokenEntityTypes.contains(type)) {
                        brokenTypes++;
                        entityCutoffSqr.put(type, -1.0);
                    } else {
                        Collections.sort(dists);
                        entityCutoffSqr.put(type, dists.get(maxN - 1));
                    }
                }
            }
        }

        // 2. Evaluate Block Entities from previous interval's samples
        blockEntityCutoffSqr.clear();
        blockEntityCounts.clear();
        boolean anySignExcessive = false;

        if (limitBlockEntities.get()) {
            for (Map.Entry<BlockEntityType<?>, List<Double>> entry : blockEntitySampleDists.entrySet()) {
                BlockEntityType<?> type = entry.getKey();
                List<Double> dists = entry.getValue();
                int count = dists.size();
                blockEntityCounts.put(type, count);

                if (type == BlockEntityType.SIGN || type == BlockEntityType.HANGING_SIGN) {
                    if (count > maxN) {
                        anySignExcessive = true;
                    }
                }

                if (count > maxN) {
                    throttledTypes++;
                    if (count > topOffenderCount) {
                        topOffenderCount = count;
                        topOffenderName = describeBlockEntityType(type);
                    }
                    if (lowFps) {
                        brokenBlockEntityTypes.add(type);
                        brokenCooldownTicks = 40;
                    }
                    if (brokenBlockEntityTypes.contains(type)) {
                        brokenTypes++;
                        blockEntityCutoffSqr.put(type, -1.0);
                    } else {
                        Collections.sort(dists);
                        blockEntityCutoffSqr.put(type, dists.get(maxN - 1));
                    }
                } else if (brokenBlockEntityTypes.contains(type) && brokenCooldownTicks <= 0) {
                    brokenBlockEntityTypes.remove(type);
                }
            }

            // Keep already-broken block entity types broken during cooldown
            for (BlockEntityType<?> broken : brokenBlockEntityTypes) {
                blockEntityCutoffSqr.putIfAbsent(broken, -1.0);
                if (!blockEntityCounts.containsKey(broken)) {
                    brokenTypes++;
                    if (topOffenderName.isEmpty()) {
                        topOffenderName = describeBlockEntityType(broken);
                    }
                }
            }
        }
        blockEntitySampleDists.clear();

        signTextCulled = cullSignText.get() && (anySignExcessive || (lowFps && panicFps > 0));

        if (panicFreeze.get()) {
            activeAlertText = "⚠ 防卡死: 紧急全冻结已开启 (隐藏非玩家对象)";
        } else if (brokenTypes > 0) {
            activeAlertText = "⚠ 防卡死熔断: 已暂停 " + topOffenderName
                    + (topOffenderCount > 0 ? " (" + topOffenderCount + ")" : "")
                    + (brokenTypes > 1 ? " 等" + brokenTypes + "类" : "");
        } else if (throttledTypes > 0) {
            activeAlertText = "🛡 防卡死限流: " + topOffenderName + " (" + topOffenderCount + " → 仅渲染最近 " + maxN + ")";
        } else if (signTextCulled) {
            activeAlertText = "🛡 防卡死: 低FPS已跳过告示牌文字渲染";
        } else {
            activeAlertText = "";
        }
        culledThisInterval = 0;
    }

    public boolean shouldSkipEntity(Entity entity) {
        if (!isEnabled() || entity == null) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        if (entity instanceof Player || entity == mc.player.getVehicle()) return false;

        if (panicFreeze.get()) {
            culledThisInterval++;
            return true;
        }
        if (!limitEntities.get()) return false;

        Double cutoff = entityCutoffSqr.get(entity.getType());
        if (cutoff == null) return false;
        if (cutoff < 0 || entity.distanceToSqr(mc.player) > cutoff) {
            culledThisInterval++;
            return true;
        }
        return false;
    }

    public boolean shouldSkipBlockEntity(BlockEntity blockEntity) {
        if (!isEnabled() || blockEntity == null) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;

        BlockPos pos = blockEntity.getBlockPos();
        double d2 = mc.player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);

        // Always keep block entities within 5 blocks so the player can interact with chests/signs right next to them
        if (d2 <= 25.0 && !panicFreeze.get()) {
            if (limitBlockEntities.get()) {
                blockEntitySampleDists.computeIfAbsent(blockEntity.getType(), k -> new ArrayList<>()).add(d2);
            }
            return false;
        }

        if (panicFreeze.get()) {
            culledThisInterval++;
            return true;
        }
        if (!limitBlockEntities.get()) return false;

        BlockEntityType<?> type = blockEntity.getType();
        blockEntitySampleDists.computeIfAbsent(type, k -> new ArrayList<>()).add(d2);

        Double cutoff = blockEntityCutoffSqr.get(type);
        if (cutoff == null) return false;
        if (cutoff < 0 || d2 > cutoff) {
            culledThisInterval++;
            return true;
        }
        return false;
    }

    public boolean shouldSkipSignText() {
        return isEnabled() && (signTextCulled || panicFreeze.get());
    }

    public boolean shouldSkipParticle() {
        if (!isEnabled() || !limitParticles.get()) return false;
        if (panicFreeze.get()) return true;
        Minecraft mc = Minecraft.getInstance();
        int fps = mc.getFps();
        return fps > 0 && fps < (int) Math.round(panicFpsThreshold.get());
    }

    public void renderHudAlert(GuiGraphics g) {
        if (!isEnabled() || !showHudAlert.get() || activeAlertText.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        int sw = mc.getWindow().getGuiScaledWidth();
        int tw = font.width(activeAlertText);
        int pillW = tw + 18;
        int pillH = 18;
        int x = (sw - pillW) / 2;
        int y = 8;
        int accent = (panicFreeze.get() || !brokenEntityTypes.isEmpty() || !brokenBlockEntityTypes.isEmpty())
                ? 0xE6C93B32 : 0xCC1E293B;
        AppleUiRenderer.drawRoundedRect(g, x, y, pillW, pillH, 9, accent);
        g.drawString(font, activeAlertText, x + 9, y + 5, 0xFFF8FAFC, false);
    }

    private static String describeBlockEntityType(BlockEntityType<?> type) {
        if (type == BlockEntityType.SIGN || type == BlockEntityType.HANGING_SIGN) return "告示牌";
        if (type == BlockEntityType.CHEST || type == BlockEntityType.TRAPPED_CHEST || type == BlockEntityType.ENDER_CHEST) return "箱子";
        if (type == BlockEntityType.SHULKER_BOX) return "潜影盒";
        if (type == BlockEntityType.BANNER) return "旗帜";
        if (type == BlockEntityType.SKULL) return "头颅";
        if (type == BlockEntityType.BEACON) return "信标";
        if (type == BlockEntityType.BED) return "床";
        return "方块实体";
    }
}
