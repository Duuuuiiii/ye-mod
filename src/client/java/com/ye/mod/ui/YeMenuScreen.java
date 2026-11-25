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
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Meteor-style multi-panel draggable ClickGUI with Apple rounded-rect aesthetics.
 * Left-click toggles modules, right-click (or gear button) opens ModuleSettingsScreen.
 */
public class YeMenuScreen extends Screen {
    private enum OverlayMode {
        NONE,
        STATS,
        KEYBINDS
    }

    private enum StatsTab {
        MESSAGES("按发言"),
        KILLS("按击杀"),
        DEATHS("按死亡"),
        MUTED("黑名单");

        final String label;
        StatsTab(String label) { this.label = label; }
    }

    private static final class PanelState {
        int x;
        int y;
        boolean collapsed;

        PanelState(int x, int y, boolean collapsed) {
            this.x = x;
            this.y = y;
            this.collapsed = collapsed;
        }
    }

    private static final Map<Category, PanelState> PANEL_STATES = new EnumMap<>(Category.class);
    private static final int PANEL_W = 148;
    private static final int HEADER_H = 28;
    private static final int ROW_H = 24;

    private String searchQuery = "";
    private boolean searchFocused = false;

    private Category draggingCategory = null;
    private int dragOffsetX = 0;
    private int dragOffsetY = 0;

    private OverlayMode overlayMode = OverlayMode.NONE;
    private StatsTab statsTab = StatsTab.MESSAGES;
    private double statsScroll = 0;
    private String muteInput = "";
    private boolean muteInputFocused = false;
    private boolean listeningMenuKey = false;

    private final List<HitArea> hitAreas = new ArrayList<>();

    private record HitArea(int x, int y, int w, int h, Runnable leftClick, Runnable rightClick) {}

    public YeMenuScreen() {
        super(Component.literal("Ye Mod ClickGUI"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        super.init();
        if (PANEL_STATES.isEmpty()) {
            resetPanelLayout();
        }
    }

    @Override
    public void onClose() {
        ConfigManager.saveAsync();
        super.onClose();
    }

    private void resetPanelLayout() {
        PANEL_STATES.clear();
        Category[] cats = Category.values();
        int gap = 10;
        int maxCols = Math.max(1, (this.width - 28 + gap) / (PANEL_W + gap));
        int cols = Math.min(cats.length, maxCols);
        int totalRowW = cols * PANEL_W + (cols - 1) * gap;
        int startX = Math.max(14, (this.width - totalRowW) / 2);
        int startY = 58;

        for (int i = 0; i < cats.length; i++) {
            int col = i % cols;
            int row = i / cols;
            int px = startX + col * (PANEL_W + gap);
            int py = startY + row * 195;
            PANEL_STATES.put(cats[i], new PanelState(px, py, false));
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        hitAreas.clear();
        g.fill(0, 0, this.width, this.height, AppleUiRenderer.BG_BACKDROP);

        renderTopBar(g, mouseX, mouseY);

        if (overlayMode == OverlayMode.NONE) {
            String hoveredDesc = renderCategoryPanels(g, mouseX, mouseY);
            renderBottomHint(g, hoveredDesc);
        } else if (overlayMode == OverlayMode.STATS) {
            renderStatsOverlay(g, mouseX, mouseY);
        } else if (overlayMode == OverlayMode.KEYBINDS) {
            renderKeybindsOverlay(g, mouseX, mouseY);
        }
    }

    private void renderTopBar(GuiGraphics g, int mouseX, int mouseY) {
        int barW = Math.min(620, this.width - 24);
        int barH = 34;
        int bx = (this.width - barW) / 2;
        int by = 12;
        AppleUiRenderer.drawRoundedRect(g, bx, by, barW, barH, 11, AppleUiRenderer.BG_WINDOW);

        // Brand pill
        int brandW = 68;
        AppleUiRenderer.drawRoundedRect(g, bx + 6, by + 5, brandW, 24, 7, AppleUiRenderer.APPLE_BLUE);
        g.drawCenteredString(this.font, "Ye 1.0.6", bx + 6 + brandW / 2, by + 13, AppleUiRenderer.TEXT_PRIMARY);

        // Action buttons on right
        int btnH = 24;
        int btnY = by + 5;

        int resetW = 64;
        int resetX = bx + barW - 6 - resetW;
        boolean resetHover = inBounds(mouseX, mouseY, resetX, btnY, resetW, btnH);
        AppleUiRenderer.drawRoundedRect(g, resetX, btnY, resetW, btnH, 7, resetHover ? AppleUiRenderer.BG_CARD_HOVER : AppleUiRenderer.BG_CARD);
        g.drawCenteredString(this.font, "重置布局", resetX + resetW / 2, btnY + 8, AppleUiRenderer.TEXT_PRIMARY);
        hitAreas.add(new HitArea(resetX, btnY, resetW, btnH, () -> {
            overlayMode = OverlayMode.NONE;
            resetPanelLayout();
        }, null));

        int keyW = 66;
        int keyX = resetX - 6 - keyW;
        boolean keyActive = overlayMode == OverlayMode.KEYBINDS;
        boolean keyHover = inBounds(mouseX, mouseY, keyX, btnY, keyW, btnH);
        AppleUiRenderer.drawRoundedRect(g, keyX, btnY, keyW, btnH, 7,
                keyActive ? AppleUiRenderer.APPLE_BLUE : (keyHover ? AppleUiRenderer.BG_CARD_HOVER : AppleUiRenderer.BG_CARD));
        g.drawCenteredString(this.font, "按键设置", keyX + keyW / 2, btnY + 8, AppleUiRenderer.TEXT_PRIMARY);
        hitAreas.add(new HitArea(keyX, btnY, keyW, btnH, () -> {
            overlayMode = (overlayMode == OverlayMode.KEYBINDS) ? OverlayMode.NONE : OverlayMode.KEYBINDS;
            searchFocused = false;
            muteInputFocused = false;
        }, null));

        int statsW = 66;
        int statsX = keyX - 6 - statsW;
        boolean statsActive = overlayMode == OverlayMode.STATS;
        boolean statsHover = inBounds(mouseX, mouseY, statsX, btnY, statsW, btnH);
        AppleUiRenderer.drawRoundedRect(g, statsX, btnY, statsW, btnH, 7,
                statsActive ? AppleUiRenderer.APPLE_BLUE : (statsHover ? AppleUiRenderer.BG_CARD_HOVER : AppleUiRenderer.BG_CARD));
        g.drawCenteredString(this.font, "玩家统计", statsX + statsW / 2, btnY + 8, AppleUiRenderer.TEXT_PRIMARY);
        hitAreas.add(new HitArea(statsX, btnY, statsW, btnH, () -> {
            overlayMode = (overlayMode == OverlayMode.STATS) ? OverlayMode.NONE : OverlayMode.STATS;
            searchFocused = false;
            muteInputFocused = false;
        }, null));

        // Search box in remaining center space
        int searchX = bx + 6 + brandW + 8;
        int searchW = Math.max(80, statsX - 8 - searchX);
        int searchBg = searchFocused ? 0xFF283548 : AppleUiRenderer.BG_CARD;
        AppleUiRenderer.drawRoundedRect(g, searchX, btnY, searchW, btnH, 7, searchBg);
        String displaySearch = searchQuery.isEmpty()
                ? (searchFocused ? "|" : "搜索模块...")
                : (searchQuery + (searchFocused ? "|" : ""));
        int searchColor = searchQuery.isEmpty() && !searchFocused ? AppleUiRenderer.TEXT_TERTIARY : AppleUiRenderer.TEXT_PRIMARY;
        g.drawString(this.font, trimToWidth(displaySearch, searchW - 16), searchX + 8, btnY + 8, searchColor, false);
        hitAreas.add(new HitArea(searchX, btnY, searchW, btnH, () -> {
            overlayMode = OverlayMode.NONE;
            searchFocused = true;
            muteInputFocused = false;
        }, () -> {
            searchQuery = "";
            searchFocused = false;
        }));
    }

    private String renderCategoryPanels(GuiGraphics g, int mouseX, int mouseY) {
        String hoveredDesc = null;
        String q = searchQuery.trim().toLowerCase(Locale.ROOT);

        for (Category cat : Category.values()) {
            PanelState state = PANEL_STATES.computeIfAbsent(cat, c -> new PanelState(20, 58, false));
            List<Module> modules = filterModules(ModuleManager.getByCategory(cat), q);

            int px = Math.max(4, Math.min(this.width - PANEL_W - 4, state.x));
            int py = Math.max(50, Math.min(this.height - HEADER_H - 4, state.y));
            state.x = px;
            state.y = py;

            boolean expanded = !state.collapsed && !modules.isEmpty();
            int bodyH = expanded ? (modules.size() * ROW_H + 6) : 0;
            int totalH = HEADER_H + bodyH;

            // Panel background
            AppleUiRenderer.drawRoundedRect(g, px, py, PANEL_W, totalH, 10, AppleUiRenderer.BG_WINDOW);

            // Header bar
            AppleUiRenderer.drawRoundedRect(g, px, py, PANEL_W, HEADER_H, 10, AppleUiRenderer.BG_SIDEBAR);
            // Category accent dot
            AppleUiRenderer.drawCircle(g, px + 9, py + 10, 8, cat.getAccentColor());
            g.drawString(this.font, cat.getDisplayName(), px + 22, py + 10, AppleUiRenderer.TEXT_PRIMARY, false);

            String countStr = modules.size() + " " + (state.collapsed ? "▸" : "▾");
            g.drawString(this.font, countStr, px + PANEL_W - 9 - this.font.width(countStr), py + 10, AppleUiRenderer.TEXT_TERTIARY, false);

            // Header hit area: left click drags (handled in mouseClicked), right click toggles collapse
            hitAreas.add(new HitArea(px, py, PANEL_W, HEADER_H, null, () -> state.collapsed = !state.collapsed));

            if (expanded) {
                int ry = py + HEADER_H + 3;
                for (Module m : modules) {
                    int rowX = px + 4;
                    int rowW = PANEL_W - 8;
                    boolean hover = inBounds(mouseX, mouseY, rowX, ry, rowW, ROW_H - 2);
                    if (hover) {
                        hoveredDesc = m.getName() + " — " + m.getDescription() + "  (左键开关 · 右键设置)";
                    }

                    int rowBg;
                    if (m.isEnabled()) {
                        rowBg = hover ? 0xCC1E40AF : 0xAA1D4ED8;
                    } else {
                        rowBg = hover ? AppleUiRenderer.BG_CARD_HOVER : AppleUiRenderer.BG_CARD;
                    }
                    AppleUiRenderer.drawRoundedRect(g, rowX, ry, rowW, ROW_H - 2, 6, rowBg);

                    // Status dot
                    int dotColor = m.isEnabled() ? AppleUiRenderer.APPLE_GREEN : 0xFF475569;
                    AppleUiRenderer.drawCircle(g, rowX + 7, ry + 8, 6, dotColor);

                    // Module name
                    String name = trimToWidth(m.getName(), rowW - 34);
                    g.drawString(this.font, name, rowX + 17, ry + 7, AppleUiRenderer.TEXT_PRIMARY, false);

                    // Settings gear indicator if module has settings
                    if (!m.getSettings().isEmpty()) {
                        g.drawString(this.font, "⚙", rowX + rowW - 13, ry + 7, AppleUiRenderer.TEXT_TERTIARY, false);
                    }

                    hitAreas.add(new HitArea(rowX, ry, rowW, ROW_H - 2,
                            () -> {
                                m.toggle();
                                ConfigManager.saveAsync();
                            },
                            () -> Minecraft.getInstance().setScreen(new ModuleSettingsScreen(this, m))));

                    ry += ROW_H;
                }
            }
        }
        return hoveredDesc;
    }

    private void renderBottomHint(GuiGraphics g, String hoveredDesc) {
        String text = hoveredDesc != null
                ? hoveredDesc
                : "左键开关模块 · 右键模块打开设置 · 拖拽分类标题移动面板 · 右键标题折叠";
        int tw = this.font.width(text);
        int pw = Math.min(this.width - 24, tw + 20);
        int px = (this.width - pw) / 2;
        int py = this.height - 26;
        AppleUiRenderer.drawRoundedRect(g, px, py, pw, 18, 9, 0xBB0F172A);
        g.drawCenteredString(this.font, trimToWidth(text, pw - 14), this.width / 2, py + 5, AppleUiRenderer.TEXT_SECONDARY);
    }

    private void renderStatsOverlay(GuiGraphics g, int mouseX, int mouseY) {
        int mw = Math.min(600, this.width - 36);
        int mh = Math.min(390, this.height - 74);
        int mx = (this.width - mw) / 2;
        int my = 56;
        AppleUiRenderer.drawRoundedRect(g, mx, my, mw, mh, 14, AppleUiRenderer.BG_WINDOW);

        // Tabs row
        int tx = mx + 12;
        int ty = my + 12;
        for (StatsTab tab : StatsTab.values()) {
            int tw = 64;
            boolean active = statsTab == tab;
            boolean hover = inBounds(mouseX, mouseY, tx, ty, tw, 22);
            AppleUiRenderer.drawRoundedRect(g, tx, ty, tw, 22, 7,
                    active ? AppleUiRenderer.APPLE_BLUE : (hover ? AppleUiRenderer.BG_CARD_HOVER : AppleUiRenderer.BG_CARD));
            g.drawCenteredString(this.font, tab.label, tx + tw / 2, ty + 7, AppleUiRenderer.TEXT_PRIMARY);
            hitAreas.add(new HitArea(tx, ty, tw, 22, () -> {
                statsTab = tab;
                statsScroll = 0;
            }, null));
            tx += tw + 6;
        }

        // Open logs + Clear stats on right
        int clearW = 62;
        int clearX = mx + mw - 12 - clearW;
        AppleUiRenderer.drawRoundedRect(g, clearX, ty, clearW, 22, 7, 0xFF7F1D1D);
        g.drawCenteredString(this.font, "清空统计", clearX + clearW / 2, ty + 7, AppleUiRenderer.TEXT_PRIMARY);
        hitAreas.add(new HitArea(clearX, ty, clearW, 22, PlayerStatsManager::clearCurrentStats, null));

        int logsW = 70;
        int logsX = clearX - 6 - logsW;
        AppleUiRenderer.drawRoundedRect(g, logsX, ty, logsW, 22, 7, AppleUiRenderer.BG_CARD);
        g.drawCenteredString(this.font, "日志文件夹", logsX + logsW / 2, ty + 7, AppleUiRenderer.TEXT_PRIMARY);
        hitAreas.add(new HitArea(logsX, ty, logsW, 22, PlayerStatsManager::openLogsFolder, null));

        // Manual mute input bar
        int inpY = ty + 30;
        int addMuteW = 76;
        int inpW = mw - 24 - addMuteW - 8;
        AppleUiRenderer.drawRoundedRect(g, mx + 12, inpY, inpW, 24, 7, muteInputFocused ? 0xFF283548 : AppleUiRenderer.BG_CARD);
        String inpText = muteInput.isEmpty()
                ? (muteInputFocused ? "|" : "输入要屏蔽的玩家 ID...")
                : (muteInput + (muteInputFocused ? "|" : ""));
        g.drawString(this.font, trimToWidth(inpText, inpW - 14), mx + 20, inpY + 8,
                muteInput.isEmpty() && !muteInputFocused ? AppleUiRenderer.TEXT_TERTIARY : AppleUiRenderer.TEXT_PRIMARY, false);
        hitAreas.add(new HitArea(mx + 12, inpY, inpW, 24, () -> {
            muteInputFocused = true;
            searchFocused = false;
        }, null));

        int addBtnX = mx + 12 + inpW + 8;
        AppleUiRenderer.drawRoundedRect(g, addBtnX, inpY, addMuteW, 24, 7, AppleUiRenderer.APPLE_RED);
        g.drawCenteredString(this.font, "屏蔽玩家", addBtnX + addMuteW / 2, inpY + 8, AppleUiRenderer.TEXT_PRIMARY);
        hitAreas.add(new HitArea(addBtnX, inpY, addMuteW, 24, () -> {
            if (!muteInput.isBlank()) {
                PlayerStatsManager.addMute(muteInput.trim());
                muteInput = "";
            }
        }, null));

        // List area
        int listY = inpY + 32;
        int listH = mh - (listY - my) - 12;
        g.enableScissor(mx + 8, listY, mx + mw - 8, listY + listH);

        int ry = (int) Math.round(listY - statsScroll);
        if (statsTab == StatsTab.MUTED) {
            List<String> muted = new ArrayList<>(PlayerStatsManager.getMutedPlayers());
            if (muted.isEmpty()) {
                g.drawCenteredString(this.font, "黑名单为空", mx + mw / 2, ry + 20, AppleUiRenderer.TEXT_TERTIARY);
            }
            for (String id : muted) {
                if (ry + 28 >= listY && ry <= listY + listH) {
                    AppleUiRenderer.drawRoundedRect(g, mx + 12, ry, mw - 24, 26, 7, AppleUiRenderer.BG_CARD);
                    g.drawString(this.font, id, mx + 22, ry + 9, AppleUiRenderer.TEXT_PRIMARY, false);
                    int btnX = mx + mw - 82;
                    AppleUiRenderer.drawRoundedRect(g, btnX, ry + 3, 62, 20, 6, AppleUiRenderer.APPLE_GREEN);
                    g.drawCenteredString(this.font, "解除屏蔽", btnX + 31, ry + 9, AppleUiRenderer.TEXT_PRIMARY);
                    if (ry >= listY && ry + 26 <= listY + listH) {
                        hitAreas.add(new HitArea(btnX, ry + 3, 62, 20, () -> PlayerStatsManager.toggleMute(id), null));
                    }
                }
                ry += 30;
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

            if (records.isEmpty()) {
                g.drawCenteredString(this.font, "暂无玩家统计记录（进服后自动记录真实玩家 ID）", mx + mw / 2, ry + 24, AppleUiRenderer.TEXT_TERTIARY);
            }
            for (PlayerStatsManager.PlayerRecord rec : records) {
                if (ry + 28 >= listY && ry <= listY + listH) {
                    AppleUiRenderer.drawRoundedRect(g, mx + 12, ry, mw - 24, 26, 7, AppleUiRenderer.BG_CARD);
                    g.drawString(this.font, rec.name, mx + 22, ry + 9, AppleUiRenderer.TEXT_PRIMARY, false);

                    String statsText = "发言 " + rec.messages + "  ·  击杀 " + rec.kills + "  ·  死亡 " + rec.deaths;
                    g.drawString(this.font, statsText, mx + mw - 102 - this.font.width(statsText), ry + 9, AppleUiRenderer.TEXT_SECONDARY, false);

                    boolean muted = PlayerStatsManager.isMuted(rec.name);
                    int btnX = mx + mw - 86;
                    AppleUiRenderer.drawRoundedRect(g, btnX, ry + 3, 66, 20, 6, muted ? AppleUiRenderer.APPLE_RED : 0xFF334155);
                    g.drawCenteredString(this.font, muted ? "已屏蔽" : "屏蔽", btnX + 33, ry + 9, AppleUiRenderer.TEXT_PRIMARY);
                    if (ry >= listY && ry + 26 <= listY + listH) {
                        hitAreas.add(new HitArea(btnX, ry + 3, 66, 20, () -> PlayerStatsManager.toggleMute(rec.name), null));
                    }
                }
                ry += 30;
            }
        }
        g.disableScissor();
    }

    private void renderKeybindsOverlay(GuiGraphics g, int mouseX, int mouseY) {
        int mw = Math.min(480, this.width - 36);
        int mh = Math.min(360, this.height - 74);
        int mx = (this.width - mw) / 2;
        int my = 56;
        AppleUiRenderer.drawRoundedRect(g, mx, my, mw, mh, 14, AppleUiRenderer.BG_WINDOW);

        g.drawString(this.font, "快捷键设置", mx + 16, my + 14, AppleUiRenderer.TEXT_PRIMARY, false);

        // Menu keybind row
        int rowY = my + 38;
        AppleUiRenderer.drawRoundedRect(g, mx + 14, rowY, mw - 28, 34, 8, AppleUiRenderer.BG_CARD);
        g.drawString(this.font, "打开/关闭 Ye 菜单", mx + 24, rowY + 13, AppleUiRenderer.TEXT_PRIMARY, false);
        String menuKeyLabel = listeningMenuKey ? "请按下按键..." : ModuleSettingsScreen.getKeyName(ConfigManager.getMenuKey());
        int keyBtnW = 92;
        int keyBtnX = mx + mw - 22 - keyBtnW;
        AppleUiRenderer.drawRoundedRect(g, keyBtnX, rowY + 6, keyBtnW, 22, 6,
                listeningMenuKey ? AppleUiRenderer.APPLE_BLUE : 0xFF334155);
        g.drawCenteredString(this.font, menuKeyLabel, keyBtnX + keyBtnW / 2, rowY + 13, AppleUiRenderer.TEXT_PRIMARY);
        hitAreas.add(new HitArea(keyBtnX, rowY + 6, keyBtnW, 22, () -> listeningMenuKey = !listeningMenuKey, null));

        // Bound module keybinds list
        g.drawString(this.font, "已绑定快捷键的模块 (右键模块进入设置修改)", mx + 16, rowY + 46, AppleUiRenderer.TEXT_TERTIARY, false);
        int ly = rowY + 64;
        int count = 0;
        for (Module m : ModuleManager.getAll()) {
            if (m.getKeybind() > 0 && ly + 26 < my + mh - 10) {
                AppleUiRenderer.drawRoundedRect(g, mx + 14, ly, mw - 28, 24, 6, AppleUiRenderer.BG_CARD);
                g.drawString(this.font, m.getName(), mx + 24, ly + 8, AppleUiRenderer.TEXT_PRIMARY, false);
                String k = ModuleSettingsScreen.getKeyName(m.getKeybind());
                g.drawString(this.font, k, mx + mw - 24 - this.font.width(k), ly + 8, AppleUiRenderer.APPLE_BLUE, false);
                hitAreas.add(new HitArea(mx + 14, ly, mw - 28, 24,
                        () -> Minecraft.getInstance().setScreen(new ModuleSettingsScreen(this, m)), null));
                ly += 28;
                count++;
            }
        }
        if (count == 0) {
            g.drawCenteredString(this.font, "暂无模块快捷键 — 右键任意模块可单独绑定", mx + mw / 2, ly + 20, AppleUiRenderer.TEXT_TERTIARY);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x();
        double my = event.y();

        // Check category header drag first when no overlay is open
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
                state.y = Math.max(50, Math.min(this.height - HEADER_H - 4, (int) event.y() - dragOffsetY));
            }
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
