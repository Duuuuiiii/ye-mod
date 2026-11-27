package com.ye.mod.ui;

import com.ye.mod.core.Category;
import com.ye.mod.core.ConfigManager;
import com.ye.mod.core.Module;
import com.ye.mod.core.ModuleManager;
import com.ye.mod.core.setting.ActionSetting;
import com.ye.mod.core.setting.BoolSetting;
import com.ye.mod.core.setting.DoubleSetting;
import com.ye.mod.core.setting.EnumSetting;
import com.ye.mod.core.setting.Setting;
import com.ye.mod.modules.render.AccessoriesModule;
import com.ye.mod.modules.render.CapeManager;
import com.ye.mod.modules.render.CustomCapeModule;
import com.ye.mod.modules.render.CustomSkinModule;
import com.ye.mod.modules.render.SkinManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 3D Layered, Silky-Animated Floating UI (F9 Arrow UI / F10 Mouse UI):
 * - 3 Category Tabs (玩家模型 / 视觉渲染 / 特定功能) + 设置 (含完整多配置档管理与分享码)
 * - Strictly clamped smooth scrolling with minimalist scroll indicator
 * - Per-module keybind & single-module independent reset button in detail header
 */
public final class YeQuickMenu {
    private enum TopSection {
        PLAYER_MODEL("玩家模型", "Model", Category.PLAYER_MODEL),
        VISUAL("视觉渲染", "Visual", Category.VISUAL),
        UTILITY("特定功能", "Utility", Category.UTILITY),
        SETTINGS("设置", "Config", null);

        final String zh;
        final String en;
        final Category category;

        TopSection(String zh, String en, Category category) {
            this.zh = zh;
            this.en = en;
            this.category = category;
        }

        String label() {
            return ConfigManager.tr(zh, en);
        }
    }

    public static final int WIN_W = 388;
    public static final int WIN_H = 282;
    private static final int EDGE_MARGIN = 16;
    private static final int GRID_COLS = 4;

    private static boolean open = false;
    private static float openAnim = 0.0f;
    private static float pageSlide = 0.0f;
    private static float tabIndicatorX = -1.0f;
    private static float tabIndicatorW = 54.0f;

    public static int listeningKeySlot = 0; // 0=none, 1=menuKey, 2=mouseUnlockKey
    public static Module listeningModuleKey = null;

    // Inline profile naming state: 0=none, 1=create new profile, 2=rename active profile
    public static int profileInputMode = 0;
    public static String profileInputBuffer = "";

    private static String toastText = "";
    private static int toastColor = 0xFF22C55E;
    private static long toastUntilMs = 0L;

    private static TopSection activeSection = TopSection.PLAYER_MODEL;
    private static Module activeModule = null;
    private static int selectedIndex = 0;

    private static double scrollOffset = 0.0;
    private static double targetScroll = 0.0;
    private static double maxScroll = 0.0;

    private static EnumSetting<?> openDropdown = null;
    private static int dropX, dropY, dropW;
    private static double dropScroll = 0.0;

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
            openDropdown = null;
            listeningKeySlot = 0;
            listeningModuleKey = null;
            profileInputMode = 0;
            ConfigManager.saveAsync();
        }
    }

    public static void setOpen(boolean v) {
        open = v;
        if (!open) {
            openDropdown = null;
            listeningKeySlot = 0;
            listeningModuleKey = null;
            profileInputMode = 0;
        }
    }

    public static void playClick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.getSoundManager() != null) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }
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
        if (openDropdown != null && inBounds(mx, my, dropX, Math.max(4, dropY - 180), dropW, 360)) return true;
        return false;
    }

    public static void render(GuiGraphics g, int mouseX, int mouseY, boolean mouseUnlocked) {
        AppleUiRenderer.beginFrame();
        float dt = AppleUiRenderer.dt();
        openAnim = AppleUiRenderer.smooth(openAnim, open ? 1.0f : 0.0f, 18.0f, dt);
        pageSlide = AppleUiRenderer.smooth(pageSlide, 0.0f, 20.0f, dt);

        if (targetScroll > maxScroll) targetScroll = maxScroll;
        if (targetScroll < 0.0) targetScroll = 0.0;
        scrollOffset = AppleUiRenderer.smooth((float) scrollOffset, (float) targetScroll, 20.0f, dt);
        if (scrollOffset > maxScroll) scrollOffset = maxScroll;
        if (scrollOffset < 0.0) scrollOffset = 0.0;

        if (!open && openAnim < 0.02f) return;

        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();

        int slideY = Math.round((1.0f - openAnim) * 20.0f);
        int wx = getWinX(sw);
        int wy = getWinY(sh) + slideY;
        int accent = ConfigManager.getAccentColor();

        HIT_RECTS.clear();

        // 1. 3D Elevated Window Shell + Header Bar
        AppleUiRenderer.drawElevatedWindow(g, wx, wy, WIN_W, WIN_H, 12);
        AppleUiRenderer.drawRoundedRect(g, wx + 1, wy + 1, WIN_W - 2, 28, 11, AppleUiRenderer.bgHeader());
        g.fill(wx + 8, wy + 28, wx + WIN_W - 8, wy + 29, 0x22FFFFFF);

        // Brand emblem
        AppleUiRenderer.drawRoundedRect(g, wx + 9, wy + 7, 4, 14, 2, accent);
        AppleUiRenderer.drawText(g, font, "Ye", wx + 17, wy + 10, AppleUiRenderer.textPrimary());

        // 4 Top Section Tabs with smooth sliding indicator pill
        int tx = wx + 38;
        TopSection[] secs = TopSection.values();
        int targetTabX = tx;
        int targetTabW = 54;
        int[] tabXs = new int[secs.length];
        int[] tabWs = new int[secs.length];

        for (int i = 0; i < secs.length; i++) {
            String lbl = secs[i].label();
            int tw = Math.max(38, font.width(lbl) + 14);
            tabXs[i] = tx;
            tabWs[i] = tw;
            if (secs[i] == activeSection) {
                targetTabX = tx;
                targetTabW = tw;
            }
            tx += tw + 4;
        }

        if (tabIndicatorX < 0) {
            tabIndicatorX = targetTabX;
            tabIndicatorW = targetTabW;
        } else {
            tabIndicatorX = AppleUiRenderer.smooth(tabIndicatorX, targetTabX, 22.0f, dt);
            tabIndicatorW = AppleUiRenderer.smooth(tabIndicatorW, targetTabW, 22.0f, dt);
        }
        AppleUiRenderer.drawRoundedRect(g, Math.round(tabIndicatorX), wy + 5, Math.round(tabIndicatorW), 18, 6, accent);
        g.fill(Math.round(tabIndicatorX) + 4, wy + 5, Math.round(tabIndicatorX + tabIndicatorW) - 4, wy + 6, 0x45FFFFFF);

        for (int i = 0; i < secs.length; i++) {
            TopSection sec = secs[i];
            int bx = tabXs[i], bw = tabWs[i];
            boolean sel = (sec == activeSection);
            boolean hov = mouseUnlocked && inBounds(mouseX, mouseY, bx, wy + 5, bw, 18);
            if (!sel && hov) {
                AppleUiRenderer.drawRoundedRect(g, bx, wy + 5, bw, 18, 6, AppleUiRenderer.bgCardHover());
            }
            AppleUiRenderer.drawCenteredText(g, font, sec.label(), bx + bw / 2, wy + 10,
                    sel ? AppleUiRenderer.TEXT_PRIMARY : (hov ? AppleUiRenderer.textPrimary() : AppleUiRenderer.textSecondary()));
            HIT_RECTS.add(new HitRect(bx, wy + 5, bw, 18, () -> switchSection(sec), null, null));
        }

        String hint = mouseUnlocked ? ("档:" + ConfigManager.getActiveProfile())
                : (keyName(ConfigManager.getMouseUnlockKey()) + "鼠标·空格设置");
        if (tx + font.width(hint) + 10 < wx + WIN_W) {
            AppleUiRenderer.drawText(g, font, hint, wx + WIN_W - 9 - font.width(hint), wy + 10, AppleUiRenderer.textTertiary());
        }

        int bodyX = wx + 10 + Math.round(pageSlide);
        int bodyY = wy + 34;
        int bodyW = WIN_W - 20;
        int bodyH = WIN_H - 42;

        g.enableScissor(wx + 6, bodyY - 2, wx + WIN_W - 6, wy + WIN_H - 5);
        if (activeSection != TopSection.SETTINGS) {
            if (activeModule == null) {
                renderCategoryGrid(g, font, activeSection.category, bodyX, bodyY, bodyW, bodyH, mouseX, mouseY, mouseUnlocked, accent);
            } else {
                renderModuleDetail(g, font, bodyX, bodyY, bodyW, bodyH, mouseX, mouseY, mouseUnlocked, accent);
            }
        } else {
            renderSettingsTab(g, font, bodyX, bodyY, bodyW, bodyH, mouseX, mouseY, mouseUnlocked, accent);
        }
        g.disableScissor();

        if (openDropdown != null && mouseUnlocked) {
            renderDropdown(g, font, openDropdown, mouseX, mouseY, accent);
        }

        // Floating Toast Banner above window
        long rem = toastUntilMs - System.currentTimeMillis();
        if (rem > 0 && !toastText.isEmpty()) {
            float alpha = Math.min(1.0f, rem / 300.0f);
            int tw = font.width(toastText) + 20;
            int txBox = wx + (WIN_W - tw) / 2;
            int tyBox = Math.max(4, wy - 22);
            AppleUiRenderer.drawRoundedRect(g, txBox, tyBox, tw, 18, 6, AppleUiRenderer.withAlpha(0xEE0D121D, alpha));
            g.renderOutline(txBox, tyBox, tw, 18, AppleUiRenderer.withAlpha(toastColor, alpha));
            AppleUiRenderer.drawCenteredText(g, font, toastText, txBox + tw / 2, tyBox + 5,
                    AppleUiRenderer.withAlpha(toastColor, alpha));
        }
    }

    private static void switchSection(TopSection sec) {
        if (activeSection == sec && activeModule == null) return;
        playClick();
        pageSlide = (sec.ordinal() >= activeSection.ordinal()) ? 18.0f : -18.0f;
        activeSection = sec;
        activeModule = null;
        openDropdown = null;
        listeningModuleKey = null;
        profileInputMode = 0;
        selectedIndex = 0;
        scrollOffset = 0.0;
        targetScroll = 0.0;
    }

    private static void renderCategoryGrid(GuiGraphics g, Font font, Category cat, int x, int y, int w, int h,
                                           int mx, int my, boolean mouseUnlocked, int accent) {
        List<Module> mods = ModuleManager.getByCategory(cat);
        if (selectedIndex >= mods.size()) selectedIndex = Math.max(0, mods.size() - 1);

        int gap = 6;
        int cellW = (w - 4 - (GRID_COLS - 1) * gap) / GRID_COLS;
        int cellH = 38;
        int rowStep = cellH + gap;
        int totalRows = (mods.size() + GRID_COLS - 1) / GRID_COLS;
        int contentH = Math.max(0, totalRows * rowStep - gap + 4);
        maxScroll = Math.max(0.0, contentH - h);

        g.enableScissor(x - 2, y - 2, x + w + 2, y + h + 2);
        int baseY = y - (int) Math.round(scrollOffset);
        int selX = -1, selY = -1;

        for (int i = 0; i < mods.size(); i++) {
            Module m = mods.get(i);
            int col = i % GRID_COLS;
            int row = i / GRID_COLS;
            int cx = x + col * (cellW + gap);
            int rawCy = baseY + row * rowStep;

            boolean hovered = mouseUnlocked && openDropdown == null && inBounds(mx, my, cx, rawCy, cellW, cellH);
            boolean keySel = !mouseUnlocked && i == selectedIndex;

            float hovAnim = AppleUiRenderer.animChannel("mhov_" + m.getId(), (hovered || keySel) ? 1.0f : 0.0f, 18.0f);
            float actAnim = AppleUiRenderer.animChannel("mact_" + m.getId(), m.isEnabled() ? 1.0f : 0.0f, 16.0f);
            int cy = rawCy - Math.round(hovAnim * 1.5f);

            if (keySel) {
                selX = cx;
                selY = cy;
            }

            if (cy + cellH >= y - 2 && cy <= y + h + 2) {
                AppleUiRenderer.drawElevatedCard(g, cx, cy, cellW, cellH, 7, actAnim, hovAnim, accent);

                String name = ConfigManager.english ? m.getSubtitle() : m.getName();
                if (font.width(name) > cellW - 10) {
                    name = font.plainSubstrByWidth(name, cellW - 12);
                }
                int txtCol = m.isEnabled() ? AppleUiRenderer.TEXT_PRIMARY : AppleUiRenderer.textPrimary();
                AppleUiRenderer.drawCenteredText(g, font, name, cx + cellW / 2, cy + 10, txtCol);

                String sub = m.getKeybind() > 0 ? ("[" + keyName(m.getKeybind()) + "]")
                        : (m.isEnabled() ? "ON" : "OFF");
                int subCol = m.isEnabled() ? 0xCCFFFFFF : AppleUiRenderer.textTertiary();
                AppleUiRenderer.drawCenteredText(g, font, sub, cx + cellW / 2, cy + 23, subCol);

                if (!getDisplaySettings(m).isEmpty()) {
                    AppleUiRenderer.drawCircle(g, cx + cellW - 9, cy + 5, 4,
                            m.isEnabled() ? 0xDDFFFFFF : AppleUiRenderer.textTertiary());
                }

                if (rawCy + cellH > y + 2 && rawCy < y + h - 2) {
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
        }

        if (!mouseUnlocked && selX >= 0) {
            g.renderOutline(selX - 1, selY - 1, cellW + 2, cellH + 2, 0xEEFFFFFF);
        }
        drawScrollbar(g, x + w - 2, y, h, contentH, accent);
        g.disableScissor();
    }

    private static void openModuleDetail(Module m) {
        playClick();
        activeModule = m;
        listeningModuleKey = null;
        pageSlide = 20.0f;
        selectedIndex = 0;
        scrollOffset = 0.0;
        targetScroll = 0.0;
        openDropdown = null;
    }

    private static void renderModuleDetail(GuiGraphics g, Font font, int x, int y, int w, int h,
                                           int mx, int my, boolean mouseUnlocked, int accent) {
        // Detail Header: Back + Module Title + [重置] Single-Module Reset + [键: ...] Keybind + Switch
        boolean backHov = mouseUnlocked && inBounds(mx, my, x, y, 32, 18);
        AppleUiRenderer.drawElevatedCard(g, x, y, 32, 18, 6, 0f, backHov ? 1f : 0f, accent);
        AppleUiRenderer.drawCenteredText(g, font, "←", x + 16, y + 5, AppleUiRenderer.textPrimary());
        HIT_RECTS.add(new HitRect(x, y, 32, 18, () -> {
            playClick();
            activeModule = null;
            listeningModuleKey = null;
            pageSlide = -20.0f;
            openDropdown = null;
            selectedIndex = 0;
            scrollOffset = 0.0;
            targetScroll = 0.0;
        }, null, null));

        String mName = ConfigManager.english ? activeModule.getSubtitle() : activeModule.getName();
        AppleUiRenderer.drawText(g, font, mName, x + 38, y + 5, AppleUiRenderer.textPrimary());

        // Per-module Keybind Pill
        boolean listeningThis = (listeningModuleKey == activeModule);
        String kbText = listeningThis ? "按键..." : ("键:" + (activeModule.getKeybind() > 0 ? keyName(activeModule.getKeybind()) : "无"));
        int kbW = Math.max(42, font.width(kbText) + 10);
        int kbX = x + w - 32 - kbW - 4;
        AppleUiRenderer.drawRoundedRect(g, kbX, y + 1, kbW, 16, 5,
                listeningThis ? 0xFFEF4444 : AppleUiRenderer.bgCardHover());
        AppleUiRenderer.drawCenteredText(g, font, kbText, kbX + kbW / 2, y + 5,
                listeningThis ? AppleUiRenderer.TEXT_PRIMARY : AppleUiRenderer.textSecondary());
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

        // Single-Module Reset to Default Pill ("重置")
        String rstTxt = ConfigManager.tr("重置", "Reset");
        int rstW = Math.max(32, font.width(rstTxt) + 10);
        int rstX = kbX - rstW - 4;
        boolean rstHov = mouseUnlocked && inBounds(mx, my, rstX, y + 1, rstW, 16);
        AppleUiRenderer.drawRoundedRect(g, rstX, y + 1, rstW, 16, 5,
                rstHov ? 0xFFE11D48 : AppleUiRenderer.bgCardHover());
        AppleUiRenderer.drawCenteredText(g, font, rstTxt, rstX + rstW / 2, y + 5,
                rstHov ? AppleUiRenderer.TEXT_PRIMARY : AppleUiRenderer.textSecondary());
        HIT_RECTS.add(new HitRect(rstX, y + 1, rstW, 16, () -> {
            playClick();
            activeModule.resetToDefault();
            openDropdown = null;
            showToast("已将「" + mName + "」恢复默认", 0xFF38BDF8);
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
        List<Setting<?>> settings = getDisplaySettings(activeModule);
        if (selectedIndex >= settings.size()) selectedIndex = Math.max(0, settings.size() - 1);

        g.enableScissor(x, listY, x + w, listY + listH);
        int startRy = listY - (int) Math.round(scrollOffset);
        int ry = startRy;

        ry += YePreviewRenderer.renderModulePreviewCard(g, font, activeModule, x, ry, rowW);

        if (activeModule instanceof CustomCapeModule cm) {
            for (CapeManager.CapeEntry ce : CapeManager.getAllCapes()) {
                if (ry + 22 >= listY && ry <= listY + listH) {
                    boolean sel = ce.id().equals(cm.selectedCape.get());
                    boolean hov = mouseUnlocked && openDropdown == null && inBounds(mx, my, x, ry, rowW, 20);
                    AppleUiRenderer.drawElevatedCard(g, x, ry, rowW, 20, 5, sel ? 1f : 0f, hov ? 1f : 0f, accent);
                    g.blit(RenderPipelines.GUI_TEXTURED, ce.textureId(), x + 5, ry + 2, 1.0f, 1.0f, 10, 16, 10, 16, 64, 32);
                    AppleUiRenderer.drawText(g, font, ce.displayName(), x + 21, ry + 6,
                            sel ? AppleUiRenderer.TEXT_PRIMARY : AppleUiRenderer.textPrimary());
                    if (ry + 20 > listY + 2 && ry < listY + listH - 2) {
                        HIT_RECTS.add(new HitRect(x, ry, rowW, 20, () -> {
                            playClick();
                            cm.selectedCape.set(ce.id());
                            cm.setEnabled(true);
                            ConfigManager.saveAsync();
                        }, null, null));
                    }
                }
                ry += 23;
            }
        } else if (activeModule instanceof CustomSkinModule sm) {
            List<SkinManager.SkinEntry> skins = SkinManager.getAllSkins();
            int colW = (rowW - 4) / 2;
            for (int i = 0; i < skins.size(); i++) {
                SkinManager.SkinEntry se = skins.get(i);
                int c = i % 2;
                int cx = x + c * (colW + 4);
                int cy = ry + (i / 2) * 23;
                if (cy + 20 >= listY && cy <= listY + listH) {
                    boolean sel = se.id().equals(sm.selectedSkin.get());
                    boolean hov = mouseUnlocked && openDropdown == null && inBounds(mx, my, cx, cy, colW, 20);
                    AppleUiRenderer.drawElevatedCard(g, cx, cy, colW, 20, 5, sel ? 1f : 0f, hov ? 1f : 0f, accent);
                    g.blit(RenderPipelines.GUI_TEXTURED, se.textureId(), cx + 4, cy + 2, 8.0f, 8.0f, 16, 16, 8, 8, 64, 64);
                    g.blit(RenderPipelines.GUI_TEXTURED, se.textureId(), cx + 4, cy + 2, 40.0f, 8.0f, 16, 16, 8, 8, 64, 64);
                    String sName = font.plainSubstrByWidth(se.displayName(), colW - 26);
                    AppleUiRenderer.drawText(g, font, sName, cx + 23, cy + 6,
                            sel ? AppleUiRenderer.TEXT_PRIMARY : AppleUiRenderer.textPrimary());
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
            ry += ((skins.size() + 1) / 2) * 23 + 2;
        }

        for (int i = 0; i < settings.size(); i++) {
            Setting<?> s = settings.get(i);
            if ((activeModule instanceof CustomCapeModule && s.getId().equals("selectedCape"))
                    || (activeModule instanceof CustomSkinModule && s.getId().equals("selectedSkin"))) {
                continue;
            }
            int rowH = (s instanceof DoubleSetting) ? 30 : 22;
            if (ry + rowH >= listY && ry <= listY + listH) {
                boolean keySel = !mouseUnlocked && i == selectedIndex;
                boolean hov = mouseUnlocked && openDropdown == null && inBounds(mx, my, x, ry, rowW, rowH);
                float hProg = AppleUiRenderer.animChannel("shov_" + activeModule.getId() + "_" + s.getId(),
                        (hov || keySel) ? 1f : 0f, 18f);
                AppleUiRenderer.drawElevatedCard(g, x, ry, rowW, rowH, 6, 0f, hProg, accent);
                if (keySel) g.renderOutline(x, ry, rowW, rowH, accent);

                AppleUiRenderer.drawText(g, font, s.getName(), x + 8, ry + 6, AppleUiRenderer.textPrimary());
                boolean inClip = ry + rowH > listY + 2 && ry < listY + listH - 2;

                if (s instanceof BoolSetting bs) {
                    float bProg = AppleUiRenderer.animChannel("bsw_" + activeModule.getId() + "_" + bs.getId(),
                            bs.get() ? 1f : 0f, 18f);
                    AppleUiRenderer.drawPillSwitch(g, x + rowW - 34, ry + 4, 28, 14, bProg, accent);
                    if (inClip) {
                        HIT_RECTS.add(new HitRect(x, ry, rowW, rowH, () -> {
                            playClick();
                            openDropdown = null;
                            bs.toggle();
                            ConfigManager.saveAsync();
                        }, null, null));
                    }
                } else if (s instanceof DoubleSetting ds) {
                    String vt = formatDouble(ds.get());
                    AppleUiRenderer.drawText(g, font, vt, x + rowW - 8 - font.width(vt), ry + 5, accent);
                    int bw = rowW - 16;
                    int bx = x + 8;
                    int by = ry + 20;
                    float targetPct = (float) ((ds.get() - ds.getMin()) / Math.max(0.0001, ds.getMax() - ds.getMin()));
                    float pct = AppleUiRenderer.animChannel("dsl_" + activeModule.getId() + "_" + ds.getId(), targetPct, 22f);
                    AppleUiRenderer.drawRoundedRect(g, bx, by, bw, 4, 2, 0xFF283142);
                    int fillW = Math.max(4, Math.round(bw * pct));
                    AppleUiRenderer.drawRoundedRect(g, bx, by, fillW, 4, 2, accent);
                    AppleUiRenderer.drawCircle(g, bx + fillW - 4, by - 2, 8, 0xFFFFFFFF);
                    if (inClip) {
                        HIT_RECTS.add(new HitRect(bx - 3, by - 6, bw + 6, 16, null, null, ds));
                    }
                } else if (s instanceof EnumSetting<?> es) {
                    String val = enumLabel(es.get()) + " ▾";
                    int bw = Math.min(rowW / 2 + 24, Math.max(82, font.width(val) + 14));
                    int bx = x + rowW - 5 - bw;
                    int by = ry + 3;
                    boolean isOpen = (openDropdown == es);
                    AppleUiRenderer.drawRoundedRect(g, bx, by, bw, 16, 5, isOpen ? accent : AppleUiRenderer.bgCardHover());
                    AppleUiRenderer.drawCenteredText(g, font, val, bx + bw / 2, by + 4,
                            isOpen ? AppleUiRenderer.TEXT_PRIMARY : AppleUiRenderer.textPrimary());
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
                    int bx = x + rowW - 50;
                    AppleUiRenderer.drawRoundedRect(g, bx, ry + 3, 44, 16, 5, accent);
                    AppleUiRenderer.drawCenteredText(g, font, "执行", bx + 22, ry + 7, AppleUiRenderer.TEXT_PRIMARY);
                    if (inClip) {
                        HIT_RECTS.add(new HitRect(bx, ry + 3, 44, 16, () -> {
                            playClick();
                            as.trigger();
                        }, null, null));
                    }
                }
            }
            ry += rowH + 3;
        }

        int contentH = (ry - startRy) + 4;
        maxScroll = Math.max(0.0, contentH - listH);
        drawScrollbar(g, x + w - 2, listY, listH, contentH, accent);
        g.disableScissor();
    }

    private static <E extends Enum<E>> void renderDropdown(GuiGraphics g, Font font, EnumSetting<E> es,
                                                            int mx, int my, int accent) {
        E[] vals = es.getDefaultValue().getDeclaringClass().getEnumConstants();
        int itemH = (vals.length > 0 && vals[0] instanceof com.ye.mod.modules.visual.CrosshairModule.Preset) ? 19 : 17;
        int maxVisItems = 9;
        int fullH = vals.length * itemH + 4;
        int boxH = Math.min(fullH, maxVisItems * itemH + 4);
        double maxDropSc = Math.max(0.0, fullH - boxH);
        dropScroll = Math.max(0.0, Math.min(maxDropSc, dropScroll));

        int sh = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        int dy = (dropY + boxH + 6 > sh) ? Math.max(6, dropY - 20 - boxH) : dropY;

        AppleUiRenderer.drawElevatedWindow(g, dropX, dy, dropW, boxH, 7);
        g.renderOutline(dropX, dy, dropW, boxH, accent);

        g.enableScissor(dropX + 1, dy + 2, dropX + dropW - 1, dy + boxH - 2);
        for (int i = 0; i < vals.length; i++) {
            E opt = vals[i];
            int iy = dy + 2 + i * itemH - (int) Math.round(dropScroll);
            if (iy + itemH < dy || iy > dy + boxH) continue;

            boolean sel = (opt == es.get());
            boolean hov = inBounds(mx, my, dropX + 2, iy, dropW - 4, itemH);
            int bg = sel ? accent : (hov ? AppleUiRenderer.bgCardHover() : AppleUiRenderer.bgCard());
            AppleUiRenderer.drawRoundedRect(g, dropX + 2, iy, dropW - 4, itemH - 1, 4, bg);
            int swOff = YePreviewRenderer.renderOptionSwatch(g, opt, dropX + 5, iy + 2, 12);
            AppleUiRenderer.drawText(g, font, enumLabel(opt), dropX + 6 + swOff, iy + (itemH - 8) / 2,
                    sel ? AppleUiRenderer.TEXT_PRIMARY : AppleUiRenderer.textPrimary());
            if (iy >= dy && iy + itemH <= dy + boxH + 2) {
                HIT_RECTS.add(new HitRect(dropX + 2, iy, dropW - 4, itemH, () -> {
                    playClick();
                    es.set(opt);
                    openDropdown = null;
                    ConfigManager.saveAsync();
                }, null, null));
            }
        }
        g.disableScissor();
    }

    private static void renderSettingsTab(GuiGraphics g, Font font, int x, int y, int w, int h,
                                          int mx, int my, boolean mouseUnlocked, int accent) {
        g.enableScissor(x, y, x + w, y + h);
        int startRy = y - (int) Math.round(scrollOffset);
        int ry = startRy;
        int rowW = w - 6;

        // ================= 1. CONFIG PROFILE MANAGER SECTION =================
        String hdr = ConfigManager.tr(
                "配置档案 · 账号: " + ConfigManager.getCurrentAccountName() + " (当前: " + ConfigManager.getActiveProfile() + ")",
                "Profiles · User: " + ConfigManager.getCurrentAccountName() + " (" + ConfigManager.getActiveProfile() + ")");
        AppleUiRenderer.drawText(g, font, hdr, x + 2, ry, AppleUiRenderer.textSecondary());
        ry += 13;

        // Profile Pills Grid (3 columns)
        List<String> profiles = ConfigManager.listAvailableProfiles();
        int pCols = 3;
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
                    : font.plainSubstrByWidth(pName, pCellW - 10);
            AppleUiRenderer.drawCenteredText(g, font, disp, pcx + pCellW / 2, pcy + 5,
                    sel ? AppleUiRenderer.TEXT_PRIMARY : AppleUiRenderer.textPrimary());
            if (pcy + 18 > y && pcy < y + h) {
                HIT_RECTS.add(new HitRect(pcx, pcy, pCellW, 18, () -> {
                    playClick();
                    profileInputMode = 0;
                    ConfigManager.switchToProfile(pName, true);
                    showToast("已切换配置: " + pName, accent);
                }, null, null));
            }
        }
        ry += ((profiles.size() + pCols - 1) / pCols) * 21 + 3;

        // Inline Text Input Box (when creating or renaming a profile)
        if (profileInputMode != 0) {
            AppleUiRenderer.drawRoundedRect(g, x, ry, rowW, 22, 6, 0xEE090D16);
            g.renderOutline(x, ry, rowW, 22, accent);
            String prefix = (profileInputMode == 1) ? "新建名称: " : "重命名为: ";
            String cursor = ((System.currentTimeMillis() / 450L) % 2 == 0) ? "|" : "";
            AppleUiRenderer.drawText(g, font, prefix + profileInputBuffer + cursor, x + 7, ry + 7, 0xFFF8FAFC);

            int okX = x + rowW - 44;
            AppleUiRenderer.drawRoundedRect(g, okX, ry + 3, 40, 16, 4, accent);
            AppleUiRenderer.drawCenteredText(g, font, "确定", okX + 20, ry + 7, AppleUiRenderer.TEXT_PRIMARY);
            if (ry + 22 > y && ry < y + h) {
                HIT_RECTS.add(new HitRect(okX, ry + 3, 40, 16, YeQuickMenu::confirmProfileInput, null, null));
            }
            ry += 26;
        }

        // 2x3 Action Grid for Config Operations
        int halfW = (rowW - 6) / 2;
        drawActionBtn(g, font, x, ry, halfW, ConfigManager.tr("+ 新建配置档", "+ New Profile"), mx, my, mouseUnlocked, accent, y, h, () -> {
            playClick();
            profileInputMode = 1;
            profileInputBuffer = ConfigManager.getCurrentAccountName() + "_pvp";
        });
        drawActionBtn(g, font, x + halfW + 6, ry, halfW, ConfigManager.tr("重命名当前配置", "Rename Profile"), mx, my, mouseUnlocked, accent, y, h, () -> {
            playClick();
            if (ConfigManager.getActiveProfile().equalsIgnoreCase(ConfigManager.DEFAULT_PROFILE)) {
                showToast("出厂 default 不可重命名", 0xFFEF4444);
            } else {
                profileInputMode = 2;
                profileInputBuffer = ConfigManager.getActiveProfile();
            }
        });
        ry += 22;

        drawActionBtn(g, font, x, ry, halfW, ConfigManager.tr("设为新号默认模板", "Set as Template"), mx, my, mouseUnlocked, accent, y, h, () -> {
            playClick();
            ConfigManager.saveCurrentAsDefaultTemplate();
            showToast("✓ 已保存为新玩家继承模板", 0xFF22C55E);
        });
        drawActionBtn(g, font, x + halfW + 6, ry, halfW, ConfigManager.tr("恢复出厂 default", "Reset to Default"), mx, my, mouseUnlocked, accent, y, h, () -> {
            playClick();
            ConfigManager.resetCurrentToFactoryDefault();
            showToast("✓ 已恢复出厂默认参数", 0xFF38BDF8);
        });
        ry += 22;

        drawActionBtn(g, font, x, ry, halfW, ConfigManager.tr("复制剪贴板配置码", "Copy Share Code"), mx, my, mouseUnlocked, accent, y, h, () -> {
            playClick();
            String code = ConfigManager.exportShareCodeToClipboard();
            if (code != null) showToast("✓ 已复制配置码 (" + code.length() + "字)", 0xFF22C55E);
            else showToast("复制配置码失败", 0xFFEF4444);
        });
        drawActionBtn(g, font, x + halfW + 6, ry, halfW, ConfigManager.tr("粘贴导入配置码", "Paste Share Code"), mx, my, mouseUnlocked, accent, y, h, () -> {
            playClick();
            if (ConfigManager.importShareCodeFromClipboard()) {
                showToast("✓ 已从剪贴板导入整套配置!", 0xFF22C55E);
            } else {
                showToast("剪贴板无有效 YE1 配置码", 0xFFEF4444);
            }
        });
        ry += 22;

        drawActionBtn(g, font, x, ry, rowW, ConfigManager.tr("打开 configs 配置文件夹 (导入/导出 .json)", "Open configs Folder"), mx, my, mouseUnlocked, accent, y, h, () -> {
            playClick();
            ConfigManager.openConfigsFolder();
        });
        ry += 26;

        // ================= 2. UI APPEARANCE & HOTKEYS SECTION =================
        AppleUiRenderer.drawText(g, font, ConfigManager.tr("界面与快捷键", "UI & Hotkeys"), x + 2, ry, AppleUiRenderer.textSecondary());
        ry += 13;

        // Appearance
        AppleUiRenderer.drawElevatedCard(g, x, ry, rowW, 21, 6, 0f,
                (mouseUnlocked && inBounds(mx, my, x, ry, rowW, 21)) ? 1f : 0f, accent);
        AppleUiRenderer.drawText(g, font, ConfigManager.tr("深浅外观", "Appearance"), x + 8, ry + 6, AppleUiRenderer.textPrimary());
        String tVal = ConfigManager.lightTheme ? ConfigManager.tr("浅色", "Light") : ConfigManager.tr("深色", "Dark");
        AppleUiRenderer.drawRoundedRect(g, x + rowW - 52, ry + 3, 46, 15, 5, accent);
        AppleUiRenderer.drawCenteredText(g, font, tVal, x + rowW - 29, ry + 6, AppleUiRenderer.TEXT_PRIMARY);
        if (ry + 21 > y && ry < y + h) {
            HIT_RECTS.add(new HitRect(x, ry, rowW, 21, () -> {
                playClick();
                AppleUiRenderer.toggleTheme();
            }, null, null));
        }
        ry += 24;

        // Language
        AppleUiRenderer.drawElevatedCard(g, x, ry, rowW, 21, 6, 0f,
                (mouseUnlocked && inBounds(mx, my, x, ry, rowW, 21)) ? 1f : 0f, accent);
        AppleUiRenderer.drawText(g, font, ConfigManager.tr("语言", "Language"), x + 8, ry + 6, AppleUiRenderer.textPrimary());
        String lVal = ConfigManager.english ? "EN" : "中文";
        AppleUiRenderer.drawRoundedRect(g, x + rowW - 52, ry + 3, 46, 15, 5, accent);
        AppleUiRenderer.drawCenteredText(g, font, lVal, x + rowW - 29, ry + 6, AppleUiRenderer.TEXT_PRIMARY);
        if (ry + 21 > y && ry < y + h) {
            HIT_RECTS.add(new HitRect(x, ry, rowW, 21, () -> {
                playClick();
                ConfigManager.english = !ConfigManager.english;
                ConfigManager.saveAsync();
            }, null, null));
        }
        ry += 24;

        // Arrow Key UI Hotkey ("方向键UI")
        AppleUiRenderer.drawElevatedCard(g, x, ry, rowW, 21, 6, 0f,
                (mouseUnlocked && inBounds(mx, my, x, ry, rowW, 21)) ? 1f : 0f, accent);
        AppleUiRenderer.drawText(g, font, ConfigManager.tr("方向键UI", "Arrow UI"), x + 8, ry + 6, AppleUiRenderer.textPrimary());
        String mkVal = listeningKeySlot == 1 ? ConfigManager.tr("请按键...", "Press...") : keyName(ConfigManager.getMenuKey());
        AppleUiRenderer.drawRoundedRect(g, x + rowW - 62, ry + 3, 56, 15, 5, listeningKeySlot == 1 ? 0xFFEF4444 : accent);
        AppleUiRenderer.drawCenteredText(g, font, mkVal, x + rowW - 34, ry + 6, AppleUiRenderer.TEXT_PRIMARY);
        if (ry + 21 > y && ry < y + h) {
            HIT_RECTS.add(new HitRect(x, ry, rowW, 21, () -> {
                playClick();
                listeningModuleKey = null;
                listeningKeySlot = (listeningKeySlot == 1) ? 0 : 1;
            }, null, null));
        }
        ry += 24;

        // Mouse UI Hotkey ("鼠标UI")
        AppleUiRenderer.drawElevatedCard(g, x, ry, rowW, 21, 6, 0f,
                (mouseUnlocked && inBounds(mx, my, x, ry, rowW, 21)) ? 1f : 0f, accent);
        AppleUiRenderer.drawText(g, font, ConfigManager.tr("鼠标UI", "Mouse UI"), x + 8, ry + 6, AppleUiRenderer.textPrimary());
        String umVal = listeningKeySlot == 2 ? ConfigManager.tr("请按键...", "Press...") : keyName(ConfigManager.getMouseUnlockKey());
        AppleUiRenderer.drawRoundedRect(g, x + rowW - 62, ry + 3, 56, 15, 5, listeningKeySlot == 2 ? 0xFFEF4444 : accent);
        AppleUiRenderer.drawCenteredText(g, font, umVal, x + rowW - 34, ry + 6, AppleUiRenderer.TEXT_PRIMARY);
        if (ry + 21 > y && ry < y + h) {
            HIT_RECTS.add(new HitRect(x, ry, rowW, 21, () -> {
                playClick();
                listeningModuleKey = null;
                listeningKeySlot = (listeningKeySlot == 2) ? 0 : 2;
            }, null, null));
        }
        ry += 26;

        // Concise Pure Accent Color Swatches (蓝 / 紫 / 金 / 红 / 绿 / 粉)
        AppleUiRenderer.drawText(g, font, ConfigManager.tr("主题色", "Theme Color"), x + 2, ry, AppleUiRenderer.textSecondary());
        ry += 13;
        ConfigManager.ThemeAccent[] accents = ConfigManager.ThemeAccent.values();
        int cw = (rowW - 25) / 6;
        for (int i = 0; i < accents.length; i++) {
            ConfigManager.ThemeAccent ta = accents[i];
            int cx = x + i * (cw + 5);
            boolean sel = ConfigManager.themeAccent == ta;
            boolean hov = mouseUnlocked && inBounds(mx, my, cx, ry, cw, 21);
            AppleUiRenderer.drawElevatedCard(g, cx, ry, cw, 21, 6, sel ? 1f : 0f, hov ? 1f : 0f, ta.argb);
            if (!sel) {
                AppleUiRenderer.drawCircle(g, cx + 6, ry + 5, 10, ta.argb);
            }
            String label = ConfigManager.english ? ta.en : ta.zh;
            AppleUiRenderer.drawCenteredText(g, font, label, cx + cw / 2 + (sel ? 0 : 5), ry + 6,
                    sel ? AppleUiRenderer.TEXT_PRIMARY : AppleUiRenderer.textPrimary());
            if (ry + 21 > y && ry < y + h) {
                HIT_RECTS.add(new HitRect(cx, ry, cw, 21, () -> {
                    playClick();
                    ConfigManager.themeAccent = ta;
                    ConfigManager.saveAsync();
                }, null, null));
            }
        }
        ry += 26;

        drawActionBtn(g, font, x, ry, rowW, ConfigManager.tr("重置菜单与HUD窗口位置", "Reset Window & HUD Pos"), mx, my, mouseUnlocked, accent, y, h, () -> {
            playClick();
            ConfigManager.widgetX = -1;
            ConfigManager.widgetY = -1;
            ConfigManager.resetHudSlotsDefault();
            ConfigManager.saveAsync();
            showToast("已重置菜单与 HUD 坐标", accent);
        });
        ry += 24;

        int contentH = ry - startRy;
        maxScroll = Math.max(0.0, contentH - h);
        drawScrollbar(g, x + w - 2, y, h, contentH, accent);
        g.disableScissor();
    }

    private static void drawActionBtn(GuiGraphics g, Font font, int x, int y, int w, String text,
                                      int mx, int my, boolean mouseUnlocked, int accent, int clipY, int clipH, Runnable onClick) {
        boolean hov = mouseUnlocked && inBounds(mx, my, x, y, w, 19);
        AppleUiRenderer.drawElevatedCard(g, x, y, w, 19, 5, 0f, hov ? 1f : 0f, accent);
        AppleUiRenderer.drawCenteredText(g, font, text, x + w / 2, y + 5, AppleUiRenderer.textPrimary());
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
                showToast("✓ 已新建并切换配置: " + created, 0xFF22C55E);
            } else if (profileInputMode == 2) {
                if (ConfigManager.renameActiveProfile(name)) {
                    showToast("✓ 已重命名为: " + ConfigManager.getActiveProfile(), 0xFF22C55E);
                } else {
                    showToast("重命名失败", 0xFFEF4444);
                }
            }
        }
        profileInputMode = 0;
    }

    private static void drawScrollbar(GuiGraphics g, int barX, int viewY, int viewH, int contentH, int accent) {
        if (contentH <= viewH + 2) return;
        g.fill(barX, viewY + 2, barX + 2, viewY + viewH - 2, 0x22FFFFFF);
        int trackH = viewH - 4;
        int thumbH = Math.max(18, Math.round((viewH / (float) contentH) * trackH));
        float ratio = (float) (scrollOffset / Math.max(1.0, contentH - viewH));
        int thumbY = viewY + 2 + Math.round((trackH - thumbH) * Math.max(0f, Math.min(1f, ratio)));
        g.fill(barX, thumbY, barX + 2, thumbY + thumbH, AppleUiRenderer.withAlpha(accent, 0.85f));
    }

    public static String enumLabel(Enum<?> e) {
        if (e == null) return "";
        if (!ConfigManager.english) {
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
                    if (v instanceof String s && !s.isEmpty()) return s;
                }
            } catch (Exception ignored) {}
        }
        return e.name();
    }

    public static String keyName(int key) {
        if (key <= 0) return "无";
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
        if (profileInputMode != 0) {
            profileInputMode = 0;
            return true;
        }
        if (listeningModuleKey != null) {
            listeningModuleKey = null;
            return true;
        }
        if (openDropdown != null) {
            openDropdown = null;
            return true;
        }
        if (activeSection != TopSection.SETTINGS && activeModule != null) {
            activeModule = null;
            pageSlide = -20.0f;
            selectedIndex = 0;
            scrollOffset = 0.0;
            targetScroll = 0.0;
            return true;
        }
        if (activeSection != TopSection.PLAYER_MODEL) {
            activeSection = TopSection.PLAYER_MODEL;
            pageSlide = -20.0f;
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
                List<Module> mods = ModuleManager.getByCategory(activeSection.category);
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
                double rowTop = row * 44.0;
                if (rowTop < targetScroll) targetScroll = rowTop;
                else if (rowTop + 40 > targetScroll + (WIN_H - 42)) targetScroll = rowTop + 40 - (WIN_H - 42);
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
            switchSection(TopSection.UTILITY);
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
        targetScroll = Math.max(0.0, Math.min(maxScroll, targetScroll - delta * 22.0));
    }

    private static boolean inBounds(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    private static String formatDouble(double v) {
        if (Math.abs(v - Math.round(v)) < 0.001) return String.valueOf((long) Math.round(v));
        return String.format(Locale.ROOT, "%.2f", v);
    }
}
