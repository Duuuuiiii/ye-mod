package com.ye.mod.modules.visual;

import com.ye.mod.core.Category;
import com.ye.mod.core.Module;
import com.ye.mod.core.setting.BoolSetting;
import com.ye.mod.core.setting.EnumSetting;
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
 * Rich multi-layered 3D hit shockwave feedback with 4 styled presets.
 */
public class HitFeedbackModule extends Module {
    public enum HitPreset {
        STARBURST("星芒冲击"),
        CRIMSON_SLASH("血红斩击"),
        HOLY_GOLD("金色圣光"),
        VOID_ARRAY("虚空法阵");

        public final String label;
        HitPreset(String label) { this.label = label; }
    }

    public final EnumSetting<HitPreset> preset = addSetting(new EnumSetting<>(
            "preset", "特效预设", "星芒冲击 / 血红斩击 / 金色圣光 / 虚空法阵", HitPreset.STARBURST
    ));

    public final BoolSetting hitSound = addSetting(new BoolSetting(
            "hitSound", "命中音效", "攻击命中时播放清脆提示音", true
    ));

    public final BoolSetting hitRing = addSetting(new BoolSetting(
            "hitRing", "立体特效", "在命中处生成 3D 冲击波", true
    ));

    private record HitPulse(Vec3 pos, long startMs) {}
    private final List<HitPulse> pulses = new CopyOnWriteArrayList<>();

    public HitFeedbackModule() {
        super("hit_feedback", "打击反馈", "Hit Feedback", "攻击目标时弹出多层 3D 冲击特效与清脆音效", Category.VISUAL);
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
        HitPreset pType = preset.get();
        int c1, c2;
        switch (pType) {
            case CRIMSON_SLASH -> { c1 = 0xEF4444; c2 = 0xFCA5A5; }
            case HOLY_GOLD -> { c1 = 0xFACC15; c2 = 0xFEF08A; }
            case VOID_ARRAY -> { c1 = 0xA855F7; c2 = 0x38BDF8; }
            default -> { c1 = 0x38BDF8; c2 = 0xFFFFFF; }
        }

        Iterator<HitPulse> it = pulses.iterator();
        while (it.hasNext()) {
            HitPulse p = it.next();
            long elapsed = now - p.startMs;
            if (elapsed > 460L) {
                pulses.remove(p);
                continue;
            }
            float t = elapsed / 460.0f;
            float rOuter = 0.25f + t * 1.15f;
            float rInner = 0.15f + t * 0.65f;
            int a1 = Math.max(20, Math.round((1.0f - t) * 235));
            int a2 = Math.max(25, Math.round((1.0f - t) * 255));

            Gizmos.circle(p.pos, rOuter, GizmoStyle.stroke((a1 << 24) | c1, 3.8f)).setAlwaysOnTop();
            Gizmos.circle(p.pos.add(0, 0.08 * t, 0), rInner, GizmoStyle.stroke((a2 << 24) | c2, 2.6f)).setAlwaysOnTop();

            if (pType == HitPreset.CRIMSON_SLASH) {
                float s = 0.55f + t * 0.5f;
                Gizmos.line(p.pos.add(-s, -s * 0.7, 0), p.pos.add(s, s * 0.7, 0), (a2 << 24) | c1, 4.0f).setAlwaysOnTop();
                Gizmos.line(p.pos.add(-s, s * 0.7, 0), p.pos.add(s, -s * 0.7, 0), (a2 << 24) | c2, 3.0f).setAlwaysOnTop();
            } else {
                Gizmos.line(p.pos.add(0, -0.4 * (1 - t), 0), p.pos.add(0, 0.5 * (1 - t), 0), (a2 << 24) | c2, 3.0f).setAlwaysOnTop();
            }
        }
    }
}
