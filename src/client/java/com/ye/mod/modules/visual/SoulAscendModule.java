package com.ye.mod.modules.visual;

import com.ye.mod.core.Category;
import com.ye.mod.core.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * Soul Ascend Death Animation (#14): dying mobs/players release a translucent
 * spirit wisp that sways upward into the sky and fades out.
 */
public class SoulAscendModule extends Module {
    private record SoulWisp(double x, double y, double z, long spawnMs, double phase) {}

    private final List<SoulWisp> wisps = new ArrayList<>();
    private final Set<Integer> seenDead = new HashSet<>();

    public SoulAscendModule() {
        super("soul_ascend", "灵魂升天特效", "Soul Ascend", "击杀生物时原地飘出半透明小灵魂摇曳升入天空", Category.VISUAL);
    }

    @Override
    protected void onDisable() {
        wisps.clear();
        seenDead.clear();
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
                if (seenDead.add(e.getId())) {
                    wisps.add(new SoulWisp(e.getX(), e.getY() + le.getBbHeight() * 0.6, e.getZ(), now, (e.getId() % 10) * 0.6));
                    if (wisps.size() > 32) wisps.remove(0);
                }
            }
        }
        if (seenDead.size() > 256) seenDead.clear();

        Iterator<SoulWisp> it = wisps.iterator();
        while (it.hasNext()) {
            if (now - it.next().spawnMs > 2800L) it.remove();
        }
    }

    public void emitGizmos() {
        if (!isEnabled() || wisps.isEmpty()) return;
        long now = System.currentTimeMillis();
        for (SoulWisp w : wisps) {
            float t = (now - w.spawnMs) / 2800.0f;
            int a = Math.max(12, Math.round((1.0f - t) * 210.0f));
            int bodyCol = (a << 24) | 0x00E0F2FE;
            int eyeCol = (a << 24) | 0x0038BDF8;

            double cy = w.y + t * 5.8;
            double cx = w.x + Math.sin(t * 7.5 + w.phase) * 0.35;
            double cz = w.z + Math.cos(t * 6.0 + w.phase) * 0.35;

            // Translucent spirit head + trailing tail wisps
            Gizmos.cuboid(new AABB(cx - 0.18, cy - 0.18, cz - 0.18, cx + 0.18, cy + 0.18, cz + 0.18),
                    GizmoStyle.fill(bodyCol));
            Gizmos.cuboid(new AABB(cx - 0.10, cy - 0.50, cz - 0.10, cx + 0.10, cy - 0.18, cz + 0.10),
                    GizmoStyle.fill(eyeCol));
            Gizmos.line(new Vec3(cx, cy - 0.50, cz), new Vec3(cx - Math.sin(t * 7.5 + w.phase) * 0.18, cy - 0.95, cz), eyeCol, 2.5f);
        }
    }
}
