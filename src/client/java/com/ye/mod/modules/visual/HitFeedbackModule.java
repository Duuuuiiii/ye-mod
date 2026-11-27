package com.ye.mod.modules.visual;

import com.ye.mod.core.Category;
import com.ye.mod.core.Module;
import com.ye.mod.core.setting.BoolSetting;
import com.ye.mod.core.setting.DoubleSetting;
import com.ye.mod.core.setting.EnumSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Multi-layered 256x256 3D Shockwave + Star-Flare Hit Effect (rendered via YeWorldFxRenderer).
 */
public class HitFeedbackModule extends Module {
    public enum HitPreset {
        STARBURST("星芒闪耀", 0x38BDF8, 0xFFFFFF),
        CRIMSON_NOVA("绯红冲击", 0xEF4444, 0xFCA5A5),
        HOLY_GOLD("圣金辉耀", 0xFACC15, 0xFEF08A),
        VOID_PULSE("虚空震荡", 0xA855F7, 0x22D3EE);

        public final String label;
        public final int outerRgb;
        public final int coreRgb;

        HitPreset(String label, int outerRgb, int coreRgb) {
            this.label = label;
            this.outerRgb = outerRgb;
            this.coreRgb = coreRgb;
        }
    }

    public record HitPulse(Vec3 pos, long startMs, float rotOffset) {}

    public final EnumSetting<HitPreset> preset = addSetting(new EnumSetting<>(
            "preset", "预设", "星芒闪耀 / 绯红冲击 / 圣金辉耀 / 虚空震荡", HitPreset.STARBURST
    ));

    public final DoubleSetting scale = addSetting(new DoubleSetting(
            "scale", "大小", "命中特效扩散尺寸", 1.0, 0.5, 2.0, 0.1
    ));

    public final BoolSetting hitSound = addSetting(new BoolSetting(
            "hitSound", "音效", "命中播放清脆反馈音", true
    ));

    private final List<HitPulse> pulses = new CopyOnWriteArrayList<>();

    public HitFeedbackModule() {
        super("hit_feedback", "打击特效", "Hit FX", "多层高清星芒闪光与立体扩散冲击波", Category.VISUAL);
    }

    @Override
    protected void onDisable() {
        pulses.clear();
    }

    public void onAttackEntity(Entity target) {
        if (!isEnabled() || target == null) return;
        Minecraft mc = Minecraft.getInstance();
        long now = System.currentTimeMillis();
        pulses.add(new HitPulse(target.position().add(0, target.getBbHeight() * 0.56, 0), now, (now % 360)));
        while (pulses.size() > 16) pulses.remove(0);

        if (hitSound.get() && mc.player != null) {
            mc.player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.45f, 1.35f);
        }
    }

    public List<HitPulse> getPulses() {
        return pulses;
    }
}
