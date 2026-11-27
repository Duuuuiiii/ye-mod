package com.ye.mod.modules.visual;

import com.ye.mod.core.Category;
import com.ye.mod.core.Module;
import com.ye.mod.core.setting.DoubleSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.player.Player;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Zero-lag single-batch 3D Voxel Shatter Death Explosion.
 * Capped shard count + cached ground bounce height + 1 single GPU draw call in YeWorldFxRenderer!
 */
public class VoxelShatterModule extends Module {
    public static final class VoxelPiece {
        public double x, y, z, vx, vy, vz, size, floorY;
        public int rgb;
        public long spawnMs;

        VoxelPiece(double x, double y, double z, double vx, double vy, double vz,
                   double size, double floorY, int rgb, long spawnMs) {
            this.x = x; this.y = y; this.z = z;
            this.vx = vx; this.vy = vy; this.vz = vz;
            this.size = size; this.floorY = floorY;
            this.rgb = rgb; this.spawnMs = spawnMs;
        }
    }

    public final DoubleSetting pieceCount = addSetting(new DoubleSetting(
            "pieceCount", "碎块数", "单个目标爆裂碎块数（低开销单批次渲染）", 14.0, 6.0, 24.0, 2.0
    ));

    private final List<VoxelPiece> pieces = new CopyOnWriteArrayList<>();
    private final Set<Integer> shatteredIds = new HashSet<>();
    private final Random rng = new Random();

    public VoxelShatterModule() {
        super("voxel_shatter", "击杀碎裂", "Voxel Shatter", "击杀目标爆散弹跳立体碎块（单批次零掉帧）", Category.VISUAL);
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
            if (le.deathTime == 1 && e.distanceToSqr(mc.player) < 1600.0) {
                if (shatteredIds.add(e.getId())) {
                    spawnBurst(le, now);
                }
            }
        }
        if (shatteredIds.size() > 128) shatteredIds.clear();

        for (VoxelPiece p : pieces) {
            if (now - p.spawnMs > 1600L) {
                pieces.remove(p);
                continue;
            }
            p.vy -= 0.032;
            p.x += p.vx;
            p.y += p.vy;
            p.z += p.vz;
            if (p.y < p.floorY + p.size * 0.5 && p.vy < 0) {
                p.y = p.floorY + p.size * 0.5;
                p.vy = -p.vy * 0.46;
                p.vx *= 0.72;
                p.vz *= 0.72;
            }
        }
    }

    private void spawnBurst(LivingEntity le, long now) {
        int count = Math.max(6, Math.min(24, (int) Math.round(pieceCount.get())));
        int[] palette = pickPalette(le);
        double h = Math.max(0.8, le.getBbHeight());
        double floorY = Math.floor(le.getY());
        for (int i = 0; i < count; i++) {
            double px = le.getX() + (rng.nextDouble() - 0.5) * le.getBbWidth();
            double py = le.getY() + 0.15 + rng.nextDouble() * (h * 0.85);
            double pz = le.getZ() + (rng.nextDouble() - 0.5) * le.getBbWidth();
            double vx = (rng.nextDouble() - 0.5) * 0.30;
            double vy = 0.10 + rng.nextDouble() * 0.25;
            double vz = (rng.nextDouble() - 0.5) * 0.30;
            double sz = 0.11 + rng.nextDouble() * 0.09;
            int rgb = palette[rng.nextInt(palette.length)];
            pieces.add(new VoxelPiece(px, py, pz, vx, vy, vz, sz, floorY, rgb, now));
        }
        while (pieces.size() > 72) pieces.remove(0);
    }

    private static int[] pickPalette(LivingEntity le) {
        if (le instanceof Creeper) return new int[]{0x4ADE80, 0x22C55E, 0x166534};
        if (le instanceof AbstractSkeleton) return new int[]{0xF8FAFC, 0xCBD5E1, 0x94A3B8};
        if (le instanceof EnderMan) return new int[]{0x1E1B2E, 0xA855F7, 0xD946EF};
        if (le instanceof Player) return new int[]{0x38BDF8, 0xFACC15, 0xEF4444};
        return new int[]{0xEF4444, 0xF97316, 0xFACC15};
    }

    public List<VoxelPiece> getPieces() {
        return pieces;
    }
}
