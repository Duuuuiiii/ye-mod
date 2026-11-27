package com.ye.mod.modules.visual;

import com.ye.mod.core.Category;
import com.ye.mod.core.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Ethereal glowing spirit flame ascending on mob/player death (rendered via YeWorldFxRenderer).
 */
public class SoulAscendModule extends Module {
    public record SoulWisp(double x, double y, double z, long spawnMs, double phase) {}

    private final List<SoulWisp> wisps = new CopyOnWriteArrayList<>();
    private final Set<Integer> seenDead = new HashSet<>();

    public SoulAscendModule() {
        super("soul_ascend", "灵魂升天", "Soul Ascend", "目标死亡时飘出灵体魂火摇曳升空", Category.VISUAL);
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
            if (le.deathTime == 1 && e.distanceToSqr(mc.player) < 2000.0) {
                if (seenDead.add(e.getId())) {
                    wisps.add(new SoulWisp(e.getX(), e.getY() + le.getBbHeight() * 0.55, e.getZ(), now, (e.getId() % 10) * 0.6));
                    while (wisps.size() > 24) wisps.remove(0);
                }
            }
        }
        if (seenDead.size() > 128) seenDead.clear();

        for (SoulWisp w : wisps) {
            if (now - w.spawnMs > 2800L) wisps.remove(w);
        }
    }

    public List<SoulWisp> getWisps() {
        return wisps;
    }
}
