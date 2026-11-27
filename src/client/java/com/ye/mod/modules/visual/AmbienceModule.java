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
 * Sky, Fog & Oppressive Vignette Atmosphere module.
 * Automatically overrides Nuit custom resourcepack skyboxes when active so sky/fog changes ALWAYS work!
 */
public class AmbienceModule extends Module {
    public enum Theme {
        ABYSS_GRAY("深渊灰黑", 0xFF060709, 0.035f, 0.038f, 0.045f),
        PURE_VOID("纯黑虚空", 0xFF020305, 0.012f, 0.012f, 0.018f),
        OBSIDIAN_PURPLE("暗夜紫晶", 0xFF10071E, 0.09f, 0.04f, 0.16f),
        CRIMSON_MOON("猩红血夜", 0xFF180508, 0.15f, 0.025f, 0.035f),
        AURORA_CYAN("极光寒夜", 0xFF051822, 0.03f, 0.16f, 0.20f),
        SAKURA_DUSK("绯樱黄昏", 0xFF2B1226, 0.24f, 0.10f, 0.19f);

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
            "theme", "预设", "深渊灰黑 / 纯黑 / 暗紫 / 血夜 / 极光 / 绯樱", Theme.ABYSS_GRAY
    ));

    public final BoolSetting overridePacks = addSetting(new BoolSetting(
            "overridePacks", "覆盖材质包天空", "强制接管 Nuit 等材质包自定义天空，确保天空修改生效", true
    ));

    public final BoolSetting customSkyAndFog = addSetting(new BoolSetting(
            "customSkyAndFog", "天空与雾色", "替换天空天幕与远景雾气颜色", true
    ));

    public final BoolSetting hideCelestial = addSetting(new BoolSetting(
            "hideCelestial", "隐藏日月星", "移除太阳月亮与星星", true
    ));

    public final DoubleSetting fogDistanceScale = addSetting(new DoubleSetting(
            "fogDistanceScale", "雾距缩放", "越小雾气越近越深邃", 0.55, 0.15, 1.50, 0.05
    ));

    public final BoolSetting oppressiveVignette = addSetting(new BoolSetting(
            "oppressiveVignette", "暗角", "屏幕四周叠加冷冽深邃暗角", true
    ));

    public final DoubleSetting vignetteStrength = addSetting(new DoubleSetting(
            "vignetteStrength", "暗角强度", "调节四周暗角浓重程度", 0.55, 0.1, 1.0, 0.05
    ).visible(oppressiveVignette::get));

    // Cached reflection handle for Nuit mod (me.flashyreese.mods.nuit.SkyboxManager)
    private static boolean nuitChecked = false;
    private static Method nuitGetInstance = null;
    private static Method nuitIsEnabled = null;
    private static Method nuitSetEnabled = null;
    private boolean disabledNuitByUs = false;

    public AmbienceModule() {
        super("ambience", "天空氛围", "Ambience", "自定义天空雾色、压抑暗角并支持强制覆盖材质包天空", Category.VISUAL);
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
        } catch (Throwable ignored) {
        }
    }

    private void restoreNuit() {
        if (!disabledNuitByUs || nuitGetInstance == null) return;
        try {
            Object mgr = nuitGetInstance.invoke(null);
            if (mgr != null) {
                nuitSetEnabled.invoke(mgr, true);
            }
        } catch (Throwable ignored) {
        }
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
        } catch (Throwable ignored) {
        }
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
        int edgeA = Math.min(210, Math.round(155 * str));
        int tintA = Math.min(85, Math.round(30 * str));

        g.fill(0, 0, sw, sh, (tintA << 24) | 0x0004060A);
        int band = Math.max(28, sh / 5);
        g.fillGradient(0, 0, sw, band, (edgeA << 24) | 0x00020305, 0x00020305);
        g.fillGradient(0, sh - band, sw, sh, 0x00020305, (edgeA << 24) | 0x00020305);
    }
}
