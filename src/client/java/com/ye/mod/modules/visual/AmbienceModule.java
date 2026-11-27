package com.ye.mod.modules.visual;

import com.ye.mod.core.Category;
import com.ye.mod.core.ConfigManager;
import com.ye.mod.core.Module;
import com.ye.mod.core.setting.ActionSetting;
import com.ye.mod.core.setting.BoolSetting;
import com.ye.mod.core.setting.DoubleSetting;
import com.ye.mod.core.setting.EnumSetting;
import com.ye.mod.render.YeSkyPanoramaRenderer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Util;
import org.joml.Vector4f;

import java.lang.reflect.Method;

/**
 * Sky & Atmosphere Module (天空氛围):
 * - Built-in 360° Seamless Panorama Skybox Art (深空星云 / 极光星幕 / 绯樱晚霞云海 / 蔚蓝夏日积云 + 自定义大图)!
 * - Default oppressiveVignette ("暗角") is strictly OFF!
 * - Fixed "隐藏日月" & "璀璨繁星" independent toggles (turning off 璀璨繁星 sets star brightness strictly to 0.0f!).
 */
public class AmbienceModule extends Module {
    public enum SkyStyle {
        AZURE_CUMULUS("蔚蓝积云", Theme.CLEAR_AZURE),
        SAKURA_CLOUDS("绯樱云海", Theme.SAKURA_SUNSET),
        AURORA_STARRY("极光星幕", Theme.AURORA_TEAL),
        GALAXY_NEBULA("深空星云", Theme.DREAM_VIOLET),
        CUSTOM_IMAGE("自定义全景", Theme.CLEAR_AZURE),
        GRADIENT_ONLY("纯大气渐变", Theme.SAKURA_SUNSET);

        public final String label;
        public final Theme matchTheme;

        SkyStyle(String label, Theme matchTheme) {
            this.label = label;
            this.matchTheme = matchTheme;
        }
    }

    public enum Theme {
        SAKURA_SUNSET("绯樱晚霞", 0xFF5B3E96, 0xFFFF8FA3, 0.96f, 0.56f, 0.68f, 0.55f),
        CLEAR_AZURE("蔚蓝晴空", 0xFF2563EB, 0xFF93C5FD, 0.58f, 0.80f, 1.00f, 0.0f),
        AURORA_TEAL("极光星夜", 0xFF0F2942, 0xFF2DD4BF, 0.16f, 0.68f, 0.64f, 0.95f),
        DREAM_VIOLET("梦幻紫霞", 0xFF4C1D95, 0xFFD946EF, 0.72f, 0.42f, 0.94f, 0.85f),
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

    public final EnumSetting<SkyStyle> skyStyle = addSetting(new EnumSetting<>(
            "skyStyle", "天空类型", "内置无缝全景 / 自定义全景 / 纯渐变", SkyStyle.AZURE_CUMULUS
    ).functionGroup());

    public final EnumSetting<Theme> theme = addSetting(new EnumSetting<>(
            "theme", "大气雾色调", "地平线与远景大气雾散射配色", Theme.SAKURA_SUNSET
    ).visible(() -> skyStyle.get() == SkyStyle.GRADIENT_ONLY || skyStyle.get() == SkyStyle.CUSTOM_IMAGE).functionGroup());

    public final DoubleSetting skyBrightness = addSetting(new DoubleSetting(
            "skyBrightness", "天空亮度", "调节全景天幕明暗", 1.28, 0.50, 2.00, 0.05
    ).visible(() -> skyStyle.get() != SkyStyle.GRADIENT_ONLY));

    public final DoubleSetting panoRotateSpeed = addSetting(new DoubleSetting(
            "panoRotateSpeed", "云海流转速度", "全景天幕缓慢自转速率", 0.4, 0.0, 5.0, 0.2
    ).visible(() -> skyStyle.get() != SkyStyle.GRADIENT_ONLY).functionGroup());

    public final BoolSetting overridePacks = addSetting(new BoolSetting(
            "overridePacks", "覆盖材质包天空", "接管第三方材质包天空渲染", true
    ).functionGroup());

    public final BoolSetting customSkyAndFog = addSetting(new BoolSetting(
            "customSkyAndFog", "同步远景雾色", "启用地平线大气雾过渡", true
    ).functionGroup());

    public final BoolSetting showStars = addSetting(new BoolSetting(
            "showStars", "璀璨繁星", "开关原版星空点缀", false
    ).functionGroup());

    public final BoolSetting hideSunMoon = addSetting(new BoolSetting(
            "hideCelestial", "隐藏日月", "隐藏原版太阳与月亮贴图", true
    ).functionGroup());

    public final DoubleSetting fogDistanceScale = addSetting(new DoubleSetting(
            "fogDistanceScale", "霞光延伸度", "调节远景雾气过渡距离", 0.95, 0.25, 1.60, 0.05
    ).functionGroup());

    public final BoolSetting syncGlobalColor = addSetting(new BoolSetting(
            "syncGlobalColor", "跟随同步色", "地平线霞光跟随同步色模块", false
    ));

    public final BoolSetting oppressiveVignette = addSetting(new BoolSetting(
            "oppressiveVignette", "暗角", "屏幕上下边缘叠加柔和暗角（默认关闭）", false
    ));

    public final DoubleSetting vignetteStrength = addSetting(new DoubleSetting(
            "vignetteStrength", "暗角浓度", "调节边缘暗角强度", 0.35, 0.1, 1.0, 0.05
    ).visible(oppressiveVignette::get));

    private static boolean nuitChecked = false;
    private static Method nuitGetInstance = null;
    private static Method nuitIsEnabled = null;
    private static Method nuitSetEnabled = null;
    private boolean disabledNuitByUs = false;

    public AmbienceModule() {
        super("ambience", "天空氛围", "Ambience", "内置360°无缝全景天空与大气霞光渲染", Category.VISUAL);

        addSetting(new ActionSetting("openSkyFolder", "打开自定义天空目录", "将任意全景 PNG/JPG 放入此目录即可切换为自定义天空", () -> {
            ConfigManager.ensureDirectories();
            YeSkyPanoramaRenderer.reloadCustomSky();
            Util.getPlatform().openPath(ConfigManager.getSkiesDir());
        }).visible(() -> skyStyle.get() == SkyStyle.CUSTOM_IMAGE).functionGroup());
    }

    public boolean usesPanoramaDome() {
        return isEnabled() && skyStyle.get() != SkyStyle.GRADIENT_ONLY;
    }

    private Theme activeFogTheme() {
        SkyStyle ss = skyStyle.get();
        if (ss == SkyStyle.GRADIENT_ONLY || ss == SkyStyle.CUSTOM_IMAGE) {
            return theme.get();
        }
        return ss.matchTheme;
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
            boolean wantDisableNuit = isEnabled() && overridePacks.get();
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
        Theme t = activeFogTheme();
        if (syncGlobalColor.get()) {
            int gRgb = GlobalColorModule.resolveRgb(true, t.skyArgb, 0);
            int r = Math.round(((gRgb >> 16) & 0xFF) * 0.55f);
            int g = Math.round(((gRgb >> 8) & 0xFF) * 0.55f);
            int b = Math.round((gRgb & 0xFF) * 0.65f);
            return 0xFF000000 | (r << 16) | (g << 8) | b;
        }
        return t.skyArgb;
    }

    public int getHorizonArgb() {
        Theme t = activeFogTheme();
        if (syncGlobalColor.get()) {
            return GlobalColorModule.resolveArgb(true, t.horizonArgb, 2);
        }
        return t.horizonArgb;
    }

    public Vector4f getFogColor() {
        if (syncGlobalColor.get()) {
            int h = getHorizonArgb();
            return new Vector4f(((h >> 16) & 0xFF) / 255.0f, ((h >> 8) & 0xFF) / 255.0f, (h & 0xFF) / 255.0f, 1.0f);
        }
        Theme t = activeFogTheme();
        return new Vector4f(t.fogR, t.fogG, t.fogB, 1.0f);
    }

    /**
     * Strictly returns 0.0f when showStars is disabled so turning OFF 璀璨繁星 always turns stars off immediately!
     */
    public float getStarBrightness(float vanillaStarBrightness) {
        if (!showStars.get()) return 0.0f;
        return Math.max(vanillaStarBrightness, Math.max(0.65f, activeFogTheme().starAlpha));
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
