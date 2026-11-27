package com.ye.mod.modules.visual;

import com.ye.mod.core.Category;
import com.ye.mod.core.Module;
import com.ye.mod.core.YeSoundPresets;
import com.ye.mod.core.setting.BoolSetting;
import com.ye.mod.core.setting.DoubleSetting;
import com.ye.mod.core.setting.EnumSetting;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Multi-layered 256x256 3D Shockwave + Star-Flare Hit Effect with rich Hit Sound Presets
 * (Meow 喵叫, Error报错, 电子木鱼, 紫晶清鸣, 灵动气泡, 经典叮声, 街机重击, 静音)!
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
            "preset", "视觉预设", "星芒闪耀 / 绯红冲击 / 圣金辉耀 / 虚空震荡", HitPreset.STARBURST
    ));

    public final BoolSetting syncGlobalColor = addSetting(new BoolSetting(
            "syncGlobalColor", "同步全局色彩", "打击冲击波颜色跟随全局色彩模块", false
    ));

    public final DoubleSetting scale = addSetting(new DoubleSetting(
            "scale", "特效大小", "命中特效扩散尺寸", 1.0, 0.5, 2.0, 0.1
    ));

    public final EnumSetting<YeSoundPresets.SoundType> hitSoundType = addSetting(new EnumSetting<>(
            "hitSoundType", "打击音效", "喵叫 / Error报错 / 电子木鱼 / 紫晶 / 气泡 / 叮声 / 暴击 / 静音",
            YeSoundPresets.SoundType.EXPERIENCE
    ));

    public final DoubleSetting soundVolume = addSetting(new DoubleSetting(
            "soundVolume", "音效音量", "调节命中反馈声音大小", 0.80, 0.1, 1.5, 0.05
    ).visible(() -> hitSoundType.get() != YeSoundPresets.SoundType.MUTE));

    private final List<HitPulse> pulses = new CopyOnWriteArrayList<>();

    public HitFeedbackModule() {
        super("hit_feedback", "打击特效", "Hit FX", "多层星芒扩散冲击波与自定义打击音效（喵/Error/木鱼等）", Category.VISUAL);
    }

    @Override
    protected void onDisable() {
        pulses.clear();
    }

    public void onAttackEntity(Entity target) {
        if (!isEnabled() || target == null) return;
        long now = System.currentTimeMillis();
        pulses.add(new HitPulse(target.position().add(0, target.getBbHeight() * 0.56, 0), now, (now % 360)));
        while (pulses.size() > 16) pulses.remove(0);

        YeSoundPresets.playUi(hitSoundType.get(), soundVolume.get().floatValue());
    }

    public List<HitPulse> getPulses() {
        return pulses;
    }
}
