package com.ye.mod.modules.world;

import com.ye.mod.core.Category;
import com.ye.mod.core.Module;
import com.ye.mod.core.setting.BoolSetting;
import com.ye.mod.core.setting.DoubleSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.EnderChestBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayList;
import java.util.List;

/**
 * Storage & Stash ESP (inspired by RusherHack StorageESP / BaseFinder):
 * highlights chests, shulker boxes, ender chests, barrels, hoppers, and spawners through walls.
 */
public class StashRadarModule extends Module {
    public final DoubleSetting rangeChunks = addSetting(new DoubleSetting(
            "rangeChunks", "扫描区块半径", "扫描玩家周围多少个区块内的容器与刷怪笼", 5.0, 2.0, 10.0, 1.0
    ));

    public final BoolSetting chests = addSetting(new BoolSetting("chests", "箱子与木桶", "高亮普通箱子、陷阱箱与木桶", true));
    public final BoolSetting shulkers = addSetting(new BoolSetting("shulkers", "潜影盒", "高亮潜影盒（亮紫色）", true));
    public final BoolSetting enderChests = addSetting(new BoolSetting("enderChests", "末影箱", "高亮末影箱（天蓝色）", true));
    public final BoolSetting spawners = addSetting(new BoolSetting("spawners", "刷怪笼", "高亮刷怪笼（亮红色）", true));
    public final BoolSetting hoppers = addSetting(new BoolSetting("hoppers", "漏斗", "高亮漏斗（浅绿色）", false));
    public final BoolSetting showCountHud = addSetting(new BoolSetting("showCountHud", "HUD 计数提示", "在屏幕左上角显示发现的容器总数", true));

    private record StorageEntry(BlockPos pos, int strokeArgb, int fillArgb) {}

    private volatile List<StorageEntry> cached = List.of();
    private volatile int shulkerCount = 0;
    private volatile int totalCount = 0;
    private int tickCounter = 0;

    public StashRadarModule() {
        super("stash_radar", "仓库容器雷达", "Stash Radar", "透视高亮周围箱子、潜影盒、末影箱与刷怪笼，跑图找仓库神器", Category.WORLD);
    }

    @Override
    protected void onDisable() {
        cached = List.of();
        shulkerCount = 0;
        totalCount = 0;
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

        List<StorageEntry> found = new ArrayList<>();
        int shulkersFound = 0;

        for (int cx = pcx - cr; cx <= pcx + cr; cx++) {
            for (int cz = pcz - cr; cz <= pcz + cr; cz++) {
                if (!level.hasChunk(cx, cz)) continue;
                LevelChunk chunk = level.getChunk(cx, cz);
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (be instanceof ShulkerBoxBlockEntity && shulkers.get()) {
                        found.add(new StorageEntry(be.getBlockPos(), 0xFFD946EF, 0x44D946EF));
                        shulkersFound++;
                    } else if ((be instanceof ChestBlockEntity || be instanceof BarrelBlockEntity) && chests.get()) {
                        found.add(new StorageEntry(be.getBlockPos(), 0xFFF59E0B, 0x33F59E0B));
                    } else if (be instanceof EnderChestBlockEntity && enderChests.get()) {
                        found.add(new StorageEntry(be.getBlockPos(), 0xFF38BDF8, 0x3838BDF8));
                    } else if (be instanceof SpawnerBlockEntity && spawners.get()) {
                        found.add(new StorageEntry(be.getBlockPos(), 0xFFEF4444, 0x44EF4444));
                    } else if (be instanceof HopperBlockEntity && hoppers.get()) {
                        found.add(new StorageEntry(be.getBlockPos(), 0xFF4ADE80, 0x2A4ADE80));
                    }
                    if (found.size() >= 600) break;
                }
                if (found.size() >= 600) break;
            }
            if (found.size() >= 600) break;
        }

        cached = found;
        shulkerCount = shulkersFound;
        totalCount = found.size();
    }

    public void emitGizmos() {
        if (!isEnabled()) return;
        for (StorageEntry e : cached) {
            Gizmos.cuboid(e.pos, GizmoStyle.strokeAndFill(e.strokeArgb, 1.5f, e.fillArgb)).setAlwaysOnTop();
        }
    }

    public void renderHud(GuiGraphics g) {
        if (!isEnabled() || !showCountHud.get() || totalCount == 0) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;
        String text = "仓库雷达: " + totalCount + " 个容器" + (shulkerCount > 0 ? " (潜影盒 " + shulkerCount + ")" : "");
        g.drawString(mc.font, text, 8, 32, 0xFFD946EF, true);
    }
}
