package com.ye.mod.ui;

import com.ye.mod.core.Category;
import com.ye.mod.core.ConfigManager;
import com.ye.mod.core.FriendManager;
import com.ye.mod.core.KeyCombo;
import com.ye.mod.core.Module;
import com.ye.mod.core.ModuleManager;
import com.ye.mod.core.YeSoundPresets;
import com.ye.mod.core.setting.ActionSetting;
import com.ye.mod.core.setting.BoolSetting;
import com.ye.mod.core.setting.ColorSetting;
import com.ye.mod.core.setting.DoubleSetting;
import com.ye.mod.core.setting.EnumSetting;
import com.ye.mod.core.setting.KeybindSetting;
import com.ye.mod.core.setting.Setting;
import com.ye.mod.core.setting.StringSetting;
import com.ye.mod.modules.hud.HudElementModule;
import com.ye.mod.modules.render.AccessoriesModule;
import com.ye.mod.modules.render.CapeManager;
import com.ye.mod.modules.render.CustomCapeModule;
import com.ye.mod.modules.render.CustomSkinModule;
import com.ye.mod.modules.render.SkinManager;
import com.ye.mod.modules.world.BlockSearchModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.world.entity.player.Player;

import java.awt.Color;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;

/**
 * Dual-Mode, Dual-Persona Main Interface (YeQuickMenu v1.7.3):
 * - Arrow UI (F9): Compact 308x206 card at bottom-right with smooth slide-down exit animation!
 * - Mouse UI (F10): Centered 538x366 spacious command center with vector module icons, Pin/Favorite badges,
 *   Gaussian Glass real blur, 2D draggable HSV color picker, double-click inline number input,
 *   confirmation modal on all resets, tutorial guide modal, and dedicated 4th top tab: 好友!
 */
public final class YeQuickMenu {
    public enum TopSection {
        FEATURES("功能", "Features"),
        HUD("HUD", "HUD"),
        SETTINGS("设置", "Settings"),
        FRIENDS("好友", "Friends");

        final String zh;
        final String en;
        TopSection(String zh, String en) { this.zh = zh; this.en = en; }
        String label() { return ConfigManager.tr(zh, en); }
    }

    private static final Category[] FEATURE_CATEGORIES = {
            Category.PLAYER_MODEL, Category.VISUAL, Category.UTILITY
    };

    // Compact Arrow-UI (bottom-right) vs Large Centered Mouse-UI
    public static final int COMPACT_W = 308;
    public static final int COMPACT_H = 206;
    public static final int LARGE_W = 538;
    public static final int LARGE_H = 366;

    private static boolean open = false;
    private static float openAnim = 0.0f;
    private static float pageSlide = 0.0f;
    private static float tabIndicatorX = -1.0f;
    private static float tabIndicatorW = 52.0f;

    // Keybind recording target (supports Module toggle key, Module preset cycle key, KeybindSetting, MenuKey, MouseKey)
    public static java.util.function.IntConsumer activeKeybindConsumer = null;
    public static String activeKeybindId = "";
    public static int pendingModifierKeyAlone = -1;

    // Inline text/number editing
    public static int textInputKind = 0; // 0=none, 1=new profile, 2=rename profile, 3=friend ID, 4=StringSetting, 5=Number edit, 6=Hex color
    public static String textInputBuffer = "";
    public static StringSetting activeStringSetting = null;
    public static DoubleConsumer activeNumberSetter = null;
    public static String activeNumberKey = "";

    // Interactive 2D HSV Color Picker Popover
    public static ColorSetting openColorSetting = null;
    public static boolean openThemeCustomColor = false;
    public static int pickerX, pickerY, pickerW = 148, pickerSvH = 86;

    // Confirmation Modal (确定要重置吗？) & Tutorial Guide Modal
    private static String confirmMessage = null;
    private static Runnable confirmAction = null;
    public static boolean tutorialModalOpen = false;

    private static String toastText = "";
    private static int toastColor = 0xFF22C55E;
    private static long toastUntilMs = 0L;

    private static TopSection activeSection = TopSection.FEATURES;
    private static Module activeModule = null;
    private static int selectedIndex = 0;

    private static double scrollOffset = 0.0;
    private static double targetScroll = 0.0;
    private static double maxScroll = 0.0;
    private static double savedFeaturesScroll = 0.0;
    private static double savedHudScroll = 0.0;

    private static EnumSetting<?> openDropdown = null;
    private static int dropX, dropY, dropW;
    private static double dropScroll = 0.0;

    private static boolean soundDropdownOpen = false;
    private static int soundDropX, soundDropY, soundDropW;

    private static final Map<Class<?>, Field> ENUM_LABEL_CACHE = new HashMap<>();
    private static final List<HitRect> HIT_RECTS = new ArrayList<>();

    public interface SliderBinding {
        double getMin();
        double getMax();
        void setValue(double v);
    }

    public record HitRect(int x, int y, int w, int h, Runnable leftClick, Runnable rightClick, SliderBinding slider, int zPriority, Runnable doubleClick) {
        public HitRect(int x, int y, int w, int h, Runnable leftClick, Runnable rightClick, SliderBinding slider, int zPriority) {
            this(x, y, w, h, leftClick, rightClick, slider, zPriority, null);
        }
    }

    private YeQuickMenu() {}

    public static boolean isOpen() { return open; }

    public static void showToast(String msg, int color) {
        toastText = msg;
        toastColor = color;
        toastUntilMs = System.currentTimeMillis() + 2400L;
    }

    public static void requestConfirmation(String msg, Runnable onConfirm) {
        confirmMessage = msg;
        confirmAction = onConfirm;
    }

    public static void openEntityPickerModal(String title, Set<String> set) {
        YePickerModal.openEntityModal(title, set);
    }

    public static void openBlockPickerModal(String title, Set<String> set) {
        YePickerModal.openBlockModal(title, set);
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

    public static void closeOverlays() {
        openDropdown = null;
        soundDropdownOpen = false;
        openColorSetting = null;
        openThemeCustomColor = false;
        activeKeybindConsumer = null;
        activeKeybindId = "";
        pendingModifierKeyAlone = -1;
        commitInlineInputIfNumber();
        textInputKind = 0;
        confirmMessage = null;
        confirmAction = null;
        tutorialModalOpen = false;
        YePickerModal.close();
    }

    public static void openHudElementSettings(HudElementModule hm) {
        if (hm == null) return;
        open = true;
        ConfigManager.uiMinimized = false;
        openAnim = 1.0f;
        activeSection = TopSection.HUD;
        activeModule = hm;
        closeOverlays();
        selectedIndex = 0;
        scrollOffset = 0.0;
        targetScroll = 0.0;
        playClick();
    }

    public static void openSectionFromSidebar(TopSection sec) {
        Minecraft mc = Minecraft.getInstance();
        open = true;
        ConfigManager.uiMinimized = false;
        activeSection = sec;
        activeModule = null;
        closeOverlays();
        if (mc != null && !(mc.screen instanceof YeMouseScreen)) {
            mc.setScreen(new YeMouseScreen());
        }
        playClick();
    }

    public static void playClick() {
        YeSoundPresets.playUi(ConfigManager.uiClickSound, ConfigManager.uiSoundVolume);
    }

    public static int currentWinW(boolean mouseMode) {
        if (!mouseMode) return COMPACT_W;
        Minecraft mc = Minecraft.getInstance();
        int sw = (mc != null && mc.getWindow() != null) ? mc.getWindow().getGuiScaledWidth() : LARGE_W;
        return Math.max(336, Math.min(Math.round(sw * 0.82f), sw - 34));
    }

    public static int currentWinH(boolean mouseMode) {
        if (!mouseMode) return COMPACT_H;
        Minecraft mc = Minecraft.getInstance();
        int sh = (mc != null && mc.getWindow() != null) ? mc.getWindow().getGuiScaledHeight() : LARGE_H;
        return Math.max(210, Math.min(Math.round(sh * 0.82f), sh - 20));
    }

    public static int getWinX(int sw, boolean mouseMode) {
        if (mouseMode) {
            return Math.max(6, (sw - currentWinW(true)) / 2);
        }
        return Math.max(6, sw - COMPACT_W - 10);
    }

    public static int getWinY(int sh, boolean mouseMode) {
        if (mouseMode) {
            return Math.max(6, (sh - currentWinH(true)) / 2);
        }
        return Math.max(6, sh - COMPACT_H - 10);
    }

    public static boolean isPointInsideMenuOrOverlay(double mx, double my, int sw, int sh) {
        if (ConfigManager.rusherSidebar && inBounds(mx, my, sw - 27, (sh - 68) / 2, 24, 68)) return true;
        if (ConfigManager.uiMinimized) {
            int minW = 58, minH = 22;
            int minX = sw - minW - 12, minY = 10;
            return inBounds(mx, my, minX, minY, minW, minH);
        }
        if (YePickerModal.isOpen() || confirmMessage != null || tutorialModalOpen) return true;
        int ww = currentWinW(true), wh = currentWinH(true);
        int wx = getWinX(sw, true);
        int wy = getWinY(sh, true);
        if (inBounds(mx, my, wx, wy, ww, wh)) return true;
        if (openDropdown != null && inBounds(mx, my, dropX, Math.max(4, dropY - 220), Math.max(dropW, 300), 440)) return true;
        if (soundDropdownOpen && inBounds(mx, my, soundDropX, Math.max(4, soundDropY - 180), soundDropW, 360)) return true;
        if ((openColorSetting != null || openThemeCustomColor) && inBounds(mx, my, pickerX, pickerY, pickerW + 16, pickerSvH + 54)) return true;
        return false;
    }

    private static void addClippedHit(int x, int y, int w, int h, int clipTop, int clipBot,
                                      Runnable leftClick, Runnable rightClick, SliderBinding slider, Runnable dblClick) {
        int top = Math.max(clipTop, y);
        int bot = Math.min(clipBot, y + h);
        if (bot - top >= 3) {
            HIT_RECTS.add(new HitRect(x, top, w, bot - top, leftClick, rightClick, slider, 0, dblClick));
        }
    }

    public static void render(GuiGraphics g, int mouseX, int mouseY, boolean mouseUnlocked) {
        render(g, mouseX, mouseY, mouseUnlocked, false);
    }

    public static void render(GuiGraphics g, int mouseX, int mouseY, boolean mouseUnlocked, boolean hideForHudDrag) {
        AppleUiRenderer.beginFrame();
        float dt = AppleUiRenderer.dt();
        boolean mech = AppleUiRenderer.isMechanical();

        float targetOpen = open ? 1.0f : 0.0f;
        openAnim = mech ? targetOpen : AppleUiRenderer.smooth(openAnim, targetOpen, 18.0f, dt);
        pageSlide = mech ? 0.0f : AppleUiRenderer.smooth(pageSlide, 0.0f, 22.0f, dt);

        if (targetScroll > maxScroll) targetScroll = maxScroll;
        if (targetScroll < 0.0) targetScroll = 0.0;
        scrollOffset = mech ? targetScroll : AppleUiRenderer.smooth((float) scrollOffset, (float) targetScroll, 24.0f, dt);
        if (scrollOffset > maxScroll) scrollOffset = maxScroll;
        if (scrollOffset < 0.0) scrollOffset = 0.0;

        Minecraft mc = Minecraft.getInstance();
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();

        // Always render floating Toast even when menu is closed!
        if (!open && openAnim < 0.015f) {
            renderToastOnly(g, sw, sh);
            return;
        }

        if (hideForHudDrag) {
            renderToastOnly(g, sw, sh);
            return;
        }

        int accent = ConfigManager.getAccentColor();

        // Compact Minimized "Yer" Floating Bar
        if (mouseUnlocked && ConfigManager.uiMinimized) {
            HIT_RECTS.clear();
            int minW = 58, minH = 22;
            int minX = sw - minW - 12, minY = 10;
            int rad = mech ? 0 : 7;
            AppleUiRenderer.drawElevatedWindow(g, minX, minY, minW, minH, rad);
            AppleUiRenderer.drawRoundedOutline(g, minX, minY, minW, minH, rad, 1.2f, accent);
            AppleUiRenderer.drawRoundedRect(g, minX + 6, minY + 5, 3, 12, 1.5f, accent);
            YeHdFont.draw(g, "Yer", minX + 13, YeHdFont.vCenterY(minY, minH, 9.5f), 9.5f, AppleUiRenderer.textPrimary(), false, true);
            YeHdFont.draw(g, "+", minX + minW - 14, YeHdFont.vCenterY(minY, minH, 9.5f), 9.5f, accent, false, true);
            HIT_RECTS.add(new HitRect(minX, minY, minW, minH, () -> {
                playClick();
                ConfigManager.uiMinimized = false;
            }, null, null, 8));
            renderRusherSidebar(g, sw, sh, mouseX, mouseY, true);
            renderToastOnly(g, sw, sh);
            return;
        }

        int winW = currentWinW(mouseUnlocked);
        int winH = currentWinH(mouseUnlocked);
        int wx = getWinX(sw, mouseUnlocked);
        int baseWy = getWinY(sh, mouseUnlocked);
        // Only Compact Arrow UI slides down off-screen at bottom-right; Large Mouse UI stays centered with smooth zoom/fade!
        int slideDownPx = mouseUnlocked ? 0 : Math.round((1.0f - openAnim) * (winH + 24.0f));
        int wy = baseWy + slideDownPx;
        int gridCols = mouseUnlocked ? (winW >= 540 ? 5 : 4) : 3;

        HIT_RECTS.clear();

        // Smooth center-scale for Large UI (uniform regardless of direction!)
        float uiScale = (mouseUnlocked && !mech) ? (0.93f + 0.07f * openAnim) : 1.0f;
        var pose = g.pose();
        boolean scaledPose = Math.abs(uiScale - 1.0f) > 0.002f;
        if (scaledPose) {
            pose.pushMatrix();
            pose.translate(wx + winW * 0.5f, wy + winH * 0.5f);
            pose.scale(uiScale, uiScale);
            pose.translate(-(wx + winW * 0.5f), -(wy + winH * 0.5f));
        }

        // 1. Window Shell + Header Bar
        int winRad = mech ? 0 : (mouseUnlocked ? 12 : 9);
        int hdrH = mouseUnlocked ? 32 : 24;
        AppleUiRenderer.drawElevatedWindow(g, wx, wy, winW, winH, winRad);
        AppleUiRenderer.drawRoundedRect(g, wx + 2, wy + 2, winW - 4, hdrH - 2, mech ? 0 : Math.max(4, winRad - 2), AppleUiRenderer.bgHeader());
        g.fill(wx + 8, wy + hdrH, wx + winW - 8, wy + hdrH + 1, AppleUiRenderer.borderSubtle());

        // Brand Mark
        AppleUiRenderer.drawRoundedRect(g, wx + 10, wy + (hdrH - 13) / 2f, 3.2f, 13, 1.6f, accent);
        AppleUiRenderer.drawBoldText(g, "Ye", wx + 17, YeHdFont.vCenterY(wy, hdrH, mouseUnlocked ? 10.0f : 9.0f), mouseUnlocked ? 10.0f : 9.0f, AppleUiRenderer.textPrimary());

        // 4 Top Tabs: 功能 | HUD | 设置 | 好友 (Registered at zPriority = 2 so scrolled cards NEVER intercept them!)
        int tx = wx + (mouseUnlocked ? 44 : 36);
        TopSection[] secs = TopSection.values();
        int targetTabX = tx;
        int targetTabW = 48;
        int[] tabXs = new int[secs.length];
        int[] tabWs = new int[secs.length];
        float tabFont = mouseUnlocked ? 8.9f : 8.0f;
        int tabH = mouseUnlocked ? 20 : 16;
        int tabY = wy + (hdrH - tabH) / 2;

        for (int i = 0; i < secs.length; i++) {
            String lbl = secs[i].label();
            int tw = Math.max(mouseUnlocked ? 46 : 36, YeHdFont.width(lbl, tabFont, true) + (mouseUnlocked ? 18 : 12));
            tabXs[i] = tx;
            tabWs[i] = tw;
            if (secs[i] == activeSection) {
                targetTabX = tx;
                targetTabW = tw;
            }
            tx += tw + (mouseUnlocked ? 5 : 3);
        }

        if (tabIndicatorX < 0 || mech) {
            tabIndicatorX = targetTabX;
            tabIndicatorW = targetTabW;
        } else {
            tabIndicatorX = AppleUiRenderer.smooth(tabIndicatorX, targetTabX, 22.0f, dt);
            tabIndicatorW = AppleUiRenderer.smooth(tabIndicatorW, targetTabW, 22.0f, dt);
        }
        AppleUiRenderer.drawRoundedRect(g, tabIndicatorX, tabY, tabIndicatorW, tabH, mech ? 0 : 6.0f, accent);

        for (int i = 0; i < secs.length; i++) {
            TopSection sec = secs[i];
            int bx = tabXs[i], bw = tabWs[i];
            boolean sel = (sec == activeSection);
            boolean hov = mouseUnlocked && !hasModalOpen() && inBounds(mouseX, mouseY, bx, tabY, bw, tabH);
            if (!sel && hov) {
                AppleUiRenderer.drawRoundedRect(g, bx, tabY, bw, tabH, mech ? 0 : 6.0f, AppleUiRenderer.bgCardHover());
            }
            YeHdFont.drawCenteredInBox(g, sec.label(), bx, tabY, bw, tabH, tabFont,
                    sel ? 0xFFFFFFFF : (hov ? AppleUiRenderer.textPrimary() : AppleUiRenderer.textSecondary()), false, sel);
            HIT_RECTS.add(new HitRect(bx, tabY, bw, tabH, () -> switchSection(sec), null, null, 2));
        }

        if (mouseUnlocked) {
            // Top-right Minimize button -> shrinks to compact "Yer" bar
            int minBtnW = 18, minBtnX = wx + winW - 26, minBtnY = wy + (hdrH - 16) / 2;
            boolean minHov = inBounds(mouseX, mouseY, minBtnX, minBtnY, minBtnW, 16);
            AppleUiRenderer.drawRoundedRect(g, minBtnX, minBtnY, minBtnW, 16, mech ? 0 : 4, minHov ? accent : AppleUiRenderer.bgCardHover());
            YeHdFont.drawCenteredInBox(g, "—", minBtnX, minBtnY, minBtnW, 16, 8.2f, minHov ? 0xFFFFFFFF : AppleUiRenderer.textSecondary(), false, true);
            HIT_RECTS.add(new HitRect(minBtnX, minBtnY, minBtnW, 16, () -> {
                playClick();
                ConfigManager.uiMinimized = true;
            }, null, null, 3));

            String profBadge = ConfigManager.tr("档案: ", "Profile: ") + ConfigManager.getActiveProfile();
            int pw = YeHdFont.width(profBadge, 8.2f, false);
            if (tx + pw + 36 < wx + winW) {
                YeHdFont.draw(g, profBadge, minBtnX - 8 - pw, YeHdFont.vCenterY(wy, hdrH, 8.2f), 8.2f, AppleUiRenderer.textTertiary(), false, false);
            }
        }

        int padX = mouseUnlocked ? 12 : 8;
        int bodyX = wx + padX + Math.round(pageSlide);
        int bodyY = wy + hdrH + 5;
        int bodyW = winW - padX * 2;
        int bodyH = winH - hdrH - 10;

        // Strict Scissor AT bodyY (zero card bleed above bodyY!)
        g.enableScissor(wx + 4, bodyY, wx + winW - 4, bodyY + bodyH);
        if (activeModule != null && (activeSection == TopSection.FEATURES || activeSection == TopSection.HUD)) {
            renderModuleDetail(g, mc.font, bodyX, bodyY, bodyW, bodyH, mouseX, mouseY, mouseUnlocked, accent);
        } else if (activeSection == TopSection.FEATURES) {
            renderUnifiedFeaturesPage(g, bodyX, bodyY, bodyW, bodyH, gridCols, mouseX, mouseY, mouseUnlocked, accent);
        } else if (activeSection == TopSection.HUD) {
            renderHudDashboardPage(g, bodyX, bodyY, bodyW, bodyH, gridCols, mouseX, mouseY, mouseUnlocked, accent);
        } else if (activeSection == TopSection.SETTINGS) {
            renderSettingsTab(g, bodyX, bodyY, bodyW, bodyH, mouseX, mouseY, mouseUnlocked, accent);
        } else {
            renderFriendsTab(g, bodyX, bodyY, bodyW, bodyH, mouseX, mouseY, mouseUnlocked, accent);
        }
        g.disableScissor();

        // Overlays & Modals (higher Z-priority!)
        if (mouseUnlocked) {
            if (openDropdown != null) {
                renderDropdown(g, openDropdown, mouseX, mouseY, accent);
            }
            if (soundDropdownOpen) {
                renderSoundDropdown(g, mouseX, mouseY, accent);
            }
            if (openColorSetting != null || openThemeCustomColor) {
                renderColorPickerPopover(g, sw, sh, mouseX, mouseY, accent);
            }
            if (tutorialModalOpen) {
                renderTutorialModal(g, sw, sh, accent);
            }
            if (YePickerModal.isOpen()) {
                YePickerModal.render(g, sw, sh, mouseX, mouseY, accent, HIT_RECTS);
            }
            if (confirmMessage != null) {
                renderConfirmationModal(g, sw, sh, mouseX, mouseY, accent);
            }
        }

        if (scaledPose) {
            pose.popMatrix();
        }

        // RusherHack-style right-edge floating Features | HUD switcher
        if (mouseUnlocked) {
            renderRusherSidebar(g, sw, sh, mouseX, mouseY, true);
        }

        renderToastOnly(g, sw, sh);
    }

    public static void renderRusherSidebar(GuiGraphics g, int sw, int sh, int mx, int my, boolean clickable) {
        if (!ConfigManager.rusherSidebar) return;
        int accent = ConfigManager.getAccentColor();
        int bw = 24, bh = 68;
        int bx = sw - bw - 3;
        int by = (sh - bh) / 2;
        int rad = AppleUiRenderer.isMechanical() ? 0 : 6;
        AppleUiRenderer.drawRoundedRect(g, bx, by, bw, bh, rad, 0xCC0B101B);
        AppleUiRenderer.drawRoundedOutline(g, bx, by, bw, bh, rad, 1.0f, AppleUiRenderer.borderOuter());

        boolean inFeat = open && activeSection == TopSection.FEATURES;
        boolean inHud = open && activeSection == TopSection.HUD;
        int topH = bh / 2 - 2;

        AppleUiRenderer.drawRoundedRect(g, bx + 2, by + 2, bw - 4, topH, rad > 0 ? 4 : 0, inFeat ? accent : 0x22FFFFFF);
        YeHdFont.drawCenteredInBox(g, ConfigManager.tr("功", "F"), bx + 2, by + 2, bw - 4, topH, 8.2f, inFeat ? 0xFFFFFFFF : AppleUiRenderer.textPrimary(), false, true);

        AppleUiRenderer.drawRoundedRect(g, bx + 2, by + topH + 3, bw - 4, topH, rad > 0 ? 4 : 0, inHud ? accent : 0x22FFFFFF);
        YeHdFont.drawCenteredInBox(g, "H", bx + 2, by + topH + 3, bw - 4, topH, 8.2f, inHud ? 0xFFFFFFFF : AppleUiRenderer.textPrimary(), false, true);

        if (clickable) {
            HIT_RECTS.add(new HitRect(bx + 2, by + 2, bw - 4, topH, () -> openSectionFromSidebar(TopSection.FEATURES), null, null, 7));
            HIT_RECTS.add(new HitRect(bx + 2, by + topH + 3, bw - 4, topH, () -> openSectionFromSidebar(TopSection.HUD), null, null, 7));
        }
    }

    private static boolean hasModalOpen() {
        return YePickerModal.isOpen() || confirmMessage != null || tutorialModalOpen;
    }

    private static void renderToastOnly(GuiGraphics g, int sw, int sh) {
        long rem = toastUntilMs - System.currentTimeMillis();
        if (rem <= 0 || toastText.isEmpty()) return;
        float alpha = Math.min(1.0f, rem / 280.0f);
        int tw = YeHdFont.width(toastText, 8.6f, true) + 24;
        int txBox = (sw - tw) / 2;
        int tyBox = 14;
        int rad = AppleUiRenderer.isMechanical() ? 0 : 7;
        AppleUiRenderer.drawRoundedRect(g, txBox, tyBox, tw, 20, rad, AppleUiRenderer.withAlpha(0xF20B101B, alpha));
        AppleUiRenderer.drawRoundedOutline(g, txBox, tyBox, tw, 20, rad, 1.2f, AppleUiRenderer.withAlpha(toastColor, alpha));
        YeHdFont.drawCenteredInBox(g, toastText, txBox, tyBox, tw, 20, 8.6f, AppleUiRenderer.withAlpha(toastColor, alpha), false, true);
    }

    private static void switchSection(TopSection sec) {
        if (activeSection == sec && activeModule == null) return;
        playClick();
        pageSlide = (sec.ordinal() >= activeSection.ordinal()) ? 14.0f : -14.0f;
        activeSection = sec;
        activeModule = null;
        closeOverlays();
        selectedIndex = 0;
        scrollOffset = 0.0;
        targetScroll = 0.0;
    }

    private static List<Module> getOrderedFeatureModulesForNav() {
        List<Module> all = new ArrayList<>();
        // 1) Pinned first
        for (Category cat : FEATURE_CATEGORIES) {
            for (Module m : ModuleManager.getByCategory(cat)) {
                if (ConfigManager.isPinned(m)) all.add(m);
            }
        }
        // 2) Favorites folder
        for (Category cat : FEATURE_CATEGORIES) {
            for (Module m : ModuleManager.getByCategory(cat)) {
                if (!ConfigManager.isPinned(m) && ConfigManager.isFavorite(m)) all.add(m);
            }
        }
        // 3) Categories
        for (Category cat : FEATURE_CATEGORIES) {
            for (Module m : ModuleManager.getByCategory(cat)) {
                if (!ConfigManager.isPinned(m)) all.add(m);
            }
        }
        return all;
    }

    // ==================== 1. FEATURES PAGE (PINNED + FAVORITES FOLDER + 3 CATEGORIES) ====================

    private static void renderUnifiedFeaturesPage(GuiGraphics g, int x, int y, int w, int h, int cols,
                                                  int mx, int my, boolean mouseUnlocked, int accent) {
        List<Module> navList = getOrderedFeatureModulesForNav();
        if (selectedIndex >= navList.size()) selectedIndex = Math.max(0, navList.size() - 1);

        int gap = mouseUnlocked ? 6 : 4;
        int cellW = (w - 6 - (cols - 1) * gap) / cols;
        int cellH = mouseUnlocked ? 40 : 28;
        int rowStep = cellH + gap;

        int startRy = y - (int) Math.round(scrollOffset);
        int ry = startRy;
        int globalIdx = 0;

        // A. Pinned Modules at very top!
        List<Module> pinned = new ArrayList<>();
        List<Module> favs = new ArrayList<>();
        for (Category cat : FEATURE_CATEGORIES) {
            for (Module m : ModuleManager.getByCategory(cat)) {
                if (ConfigManager.isPinned(m)) pinned.add(m);
                if (ConfigManager.isFavorite(m)) favs.add(m);
            }
        }

        if (!pinned.isEmpty()) {
            ry = drawGroupGrid(g, ConfigManager.tr("置顶", "Pinned"), pinned, x, ry, w, cols, cellW, cellH, gap, rowStep,
                    y, h, mx, my, mouseUnlocked, globalIdx, accent);
            globalIdx += pinned.size();
        }

        // B. Favorites Folder Section
        if (!favs.isEmpty()) {
            List<Module> nonDupFavs = favs.stream().filter(m -> !ConfigManager.isPinned(m)).toList();
            ry = drawGroupGrid(g, ConfigManager.tr("收藏夹", "Favorites"), favs, x, ry, w, cols, cellW, cellH, gap, rowStep,
                    y, h, mx, my, mouseUnlocked, globalIdx, 0xFFFACC15);
            globalIdx += nonDupFavs.size();
        }

        // C. Standard 3 Feature Categories (excluding pinned so pinned stays cleanly at top)
        for (Category cat : FEATURE_CATEGORIES) {
            List<Module> mods = ModuleManager.getByCategory(cat).stream().filter(m -> !ConfigManager.isPinned(m)).toList();
            if (mods.isEmpty()) continue;
            ry = drawGroupGrid(g, cat.label(), mods, x, ry, w, cols, cellW, cellH, gap, rowStep,
                    y, h, mx, my, mouseUnlocked, globalIdx, accent);
            globalIdx += mods.size();
        }

        int contentH = Math.max(0, (ry - startRy) - 4);
        maxScroll = Math.max(0.0, contentH - h);
        drawScrollbar(g, x + w - 2, y, h, contentH, accent);
    }

    private static int drawGroupGrid(GuiGraphics g, String title, List<Module> mods, int x, int ry, int w, int cols,
                                     int cellW, int cellH, int gap, int rowStep, int clipY, int clipH,
                                     int mx, int my, boolean mouseUnlocked, int startGlobalIdx, int barCol) {
        if (ry + 16 >= clipY && ry <= clipY + clipH) {
            AppleUiRenderer.drawRoundedRect(g, x + 2, ry + 3, 3, 10, 1.5f, barCol);
            AppleUiRenderer.drawBoldText(g, title, x + 9, YeHdFont.vCenterY(ry, 16, 8.6f), 8.6f, AppleUiRenderer.textSecondary());
            int titleW = YeHdFont.width(title, 8.6f, true);
            g.fill(x + 14 + titleW, ry + 8, x + w - 8, ry + 9, AppleUiRenderer.borderSubtle());
        }
        ry += 17;

        for (int i = 0; i < mods.size(); i++) {
            Module m = mods.get(i);
            int col = i % cols;
            int row = i / cols;
            int cx = x + col * (cellW + gap);
            int rawCy = ry + row * rowStep;
            boolean keySel = !mouseUnlocked && ((startGlobalIdx + i) == selectedIndex);
            renderModuleCardCell(g, m, cx, rawCy, cellW, cellH, clipY, clipH, mx, my, mouseUnlocked, keySel, ConfigManager.getAccentColor());
        }
        int rows = (mods.size() + cols - 1) / cols;
        return ry + rows * rowStep + 5;
    }

    // ==================== 2. HUD DASHBOARD PAGE ====================

    private static void renderHudDashboardPage(GuiGraphics g, int x, int y, int w, int h, int cols,
                                               int mx, int my, boolean mouseUnlocked, int accent) {
        List<Module> hudMods = ModuleManager.getByCategory(Category.HUD);
        if (selectedIndex >= hudMods.size()) selectedIndex = Math.max(0, hudMods.size() - 1);

        int startRy = y - (int) Math.round(scrollOffset);
        int ry = startRy;
        int rowW = w - 6;

        if (mouseUnlocked && ry + 22 >= y && ry <= y + h) {
            AppleUiRenderer.drawRoundedRect(g, x, ry, rowW, 21, 6, AppleUiRenderer.bgCard());
            AppleUiRenderer.drawRoundedOutline(g, x, ry, rowW, 21, 6, 1.0f, AppleUiRenderer.borderSubtle());
            String desc = ConfigManager.tr("屏幕四角、四边中点与正中央设有自动磁吸锚点", "9-zone magnetic alignment anchors on screen edges & center");
            YeHdFont.draw(g, YeHdFont.clipToWidth(desc, 8.2f, false, rowW - 88), x + 8, YeHdFont.vCenterY(ry, 21, 8.2f), 8.2f, AppleUiRenderer.textSecondary(), false, false);

            int btnW = 76;
            int btnX = x + rowW - btnW - 4;
            AppleUiRenderer.drawRoundedRect(g, btnX, ry + 3, btnW, 15, 4.5f, accent);
            YeHdFont.drawCenteredInBox(g, ConfigManager.tr("重置全部位置", "Reset All"), btnX, ry + 3, btnW, 15, 7.9f, 0xFFFFFFFF, false, true);
            addClippedHit(btnX, ry + 3, btnW, 15, y, y + h, () -> {
                playClick();
                requestConfirmation(ConfigManager.tr("确定重置全部 HUD 组件的屏幕位置吗？", "Reset all HUD positions?"), () -> {
                    ConfigManager.resetHudSlotsDefault();
                    ConfigManager.saveAsync();
                    showToast(ConfigManager.tr("已恢复全部 HUD 默认布局", "Reset all HUD positions"), accent);
                });
            }, null, null, null);
            ry += 26;
        }

        int gap = mouseUnlocked ? 6 : 4;
        int cellW = (w - 6 - (cols - 1) * gap) / cols;
        int cellH = mouseUnlocked ? 40 : 28;
        int rowStep = cellH + gap;

        for (int i = 0; i < hudMods.size(); i++) {
            Module m = hudMods.get(i);
            int col = i % cols;
            int row = i / cols;
            int cx = x + col * (cellW + gap);
            int rawCy = ry + row * rowStep;
            boolean keySel = !mouseUnlocked && (i == selectedIndex);
            renderModuleCardCell(g, m, cx, rawCy, cellW, cellH, y, h, mx, my, mouseUnlocked, keySel, accent);
        }

        int rows = (hudMods.size() + cols - 1) / cols;
        ry += rows * rowStep + 4;

        int contentH = Math.max(0, ry - startRy);
        maxScroll = Math.max(0.0, contentH - h);
        drawScrollbar(g, x + w - 2, y, h, contentH, accent);
    }

    private static void renderModuleCardCell(GuiGraphics g, Module m, int cx, int rawCy, int cellW, int cellH,
                                             int clipY, int clipH, int mx, int my, boolean mouseUnlocked, boolean keySel, int accent) {
        if (rawCy + cellH < clipY || rawCy > clipY + clipH) return;

        boolean hovered = mouseUnlocked && !hasModalOpen() && openDropdown == null
                && mx >= cx && mx <= cx + cellW && my >= Math.max(clipY, rawCy) && my <= Math.min(clipY + clipH, rawCy + cellH);
        float hovAnim = AppleUiRenderer.animChannel("mhov_" + m.getId(), (hovered || keySel) ? 1.0f : 0.0f, 18.0f);
        float actAnim = AppleUiRenderer.animChannel("mact_" + m.getId(), m.isEnabled() ? 1.0f : 0.0f, 16.0f);
        int cy = rawCy - Math.round(hovAnim * 1.0f);

        AppleUiRenderer.drawElevatedCard(g, cx, cy, cellW, cellH, 7, actAnim, hovAnim, accent);
        if (keySel) {
            AppleUiRenderer.drawRoundedOutline(g, cx, cy, cellW, cellH, 7, 1.6f, 0xFFFFFFFF);
        }

        String rawName = ConfigManager.english ? m.getSubtitle() : m.getName();
        int txtCol = (m.isEnabled() && !AppleUiRenderer.isMechanical()) ? 0xFFFFFFFF : AppleUiRenderer.textPrimary();

        if (!mouseUnlocked) {
            // Compact Arrow-UI card
            String name = YeHdFont.clipToWidth(rawName, 8.2f, true, cellW - 8);
            YeHdFont.drawCenteredInBox(g, name, cx, cy, cellW, cellH, 8.2f, txtCol, false, true);
            return;
        }

        // Large Mouse-UI card with Crisp Vector Icon + Optical Centered Name & Subtitle + Hover Favorite/Pin Micro-Actions!
        int iconCol = (m.isEnabled() && !AppleUiRenderer.isMechanical()) ? 0xEEFFFFFF : accent;
        AppleUiRenderer.drawModuleIcon(g, m, cx + 8, cy + 8, 11, iconCol);

        String name = YeHdFont.clipToWidth(rawName, 8.9f, true, cellW - 38);
        YeHdFont.draw(g, name, cx + 23, cy + 7.0f, 8.9f, txtCol, false, true);

        String sub = m.getKeybind() > 0 ? ("[" + KeyCombo.format(m.getKeybind()) + "]")
                : (m.isEnabled() ? ConfigManager.tr("开启", "On") : ConfigManager.tr("关闭", "Off"));
        int subCol = (m.isEnabled() && !AppleUiRenderer.isMechanical()) ? 0xD8FFFFFF : AppleUiRenderer.textTertiary();
        YeHdFont.draw(g, sub, cx + 23, cy + 22.0f, 7.7f, subCol, false, false);

        // Clean full-card hit rect with exact clipY + clipH bottom bound (fixes bottom 2 rows clickability!)
        addClippedHit(cx, rawCy, cellW, cellH, clipY, clipY + clipH,
                () -> {
                    playClick();
                    m.toggle();
                    ConfigManager.saveAsync();
                },
                () -> openModuleDetail(m),
                null, null);
    }

    private static void openModuleDetail(Module m) {
        playClick();
        if (activeSection == TopSection.FEATURES) savedFeaturesScroll = targetScroll;
        else if (activeSection == TopSection.HUD) savedHudScroll = targetScroll;
        activeModule = m;
        closeOverlays();
        pageSlide = 14.0f;
        selectedIndex = 0;
        scrollOffset = 0.0;
        targetScroll = 0.0;
    }

    // ==================== 3. MODULE DETAIL INSPECTOR (UPPER FUNCTIONAL / LOWER DISPLAY & COLOR) ====================

    private static void renderModuleDetail(GuiGraphics g, Font font, int x, int y, int w, int h,
                                           int mx, int my, boolean mouseUnlocked, int accent) {
        boolean backHov = mouseUnlocked && inBounds(mx, my, x, y, 28, 18);
        AppleUiRenderer.drawElevatedCard(g, x, y, 28, 18, 5, 0f, backHov ? 1f : 0f, accent);
        YeHdFont.drawCenteredInBox(g, "←", x, y, 28, 18, 9.2f, AppleUiRenderer.textPrimary(), false, true);
        HIT_RECTS.add(new HitRect(x, y, 28, 18, () -> {
            playClick();
            activeModule = null;
            closeOverlays();
            pageSlide = -14.0f;
            selectedIndex = 0;
            double restore = (activeSection == TopSection.FEATURES) ? savedFeaturesScroll
                    : (activeSection == TopSection.HUD ? savedHudScroll : 0.0);
            scrollOffset = restore;
            targetScroll = restore;
        }, null, null, 2));

        String mName = ConfigManager.english ? activeModule.getSubtitle() : activeModule.getName();
        AppleUiRenderer.drawBoldText(g, mName, x + 34, YeHdFont.vCenterY(y, 18, 9.5f), 9.5f, AppleUiRenderer.textPrimary());

        int rightCursor = x + w - 34;

        // Enable Switch at far right
        float mSwitch = AppleUiRenderer.animChannel("dsw_" + activeModule.getId(), activeModule.isEnabled() ? 1f : 0f, 18f);
        AppleUiRenderer.drawPillSwitch(g, x + w - 30, y + 2, 30, 15, mSwitch, accent);
        HIT_RECTS.add(new HitRect(x + w - 30, y + 2, 30, 15, () -> {
            playClick();
            activeModule.toggle();
            ConfigManager.saveAsync();
        }, null, null, 2));

        if (mouseUnlocked) {
            // 1) Module Keybind Pill (Single or Combo!)
            String kbId = "mod_kb_" + activeModule.getId();
            boolean listeningKb = kbId.equals(activeKeybindId);
            String kbText = listeningKb ? liveBindPrompt()
                    : (ConfigManager.tr("键: ", "Key: ") + KeyCombo.format(activeModule.getKeybind()));
            int kbW = Math.max(46, YeHdFont.width(kbText, 8.0f, false) + 12);
            rightCursor -= (kbW + 5);
            int kbX = rightCursor;
            AppleUiRenderer.drawRoundedRect(g, kbX, y + 1, kbW, 16, 5, listeningKb ? 0xFFEF4444 : AppleUiRenderer.bgCardHover());
            YeHdFont.drawCenteredInBox(g, kbText, kbX, y + 1, kbW, 16, 8.0f, listeningKb ? 0xFFFFFFFF : AppleUiRenderer.textSecondary(), false, false);
            HIT_RECTS.add(new HitRect(kbX, y + 1, kbW, 16,
                    () -> startKeybindListen(kbId, activeModule::setKeybind),
                    () -> { playClick(); activeModule.setKeybind(-1); stopKeybindListen(); }, null, 2));

            // 2) Quick Preset Cycle Key Pill (for Wings, Filters, Shaders, Sky, etc.!)
            if (activeModule.hasCycleablePreset()) {
                String cycId = "mod_cyc_" + activeModule.getId();
                boolean listeningCyc = cycId.equals(activeKeybindId);
                String cycTxt = listeningCyc ? liveBindPrompt()
                        : (ConfigManager.tr("切效: ", "Next: ") + KeyCombo.format(activeModule.getPresetCycleKey()));
                int cycW = Math.max(48, YeHdFont.width(cycTxt, 8.0f, false) + 12);
                rightCursor -= (cycW + 5);
                int cycX = rightCursor;
                AppleUiRenderer.drawRoundedRect(g, cycX, y + 1, cycW, 16, 5, listeningCyc ? 0xFFEF4444 : AppleUiRenderer.bgCardHover());
                YeHdFont.drawCenteredInBox(g, cycTxt, cycX, y + 1, cycW, 16, 8.0f, listeningCyc ? 0xFFFFFFFF : AppleUiRenderer.textSecondary(), false, false);
                HIT_RECTS.add(new HitRect(cycX, y + 1, cycW, 16,
                        () -> startKeybindListen(cycId, activeModule::setPresetCycleKey),
                        () -> { playClick(); activeModule.setPresetCycleKey(-1); stopKeybindListen(); }, null, 2));
            }

            // 3) Reset Button (with Confirmation Modal!)
            String rstTxt = ConfigManager.tr("重置", "Reset");
            int rstW = Math.max(34, YeHdFont.width(rstTxt, 8.0f, false) + 12);
            rightCursor -= (rstW + 5);
            int rstX = rightCursor;
            boolean rstHov = inBounds(mx, my, rstX, y + 1, rstW, 16);
            AppleUiRenderer.drawRoundedRect(g, rstX, y + 1, rstW, 16, 5, rstHov ? 0xFFE11D48 : AppleUiRenderer.bgCardHover());
            YeHdFont.drawCenteredInBox(g, rstTxt, rstX, y + 1, rstW, 16, 8.0f, rstHov ? 0xFFFFFFFF : AppleUiRenderer.textSecondary(), false, false);
            HIT_RECTS.add(new HitRect(rstX, y + 1, rstW, 16, () -> {
                playClick();
                Module target = activeModule;
                requestConfirmation(ConfigManager.tr("确定将「" + mName + "」重置为默认参数吗？", "Reset " + mName + " to default?"), () -> {
                    target.resetToDefault();
                    openDropdown = null;
                    showToast(ConfigManager.tr("已恢复默认: " + mName, "Reset " + mName), accent);
                });
            }, null, null, 2));

            // 4) Favorite & Pin quick toggles
            boolean fav = ConfigManager.isFavorite(activeModule);
            rightCursor -= 22;
            int favX = rightCursor;
            AppleUiRenderer.drawRoundedRect(g, favX, y + 1, 18, 16, 5, AppleUiRenderer.bgCardHover());
            YeHdFont.drawCenteredInBox(g, "★", favX, y + 1, 18, 16, 8.4f, fav ? 0xFFFACC15 : AppleUiRenderer.textTertiary(), false, true);
            HIT_RECTS.add(new HitRect(favX, y + 1, 18, 16, () -> { playClick(); ConfigManager.toggleFavorite(activeModule); }, null, null, 2));

            boolean pin = ConfigManager.isPinned(activeModule);
            rightCursor -= 22;
            int pinX = rightCursor;
            AppleUiRenderer.drawRoundedRect(g, pinX, y + 1, 18, 16, 5, AppleUiRenderer.bgCardHover());
            YeHdFont.drawCenteredInBox(g, "▲", pinX, y + 1, 18, 16, 8.0f, pin ? 0xFF38BDF8 : AppleUiRenderer.textTertiary(), false, true);
            HIT_RECTS.add(new HitRect(pinX, y + 1, 18, 16, () -> { playClick(); ConfigManager.togglePinned(activeModule); }, null, null, 2));
        }

        int listY = y + 22;
        int listH = h - 22;
        int rowW = w - 6;

        List<Setting<?>> rawSettings = getDisplaySettings(activeModule);
        List<Setting<?>> funcSettings = new ArrayList<>();
        List<Setting<?>> dispSettings = new ArrayList<>();
        List<Setting<?>> flatNavSettings = new ArrayList<>();

        for (Setting<?> s : rawSettings) {
            if ((activeModule instanceof CustomCapeModule && s.getId().equals("selectedCape"))
                    || (activeModule instanceof CustomSkinModule && s.getId().equals("selectedSkin"))) {
                continue;
            }
            if (s.isDisplayGroup()) dispSettings.add(s);
            else funcSettings.add(s);
        }
        flatNavSettings.addAll(funcSettings);
        flatNavSettings.addAll(dispSettings);
        if (selectedIndex >= flatNavSettings.size()) selectedIndex = Math.max(0, flatNavSettings.size() - 1);

        g.enableScissor(x - 1, listY, x + w + 1, listY + listH);
        int startRy = listY - (int) Math.round(scrollOffset);
        int ry = startRy;

        if (mouseUnlocked) {
            ry += YePreviewRenderer.renderModulePreviewCard(g, font, activeModule, x, ry, rowW);
        }

        // Cape, Skin, BlockSearch, StashRadar Hierarchy & AutoLog Condition-Flow Cards
        if (activeModule instanceof CustomCapeModule cm) {
            ry = renderCapeGallery(g, cm, x, ry, rowW, listY, listH, mx, my, mouseUnlocked, accent);
        } else if (activeModule instanceof CustomSkinModule sm) {
            if (mouseUnlocked) {
                ry = renderOnlineSkinSearchBar(g, sm, x, ry, rowW, listY, listH, accent);
            }
            ry = renderSkinGallery(g, sm, x, ry, rowW, listY, listH, mx, my, mouseUnlocked, accent);
        } else if (activeModule instanceof BlockSearchModule bsm && mouseUnlocked) {
            ry = renderBlockSearchResultsPanel(g, bsm, x, ry, rowW, listY, listH, mx, my, accent);
        } else if (activeModule instanceof com.ye.mod.modules.world.StashRadarModule sr && mouseUnlocked) {
            ry = renderStashRadarHierarchy(g, sr, x, ry, rowW, listY, listH, mx, my, accent);
            // Only render global non-per-container settings cleanly below
            List<Setting<?>> globals = funcSettings.stream()
                    .filter(s -> !(s == sr.editingKind) && !s.getId().contains("_"))
                    .toList();
            if (!globals.isEmpty()) {
                ry = drawSettingSectionHeader(g, ConfigManager.tr("全局过滤与范围", "Global Filter & Range"), x, ry, rowW, listY, listH, accent);
                ry = renderSettingsBlock(g, globals, flatNavSettings, x, ry, rowW, listY, listH, mx, my, true, accent);
            }
            funcSettings.clear();
        } else if (activeModule instanceof com.ye.mod.modules.world.AutoLogModule al && mouseUnlocked) {
            ry = renderAutoLogConditionCards(g, al, x, ry, rowW, listY, listH, mx, my, accent);
            funcSettings.clear();
            dispSettings.clear();
        }

        // Upper Section: 功能参数
        if (!funcSettings.isEmpty()) {
            if (mouseUnlocked && !dispSettings.isEmpty()) {
                ry = drawSettingSectionHeader(g, ConfigManager.tr("功能设置", "Function Settings"), x, ry, rowW, listY, listH, accent);
            }
            ry = renderSettingsBlock(g, funcSettings, flatNavSettings, x, ry, rowW, listY, listH, mx, my, mouseUnlocked, accent);
        }

        // Lower Section: 显示与色彩
        if (!dispSettings.isEmpty()) {
            if (mouseUnlocked && !funcSettings.isEmpty()) {
                ry += 3;
                ry = drawSettingSectionHeader(g, ConfigManager.tr("显示与色彩", "Display & Color"), x, ry, rowW, listY, listH, 0xFFA855F7);
            }
            ry = renderSettingsBlock(g, dispSettings, flatNavSettings, x, ry, rowW, listY, listH, mx, my, mouseUnlocked, accent);
        }

        int contentH = (ry - startRy) + 4;
        maxScroll = Math.max(0.0, contentH - listH);
        drawScrollbar(g, x + w - 2, listY, listH, contentH, accent);
        g.disableScissor();
    }

    private static int drawSettingSectionHeader(GuiGraphics g, String label, int x, int ry, int rowW, int clipY, int clipH, int barCol) {
        if (ry + 15 >= clipY && ry <= clipY + clipH) {
            AppleUiRenderer.drawRoundedRect(g, x + 1, ry + 2, 2.8f, 9.5f, 1.4f, barCol);
            YeHdFont.draw(g, label, x + 7, YeHdFont.vCenterY(ry, 14, 8.2f), 8.2f, AppleUiRenderer.textSecondary(), false, true);
            int tw = YeHdFont.width(label, 8.2f, true);
            g.fill(x + 12 + tw, ry + 7, x + rowW - 4, ry + 8, AppleUiRenderer.borderSubtle());
        }
        return ry + 15;
    }

    private static int renderSettingsBlock(GuiGraphics g, List<Setting<?>> group, List<Setting<?>> allNav,
                                           int x, int ry, int rowW, int listY, int listH,
                                           int mx, int my, boolean mouseUnlocked, int accent) {
        boolean twoCol = mouseUnlocked && group.size() >= 2;
        if (!twoCol) {
            for (Setting<?> s : group) {
                int navIdx = allNav.indexOf(s);
                int cellH = (s instanceof DoubleSetting) ? 28 : 22;
                renderSettingCell(g, s, x, ry, rowW, cellH, listY, listH, mx, my, mouseUnlocked, !mouseUnlocked && navIdx == selectedIndex, accent, false);
                ry += cellH + 4;
            }
        } else {
            int colGap = 6;
            int colW = (rowW - colGap) / 2;
            for (int i = 0; i < group.size(); i += 2) {
                Setting<?> sL = group.get(i);
                Setting<?> sR = (i + 1 < group.size()) ? group.get(i + 1) : null;
                int hL = (sL instanceof DoubleSetting) ? 28 : 23;
                int hR = (sR instanceof DoubleSetting) ? 28 : 23;
                int rowH = (sR == null) ? hL : Math.max(hL, hR);

                if (sR == null) {
                    renderSettingCell(g, sL, x, ry, rowW, rowH, listY, listH, mx, my, true, false, accent, false);
                } else {
                    renderSettingCell(g, sL, x, ry, colW, rowH, listY, listH, mx, my, true, false, accent, true);
                    renderSettingCell(g, sR, x + colW + colGap, ry, colW, rowH, listY, listH, mx, my, true, false, accent, true);
                }
                ry += rowH + 4;
            }
        }
        return ry;
    }

    private static void renderSettingCell(GuiGraphics g, Setting<?> s, int cx, int cy, int cw, int ch,
                                          int clipY, int clipH, int mx, int my, boolean mouseUnlocked,
                                          boolean keySel, int accent, boolean compactCol) {
        if (cy + ch < clipY || cy > clipY + clipH) return;

        boolean hov = mouseUnlocked && !hasModalOpen() && openDropdown == null
                && mx >= cx && mx <= cx + cw && my >= Math.max(clipY, cy) && my <= Math.min(clipY + clipH, cy + ch);
        float hProg = AppleUiRenderer.animChannel("shov_" + activeModule.getId() + "_" + s.getId(), (hov || keySel) ? 1f : 0f, 18f);
        AppleUiRenderer.drawElevatedCard(g, cx, cy, cw, ch, 6, 0f, hProg, accent);
        if (keySel) {
            AppleUiRenderer.drawRoundedOutline(g, cx, cy, cw, ch, 6, 1.5f, accent);
        }

        float labelSize = compactCol ? 8.3f : 8.6f;

        // Per-setting crisp circular Reset button on right edge in Mouse UI
        int rstOff = 0;
        if (mouseUnlocked && !(s instanceof ActionSetting)) {
            rstOff = 17;
            int rxBtn = cx + cw - 17;
            int ryBtn = cy + (ch - 12) / 2;
            boolean rHov = inBounds(mx, my, rxBtn, ryBtn, 12, 12);
            AppleUiRenderer.drawResetCircleIcon(g, rxBtn + 1, ryBtn + 1, 10, rHov ? accent : AppleUiRenderer.textTertiary());
            addClippedHit(rxBtn, ryBtn, 13, 13, clipY, clipY + clipH, () -> {
                playClick();
                s.reset();
                ConfigManager.saveAsync();
            }, null, null, null);
        }
        int usableW = cw - rstOff;

        if (s instanceof BoolSetting bs) {
            String lbl = YeHdFont.clipToWidth(s.getName(), labelSize, false, usableW - 40);
            YeHdFont.draw(g, lbl, cx + 7, YeHdFont.vCenterY(cy, ch, labelSize), labelSize, AppleUiRenderer.textPrimary(), false, false);
            float bProg = AppleUiRenderer.animChannel("bsw_" + activeModule.getId() + "_" + bs.getId(), bs.get() ? 1f : 0f, 18f);
            AppleUiRenderer.drawPillSwitch(g, cx + usableW - 33, cy + (ch - 14) / 2, 27, 14, bProg, accent);
            addClippedHit(cx, cy, usableW, ch, clipY, clipY + clipH, () -> {
                playClick();
                openDropdown = null;
                bs.toggle();
                ConfigManager.saveAsync();
            }, null, null, null);
        } else if (s instanceof DoubleSetting ds) {
            String numKey = "ds_" + activeModule.getId() + "_" + ds.getId();
            boolean editingThis = (textInputKind == 5 && numKey.equals(activeNumberKey));
            String vt = editingThis ? (textInputBuffer + "|") : formatDouble(ds.get(), ds.getStep());
            float vtW = Math.max(24f, YeHdFont.widthF(vt, labelSize, true));
            String lbl = YeHdFont.clipToWidth(s.getName(), labelSize, false, usableW - vtW - 18);
            YeHdFont.draw(g, lbl, cx + 7, cy + 3.8f, labelSize, AppleUiRenderer.textPrimary(), false, false);

            int numBoxX = Math.round(cx + usableW - 9 - vtW);
            if (editingThis) {
                AppleUiRenderer.drawRoundedRect(g, numBoxX - 3, cy + 2, vtW + 6, 13, 3, 0xEE080C16);
                AppleUiRenderer.drawRoundedOutline(g, numBoxX - 3, cy + 2, vtW + 6, 13, 3, 1.0f, accent);
            }
            YeHdFont.draw(g, vt, numBoxX, cy + 3.8f, labelSize, editingThis ? 0xFFFFFFFF : accent, false, true);

            // Double-clicking the numeric readout opens inline manual keyboard input!
            addClippedHit(numBoxX - 4, cy + 1, Math.round(vtW) + 8, 14, clipY, clipY + clipH,
                    null, null, null, () -> startNumberEdit(numKey, formatDouble(ds.get(), ds.getStep()), ds::set));

            int bw = usableW - 14;
            int bx = cx + 7;
            int by = cy + ch - 8;
            float targetPct = (float) ((ds.get() - ds.getMin()) / Math.max(0.0001, ds.getMax() - ds.getMin()));
            float pct = AppleUiRenderer.animChannel("dsl_" + activeModule.getId() + "_" + ds.getId(), targetPct, 22f);
            AppleUiRenderer.drawRoundedRect(g, bx, by, bw, 3.6f, 1.8f, AppleUiRenderer.lightTheme ? 0xFFCBD5E1 : 0xFF283142);
            float fillW = Math.max(3.6f, bw * pct);
            AppleUiRenderer.drawRoundedRect(g, bx, by, fillW, 3.6f, 1.8f, accent);
            AppleUiRenderer.drawCircle(g, bx + fillW - 4.0f, by - 2.2f, 8.0f, 0xFFFFFFFF);
            addClippedHit(bx - 3, by - 6, bw + 6, 14, clipY, clipY + clipH, null, null, new SliderBinding() {
                @Override public double getMin() { return ds.getMin(); }
                @Override public double getMax() { return ds.getMax(); }
                @Override public void setValue(double v) { ds.set(v); }
            }, null);
        } else if (s instanceof EnumSetting<?> es) {
            String val = enumLabel(es.get()) + " ▾";
            int maxBtnW = compactCol ? (usableW / 2 + 16) : (usableW / 2 + 28);
            int bw = Math.min(maxBtnW, Math.max(compactCol ? 60 : 74, YeHdFont.width(val, 8.0f, false) + 14));
            int bx = cx + usableW - 5 - bw;
            int by = cy + (ch - 16) / 2;

            String lbl = YeHdFont.clipToWidth(s.getName(), labelSize, false, usableW - bw - 14);
            YeHdFont.draw(g, lbl, cx + 7, YeHdFont.vCenterY(cy, ch, labelSize), labelSize, AppleUiRenderer.textPrimary(), false, false);

            boolean isOpen = (openDropdown == es);
            AppleUiRenderer.drawRoundedRect(g, bx, by, bw, 16, 5, isOpen ? accent : AppleUiRenderer.bgCardHover());
            YeHdFont.drawCenteredInBox(g, YeHdFont.clipToWidth(val, 8.0f, false, bw - 8), bx, by, bw, 16, 8.0f,
                    isOpen ? 0xFFFFFFFF : AppleUiRenderer.textPrimary(), false, false);
            addClippedHit(bx, by, bw, 16, clipY, clipY + clipH, () -> {
                playClick();
                if (openDropdown == es) openDropdown = null;
                else {
                    openDropdown = es;
                    dropScroll = 0.0;
                    dropX = bx;
                    dropY = by + 18;
                    dropW = Math.max(bw, 114);
                }
            }, null, null, null);
        } else if (s instanceof ColorSetting cs) {
            String lbl = YeHdFont.clipToWidth(s.getName(), labelSize, false, usableW - 70);
            YeHdFont.draw(g, lbl, cx + 7, YeHdFont.vCenterY(cy, ch, labelSize), labelSize, AppleUiRenderer.textPrimary(), false, false);
            int bx = cx + usableW - 65;
            int by = cy + (ch - 16) / 2;
            AppleUiRenderer.drawRoundedRect(g, bx, by, 60, 16, 5, AppleUiRenderer.bgCardHover());
            AppleUiRenderer.drawRoundedRect(g, bx + 3, by + 3, 10, 10, 3, cs.getArgb());
            YeHdFont.draw(g, cs.getHex(), bx + 16, YeHdFont.vCenterY(by, 16, 7.6f), 7.6f, AppleUiRenderer.textPrimary(), false, true);
            addClippedHit(bx, by, 60, 16, clipY, clipY + clipH, () -> {
                playClick();
                openThemeCustomColor = false;
                openColorSetting = (openColorSetting == cs) ? null : cs;
                pickerX = bx - 80;
                pickerY = by + 18;
            }, null, null, null);
        } else if (s instanceof KeybindSetting ks) {
            String kid = "ks_" + activeModule.getId() + "_" + ks.getId();
            boolean listening = kid.equals(activeKeybindId);
            String kVal = listening ? liveBindPrompt() : ks.formatKey();
            int bw = Math.max(48, YeHdFont.width(kVal, 8.0f, false) + 12);
            int bx = cx + usableW - 5 - bw;
            int by = cy + (ch - 16) / 2;
            String lbl = YeHdFont.clipToWidth(s.getName(), labelSize, false, usableW - bw - 14);
            YeHdFont.draw(g, lbl, cx + 7, YeHdFont.vCenterY(cy, ch, labelSize), labelSize, AppleUiRenderer.textPrimary(), false, false);
            AppleUiRenderer.drawRoundedRect(g, bx, by, bw, 16, 5, listening ? 0xFFEF4444 : AppleUiRenderer.bgCardHover());
            YeHdFont.drawCenteredInBox(g, kVal, bx, by, bw, 16, 8.0f, listening ? 0xFFFFFFFF : accent, false, true);
            addClippedHit(bx, by, bw, 16, clipY, clipY + clipH,
                    () -> startKeybindListen(kid, ks::set),
                    () -> { playClick(); ks.set(-1); stopKeybindListen(); }, null, null);
        } else if (s instanceof StringSetting ss) {
            boolean editing = (textInputKind == 4 && activeStringSetting == ss);
            String lbl = YeHdFont.clipToWidth(s.getName(), labelSize, false, usableW * 0.42f);
            YeHdFont.draw(g, lbl, cx + 7, YeHdFont.vCenterY(cy, ch, labelSize), labelSize, AppleUiRenderer.textPrimary(), false, false);
            int bx = cx + Math.round(usableW * 0.44f);
            int bw = usableW - Math.round(usableW * 0.44f) - 5;
            int by = cy + (ch - 16) / 2;
            AppleUiRenderer.drawRoundedRect(g, bx, by, bw, 16, 4, 0xCC090D16);
            AppleUiRenderer.drawRoundedOutline(g, bx, by, bw, 16, 4, 1.0f, editing ? accent : AppleUiRenderer.borderSubtle());
            String txt = editing ? (textInputBuffer + "|") : ss.get();
            YeHdFont.draw(g, YeHdFont.clipToWidth(txt, 7.9f, false, bw - 8), bx + 5, YeHdFont.vCenterY(by, 16, 7.9f), 7.9f, 0xFFF8FAFC, false, false);
            addClippedHit(bx, by, bw, 16, clipY, clipY + clipH, () -> {
                playClick();
                commitInlineInputIfNumber();
                textInputKind = 4;
                activeStringSetting = ss;
                textInputBuffer = ss.get();
            }, null, null, null);
        } else if (s instanceof ActionSetting as) {
            String lbl = YeHdFont.clipToWidth(s.getName(), labelSize, false, cw - 54);
            YeHdFont.draw(g, lbl, cx + 7, YeHdFont.vCenterY(cy, ch, labelSize), labelSize, AppleUiRenderer.textPrimary(), false, false);
            int bx = cx + cw - 47;
            int by = cy + (ch - 16) / 2;
            AppleUiRenderer.drawRoundedRect(g, bx, by, 42, 16, 5, accent);
            YeHdFont.drawCenteredInBox(g, ConfigManager.tr("打开", "Open"), bx, by, 42, 16, 8.0f, 0xFFFFFFFF, false, true);
            addClippedHit(bx, by, 42, 16, clipY, clipY + clipH, () -> {
                playClick();
                as.trigger();
            }, null, null, null);
        }
    }

    private static int renderCapeGallery(GuiGraphics g, CustomCapeModule cm, int x, int ry, int rowW, int listY, int listH,
                                         int mx, int my, boolean mouseUnlocked, int accent) {
        List<CapeManager.CapeEntry> capes = CapeManager.getAllCapes();
        int cols = mouseUnlocked ? 3 : 2;
        int gap = 5;
        int colW = (rowW - (cols - 1) * gap) / cols;
        for (int i = 0; i < capes.size(); i++) {
            CapeManager.CapeEntry ce = capes.get(i);
            int cx = x + (i % cols) * (colW + gap);
            int cy = ry + (i / cols) * 23;
            if (cy + 20 >= listY && cy <= listY + listH) {
                boolean sel = ce.id().equals(cm.selectedCape.get());
                boolean hov = mouseUnlocked && inBounds(mx, my, cx, cy, colW, 20);
                AppleUiRenderer.drawElevatedCard(g, cx, cy, colW, 20, 5, sel ? 1f : 0f, hov ? 1f : 0f, accent);
                g.blit(RenderPipelines.GUI_TEXTURED, ce.textureId(), cx + 5, cy + 2, 1.0f, 1.0f, 10, 16, 10, 16, 64, 32);
                YeHdFont.draw(g, YeHdFont.clipToWidth(ce.displayName(), 8.2f, sel, colW - 22), cx + 19, YeHdFont.vCenterY(cy, 20, 8.2f), 8.2f,
                        sel ? 0xFFFFFFFF : AppleUiRenderer.textPrimary(), false, sel);
                addClippedHit(cx, cy, colW, 20, listY, listY + listH, () -> {
                    playClick();
                    cm.selectedCape.set(ce.id());
                    cm.setEnabled(true);
                    ConfigManager.saveAsync();
                }, null, null, null);
            }
        }
        return ry + ((capes.size() + cols - 1) / cols) * 23 + 4;
    }

    private static int renderSkinGallery(GuiGraphics g, CustomSkinModule sm, int x, int ry, int rowW, int listY, int listH,
                                         int mx, int my, boolean mouseUnlocked, int accent) {
        List<SkinManager.SkinEntry> skins = SkinManager.getAllSkins();
        int cols = mouseUnlocked ? 4 : 3;
        int gap = 4;
        int colW = (rowW - (cols - 1) * gap) / cols;
        for (int i = 0; i < skins.size(); i++) {
            SkinManager.SkinEntry se = skins.get(i);
            int cx = x + (i % cols) * (colW + gap);
            int cy = ry + (i / cols) * 23;
            if (cy + 20 >= listY && cy <= listY + listH) {
                boolean sel = se.id().equals(sm.selectedSkin.get());
                boolean hov = mouseUnlocked && inBounds(mx, my, cx, cy, colW, 20);
                AppleUiRenderer.drawElevatedCard(g, cx, cy, colW, 20, 5, sel ? 1f : 0f, hov ? 1f : 0f, accent);
                g.blit(RenderPipelines.GUI_TEXTURED, se.textureId(), cx + 4, cy + 2, 8.0f, 8.0f, 16, 16, 8, 8, 64, 64);
                g.blit(RenderPipelines.GUI_TEXTURED, se.textureId(), cx + 4, cy + 2, 40.0f, 8.0f, 16, 16, 8, 8, 64, 64);
                YeHdFont.draw(g, YeHdFont.clipToWidth(se.displayName(), 7.9f, sel, colW - 24), cx + 23, YeHdFont.vCenterY(cy, 20, 7.9f), 7.9f,
                        sel ? 0xFFFFFFFF : AppleUiRenderer.textPrimary(), false, sel);
                addClippedHit(cx, cy, colW, 20, listY, listY + listH, () -> {
                    playClick();
                    sm.selectedSkin.set(se.id());
                    sm.setEnabled(true);
                    ConfigManager.saveAsync();
                }, null, null, null);
            }
        }
        return ry + ((skins.size() + cols - 1) / cols) * 23 + 4;
    }

    private static int renderBlockSearchResultsPanel(GuiGraphics g, BlockSearchModule bsm, int x, int ry, int rowW,
                                                     int listY, int listH, int mx, int my, int accent) {
        List<BlockSearchModule.SearchResult> res = bsm.getResults();
        if (res.isEmpty()) return ry;

        ry = drawSettingSectionHeader(g, "实时搜索坐标结果 (" + res.size() + " 处)", x, ry, rowW, listY, listH, 0xFF22D3EE);
        int showCount = Math.min(24, res.size());
        for (int i = 0; i < showCount; i++) {
            BlockSearchModule.SearchResult r = res.get(i);
            if (ry + 19 >= listY && ry <= listY + listH) {
                boolean pinned = r.pos().equals(bsm.getPinnedTarget());
                AppleUiRenderer.drawElevatedCard(g, x, ry, rowW, 18, 4, pinned ? 1f : 0f, 0f, accent);
                String line = String.format(Locale.ROOT, "%s  [%d, %d, %d]  %.1fm",
                        r.blockName(), r.pos().getX(), r.pos().getY(), r.pos().getZ(), r.distance());
                YeHdFont.draw(g, line, x + 7, YeHdFont.vCenterY(ry, 18, 8.0f), 8.0f, 0xFFF8FAFC, false, pinned);

                int pinBtnX = x + rowW - 96;
                AppleUiRenderer.drawRoundedRect(g, pinBtnX, ry + 2, 42, 14, 3.5f, AppleUiRenderer.bgCardHover());
                YeHdFont.drawCenteredInBox(g, pinned ? "已锁定" : "追踪", pinBtnX, ry + 2, 42, 14, 7.6f, accent, false, true);
                addClippedHit(pinBtnX, ry + 2, 42, 14, listY, listY + listH, () -> {
                    playClick();
                    bsm.setPinnedTarget(pinned ? null : r.pos());
                }, null, null, null);

                int gotoBtnX = x + rowW - 50;
                AppleUiRenderer.drawRoundedRect(g, gotoBtnX, ry + 2, 46, 14, 3.5f, accent);
                YeHdFont.drawCenteredInBox(g, "前往", gotoBtnX, ry + 2, 46, 14, 7.6f, 0xFFFFFFFF, false, true);
                addClippedHit(gotoBtnX, ry + 2, 46, 14, listY, listY + listH, () -> {
                    playClick();
                    bsm.navigateBaritoneTo(r.pos());
                }, null, null, null);
            }
            ry += 20;
        }
        return ry + 4;
    }

    // ==================== 4. SETTINGS TAB (THEME & COLOR AT TOP, CONFIG AT BOTTOM!) ====================

    private static void renderSettingsTab(GuiGraphics g, int x, int y, int w, int h,
                                          int mx, int my, boolean mouseUnlocked, int accent) {
        int startRy = y - (int) Math.round(scrollOffset);
        int ry = startRy;
        int rowW = w - 6;
        int halfW = (rowW - 6) / 2;
        int rxCol = x + halfW + 6;

        // ---- TOP: APPEARANCE, THEME COLOR & SOUND ----
        ry = drawSettingSectionHeader(g, ConfigManager.tr("界面风格、主题色与音效", "Style, Theme Color & Sound"), x, ry, rowW, y, h, accent);

        // Row 1: UI Style Persona (丝滑风 vs 机械风) + Material Theme (深色 / 浅色 / 高斯玻璃)
        AppleUiRenderer.drawElevatedCard(g, x, ry, halfW, 22, 6, 0f, (mouseUnlocked && inBounds(mx, my, x, ry, halfW, 22)) ? 1f : 0f, accent);
        YeHdFont.draw(g, ConfigManager.tr("界面流派", "UI Style"), x + 8, YeHdFont.vCenterY(ry, 22, 8.5f), 8.5f, AppleUiRenderer.textPrimary(), false, false);
        String stLbl = ConfigManager.english ? ConfigManager.uiStyle.en : ConfigManager.uiStyle.zh;
        AppleUiRenderer.drawRoundedRect(g, x + halfW - 56, ry + 3, 50, 16, 5, accent);
        YeHdFont.drawCenteredInBox(g, stLbl, x + halfW - 56, ry + 3, 50, 16, 8.1f, 0xFFFFFFFF, false, true);
        addClippedHit(x, ry, halfW, 22, y, y + h, () -> {
            playClick();
            ConfigManager.uiStyle = (ConfigManager.uiStyle == ConfigManager.UiStyle.SILKY)
                    ? ConfigManager.UiStyle.MECHANICAL : ConfigManager.UiStyle.SILKY;
            ConfigManager.saveAsync();
        }, null, null, null);

        AppleUiRenderer.drawElevatedCard(g, rxCol, ry, halfW, 22, 6, 0f, (mouseUnlocked && inBounds(mx, my, rxCol, ry, halfW, 22)) ? 1f : 0f, accent);
        YeHdFont.draw(g, ConfigManager.tr("外观主题", "Theme"), rxCol + 8, YeHdFont.vCenterY(ry, 22, 8.5f), 8.5f, AppleUiRenderer.textPrimary(), false, false);
        String thLbl = ConfigManager.english ? ConfigManager.uiTheme.en : ConfigManager.uiTheme.zh;
        AppleUiRenderer.drawRoundedRect(g, rxCol + halfW - 60, ry + 3, 54, 16, 5, accent);
        YeHdFont.drawCenteredInBox(g, thLbl, rxCol + halfW - 60, ry + 3, 54, 16, 8.1f, 0xFFFFFFFF, false, true);
        addClippedHit(rxCol, ry, halfW, 22, y, y + h, () -> { playClick(); AppleUiRenderer.cycleUiTheme(); }, null, null, null);
        ry += 26;

        // Row 2: Panel Opacity Slider (with double-click exact input!) + Font Switch (自带字体 vs 客户端字体)
        renderCustomSliderCard(g, x, ry, halfW, ConfigManager.tr("面板不透明度", "UI Opacity"), "ui_opacity",
                Math.round(ConfigManager.uiOpacity * 100) + "%",
                ConfigManager.uiOpacity, 0.25, 1.00,
                v -> { ConfigManager.uiOpacity = (float) Math.max(0.25, Math.min(1.0, v)); ConfigManager.saveAsync(); },
                vPct -> { ConfigManager.uiOpacity = (float) Math.max(0.25, Math.min(1.0, vPct > 1.5 ? vPct / 100.0 : vPct)); ConfigManager.saveAsync(); },
                y, h, mx, my, mouseUnlocked, accent);

        AppleUiRenderer.drawElevatedCard(g, rxCol, ry, halfW, 28, 6, 0f, (mouseUnlocked && inBounds(mx, my, rxCol, ry, halfW, 28)) ? 1f : 0f, accent);
        YeHdFont.draw(g, ConfigManager.tr("界面字体", "UI Font"), rxCol + 8, YeHdFont.vCenterY(ry, 28, 8.5f), 8.5f, AppleUiRenderer.textPrimary(), false, false);
        String fntLbl = ConfigManager.useClientFont ? ConfigManager.tr("客户端字体", "Client") : ConfigManager.tr("自带字体", "Built-in");
        AppleUiRenderer.drawRoundedRect(g, rxCol + halfW - 64, ry + 6, 58, 16, 5, accent);
        YeHdFont.drawCenteredInBox(g, fntLbl, rxCol + halfW - 64, ry + 6, 58, 16, 8.0f, 0xFFFFFFFF, false, true);
        addClippedHit(rxCol, ry, halfW, 28, y, y + h, () -> {
            playClick();
            ConfigManager.useClientFont = !ConfigManager.useClientFont;
            ConfigManager.saveAsync();
        }, null, null, null);
        ry += 32;

        // Row 2.5: Total Background Blur Strength (for Glass, Dark, Light) + RusherHack Right Sidebar Toggle
        renderCustomSliderCard(g, x, ry, halfW, ConfigManager.tr("总背景模糊度", "BG Blur"), "ui_blur_str",
                Math.round(ConfigManager.uiBlurStrength * 100) + "%",
                ConfigManager.uiBlurStrength, 0.0, 1.0,
                v -> { ConfigManager.uiBlurStrength = (float) Math.max(0.0, Math.min(1.0, v)); ConfigManager.saveAsync(); },
                vPct -> { ConfigManager.uiBlurStrength = (float) Math.max(0.0, Math.min(1.0, vPct > 1.5 ? vPct / 100.0 : vPct)); ConfigManager.saveAsync(); },
                y, h, mx, my, mouseUnlocked, accent);

        AppleUiRenderer.drawElevatedCard(g, rxCol, ry, halfW, 28, 6, 0f, (mouseUnlocked && inBounds(mx, my, rxCol, ry, halfW, 28)) ? 1f : 0f, accent);
        YeHdFont.draw(g, ConfigManager.tr("右侧功能/HUD栏", "Right Switcher"), rxCol + 8, YeHdFont.vCenterY(ry, 28, 8.5f), 8.5f, AppleUiRenderer.textPrimary(), false, false);
        float rsAnim = AppleUiRenderer.animChannel("rs_bar_sw", ConfigManager.rusherSidebar ? 1f : 0f, 18f);
        AppleUiRenderer.drawPillSwitch(g, rxCol + halfW - 34, ry + 7, 27, 14, rsAnim, accent);
        addClippedHit(rxCol, ry, halfW, 28, y, y + h, () -> {
            playClick();
            ConfigManager.rusherSidebar = !ConfigManager.rusherSidebar;
            ConfigManager.saveAsync();
        }, null, null, null);
        ry += 32;

        // Theme Color Swatches + Draggable Custom Picker Button
        ConfigManager.ThemeAccent[] accents = ConfigManager.ThemeAccent.values();
        int cw = (rowW - (accents.length - 1) * 5) / accents.length;
        for (int i = 0; i < accents.length; i++) {
            ConfigManager.ThemeAccent ta = accents[i];
            int cx = x + i * (cw + 5);
            int swArgb = (ta == ConfigManager.ThemeAccent.CUSTOM) ? ConfigManager.getCustomAccentArgb() : ta.argb;
            boolean sel = ConfigManager.themeAccent == ta;
            boolean hov = mouseUnlocked && inBounds(mx, my, cx, ry, cw, 22);
            AppleUiRenderer.drawElevatedCard(g, cx, ry, cw, 22, 6, sel ? 1f : 0f, hov ? 1f : 0f, swArgb);
            if (!sel) {
                AppleUiRenderer.drawCircle(g, cx + 6, ry + 6, 10, swArgb);
            }
            String label = ConfigManager.english ? ta.en : ta.zh;
            YeHdFont.drawCenteredInBox(g, label, cx + (sel ? 0 : 8), ry, cw - (sel ? 0 : 8), 22, 8.2f,
                    sel ? 0xFFFFFFFF : AppleUiRenderer.textPrimary(), false, sel);
            int finalRy = ry;
            addClippedHit(cx, ry, cw, 22, y, y + h, () -> {
                playClick();
                ConfigManager.themeAccent = ta;
                if (ta == ConfigManager.ThemeAccent.CUSTOM) {
                    openColorSetting = null;
                    openThemeCustomColor = !openThemeCustomColor;
                    pickerX = Math.max(x, cx - 70);
                    pickerY = finalRy + 24;
                } else {
                    openThemeCustomColor = false;
                }
                ConfigManager.saveAsync();
            }, null, null, null);
        }
        ry += 27;

        // Row 3: UI Button Sound (4 Checked Options!) + UI Sound Volume Slider (0% ~ 125% + Double-Click Input!)
        AppleUiRenderer.drawElevatedCard(g, x, ry, halfW, 28, 6, 0f, (mouseUnlocked && inBounds(mx, my, x, ry, halfW, 28)) ? 1f : 0f, accent);
        YeHdFont.draw(g, ConfigManager.tr("按键音效", "Click Sound"), x + 8, YeHdFont.vCenterY(ry, 28, 8.5f), 8.5f, AppleUiRenderer.textPrimary(), false, false);
        String sndLbl = enumLabel(ConfigManager.uiClickSound) + " ▾";
        int sbw = Math.min(halfW - 64, Math.max(76, YeHdFont.width(sndLbl, 8.0f, false) + 12));
        int sbx = x + halfW - 5 - sbw;
        AppleUiRenderer.drawRoundedRect(g, sbx, ry + 6, sbw, 16, 5, soundDropdownOpen ? accent : AppleUiRenderer.bgCardHover());
        YeHdFont.drawCenteredInBox(g, sndLbl, sbx, ry + 6, sbw, 16, 8.0f, soundDropdownOpen ? 0xFFFFFFFF : AppleUiRenderer.textPrimary(), false, false);
        int finalSndRy = ry;
        addClippedHit(sbx, ry + 6, sbw, 16, y, y + h, () -> {
            soundDropdownOpen = !soundDropdownOpen;
            soundDropX = sbx;
            soundDropY = finalSndRy + 24;
            soundDropW = Math.max(sbw, 106);
            playClick();
        }, null, null, null);

        renderCustomSliderCard(g, rxCol, ry, halfW, ConfigManager.tr("按键音量", "Sound Vol"), "ui_snd_vol",
                Math.round(ConfigManager.uiSoundVolume * 100) + "%",
                ConfigManager.uiSoundVolume, 0.0, 1.25,
                v -> { ConfigManager.uiSoundVolume = (float) Math.max(0.0, Math.min(1.25, v)); ConfigManager.saveAsync(); },
                vPct -> { ConfigManager.uiSoundVolume = (float) Math.max(0.0, Math.min(1.25, vPct > 1.5 ? vPct / 100.0 : vPct)); playClick(); ConfigManager.saveAsync(); },
                y, h, mx, my, mouseUnlocked, accent);
        ry += 32;

        // Row 4: Arrow UI Key + Mouse UI Key (Combo & Single supported!)
        renderGlobalKeybindCard(g, x, ry, halfW, ConfigManager.tr("方向键UI快捷键", "Arrow UI Key"), "glob_menu_key",
                ConfigManager.getMenuKey(), ConfigManager::setMenuKey, y, h, mx, my, mouseUnlocked, accent);
        renderGlobalKeybindCard(g, rxCol, ry, halfW, ConfigManager.tr("鼠标UI快捷键", "Mouse UI Key"), "glob_mouse_key",
                ConfigManager.getMouseUnlockKey(), ConfigManager::setMouseUnlockKey, y, h, mx, my, mouseUnlocked, accent);
        ry += 26;

        // Row 5: Language + Built-in Tutorial Guide!
        AppleUiRenderer.drawElevatedCard(g, x, ry, halfW, 22, 6, 0f, (mouseUnlocked && inBounds(mx, my, x, ry, halfW, 22)) ? 1f : 0f, accent);
        YeHdFont.draw(g, ConfigManager.tr("界面语言", "Language"), x + 8, YeHdFont.vCenterY(ry, 22, 8.5f), 8.5f, AppleUiRenderer.textPrimary(), false, false);
        String lVal = ConfigManager.english ? "English" : "简体中文";
        AppleUiRenderer.drawRoundedRect(g, x + halfW - 58, ry + 3, 52, 16, 5, accent);
        YeHdFont.drawCenteredInBox(g, lVal, x + halfW - 58, ry + 3, 52, 16, 8.1f, 0xFFFFFFFF, false, true);
        addClippedHit(x, ry, halfW, 22, y, y + h, () -> {
            playClick();
            ConfigManager.english = !ConfigManager.english;
            ConfigManager.saveAsync();
        }, null, null, null);

        drawActionBtn(g, rxCol, ry + 1, halfW, ConfigManager.tr("查看操作与快捷键使用教程", "Open Controls Tutorial"),
                mx, my, mouseUnlocked, accent, y, h, () -> { playClick(); tutorialModalOpen = true; });
        ry += 28;

        // ---- BOTTOM: CONFIG / PROFILES MANAGER ----
        ry = drawSettingSectionHeader(g,
                ConfigManager.tr("配置档案管理 · 当前账号: " + ConfigManager.getCurrentAccountName() + " (" + ConfigManager.getActiveProfile() + ")",
                        "Config Profiles · " + ConfigManager.getActiveProfile()),
                x, ry, rowW, y, h, 0xFF34D399);

        List<String> profiles = ConfigManager.listAvailableProfiles();
        int pCols = 4;
        int pGap = 5;
        int pCellW = (rowW - (pCols - 1) * pGap) / pCols;
        for (int i = 0; i < profiles.size(); i++) {
            String pName = profiles.get(i);
            int pcx = x + (i % pCols) * (pCellW + pGap);
            int pcy = ry + (i / pCols) * 21;
            boolean sel = pName.equalsIgnoreCase(ConfigManager.getActiveProfile());
            boolean hov = mouseUnlocked && inBounds(mx, my, pcx, pcy, pCellW, 18);
            AppleUiRenderer.drawElevatedCard(g, pcx, pcy, pCellW, 18, 5, sel ? 1f : 0f, hov ? 1f : 0f, accent);
            String disp = pName.equalsIgnoreCase(ConfigManager.DEFAULT_PROFILE)
                    ? ConfigManager.tr("default·默认", "default")
                    : YeHdFont.clipToWidth(pName, 8.1f, sel, pCellW - 8);
            YeHdFont.drawCenteredInBox(g, disp, pcx, pcy, pCellW, 18, 8.1f, sel ? 0xFFFFFFFF : AppleUiRenderer.textPrimary(), false, sel);
            addClippedHit(pcx, pcy, pCellW, 18, y, y + h, () -> {
                playClick();
                textInputKind = 0;
                ConfigManager.switchToProfile(pName, true);
                showToast(ConfigManager.tr("已切换档案: ", "Switched: ") + pName, accent);
            }, null, null, null);
        }
        ry += ((profiles.size() + pCols - 1) / pCols) * 21 + 4;

        if (textInputKind == 1 || textInputKind == 2) {
            AppleUiRenderer.drawRoundedRect(g, x, ry, rowW, 22, 6, 0xEE090D16);
            AppleUiRenderer.drawRoundedOutline(g, x, ry, rowW, 22, 6, 1.2f, accent);
            String prefix = (textInputKind == 1) ? ConfigManager.tr("新建名称: ", "New Name: ") : ConfigManager.tr("重命名为: ", "Rename: ");
            YeHdFont.draw(g, prefix + textInputBuffer + "|", x + 8, YeHdFont.vCenterY(ry, 22, 8.5f), 8.5f, 0xFFF8FAFC, false, false);
            int okX = x + rowW - 44;
            AppleUiRenderer.drawRoundedRect(g, okX, ry + 3, 40, 16, 4.5f, accent);
            YeHdFont.drawCenteredInBox(g, ConfigManager.tr("确定", "OK"), okX, ry + 3, 40, 16, 8.1f, 0xFFFFFFFF, false, true);
            addClippedHit(okX, ry + 3, 40, 16, y, y + h, YeQuickMenu::confirmTextInput, null, null, null);
            ry += 26;
        }

        int thirdW = (rowW - 10) / 3;
        drawActionBtn(g, x, ry, thirdW, ConfigManager.tr("+ 新建档案", "+ New Profile"), mx, my, mouseUnlocked, accent, y, h, () -> {
            playClick();
            textInputKind = 1;
            textInputBuffer = ConfigManager.getCurrentAccountName() + "_pvp";
        });
        drawActionBtn(g, x + thirdW + 5, ry, thirdW, ConfigManager.tr("重命名档案", "Rename"), mx, my, mouseUnlocked, accent, y, h, () -> {
            playClick();
            if (ConfigManager.getActiveProfile().equalsIgnoreCase(ConfigManager.DEFAULT_PROFILE)) {
                showToast(ConfigManager.tr("默认档案不可重命名", "Cannot rename default"), 0xFFEF4444);
            } else {
                textInputKind = 2;
                textInputBuffer = ConfigManager.getActiveProfile();
            }
        });
        drawActionBtn(g, x + (thirdW + 5) * 2, ry, thirdW, ConfigManager.tr("设为新号模板", "Set Template"), mx, my, mouseUnlocked, accent, y, h, () -> {
            playClick();
            ConfigManager.saveCurrentAsDefaultTemplate();
            showToast(ConfigManager.tr("已设为新账号默认模板", "Saved template"), 0xFF22C55E);
        });
        ry += 23;

        drawActionBtn(g, x, ry, thirdW, ConfigManager.tr("复制配置码", "Copy Code"), mx, my, mouseUnlocked, accent, y, h, () -> {
            playClick();
            String code = ConfigManager.exportShareCodeToClipboard();
            if (code != null) showToast(ConfigManager.tr("已复制配置码", "Copied share code"), 0xFF22C55E);
        });
        drawActionBtn(g, x + thirdW + 5, ry, thirdW, ConfigManager.tr("粘贴配置码", "Paste Code"), mx, my, mouseUnlocked, accent, y, h, () -> {
            playClick();
            if (ConfigManager.importShareCodeFromClipboard()) showToast(ConfigManager.tr("已导入配置码", "Imported code"), 0xFF22C55E);
            else showToast(ConfigManager.tr("剪贴板无有效配置码", "Invalid clipboard code"), 0xFFEF4444);
        });
        drawActionBtn(g, x + (thirdW + 5) * 2, ry, thirdW, ConfigManager.tr("恢复出厂默认", "Factory Reset"), mx, my, mouseUnlocked, accent, y, h, () -> {
            playClick();
            requestConfirmation(ConfigManager.tr("确定将当前配置档案恢复为出厂默认设置吗？", "Reset current profile to factory default?"), () -> {
                ConfigManager.resetCurrentToFactoryDefault();
                showToast(ConfigManager.tr("已恢复出厂默认", "Reset to factory default"), accent);
            });
        });
        ry += 23;

        drawActionBtn(g, x, ry, halfW, ConfigManager.tr("打开配置文件夹", "Open Configs Dir"), mx, my, mouseUnlocked, accent, y, h, () -> {
            playClick();
            ConfigManager.openConfigsFolder();
        });
        drawActionBtn(g, rxCol, ry, halfW, ConfigManager.tr("重置全部HUD位置", "Reset HUD Pos"), mx, my, mouseUnlocked, accent, y, h, () -> {
            playClick();
            requestConfirmation(ConfigManager.tr("确定重置所有 HUD 组件坐标吗？", "Reset all HUD positions?"), () -> {
                ConfigManager.resetHudSlotsDefault();
                ConfigManager.saveAsync();
                showToast(ConfigManager.tr("已重置全部 HUD 坐标", "Reset positions"), accent);
            });
        });
        ry += 24;

        int contentH = ry - startRy;
        maxScroll = Math.max(0.0, contentH - h);
        drawScrollbar(g, x + w - 2, y, h, contentH, accent);
    }

    // ==================== 5. DEDICATED FRIENDS TAB (RIGHT OF SETTINGS!) ====================

    private static void renderFriendsTab(GuiGraphics g, int x, int y, int w, int h,
                                         int mx, int my, boolean mouseUnlocked, int accent) {
        int startRy = y - (int) Math.round(scrollOffset);
        int ry = startRy;
        int rowW = w - 6;
        int halfW = (rowW - 6) / 2;
        int rxCol = x + halfW + 6;

        // 1. Add Friend by ID Input Bar
        ry = drawSettingSectionHeader(g, ConfigManager.tr("添加好友与近距中键开关", "Add Friends & Middle-Click"), x, ry, rowW, y, h, 0xFF34D399);

        boolean typingId = (textInputKind == 3);
        int inpW = rowW - 76;
        AppleUiRenderer.drawRoundedRect(g, x, ry, inpW, 21, 5, 0xDD090D16);
        AppleUiRenderer.drawRoundedOutline(g, x, ry, inpW, 21, 5, 1.1f, typingId ? accent : AppleUiRenderer.borderSubtle());
        String idPrompt = typingId ? (textInputBuffer + "|")
                : (textInputBuffer.isEmpty() ? ConfigManager.tr("点击输入玩家 ID 名称...", "Click to enter player ID...") : textInputBuffer);
        YeHdFont.draw(g, idPrompt, x + 8, YeHdFont.vCenterY(ry, 21, 8.4f), 8.4f,
                (!typingId && textInputBuffer.isEmpty()) ? AppleUiRenderer.textTertiary() : 0xFFF8FAFC, false, false);
        addClippedHit(x, ry, inpW, 21, y, y + h, () -> {
            playClick();
            commitInlineInputIfNumber();
            textInputKind = 3;
        }, () -> textInputBuffer = "", null, null);

        int addBtnX = x + inpW + 5;
        AppleUiRenderer.drawRoundedRect(g, addBtnX, ry, 71, 21, 5, 0xFF10B981);
        YeHdFont.drawCenteredInBox(g, ConfigManager.tr("+ 添加好友", "+ Add"), addBtnX, ry, 71, 21, 8.3f, 0xFFFFFFFF, false, true);
        addClippedHit(addBtnX, ry, 71, 21, y, y + h, YeQuickMenu::confirmTextInput, null, null, null);
        ry += 26;

        // 2. Middle-Click Close-Range Friend Toggle (Fixed 4.5m interaction range)
        AppleUiRenderer.drawElevatedCard(g, x, ry, rowW, 24, 6, 0f, (mouseUnlocked && inBounds(mx, my, x, ry, rowW, 24)) ? 1f : 0f, accent);
        YeHdFont.draw(g, ConfigManager.tr("中键加删好友 (固定 4.5 格近距触发)", "Middle-Click Friend (4.5m)"), x + 8, YeHdFont.vCenterY(ry, 24, 8.5f), 8.5f, AppleUiRenderer.textPrimary(), false, false);
        float mcfAnim = AppleUiRenderer.animChannel("mcf_sw", FriendManager.middleClickAdd ? 1f : 0f, 18f);
        AppleUiRenderer.drawPillSwitch(g, x + rowW - 34, ry + 5, 27, 14, mcfAnim, accent);
        addClippedHit(x, ry, rowW, 24, y, y + h, () -> {
            playClick();
            FriendManager.middleClickAdd = !FriendManager.middleClickAdd;
            FriendManager.save();
        }, null, null, null);
        ry += 29;

        // 3. Nearby Players in World (1-Click Quick Toggle!)
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.player != null) {
            List<? extends Player> nearby = mc.level.players().stream()
                    .filter(p -> p != mc.player && !p.isSpectator())
                    .sorted((a, b) -> Float.compare(mc.player.distanceTo(a), mc.player.distanceTo(b)))
                    .toList();
            if (!nearby.isEmpty()) {
                ry = drawSettingSectionHeader(g, ConfigManager.tr("周围在线玩家 (点击一键加删)", "Nearby Players"), x, ry, rowW, y, h, accent);
                int cols = 3, gap = 5, cw = (rowW - (cols - 1) * gap) / cols;
                for (int i = 0; i < nearby.size(); i++) {
                    Player p = nearby.get(i);
                    String nm = p.getGameProfile().name();
                    boolean isFr = FriendManager.isFriend(nm);
                    int cx = x + (i % cols) * (cw + gap);
                    int cy = ry + (i / cols) * 23;
                    if (cy + 19 >= y && cy <= y + h) {
                        AppleUiRenderer.drawElevatedCard(g, cx, cy, cw, 19, 5, isFr ? 1f : 0f, 0f, 0xFF10B981);
                        String txt = String.format(Locale.ROOT, "%s (%.1fm)", nm, mc.player.distanceTo(p));
                        YeHdFont.drawCenteredInBox(g, YeHdFont.clipToWidth(txt, 8.0f, isFr, cw - 8), cx, cy, cw, 19, 8.0f,
                                isFr ? 0xFFFFFFFF : AppleUiRenderer.textPrimary(), false, isFr);
                        addClippedHit(cx, cy, cw, 19, y, y + h, () -> {
                            playClick();
                            FriendManager.toggleFriend(nm);
                        }, null, null, null);
                    }
                }
                ry += ((nearby.size() + cols - 1) / cols) * 23 + 4;
            }
        }

        // 4. Current Friends Whitelist
        List<String> friends = FriendManager.getFriends();
        ry = drawSettingSectionHeader(g, ConfigManager.tr("当前好友白名单 (" + friends.size() + " 人)", "Friends List (" + friends.size() + ")"), x, ry, rowW, y, h, 0xFF34D399);

        if (friends.isEmpty()) {
            YeHdFont.draw(g, ConfigManager.tr("暂无好友，可在上方输入 ID 添加或开启近距中键添加", "No friends added yet"),
                    x + 6, ry + 4, 8.2f, AppleUiRenderer.textTertiary(), false, false);
            ry += 22;
        } else {
            int cols = 3, gap = 5, cw = (rowW - (cols - 1) * gap) / cols;
            for (int i = 0; i < friends.size(); i++) {
                String fn = friends.get(i);
                int cx = x + (i % cols) * (cw + gap);
                int cy = ry + (i / cols) * 23;
                if (cy + 19 >= y && cy <= y + h) {
                    AppleUiRenderer.drawElevatedCard(g, cx, cy, cw, 19, 5, 0f, (mouseUnlocked && inBounds(mx, my, cx, cy, cw, 19)) ? 1f : 0f, 0xFFEF4444);
                    YeHdFont.draw(g, YeHdFont.clipToWidth(fn, 8.1f, true, cw - 22), cx + 7, YeHdFont.vCenterY(cy, 19, 8.1f), 8.1f, 0xFF34D399, false, true);
                    YeHdFont.draw(g, "×", cx + cw - 13, YeHdFont.vCenterY(cy, 19, 8.8f), 8.8f, 0xFFF87171, false, true);
                    addClippedHit(cx, cy, cw, 19, y, y + h, () -> {
                        playClick();
                        FriendManager.removeFriend(fn);
                    }, null, null, null);
                }
            }
            ry += ((friends.size() + cols - 1) / cols) * 23 + 5;

            drawActionBtn(g, x, ry, 118, ConfigManager.tr("清空全部好友", "Clear All Friends"), mx, my, mouseUnlocked, 0xFFEF4444, y, h, () -> {
                playClick();
                requestConfirmation(ConfigManager.tr("确定清空全部好友白名单吗？", "Clear all friends?"), () -> {
                    FriendManager.clearFriends();
                    showToast(ConfigManager.tr("已清空好友列表", "Cleared friends"), 0xFFEF4444);
                });
            });
            ry += 24;
        }

        int contentH = ry - startRy;
        maxScroll = Math.max(0.0, contentH - h);
        drawScrollbar(g, x + w - 2, y, h, contentH, accent);
    }

    // ==================== HELPER WIDGETS & MODALS ====================

    private static void renderCustomSliderCard(GuiGraphics g, int cx, int cy, int cw, String title, String numKey,
                                               String formattedVal, double curVal, double min, double max,
                                               DoubleConsumer onSlide, DoubleConsumer onManualInput,
                                               int clipY, int clipH, int mx, int my, boolean mouseUnlocked, int accent) {
        if (cy + 28 < clipY || cy > clipY + clipH) return;
        boolean hov = mouseUnlocked && inBounds(mx, my, cx, cy, cw, 28);
        AppleUiRenderer.drawElevatedCard(g, cx, cy, cw, 28, 6, 0f, hov ? 1f : 0f, accent);

        boolean editing = (textInputKind == 5 && numKey.equals(activeNumberKey));
        String vt = editing ? (textInputBuffer + "|") : formattedVal;
        float vtW = Math.max(24f, YeHdFont.widthF(vt, 8.4f, true));
        YeHdFont.draw(g, title, cx + 8, cy + 3.8f, 8.4f, AppleUiRenderer.textPrimary(), false, false);

        int numX = Math.round(cx + cw - 8 - vtW);
        if (editing) {
            AppleUiRenderer.drawRoundedRect(g, numX - 3, cy + 2, vtW + 6, 13, 3, 0xEE080C16);
            AppleUiRenderer.drawRoundedOutline(g, numX - 3, cy + 2, vtW + 6, 13, 3, 1.0f, accent);
        }
        YeHdFont.draw(g, vt, numX, cy + 3.8f, 8.4f, editing ? 0xFFFFFFFF : accent, false, true);
        addClippedHit(numX - 4, cy + 1, Math.round(vtW) + 8, 14, clipY, clipY + clipH,
                null, null, null, () -> startNumberEdit(numKey, formattedVal.replace("%", "").replace("m", ""), onManualInput));

        int bw = cw - 16, bx = cx + 8, by = cy + 20;
        float pct = (float) ((curVal - min) / Math.max(0.0001, max - min));
        AppleUiRenderer.drawRoundedRect(g, bx, by, bw, 3.6f, 1.8f, AppleUiRenderer.lightTheme ? 0xFFCBD5E1 : 0xFF283142);
        float fillW = Math.max(3.6f, bw * Math.max(0f, Math.min(1f, pct)));
        AppleUiRenderer.drawRoundedRect(g, bx, by, fillW, 3.6f, 1.8f, accent);
        AppleUiRenderer.drawCircle(g, bx + fillW - 4.0f, by - 2.2f, 8.0f, 0xFFFFFFFF);
        addClippedHit(bx - 3, by - 6, bw + 6, 14, clipY, clipY + clipH, null, null, new SliderBinding() {
            @Override public double getMin() { return min; }
            @Override public double getMax() { return max; }
            @Override public void setValue(double v) { onSlide.accept(v); }
        }, null);
    }

    private static void renderGlobalKeybindCard(GuiGraphics g, int cx, int cy, int cw, String title, String kid,
                                                int currentKey, java.util.function.IntConsumer setter,
                                                int clipY, int clipH, int mx, int my, boolean mouseUnlocked, int accent) {
        if (cy + 22 < clipY || cy > clipY + clipH) return;
        boolean listening = kid.equals(activeKeybindId);
        AppleUiRenderer.drawElevatedCard(g, cx, cy, cw, 22, 6, 0f, (mouseUnlocked && inBounds(mx, my, cx, cy, cw, 22)) ? 1f : 0f, accent);
        YeHdFont.draw(g, title, cx + 8, YeHdFont.vCenterY(cy, 22, 8.4f), 8.4f, AppleUiRenderer.textPrimary(), false, false);
        String txt = listening ? liveBindPrompt() : KeyCombo.format(currentKey);
        int bw = Math.max(52, YeHdFont.width(txt, 8.0f, true) + 12);
        int bx = cx + cw - 5 - bw;
        AppleUiRenderer.drawRoundedRect(g, bx, cy + 3, bw, 16, 5, listening ? 0xFFEF4444 : accent);
        YeHdFont.drawCenteredInBox(g, txt, bx, cy + 3, bw, 16, 8.0f, 0xFFFFFFFF, false, true);
        addClippedHit(cx, cy, cw, 22, clipY, clipY + clipH,
                () -> startKeybindListen(kid, setter),
                () -> { playClick(); stopKeybindListen(); }, null, null);
    }

    private static void renderColorPickerPopover(GuiGraphics g, int sw, int sh, int mx, int my, int accent) {
        int boxW = pickerW + 16;
        int boxH = pickerSvH + 54;
        int px = Math.max(8, Math.min(sw - boxW - 8, pickerX));
        int py = Math.max(8, Math.min(sh - boxH - 8, pickerY));
        pickerX = px;
        pickerY = py;

        float h, s, b;
        String hex;
        if (openColorSetting != null) {
            h = openColorSetting.getHue();
            s = openColorSetting.getSat();
            b = openColorSetting.getBri();
            hex = openColorSetting.getHex();
        } else {
            h = ConfigManager.customAccentHue;
            s = ConfigManager.customAccentSat;
            b = ConfigManager.customAccentBri;
            hex = String.format(Locale.ROOT, "#%06X", ConfigManager.getCustomAccentArgb() & 0x00FFFFFF);
        }

        AppleUiRenderer.drawElevatedWindow(g, px, py, boxW, boxH, AppleUiRenderer.isMechanical() ? 0 : 8);
        AppleUiRenderer.drawRoundedOutline(g, px, py, boxW, boxH, AppleUiRenderer.isMechanical() ? 0 : 8, 1.4f, accent);
        HIT_RECTS.add(new HitRect(px, py, boxW, boxH, () -> {}, null, null, 5));

        int svX = px + 8, svY = py + 8;
        int hueY = svY + pickerSvH + 7;
        AppleUiRenderer.drawHsvPicker(g, svX, svY, pickerW, pickerSvH, hueY, 9, h, s, b);

        // Hex display + Done button
        int footY = hueY + 14;
        boolean editingHex = (textInputKind == 6);
        AppleUiRenderer.drawRoundedRect(g, svX, footY, 76, 16, 4, 0xDD080C15);
        AppleUiRenderer.drawRoundedOutline(g, svX, footY, 76, 16, 4, 1.0f, editingHex ? accent : AppleUiRenderer.borderSubtle());
        YeHdFont.drawCenteredInBox(g, editingHex ? (textInputBuffer + "|") : hex, svX, footY, 76, 16, 7.9f, 0xFFF8FAFC, false, true);
        HIT_RECTS.add(new HitRect(svX, footY, 76, 16, () -> {
            playClick();
            textInputKind = 6;
            textInputBuffer = hex;
        }, null, null, 6));

        int doneX = svX + pickerW - 44;
        AppleUiRenderer.drawRoundedRect(g, doneX, footY, 44, 16, 4, accent);
        YeHdFont.drawCenteredInBox(g, ConfigManager.tr("确定", "Done"), doneX, footY, 44, 16, 7.9f, 0xFFFFFFFF, false, true);
        HIT_RECTS.add(new HitRect(doneX, footY, 44, 16, () -> {
            playClick();
            openColorSetting = null;
            openThemeCustomColor = false;
            if (textInputKind == 6) textInputKind = 0;
        }, null, null, 6));
    }

    private static void renderConfirmationModal(GuiGraphics g, int sw, int sh, int mx, int my, int accent) {
        g.fill(0, 0, sw, sh, 0x9204070E);
        int mw = 264, mh = 94;
        int x = (sw - mw) / 2, y = (sh - mh) / 2;
        int rad = AppleUiRenderer.isMechanical() ? 0 : 10;

        HIT_RECTS.add(new HitRect(0, 0, sw, sh, () -> { confirmMessage = null; confirmAction = null; }, null, null, 14));
        HIT_RECTS.add(new HitRect(x, y, mw, mh, () -> {}, null, null, 15));

        AppleUiRenderer.drawElevatedWindow(g, x, y, mw, mh, rad);
        AppleUiRenderer.drawRoundedOutline(g, x, y, mw, mh, rad, 1.4f, 0xFFEF4444);

        YeHdFont.drawCentered(g, ConfigManager.tr("确认操作", "Confirm Reset"), x + mw * 0.5f, y + 12, 9.6f, 0xFFF87171, false, true);
        YeHdFont.drawCentered(g, YeHdFont.clipToWidth(confirmMessage, 8.4f, false, mw - 20), x + mw * 0.5f, y + 34, 8.4f, AppleUiRenderer.textPrimary(), false, false);

        int btnW = 88, btnY = y + mh - 28;
        int cancelX = x + 34;
        AppleUiRenderer.drawRoundedRect(g, cancelX, btnY, btnW, 19, AppleUiRenderer.isMechanical() ? 0 : 5, AppleUiRenderer.bgCardHover());
        YeHdFont.drawCenteredInBox(g, ConfigManager.tr("取消", "Cancel"), cancelX, btnY, btnW, 19, 8.4f, AppleUiRenderer.textPrimary(), false, false);
        HIT_RECTS.add(new HitRect(cancelX, btnY, btnW, 19, () -> {
            playClick();
            confirmMessage = null;
            confirmAction = null;
        }, null, null, 16));

        int okX = x + mw - 34 - btnW;
        AppleUiRenderer.drawRoundedRect(g, okX, btnY, btnW, 19, AppleUiRenderer.isMechanical() ? 0 : 5, 0xFFEF4444);
        YeHdFont.drawCenteredInBox(g, ConfigManager.tr("确定重置", "Confirm"), okX, btnY, btnW, 19, 8.4f, 0xFFFFFFFF, false, true);
        HIT_RECTS.add(new HitRect(okX, btnY, btnW, 19, () -> {
            playClick();
            Runnable act = confirmAction;
            confirmMessage = null;
            confirmAction = null;
            if (act != null) act.run();
        }, null, null, 16));
    }

    private static void renderTutorialModal(GuiGraphics g, int sw, int sh, int accent) {
        g.fill(0, 0, sw, sh, 0x8C04070E);
        int mw = 376, mh = 216;
        int x = (sw - mw) / 2, y = (sh - mh) / 2;
        int rad = AppleUiRenderer.isMechanical() ? 0 : 10;

        HIT_RECTS.add(new HitRect(0, 0, sw, sh, () -> tutorialModalOpen = false, null, null, 9));
        HIT_RECTS.add(new HitRect(x, y, mw, mh, () -> {}, null, null, 9));

        AppleUiRenderer.drawElevatedWindow(g, x, y, mw, mh, rad);
        YeHdFont.draw(g, ConfigManager.tr("Ye 模组快捷操作教程", "Ye Mod Quick Guide"), x + 14, y + 10, 9.6f, accent, false, true);

        String[] lines = {
                "1. 界面呼出：按「" + KeyCombo.format(ConfigManager.getMenuKey()) + "」开关右下角便携方向键面板；按「" + KeyCombo.format(ConfigManager.getMouseUnlockKey()) + "」开关居中鼠标大面板。",
                "2. 模块控制：左键卡片开关功能，右键卡片进入详细参数；点击 ★ 加入收藏夹，点击 ▲ 置顶。",
                "3. 快捷键绑定：支持单键、鼠标侧键或 Ctrl/Shift/Alt 组合键；右键快捷键按钮可清除绑定。",
                "4. 精确数值输入：在任意滑条或音量的数字上「双击左键」，即可直接键盘输入精确数值并回车确认。",
                "5. HUD 排版：打开鼠标面板时，在屏幕上直接左键拖动任意 HUD 自动磁吸，右键屏幕 HUD 直达其专属设置。",
                "6. 放大镜与调速：按住放大镜键滑动滚轮无级缩放视野；按住飞行调速键滑动滚轮实时调节飞行速度。",
                "7. 好友系统：可在顶部「好友」页输入 ID 添加，或开启近距中键对身边玩家按鼠标中键一键加删好友。"
        };
        float ry = y + 32;
        for (String ln : lines) {
            YeHdFont.draw(g, YeHdFont.clipToWidth(ln, 8.1f, false, mw - 28), x + 14, ry, 8.1f, AppleUiRenderer.textPrimary(), false, false);
            ry += 20;
        }

        int closeX = x + (mw - 86) / 2;
        AppleUiRenderer.drawRoundedRect(g, closeX, y + mh - 26, 86, 18, AppleUiRenderer.isMechanical() ? 0 : 5, accent);
        YeHdFont.drawCenteredInBox(g, ConfigManager.tr("我知道了", "Got It"), closeX, y + mh - 26, 86, 18, 8.3f, 0xFFFFFFFF, false, true);
        HIT_RECTS.add(new HitRect(closeX, y + mh - 26, 86, 18, () -> { playClick(); tutorialModalOpen = false; }, null, null, 10));
    }

    private static <E extends Enum<E>> void renderDropdown(GuiGraphics g, EnumSetting<E> es, int mx, int my, int accent) {
        E[] vals = es.getDefaultValue().getDeclaringClass().getEnumConstants();
        boolean useTable = es.isGridPicker() || vals.length >= 6;
        int cols = 1;
        if (useTable) {
            if (vals.length > 0 && vals[0] instanceof com.ye.mod.modules.visual.CrosshairModule.Preset) cols = 6;
            else if (vals.length >= 9) cols = 3;
            else cols = 2;
        }
        int itemH = (vals.length > 0 && vals[0] instanceof com.ye.mod.modules.visual.CrosshairModule.Preset) ? 24 : 19;
        int cellW = (cols == 6) ? 54 : (cols == 3 ? 98 : (cols == 2 ? 108 : Math.max(dropW, 114)));
        int tableW = cols == 1 ? cellW : (cols * cellW + (cols - 1) * 4 + 8);
        int rows = (vals.length + cols - 1) / cols;
        int maxVisRows = 8;
        int fullH = rows * itemH + 6;
        int boxH = Math.min(fullH, maxVisRows * itemH + 6);
        double maxDropSc = Math.max(0.0, fullH - boxH);
        dropScroll = Math.max(0.0, Math.min(maxDropSc, dropScroll));

        int sh = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        int sw = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int dx = Math.max(6, Math.min(sw - tableW - 6, dropX));
        int dy = (dropY + boxH + 6 > sh) ? Math.max(6, dropY - 22 - boxH) : dropY;

        AppleUiRenderer.drawElevatedWindow(g, dx, dy, tableW, boxH, AppleUiRenderer.isMechanical() ? 0 : 7);
        AppleUiRenderer.drawRoundedOutline(g, dx, dy, tableW, boxH, AppleUiRenderer.isMechanical() ? 0 : 7, 1.4f, accent);
        HIT_RECTS.add(new HitRect(dx, dy, tableW, boxH, () -> {}, null, null, 5));

        g.enableScissor(dx + 1, dy + 2, dx + tableW - 1, dy + boxH - 2);
        for (int i = 0; i < vals.length; i++) {
            E opt = vals[i];
            int colIdx = i % cols;
            int rowIdx = i / cols;
            int ix = (cols == 1) ? (dx + 3) : (dx + 4 + colIdx * (cellW + 4));
            int iw = (cols == 1) ? (tableW - 6) : cellW;
            int iy = dy + 3 + rowIdx * itemH - (int) Math.round(dropScroll);
            if (iy + itemH < dy || iy > dy + boxH) continue;

            boolean sel = (opt == es.get());
            boolean hov = inBounds(mx, my, ix, iy, iw, itemH);
            int bg = sel ? accent : (hov ? AppleUiRenderer.bgCardHover() : AppleUiRenderer.bgCard());
            AppleUiRenderer.drawRoundedRect(g, ix, iy, iw, itemH - 2, AppleUiRenderer.isMechanical() ? 0 : 4.5f, bg);
            if (cols == 6 && opt instanceof com.ye.mod.modules.visual.CrosshairModule.Preset cp) {
                com.ye.mod.modules.visual.CrosshairModule.renderAtCenter(g, cp, ix + iw / 2, iy + (itemH - 2) / 2,
                        sel ? 0xFFFFFFFF : 0xFFE2E8F0, false);
            } else {
                int swOff = YePreviewRenderer.renderOptionSwatch(g, opt, ix + 4, iy + 3, 11);
                YeHdFont.draw(g, YeHdFont.clipToWidth(enumLabel(opt), 8.0f, sel, iw - swOff - 8),
                        ix + 5 + swOff, YeHdFont.vCenterY(iy, itemH - 2, 8.0f), 8.0f,
                        sel ? 0xFFFFFFFF : AppleUiRenderer.textPrimary(), false, sel);
            }
            int ct = Math.max(dy + 2, iy), cb = Math.min(dy + boxH - 2, iy + itemH);
            if (cb > ct) {
                HIT_RECTS.add(new HitRect(ix, ct, iw, cb - ct, () -> {
                    es.set(opt);
                    openDropdown = null;
                    playClick();
                    ConfigManager.saveAsync();
                }, null, null, 6));
            }
        }
        g.disableScissor();
    }

    private static void renderSoundDropdown(GuiGraphics g, int mx, int my, int accent) {
        YeSoundPresets.UiSoundType[] vals = YeSoundPresets.UiSoundType.values();
        int itemH = 18;
        int boxH = vals.length * itemH + 6;
        int sh = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        int dy = (soundDropY + boxH + 6 > sh) ? Math.max(6, soundDropY - 22 - boxH) : soundDropY;

        AppleUiRenderer.drawElevatedWindow(g, soundDropX, dy, soundDropW, boxH, 7);
        AppleUiRenderer.drawRoundedOutline(g, soundDropX, dy, soundDropW, boxH, 7, 1.4f, accent);
        HIT_RECTS.add(new HitRect(soundDropX, dy, soundDropW, boxH, () -> {}, null, null, 5));

        for (int i = 0; i < vals.length; i++) {
            YeSoundPresets.UiSoundType st = vals[i];
            int iy = dy + 3 + i * itemH;
            boolean sel = (st == ConfigManager.uiClickSound);
            boolean hov = inBounds(mx, my, soundDropX + 3, iy, soundDropW - 6, itemH);
            int bg = sel ? accent : (hov ? AppleUiRenderer.bgCardHover() : AppleUiRenderer.bgCard());
            AppleUiRenderer.drawRoundedRect(g, soundDropX + 3, iy, soundDropW - 6, itemH - 1, 4.5f, bg);
            YeHdFont.draw(g, enumLabel(st), soundDropX + 8, YeHdFont.vCenterY(iy, itemH - 1, 8.2f), 8.2f,
                    sel ? 0xFFFFFFFF : AppleUiRenderer.textPrimary(), false, sel);
            HIT_RECTS.add(new HitRect(soundDropX + 3, iy, soundDropW - 6, itemH, () -> {
                ConfigManager.uiClickSound = st;
                soundDropdownOpen = false;
                playClick();
                ConfigManager.saveAsync();
            }, null, null, 6));
        }
    }

    private static void drawActionBtn(GuiGraphics g, int x, int y, int w, String text,
                                      int mx, int my, boolean mouseUnlocked, int accent, int clipY, int clipH, Runnable onClick) {
        if (y + 19 < clipY || y > clipY + clipH) return;
        boolean hov = mouseUnlocked && !hasModalOpen() && !soundDropdownOpen && inBounds(mx, my, x, y, w, 19);
        AppleUiRenderer.drawElevatedCard(g, x, y, w, 19, 5, 0f, hov ? 1f : 0f, accent);
        YeHdFont.drawCenteredInBox(g, text, x, y, w, 19, 8.1f, AppleUiRenderer.textPrimary(), false, false);
        addClippedHit(x, y, w, 19, clipY, clipY + clipH, onClick, null, null, null);
    }

    // ==================== INLINE INPUT & KEYBIND RECORDING HELPERS ====================

    private static void startKeybindListen(String id, java.util.function.IntConsumer consumer) {
        playClick();
        if (id.equals(activeKeybindId)) {
            stopKeybindListen();
        } else {
            activeKeybindId = id;
            activeKeybindConsumer = consumer;
            pendingModifierKeyAlone = -1;
        }
    }

    public static void stopKeybindListen() {
        activeKeybindId = "";
        activeKeybindConsumer = null;
        pendingModifierKeyAlone = -1;
    }

    private static String liveBindPrompt() {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.getWindow() != null) {
            return KeyCombo.formatPendingModifiers(mc.getWindow().handle());
        }
        return ConfigManager.tr("请按键...", "Press...");
    }

    private static void startNumberEdit(String key, String initVal, DoubleConsumer setter) {
        playClick();
        commitInlineInputIfNumber();
        textInputKind = 5;
        activeNumberKey = key;
        activeNumberSetter = setter;
        textInputBuffer = initVal;
    }

    public static void commitInlineInputIfNumber() {
        if (textInputKind == 5 && activeNumberSetter != null) {
            try {
                String clean = textInputBuffer.trim().replace("%", "").replace("m", "");
                if (!clean.isEmpty()) {
                    double parsed = Double.parseDouble(clean);
                    activeNumberSetter.accept(parsed);
                }
            } catch (Exception ignored) {}
            textInputKind = 0;
            activeNumberSetter = null;
            activeNumberKey = "";
        }
    }

    public static void confirmTextInput() {
        playClick();
        String val = textInputBuffer.trim();
        if (textInputKind == 1) {
            if (!val.isEmpty()) {
                String created = ConfigManager.createNewProfile(val);
                showToast(ConfigManager.tr("已新建档案: ", "Created: ") + created, 0xFF22C55E);
            }
            textInputKind = 0;
        } else if (textInputKind == 2) {
            if (!val.isEmpty() && ConfigManager.renameActiveProfile(val)) {
                showToast(ConfigManager.tr("已重命名为: ", "Renamed: ") + ConfigManager.getActiveProfile(), 0xFF22C55E);
            }
            textInputKind = 0;
        } else if (textInputKind == 3) {
            if (!val.isEmpty()) {
                FriendManager.addFriend(val);
                showToast(ConfigManager.tr("已添加好友: ", "Added Friend: ") + val, 0xFF34D399);
                textInputBuffer = "";
            }
            textInputKind = 0;
        } else if (textInputKind == 4 && activeStringSetting != null) {
            activeStringSetting.set(textInputBuffer);
            activeStringSetting = null;
            textInputKind = 0;
        } else if (textInputKind == 5) {
            commitInlineInputIfNumber();
        } else if (textInputKind == 6) {
            if (openColorSetting != null) {
                openColorSetting.setHex(val);
            } else if (openThemeCustomColor) {
                String clean = val.startsWith("#") ? val.substring(1) : val;
                if (clean.length() == 6) {
                    try {
                        int rgb = Integer.parseInt(clean, 16);
                        float[] hsb = Color.RGBtoHSB((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, null);
                        ConfigManager.customAccentHue = hsb[0];
                        ConfigManager.customAccentSat = hsb[1];
                        ConfigManager.customAccentBri = hsb[2];
                        ConfigManager.saveAsync();
                    } catch (Exception ignored) {}
                }
            }
            textInputKind = 0;
        } else if (textInputKind == 7) {
            if (!val.isEmpty()) {
                showToast(ConfigManager.tr("正在联网搜索皮肤: ", "Fetching skin: ") + val, 0xFF38BDF8);
                SkinManager.fetchOnlineSkinAsync(val, skinId -> {
                    if (skinId != null) {
                        CustomSkinModule sm = ModuleManager.get(CustomSkinModule.class);
                        if (sm != null) {
                            sm.selectedSkin.set(skinId);
                            sm.setEnabled(true);
                        }
                        showToast(ConfigManager.tr("已下载并应用皮肤: ", "Applied skin: ") + val, 0xFF22C55E);
                    } else {
                        showToast(ConfigManager.tr("未找到该皮肤: ", "Skin not found: ") + val, 0xFFEF4444);
                    }
                });
                textInputBuffer = "";
            }
            textInputKind = 0;
        }
    }

    private static int renderOnlineSkinSearchBar(GuiGraphics g, CustomSkinModule sm, int x, int ry, int rowW, int listY, int listH, int accent) {
        if (ry + 22 >= listY && ry <= listY + listH) {
            boolean typing = (textInputKind == 7);
            int inpW = rowW - 82;
            AppleUiRenderer.drawRoundedRect(g, x, ry, inpW, 20, 5, 0xCC090D16);
            AppleUiRenderer.drawRoundedOutline(g, x, ry, inpW, 20, 5, 1.0f, typing ? accent : AppleUiRenderer.borderSubtle());
            String prompt = typing ? (textInputBuffer + "|")
                    : (textInputBuffer.isEmpty() ? ConfigManager.tr("输入角色名或正版ID联网下载皮肤...", "Enter player ID or anime name...") : textInputBuffer);
            YeHdFont.draw(g, YeHdFont.clipToWidth(prompt, 8.0f, false, inpW - 12), x + 7, YeHdFont.vCenterY(ry, 20, 8.0f), 8.0f,
                    (!typing && textInputBuffer.isEmpty()) ? AppleUiRenderer.textTertiary() : 0xFFF8FAFC, false, false);
            addClippedHit(x, ry, inpW, 20, listY, listY + listH, () -> {
                playClick();
                commitInlineInputIfNumber();
                textInputKind = 7;
            }, () -> textInputBuffer = "", null, null);

            int btnX = x + inpW + 5;
            AppleUiRenderer.drawRoundedRect(g, btnX, ry, 77, 20, 5, accent);
            YeHdFont.drawCenteredInBox(g, ConfigManager.tr("联网下载", "Download"), btnX, ry, 77, 20, 8.0f, 0xFFFFFFFF, false, true);
            addClippedHit(btnX, ry, 77, 20, listY, listY + listH, YeQuickMenu::confirmTextInput, null, null, null);
        }
        return ry + 24;
    }

    private static int renderStashRadarHierarchy(GuiGraphics g, com.ye.mod.modules.world.StashRadarModule sr,
                                                 int x, int ry, int rowW, int listY, int listH, int mx, int my, int accent) {
        var kinds = com.ye.mod.modules.world.StashRadarModule.ContainerKind.values();
        // Top Big Container Selector Box
        int boxH = 32;
        if (ry + boxH >= listY && ry <= listY + listH) {
            AppleUiRenderer.drawRoundedRect(g, x, ry, rowW, boxH, AppleUiRenderer.isMechanical() ? 0 : 7, AppleUiRenderer.bgCard());
            AppleUiRenderer.drawRoundedOutline(g, x, ry, rowW, boxH, AppleUiRenderer.isMechanical() ? 0 : 7, 1.2f, accent);
            int gap = 4, bw = (rowW - 12 - (kinds.length - 1) * gap) / kinds.length;
            for (int i = 0; i < kinds.length; i++) {
                var k = kinds[i];
                int bx = x + 6 + i * (bw + gap);
                boolean sel = (sr.editingKind.get() == k);
                var cfg = sr.configs.get(k);
                int kCol = cfg != null ? cfg.color.getArgb() : accent;
                AppleUiRenderer.drawElevatedCard(g, bx, ry + 5, bw, 22, 5, sel ? 1f : 0f, 0f, kCol);
                YeHdFont.drawCenteredInBox(g, k.label, bx, ry + 5, bw, 22, 8.0f, sel ? 0xFFFFFFFF : AppleUiRenderer.textPrimary(), false, sel);
                addClippedHit(bx, ry + 5, bw, 22, listY, listY + listH, () -> {
                    playClick();
                    sr.editingKind.set(k);
                }, null, null, null);
            }
        }
        ry += boxH + 5;

        // Nested Indented Child Container Settings Box (clear parent-child hierarchy!)
        var selKind = sr.editingKind.get();
        var cfg = sr.configs.get(selKind);
        if (cfg != null) {
            List<Setting<?>> childSettings = List.<Setting<?>>of(
                    cfg.enabled, cfg.color, cfg.boxMode, cfg.fillAlpha,
                    cfg.tracer, cfg.lineStyle, cfg.lineWidth, cfg.blinkHz
            ).stream().filter(Setting::isVisible).toList();
            int subX = x + 10, subW = rowW - 10;
            ry = drawSettingSectionHeader(g, selKind.label + ConfigManager.tr(" · 专属样式与线条", " · Container Config"), subX, ry, subW, listY, listH, cfg.color.getArgb());
            ry = renderSettingsBlock(g, childSettings, List.of(), subX, ry, subW, listY, listH, mx, my, true, cfg.color.getArgb());
        }
        return ry + 4;
    }

    private static int renderAutoLogConditionCards(GuiGraphics g, com.ye.mod.modules.world.AutoLogModule al,
                                                   int x, int ry, int rowW, int listY, int listH, int mx, int my, int accent) {
        ry = drawSettingSectionHeader(g, ConfigManager.tr("保命触发条件 (每项可设: 关闭 / 任一触发 / 联合触发)", "AutoLog Rules (Off / Any / All)"), x, ry, rowW, listY, listH, accent);
        record RuleCard(EnumSetting<com.ye.mod.modules.world.AutoLogModule.RuleMode> rule, List<Setting<?>> params) {}
        List<RuleCard> cards = List.of(
                new RuleCard(al.popRule, List.of(al.maxPops, al.popResetSec)),
                new RuleCard(al.totemLeftRule, List.of(al.minTotems)),
                new RuleCard(al.healthRule, List.of(al.minHealth, al.includeAbsorption)),
                new RuleCard(al.playerRule, List.of(al.playerRange, al.ignoreFriends, al.armedOnly)),
                new RuleCard(al.fireworkRule, List.of(al.minFireworks)),
                new RuleCard(al.gappleRule, List.of(al.minGapples)),
                new RuleCard(al.xpBottleRule, List.of(al.minXpBottles)),
                new RuleCard(al.durabilityRule, List.of(al.minDurabilityPct)),
                new RuleCard(al.voidRule, List.of())
        );
        var modes = com.ye.mod.modules.world.AutoLogModule.RuleMode.values();
        String[] shortLabels = {ConfigManager.tr("关闭", "Off"), ConfigManager.tr("任一", "OR"), ConfigManager.tr("联合", "AND")};

        for (RuleCard rc : cards) {
            boolean active = rc.rule.get() != com.ye.mod.modules.world.AutoLogModule.RuleMode.OFF;
            if (ry + 24 >= listY && ry <= listY + listH) {
                AppleUiRenderer.drawElevatedCard(g, x, ry, rowW, 23, 6, active ? 0.18f : 0f, 0f, accent);
                YeHdFont.draw(g, rc.rule.getName(), x + 8, YeHdFont.vCenterY(ry, 23, 8.6f), 8.6f,
                        active ? AppleUiRenderer.textPrimary() : AppleUiRenderer.textSecondary(), false, active);
                int pillW = 36, gap = 3;
                int startPx = x + rowW - (pillW * 3 + gap * 2 + 6);
                for (int mi = 0; mi < 3; mi++) {
                    var mode = modes[mi];
                    int px = startPx + mi * (pillW + gap);
                    boolean sel = rc.rule.get() == mode;
                    int pCol = (mi == 0) ? 0xFF475569 : (mi == 1 ? accent : 0xFFA855F7);
                    AppleUiRenderer.drawRoundedRect(g, px, ry + 4, pillW, 15, 4, sel ? pCol : AppleUiRenderer.bgCardHover());
                    YeHdFont.drawCenteredInBox(g, shortLabels[mi], px, ry + 4, pillW, 15, 7.8f, sel ? 0xFFFFFFFF : AppleUiRenderer.textSecondary(), false, sel);
                    addClippedHit(px, ry + 4, pillW, 15, listY, listY + listH, () -> {
                        playClick();
                        rc.rule.set(mode);
                    }, null, null, null);
                }
            }
            ry += 25;
            if (active && !rc.params.isEmpty()) {
                List<Setting<?>> vis = rc.params.stream().filter(Setting::isVisible).toList();
                if (!vis.isEmpty()) {
                    ry = renderSettingsBlock(g, vis, List.of(), x + 12, ry, rowW - 12, listY, listH, mx, my, true, accent);
                }
            }
        }

        ry = drawSettingSectionHeader(g, ConfigManager.tr("防重连死循环与好友快捷", "Safety & Friends"), x, ry, rowW, listY, listH, 0xFF34D399);
        List<Setting<?>> safety = List.of(al.autoDisableOnLog, al.joinGraceSec);
        ry = renderSettingsBlock(g, safety, List.of(), x, ry, rowW, listY, listH, mx, my, true, accent);
        return ry;
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

    public static boolean handleEsc() {
        if (!open) return false;
        playClick();
        if (confirmMessage != null) { confirmMessage = null; confirmAction = null; return true; }
        if (tutorialModalOpen) { tutorialModalOpen = false; return true; }
        if (YePickerModal.isOpen()) { YePickerModal.close(); return true; }
        if (textInputKind != 0) { commitInlineInputIfNumber(); textInputKind = 0; return true; }
        if (activeKeybindConsumer != null) { stopKeybindListen(); return true; }
        if (openColorSetting != null || openThemeCustomColor) { openColorSetting = null; openThemeCustomColor = false; return true; }
        if (openDropdown != null) { openDropdown = null; return true; }
        if (soundDropdownOpen) { soundDropdownOpen = false; return true; }
        if ((activeSection == TopSection.FEATURES || activeSection == TopSection.HUD) && activeModule != null) {
            activeModule = null;
            pageSlide = -14.0f;
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
        int cols = 3; // Compact Arrow-UI uses 3 columns
        if (activeSection == TopSection.FEATURES || activeSection == TopSection.HUD) {
            if (activeModule == null) {
                List<Module> mods = (activeSection == TopSection.FEATURES)
                        ? getOrderedFeatureModulesForNav()
                        : ModuleManager.getByCategory(Category.HUD);
                if (action == 0 && selectedIndex >= cols) selectedIndex -= cols;
                else if (action == 1 && selectedIndex + cols < mods.size()) selectedIndex += cols;
                else if (action == 2) {
                    if (selectedIndex % cols == 0 && activeSection.ordinal() > 0) {
                        switchSection(TopSection.values()[activeSection.ordinal() - 1]);
                        return;
                    } else if (selectedIndex > 0) selectedIndex--;
                } else if (action == 3) {
                    if ((selectedIndex % cols == cols - 1 || selectedIndex == mods.size() - 1)
                            && activeSection.ordinal() < TopSection.values().length - 1) {
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

                int row = selectedIndex / cols;
                double rowTop = row * 34.0;
                int viewH = COMPACT_H - 36;
                if (rowTop < targetScroll) targetScroll = rowTop;
                else if (rowTop + 42 > targetScroll + viewH) targetScroll = rowTop + 42 - viewH;
            } else {
                List<Setting<?>> sets = getDisplaySettings(activeModule);
                if (action == 5) handleEsc();
                else if (action == 0 && selectedIndex > 0) selectedIndex--;
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
        } else if (action == 2 && activeSection.ordinal() > 0) {
            switchSection(TopSection.values()[activeSection.ordinal() - 1]);
        } else if (action == 3 && activeSection.ordinal() < TopSection.values().length - 1) {
            switchSection(TopSection.values()[activeSection.ordinal() + 1]);
        } else if (action == 5) {
            handleEsc();
        }
    }

    public static List<HitRect> getHitRects() { return HIT_RECTS; }

    public static void addScroll(double mouseX, double mouseY, double delta) {
        if (YePickerModal.isOpen()) {
            YePickerModal.onScroll(delta);
            return;
        }
        if (openDropdown != null && inBounds(mouseX, mouseY, dropX, Math.max(0, dropY - 220), dropW, 440)) {
            dropScroll = Math.max(0.0, dropScroll - delta * 18.0);
            return;
        }
        targetScroll = Math.max(0.0, Math.min(maxScroll, targetScroll - delta * 26.0));
    }

    private static boolean inBounds(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    private static String formatDouble(double v, double step) {
        if (step >= 1.0 && Math.abs(v - Math.round(v)) < 0.001) return String.valueOf((long) Math.round(v));
        if (step < 0.01) return String.format(Locale.ROOT, "%.3f", v);
        if (step >= 0.1 - 1e-6 && Math.abs(v * 10 - Math.round(v * 10)) < 0.01) return String.format(Locale.ROOT, "%.1f", v);
        return String.format(Locale.ROOT, "%.2f", v);
    }
}
