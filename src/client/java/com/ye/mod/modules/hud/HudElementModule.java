package com.ye.mod.modules.hud;

import com.ye.mod.core.Category;
import com.ye.mod.core.ConfigManager;
import com.ye.mod.core.Module;
import com.ye.mod.core.ModuleManager;
import com.ye.mod.core.setting.ActionSetting;
import com.ye.mod.core.setting.BoolSetting;
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
 * Base class for each independent, right-clickable HUD Sub-Module in the dedicated "HUD" tab!
 * Every HUD widget has its own scale, palette, global color sync, rounded card backdrop, opacity,
 * corner radius, rounded outline, text shadow, and position reset — rendered with 1:1 physical-pixel YeHdFont!
 */
public abstract class HudElementModule extends Module {
    public enum ColorStyle {
        THEME("主题色", 0xFF0A84FF),
        RUSHER_PINK("紫粉", 0xFFE879F9),
        ICE_CYAN("冰晶青", 0xFF38BDF8),
        ROYAL_GOLD("鎏金", 0xFFFACC15),
        EMERALD("翡翠绿", 0xFF4ADE80),
        CRIMSON("绯红", 0xFFF87171),
        PURE_WHITE("纯净白", 0xFFF8FAFC);

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
    public final EnumSetting<ColorStyle> colorStyle;
    public final BoolSetting syncGlobalColor;
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
                "scale", "组件大小", "无损矢量缩放该 HUD 组件大小", 1.0, 0.65, 1.80, 0.05
        ));
        this.colorStyle = addSetting(new EnumSetting<>(
                "colorStyle", "独立配色", "单独指定该 HUD 的文字与高亮色", defaultColor
        ));
        this.syncGlobalColor = addSetting(new BoolSetting(
                "syncGlobalColor", "同步全局色彩", "跟随「全局色彩」模块的流光或自定义色", false
        ));
        this.showBackdrop = addSetting(new BoolSetting(
                "showBackdrop", "显示底板", "绘制平滑圆角半透明深色背景底板", defaultBackdrop
        ));
        this.backdropAlpha = addSetting(new DoubleSetting(
                "backdropAlpha", "底板不透明度", "调节圆角底板浓淡", 0.62, 0.15, 0.95, 0.05
        ).visible(showBackdrop::get));
        this.cornerRadius = addSetting(new DoubleSetting(
                "cornerRadius", "底板圆角", "调节圆角弧度大小", 5.0, 0.0, 12.0, 1.0
        ).visible(showBackdrop::get));
        this.showBorder = addSetting(new BoolSetting(
                "showBorder", "圆角细描边", "绘制1px平滑抗锯齿圆角高光边框（绝无直角框溢出）", true
        ).visible(showBackdrop::get));
        this.textShadow = addSetting(new BoolSetting(
                "textShadow", "文字阴影", "增强亮色天空或地面背景下的字迹清晰度", true
        ));
    }

    protected void addResetPositionButton() {
        addSetting(new ActionSetting("resetSlotPos", "重置屏幕位置", "将该 HUD 恢复到初始默认吸附位置", () -> {
            ConfigManager.resetSingleHudSlot(slotId);
            ConfigManager.saveAsync();
            YeQuickMenu.showToast(ConfigManager.tr("已重置「" + getName() + "」位置", "Reset position"), ConfigManager.getAccentColor());
        }));
    }

    public String getSlotId() {
        return slotId;
    }

    public ConfigManager.HudSlot getSlot() {
        ConfigManager.HudSlot s = ConfigManager.HUD_SLOTS.get(slotId);
        if (s != null) {
            s.enabled = isEnabled();
        }
        return s;
    }

    public int resolvePrimaryColor() {
        if (syncGlobalColor.get()) {
            return GlobalColorModule.resolveArgb(true, 0xFF38BDF8, phaseIdx);
        }
        ColorStyle cs = colorStyle.get();
        return cs == ColorStyle.THEME ? ConfigManager.getAccentColor() : cs.argb;
    }

    protected void drawHudCard(GuiGraphics g, int x, int y, int w, int h, boolean editMode) {
        if (showBackdrop.get()) {
            int alpha = Math.max(20, Math.min(248, Math.round(backdropAlpha.get().floatValue() * 255.0f)));
            int bg = AppleUiRenderer.lightTheme
                    ? ((alpha << 24) | 0x00F1F5F9)
                    : ((alpha << 24) | 0x000B0F19);
            float rad = cornerRadius.get().floatValue();
            AppleUiRenderer.drawRoundedRect(g, x, y, w, h, rad, bg);
            if (showBorder.get()) {
                int borderCol = AppleUiRenderer.lightTheme ? 0x260F172A : 0x28FFFFFF;
                AppleUiRenderer.drawRoundedOutline(g, x, y, w, h, rad, 1.0f, borderCol);
            }
        }
        if (editMode) {
            AppleUiRenderer.drawRoundedRect(g, x, y, w, h, 4.0f, 0x2838BDF8);
            AppleUiRenderer.drawRoundedOutline(g, x, y, w, h, 4.0f, 1.2f, 0xCC38BDF8);
        }
    }

    protected void drawHudText(GuiGraphics g, String text, float x, float y, float fontSize, int color, boolean bold) {
        YeHdFont.draw(g, text, x, y, fontSize, color, textShadow.get(), bold);
    }

    public abstract void renderElement(GuiGraphics g, int sw, int sh, boolean editMode);

    // ==================== GLOBAL DISPATCH HELPERS ====================

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
