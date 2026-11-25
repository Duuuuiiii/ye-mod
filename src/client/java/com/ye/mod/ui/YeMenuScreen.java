package com.ye.mod.ui;

import com.ye.mod.core.Category;
import com.ye.mod.core.ConfigManager;
import com.ye.mod.core.Module;
import com.ye.mod.core.ModuleManager;
import com.ye.mod.modules.system.PlayerStatsManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Refined sharp acrylic Meteor-style ClickGUI with synchronized panel collapse,
 * smooth theme transitions, optional background blur, CN/EN language, and HUD layout editor.
 */
public class YeMenuScreen extends Screen {
    private enum OverlayMode {
        NONE,
        STATS,
        UI_SETTINGS,
        HUD_EDITOR
    }

    private enum StatsTab {
        MESSAGES("按发言", "Chat"),
        KILLS("按击杀", "Kills"),
        DEATHS("按死亡", "Deaths"),
        MUTED("黑名单", "Muted");

        final String zh;
        final String en;
        StatsTab(String zh, String en) { this.zh = zh; this.en = en; }
        String label() { return ConfigManager.tr(zh, en); }
    }

    private static final class PanelState {
        int x;
        int y;
        boolean collapsed;
        float expandAnim;

        PanelState(int x, int y, boolean collapsed) {
            this.x = x;
            this.y = y;
            this.collapsed = collapsed;
            this.expandAnim = collapsed ? 0.0f : 1.0f;
        }
    }

    private static final Map<Category, PanelState> PANEL_STATES = new EnumMap<>(Category.class);
    private static final Map<String, Float> MODULE_ENABLE_ANIM = new HashMap<>();
    private static final Map<String, Float> MODULE_HOVER_ANIM = new HashMap<>();
    private static int lastLayoutW = -1;

    private static final int PANEL_W = 122;
    private static final int HEADER_H = 21;
    private static final int ROW_H = 18;

    private float openProgress = 0.0f;
    private String searchQuery = "";
    private boolean searchFocused = false;

    private Category draggingCategory = null;
    private int dragOffsetX = 0;
    private int dragOffsetY = 0;

    private ConfigManager.HudSlot draggingHud = null;
    private int hudDragOffX = 0;
    private int hudDragOffY = 0;

    private OverlayMode overlayMode = OverlayMode.NONE;
    private StatsTab statsTab = StatsTab.MESSAGES;
    private double statsScroll = 0;
    private String muteInput = "";
    private boolean muteInputFocused = false;
    private boolean listeningMenuKey = false;

    private final List<HitArea> hitAreas = new ArrayList<>();

    private record HitArea(int x, int y, int w, int h, Runnable leftClick, Runnable rightClick) {}

    public YeMenuScreen() {
        super(Component.literal("Ye ClickGUI"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        super.init();
        if (PANEL_STATES.isEmpty() || lastLayoutW != this.width) {
            resetPanelLayout();
        }
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (ConfigManager.blurBackground) {
            super.renderBackground(g, mouseX, mouseY, partialTick);
        }
    }

    @Override
    public void onClose() {
        ConfigManager.saveAsync();
        super.onClose();
    }

    private void resetPanelLayout() {
        PANEL_STATES.clear();
        lastLayoutW = this.width;
        Category[] cats = Category.values();
        int gap = 10;
        int maxCols = Math.max(1, (this.width - 24 + gap) / (PANEL_W + gap));
        int cols = Math.min(cats.length, maxCols);
        int totalRowW = cols * PANEL_W + (cols - 1) * gap;
        int startX = Math.max(12, (this.width - totalRowW) / 2);
        int startY = 48;

        for (int i = 0; i < cats.length; i++) {
            int col = i % cols;
            int row = i / cols;
            PANEL_STATES.put(cats[i], new PanelState(startX + col * (PANEL_W + gap), startY + row * 190, false));
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        hitAreas.clear();
        AppleUiRenderer.updateThemeAnim();
        openProgress = AppleUiRenderer.smooth(openProgress, 1.0f, 16.0f, 0.03f);

        g.fill(0, 0, this.width, this.height, AppleUiRenderer.bgBackdrop());

        int slideY = Math.round((1.0f - openProgress) * 10.0f);
        g.pose().pushMatrix();
        g.pose().translate(0.0f, slideY);

        renderTopBar(g, mouseX, mouseY - slideY);

        if (overlayMode == OverlayMode.NONE) {
            String hoveredDesc = renderCategoryPanels(g, mouseX, mouseY - slideY);
            renderBottomHint(g, hoveredDesc);
        } else if (overlayMode == OverlayMode.STATS) {
            renderStatsOverlay(g, mouseX, mouseY - slideY);
        } else if (overlayMode == OverlayMode.UI_SETTINGS) {
            renderUiSettingsOverlay(g, mouseX, mouseY - slideY);
        } else if (overlayMode == OverlayMode.HUD_EDITOR) {
            renderHudEditorOverlay(g, mouseX, mouseY - slideY);
        }

        g.pose().popMatrix();
    }

    private void renderTopBar(GuiGraphics g, int mouseX, int mouseY) {
        int barW = Math.min(580, this.width - 20);
        int barH = 26;
        int bx = (this.width - barW) / 2;
        int by = 10;
        AppleUiRenderer.drawPanel(g, bx, by, barW, barH, AppleUiRenderer.bgWindow());

        // Brand tag
        int brandW = 54;
        g.fill(bx + 4, by + 4, bx + 4 + brandW, by + 22, AppleUiRenderer.APPLE_BLUE);
        AppleUiRenderer.drawCenteredText(g, this.font, "Ye 1.4.1", bx + 4 + brandW / 2, by + 9, AppleUiRenderer.TEXT_PRIMARY);

        int btnH = 18;
        int btnY = by + 4;

        int resetW = ConfigManager.english ? 42 : 36;
        int resetX = bx + barW - 4 - resetW;
        drawTopBtn(g, resetX, btnY, resetW, btnH, ConfigManager.tr("重置", "Reset"), false, mouseX, mouseY, () -> {
            overlayMode = OverlayMode.NONE;
            resetPanelLayout();
        });

        int uiW = ConfigManager.english ? 36 : 48;
        int uiX = resetX - 4 - uiW;
        drawTopBtn(g, uiX, btnY, uiW, btnH, ConfigManager.tr("UI设置", "UI"), overlayMode == OverlayMode.UI_SETTINGS, mouseX, mouseY, () -> {
            overlayMode = (overlayMode == OverlayMode.UI_SETTINGS) ? OverlayMode.NONE : OverlayMode.UI_SETTINGS;
            searchFocused = false;
            muteInputFocused = false;
        });

        int statsW = ConfigManager.english ? 42 : 54;
        int statsX = uiX - 4 - statsW;
        drawTopBtn(g, statsX, btnY, statsW, btnH, ConfigManager.tr("玩家统计", "Stats"), overlayMode == OverlayMode.STATS, mouseX, mouseY, () -> {
            overlayMode = (overlayMode == OverlayMode.STATS) ? OverlayMode.NONE : OverlayMode.STATS;
            searchFocused = false;
            muteInputFocused = false;
        });

        // Search box
        int searchX = bx + 4 + brandW + 6;
        int searchW = Math.max(60, statsX - 6 - searchX);
        int searchBg = searchFocused ? AppleUiRenderer.bgCardHover() : AppleUiRenderer.bgCard();
        AppleUiRenderer.drawPanel(g, searchX, btnY, searchW, btnH, searchBg);
        String disp = searchQuery.isEmpty()
                ? (searchFocused ? "|" : ConfigManager.tr("搜索...", "Search..."))
                : (searchQuery + (searchFocused ? "|" : ""));
        int sCol = searchQuery.isEmpty() && !searchFocused ? AppleUiRenderer.textTertiary() : AppleUiRenderer.textPrimary();
        AppleUiRenderer.drawText(g, this.font, trimToWidth(disp, searchW - 10), searchX + 5, btnY + 5, sCol);
        hitAreas.add(new HitArea(searchX, btnY, searchW, btnH, () -> {
            overlayMode = OverlayMode.NONE;
            searchFocused = true;
            muteInputFocused = false;
        }, () -> {
            searchQuery = "";
            searchFocused = false;
        }));
    }

    private void drawTopBtn(GuiGraphics g, int x, int y, int w, int h, String label, boolean active, int mx, int my, Runnable action) {
        boolean hover = inBounds(mx, my, x, y, w, h);
        int bg = active ? AppleUiRenderer.APPLE_BLUE : (hover ? AppleUiRenderer.bgCardHover() : AppleUiRenderer.bgCard());
        AppleUiRenderer.drawPanel(g, x, y, w, h, bg);
        AppleUiRenderer.drawCenteredText(g, this.font, label, x + w / 2, y + 5, active ? AppleUiRenderer.TEXT_PRIMARY : AppleUiRenderer.textPrimary());
        hitAreas.add(new HitArea(x, y, w, h, action, null));
    }

    private String renderCategoryPanels(GuiGraphics g, int mouseX, int mouseY) {
        String hoveredDesc = null;
        String q = searchQuery.trim().toLowerCase(Locale.ROOT);

        for (Category cat : Category.values()) {
            PanelState state = PANEL_STATES.computeIfAbsent(cat, c -> new PanelState(20, 48, false));
            List<Module> modules = filterModules(ModuleManager.getByCategory(cat), q);

            int px = Math.max(4, Math.min(this.width - PANEL_W - 4, state.x));
            int py = Math.max(42, Math.min(this.height - HEADER_H - 4, state.y));
            state.x = px;
            state.y = py;

            float targetExpand = (!state.collapsed && !modules.isEmpty()) ? 1.0f : 0.0f;
            state.expandAnim = AppleUiRenderer.smooth(state.expandAnim, targetExpand, 18.0f, 0.03f);

            int fullBodyH = modules.isEmpty() ? 0 : (modules.size() * ROW_H + 4);
            int animBodyH = Math.round(fullBodyH * state.expandAnim);
            int totalH = HEADER_H + animBodyH;

            // Single synchronized clip for entire panel so header and body never desync/leak!
            g.enableScissor(px, py, px + PANEL_W, py + totalH);
            AppleUiRenderer.drawPanel(g, px, py, PANEL_W, totalH, AppleUiRenderer.bgWindow());

            // Header bar with crisp 2px left accent bar
            g.fill(px + 1, py + 1, px + PANEL_W - 1, py + HEADER_H, AppleUiRenderer.bgSidebar());
            g.fill(px + 1, py + 1, px + 3, py + HEADER_H, cat.getAccentColor());
            String catName = ConfigManager.english ? cat.name() : cat.getDisplayName();
            AppleUiRenderer.drawText(g, this.font, catName, px + 8, py + 7, AppleUiRenderer.textPrimary());
            String countStr = String.valueOf(modules.size());
            AppleUiRenderer.drawText(g, this.font, countStr, px + PANEL_W - 6 - this.font.width(countStr), py + 7, AppleUiRenderer.textTertiary());

            hitAreas.add(new HitArea(px, py, PANEL_W, HEADER_H, null, () -> state.collapsed = !state.collapsed));

            if (animBodyH > 2) {
                int ry = py + HEADER_H + 2;
                for (Module m : modules) {
                    int rowX = px + 3;
                    int rowW = PANEL_W - 6;
                    boolean hover = !state.collapsed && inBounds(mouseX, mouseY, rowX, ry, rowW, ROW_H - 2);
                    if (hover) {
                        String mTitle = ConfigManager.english ? m.getSubtitle() : m.getName();
                        hoveredDesc = mTitle + " — " + m.getDescription();
                    }

                    float enAnim = AppleUiRenderer.smooth(MODULE_ENABLE_ANIM.getOrDefault(m.getId(), m.isEnabled() ? 1f : 0f), m.isEnabled() ? 1f : 0f, 16.0f, 0.03f);
                    float hvAnim = AppleUiRenderer.smooth(MODULE_HOVER_ANIM.getOrDefault(m.getId(), 0f), hover ? 1f : 0f, 16.0f, 0.03f);
                    MODULE_ENABLE_ANIM.put(m.getId(), enAnim);
                    MODULE_HOVER_ANIM.put(m.getId(), hvAnim);

                    int offBg = AppleUiRenderer.lerpColor(AppleUiRenderer.bgCard(), AppleUiRenderer.bgCardHover(), hvAnim);
                    int onBg = AppleUiRenderer.activeRowBg(hover);
                    int rowBg = AppleUiRenderer.lerpColor(offBg, onBg, enAnim);
                    g.fill(rowX, ry, rowX + rowW, ry + ROW_H - 2, rowBg);

                    // Left status indicator bar
                    int barCol = AppleUiRenderer.lerpColor(0x00000000, AppleUiRenderer.APPLE_GREEN, enAnim);
                    g.fill(rowX, ry, rowX + 2, ry + ROW_H - 2, barCol);

                    String mName = ConfigManager.english ? m.getSubtitle() : m.getName();
                    AppleUiRenderer.drawText(g, this.font, trimToWidth(mName, rowW - 10), rowX + 6, ry + 4, AppleUiRenderer.textPrimary());

                    if (!state.collapsed) {
                        hitAreas.add(new HitArea(rowX, ry, rowW, ROW_H - 2,
                                () -> {
                                    m.toggle();
                                    ConfigManager.saveAsync();
                                },
                                () -> Minecraft.getInstance().setScreen(new ModuleSettingsScreen(this, m))));
                    }
                    ry += ROW_H;
                }
            }
            g.disableScissor();
        }
        return hoveredDesc;
    }

    private void renderBottomHint(GuiGraphics g, String hoveredDesc) {
        String text = hoveredDesc != null
                ? hoveredDesc
                : ConfigManager.tr("左键开关 · 右键设置 · 拖拽标题移动 · 右键标题折叠", "LClick Toggle · RClick Settings · Drag Header · RClick Collapse");
        int tw = this.font.width(text);
        int pw = Math.min(this.width - 24, tw + 16);
        int px = (this.width - pw) / 2;
        int py = this.height - 22;
        AppleUiRenderer.drawPanel(g, px, py, pw, 16, AppleUiRenderer.bgWindow());
        AppleUiRenderer.drawCenteredText(g, this.font, trimToWidth(text, pw - 10), this.width / 2, py + 4, AppleUiRenderer.textSecondary());
    }

    private void renderUiSettingsOverlay(GuiGraphics g, int mouseX, int mouseY) {
        int mw = Math.min(440, this.width - 36);
        int mh = Math.min(340, this.height - 64);
        int mx = (this.width - mw) / 2;
        int my = 48;
        AppleUiRenderer.drawPanel(g, mx, my, mw, mh, AppleUiRenderer.bgWindow());

        AppleUiRenderer.drawText(g, this.font, ConfigManager.tr("UI 与全局设置", "UI & Global Settings"), mx + 12, my + 10, AppleUiRenderer.textPrimary());

        int ry = my + 30;
        int rw = mw - 24;

        // 1. Background blur
        drawSettingButtonRow(g, mx + 12, ry, rw, ConfigManager.tr("背景模糊", "Background Blur"),
                ConfigManager.blurBackground ? ConfigManager.tr("开", "ON") : ConfigManager.tr("关", "OFF"),
                ConfigManager.blurBackground, () -> {
                    ConfigManager.blurBackground = !ConfigManager.blurBackground;
                    ConfigManager.saveAsync();
                });
        ry += 28;

        // 2. Dark / Light Theme
        drawSettingButtonRow(g, mx + 12, ry, rw, ConfigManager.tr("界面主题", "Theme"),
                ConfigManager.lightTheme ? ConfigManager.tr("浅色", "Light") : ConfigManager.tr("深色", "Dark"),
                !ConfigManager.lightTheme, AppleUiRenderer::toggleTheme);
        ry += 28;

        // 3. Language
        drawSettingButtonRow(g, mx + 12, ry, rw, ConfigManager.tr("语言 / Language", "Language"),
                ConfigManager.english ? "English" : "中文",
                true, () -> {
                    ConfigManager.english = !ConfigManager.english;
                    ConfigManager.saveAsync();
                });
        ry += 28;

        // 4. Menu Keybind
        String menuKeyLabel = listeningMenuKey
                ? ConfigManager.tr("按下按键...", "Press key...")
                : ModuleSettingsScreen.getKeyName(ConfigManager.getMenuKey());
        drawSettingButtonRow(g, mx + 12, ry, rw, ConfigManager.tr("菜单快捷键", "Menu Keybind"),
                menuKeyLabel, listeningMenuKey, () -> listeningMenuKey = !listeningMenuKey);
        ry += 34;

        // 5. Bound module keybinds
        AppleUiRenderer.drawText(g, this.font, ConfigManager.tr("已绑定模块快捷键 (点击修改)", "Bound Module Keys"), mx + 12, ry, AppleUiRenderer.textTertiary());
        ry += 14;
        for (Module m : ModuleManager.getAll()) {
            if (m.getKeybind() > 0 && ry + 22 < my + mh - 8) {
                AppleUiRenderer.drawPanel(g, mx + 12, ry, rw, 20, AppleUiRenderer.bgCard());
                String name = ConfigManager.english ? m.getSubtitle() : m.getName();
                AppleUiRenderer.drawText(g, this.font, name, mx + 20, ry + 6, AppleUiRenderer.textPrimary());
                String k = ModuleSettingsScreen.getKeyName(m.getKeybind());
                AppleUiRenderer.drawText(g, this.font, k, mx + mw - 20 - this.font.width(k), ry + 6, AppleUiRenderer.APPLE_BLUE);
                hitAreas.add(new HitArea(mx + 12, ry, rw, 20,
                        () -> Minecraft.getInstance().setScreen(new ModuleSettingsScreen(this, m)), null));
                ry += 22;
            }
        }
    }

    private void drawSettingButtonRow(GuiGraphics g, int x, int y, int w, String label, String val, boolean highlight, Runnable onClick) {
        AppleUiRenderer.drawPanel(g, x, y, w, 24, AppleUiRenderer.bgCard());
        AppleUiRenderer.drawText(g, this.font, label, x + 10, y + 8, AppleUiRenderer.textPrimary());
        int bw = Math.max(54, this.font.width(val) + 14);
        int bx = x + w - 6 - bw;
        g.fill(bx, y + 4, bx + bw, y + 20, highlight ? AppleUiRenderer.APPLE_BLUE : AppleUiRenderer.bgCardHover());
        AppleUiRenderer.drawCenteredText(g, this.font, val, bx + bw / 2, y + 8,
                highlight ? AppleUiRenderer.TEXT_PRIMARY : AppleUiRenderer.textPrimary());
        hitAreas.add(new HitArea(x, y, w, 24, onClick, null));
    }

    private void renderHudEditorOverlay(GuiGraphics g, int mouseX, int mouseY) {
        String hint = ConfigManager.tr("拖拽方框调整 HUD 位置 · 右键方框单独开关显示", "Drag boxes to move HUD · RClick box to toggle");
        AppleUiRenderer.drawCenteredText(g, this.font, hint, this.width / 2, 46, AppleUiRenderer.textPrimary());

        for (ConfigManager.HudSlot slot : ConfigManager.HUD_SLOTS.values()) {
            String title = ConfigManager.english ? slot.enName : slot.zhName;
            String stateStr = slot.enabled ? ConfigManager.tr("开", "ON") : ConfigManager.tr("关", "OFF");
            String text = title + " [" + stateStr + "]";
            int bw = this.font.width(text) + 14;
            int bh = 18;
            int sx = Math.max(4, Math.min(this.width - bw - 4, slot.x));
            int sy = Math.max(60, Math.min(this.height - bh - 4, slot.y));
            slot.x = sx;
            slot.y = sy;

            boolean hover = inBounds(mouseX, mouseY, sx, sy, bw, bh);
            int bg = slot.enabled
                    ? (hover ? 0xCC1E40AF : 0xAA1D4ED8)
                    : (hover ? AppleUiRenderer.bgCardHover() : AppleUiRenderer.bgCard());
            AppleUiRenderer.drawPanel(g, sx, sy, bw, bh, bg);
            AppleUiRenderer.drawText(g, this.font, text, sx + 7, sy + 5, AppleUiRenderer.textPrimary());

            hitAreas.add(new HitArea(sx, sy, bw, bh, null, () -> {
                slot.enabled = !slot.enabled;
                ConfigManager.saveAsync();
            }));
        }
    }

    private void renderStatsOverlay(GuiGraphics g, int mouseX, int mouseY) {
        int mw = Math.min(560, this.width - 36);
        int mh = Math.min(360, this.height - 64);
        int mx = (this.width - mw) / 2;
        int my = 48;
        AppleUiRenderer.drawPanel(g, mx, my, mw, mh, AppleUiRenderer.bgWindow());

        int tx = mx + 10;
        int ty = my + 10;
        for (StatsTab tab : StatsTab.values()) {
            int tw = 58;
            boolean active = statsTab == tab;
            boolean hover = inBounds(mouseX, mouseY, tx, ty, tw, 20);
            AppleUiRenderer.drawPanel(g, tx, ty, tw, 20,
                    active ? AppleUiRenderer.APPLE_BLUE : (hover ? AppleUiRenderer.bgCardHover() : AppleUiRenderer.bgCard()));
            AppleUiRenderer.drawCenteredText(g, this.font, tab.label(), tx + tw / 2, ty + 6, AppleUiRenderer.textPrimary());
            hitAreas.add(new HitArea(tx, ty, tw, 20, () -> {
                statsTab = tab;
                statsScroll = 0;
            }, null));
            tx += tw + 5;
        }

        int clearW = 58;
        int clearX = mx + mw - 10 - clearW;
        AppleUiRenderer.drawPanel(g, clearX, ty, clearW, 20, 0xFF7F1D1D);
        AppleUiRenderer.drawCenteredText(g, this.font, ConfigManager.tr("清空", "Clear"), clearX + clearW / 2, ty + 6, AppleUiRenderer.TEXT_PRIMARY);
        hitAreas.add(new HitArea(clearX, ty, clearW, 20, PlayerStatsManager::clearCurrentStats, null));

        int logsW = 60;
        int logsX = clearX - 5 - logsW;
        AppleUiRenderer.drawPanel(g, logsX, ty, logsW, 20, AppleUiRenderer.bgCard());
        AppleUiRenderer.drawCenteredText(g, this.font, ConfigManager.tr("日志目录", "Logs"), logsX + logsW / 2, ty + 6, AppleUiRenderer.textPrimary());
        hitAreas.add(new HitArea(logsX, ty, logsW, 20, PlayerStatsManager::openLogsFolder, null));

        // Manual mute bar
        int inpY = ty + 26;
        int addMuteW = 66;
        int inpW = mw - 20 - addMuteW - 6;
        AppleUiRenderer.drawPanel(g, mx + 10, inpY, inpW, 22, muteInputFocused ? AppleUiRenderer.bgCardHover() : AppleUiRenderer.bgCard());
        String inpText = muteInput.isEmpty()
                ? (muteInputFocused ? "|" : ConfigManager.tr("输入要屏蔽的玩家 ID...", "Player ID to mute..."))
                : (muteInput + (muteInputFocused ? "|" : ""));
        AppleUiRenderer.drawText(g, this.font, trimToWidth(inpText, inpW - 12), mx + 16, inpY + 7,
                muteInput.isEmpty() && !muteInputFocused ? AppleUiRenderer.textTertiary() : AppleUiRenderer.textPrimary());
        hitAreas.add(new HitArea(mx + 10, inpY, inpW, 22, () -> {
            muteInputFocused = true;
            searchFocused = false;
        }, null));

        int addBtnX = mx + 10 + inpW + 6;
        AppleUiRenderer.drawPanel(g, addBtnX, inpY, addMuteW, 22, AppleUiRenderer.APPLE_RED);
        AppleUiRenderer.drawCenteredText(g, this.font, ConfigManager.tr("屏蔽", "Mute"), addBtnX + addMuteW / 2, inpY + 7, AppleUiRenderer.TEXT_PRIMARY);
        hitAreas.add(new HitArea(addBtnX, inpY, addMuteW, 22, () -> {
            if (!muteInput.isBlank()) {
                PlayerStatsManager.addMute(muteInput.trim());
                muteInput = "";
            }
        }, null));

        int listY = inpY + 28;
        int listH = mh - (listY - my) - 10;
        g.enableScissor(mx + 6, listY, mx + mw - 6, listY + listH);

        int ry = (int) Math.round(listY - statsScroll);
        if (statsTab == StatsTab.MUTED) {
            List<String> muted = new ArrayList<>(PlayerStatsManager.getMutedPlayers());
            for (String id : muted) {
                if (ry + 24 >= listY && ry <= listY + listH) {
                    AppleUiRenderer.drawPanel(g, mx + 10, ry, mw - 20, 22, AppleUiRenderer.bgCard());
                    AppleUiRenderer.drawText(g, this.font, id, mx + 18, ry + 7, AppleUiRenderer.textPrimary());
                    int btnX = mx + mw - 72;
                    g.fill(btnX, ry + 3, btnX + 54, ry + 19, AppleUiRenderer.APPLE_GREEN);
                    AppleUiRenderer.drawCenteredText(g, this.font, ConfigManager.tr("解除", "Unmute"), btnX + 27, ry + 7, AppleUiRenderer.TEXT_PRIMARY);
                    if (ry >= listY && ry + 22 <= listY + listH) {
                        hitAreas.add(new HitArea(btnX, ry + 3, 54, 16, () -> PlayerStatsManager.toggleMute(id), null));
                    }
                }
                ry += 25;
            }
        } else {
            List<PlayerStatsManager.PlayerRecord> records = new ArrayList<>(PlayerStatsManager.getAllRecords());
            Comparator<PlayerStatsManager.PlayerRecord> cmp = switch (statsTab) {
                case MESSAGES -> Comparator.comparingInt((PlayerStatsManager.PlayerRecord r) -> r.messages).reversed();
                case KILLS -> Comparator.comparingInt((PlayerStatsManager.PlayerRecord r) -> r.kills).reversed();
                case DEATHS -> Comparator.comparingInt((PlayerStatsManager.PlayerRecord r) -> r.deaths).reversed();
                default -> Comparator.comparing(r -> r.name);
            };
            records.sort(cmp.thenComparing(r -> r.name));

            for (PlayerStatsManager.PlayerRecord rec : records) {
                if (ry + 24 >= listY && ry <= listY + listH) {
                    AppleUiRenderer.drawPanel(g, mx + 10, ry, mw - 20, 22, AppleUiRenderer.bgCard());
                    AppleUiRenderer.drawText(g, this.font, rec.name, mx + 18, ry + 7, AppleUiRenderer.textPrimary());

                    String statsText = rec.messages + " / " + rec.kills + " / " + rec.deaths;
                    AppleUiRenderer.drawText(g, this.font, statsText, mx + mw - 86 - this.font.width(statsText), ry + 7, AppleUiRenderer.textSecondary());

                    boolean muted = PlayerStatsManager.isMuted(rec.name);
                    int btnX = mx + mw - 72;
                    g.fill(btnX, ry + 3, btnX + 54, ry + 19, muted ? AppleUiRenderer.APPLE_RED : AppleUiRenderer.bgCardHover());
                    AppleUiRenderer.drawCenteredText(g, this.font, muted ? ConfigManager.tr("已屏蔽", "Muted") : ConfigManager.tr("屏蔽", "Mute"), btnX + 27, ry + 7, AppleUiRenderer.textPrimary());
                    if (ry >= listY && ry + 22 <= listY + listH) {
                        hitAreas.add(new HitArea(btnX, ry + 3, 54, 16, () -> PlayerStatsManager.toggleMute(rec.name), null));
                    }
                }
                ry += 25;
            }
        }
        g.disableScissor();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x();
        double my = event.y();

        if (overlayMode == OverlayMode.NONE && event.button() == 0) {
            Category[] cats = Category.values();
            for (int i = cats.length - 1; i >= 0; i--) {
                Category cat = cats[i];
                PanelState state = PANEL_STATES.get(cat);
                if (state != null && inBounds(mx, my, state.x, state.y, PANEL_W, HEADER_H)) {
                    draggingCategory = cat;
                    dragOffsetX = (int) mx - state.x;
                    dragOffsetY = (int) my - state.y;
                    searchFocused = false;
                    return true;
                }
            }
        }

        if (overlayMode == OverlayMode.HUD_EDITOR && event.button() == 0) {
            for (ConfigManager.HudSlot slot : ConfigManager.HUD_SLOTS.values()) {
                String title = ConfigManager.english ? slot.enName : slot.zhName;
                String stateStr = slot.enabled ? ConfigManager.tr("开", "ON") : ConfigManager.tr("关", "OFF");
                int bw = this.font.width(title + " [" + stateStr + "]") + 14;
                if (inBounds(mx, my, slot.x, slot.y, bw, 18)) {
                    draggingHud = slot;
                    hudDragOffX = (int) mx - slot.x;
                    hudDragOffY = (int) my - slot.y;
                    return true;
                }
            }
        }

        for (int i = hitAreas.size() - 1; i >= 0; i--) {
            HitArea area = hitAreas.get(i);
            if (inBounds(mx, my, area.x, area.y, area.w, area.h)) {
                if (event.button() == 0 && area.leftClick != null) {
                    area.leftClick.run();
                    return true;
                }
                if (event.button() == 1 && area.rightClick != null) {
                    area.rightClick.run();
                    return true;
                }
            }
        }

        searchFocused = false;
        muteInputFocused = false;
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (draggingCategory != null && event.button() == 0) {
            PanelState state = PANEL_STATES.get(draggingCategory);
            if (state != null) {
                state.x = Math.max(4, Math.min(this.width - PANEL_W - 4, (int) event.x() - dragOffsetX));
                state.y = Math.max(42, Math.min(this.height - HEADER_H - 4, (int) event.y() - dragOffsetY));
            }
            return true;
        }
        if (draggingHud != null && event.button() == 0) {
            draggingHud.x = Math.max(4, Math.min(this.width - 80, (int) event.x() - hudDragOffX));
            draggingHud.y = Math.max(60, Math.min(this.height - 22, (int) event.y() - hudDragOffY));
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (draggingCategory != null) {
            draggingCategory = null;
            return true;
        }
        if (draggingHud != null) {
            draggingHud = null;
            ConfigManager.saveAsync();
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (overlayMode == OverlayMode.STATS) {
            statsScroll = Math.max(0, statsScroll - scrollY * 22);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (listeningMenuKey) {
            if (key != GLFW.GLFW_KEY_ESCAPE) {
                ConfigManager.setMenuKey(key);
                ConfigManager.saveAsync();
            }
            listeningMenuKey = false;
            return true;
        }
        if (searchFocused) {
            if (key == GLFW.GLFW_KEY_BACKSPACE && !searchQuery.isEmpty()) {
                searchQuery = searchQuery.substring(0, searchQuery.length() - 1);
                return true;
            }
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                searchFocused = false;
                return true;
            }
        }
        if (muteInputFocused) {
            if (key == GLFW.GLFW_KEY_BACKSPACE && !muteInput.isEmpty()) {
                muteInput = muteInput.substring(0, muteInput.length() - 1);
                return true;
            }
            if ((key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) && !muteInput.isBlank()) {
                PlayerStatsManager.addMute(muteInput.trim());
                muteInput = "";
                return true;
            }
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                muteInputFocused = false;
                return true;
            }
        }
        if (key == ConfigManager.getMenuKey() && !searchFocused && !muteInputFocused) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (event.isAllowedChatCharacter()) {
            String ch = event.codepointAsString();
            if (searchFocused) {
                searchQuery += ch;
                return true;
            }
            if (muteInputFocused) {
                if (ch.matches("[A-Za-z0-9_]") && muteInput.length() < 16) {
                    muteInput += ch;
                }
                return true;
            }
        }
        return super.charTyped(event);
    }

    private static List<Module> filterModules(List<Module> list, String query) {
        if (query.isEmpty()) return list;
        List<Module> result = new ArrayList<>();
        for (Module m : list) {
            if (m.getName().toLowerCase(Locale.ROOT).contains(query)
                    || m.getSubtitle().toLowerCase(Locale.ROOT).contains(query)
                    || m.getId().toLowerCase(Locale.ROOT).contains(query)) {
                result.add(m);
            }
        }
        return result;
    }

    private static boolean inBounds(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    private String trimToWidth(String text, int maxW) {
        if (text == null) return "";
        if (this.font.width(text) <= maxW) return text;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            String next = sb.toString() + text.charAt(i) + "…";
            if (this.font.width(next) > maxW) break;
            sb.append(text.charAt(i));
        }
        return sb + "…";
    }
}
