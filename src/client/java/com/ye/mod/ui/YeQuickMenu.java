package com.ye.mod.ui;

import com.ye.mod.core.Category;
import com.ye.mod.core.ConfigManager;
import com.ye.mod.core.Module;
import com.ye.mod.core.ModuleManager;
import com.ye.mod.core.YeSoundPresets;
import com.ye.mod.core.setting.ActionSetting;
import com.ye.mod.core.setting.BoolSetting;
import com.ye.mod.core.setting.DoubleSetting;
import com.ye.mod.core.setting.EnumSetting;
import com.ye.mod.core.setting.Setting;
import com.ye.mod.modules.hud.HudElementModule;
import com.ye.mod.modules.render.AccessoriesModule;
import com.ye.mod.modules.render.CapeManager;
import com.ye.mod.modules.render.CustomCapeModule;
import com.ye.mod.modules.render.CustomSkinModule;
import com.ye.mod.modules.render.SkinManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Desktop-Grade 1:1 Vector UI (YeQuickMenu):
 * - 3 Clean Top-Level Plates: 功能 (Unified Vertical Scroll) / HUD (11 Modular Widgets) / 设置 (Config & Sound & Theme)
 * - ZERO corner square artifacts, ZERO bottom white lines, ZERO top-right module dots!
 * - Automatic 2-Column Balanced Inspector Table for modules with 4+ settings!
 * - Right-clicking any HUD element on screen opens its dedicated settings page immediately!
 */
public final class YeQuickMenu {
    public enum TopSection {
        FEATURES("功能", "Features"),
        HUD("HUD", "HUD"),
        SETTINGS("设置", "Settings");

        final String zh;
        final String en;

        TopSection(String zh, String en) {
            this.zh = zh;
            this.en = en;
        }

        String label() {
            return ConfigManager.tr(zh, en);
        }
    }

    private static final Category[] FEATURE_CATEGORIES = {
            Category.PLAYER_MODEL, Category.VISUAL, Category.UTILITY
    };

    public static final int WIN_W = 424;
    public static final int WIN_H = 304;
    private static final int EDGE_MARGIN = 16;
    private static final int GRID_COLS = 4;

    private static boolean open = false;
    private static float openAnim = 0.0f;
    private static float pageSlide = 0.0f;
    private static float tabIndicatorX = -1.0f;
    private static float tabIndicatorW = 54.0f;

    public static int listeningKeySlot = 0;
    public static Module listeningModuleKey = null;

    public static int profileInputMode = 0;
    public static String profileInputBuffer = "";

    private static String toastText = "";
    private static int toastColor = 0xFF22C55E;
    private static long toastUntilMs = 0L;

    private static TopSection activeSection = TopSection.FEATURES;
    private static Module activeModule = null;
    private static int selectedIndex = 0;

    private static double scrollOffset = 0.0;
    private static double targetScroll = 0.0;
    private static double maxScroll = 0.0;

    private static EnumSetting<?> openDropdown = null;
    private static int dropX, dropY, dropW;
    private static double dropScroll = 0.0;

    private static boolean soundDropdownOpen = false;
    private static int soundDropX, soundDropY, soundDropW;

    private static final Map<Class<?>, Field> ENUM_LABEL_CACHE = new HashMap<>();
    private static final List<HitRect> HIT_RECTS = new ArrayList<>();
    public record HitRect(int x, int y, int w, int h, Runnable leftClick, Runnable rightClick, DoubleSetting slider) {}

    private YeQuickMenu() {}

    public static boolean isOpen() { return open; }

    public static void showToast(String msg, int color) {
        toastText = msg;
        toastColor = color;
        toastUntilMs = System.currentTimeMillis() + 2400L;
    }

    public static void toggleOpen() {
        open = !open;
        playClick();
        if (!open) {
            closeOverlays();
            ConfigManager.saveAsync();
        }
    }

    public static void setOpen(boolean v) {
        open = v;
        if (!open) closeOverlays();
    }

    private static void closeOverlays() {
        openDropdown = null;
        soundDropdownOpen = false;
        listeningKeySlot = 0;
        listeningModuleKey = null;
        profileInputMode = 0;
    }

    /**
     * Jumps directly to a specific HUD element's settings page (invoked on right-clicking HUD on screen!).
     */
    public static void openHudElementSettings(HudElementModule hm) {
        if (hm == null) return;
        open = true;
        openAnim = 1.0f;
        activeSection = TopSection.HUD;
        activeModule = hm;
        closeOverlays();
        selectedIndex = 0;
        scrollOffset = 0.0;
        targetScroll = 0.0;
        playClick();
    }

    public static void playClick() {
        YeSoundPresets.playUi(ConfigManager.uiClickSound, ConfigManager.uiSoundVolume);
    }

    public static int getWinX(int sw) {
        if (ConfigManager.widgetX >= 0) {
            return Math.max(8, Math.min(sw - WIN_W - 8, ConfigManager.widgetX));
        }
        return sw - WIN_W - EDGE_MARGIN;
    }

    public static int getWinY(int sh) {
        if (ConfigManager.widgetY >= 0) {
            return Math.max(8, Math.min(sh - WIN_H - 8, ConfigManager.widgetY));
        }
        return sh - WIN_H - EDGE_MARGIN;
    }

    public static boolean isPointInsideMenuOrDropdown(double mx, double my, int sw, int sh) {
        int wx = getWinX(sw);
        int wy = getWinY(sh);
        if (inBounds(mx, my, wx, wy, WIN_W, WIN_H)) return true;
        if (openDropdown != null && inBounds(mx, my, dropX, Math.max(4, dropY - 200), dropW, 400)) return true;
        if (soundDropdownOpen && inBounds(mx, my, soundDropX, Math.max(4, soundDropY - 200), soundDropW, 400)) return true;
        return false;
    }

    public static void render(GuiGraphics g, int mouseX, int mouseY, boolean mouseUnlocked) {
        AppleUiRenderer.beginFrame();
        float dt = AppleUiRenderer.dt();
        openAnim = AppleUiRenderer.smooth(openAnim, open ? 1.0f : 0.0f, 18.0f, dt);
        pageSlide = AppleUiRenderer.smooth(pageSlide, 0.0f, 20.0f, dt);

        if (targetScroll > maxScroll) targetScroll = maxScroll;
        if (targetScroll < 0.0) targetScroll = 0.0;
        scrollOffset = AppleUiRenderer.smooth((float) scrollOffset, (float) targetScroll, 22.0f, dt);
        if (scrollOffset > maxScroll) scrollOffset = maxScroll;
        if (scrollOffset < 0.0) scrollOffset = 0.0;

        if (!open && openAnim < 0.02f) return;

        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();

        int slideY = Math.round((1.0f - openAnim) * 18.0f);
        int wx = getWinX(sw);
        int wy = getWinY(sh) + slideY;
        int accent = ConfigManager.getAccentColor();

        HIT_RECTS.clear();

        // 1. Seam-free 1:1 Vector Window Shell + Header Bar (zero sharp square corner boxes!)
        AppleUiRenderer.drawElevatedWindow(g, wx, wy, WIN_W, WIN_H, 11);
        AppleUiRenderer.drawRoundedRect(g, wx + 2, wy + 2, WIN_W - 4, 28, 9, AppleUiRenderer.bgHeader());
        g.fill(wx + 12, wy + 30, wx + WIN_W - 12, wy + 31, AppleUiRenderer.borderSubtle());

        // Crisp Brand Mark
        AppleUiRenderer.drawRoundedRect(g, wx + 11, wy + 8, 3.5f, 15, 1.8f, accent);
        AppleUiRenderer.drawBoldText(g, "Ye", wx + 19, wy + 10, 10.2f, AppleUiRenderer.textPrimary());

        // 3 Clean Top Tabs: 功能 / HUD / 设置
        int tx = wx + 46;
        TopSection[] secs = TopSection.values();
        int targetTabX = tx;
        int targetTabW = 54;
        int[] tabXs = new int[secs.length];
        int[] tabWs = new int[secs.length];

        for (int i = 0; i < secs.length; i++) {
            String lbl = secs[i].label();
            int tw = Math.max(48, YeHdFont.width(lbl, 9.0f, true) + 20);
            tabXs[i] = tx;
            tabWs[i] = tw;
            if (secs[i] == activeSection) {
                targetTabX = tx;
                targetTabW = tw;
            }
            tx += tw + 6;
        }

        if (tabIndicatorX < 0) {
            tabIndicatorX = targetTabX;
            tabIndicatorW = targetTabW;
        } else {
            tabIndicatorX = AppleUiRenderer.smooth(tabIndicatorX, targetTabX, 22.0f, dt);
            tabIndicatorW = AppleUiRenderer.smooth(tabIndicatorW, targetTabW, 22.0f, dt);
        }
        AppleUiRenderer.drawRoundedRect(g, tabIndicatorX, wy + 6, tabIndicatorW, 20, 6.5f, accent);

        for (int i = 0; i < secs.length; i++) {
            TopSection sec = secs[i];
            int bx = tabXs[i], bw = tabWs[i];
            boolean sel = (sec == activeSection);
            boolean hov = mouseUnlocked && inBounds(mouseX, mouseY, bx, wy + 6, bw, 20);
            if (!sel && hov) {
                AppleUiRenderer.drawRoundedRect(g, bx, wy + 6, bw, 20, 6.5f, AppleUiRenderer.bgCardHover());
            }
            AppleUiRenderer.drawCenteredSmooth(g, sec.label(), bx + bw * 0.5f, wy + 10.5f, 9.0f,
                    sel ? AppleUiRenderer.TEXT_PRIMARY : (hov ? AppleUiRenderer.textPrimary() : AppleUiRenderer.textSecondary()), sel);
            HIT_RECTS.add(new HitRect(bx, wy + 6, bw, 20, () -> switchSection(sec), null, null));
        }

        String hint = mouseUnlocked
                ? (ConfigManager.tr("档案: ", "Profile: ") + ConfigManager.getActiveProfile())
                : (keyName(ConfigManager.getMouseUnlockKey()) + ConfigManager.tr(" 鼠标 · 空格详情", " Mouse · Space"));
        int hintW = YeHdFont.width(hint, 8.2f, false);
        if (tx + hintW + 14 < wx + WIN_W) {
            YeHdFont.draw(g, hint, wx + WIN_W - 12 - hintW, wy + 11, 8.2f, AppleUiRenderer.textTertiary(), false, false);
        }

        int bodyX = wx + 11 + Math.round(pageSlide);
        int bodyY = wy + 36;
        int bodyW = WIN_W - 22;
        int bodyH = WIN_H - 44;

        g.enableScissor(wx + 6, bodyY - 2, wx + WIN_W - 6, wy + WIN_H - 6);
        if (activeModule != null && activeSection != TopSection.SETTINGS) {
            renderModuleDetail(g, font, bodyX, bodyY, bodyW, bodyH, mouseX, mouseY, mouseUnlocked, accent);
        } else if (activeSection == TopSection.FEATURES) {
            renderUnifiedFeaturesPage(g, bodyX, bodyY, bodyW, bodyH, mouseX, mouseY, mouseUnlocked, accent);
        } else if (activeSection == TopSection.HUD) {
            renderHudDashboardPage(g, bodyX, bodyY, bodyW, bodyH, mouseX, mouseY, mouseUnlocked, accent);
        } else {
            renderSettingsTab(g, bodyX, bodyY, bodyW, bodyH, mouseX, mouseY, mouseUnlocked, accent);
        }
        g.disableScissor();

        if (openDropdown != null && mouseUnlocked) {
            renderDropdown(g, openDropdown, mouseX, mouseY, accent);
        }
        if (soundDropdownOpen && mouseUnlocked) {
            renderSoundDropdown(g, mouseX, mouseY, accent);
        }

        // Crisp Toast Notification Pill
        long rem = toastUntilMs - System.currentTimeMillis();
        if (rem > 0 && !toastText.isEmpty()) {
            float alpha = Math.min(1.0f, rem / 300.0f);
            int tw = YeHdFont.width(toastText, 8.6f, true) + 24;
            int txBox = wx + (WIN_W - tw) / 2;
            int tyBox = Math.max(4, wy - 24);
            AppleUiRenderer.drawRoundedRect(g, txBox, tyBox, tw, 20, 7, AppleUiRenderer.withAlpha(0xF20D121D, alpha));
            AppleUiRenderer.drawRoundedOutline(g, txBox, tyBox, tw, 20, 7, 1.2f, AppleUiRenderer.withAlpha(toastColor, alpha));
            YeHdFont.drawCentered(g, toastText, txBox + tw * 0.5f, tyBox + 5.0f, 8.6f,
                    AppleUiRenderer.withAlpha(toastColor, alpha), false, true);
        }
    }

    private static void switchSection(TopSection sec) {
        if (activeSection == sec && activeModule == null) return;
        playClick();
        pageSlide = (sec.ordinal() >= activeSection.ordinal()) ? 16.0f : -16.0f;
        activeSection = sec;
        activeModule = null;
        closeOverlays();
        selectedIndex = 0;
        scrollOffset = 0.0;
        targetScroll = 0.0;
    }

    private static List<Module> getAllFeatureModulesOrdered() {
        List<Module> all = new ArrayList<>();
        for (Category cat : FEATURE_CATEGORIES) {
            all.addAll(ModuleManager.getByCategory(cat));
        }
        return all;
    }

    /**
     * Unified Single-Page Vertical Scroll for ALL Feature Categories (玩家模型 / 视觉渲染 / 特定功能)!
     * Zero top-right dots, silky smooth vertical scrolling, razor-sharp typography!
     */
    private static void renderUnifiedFeaturesPage(GuiGraphics g, int x, int y, int w, int h,
                                                  int mx, int my, boolean mouseUnlocked, int accent) {
        List<Module> flatList = getAllFeatureModulesOrdered();
        if (selectedIndex >= flatList.size()) selectedIndex = Math.max(0, flatList.size() - 1);

        int gap = 6;
        int cellW = (w - 6 - (GRID_COLS - 1) * gap) / GRID_COLS;
        int cellH = 38;
        int rowStep = cellH + gap;

        int startRy = y - (int) Math.round(scrollOffset);
        int ry = startRy;
        int globalIdx = 0;

        for (Category cat : FEATURE_CATEGORIES) {
            List<Module> mods = ModuleManager.getByCategory(cat);
            if (mods.isEmpty()) continue;

            // Section Header Divider
            if (ry + 18 >= y - 4 && ry <= y + h + 4) {
                AppleUiRenderer.drawRoundedRect(g, x + 2, ry + 3, 3, 10, 1.5f, accent);
                AppleUiRenderer.drawBoldText(g, cat.label(), x + 9, ry + 2.5f, 8.8f, AppleUiRenderer.textSecondary());
                int titleW = YeHdFont.width(cat.label(), 8.8f, true);
                g.fill(x + 14 + titleW, ry + 8, x + w - 8, ry + 9, AppleUiRenderer.borderSubtle());
            }
            ry += 18;

            for (int i = 0; i < mods.size(); i++) {
                Module m = mods.get(i);
                int col = i % GRID_COLS;
                int row = i / GRID_COLS;
                int cx = x + col * (cellW + gap);
                int rawCy = ry + row * rowStep;
                boolean keySel = !mouseUnlocked && (globalIdx == selectedIndex);
                renderModuleCardCell(g, m, cx, rawCy, cellW, cellH, y, h, mx, my, mouseUnlocked, keySel, accent);
                globalIdx++;
            }

            int rows = (mods.size() + GRID_COLS - 1) / GRID_COLS;
            ry += rows * rowStep + 6;
        }

        int contentH = Math.max(0, (ry - startRy) - 4);
        maxScroll = Math.max(0.0, contentH - h);
        drawScrollbar(g, x + w - 2, y, h, contentH, accent);
    }

    /**
     * Dedicated HUD Dashboard Tab:
     * Every HUD component is its own card (left-click toggles visibility, right-click opens its rich settings!).
     */
    private static void renderHudDashboardPage(GuiGraphics g, int x, int y, int w, int h,
                                               int mx, int my, boolean mouseUnlocked, int accent) {
        List<Module> hudMods = ModuleManager.getByCategory(Category.HUD);
        if (selectedIndex >= hudMods.size()) selectedIndex = Math.max(0, hudMods.size() - 1);

        int startRy = y - (int) Math.round(scrollOffset);
        int ry = startRy;
        int rowW = w - 6;

        // Top HUD Helper Banner + Reset All Positions Button
        if (ry + 24 >= y - 4 && ry <= y + h + 4) {
            AppleUiRenderer.drawRoundedRect(g, x, ry, rowW, 22, 6, AppleUiRenderer.bgCard());
            AppleUiRenderer.drawRoundedOutline(g, x, ry, rowW, 22, 6, 1.0f, AppleUiRenderer.borderSubtle());
            String tip = ConfigManager.tr("提示：按住左键拖拽屏幕HUD吸附 · 右键屏幕HUD或卡片直达专属设置",
                    "Tip: Drag HUD on screen to snap · Right-click any HUD to configure");
            YeHdFont.draw(g, YeHdFont.clipToWidth(tip, 8.2f, false, rowW - 88), x + 8, ry + 6, 8.2f, AppleUiRenderer.textSecondary(), false, false);

            int btnW = 76;
            int btnX = x + rowW - btnW - 4;
            AppleUiRenderer.drawRoundedRect(g, btnX, ry + 3, btnW, 16, 4.5f, accent);
            AppleUiRenderer.drawCenteredSmooth(g, ConfigManager.tr("重置全部位置", "Reset All"), btnX + btnW * 0.5f, ry + 6, 8.0f, AppleUiRenderer.TEXT_PRIMARY, true);
            if (ry + 22 > y && ry < y + h) {
                HIT_RECTS.add(new HitRect(btnX, ry + 3, btnW, 16, () -> {
                    playClick();
                    ConfigManager.resetHudSlotsDefault();
                    ConfigManager.saveAsync();
                    showToast(ConfigManager.tr("已恢复全部 HUD 默认布局", "Reset all HUD positions"), accent);
                }, null, null));
            }
        }
        ry += 28;

        int gap = 6;
        int cellW = (w - 6 - (GRID_COLS - 1) * gap) / GRID_COLS;
        int cellH = 40;
        int rowStep = cellH + gap;

        for (int i = 0; i < hudMods.size(); i++) {
            Module m = hudMods.get(i);
            int col = i % GRID_COLS;
            int row = i / GRID_COLS;
            int cx = x + col * (cellW + gap);
            int rawCy = ry + row * rowStep;
            boolean keySel = !mouseUnlocked && (i == selectedIndex);
            renderModuleCardCell(g, m, cx, rawCy, cellW, cellH, y, h, mx, my, mouseUnlocked, keySel, accent);
        }

        int rows = (hudMods.size() + GRID_COLS - 1) / GRID_COLS;
        ry += rows * rowStep + 4;

        int contentH = Math.max(0, ry - startRy);
        maxScroll = Math.max(0.0, contentH - h);
        drawScrollbar(g, x + w - 2, y, h, contentH, accent);
    }

    private static void renderModuleCardCell(GuiGraphics g, Module m, int cx, int rawCy, int cellW, int cellH,
                                             int clipY, int clipH, int mx, int my, boolean mouseUnlocked, boolean keySel, int accent) {
        boolean hovered = mouseUnlocked && openDropdown == null && inBounds(mx, my, cx, rawCy, cellW, cellH);
        float hovAnim = AppleUiRenderer.animChannel("mhov_" + m.getId(), (hovered || keySel) ? 1.0f : 0.0f, 18.0f);
        float actAnim = AppleUiRenderer.animChannel("mact_" + m.getId(), m.isEnabled() ? 1.0f : 0.0f, 16.0f);
        int cy = rawCy - Math.round(hovAnim * 1.2f);

        if (cy + cellH < clipY - 2 || cy > clipY + clipH + 2) return;

        AppleUiRenderer.drawElevatedCard(g, cx, cy, cellW, cellH, 7, actAnim, hovAnim, accent);
        if (keySel) {
            AppleUiRenderer.drawRoundedOutline(g, cx, cy, cellW, cellH, 7, 1.8f, 0xFFFFFFFF);
        }

        String rawName = ConfigManager.english ? m.getSubtitle() : m.getName();
        String name = YeHdFont.clipToWidth(rawName, 9.0f, true, cellW - 10);
        int txtCol = m.isEnabled() ? AppleUiRenderer.TEXT_PRIMARY : AppleUiRenderer.textPrimary();
        AppleUiRenderer.drawCenteredSmooth(g, name, cx + cellW * 0.5f, cy + 8.5f, 9.0f, txtCol, true);

        String sub = m.getKeybind() > 0 ? ("[" + keyName(m.getKeybind()) + "]")
                : (m.isEnabled() ? ConfigManager.tr("已开启", "Enabled") : ConfigManager.tr("已关闭", "Disabled"));
        int subCol = m.isEnabled() ? 0xD8FFFFFF : AppleUiRenderer.textTertiary();
        AppleUiRenderer.drawCenteredSmooth(g, sub, cx + cellW * 0.5f, cy + 22.5f, 7.8f, subCol, false);

        if (rawCy + cellH > clipY + 2 && rawCy < clipY + clipH - 2) {
            HIT_RECTS.add(new HitRect(cx, rawCy, cellW, cellH,
                    () -> {
                        playClick();
                        m.toggle();
                        ConfigManager.saveAsync();
                    },
                    () -> openModuleDetail(m),
                    null));
        }
    }

    private static void openModuleDetail(Module m) {
        playClick();
        activeModule = m;
        closeOverlays();
        pageSlide = 16.0f;
        selectedIndex = 0;
        scrollOffset = 0.0;
        targetScroll = 0.0;
    }

    /**
     * Module Detail Inspector:
     * - Real 3D / High-DPI Live Preview Card at top
     * - Automatic 2-Column Balanced Table Layout when a module has >= 4 settings!
     */
    private static void renderModuleDetail(GuiGraphics g, Font font, int x, int y, int w, int h,
                                           int mx, int my, boolean mouseUnlocked, int accent) {
        boolean backHov = mouseUnlocked && inBounds(mx, my, x, y, 32, 18);
        AppleUiRenderer.drawElevatedCard(g, x, y, 32, 18, 6, 0f, backHov ? 1f : 0f, accent);
        AppleUiRenderer.drawCenteredSmooth(g, "←", x + 16, y + 4.0f, 9.5f, AppleUiRenderer.textPrimary(), true);
        HIT_RECTS.add(new HitRect(x, y, 32, 18, () -> {
            playClick();
            activeModule = null;
            closeOverlays();
            pageSlide = -16.0f;
            selectedIndex = 0;
            scrollOffset = 0.0;
            targetScroll = 0.0;
        }, null, null));

        String mName = ConfigManager.english ? activeModule.getSubtitle() : activeModule.getName();
        AppleUiRenderer.drawBoldText(g, mName, x + 38, y + 4.5f, 9.6f, AppleUiRenderer.textPrimary());

        // Per-module Keybind Pill
        boolean listeningThis = (listeningModuleKey == activeModule);
        String kbText = listeningThis ? ConfigManager.tr("请按键...", "Press...")
                : (ConfigManager.tr("快捷键: ", "Key: ") + (activeModule.getKeybind() > 0 ? keyName(activeModule.getKeybind()) : ConfigManager.tr("无", "None")));
        int kbW = Math.max(48, YeHdFont.width(kbText, 8.2f, false) + 14);
        int kbX = x + w - 34 - kbW - 5;
        AppleUiRenderer.drawRoundedRect(g, kbX, y + 1, kbW, 16, 5, listeningThis ? 0xFFEF4444 : AppleUiRenderer.bgCardHover());
        AppleUiRenderer.drawCenteredSmooth(g, kbText, kbX + kbW * 0.5f, y + 4.2f, 8.2f,
                listeningThis ? AppleUiRenderer.TEXT_PRIMARY : AppleUiRenderer.textSecondary(), false);
        HIT_RECTS.add(new HitRect(kbX, y + 1, kbW, 16,
                () -> {
                    playClick();
                    listeningModuleKey = (listeningModuleKey == activeModule) ? null : activeModule;
                    listeningKeySlot = 0;
                },
                () -> {
                    playClick();
                    activeModule.setKeybind(-1);
                    listeningModuleKey = null;
                }, null));

        // Single-Module Reset Pill
        String rstTxt = ConfigManager.tr("重置", "Reset");
        int rstW = Math.max(36, YeHdFont.width(rstTxt, 8.2f, false) + 12);
        int rstX = kbX - rstW - 5;
        boolean rstHov = mouseUnlocked && inBounds(mx, my, rstX, y + 1, rstW, 16);
        AppleUiRenderer.drawRoundedRect(g, rstX, y + 1, rstW, 16, 5, rstHov ? 0xFFE11D48 : AppleUiRenderer.bgCardHover());
        AppleUiRenderer.drawCenteredSmooth(g, rstTxt, rstX + rstW * 0.5f, y + 4.2f, 8.2f,
                rstHov ? AppleUiRenderer.TEXT_PRIMARY : AppleUiRenderer.textSecondary(), false);
        HIT_RECTS.add(new HitRect(rstX, y + 1, rstW, 16, () -> {
            playClick();
            activeModule.resetToDefault();
            openDropdown = null;
            showToast(ConfigManager.tr("已将「" + mName + "」恢复默认", "Reset " + mName), 0xFF38BDF8);
        }, null, null));

        float mSwitch = AppleUiRenderer.animChannel("dsw_" + activeModule.getId(), activeModule.isEnabled() ? 1f : 0f, 18f);
        AppleUiRenderer.drawPillSwitch(g, x + w - 30, y + 2, 30, 15, mSwitch, accent);
        HIT_RECTS.add(new HitRect(x + w - 30, y + 2, 30, 15, () -> {
            playClick();
            activeModule.toggle();
            ConfigManager.saveAsync();
        }, null, null));

        int listY = y + 23;
        int listH = h - 23;
        int rowW = w - 6;

        List<Setting<?>> rawSettings = getDisplaySettings(activeModule);
        List<Setting<?>> settings = new ArrayList<>();
        for (Setting<?> s : rawSettings) {
            if ((activeModule instanceof CustomCapeModule && s.getId().equals("selectedCape"))
                    || (activeModule instanceof CustomSkinModule && s.getId().equals("selectedSkin"))) {
                continue;
            }
            settings.add(s);
        }
        if (selectedIndex >= settings.size()) selectedIndex = Math.max(0, settings.size() - 1);

        g.enableScissor(x - 1, listY, x + w + 1, listY + listH);
        int startRy = listY - (int) Math.round(scrollOffset);
        int ry = startRy;

        ry += YePreviewRenderer.renderModulePreviewCard(g, font, activeModule, x, ry, rowW);

        // Multi-column Cape & 73-Skin Gallery
        if (activeModule instanceof CustomCapeModule cm) {
            List<CapeManager.CapeEntry> capes = CapeManager.getAllCapes();
            int colW = (rowW - 5) / 2;
            for (int i = 0; i < capes.size(); i++) {
                CapeManager.CapeEntry ce = capes.get(i);
                int cx = x + (i % 2) * (colW + 5);
                int cy = ry + (i / 2) * 23;
                if (cy + 20 >= listY && cy <= listY + listH) {
                    boolean sel = ce.id().equals(cm.selectedCape.get());
                    boolean hov = mouseUnlocked && openDropdown == null && inBounds(mx, my, cx, cy, colW, 20);
                    AppleUiRenderer.drawElevatedCard(g, cx, cy, colW, 20, 5, sel ? 1f : 0f, hov ? 1f : 0f, accent);
                    g.blit(RenderPipelines.GUI_TEXTURED, ce.textureId(), cx + 5, cy + 2, 1.0f, 1.0f, 10, 16, 10, 16, 64, 32);
                    YeHdFont.draw(g, YeHdFont.clipToWidth(ce.displayName(), 8.4f, sel, colW - 22), cx + 19, cy + 5.5f, 8.4f,
                            sel ? AppleUiRenderer.TEXT_PRIMARY : AppleUiRenderer.textPrimary(), false, sel);
                    if (cy + 20 > listY + 2 && cy < listY + listH - 2) {
                        HIT_RECTS.add(new HitRect(cx, cy, colW, 20, () -> {
                            playClick();
                            cm.selectedCape.set(ce.id());
                            cm.setEnabled(true);
                            ConfigManager.saveAsync();
                        }, null, null));
                    }
                }
            }
            ry += ((capes.size() + 1) / 2) * 23 + 3;
        } else if (activeModule instanceof CustomSkinModule sm) {
            List<SkinManager.SkinEntry> skins = SkinManager.getAllSkins();
            int cols = 3;
            int gap = 4;
            int colW = (rowW - (cols - 1) * gap) / cols;
            for (int i = 0; i < skins.size(); i++) {
                SkinManager.SkinEntry se = skins.get(i);
                int c = i % cols;
                int cx = x + c * (colW + gap);
                int cy = ry + (i / cols) * 23;
                if (cy + 20 >= listY && cy <= listY + listH) {
                    boolean sel = se.id().equals(sm.selectedSkin.get());
                    boolean hov = mouseUnlocked && openDropdown == null && inBounds(mx, my, cx, cy, colW, 20);
                    AppleUiRenderer.drawElevatedCard(g, cx, cy, colW, 20, 5, sel ? 1f : 0f, hov ? 1f : 0f, accent);
                    g.blit(RenderPipelines.GUI_TEXTURED, se.textureId(), cx + 4, cy + 2, 8.0f, 8.0f, 16, 16, 8, 8, 64, 64);
                    g.blit(RenderPipelines.GUI_TEXTURED, se.textureId(), cx + 4, cy + 2, 40.0f, 8.0f, 16, 16, 8, 8, 64, 64);
                    String sName = YeHdFont.clipToWidth(se.displayName(), 8.0f, sel, colW - 25);
                    YeHdFont.draw(g, sName, cx + 23, cy + 5.5f, 8.0f,
                            sel ? AppleUiRenderer.TEXT_PRIMARY : AppleUiRenderer.textPrimary(), false, sel);
                    if (cy + 20 > listY + 2 && cy < listY + listH - 2) {
                        HIT_RECTS.add(new HitRect(cx, cy, colW, 20, () -> {
                            playClick();
                            sm.selectedSkin.set(se.id());
                            sm.setEnabled(true);
                            ConfigManager.saveAsync();
                        }, null, null));
                    }
                }
            }
            ry += ((skins.size() + cols - 1) / cols) * 23 + 3;
        }

        // 2-Column Balanced Table Layout when >= 4 settings!
        boolean twoCol = settings.size() >= 4;
        if (!twoCol) {
            for (int i = 0; i < settings.size(); i++) {
                Setting<?> s = settings.get(i);
                int cellH = (s instanceof DoubleSetting) ? 29 : 22;
                renderSettingCell(g, s, x, ry, rowW, cellH, listY, listH, mx, my, mouseUnlocked, !mouseUnlocked && i == selectedIndex, accent, false);
                ry += cellH + 4;
            }
        } else {
            int colGap = 6;
            int colW = (rowW - colGap) / 2;
            for (int i = 0; i < settings.size(); i += 2) {
                Setting<?> sL = settings.get(i);
                Setting<?> sR = (i + 1 < settings.size()) ? settings.get(i + 1) : null;
                int hL = (sL instanceof DoubleSetting) ? 29 : 23;
                int hR = (sR instanceof DoubleSetting) ? 29 : 23;
                int rowH = (sR == null) ? hL : Math.max(hL, hR);

                if (sR == null) {
                    renderSettingCell(g, sL, x, ry, rowW, rowH, listY, listH, mx, my, mouseUnlocked, !mouseUnlocked && i == selectedIndex, accent, false);
                } else {
                    renderSettingCell(g, sL, x, ry, colW, rowH, listY, listH, mx, my, mouseUnlocked, !mouseUnlocked && i == selectedIndex, accent, true);
                    renderSettingCell(g, sR, x + colW + colGap, ry, colW, rowH, listY, listH, mx, my, mouseUnlocked, !mouseUnlocked && (i + 1) == selectedIndex, accent, true);
                }
                ry += rowH + 4;
            }
        }

        int contentH = (ry - startRy) + 4;
        maxScroll = Math.max(0.0, contentH - listH);
        drawScrollbar(g, x + w - 2, listY, listH, contentH, accent);
        g.disableScissor();
    }

    private static void renderSettingCell(GuiGraphics g, Setting<?> s, int cx, int cy, int cw, int ch,
                                          int clipY, int clipH, int mx, int my, boolean mouseUnlocked,
                                          boolean keySel, int accent, boolean compactCol) {
        if (cy + ch < clipY - 2 || cy > clipY + clipH + 2) return;

        boolean hov = mouseUnlocked && openDropdown == null && inBounds(mx, my, cx, cy, cw, ch);
        float hProg = AppleUiRenderer.animChannel("shov_" + activeModule.getId() + "_" + s.getId(), (hov || keySel) ? 1f : 0f, 18f);
        AppleUiRenderer.drawElevatedCard(g, cx, cy, cw, ch, 6, 0f, hProg, accent);
        if (keySel) {
            AppleUiRenderer.drawRoundedOutline(g, cx, cy, cw, ch, 6, 1.5f, accent);
        }

        boolean inClip = cy + ch > clipY + 2 && cy < clipY + clipH - 2;
        float labelSize = compactCol ? 8.3f : 8.7f;

        if (s instanceof BoolSetting bs) {
            String lbl = YeHdFont.clipToWidth(s.getName(), labelSize, false, cw - 42);
            YeHdFont.draw(g, lbl, cx + 7, cy + (ch - 9) * 0.5f, labelSize, AppleUiRenderer.textPrimary(), false, false);
            float bProg = AppleUiRenderer.animChannel("bsw_" + activeModule.getId() + "_" + bs.getId(), bs.get() ? 1f : 0f, 18f);
            AppleUiRenderer.drawPillSwitch(g, cx + cw - 33, cy + (ch - 14) / 2, 27, 14, bProg, accent);
            if (inClip) {
                HIT_RECTS.add(new HitRect(cx, cy, cw, ch, () -> {
                    playClick();
                    openDropdown = null;
                    bs.toggle();
                    ConfigManager.saveAsync();
                }, null, null));
            }
        } else if (s instanceof DoubleSetting ds) {
            String vt = formatDouble(ds.get());
            float vtW = YeHdFont.widthF(vt, labelSize, true);
            String lbl = YeHdFont.clipToWidth(s.getName(), labelSize, false, cw - vtW - 18);
            YeHdFont.draw(g, lbl, cx + 7, cy + 4.5f, labelSize, AppleUiRenderer.textPrimary(), false, false);
            YeHdFont.draw(g, vt, cx + cw - 7 - vtW, cy + 4.5f, labelSize, accent, false, true);

            int bw = cw - 14;
            int bx = cx + 7;
            int by = cy + ch - 8;
            float targetPct = (float) ((ds.get() - ds.getMin()) / Math.max(0.0001, ds.getMax() - ds.getMin()));
            float pct = AppleUiRenderer.animChannel("dsl_" + activeModule.getId() + "_" + ds.getId(), targetPct, 22f);
            AppleUiRenderer.drawRoundedRect(g, bx, by, bw, 3.6f, 1.8f, AppleUiRenderer.lightTheme ? 0xFFCBD5E1 : 0xFF283142);
            float fillW = Math.max(3.6f, bw * pct);
            AppleUiRenderer.drawRoundedRect(g, bx, by, fillW, 3.6f, 1.8f, accent);
            AppleUiRenderer.drawCircle(g, bx + fillW - 4.0f, by - 2.2f, 8.0f, 0xFFFFFFFF);
            if (inClip) {
                HIT_RECTS.add(new HitRect(bx - 3, by - 7, bw + 6, 16, null, null, ds));
            }
        } else if (s instanceof EnumSetting<?> es) {
            String val = enumLabel(es.get()) + " ▾";
            int maxBtnW = compactCol ? (cw / 2 + 12) : (cw / 2 + 24);
            int bw = Math.min(maxBtnW, Math.max(compactCol ? 64 : 78, YeHdFont.width(val, 8.1f, false) + 14));
            int bx = cx + cw - 5 - bw;
            int by = cy + (ch - 16) / 2;

            String lbl = YeHdFont.clipToWidth(s.getName(), labelSize, false, cw - bw - 14);
            YeHdFont.draw(g, lbl, cx + 7, cy + (ch - 9) * 0.5f, labelSize, AppleUiRenderer.textPrimary(), false, false);

            boolean isOpen = (openDropdown == es);
            AppleUiRenderer.drawRoundedRect(g, bx, by, bw, 16, 5, isOpen ? accent : AppleUiRenderer.bgCardHover());
            AppleUiRenderer.drawCenteredSmooth(g, YeHdFont.clipToWidth(val, 8.1f, false, bw - 8), bx + bw * 0.5f, by + 3.8f, 8.1f,
                    isOpen ? AppleUiRenderer.TEXT_PRIMARY : AppleUiRenderer.textPrimary(), false);
            if (inClip) {
                HIT_RECTS.add(new HitRect(bx, by, bw, 16, () -> {
                    playClick();
                    if (openDropdown == es) {
                        openDropdown = null;
                    } else {
                        openDropdown = es;
                        dropScroll = 0.0;
                        dropX = bx;
                        dropY = by + 18;
                        dropW = Math.max(bw, 108);
                    }
                }, null, null));
            }
        } else if (s instanceof ActionSetting as) {
            String lbl = YeHdFont.clipToWidth(s.getName(), labelSize, false, cw - 52);
            YeHdFont.draw(g, lbl, cx + 7, cy + (ch - 9) * 0.5f, labelSize, AppleUiRenderer.textPrimary(), false, false);
            int bx = cx + cw - 45;
            int by = cy + (ch - 16) / 2;
            AppleUiRenderer.drawRoundedRect(g, bx, by, 40, 16, 5, accent);
            AppleUiRenderer.drawCenteredSmooth(g, ConfigManager.tr("执行", "Run"), bx + 20, by + 3.8f, 8.1f, AppleUiRenderer.TEXT_PRIMARY, true);
            if (inClip) {
                HIT_RECTS.add(new HitRect(bx, by, 40, 16, () -> {
                    playClick();
                    as.trigger();
                }, null, null));
            }
        }
    }

    private static <E extends Enum<E>> void renderDropdown(GuiGraphics g, EnumSetting<E> es, int mx, int my, int accent) {
        E[] vals = es.getDefaultValue().getDeclaringClass().getEnumConstants();
        int itemH = (vals.length > 0 && vals[0] instanceof com.ye.mod.modules.visual.CrosshairModule.Preset) ? 20 : 18;
        int maxVisItems = 9;
        int fullH = vals.length * itemH + 6;
        int boxH = Math.min(fullH, maxVisItems * itemH + 6);
        double maxDropSc = Math.max(0.0, fullH - boxH);
        dropScroll = Math.max(0.0, Math.min(maxDropSc, dropScroll));

        int sh = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        int sw = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int dx = Math.max(6, Math.min(sw - dropW - 6, dropX));
        int dy = (dropY + boxH + 6 > sh) ? Math.max(6, dropY - 22 - boxH) : dropY;

        AppleUiRenderer.drawElevatedWindow(g, dx, dy, dropW, boxH, 7);
        AppleUiRenderer.drawRoundedOutline(g, dx, dy, dropW, boxH, 7, 1.4f, accent);

        g.enableScissor(dx + 1, dy + 2, dx + dropW - 1, dy + boxH - 2);
        for (int i = 0; i < vals.length; i++) {
            E opt = vals[i];
            int iy = dy + 3 + i * itemH - (int) Math.round(dropScroll);
            if (iy + itemH < dy || iy > dy + boxH) continue;

            boolean sel = (opt == es.get());
            boolean hov = inBounds(mx, my, dx + 3, iy, dropW - 6, itemH);
            int bg = sel ? accent : (hov ? AppleUiRenderer.bgCardHover() : AppleUiRenderer.bgCard());
            AppleUiRenderer.drawRoundedRect(g, dx + 3, iy, dropW - 6, itemH - 1, 4.5f, bg);
            int swOff = YePreviewRenderer.renderOptionSwatch(g, opt, dx + 6, iy + 3, 11);
            YeHdFont.draw(g, enumLabel(opt), dx + 7 + swOff, iy + (itemH - 9) * 0.5f, 8.3f,
                    sel ? AppleUiRenderer.TEXT_PRIMARY : AppleUiRenderer.textPrimary(), false, sel);
            if (iy >= dy && iy + itemH <= dy + boxH + 2) {
                HIT_RECTS.add(new HitRect(dx + 3, iy, dropW - 6, itemH, () -> {
                    es.set(opt);
                    openDropdown = null;
                    playClick();
                    ConfigManager.saveAsync();
                }, null, null));
            }
        }
        g.disableScissor();
    }

    private static void renderSoundDropdown(GuiGraphics g, int mx, int my, int accent) {
        YeSoundPresets.SoundType[] vals = YeSoundPresets.SoundType.values();
        int itemH = 18;
        int boxH = vals.length * itemH + 6;
        int sh = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        int dy = (soundDropY + boxH + 6 > sh) ? Math.max(6, soundDropY - 22 - boxH) : soundDropY;

        AppleUiRenderer.drawElevatedWindow(g, soundDropX, dy, soundDropW, boxH, 7);
        AppleUiRenderer.drawRoundedOutline(g, soundDropX, dy, soundDropW, boxH, 7, 1.4f, accent);

        for (int i = 0; i < vals.length; i++) {
            YeSoundPresets.SoundType st = vals[i];
            int iy = dy + 3 + i * itemH;
            boolean sel = (st == ConfigManager.uiClickSound);
            boolean hov = inBounds(mx, my, soundDropX + 3, iy, soundDropW - 6, itemH);
            int bg = sel ? accent : (hov ? AppleUiRenderer.bgCardHover() : AppleUiRenderer.bgCard());
            AppleUiRenderer.drawRoundedRect(g, soundDropX + 3, iy, soundDropW - 6, itemH - 1, 4.5f, bg);
            YeHdFont.draw(g, enumLabel(st), soundDropX + 8, iy + 4.5f, 8.3f,
                    sel ? AppleUiRenderer.TEXT_PRIMARY : AppleUiRenderer.textPrimary(), false, sel);
            HIT_RECTS.add(new HitRect(soundDropX + 3, iy, soundDropW - 6, itemH, () -> {
                ConfigManager.uiClickSound = st;
                soundDropdownOpen = false;
                playClick();
                ConfigManager.saveAsync();
            }, null, null));
        }
    }

    private static void renderSettingsTab(GuiGraphics g, int x, int y, int w, int h,
                                          int mx, int my, boolean mouseUnlocked, int accent) {
        int startRy = y - (int) Math.round(scrollOffset);
        int ry = startRy;
        int rowW = w - 6;
        int halfW = (rowW - 6) / 2;

        // 1. Multi-Profile Manager
        String hdr = ConfigManager.tr(
                "配置档案 · 账号: " + ConfigManager.getCurrentAccountName() + " (当前: " + ConfigManager.getActiveProfile() + ")",
                "Profiles · User: " + ConfigManager.getCurrentAccountName() + " (" + ConfigManager.getActiveProfile() + ")");
        AppleUiRenderer.drawBoldText(g, hdr, x + 2, ry, 8.6f, AppleUiRenderer.textSecondary());
        ry += 14;

        List<String> profiles = ConfigManager.listAvailableProfiles();
        int pCols = 4;
        int pGap = 5;
        int pCellW = (rowW - (pCols - 1) * pGap) / pCols;
        for (int i = 0; i < profiles.size(); i++) {
            String pName = profiles.get(i);
            int pcx = x + (i % pCols) * (pCellW + pGap);
            int pcy = ry + (i / pCols) * 21;
            boolean sel = pName.equalsIgnoreCase(ConfigManager.getActiveProfile());
            boolean hov = mouseUnlocked && !soundDropdownOpen && inBounds(mx, my, pcx, pcy, pCellW, 18);
            AppleUiRenderer.drawElevatedCard(g, pcx, pcy, pCellW, 18, 5, sel ? 1f : 0f, hov ? 1f : 0f, accent);
            String disp = pName.equalsIgnoreCase(ConfigManager.DEFAULT_PROFILE)
                    ? ConfigManager.tr("default·默认", "default")
                    : YeHdFont.clipToWidth(pName, 8.2f, sel, pCellW - 8);
            AppleUiRenderer.drawCenteredSmooth(g, disp, pcx + pCellW * 0.5f, pcy + 4.5f, 8.2f,
                    sel ? AppleUiRenderer.TEXT_PRIMARY : AppleUiRenderer.textPrimary(), sel);
            if (pcy + 18 > y && pcy < y + h) {
                HIT_RECTS.add(new HitRect(pcx, pcy, pCellW, 18, () -> {
                    playClick();
                    profileInputMode = 0;
                    ConfigManager.switchToProfile(pName, true);
                    showToast(ConfigManager.tr("已切换配置: ", "Switched: ") + pName, accent);
                }, null, null));
            }
        }
        ry += ((profiles.size() + pCols - 1) / pCols) * 21 + 3;

        if (profileInputMode != 0) {
            AppleUiRenderer.drawRoundedRect(g, x, ry, rowW, 22, 6, 0xEE090D16);
            AppleUiRenderer.drawRoundedOutline(g, x, ry, rowW, 22, 6, 1.2f, accent);
            String prefix = (profileInputMode == 1) ? ConfigManager.tr("新建名称: ", "New Name: ") : ConfigManager.tr("重命名为: ", "Rename: ");
            String cursor = ((System.currentTimeMillis() / 450L) % 2 == 0) ? "|" : "";
            YeHdFont.draw(g, prefix + profileInputBuffer + cursor, x + 8, ry + 6.5f, 8.6f, 0xFFF8FAFC, false, false);

            int okX = x + rowW - 44;
            AppleUiRenderer.drawRoundedRect(g, okX, ry + 3, 40, 16, 4.5f, accent);
            AppleUiRenderer.drawCenteredSmooth(g, ConfigManager.tr("确定", "OK"), okX + 20, ry + 6.8f, 8.2f, AppleUiRenderer.TEXT_PRIMARY, true);
            if (ry + 22 > y && ry < y + h) {
                HIT_RECTS.add(new HitRect(okX, ry + 3, 40, 16, YeQuickMenu::confirmProfileInput, null, null));
            }
            ry += 26;
        }

        int thirdW = (rowW - 10) / 3;
        drawActionBtn(g, x, ry, thirdW, ConfigManager.tr("+ 新建档案", "+ New Profile"), mx, my, mouseUnlocked, accent, y, h, () -> {
            playClick();
            profileInputMode = 1;
            profileInputBuffer = ConfigManager.getCurrentAccountName() + "_pvp";
        });
        drawActionBtn(g, x + thirdW + 5, ry, thirdW, ConfigManager.tr("重命名档案", "Rename"), mx, my, mouseUnlocked, accent, y, h, () -> {
            playClick();
            if (ConfigManager.getActiveProfile().equalsIgnoreCase(ConfigManager.DEFAULT_PROFILE)) {
                showToast(ConfigManager.tr("出厂 default 不可重命名", "Cannot rename default"), 0xFFEF4444);
            } else {
                profileInputMode = 2;
                profileInputBuffer = ConfigManager.getActiveProfile();
            }
        });
        drawActionBtn(g, x + (thirdW + 5) * 2, ry, thirdW, ConfigManager.tr("设为新号模板", "Set Template"), mx, my, mouseUnlocked, accent, y, h, () -> {
            playClick();
            ConfigManager.saveCurrentAsDefaultTemplate();
            showToast(ConfigManager.tr("✓ 已设为新玩家默认模板", "Saved template"), 0xFF22C55E);
        });
        ry += 22;

        drawActionBtn(g, x, ry, thirdW, ConfigManager.tr("复制配置码", "Copy Code"), mx, my, mouseUnlocked, accent, y, h, () -> {
            playClick();
            String code = ConfigManager.exportShareCodeToClipboard();
            if (code != null) showToast(ConfigManager.tr("✓ 已复制配置码 (" + code.length() + "字)", "Copied share code"), 0xFF22C55E);
        });
        drawActionBtn(g, x + thirdW + 5, ry, thirdW, ConfigManager.tr("粘贴配置码", "Paste Code"), mx, my, mouseUnlocked, accent, y, h, () -> {
            playClick();
            if (ConfigManager.importShareCodeFromClipboard()) showToast(ConfigManager.tr("✓ 已导入剪贴板配置!", "Imported share code"), 0xFF22C55E);
            else showToast(ConfigManager.tr("剪贴板无有效 YE1 配置码", "Invalid clipboard code"), 0xFFEF4444);
        });
        drawActionBtn(g, x + (thirdW + 5) * 2, ry, thirdW, ConfigManager.tr("恢复出厂默认", "Factory Reset"), mx, my, mouseUnlocked, accent, y, h, () -> {
            playClick();
            ConfigManager.resetCurrentToFactoryDefault();
            showToast(ConfigManager.tr("✓ 已恢复出厂默认", "Reset to factory default"), 0xFF38BDF8);
        });
        ry += 22;

        drawActionBtn(g, x, ry, rowW, ConfigManager.tr("打开 configs 配置目录 (查看/备份各玩家 .json)", "Open configs Folder"), mx, my, mouseUnlocked, accent, y, h, () -> {
            playClick();
            ConfigManager.openConfigsFolder();
        });
        ry += 25;

        // 2. 2-Column UI, Sound & Hotkey Settings
        AppleUiRenderer.drawBoldText(g, ConfigManager.tr("界面、音效与快捷键", "Appearance, Sound & Hotkeys"), x + 2, ry, 8.6f, AppleUiRenderer.textSecondary());
        ry += 14;

        // Row A: Appearance (Left) + Language (Right)
        AppleUiRenderer.drawElevatedCard(g, x, ry, halfW, 22, 6, 0f, (mouseUnlocked && inBounds(mx, my, x, ry, halfW, 22)) ? 1f : 0f, accent);
        YeHdFont.draw(g, ConfigManager.tr("外观模式", "Theme"), x + 8, ry + 6.5f, 8.5f, AppleUiRenderer.textPrimary(), false, false);
        String tVal = ConfigManager.lightTheme ? ConfigManager.tr("浅色", "Light") : ConfigManager.tr("深色", "Dark");
        AppleUiRenderer.drawRoundedRect(g, x + halfW - 50, ry + 3, 44, 16, 5, accent);
        AppleUiRenderer.drawCenteredSmooth(g, tVal, x + halfW - 28, ry + 6.5f, 8.2f, AppleUiRenderer.TEXT_PRIMARY, true);
        if (ry + 22 > y && ry < y + h) {
            HIT_RECTS.add(new HitRect(x, ry, halfW, 22, () -> { playClick(); AppleUiRenderer.toggleTheme(); }, null, null));
        }

        int rxCol = x + halfW + 6;
        AppleUiRenderer.drawElevatedCard(g, rxCol, ry, halfW, 22, 6, 0f, (mouseUnlocked && inBounds(mx, my, rxCol, ry, halfW, 22)) ? 1f : 0f, accent);
        YeHdFont.draw(g, ConfigManager.tr("界面语言", "Language"), rxCol + 8, ry + 6.5f, 8.5f, AppleUiRenderer.textPrimary(), false, false);
        String lVal = ConfigManager.english ? "English" : "简体中文";
        AppleUiRenderer.drawRoundedRect(g, rxCol + halfW - 58, ry + 3, 52, 16, 5, accent);
        AppleUiRenderer.drawCenteredSmooth(g, lVal, rxCol + halfW - 32, ry + 6.5f, 8.2f, AppleUiRenderer.TEXT_PRIMARY, true);
        if (ry + 22 > y && ry < y + h) {
            HIT_RECTS.add(new HitRect(rxCol, ry, halfW, 22, () -> {
                playClick();
                ConfigManager.english = !ConfigManager.english;
                ConfigManager.saveAsync();
            }, null, null));
        }
        ry += 26;

        // Row B: Custom Button Sound (Left) + Button Sound Volume Cycle (Right)
        AppleUiRenderer.drawElevatedCard(g, x, ry, halfW, 22, 6, 0f, (mouseUnlocked && inBounds(mx, my, x, ry, halfW, 22)) ? 1f : 0f, accent);
        YeHdFont.draw(g, ConfigManager.tr("按键音效", "Click Sound"), x + 8, ry + 6.5f, 8.5f, AppleUiRenderer.textPrimary(), false, false);
        String sndLbl = enumLabel(ConfigManager.uiClickSound) + " ▾";
        int sbw = Math.min(halfW - 64, Math.max(72, YeHdFont.width(sndLbl, 8.1f, false) + 12));
        int sbx = x + halfW - 5 - sbw;
        AppleUiRenderer.drawRoundedRect(g, sbx, ry + 3, sbw, 16, 5, soundDropdownOpen ? accent : AppleUiRenderer.bgCardHover());
        AppleUiRenderer.drawCenteredSmooth(g, sndLbl, sbx + sbw * 0.5f, ry + 6.8f, 8.1f,
                soundDropdownOpen ? AppleUiRenderer.TEXT_PRIMARY : AppleUiRenderer.textPrimary(), false);
        if (ry + 22 > y && ry < y + h) {
            int finalRy = ry;
            HIT_RECTS.add(new HitRect(sbx, ry + 3, sbw, 16, () -> {
                soundDropdownOpen = !soundDropdownOpen;
                soundDropX = sbx;
                soundDropY = finalRy + 21;
                soundDropW = Math.max(sbw, 104);
                playClick();
            }, null, null));
        }

        AppleUiRenderer.drawElevatedCard(g, rxCol, ry, halfW, 22, 6, 0f, (mouseUnlocked && inBounds(mx, my, rxCol, ry, halfW, 22)) ? 1f : 0f, accent);
        YeHdFont.draw(g, ConfigManager.tr("按键音量", "Sound Vol"), rxCol + 8, ry + 6.5f, 8.5f, AppleUiRenderer.textPrimary(), false, false);
        String volStr = Math.round(ConfigManager.uiSoundVolume * 100) + "%";
        AppleUiRenderer.drawRoundedRect(g, rxCol + halfW - 50, ry + 3, 44, 16, 5, AppleUiRenderer.bgCardHover());
        AppleUiRenderer.drawCenteredSmooth(g, volStr, rxCol + halfW - 28, ry + 6.5f, 8.2f, accent, true);
        if (ry + 22 > y && ry < y + h) {
            HIT_RECTS.add(new HitRect(rxCol, ry, halfW, 22, () -> {
                float next = ConfigManager.uiSoundVolume + 0.25f;
                if (next > 1.30f) next = 0.25f;
                ConfigManager.uiSoundVolume = next;
                playClick();
                ConfigManager.saveAsync();
            }, null, null));
        }
        ry += 26;

        // Row C: Arrow UI Hotkey (Left) + Mouse UI Hotkey (Right)
        AppleUiRenderer.drawElevatedCard(g, x, ry, halfW, 22, 6, 0f, (mouseUnlocked && inBounds(mx, my, x, ry, halfW, 22)) ? 1f : 0f, accent);
        YeHdFont.draw(g, ConfigManager.tr("方向键UI", "Arrow UI"), x + 8, ry + 6.5f, 8.5f, AppleUiRenderer.textPrimary(), false, false);
        String mkVal = listeningKeySlot == 1 ? ConfigManager.tr("请按键...", "Press...") : keyName(ConfigManager.getMenuKey());
        AppleUiRenderer.drawRoundedRect(g, x + halfW - 56, ry + 3, 50, 16, 5, listeningKeySlot == 1 ? 0xFFEF4444 : accent);
        AppleUiRenderer.drawCenteredSmooth(g, mkVal, x + halfW - 31, ry + 6.5f, 8.2f, AppleUiRenderer.TEXT_PRIMARY, true);
        if (ry + 22 > y && ry < y + h) {
            HIT_RECTS.add(new HitRect(x, ry, halfW, 22, () -> {
                playClick();
                listeningModuleKey = null;
                listeningKeySlot = (listeningKeySlot == 1) ? 0 : 1;
            }, null, null));
        }

        AppleUiRenderer.drawElevatedCard(g, rxCol, ry, halfW, 22, 6, 0f, (mouseUnlocked && inBounds(mx, my, rxCol, ry, halfW, 22)) ? 1f : 0f, accent);
        YeHdFont.draw(g, ConfigManager.tr("鼠标UI", "Mouse UI"), rxCol + 8, ry + 6.5f, 8.5f, AppleUiRenderer.textPrimary(), false, false);
        String umVal = listeningKeySlot == 2 ? ConfigManager.tr("请按键...", "Press...") : keyName(ConfigManager.getMouseUnlockKey());
        AppleUiRenderer.drawRoundedRect(g, rxCol + halfW - 56, ry + 3, 50, 16, 5, listeningKeySlot == 2 ? 0xFFEF4444 : accent);
        AppleUiRenderer.drawCenteredSmooth(g, umVal, rxCol + halfW - 31, ry + 6.5f, 8.2f, AppleUiRenderer.TEXT_PRIMARY, true);
        if (ry + 22 > y && ry < y + h) {
            HIT_RECTS.add(new HitRect(rxCol, ry, halfW, 22, () -> {
                playClick();
                listeningModuleKey = null;
                listeningKeySlot = (listeningKeySlot == 2) ? 0 : 2;
            }, null, null));
        }
        ry += 26;

        // Theme Color Swatches
        AppleUiRenderer.drawBoldText(g, ConfigManager.tr("主题色", "Theme Color"), x + 2, ry, 8.6f, AppleUiRenderer.textSecondary());
        ry += 14;
        ConfigManager.ThemeAccent[] accents = ConfigManager.ThemeAccent.values();
        int cw = (rowW - 25) / 6;
        for (int i = 0; i < accents.length; i++) {
            ConfigManager.ThemeAccent ta = accents[i];
            int cx = x + i * (cw + 5);
            boolean sel = ConfigManager.themeAccent == ta;
            boolean hov = mouseUnlocked && inBounds(mx, my, cx, ry, cw, 22);
            AppleUiRenderer.drawElevatedCard(g, cx, ry, cw, 22, 6, sel ? 1f : 0f, hov ? 1f : 0f, ta.argb);
            if (!sel) {
                AppleUiRenderer.drawCircle(g, cx + 6, ry + 6, 10, ta.argb);
            }
            String label = ConfigManager.english ? ta.en : ta.zh;
            AppleUiRenderer.drawCenteredSmooth(g, label, cx + cw * 0.5f + (sel ? 0 : 5), ry + 6.5f, 8.4f,
                    sel ? AppleUiRenderer.TEXT_PRIMARY : AppleUiRenderer.textPrimary(), sel);
            if (ry + 22 > y && ry < y + h) {
                HIT_RECTS.add(new HitRect(cx, ry, cw, 22, () -> {
                    playClick();
                    ConfigManager.themeAccent = ta;
                    ConfigManager.saveAsync();
                }, null, null));
            }
        }
        ry += 27;

        drawActionBtn(g, x, ry, rowW, ConfigManager.tr("重置主面板与所有 HUD 窗口坐标", "Reset Menu & HUD Positions"), mx, my, mouseUnlocked, accent, y, h, () -> {
            playClick();
            ConfigManager.widgetX = -1;
            ConfigManager.widgetY = -1;
            ConfigManager.resetHudSlotsDefault();
            ConfigManager.saveAsync();
            showToast(ConfigManager.tr("已重置窗口与 HUD 坐标", "Reset positions"), accent);
        });
        ry += 24;

        int contentH = ry - startRy;
        maxScroll = Math.max(0.0, contentH - h);
        drawScrollbar(g, x + w - 2, y, h, contentH, accent);
    }

    private static void drawActionBtn(GuiGraphics g, int x, int y, int w, String text,
                                      int mx, int my, boolean mouseUnlocked, int accent, int clipY, int clipH, Runnable onClick) {
        boolean hov = mouseUnlocked && !soundDropdownOpen && inBounds(mx, my, x, y, w, 19);
        AppleUiRenderer.drawElevatedCard(g, x, y, w, 19, 5, 0f, hov ? 1f : 0f, accent);
        AppleUiRenderer.drawCenteredSmooth(g, text, x + w * 0.5f, y + 5.0f, 8.2f, AppleUiRenderer.textPrimary(), false);
        if (y + 19 > clipY && y < clipY + clipH) {
            HIT_RECTS.add(new HitRect(x, y, w, 19, onClick, null, null));
        }
    }

    public static void confirmProfileInput() {
        playClick();
        String name = profileInputBuffer.trim();
        if (!name.isEmpty()) {
            if (profileInputMode == 1) {
                String created = ConfigManager.createNewProfile(name);
                showToast(ConfigManager.tr("✓ 已新建并切换: ", "Created: ") + created, 0xFF22C55E);
            } else if (profileInputMode == 2) {
                if (ConfigManager.renameActiveProfile(name)) {
                    showToast(ConfigManager.tr("✓ 已重命名为: ", "Renamed: ") + ConfigManager.getActiveProfile(), 0xFF22C55E);
                } else {
                    showToast(ConfigManager.tr("重命名失败", "Rename failed"), 0xFFEF4444);
                }
            }
        }
        profileInputMode = 0;
    }

    private static void drawScrollbar(GuiGraphics g, int barX, int viewY, int viewH, int contentH, int accent) {
        if (contentH <= viewH + 2) return;
        AppleUiRenderer.drawRoundedRect(g, barX, viewY + 2, 2.2f, viewH - 4, 1.1f, 0x20FFFFFF);
        int trackH = viewH - 4;
        int thumbH = Math.max(18, Math.round((viewH / (float) contentH) * trackH));
        float ratio = (float) (scrollOffset / Math.max(1.0, contentH - viewH));
        int thumbY = viewY + 2 + Math.round((trackH - thumbH) * Math.max(0f, Math.min(1f, ratio)));
        AppleUiRenderer.drawRoundedRect(g, barX, thumbY, 2.2f, thumbH, 1.1f, AppleUiRenderer.withAlpha(accent, 0.88f));
    }

    public static String enumLabel(Enum<?> e) {
        if (e == null) return "";
        try {
            Field f = ENUM_LABEL_CACHE.computeIfAbsent(e.getDeclaringClass(), cls -> {
                try {
                    Field lf = cls.getDeclaredField("label");
                    lf.setAccessible(true);
                    return lf;
                } catch (Exception ex) {
                    return null;
                }
            });
            if (f != null) {
                Object v = f.get(e);
                if (v instanceof String s && !s.isEmpty()) {
                    if (!ConfigManager.english) return s;
                    String translated = com.ye.mod.core.YeI18n.text(s);
                    if (!translated.equals(s)) return translated;
                }
            }
        } catch (Exception ignored) {}
        String raw = e.name().replace('_', ' ');
        return raw.substring(0, 1).toUpperCase(Locale.ROOT) + raw.substring(1).toLowerCase(Locale.ROOT);
    }

    public static String keyName(int key) {
        if (key <= 0) return ConfigManager.tr("无", "None");
        if (key >= GLFW.GLFW_KEY_F1 && key <= GLFW.GLFW_KEY_F25) {
            return "F" + (key - GLFW.GLFW_KEY_F1 + 1);
        }
        return switch (key) {
            case GLFW.GLFW_KEY_RIGHT_SHIFT -> "RShift";
            case GLFW.GLFW_KEY_LEFT_SHIFT -> "LShift";
            case GLFW.GLFW_KEY_RIGHT_CONTROL -> "RCtrl";
            case GLFW.GLFW_KEY_LEFT_CONTROL -> "LCtrl";
            case GLFW.GLFW_KEY_RIGHT_ALT -> "RAlt";
            case GLFW.GLFW_KEY_LEFT_ALT -> "LAlt";
            case GLFW.GLFW_KEY_TAB -> "Tab";
            case GLFW.GLFW_KEY_CAPS_LOCK -> "Caps";
            default -> {
                String n = GLFW.glfwGetKeyName(key, 0);
                yield n != null ? n.toUpperCase(Locale.ROOT) : ("K" + key);
            }
        };
    }

    public static boolean handleEsc() {
        if (!open) return false;
        playClick();
        if (profileInputMode != 0) { profileInputMode = 0; return true; }
        if (listeningModuleKey != null) { listeningModuleKey = null; return true; }
        if (openDropdown != null) { openDropdown = null; return true; }
        if (soundDropdownOpen) { soundDropdownOpen = false; return true; }
        if (activeSection != TopSection.SETTINGS && activeModule != null) {
            activeModule = null;
            pageSlide = -16.0f;
            selectedIndex = 0;
            scrollOffset = 0.0;
            targetScroll = 0.0;
            return true;
        }
        if (activeSection != TopSection.FEATURES) {
            activeSection = TopSection.FEATURES;
            pageSlide = -16.0f;
            selectedIndex = 0;
            scrollOffset = 0.0;
            targetScroll = 0.0;
            return true;
        }
        setOpen(false);
        ConfigManager.saveAsync();
        return true;
    }

    private static List<Setting<?>> getDisplaySettings(Module m) {
        if (m instanceof AccessoriesModule am) {
            return am.getVisibleSettings();
        }
        List<Setting<?>> out = new ArrayList<>();
        for (Setting<?> s : m.getSettings()) {
            if (s.isVisible()) out.add(s);
        }
        return out;
    }

    public static void onKeyNav(int action) {
        if (!open) return;
        playClick();
        if (activeSection != TopSection.SETTINGS) {
            if (activeModule == null) {
                List<Module> mods = (activeSection == TopSection.FEATURES)
                        ? getAllFeatureModulesOrdered()
                        : ModuleManager.getByCategory(Category.HUD);
                if (action == 0 && selectedIndex >= GRID_COLS) selectedIndex -= GRID_COLS;
                else if (action == 1 && selectedIndex + GRID_COLS < mods.size()) selectedIndex += GRID_COLS;
                else if (action == 2) {
                    if (selectedIndex % GRID_COLS == 0 && activeSection.ordinal() > 0) {
                        switchSection(TopSection.values()[activeSection.ordinal() - 1]);
                        return;
                    } else if (selectedIndex > 0) selectedIndex--;
                } else if (action == 3) {
                    if ((selectedIndex % GRID_COLS == GRID_COLS - 1 || selectedIndex == mods.size() - 1)
                            && activeSection.ordinal() < TopSection.SETTINGS.ordinal()) {
                        switchSection(TopSection.values()[activeSection.ordinal() + 1]);
                        return;
                    } else if (selectedIndex < mods.size() - 1) selectedIndex++;
                } else if (action == 4 && !mods.isEmpty()) {
                    mods.get(selectedIndex).toggle();
                    ConfigManager.saveAsync();
                } else if (action == 6 && !mods.isEmpty()) {
                    openModuleDetail(mods.get(selectedIndex));
                } else if (action == 5) {
                    handleEsc();
                }

                int row = selectedIndex / GRID_COLS;
                double rowTop = row * 46.0;
                if (rowTop < targetScroll) targetScroll = rowTop;
                else if (rowTop + 52 > targetScroll + (WIN_H - 44)) targetScroll = rowTop + 52 - (WIN_H - 44);
            } else {
                List<Setting<?>> sets = getDisplaySettings(activeModule);
                if (action == 5) {
                    handleEsc();
                } else if (action == 0 && selectedIndex > 0) selectedIndex--;
                else if (action == 1 && selectedIndex < sets.size() - 1) selectedIndex++;
                else if (!sets.isEmpty()) {
                    Setting<?> s = sets.get(selectedIndex);
                    if (s instanceof BoolSetting bs && (action == 4 || action == 2 || action == 3)) {
                        bs.toggle();
                        ConfigManager.saveAsync();
                    } else if (s instanceof DoubleSetting ds && (action == 2 || action == 3)) {
                        ds.set(ds.get() + (action == 3 ? ds.getStep() : -ds.getStep()));
                        ConfigManager.saveAsync();
                    } else if (s instanceof EnumSetting<?> es && (action == 2 || action == 3 || action == 4)) {
                        es.cycle(action == 2 ? -1 : 1);
                        ConfigManager.saveAsync();
                    } else if (s instanceof ActionSetting as && action == 4) {
                        as.trigger();
                    }
                }
            }
        } else if (action == 2) {
            switchSection(TopSection.HUD);
        } else if (action == 5) {
            handleEsc();
        }
    }

    public static List<HitRect> getHitRects() { return HIT_RECTS; }

    public static void addScroll(double mouseX, double mouseY, double delta) {
        if (openDropdown != null && inBounds(mouseX, mouseY, dropX, Math.max(0, dropY - 220), dropW, 440)) {
            dropScroll = Math.max(0.0, dropScroll - delta * 18.0);
            return;
        }
        targetScroll = Math.max(0.0, Math.min(maxScroll, targetScroll - delta * 24.0));
    }

    private static boolean inBounds(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    private static String formatDouble(double v) {
        if (Math.abs(v - Math.round(v)) < 0.001) return String.valueOf((long) Math.round(v));
        return String.format(Locale.ROOT, "%.2f", v);
    }
}
