package com.ye.mod.modules.hud;

import com.ye.mod.core.Category;
import com.ye.mod.core.ConfigManager;
import com.ye.mod.core.Module;
import com.ye.mod.core.ModuleManager;
import com.ye.mod.core.setting.ActionSetting;
import com.ye.mod.core.setting.BoolSetting;
import com.ye.mod.core.setting.ColorSetting;
import com.ye.mod.core.setting.DoubleSetting;
import com.ye.mod.core.setting.EnumSetting;
import com.ye.mod.modules.visual.GlobalColorModule;
import com.ye.mod.ui.AppleUiRenderer;
import com.ye.mod.ui.YeHdFont;
import com.ye.mod.ui.YeQuickMenu;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;

/**
 * Base class for each independent HUD Sub-Module in the dedicated "HUD" tab!
 * Supports custom HSV color picker, Synchronized Color (with spatial flowing Rainbow Gradient!),
 * 0px edge-flush placement, and 9-point magnetic snapping!
 */
public abstract class HudElementModule extends Module {
    public enum ColorStyle {
        SYNC("跟随同步色", 0xFF38BDF8),
        CUSTOM("自由调色", 0xFF38BDF8),
        THEME("跟随UI色", 0xFF0A84FF),
        RUSHER_PINK("紫粉", 0xFFE879F9),
        ICE_CYAN("冰晶青", 0xFF38BDF8),
        ROYAL_GOLD("鎏金", 0xFFFACC15),
        EMERALD("翡翠绿", 0xFF4ADE80),
        CRIMSON("绯红", 0xFFF87171),
        PURE_WHITE("纯白", 0xFFF8FAFC);

        public final String label;
        public final int argb;

        ColorStyle(String label, int argb) {
            this.label = label;
            this.argb = argb;
        }
    }

    private final String slotId;
    private final int phaseIdx;

    public final DoubleSetting scale;
    public final BoolSetting syncGlobalColor;
    public final EnumSetting<ColorStyle> colorStyle;
    public final ColorSetting customColor;
    public final BoolSetting showBackdrop;
    public final DoubleSetting backdropAlpha;
    public final DoubleSetting cornerRadius;
    public final BoolSetting showBorder;
    public final BoolSetting textShadow;

    protected HudElementModule(String id, String slotId, String name, String subtitle, String description,
                               boolean defaultOn, boolean defaultBackdrop, ColorStyle defaultColor, int phaseIdx) {
        super(id, name, subtitle, description, Category.HUD);
        this.slotId = slotId;
        this.phaseIdx = phaseIdx;
        if (defaultOn) {
            setEnabled(true, true);
        }

        this.scale = addSetting(new DoubleSetting(
                "scale", "组件缩放", "缩放该 HUD 组件尺寸", 1.0, 0.55, 2.20, 0.05
        ).displayGroup());
        this.syncGlobalColor = addSetting(new BoolSetting(
                "syncGlobalColor", "跟随同步色", "跟随同步色模块的彩虹渐变或全局自选色", false
        ).displayGroup());
        this.colorStyle = addSetting(new EnumSetting<>(
                "colorStyle", "配色方案", "选择预设色或自由调色", defaultColor
        ).visible(() -> !syncGlobalColor.get()).displayGroup());
        this.customColor = addSetting(new ColorSetting(
                "customColor", "自定义颜色", "拖动调色面板设置专属颜色", defaultColor.argb
        ).visible(() -> !syncGlobalColor.get() && colorStyle.get() == ColorStyle.CUSTOM));
        this.showBackdrop = addSetting(new BoolSetting(
                "showBackdrop", "显示底板", "绘制半透明背景卡片", defaultBackdrop
        ).displayGroup());
        this.backdropAlpha = addSetting(new DoubleSetting(
                "backdropAlpha", "底板透明度", "调节底板浓淡程度", 0.62, 0.10, 0.96, 0.05
        ).visible(showBackdrop::get).displayGroup());
        this.cornerRadius = addSetting(new DoubleSetting(
                "cornerRadius", "底板圆角", "调节圆角大小（0 为直角）", 5.0, 0.0, 12.0, 1.0
        ).visible(showBackdrop::get).displayGroup());
        this.showBorder = addSetting(new BoolSetting(
                "showBorder", "显示描边", "绘制外轮廓细边框", true
        ).visible(showBackdrop::get).displayGroup());
        this.textShadow = addSetting(new BoolSetting(
                "textShadow", "文字阴影", "提升亮色背景下的字迹对比度", true
        ).displayGroup());
    }

    protected void addResetPositionButton() {
        addSetting(new ActionSetting("resetSlotPos", "重置屏幕位置", "恢复初始默认吸附位置", () ->
                YeQuickMenu.requestConfirmation(
                        ConfigManager.tr("确定重置「" + getName() + "」的屏幕位置吗？", "Reset HUD position?"),
                        () -> {
                            ConfigManager.resetSingleHudSlot(slotId);
                            ConfigManager.saveAsync();
                            YeQuickMenu.showToast(ConfigManager.tr("已重置位置", "Reset position"), ConfigManager.getAccentColor());
                        })
        ).displayGroup());
    }

    public String getSlotId() { return slotId; }

    public ConfigManager.HudSlot getSlot() {
        ConfigManager.HudSlot s = ConfigManager.HUD_SLOTS.get(slotId);
        if (s != null) s.enabled = isEnabled();
        return s;
    }

    public boolean isUsingRainbowSync() {
        if (!syncGlobalColor.get() && colorStyle.get() != ColorStyle.SYNC) return false;
        GlobalColorModule gc = ModuleManager.get(GlobalColorModule.class);
        return gc != null && gc.isEnabled() && gc.mode.get() == GlobalColorModule.ColorMode.RAINBOW;
    }

    public int resolvePrimaryColor() {
        if (syncGlobalColor.get() || colorStyle.get() == ColorStyle.SYNC) {
            return GlobalColorModule.resolveArgb(true, 0xFF38BDF8, phaseIdx);
        }
        ColorStyle cs = colorStyle.get();
        if (cs == ColorStyle.CUSTOM) return customColor.getArgb();
        return cs == ColorStyle.THEME ? ConfigManager.getAccentColor() : cs.argb;
    }

    protected void drawHudCard(GuiGraphics g, int x, int y, int w, int h, boolean editMode) {
        boolean mech = ConfigManager.uiStyle == ConfigManager.UiStyle.MECHANICAL;
        if (showBackdrop.get()) {
            int alpha = Math.max(15, Math.min(248, Math.round(backdropAlpha.get().floatValue() * 255.0f)));
            int bg = AppleUiRenderer.lightTheme
                    ? ((alpha << 24) | 0x00F1F5F9)
                    : ((alpha << 24) | 0x000B0F19);
            float rad = mech ? 0.0f : cornerRadius.get().floatValue();
            AppleUiRenderer.drawRoundedRect(g, x, y, w, h, rad, bg);
            if (showBorder.get()) {
                int borderCol = isUsingRainbowSync()
                        ? AppleUiRenderer.withAlpha(resolvePrimaryColor(), 0.55f)
                        : (AppleUiRenderer.lightTheme ? 0x260F172A : 0x28FFFFFF);
                AppleUiRenderer.drawRoundedOutline(g, x, y, w, h, rad, 1.0f, borderCol);
            }
        }
        if (editMode) {
            AppleUiRenderer.drawRoundedRect(g, x, y, w, h, mech ? 0f : 3.5f, 0x2438BDF8);
            AppleUiRenderer.drawRoundedOutline(g, x, y, w, h, mech ? 0f : 3.5f, 1.2f, 0xCC38BDF8);
        }
    }

    protected void drawHudText(GuiGraphics g, String text, float x, float y, float fontSize, int color, boolean bold) {
        if (isUsingRainbowSync() && text != null && text.indexOf('§') < 0) {
            YeHdFont.drawRainbow(g, text, x, y, fontSize, textShadow.get(), bold);
        } else {
            YeHdFont.draw(g, text, x, y, fontSize, color, textShadow.get(), bold);
        }
    }

    public abstract void renderElement(GuiGraphics g, int sw, int sh, boolean editMode);

    public static List<HudElementModule> getAllHudModules() {
        List<HudElementModule> list = new ArrayList<>();
        for (Module m : ModuleManager.getByCategory(Category.HUD)) {
            if (m instanceof HudElementModule hm) list.add(hm);
        }
        return list;
    }

    public static HudElementModule findBySlotId(String slotId) {
        for (HudElementModule hm : getAllHudModules()) {
            if (hm.getSlotId().equals(slotId)) return hm;
        }
        return null;
    }

    public static void renderAllActive(GuiGraphics g, boolean editMode) {
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null || mc.level == null) return;
        var idle = ModuleManager.get(com.ye.mod.modules.visual.IdleWallpaperModule.class);
        if (!editMode && idle != null && idle.shouldHideHud()) return;

        int sw = g.guiWidth();
        int sh = g.guiHeight();
        for (HudElementModule hm : getAllHudModules()) {
            ConfigManager.HudSlot s = hm.getSlot();
            if (s != null) s.enabled = hm.isEnabled();
            if (hm.isEnabled()) {
                hm.renderElement(g, sw, sh, editMode);
            }
        }
    }
}
