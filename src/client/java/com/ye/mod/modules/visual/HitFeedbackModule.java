package com.ye.mod.modules.visual;

import com.ye.mod.core.Category;
import com.ye.mod.core.Module;
import com.ye.mod.core.setting.BoolSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Pure client-side visual & audio hit feedback: spawns an expanding 3D pulse ring
 * at the attacked target and optionally plays a crisp hit chime.
 */
public class HitFeedbackModule extends Module {
    public final BoolSetting hitSound = addSetting(new BoolSetting(
            "hitSound", "清脆命中音效", "攻击命中生物/玩家时播放清脆的提示音", true
    ));

    public final BoolSetting hitRing = addSetting(new BoolSetting(
            "hitRing", "3D 命中光环", "在被攻击目标位置生成扩散的 3D 能量光环", true
    ));

    private record HitPulse(Vec3 pos, long startMs) {}
    private final List<HitPulse> pulses = new CopyOnWriteArrayList<>();

    public HitFeedbackModule() {
        super("hit_feedback", "打击光环反馈", "Hit Feedback", "攻击目标时在命中处弹出 3D 扩散光环并播放清脆命中提示音", Category.VISUAL);
    }

    @Override
    protected void onDisable() {
        pulses.clear();
    }

    public void onAttackEntity(Entity target) {
        if (!isEnabled() || target == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (hitRing.get()) {
            pulses.add(new HitPulse(target.position().add(0, target.getBbHeight() * 0.55, 0), System.currentTimeMillis()));
        }
        if (hitSound.get() && mc.player != null) {
            mc.player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.45f, 1.35f);
        }
    }

    public void emitGizmos() {
        if (!isEnabled() || !hitRing.get() || pulses.isEmpty()) return;
        long now = System.currentTimeMillis();
        Iterator<HitPulse> it = pulses.iterator();
        while (it.hasNext()) {
            HitPulse p = it.next();
            long elapsed = now - p.startMs;
            if (elapsed > 450L) {
                pulses.remove(p);
                continue;
            }
            float t = elapsed / 450.0f;
            float radius = 0.25f + t * 0.85f;
            int alpha = Math.max(20, Math.round((1.0f - t) * 230));
            int color = (alpha << 24) | 0x38BDF8;
            Gizmos.circle(p.pos, radius, GizmoStyle.stroke(color, 2.5f)).setAlwaysOnTop();
        }
    }
}
