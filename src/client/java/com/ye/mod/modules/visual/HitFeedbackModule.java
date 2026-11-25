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
 * Rich multi-layered 3D hit shockwave rings and optional crisp sound feedback.
 */
public class HitFeedbackModule extends Module {
    public final BoolSetting hitSound = addSetting(new BoolSetting(
            "hitSound", "命中音效", "攻击命中时播放清脆提示音", true
    ));

    public final BoolSetting hitRing = addSetting(new BoolSetting(
            "hitRing", "立体光环", "在命中处生成多层扩散 3D 冲击波", true
    ));

    private record HitPulse(Vec3 pos, long startMs) {}
    private final List<HitPulse> pulses = new CopyOnWriteArrayList<>();

    public HitFeedbackModule() {
        super("hit_feedback", "打击反馈", "Hit Feedback", "攻击目标时弹出多层厚重 3D 冲击光环与音效", Category.VISUAL);
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
            if (elapsed > 480L) {
                pulses.remove(p);
                continue;
            }
            float t = elapsed / 480.0f;
            float rOuter = 0.30f + t * 1.15f;
            float rMid = 0.20f + t * 0.85f;
            float rInner = 0.12f + t * 0.55f;

            int aOuter = Math.max(15, Math.round((1.0f - t) * 210));
            int aMid = Math.max(20, Math.round((1.0f - t) * 240));
            int aCore = Math.max(25, Math.round((1.0f - t) * 255));

            Gizmos.circle(p.pos, rOuter, GizmoStyle.stroke((aOuter << 24) | 0x38BDF8, 4.0f)).setAlwaysOnTop();
            Gizmos.circle(p.pos.add(0, 0.06, 0), rMid, GizmoStyle.stroke((aMid << 24) | 0xA855F7, 3.2f)).setAlwaysOnTop();
            Gizmos.circle(p.pos.add(0, -0.06, 0), rInner, GizmoStyle.stroke((aCore << 24) | 0xFFFFFF, 2.5f)).setAlwaysOnTop();
            Gizmos.line(p.pos.add(0, -0.35 * (1 - t), 0), p.pos.add(0, 0.45 * (1 - t), 0), (aCore << 24) | 0x7DD3FC, 3.0f).setAlwaysOnTop();
        }
    }
}
