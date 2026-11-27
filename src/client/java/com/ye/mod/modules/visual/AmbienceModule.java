package com.ye.mod.modules.visual;

import com.ye.mod.core.Category;
import com.ye.mod.core.Module;
import com.ye.mod.core.setting.BoolSetting;
import com.ye.mod.core.setting.DoubleSetting;
import com.ye.mod.core.setting.EnumSetting;
import net.minecraft.client.gui.GuiGraphics;
import org.joml.Vector4f;

import java.lang.reflect.Method;

/**
 * Luminous Dual-Tone Zenith-to-Horizon Sky & Atmosphere Module:
 * - Eliminates pitch-black flat sky discs (shouldRenderDarkDisc = false even at Y=5!)
 * - Vibrant zenith + glowing horizon fog gradients + starry sky brightness control
 * - Automatically overrides Nuit custom skyboxes when active
 */
public class AmbienceModule extends Module {
    public enum Theme {
        SAKURA_SUNSET("绯樱晚霞", 0xFF5B3E96, 0xFFFF8FA3, 0.96f, 0.56f, 0.68f, 0.55f),
        CLEAR_AZURE("蔚蓝晴空", 0xFF2563EB, 0xFF93C5FD, 0.58f, 0.80f, 1.00f, 0.0f),
        AURORA_TEAL("极光星夜", 0xFF0F2942, 0xFF2DD4BF, 0.16f, 0.68f, 0.64f, 0.95f),
        DREAM_VIOLET("梦幻紫霞", 0xFF4C1D95, 0xFFD946EF, 0.72f, 0.42f, 0.94f, 0.80f),
        GOLDEN_DUSK("鎏金黄昏", 0xFF1E40AF, 0xFFFB923C, 0.98f, 0.64f, 0.30f, 0.30f),
        CRIMSON_MOON("绯红血月", 0xFF581122, 0xFFF43F5E, 0.82f, 0.22f, 0.32f, 0.85f);

        public final String label;
        public final int skyArgb;
        public final int horizonArgb;
        final float fogR;
        final float fogG;
        final float fogB;
        public final float starAlpha;

        public int getSkyArgb() { return skyArgb; }

        Theme(String label, int skyArgb, int horizonArgb, float fogR, float fogG, float fogB, float starAlpha) {
            this.label = label;
            this.skyArgb = skyArgb;
            this.horizonArgb = horizonArgb;
            this.fogR = fogR;
            this.fogG = fogG;
            this.fogB = fogB;
            this.starAlpha = starAlpha;
        }
    }

    public final EnumSetting<Theme> theme = addSetting(new EnumSetting<>(
            "theme", "预设", "绯樱晚霞 / 蔚蓝晴空 / 极光星夜 / 梦幻紫霞 / 鎏金黄昏 / 绯红血月", Theme.SAKURA_SUNSET
    ));

    public final BoolSetting syncGlobalColor = addSetting(new BoolSetting(
            "syncGlobalColor", "同步全局色彩", "地平线霞光跟随全局色彩模块", false
    ));

    public final BoolSetting overridePacks = addSetting(new BoolSetting(
            "overridePacks", "覆盖材质包天空", "自动接管 Nuit 等材质包天空，确保天空渲染生效", true
    ));

    public final BoolSetting customSkyAndFog = addSetting(new BoolSetting(
            "customSkyAndFog", "天空与地平线雾", "启用天顶与地平线双色大气散射渐变", true
    ));

    public final BoolSetting showStars = addSetting(new BoolSetting(
            "showStars", "璀璨繁星", "在天幕上点亮满天繁星", true
    ));

    public final BoolSetting hideSunMoon = addSetting(new BoolSetting(
            "hideCelestial", "隐藏日月", "隐藏原版方形太阳与月亮", false
    ));

    public final DoubleSetting fogDistanceScale = addSetting(new DoubleSetting(
            "fogDistanceScale", "霞光延伸度", "调节远景地平线雾气过渡距离", 0.85, 0.25, 1.60, 0.05
    ));

    public final BoolSetting oppressiveVignette = addSetting(new BoolSetting(
            "oppressiveVignette", "电影暗角", "屏幕上下边缘叠加柔和暗角", false
    ));

    public final DoubleSetting vignetteStrength = addSetting(new DoubleSetting(
            "vignetteStrength", "暗角强度", "调节四周暗角浓重程度", 0.40, 0.1, 1.0, 0.05
    ).visible(oppressiveVignette::get));

    private static boolean nuitChecked = false;
    private static Method nuitGetInstance = null;
    private static Method nuitIsEnabled = null;
    private static Method nuitSetEnabled = null;
    private boolean disabledNuitByUs = false;

    public AmbienceModule() {
        super("ambience", "天空氛围", "Ambience", "通透双色大气渐变天空与繁星氛围，兼容材质包覆盖", Category.VISUAL);
    }

    @Override
    protected void onEnable() {
        syncNuitOverride();
    }

    @Override
    protected void onDisable() {
        restoreNuit();
    }

    @Override
    public void onTick() {
        syncNuitOverride();
    }

    private void syncNuitOverride() {
        initNuitReflection();
        if (nuitGetInstance == null) return;
        try {
            Object mgr = nuitGetInstance.invoke(null);
            if (mgr == null) return;
            boolean wantDisableNuit = isEnabled() && customSkyAndFog.get() && overridePacks.get();
            boolean currentlyEnabled = (Boolean) nuitIsEnabled.invoke(mgr);
            if (wantDisableNuit && currentlyEnabled) {
                nuitSetEnabled.invoke(mgr, false);
                disabledNuitByUs = true;
            } else if (!wantDisableNuit && disabledNuitByUs) {
                nuitSetEnabled.invoke(mgr, true);
                disabledNuitByUs = false;
            }
        } catch (Throwable ignored) {}
    }

    private void restoreNuit() {
        if (!disabledNuitByUs || nuitGetInstance == null) return;
        try {
            Object mgr = nuitGetInstance.invoke(null);
            if (mgr != null) nuitSetEnabled.invoke(mgr, true);
        } catch (Throwable ignored) {}
        disabledNuitByUs = false;
    }

    private static void initNuitReflection() {
        if (nuitChecked) return;
        nuitChecked = true;
        try {
            Class<?> cls = Class.forName("me.flashyreese.mods.nuit.SkyboxManager");
            nuitGetInstance = cls.getMethod("getInstance");
            nuitIsEnabled = cls.getMethod("isEnabled");
            nuitSetEnabled = cls.getMethod("setEnabled", boolean.class);
        } catch (Throwable ignored) {}
    }

    public int getSkyArgb() {
        if (syncGlobalColor.get()) {
            int gRgb = GlobalColorModule.resolveRgb(true, theme.get().skyArgb, 0);
            // Deepen zenith to 55% brightness so horizon glows brighter!
            int r = Math.round(((gRgb >> 16) & 0xFF) * 0.55f);
            int g = Math.round(((gRgb >> 8) & 0xFF) * 0.55f);
            int b = Math.round((gRgb & 0xFF) * 0.65f);
            return 0xFF000000 | (r << 16) | (g << 8) | b;
        }
        return theme.get().skyArgb;
    }

    public int getHorizonArgb() {
        if (syncGlobalColor.get()) {
            return GlobalColorModule.resolveArgb(true, theme.get().horizonArgb, 2);
        }
        return theme.get().horizonArgb;
    }

    public Vector4f getFogColor() {
        if (syncGlobalColor.get()) {
            int h = getHorizonArgb();
            return new Vector4f(((h >> 16) & 0xFF) / 255.0f, ((h >> 8) & 0xFF) / 255.0f, (h & 0xFF) / 255.0f, 1.0f);
        }
        Theme t = theme.get();
        return new Vector4f(t.fogR, t.fogG, t.fogB, 1.0f);
    }

    public float getStarBrightness(float vanillaStarBrightness) {
        if (!showStars.get()) return vanillaStarBrightness;
        return Math.max(vanillaStarBrightness, theme.get().starAlpha);
    }

    public void renderOverlay(GuiGraphics g) {
        if (!isEnabled() || !oppressiveVignette.get()) return;
        int sw = g.guiWidth();
        int sh = g.guiHeight();
        float str = vignetteStrength.get().floatValue();
        int edgeA = Math.min(180, Math.round(135 * str));
        int band = Math.max(24, sh / 5);
        g.fillGradient(0, 0, sw, band, (edgeA << 24) | 0x00020308, 0x00020308);
        g.fillGradient(0, sh - band, sw, sh, 0x00020308, (edgeA << 24) | 0x00020308);
    }
}
