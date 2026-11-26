package com.ye.mod.modules.visual;

import com.ye.mod.core.Category;
import com.ye.mod.core.Module;
import com.ye.mod.core.setting.BoolSetting;
import com.ye.mod.core.setting.DoubleSetting;
import com.ye.mod.core.setting.EnumSetting;
import net.minecraft.client.gui.GuiGraphics;
import org.joml.Vector4f;

/**
 * Integrated Sky, Aurora, Time Lock & Hachimi Oppressive Dark Atmosphere module.
 */
public class AmbienceModule extends Module {
    public enum Theme {
        HACHIMI_ABYSS("Hachimi压抑灰黑", 0xFF060709, 0.035f, 0.038f, 0.045f),
        PURE_BLACK("纯黑虚空", 0xFF030406, 0.015f, 0.015f, 0.02f),
        OBSIDIAN_PURPLE("黑曜暗紫", 0xFF0E0818, 0.08f, 0.04f, 0.14f),
        BLOOD_NIGHT("暗夜血月", 0xFF140507, 0.12f, 0.02f, 0.03f),
        AURORA_NIGHT("极光夜空", 0xFF06151E, 0.03f, 0.14f, 0.16f),
        SAKURA_DUSK("樱花黄昏", 0xFF281224, 0.22f, 0.10f, 0.18f);

        public final String label;
        final int skyArgb;
        final float fogR;
        final float fogG;
        final float fogB;

        public int getSkyArgb() { return skyArgb; }

        Theme(String label, int skyArgb, float fogR, float fogG, float fogB) {
            this.label = label;
            this.skyArgb = skyArgb;
            this.fogR = fogR;
            this.fogG = fogG;
            this.fogB = fogB;
        }
    }

    public final EnumSetting<Theme> theme = addSetting(new EnumSetting<>(
            "theme", "天象主题", "Hachimi压抑灰黑 / 纯黑 / 暗紫 / 血月 / 极光 / 樱花黄昏", Theme.HACHIMI_ABYSS
    ));

    public final BoolSetting customSkyAndFog = addSetting(new BoolSetting(
            "customSkyAndFog", "自定义天空雾色", "替换天空颜色与远景雾效", true
    ));

    public final BoolSetting hideCelestial = addSetting(new BoolSetting(
            "hideCelestial", "隐藏日月星辰", "移除太阳月亮星星，营造压抑暗黑天幕", true
    ));

    public final DoubleSetting fogDistanceScale = addSetting(new DoubleSetting(
            "fogDistanceScale", "雾效拉近", "越小越压抑深邃，越大视野越远", 0.55, 0.15, 1.50, 0.05
    ));

    public final BoolSetting oppressiveVignette = addSetting(new BoolSetting(
            "oppressiveVignette", "Hachimi压抑暗角", "屏幕四周叠加冷冽黑色压抑暗角与微暗滤镜", true
    ));

    public final DoubleSetting vignetteStrength = addSetting(new DoubleSetting(
            "vignetteStrength", "暗角压抑强度", "调节四周暗角浓重程度", 0.55, 0.1, 1.0, 0.05
    ));

    public AmbienceModule() {
        super("ambience", "天象氛围", "Atmosphere", "Hachimi 压抑暗黑氛围、天空替换、极光/血月与雾效控制", Category.VISUAL);
    }

    public int getSkyArgb() {
        return theme.get().skyArgb;
    }

    public Vector4f getFogColor() {
        Theme t = theme.get();
        return new Vector4f(t.fogR, t.fogG, t.fogB, 1.0f);
    }

    public void renderOverlay(GuiGraphics g) {
        if (!isEnabled() || !oppressiveVignette.get()) return;
        int sw = g.guiWidth();
        int sh = g.guiHeight();
        float str = vignetteStrength.get().floatValue();
        int edgeA = Math.min(200, Math.round(145 * str));
        int tintA = Math.min(80, Math.round(28 * str));

        // Subtle desaturated dark veil
        g.fill(0, 0, sw, sh, (tintA << 24) | 0x0005070B);

        // Top & bottom oppressive vignette gradients
        int band = Math.max(24, sh / 6);
        g.fillGradient(0, 0, sw, band, (edgeA << 24) | 0x00020305, 0x00020305);
        g.fillGradient(0, sh - band, sw, sh, 0x00020305, (edgeA << 24) | 0x00020305);
    }
}
