package com.ye.mod.modules.world;

import com.ye.mod.core.Category;
import com.ye.mod.core.ConfigManager;
import com.ye.mod.core.Module;
import com.ye.mod.core.setting.BoolSetting;
import com.ye.mod.core.setting.DoubleSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.EnderChestBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Storage & Stash ESP with Lotus-inspired natural chest filtering,
 * per-type toggles, tracer lines, sound alerts, and draggable HUD slot.
 */
public class StashRadarModule extends Module {
    public final DoubleSetting rangeChunks = addSetting(new DoubleSetting(
            "rangeChunks", "扫描半径", "扫描周围多少个区块内的容器", 5.0, 2.0, 10.0, 1.0
    ));

    public final BoolSetting ignoreNatural = addSetting(new BoolSetting(
            "ignoreNatural", "过滤自然箱子", "参考 Lotus 逻辑：检测周围刷怪笼/矿井/神殿方块，自动跳过自然箱子", true
    ));

    public final BoolSetting chests = addSetting(new BoolSetting("chests", "箱子木桶", "高亮箱子、陷阱箱与木桶", true));
    public final BoolSetting shulkers = addSetting(new BoolSetting("shulkers", "潜影盒", "高亮潜影盒", true));
    public final BoolSetting enderChests = addSetting(new BoolSetting("enderChests", "末影箱", "高亮末影箱", true));
    public final BoolSetting spawners = addSetting(new BoolSetting("spawners", "刷怪笼", "高亮刷怪笼", true));
    public final BoolSetting hoppers = addSetting(new BoolSetting("hoppers", "漏斗", "高亮漏斗", false));

    public final BoolSetting shulkerTracer = addSetting(new BoolSetting("shulkerTracer", "潜影盒追踪线", "向潜影盒绘制追踪线", true));
    public final BoolSetting chestTracer = addSetting(new BoolSetting("chestTracer", "箱子追踪线", "向非自然箱子绘制追踪线", false));
    public final BoolSetting spawnerTracer = addSetting(new BoolSetting("spawnerTracer", "刷怪笼追踪线", "向刷怪笼绘制追踪线", false));
    public final BoolSetting soundAlert = addSetting(new BoolSetting("soundAlert", "发现提示音", "新发现潜影盒或仓库箱子时播放提示音", true));

    private record StorageEntry(BlockPos pos, int strokeArgb, int fillArgb, boolean tracer) {}

    private volatile List<StorageEntry> cached = List.of();
    private volatile int shulkerCount = 0;
    private volatile int totalCount = 0;
    private final Set<Long> alertedPositions = new HashSet<>();
    private int tickCounter = 0;

    public StashRadarModule() {
        super("stash_radar", "仓库雷达", "Stash Radar", "透视仓库容器与刷怪笼，自动过滤自然箱子，支持独立追踪线与提示音", Category.WORLD);
    }

    @Override
    protected void onDisable() {
        cached = List.of();
        shulkerCount = 0;
        totalCount = 0;
        alertedPositions.clear();
    }

    @Override
    public void onTick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            cached = List.of();
            return;
        }
        tickCounter++;
        if (tickCounter % 10 != 0) return;

        Level level = mc.level;
        int cr = Math.max(2, Math.min(10, (int) Math.round(rangeChunks.get())));
        int pcx = mc.player.chunkPosition().x;
        int pcz = mc.player.chunkPosition().z;
        boolean filterNat = ignoreNatural.get();

        List<StorageEntry> found = new ArrayList<>();
        int shulkersFound = 0;
        boolean playAlert = false;

        for (int cx = pcx - cr; cx <= pcx + cr; cx++) {
            for (int cz = pcz - cr; cz <= pcz + cr; cz++) {
                if (!level.hasChunk(cx, cz)) continue;
                LevelChunk chunk = level.getChunk(cx, cz);
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    BlockPos pos = be.getBlockPos();
                    if (be instanceof ShulkerBoxBlockEntity && shulkers.get()) {
                        found.add(new StorageEntry(pos, 0xFFD946EF, 0x44D946EF, shulkerTracer.get()));
                        shulkersFound++;
                        if (alertedPositions.add(pos.asLong())) playAlert = true;
                    } else if ((be instanceof ChestBlockEntity || be instanceof BarrelBlockEntity) && chests.get()) {
                        if (filterNat && isNaturalContainer(level, pos, be)) continue;
                        found.add(new StorageEntry(pos, 0xFFF59E0B, 0x33F59E0B, chestTracer.get()));
                        if (alertedPositions.add(pos.asLong())) playAlert = true;
                    } else if (be instanceof EnderChestBlockEntity && enderChests.get()) {
                        found.add(new StorageEntry(pos, 0xFF38BDF8, 0x3838BDF8, false));
                    } else if (be instanceof SpawnerBlockEntity && spawners.get()) {
                        found.add(new StorageEntry(pos, 0xFFEF4444, 0x44EF4444, spawnerTracer.get()));
                    } else if (be instanceof HopperBlockEntity && hoppers.get()) {
                        found.add(new StorageEntry(pos, 0xFF4ADE80, 0x2A4ADE80, false));
                    }
                    if (found.size() >= 500) break;
                }
                if (found.size() >= 500) break;
            }
            if (found.size() >= 500) break;
        }

        if (playAlert && soundAlert.get()) {
            mc.player.playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 0.7f, 1.2f);
        }

        cached = found;
        shulkerCount = shulkersFound;
        totalCount = found.size();
    }

    private static boolean isNaturalContainer(Level level, BlockPos pos, BlockEntity be) {
        if (be instanceof RandomizableContainerBlockEntity rce && rce.getLootTable() != null) {
            return true;
        }
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -1; dy <= 2; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    m.set(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz);
                    Block b = level.getBlockState(m).getBlock();
                    if (b == Blocks.SPAWNER || b == Blocks.COBWEB || b == Blocks.RAIL
                            || b == Blocks.MOSSY_COBBLESTONE || b == Blocks.SANDSTONE
                            || b == Blocks.CHISELED_SANDSTONE || b == Blocks.CUT_SANDSTONE
                            || b == Blocks.TERRACOTTA || b == Blocks.INFESTED_STONE_BRICKS
                            || b == Blocks.SUSPICIOUS_SAND || b == Blocks.SUSPICIOUS_GRAVEL) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public void emitGizmos() {
        if (!isEnabled()) return;
        Minecraft mc = Minecraft.getInstance();
        Vec3 start = (mc.player != null) ? mc.player.getEyePosition(1.0f).add(mc.player.getLookAngle().scale(0.8)) : null;

        for (StorageEntry e : cached) {
            Gizmos.cuboid(e.pos, GizmoStyle.strokeAndFill(e.strokeArgb, 1.5f, e.fillArgb)).setAlwaysOnTop();
            if (e.tracer && start != null) {
                Gizmos.line(start, Vec3.atCenterOf(e.pos), e.strokeArgb, 1.8f).setAlwaysOnTop();
            }
        }
    }

    public void renderHud(GuiGraphics g) {
        if (!isEnabled() || totalCount == 0) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;
        ConfigManager.HudSlot slot = ConfigManager.HUD_SLOTS.get("hud_radar");
        if (slot != null && !slot.enabled) return;
        int hx = slot != null ? slot.x : 8;
        int hy = slot != null ? slot.y : 36;
        String text = ConfigManager.tr("雷达: ", "Radar: ") + totalCount
                + (shulkerCount > 0 ? " (" + ConfigManager.tr("潜影盒 ", "Shulker ") + shulkerCount + ")" : "");
        g.drawString(mc.font, text, hx, hy, 0xFFD946EF, true);
    }
}
