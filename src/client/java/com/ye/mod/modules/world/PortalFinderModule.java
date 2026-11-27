package com.ye.mod.modules.world;

import com.ye.mod.core.Category;
import com.ye.mod.core.ConfigManager;
import com.ye.mod.core.Module;
import com.ye.mod.core.setting.BoolSetting;
import com.ye.mod.core.setting.ColorSetting;
import com.ye.mod.core.setting.DoubleSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Nether Portal Finder (传送门查找):
 * - Fixed alert sound: plays a clean crisp bell chime ONLY once per portal cluster (NEVER the loud portal-entering whoosh!)
 * - Fixed tracer ray: locked to exact interpolated camera origin so moving never makes tracer lines fly wildly!
 * - Renders exact merged portal frame bounding box!
 */
public class PortalFinderModule extends Module {
    public final DoubleSetting rangeChunks = addSetting(new DoubleSetting(
            "rangeChunks", "扫描半径", "扫描周围多少个区块内的下界传送门", 12.0, 2.0, 32.0, 1.0
    ).functionGroup());

    public final BoolSetting onlyOverworld = addSetting(new BoolSetting(
            "onlyOverworld", "仅主世界", "仅在主世界扫描传送门", true
    ).functionGroup());

    public final BoolSetting tracerLine = addSetting(new BoolSetting(
            "tracerLine", "绘制追踪线", "向每个传送门中心绘制单条稳定追踪线", true
    ).functionGroup());

    public final BoolSetting soundAlert = addSetting(new BoolSetting(
            "soundAlert", "发现提示音", "首次发现新地狱门时播放清脆叮声提示", true
    ).functionGroup());

    public final ColorSetting color = addSetting(new ColorSetting(
            "color", "高亮颜色", "设置传送门框体与追踪线颜色", 0xFFC084FC
    ));

    private record PortalCluster(AABB box, Vec3 center, long gridKey) {}

    private volatile List<PortalCluster> portalClusters = List.of();
    private final Set<Long> alertedClusters = new HashSet<>();
    private int tickCounter = 0;

    public PortalFinderModule() {
        super("portal_finder", "传送门查找", "Portals", "扫描下界传送门并绘制稳定追踪线与清脆发现提示音", Category.UTILITY);
    }

    @Override
    protected void onDisable() {
        portalClusters = List.of();
        alertedClusters.clear();
    }

    @Override
    public void onTick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            portalClusters = List.of();
            return;
        }
        if (onlyOverworld.get() && mc.level.dimension() != Level.OVERWORLD) {
            portalClusters = List.of();
            return;
        }
        tickCounter++;
        if (tickCounter % 20 != 0) return;

        Level level = mc.level;
        int cr = Math.max(2, Math.min(32, (int) Math.round(rangeChunks.get())));
        int pcx = mc.player.chunkPosition().x;
        int pcz = mc.player.chunkPosition().z;

        List<BlockPos> rawPortals = new ArrayList<>();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();

        for (int cx = pcx - cr; cx <= pcx + cr; cx++) {
            for (int cz = pcz - cr; cz <= pcz + cr; cz++) {
                if (!level.hasChunk(cx, cz)) continue;
                LevelChunk chunk = level.getChunk(cx, cz);
                LevelChunkSection[] sections = chunk.getSections();
                int bx = cx << 4;
                int bz = cz << 4;
                for (int secIdx = 0; secIdx < sections.length; secIdx++) {
                    LevelChunkSection sec = sections[secIdx];
                    if (sec.hasOnlyAir() || !sec.maybeHas(s -> s.is(Blocks.NETHER_PORTAL))) continue;
                    int syBase = level.getSectionYFromSectionIndex(secIdx) << 4;
                    for (int y = 0; y < 16; y++) {
                        for (int x = 0; x < 16; x++) {
                            for (int z = 0; z < 16; z++) {
                                if (sec.getBlockState(x, y, z).is(Blocks.NETHER_PORTAL)) {
                                    m.set(bx + x, syBase + y, bz + z);
                                    rawPortals.add(m.immutable());
                                }
                            }
                        }
                    }
                }
            }
        }

        // Group adjacent portal blocks into unified portal AABB clusters with stable quantized cluster key
        List<List<BlockPos>> groups = new ArrayList<>();
        for (BlockPos p : rawPortals) {
            List<BlockPos> matched = null;
            for (List<BlockPos> g : groups) {
                for (BlockPos existing : g) {
                    if (existing.distSqr(p) <= 25.0) {
                        matched = g;
                        break;
                    }
                }
                if (matched != null) break;
            }
            if (matched != null) matched.add(p);
            else {
                List<BlockPos> nl = new ArrayList<>();
                nl.add(p);
                groups.add(nl);
            }
        }

        List<PortalCluster> clusters = new ArrayList<>(groups.size());
        boolean newFound = false;
        for (List<BlockPos> g : groups) {
            int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
            for (BlockPos p : g) {
                minX = Math.min(minX, p.getX()); maxX = Math.max(maxX, p.getX() + 1);
                minY = Math.min(minY, p.getY()); maxY = Math.max(maxY, p.getY() + 1);
                minZ = Math.min(minZ, p.getZ()); maxZ = Math.max(maxZ, p.getZ() + 1);
            }
            AABB box = new AABB(minX, minY, minZ, maxX, maxY, maxZ);
            // Quantize cluster center to 8-block grid so chunk boundary loading NEVER re-triggers sound!
            long gridKey = BlockPos.asLong(((minX + maxX) / 2) >> 3, ((minY + maxY) / 2) >> 3, ((minZ + maxZ) / 2) >> 3);
            if (alertedClusters.add(gridKey)) {
                newFound = true;
            }
            clusters.add(new PortalCluster(box, box.getCenter(), gridKey));
        }

        if (newFound && soundAlert.get() && mc.getSoundManager() != null) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BELL.value(), 1.35f, 0.70f));
        }

        portalClusters = clusters;
    }

    public void emitGizmos(Vec3 tracerStart) {
        if (!isEnabled() || portalClusters.isEmpty()) return;
        int rgb = color.getRgb();
        int stroke = 0xEE000000 | rgb;
        int fill = 0x44000000 | rgb;

        for (PortalCluster c : portalClusters) {
            Gizmos.cuboid(c.box, GizmoStyle.strokeAndFill(stroke, 2.0f, fill)).setAlwaysOnTop();
            if (tracerLine.get() && tracerStart != null) {
                Gizmos.line(tracerStart, c.center, stroke, 2.0f).setAlwaysOnTop();
            }
            Gizmos.billboardText(ConfigManager.tr("地狱门", "Portal"), new Vec3(c.center.x, c.box.maxY + 0.6, c.center.z),
                    net.minecraft.gizmos.TextGizmo.Style.whiteAndCentered().withScale(0.6f)).setAlwaysOnTop();
        }
    }
}
