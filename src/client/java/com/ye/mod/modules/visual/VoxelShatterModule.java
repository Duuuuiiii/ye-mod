package com.ye.mod.modules.visual;

import com.ye.mod.core.Category;
import com.ye.mod.core.Module;
import com.ye.mod.core.setting.DoubleSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Voxel Shatter Death Explosion (#6): dying mobs/players burst into
 * 24-36 physics-simulated bouncing 3D colored voxel blocks.
 */
public class VoxelShatterModule extends Module {
    private static final class VoxelPiece {
        double x, y, z, vx, vy, vz, size;
        int rgb;
        long spawnMs;

        VoxelPiece(double x, double y, double z, double vx, double vy, double vz, double size, int rgb, long spawnMs) {
            this.x = x; this.y = y; this.z = z;
            this.vx = vx; this.vy = vy; this.vz = vz;
            this.size = size; this.rgb = rgb; this.spawnMs = spawnMs;
        }
    }

    public final DoubleSetting pieceCount = addSetting(new DoubleSetting(
            "pieceCount", "碎块数量", "每个目标死亡爆出的 3D 方块数量", 28.0, 12.0, 48.0, 4.0
    ));

    private final List<VoxelPiece> pieces = new ArrayList<>();
    private final Set<Integer> shatteredIds = new HashSet<>();
    private final Random rng = new Random();

    public VoxelShatterModule() {
        super("voxel_shatter", "方块碎裂击杀", "Voxel Shatter", "生物或玩家死亡时爆裂成弹跳散落的 3D 方块碎片", Category.VISUAL);
    }

    @Override
    protected void onDisable() {
        pieces.clear();
        shatteredIds.clear();
    }

    @Override
    public void onTick() {
        if (!isEnabled()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        long now = System.currentTimeMillis();
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof LivingEntity le) || e == mc.player) continue;
            if ((le.isDeadOrDying() || le.getHealth() <= 0.0f) && e.distanceToSqr(mc.player) < 2500.0) {
                if (shatteredIds.add(e.getId())) {
                    spawnBurst(le, now);
                }
            }
        }
        if (shatteredIds.size() > 256) shatteredIds.clear();

        Iterator<VoxelPiece> it = pieces.iterator();
        while (it.hasNext()) {
            VoxelPiece p = it.next();
            if (now - p.spawnMs > 2400L) {
                it.remove();
                continue;
            }
            p.vy -= 0.028;
            double nx = p.x + p.vx;
            double ny = p.y + p.vy;
            double nz = p.z + p.vz;

            BlockPos below = BlockPos.containing(nx, ny - p.size * 0.5, nz);
            if (!mc.level.getBlockState(below).isAir() && p.vy < 0) {
                p.vy = -p.vy * 0.45;
                p.vx *= 0.72;
                p.vz *= 0.72;
                ny = below.getY() + 1.0 + p.size * 0.5;
            }
            p.x = nx;
            p.y = ny;
            p.z = nz;
        }
    }

    private void spawnBurst(LivingEntity le, long now) {
        int count = Math.max(12, Math.min(48, (int) Math.round(pieceCount.get())));
        int[] palette = pickPalette(le);
        double h = Math.max(0.8, le.getBbHeight());
        for (int i = 0; i < count; i++) {
            double px = le.getX() + (rng.nextDouble() - 0.5) * le.getBbWidth();
            double py = le.getY() + rng.nextDouble() * h;
            double pz = le.getZ() + (rng.nextDouble() - 0.5) * le.getBbWidth();
            double vx = (rng.nextDouble() - 0.5) * 0.34;
            double vy = 0.12 + rng.nextDouble() * 0.28;
            double vz = (rng.nextDouble() - 0.5) * 0.34;
            double sz = 0.09 + rng.nextDouble() * 0.11;
            int rgb = palette[rng.nextInt(palette.length)];
            pieces.add(new VoxelPiece(px, py, pz, vx, vy, vz, sz, rgb, now));
        }
        while (pieces.size() > 360) pieces.remove(0);
    }

    private static int[] pickPalette(LivingEntity le) {
        if (le instanceof Creeper) return new int[]{0x4ADE80, 0x22C55E, 0x166534, 0x0F172A};
        if (le instanceof AbstractSkeleton) return new int[]{0xF8FAFC, 0xE2E8F0, 0x94A3B8};
        if (le instanceof EnderMan) return new int[]{0x1E1B2E, 0x0F172A, 0xA855F7, 0xD946EF};
        if (le instanceof Player) return new int[]{0x38BDF8, 0xFACC15, 0xEF4444, 0xF8FAFC};
        return new int[]{0xEF4444, 0xF97316, 0xFACC15, 0xE2E8F0};
    }

    public void emitGizmos() {
        if (!isEnabled() || pieces.isEmpty()) return;
        long now = System.currentTimeMillis();
        for (VoxelPiece p : pieces) {
            float age = (now - p.spawnMs) / 2400.0f;
            int a = Math.max(10, Math.round((1.0f - age) * 235.0f));
            int argb = (a << 24) | (p.rgb & 0x00FFFFFF);
            double hs = p.size * 0.5;
            Gizmos.cuboid(new AABB(p.x - hs, p.y - hs, p.z - hs, p.x + hs, p.y + hs, p.z + hs),
                    GizmoStyle.fill(argb));
        }
    }
}
