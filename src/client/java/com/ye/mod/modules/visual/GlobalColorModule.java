package com.ye.mod.modules.visual;

import com.ye.mod.core.Category;
import com.ye.mod.core.ConfigManager;
import com.ye.mod.core.Module;
import com.ye.mod.core.ModuleManager;
import com.ye.mod.core.setting.ActionSetting;
import com.ye.mod.core.setting.BoolSetting;
import com.ye.mod.core.setting.ColorSetting;
import com.ye.mod.core.setting.DoubleSetting;
import com.ye.mod.core.setting.EnumSetting;
import com.ye.mod.core.setting.Setting;
import com.ye.mod.ui.YeQuickMenu;

import java.awt.Color;

/**
 * Synchronized Color Module (同步色) for all HUD widgets & visual effects!
 * Separated cleanly from UI Menu Theme Color (设置 -> 主题色).
 * Features Rainbow Gradient (彩虹渐变), Dual-Color Breathing, Presets, and 2D Draggable HSV Color Picker!
 */
public class GlobalColorModule extends Module {
    public enum ColorMode {
        RAINBOW("渐变"),
        CUSTOM_PICKER("自定义调色"),
        BREATHING("双色渐变"),
        PRESET("精选色板");

        public final String label;
        ColorMode(String label) { this.label = label; }
    }

    public enum PresetPalette {
        ICE_CYAN("冰晶青", 0xFF38BDF8, 0xFFA855F7),
        CYBER_PURPLE("紫罗兰", 0xFFA855F7, 0xFF22D3EE),
        CRIMSON("绯红", 0xFFEF4444, 0xFFFACC15),
        ROYAL_GOLD("鎏金", 0xFFFACC15, 0xFFF97316),
        EMERALD("翡翠绿", 0xFF34D399, 0xFF38BDF8),
        SAKURA("樱花粉", 0xFFF472B6, 0xFFC084FC),
        PURE_WHITE("纯白", 0xFFF8FAFC, 0xFF94A3B8);

        public final String label;
        public final int argb;
        public final int secondArgb;

        PresetPalette(String label, int argb, int secondArgb) {
            this.label = label;
            this.argb = argb;
            this.secondArgb = secondArgb;
        }
    }

    public final EnumSetting<ColorMode> mode = addSetting(new EnumSetting<>(
            "mode", "同步模式", "彩虹渐变 / 自定义调色 / 双色渐变 / 精选色板", ColorMode.RAINBOW
    ).functionGroup());

    public final EnumSetting<PresetPalette> preset = addSetting(new EnumSetting<>(
            "preset", "预设色板", "选择主副预设配色", PresetPalette.ICE_CYAN
    ).visible(() -> mode.get() == ColorMode.PRESET).functionGroup());

    public final ColorSetting primaryColor = addSetting(new ColorSetting(
            "primaryColor", "主色调色板", "拖动面板自由选取同步主色", 0xFF38BDF8
    ).visible(() -> mode.get() == ColorMode.CUSTOM_PICKER || mode.get() == ColorMode.BREATHING));

    public final ColorSetting secondaryColor = addSetting(new ColorSetting(
            "secondaryColor", "副色调色板", "拖动面板自由选取双色渐变第二颜色", 0xFFA855F7
    ).visible(() -> mode.get() == ColorMode.BREATHING));

    public final DoubleSetting cycleSpeed = addSetting(new DoubleSetting(
            "cycleSpeed", "流转速率", "彩虹渐变或双色流动速度", 1.0, 0.2, 4.0, 0.1
    ).visible(() -> mode.get() == ColorMode.RAINBOW || mode.get() == ColorMode.BREATHING).functionGroup());

    public final DoubleSetting saturation = addSetting(new DoubleSetting(
            "saturation", "彩虹饱和度", "彩虹色彩鲜艳程度", 0.78, 0.25, 1.0, 0.02
    ).visible(() -> mode.get() == ColorMode.RAINBOW));

    public final DoubleSetting brightness = addSetting(new DoubleSetting(
            "brightness", "彩虹明度", "彩虹整体明亮程度", 1.0, 0.40, 1.0, 0.02
    ).visible(() -> mode.get() == ColorMode.RAINBOW));

    public final BoolSetting phaseWave = addSetting(new BoolSetting(
            "phaseWave", "空间波浪渐变", "不同 HUD 与特效之间形成连续流动色差", true
    ).visible(() -> mode.get() == ColorMode.RAINBOW || mode.get() == ColorMode.BREATHING).functionGroup());

    public GlobalColorModule() {
        super("global_color", "同步色", "Sync Color", "统管 HUD 与视觉模块的同步颜色，支持流光彩虹渐变与自由拖动调色", Category.VISUAL);
        setEnabled(true, true);

        addSetting(new ActionSetting("syncAllHudOn", "一键同步所有HUD", "开启全部 HUD 子模块的同步色开关", () -> {
            setEnabled(true);
            int count = setHudSyncFlags(true);
            YeQuickMenu.showToast(ConfigManager.tr("已同步 " + count + " 个 HUD 组件", "Synced " + count + " HUDs"), getCurrentArgb(0));
        }).functionGroup());

        addSetting(new ActionSetting("syncAllOn", "一键同步全部模块", "开启全部 HUD 与视觉模块的同步色开关", () -> {
            setEnabled(true);
            int count = setAllModuleSyncFlags(true);
            YeQuickMenu.showToast(ConfigManager.tr("已同步全部 " + count + " 个模块", "Synced " + count + " modules"), getCurrentArgb(0));
        }).functionGroup());

        addSetting(new ActionSetting("syncAllOff", "关闭全部同步", "恢复各模块独立配色", () -> {
            int count = setAllModuleSyncFlags(false);
            YeQuickMenu.showToast(ConfigManager.tr("已恢复 " + count + " 个模块独立色", "Restored independent colors"), 0xFF94A3B8);
        }).functionGroup());
    }

    public static int setHudSyncFlags(boolean sync) {
        int changed = 0;
        for (Module m : ModuleManager.getByCategory(Category.HUD)) {
            for (Setting<?> s : m.getSettings()) {
                if ("syncGlobalColor".equals(s.getId()) && s instanceof BoolSetting bs) {
                    bs.set(sync);
                    changed++;
                }
            }
        }
        ConfigManager.saveAsync();
        return changed;
    }

    public static int setAllModuleSyncFlags(boolean sync) {
        int changed = 0;
        for (Module m : ModuleManager.getAll()) {
            for (Setting<?> s : m.getSettings()) {
                if ("syncGlobalColor".equals(s.getId()) && s instanceof BoolSetting bs) {
                    bs.set(sync);
                    changed++;
                }
            }
        }
        ConfigManager.saveAsync();
        return changed;
    }

    public static int countSyncedModules() {
        int count = 0;
        for (Module m : ModuleManager.getAll()) {
            for (Setting<?> s : m.getSettings()) {
                if ("syncGlobalColor".equals(s.getId()) && s instanceof BoolSetting bs && bs.get()) {
                    count++;
                }
            }
        }
        return count;
    }

    public int getCurrentRgb(int phaseIndex) {
        long now = System.currentTimeMillis();
        double spd = cycleSpeed.get();
        double phase = phaseWave.get() ? (phaseIndex * 0.085) : 0.0;

        return switch (mode.get()) {
            case PRESET -> preset.get().argb & 0x00FFFFFF;
            case CUSTOM_PICKER -> primaryColor.getRgb();
            case RAINBOW -> {
                double hue = (((now / 4200.0) * spd - phase) % 1.0 + 1.0) % 1.0;
                yield Color.HSBtoRGB((float) hue, saturation.get().floatValue(), brightness.get().floatValue()) & 0x00FFFFFF;
            }
            case BREATHING -> {
                int c1 = primaryColor.getRgb();
                int c2 = secondaryColor.getRgb();
                double wave = 0.5 + 0.5 * Math.sin((now / 850.0) * spd - phase * Math.PI * 2.0);
                yield lerpRgb(c1, c2, (float) wave);
            }
        };
    }

    /**
     * Spatial rainbow/gradient helper for per-character or per-pixel flowing HUD bars!
     */
    public int getSpatialArgb(float spatialOffset) {
        long now = System.currentTimeMillis();
        double spd = cycleSpeed.get();
        if (mode.get() == ColorMode.RAINBOW) {
            double hue = (((now / 4200.0) * spd - spatialOffset * 0.04) % 1.0 + 1.0) % 1.0;
            return 0xFF000000 | (Color.HSBtoRGB((float) hue, saturation.get().floatValue(), brightness.get().floatValue()) & 0x00FFFFFF);
        } else if (mode.get() == ColorMode.BREATHING) {
            double wave = 0.5 + 0.5 * Math.sin((now / 850.0) * spd - spatialOffset * 0.25);
            return 0xFF000000 | lerpRgb(primaryColor.getRgb(), secondaryColor.getRgb(), (float) wave);
        }
        return getCurrentArgb(0);
    }

    public int getCurrentArgb(int phaseIndex) {
        return 0xFF000000 | getCurrentRgb(phaseIndex);
    }

    public static int resolveRgb(boolean moduleSyncEnabled, int fallbackRgb, int phaseIndex) {
        if (!moduleSyncEnabled) return fallbackRgb & 0x00FFFFFF;
        GlobalColorModule g = ModuleManager.get(GlobalColorModule.class);
        if (g == null || !g.isEnabled()) return fallbackRgb & 0x00FFFFFF;
        return g.getCurrentRgb(phaseIndex);
    }

    public static int resolveArgb(boolean moduleSyncEnabled, int fallbackArgb, int phaseIndex) {
        if (!moduleSyncEnabled) return fallbackArgb;
        GlobalColorModule g = ModuleManager.get(GlobalColorModule.class);
        if (g == null || !g.isEnabled()) return fallbackArgb;
        int alpha = (fallbackArgb >>> 24) & 0xFF;
        if (alpha == 0) alpha = 0xFF;
        return (alpha << 24) | g.getCurrentRgb(phaseIndex);
    }

    private static int lerpRgb(int c1, int c2, float t) {
        int r1 = (c1 >> 16) & 0xFF, g1 = (c1 >> 8) & 0xFF, b1 = c1 & 0xFF;
        int r2 = (c2 >> 16) & 0xFF, g2 = (c2 >> 8) & 0xFF, b2 = c2 & 0xFF;
        int r = Math.round(r1 + (r2 - r1) * t);
        int g = Math.round(g1 + (g2 - g1) * t);
        int b = Math.round(b1 + (b2 - b1) * t);
        return (r << 16) | (g << 8) | b;
    }
}
