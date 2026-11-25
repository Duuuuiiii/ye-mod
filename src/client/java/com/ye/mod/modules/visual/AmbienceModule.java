package com.ye.mod.modules.visual;

import com.ye.mod.core.Category;
import com.ye.mod.core.Module;
import com.ye.mod.core.setting.BoolSetting;
import com.ye.mod.core.setting.DoubleSetting;
import com.ye.mod.core.setting.EnumSetting;
import org.joml.Vector4f;

/**
 * Mio / Hachimi inspired dark atmospheric world renderer:
 * overrides sky disc, horizon, celestial bodies, and fog color/density client-side.
 */
public class AmbienceModule extends Module {
    public enum Theme {
        PURE_BLACK(0xFF040507, 0.02f, 0.02f, 0.03f),
        OBSIDIAN_PURPLE(0xFF0E0818, 0.08f, 0.04f, 0.14f),
        BLOOD_NIGHT(0xFF140507, 0.12f, 0.02f, 0.03f),
        CYBER_ABYSS(0xFF050E1A, 0.03f, 0.08f, 0.16f);

        final int skyArgb;
        final float fogR;
        final float fogG;
        final float fogB;

        Theme(int skyArgb, float fogR, float fogG, float fogB) {
            this.skyArgb = skyArgb;
            this.fogR = fogR;
            this.fogG = fogG;
            this.fogB = fogB;
        }
    }

    public final EnumSetting<Theme> theme = addSetting(new EnumSetting<>(
            "theme", "氛围主题", "PURE_BLACK=纯黑虚空, OBSIDIAN_PURPLE=黑曜暗紫, BLOOD_NIGHT=暗夜血红, CYBER_ABYSS=深海赛博",
            Theme.PURE_BLACK
    ));

    public final BoolSetting customSkyAndFog = addSetting(new BoolSetting(
            "customSkyAndFog", "暗黑天空与雾色", "将天空与远景雾效染成深邃暗色系，营造无政府端氛围", true
    ));

    public final BoolSetting hideCelestial = addSetting(new BoolSetting(
            "hideCelestial", "隐藏太阳月亮星星", "移除刺眼的太阳月亮，保留纯粹的深色天幕", true
    ));

    public final DoubleSetting fogDistanceScale = addSetting(new DoubleSetting(
            "fogDistanceScale", "雾效拉近比例", "数值越小近景越深邃暗黑，越大视野越远", 0.65, 0.20, 1.50, 0.05
    ));

    public AmbienceModule() {
        super("ambience", "暗黑氛围渲染", "Dark Ambience", "Mio/Hachimi 同款暗黑天空、雾色与氛围渲染", Category.VISUAL);
    }

    public int getSkyArgb() {
        return theme.get().skyArgb;
    }

    public Vector4f getFogColor() {
        Theme t = theme.get();
        return new Vector4f(t.fogR, t.fogG, t.fogB, 1.0f);
    }
}
