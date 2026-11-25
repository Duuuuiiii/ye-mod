package com.ye.mod.modules.world;

import com.ye.mod.core.Category;
import com.ye.mod.core.ConfigManager;
import com.ye.mod.core.Module;
import com.ye.mod.core.setting.BoolSetting;
import com.ye.mod.core.setting.DoubleSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Scans loaded chunks for Nether Portals, clustering adjacent portal blocks
 * into 1 single target marker + 1 clean tracer line + optional sound alert.
 */
public class PortalFinderModule extends Module {
    public final DoubleSetting rangeChunks = addSetting(new DoubleSetting(
            "rangeChunks", "扫描半径", "扫描周围多少个区块内的下界传送门", 6.0, 2.0, 12.0, 1.0
    ));

    public final BoolSetting tracerLine = addSetting(new BoolSetting(
            "tracerLine", "单条追踪线", "仅向每个传送门中心画一条追踪线（不画多条）", true
    ));

    public final BoolSetting soundAlert = addSetting(new BoolSetting(
            "soundAlert", "发现提示音", "扫描到新地狱门时播放提示音", true
    ));

    private volatile List<BlockPos> portalCenters = List.of();
    private final Set<Long> alerted = new HashSet<>();
    private int tickCounter = 0;

    public PortalFinderModule() {
        super("portal_finder", "传送门查找", "Portal Finder", "查找周围下界传送门，每个门仅画一条追踪线并支持提示音", Category.WORLD);
    }

    @Override
    protected void onDisable() {
        portalCenters = List.of();
        alerted.clear();
    }

    @Override
    public void onTick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            portalCenters = List.of();
            return;
        }
        tickCounter++;
        if (tickCounter % 15 != 0) return;

        Level level = mc.level;
        int cr = Math.max(2, Math.min(12, (int) Math.round(rangeChunks.get())));
        int pcx = mc.player.chunkPosition().x;
        int pcz = mc.player.chunkPosition().z;

        List<BlockPos> rawPortals = new ArrayList<>();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        int minY = Math.max(level.getMinY(), mc.player.getBlockY() - 64);
        int maxY = Math.min(level.getMaxY() - 1, mc.player.getBlockY() + 64);

        for (int cx = pcx - cr; cx <= pcx + cr; cx++) {
            for (int cz = pcz - cr; cz <= pcz + cr; cz++) {
                if (!level.hasChunk(cx, cz)) continue;
                LevelChunk chunk = level.getChunk(cx, cz);
                int bx = cx << 4;
                int bz = cz << 4;
                for (int y = minY; y <= maxY; y += 2) {
                    for (int x = 0; x < 16; x += 2) {
                        for (int z = 0; z < 16; z += 2) {
                            m.set(bx + x, y, bz + z);
                            if (chunk.getBlockState(m).is(Blocks.NETHER_PORTAL)) {
                                rawPortals.add(m.immutable());
                            }
                        }
                    }
                }
            }
        }

        // Cluster nearby portal blocks into 1 representative center per portal
        List<BlockPos> centers = new ArrayList<>();
        for (BlockPos p : rawPortals) {
            boolean merged = false;
            for (BlockPos c : centers) {
                if (c.distSqr(p) <= 36.0) {
                    merged = true;
                    break;
                }
            }
            if (!merged) {
                centers.add(p);
            }
        }

        boolean newFound = false;
        for (BlockPos c : centers) {
            if (alerted.add(c.asLong())) {
                newFound = true;
            }
        }
        if (newFound && soundAlert.get()) {
            mc.player.playSound(SoundEvents.PORTAL_TRIGGER, 0.45f, 1.4f);
        }

        portalCenters = centers;
    }

    public void emitGizmos() {
        if (!isEnabled() || portalCenters.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        Vec3 start = (mc.player != null) ? mc.player.getEyePosition(1.0f).add(mc.player.getLookAngle().scale(0.8)) : null;

        for (BlockPos p : portalCenters) {
            Gizmos.cuboid(p, GizmoStyle.strokeAndFill(0xFFC084FC, 2.2f, 0x55A855F7)).setAlwaysOnTop();
            if (tracerLine.get() && start != null) {
                Gizmos.line(start, Vec3.atCenterOf(p), 0xFFC084FC, 2.2f).setAlwaysOnTop();
            }
            Gizmos.billboardText(ConfigManager.tr("地狱门", "Portal"), Vec3.atCenterOf(p).add(0, 1.2, 0),
                    net.minecraft.gizmos.TextGizmo.Style.whiteAndCentered().withScale(0.6f)).setAlwaysOnTop();
        }
    }
}
