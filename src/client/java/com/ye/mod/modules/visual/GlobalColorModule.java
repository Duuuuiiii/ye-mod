package com.ye.mod.modules.visual;

import com.ye.mod.core.Category;
import com.ye.mod.core.ConfigManager;
import com.ye.mod.core.Module;
import com.ye.mod.core.ModuleManager;
import com.ye.mod.core.setting.ActionSetting;
import com.ye.mod.core.setting.BoolSetting;
import com.ye.mod.core.setting.DoubleSetting;
import com.ye.mod.core.setting.EnumSetting;
import com.ye.mod.core.setting.Setting;
import com.ye.mod.ui.YeQuickMenu;

import java.awt.Color;

/**
 * Global Color Synchronization Center (全局色彩).
 * Supports Custom RGB, Flowing Rainbow (with optional phase wave), Dual-Color Breathing Gradient, and Curated Presets.
 * Every color-capable module has an independent "syncGlobalColor" ("同步全局色彩", default false) toggle,
 * plus one-click batch sync buttons right here!
 */
public class GlobalColorModule extends Module {
    public enum ColorMode {
        PRESET("精选色板"),
        CUSTOM_RGB("自定义RGB"),
        RAINBOW("流光彩虹"),
        BREATHING("双色呼吸");

        public final String label;
        ColorMode(String label) { this.label = label; }
    }

    public enum PresetPalette {
        ICE_CYAN("冰晶青", 0xFF38BDF8, 0xFFA855F7),
        CYBER_PURPLE("赛博紫", 0xFFA855F7, 0xFF22D3EE),
        CRIMSON("绯红赤", 0xFFEF4444, 0xFFFACC15),
        ROYAL_GOLD("鎏金耀", 0xFFFACC15, 0xFFF97316),
        EMERALD("翡翠绿", 0xFF34D399, 0xFF38BDF8),
        SAKURA("樱花粉", 0xFFF472B6, 0xFFC084FC),
        PURE_WHITE("纯净白", 0xFFF8FAFC, 0xFF94A3B8);

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
            "mode", "色彩模式", "精选色板 / 自定义RGB / 流光彩虹 / 双色呼吸", ColorMode.PRESET
    ));

    public final EnumSetting<PresetPalette> preset = addSetting(new EnumSetting<>(
            "preset", "色板选择", "快速切换高颜值全局主副配色", PresetPalette.ICE_CYAN
    ).visible(() -> mode.get() == ColorMode.PRESET || mode.get() == ColorMode.BREATHING));

    public final DoubleSetting red = addSetting(new DoubleSetting(
            "red", "红 (R)", "自定义红色通道 (0~255)", 56.0, 0.0, 255.0, 1.0
    ).visible(() -> mode.get() == ColorMode.CUSTOM_RGB || mode.get() == ColorMode.BREATHING));

    public final DoubleSetting green = addSetting(new DoubleSetting(
            "green", "绿 (G)", "自定义绿色通道 (0~255)", 189.0, 0.0, 255.0, 1.0
    ).visible(() -> mode.get() == ColorMode.CUSTOM_RGB || mode.get() == ColorMode.BREATHING));

    public final DoubleSetting blue = addSetting(new DoubleSetting(
            "blue", "蓝 (B)", "自定义蓝色通道 (0~255)", 248.0, 0.0, 255.0, 1.0
    ).visible(() -> mode.get() == ColorMode.CUSTOM_RGB || mode.get() == ColorMode.BREATHING));

    public final BoolSetting useCustomBreathingColors = addSetting(new BoolSetting(
            "useCustomBreathing", "自定义呼吸副色", "开启后使用下方副色RGB与上方主色RGB交替呼吸", false
    ).visible(() -> mode.get() == ColorMode.BREATHING));

    public final DoubleSetting red2 = addSetting(new DoubleSetting(
            "red2", "副色红 (R2)", "呼吸渐变第二颜色 R", 168.0, 0.0, 255.0, 1.0
    ).visible(() -> mode.get() == ColorMode.BREATHING && useCustomBreathingColors.get()));

    public final DoubleSetting green2 = addSetting(new DoubleSetting(
            "green2", "副色绿 (G2)", "呼吸渐变第二颜色 G", 85.0, 0.0, 255.0, 1.0
    ).visible(() -> mode.get() == ColorMode.BREATHING && useCustomBreathingColors.get()));

    public final DoubleSetting blue2 = addSetting(new DoubleSetting(
            "blue2", "副色蓝 (B2)", "呼吸渐变第二颜色 B", 247.0, 0.0, 255.0, 1.0
    ).visible(() -> mode.get() == ColorMode.BREATHING && useCustomBreathingColors.get()));

    public final DoubleSetting cycleSpeed = addSetting(new DoubleSetting(
            "cycleSpeed", "流光速度", "彩虹流转或呼吸渐变速率", 1.0, 0.2, 4.0, 0.1
    ).visible(() -> mode.get() == ColorMode.RAINBOW || mode.get() == ColorMode.BREATHING));

    public final DoubleSetting saturation = addSetting(new DoubleSetting(
            "saturation", "饱和度", "流光彩虹色彩浓郁度", 0.78, 0.25, 1.0, 0.02
    ).visible(() -> mode.get() == ColorMode.RAINBOW));

    public final BoolSetting phaseWave = addSetting(new BoolSetting(
            "phaseWave", "追光相位差", "不同模块/魂环层之间产生微小的流光追逐渐变", true
    ).visible(() -> mode.get() == ColorMode.RAINBOW || mode.get() == ColorMode.BREATHING));

    public final BoolSetting syncUiAccent = addSetting(new BoolSetting(
            "syncUiAccent", "同步菜单主题色", "让 F9/F10 菜单按钮与高亮条也跟随全局色彩实时流转", false
    ));

    public GlobalColorModule() {
        super("global_color", "全局色彩", "Color Sync", "一键统管所有模块颜色，支持RGB/流光彩虹/双色呼吸与分模块同步开关", Category.VISUAL);
        setEnabled(true, true);

        addSetting(new ActionSetting("syncAllOn", "一键开启全部同步", "将所有支持调色的模块设为「同步全局色彩」", () -> {
            setEnabled(true);
            int count = setAllModuleSyncFlags(true);
            YeQuickMenu.showToast(ConfigManager.tr("✓ 已同步 " + count + " 个模块至全局色彩", "Synced " + count + " modules"), getCurrentArgb(0));
        }));

        addSetting(new ActionSetting("syncAllOff", "一键关闭全部同步", "恢复各模块各自独立的配色设置", () -> {
            int count = setAllModuleSyncFlags(false);
            YeQuickMenu.showToast(ConfigManager.tr("已恢复 " + count + " 个模块独立配色", "Restored independent colors"), 0xFF94A3B8);
        }));
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
            case CUSTOM_RGB -> {
                int r = clampByte(red.get());
                int g = clampByte(green.get());
                int b = clampByte(blue.get());
                yield (r << 16) | (g << 8) | b;
            }
            case RAINBOW -> {
                double hue = (((now / 4500.0) * spd + phase) % 1.0 + 1.0) % 1.0;
                yield Color.HSBtoRGB((float) hue, saturation.get().floatValue(), 1.0f) & 0x00FFFFFF;
            }
            case BREATHING -> {
                int c1, c2;
                if (useCustomBreathingColors.get()) {
                    c1 = (clampByte(red.get()) << 16) | (clampByte(green.get()) << 8) | clampByte(blue.get());
                    c2 = (clampByte(red2.get()) << 16) | (clampByte(green2.get()) << 8) | clampByte(blue2.get());
                } else {
                    c1 = preset.get().argb & 0x00FFFFFF;
                    c2 = preset.get().secondArgb & 0x00FFFFFF;
                }
                double wave = 0.5 + 0.5 * Math.sin((now / 900.0) * spd + phase * Math.PI * 2.0);
                yield lerpRgb(c1, c2, (float) wave);
            }
        };
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

    private static int clampByte(double v) {
        return Math.max(0, Math.min(255, (int) Math.round(v)));
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
