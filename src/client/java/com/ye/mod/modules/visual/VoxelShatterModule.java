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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * 144/240Hz Frame-Continuous Voxel Shatter Death Explosion:
 * Eliminates the 20-TPS choppy stutter & CopyOnWriteArrayList GC stalls by integrating physics
 * smoothly on every render frame using high-precision nanosecond delta-time!
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
            "pieceCount", "碎块数", "单个目标爆裂立体碎块数", 16.0, 6.0, 32.0, 2.0
    ));

    private final List<VoxelPiece> pieces = new ArrayList<>();
    private final Set<Integer> shatteredIds = new HashSet<>();
    private final Random rng = new Random();
    private long lastPhysicsNs = 0L;

    public VoxelShatterModule() {
        super("voxel_shatter", "击杀碎裂", "Voxel Shatter", "满帧丝滑物理插值的立体碎块爆散特效", Category.VISUAL);
    }

    @Override
    protected void onDisable() {
        synchronized (pieces) {
            pieces.clear();
        }
        shatteredIds.clear();
        lastPhysicsNs = 0L;
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
    }

    /**
     * Called directly from YeWorldFxRenderer every render frame (144Hz/240Hz+)!
     */
    public List<VoxelPiece> stepAndSnapshot(long nowMs) {
        long nowNs = System.nanoTime();
        double tickScale = 0.0;
        if (lastPhysicsNs != 0L) {
            double dtSec = Math.min(0.05, Math.max(0.0, (nowNs - lastPhysicsNs) / 1_000_000_000.0));
            tickScale = dtSec * 20.0; // Normalize velocities to 20 ticks/sec scale at arbitrary 240+ FPS!
        }
        lastPhysicsNs = nowNs;

        synchronized (pieces) {
            if (pieces.isEmpty()) return List.of();
            Iterator<VoxelPiece> it = pieces.iterator();
            while (it.hasNext()) {
                VoxelPiece p = it.next();
                if (nowMs - p.spawnMs > 1600L) {
                    it.remove();
                    continue;
                }
                if (tickScale > 0.0) {
                    p.vy -= 0.032 * tickScale;
                    p.x += p.vx * tickScale;
                    p.y += p.vy * tickScale;
                    p.z += p.vz * tickScale;
                    if (p.y < p.floorY + p.size * 0.5 && p.vy < 0) {
                        p.y = p.floorY + p.size * 0.5;
                        p.vy = -p.vy * 0.48;
                        double drag = Math.pow(0.72, tickScale);
                        p.vx *= drag;
                        p.vz *= drag;
                    }
                }
            }
            return new ArrayList<>(pieces);
        }
    }

    private void spawnBurst(LivingEntity le, long now) {
        int count = Math.max(6, Math.min(32, (int) Math.round(pieceCount.get())));
        int[] palette = pickPalette(le);
        double h = Math.max(0.8, le.getBbHeight());
        double floorY = Math.floor(le.getY());
        synchronized (pieces) {
            for (int i = 0; i < count; i++) {
                double px = le.getX() + (rng.nextDouble() - 0.5) * le.getBbWidth();
                double py = le.getY() + 0.15 + rng.nextDouble() * (h * 0.85);
                double pz = le.getZ() + (rng.nextDouble() - 0.5) * le.getBbWidth();
                double vx = (rng.nextDouble() - 0.5) * 0.32;
                double vy = 0.12 + rng.nextDouble() * 0.26;
                double vz = (rng.nextDouble() - 0.5) * 0.32;
                double sz = 0.12 + rng.nextDouble() * 0.09;
                int rgb = palette[rng.nextInt(palette.length)];
                pieces.add(new VoxelPiece(px, py, pz, vx, vy, vz, sz, floorY, rgb, now));
            }
            while (pieces.size() > 96) pieces.remove(0);
        }
    }

    private static int[] pickPalette(LivingEntity le) {
        if (le instanceof Creeper) return new int[]{0x4ADE80, 0x22C55E, 0x166534};
        if (le instanceof AbstractSkeleton) return new int[]{0xF8FAFC, 0xCBD5E1, 0x94A3B8};
        if (le instanceof EnderMan) return new int[]{0x1E1B2E, 0xA855F7, 0xD946EF};
        if (le instanceof Player) return new int[]{0x38BDF8, 0xFACC15, 0xEF4444};
        return new int[]{0xEF4444, 0xF97316, 0xFACC15};
    }
}
