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
import com.ye.mod.core.setting.StringSetting;
import com.ye.mod.modules.render.CapeManager;
import com.ye.mod.modules.render.CustomCapeModule;
import com.ye.mod.modules.render.CustomSkinModule;
import com.ye.mod.modules.render.SkinManager;
import com.ye.mod.modules.system.PlayerStatsManager;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Apple macOS/iOS inspired high-DPI rounded ClickGUI with smooth animations
 * and integrated keybind management (default F9).
 */
public class YeMenuScreen extends Screen {
    private Category selectedCategory = null; // null = All
    private boolean keybindsTab = false;
    private boolean statsTab = false;
    private int statsSortMode = 0; // 0=messages, 1=kills, 2=deaths, 3=muted only
    private String muteInput = "";
    private boolean muteInputFocused = false;
    private float targetStatsScroll = 0;
    private float statsScroll = 0;
    private Module selectedModule;
    private String searchQuery = "";
    private boolean searchFocused = false;

    private boolean bindingMenuKey = false;
    private Module bindingModule = null;

    private float targetModuleScroll = 0;
    private float moduleScroll = 0;
    private float targetInspectorScroll = 0;
    private float inspectorScroll = 0;
    private float openAnim = 0.0F;
    private long lastNanos = System.nanoTime();

    private final Map<String, Float> toggleAnims = new HashMap<>();
    private DoubleSetting draggingSlider = null;

    private int winX, winY, winW, winH;
    private static final int SIDEBAR_W = 140;
    private static final int TOPBAR_H = 40;
    private static final int MODULE_LIST_W = 212;

    public YeMenuScreen() {
        super(Component.literal("Ye Control Center"));
        List<Module> all = ModuleManager.getAll();
        if (!all.isEmpty()) {
            selectedModule = all.get(0);
        }
    }

    @Override
    protected void init() {
        winW = Math.min(620, this.width - 28);
        winH = Math.min(390, this.height - 28);
        winX = (this.width - winW) / 2;
        winY = (this.height - winH) / 2;
        lastNanos = System.nanoTime();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private float animValue(String key, boolean state, float dt) {
        float cur = toggleAnims.getOrDefault(key, state ? 1.0F : 0.0F);
        float next = AppleUiRenderer.smooth(cur, state ? 1.0F : 0.0F, 16.0F, dt);
        toggleAnims.put(key, next);
        return next;
    }

    private List<Module> getFilteredModules() {
        String q = searchQuery.trim().toLowerCase(Locale.ROOT);
        List<Module> result = new ArrayList<>();
        for (Module m : ModuleManager.getAll()) {
            if (selectedCategory != null && m.getCategory() != selectedCategory) continue;
            if (!q.isEmpty()) {
                boolean match = m.getName().toLowerCase(Locale.ROOT).contains(q)
                        || m.getSubtitle().toLowerCase(Locale.ROOT).contains(q)
                        || m.getDescription().toLowerCase(Locale.ROOT).contains(q);
                if (!match) continue;
            }
            result.add(m);
        }
        return result;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        long now = System.nanoTime();
        float dt = Math.max(0.001F, (now - lastNanos) / 1_000_000_000.0F);
        lastNanos = now;

        openAnim = AppleUiRenderer.smooth(openAnim, 1.0F, 14.0F, dt);
        moduleScroll = AppleUiRenderer.smooth(moduleScroll, targetModuleScroll, 18.0F, dt);
        inspectorScroll = AppleUiRenderer.smooth(inspectorScroll, targetInspectorScroll, 18.0F, dt);
        statsScroll = AppleUiRenderer.smooth(statsScroll, targetStatsScroll, 18.0F, dt);

        g.fill(0, 0, this.width, this.height, AppleUiRenderer.withAlpha(AppleUiRenderer.BG_BACKDROP, openAnim));

        // Window rounded shadow + container
        AppleUiRenderer.drawRoundedRect(g, winX - 2, winY - 2, winW + 4, winH + 4, 15, AppleUiRenderer.withAlpha(0x44000000, openAnim));
        AppleUiRenderer.drawRoundedRect(g, winX, winY, winW, winH, 14, AppleUiRenderer.withAlpha(AppleUiRenderer.BG_WINDOW, openAnim));

        renderSidebar(g, mouseX, mouseY);
        renderTopBar(g, mouseX, mouseY);

        int contentY = winY + TOPBAR_H;
        int contentH = winH - TOPBAR_H;

        if (keybindsTab) {
            renderKeybindsPage(g, winX + SIDEBAR_W, contentY, winW - SIDEBAR_W, contentH, mouseX, mouseY);
        } else if (statsTab) {
            renderStatsPage(g, winX + SIDEBAR_W, contentY, winW - SIDEBAR_W, contentH, mouseX, mouseY);
        } else {
            int listX = winX + SIDEBAR_W;
            int inspX = listX + MODULE_LIST_W;
            int inspW = winW - SIDEBAR_W - MODULE_LIST_W;
            renderModuleList(g, listX, contentY, MODULE_LIST_W, contentH, mouseX, mouseY, dt);
            renderInspector(g, inspX, contentY, inspW, contentH, mouseX, mouseY, dt);
        }
    }

    private void renderSidebar(GuiGraphics g, int mouseX, int mouseY) {
        AppleUiRenderer.drawRoundedRect(g, winX + 6, winY + 6, SIDEBAR_W - 10, winH - 12, 10, AppleUiRenderer.BG_SIDEBAR);

        // macOS style traffic dots
        AppleUiRenderer.drawCircle(g, winX + 16, winY + 15, 7, 0xFFFF5F57);
        AppleUiRenderer.drawCircle(g, winX + 27, winY + 15, 7, 0xFFFEBC2E);
        AppleUiRenderer.drawCircle(g, winX + 38, winY + 15, 7, 0xFF28C840);

        g.drawString(font, "Ye", winX + 54, winY + 14, AppleUiRenderer.TEXT_PRIMARY, false);
        g.drawString(font, "1.21.11", winX + 54 + font.width("Ye "), winY + 14, AppleUiRenderer.TEXT_TERTIARY, false);

        int cy = winY + 42;
        cy = drawCategoryPill(g, !keybindsTab && !statsTab && selectedCategory == null, "全部功能", ModuleManager.getAll().size(), winX + 12, cy, SIDEBAR_W - 22, mouseX, mouseY);
        for (Category cat : Category.values()) {
            int count = ModuleManager.getByCategory(cat).size();
            cy = drawCategoryPill(g, !keybindsTab && !statsTab && selectedCategory == cat, cat.getDisplayName(), count, winX + 12, cy, SIDEBAR_W - 22, mouseX, mouseY);
        }

        cy += 6;
        cy = drawCategoryPill(g, statsTab, "玩家统计", PlayerStatsManager.getAllRecords().size(), winX + 12, cy, SIDEBAR_W - 22, mouseX, mouseY);
        drawCategoryPill(g, keybindsTab, "按键设置", ModuleManager.getAll().size() + 1, winX + 12, cy, SIDEBAR_W - 22, mouseX, mouseY);

        int active = ModuleManager.getActiveCount();
        g.drawString(font, "已启用 " + active + " 项", winX + 16, winY + winH - 30, AppleUiRenderer.TEXT_SECONDARY, false);
        g.drawString(font, getKeyName(ConfigManager.getMenuKey()) + " 总菜单", winX + 16, winY + winH - 18, AppleUiRenderer.TEXT_TERTIARY, false);
    }

    private int drawCategoryPill(GuiGraphics g, boolean selected, String label, int count, int x, int y, int w, int mouseX, int mouseY) {
        int h = 24;
        boolean hovered = isInside(mouseX, mouseY, x, y, w, h);
        if (selected) {
            AppleUiRenderer.drawRoundedRect(g, x, y, w, h, 7, AppleUiRenderer.APPLE_BLUE);
        } else if (hovered) {
            AppleUiRenderer.drawRoundedRect(g, x, y, w, h, 7, 0x1AFFFFFF);
        }

        g.drawString(font, label, x + 10, y + 8, selected ? 0xFFFFFFFF : AppleUiRenderer.TEXT_SECONDARY, false);
        String countStr = String.valueOf(count);
        g.drawString(font, countStr, x + w - font.width(countStr) - 8, y + 8, selected ? 0xFFE5F2FF : AppleUiRenderer.TEXT_TERTIARY, false);
        return y + h + 4;
    }

    private void renderTopBar(GuiGraphics g, int mouseX, int mouseY) {
        int tx = winX + SIDEBAR_W + 4;
        int tw = winW - SIDEBAR_W - 12;

        // Search pill
        int sx = tx + 6;
        int sy = winY + 9;
        int sw = tw - 36;
        int sh = 22;
        AppleUiRenderer.drawRoundedRect(g, sx, sy, sw, sh, 8, searchFocused ? 0xFF232836 : AppleUiRenderer.BG_CARD);

        String display = searchQuery.isEmpty() && !searchFocused ? "搜索功能、披风、皮肤或设置..." : searchQuery + (searchFocused ? "_" : "");
        int color = searchQuery.isEmpty() && !searchFocused ? AppleUiRenderer.TEXT_TERTIARY : AppleUiRenderer.TEXT_PRIMARY;
        g.drawString(font, display, sx + 10, sy + 7, color, false);

        // Close pill
        int cx = tx + tw - 22;
        int cy = winY + 9;
        boolean closeHover = isInside(mouseX, mouseY, cx, cy, 22, 22);
        AppleUiRenderer.drawRoundedRect(g, cx, cy, 22, 22, 8, closeHover ? 0x44FF453A : AppleUiRenderer.BG_CARD);
        g.drawString(font, "×", cx + 8, cy + 7, closeHover ? AppleUiRenderer.APPLE_RED : AppleUiRenderer.TEXT_SECONDARY, false);
    }

    private void renderKeybindsPage(GuiGraphics g, int x, int y, int w, int h, int mouseX, int mouseY) {
        int pad = 14;
        g.drawString(font, "快捷键管理", x + pad, y + 6, AppleUiRenderer.TEXT_PRIMARY, false);
        g.drawString(font, "左键点击按钮后按下新按键即可绑定，右键清除", x + pad, y + 19, AppleUiRenderer.TEXT_SECONDARY, false);

        int ry = y + 38;
        int rowW = w - pad * 2;
        ry = drawKeybindRow(g, "打开 Ye 总设置菜单", bindingMenuKey ? "请按下按键..." : getKeyName(ConfigManager.getMenuKey()),
                bindingMenuKey, x + pad, ry, rowW, mouseX, mouseY);

        for (Module m : ModuleManager.getAll()) {
            if (ry + 30 > y + h - 8) break;
            boolean isBinding = bindingModule == m;
            String keyStr = isBinding ? "请按下按键..." : getKeyName(m.getKeybind());
            ry = drawKeybindRow(g, m.getName() + " (" + m.getSubtitle() + ")", keyStr, isBinding, x + pad, ry, rowW, mouseX, mouseY);
        }
    }

    private int drawKeybindRow(GuiGraphics g, String label, String keyStr, boolean active, int x, int y, int w, int mouseX, int mouseY) {
        int h = 28;
        boolean hov = isInside(mouseX, mouseY, x, y, w, h);
        AppleUiRenderer.drawRoundedRect(g, x, y, w, h, 8, hov ? AppleUiRenderer.BG_CARD_HOVER : AppleUiRenderer.BG_CARD);
        g.drawString(font, label, x + 12, y + 10, AppleUiRenderer.TEXT_PRIMARY, false);

        int kw = Math.max(64, font.width(keyStr) + 18);
        int kx = x + w - kw - 8;
        AppleUiRenderer.drawRoundedRect(g, kx, y + 4, kw, 20, 6, active ? AppleUiRenderer.APPLE_BLUE : 0xFF282D3B);
        g.drawString(font, keyStr, kx + (kw - font.width(keyStr)) / 2, y + 10, active ? 0xFFFFFFFF : AppleUiRenderer.TEXT_PRIMARY, false);
        return y + h + 6;
    }

    private List<PlayerStatsManager.PlayerRecord> getFilteredStats() {
        String q = searchQuery.trim().toLowerCase(Locale.ROOT);
        List<PlayerStatsManager.PlayerRecord> list = new ArrayList<>();
        if (statsSortMode == 3) {
            for (String muted : PlayerStatsManager.getMutedPlayers()) {
                if (!q.isEmpty() && !muted.contains(q)) continue;
                PlayerStatsManager.PlayerRecord found = null;
                for (PlayerStatsManager.PlayerRecord r : PlayerStatsManager.getAllRecords()) {
                    if (r.name.equalsIgnoreCase(muted)) { found = r; break; }
                }
                if (found != null) list.add(found);
                else list.add(new PlayerStatsManager.PlayerRecord(muted));
            }
        } else {
            for (PlayerStatsManager.PlayerRecord r : PlayerStatsManager.getAllRecords()) {
                if (!q.isEmpty() && !r.name.toLowerCase(Locale.ROOT).contains(q)) continue;
                list.add(r);
            }
            list.sort((a, b) -> switch (statsSortMode) {
                case 1 -> Integer.compare(b.kills, a.kills);
                case 2 -> Integer.compare(b.deaths, a.deaths);
                default -> Integer.compare(b.messages, a.messages);
            });
        }
        return list;
    }

    private void renderStatsPage(GuiGraphics g, int x, int y, int w, int h, int mouseX, int mouseY) {
        int pad = 14;
        int innerW = w - pad * 2;
        String[] sorts = {"按发言", "按击杀", "按死亡", "黑名单"};
        int sx = x + pad;
        for (int i = 0; i < sorts.length; i++) {
            int bw = font.width(sorts[i]) + 14;
            boolean sel = statsSortMode == i;
            AppleUiRenderer.drawRoundedRect(g, sx, y + 6, bw, 20, 6, sel ? AppleUiRenderer.APPLE_BLUE : AppleUiRenderer.BG_CARD);
            g.drawString(font, sorts[i], sx + 7, y + 12, sel ? 0xFFFFFFFF : AppleUiRenderer.TEXT_SECONDARY, false);
            sx += bw + 6;
        }

        // Open logs button on right
        String logBtn = "打开日志";
        int lw = font.width(logBtn) + 14;
        int lx = x + w - pad - lw;
        boolean lhov = isInside(mouseX, mouseY, lx, y + 6, lw, 20);
        AppleUiRenderer.drawRoundedRect(g, lx, y + 6, lw, 20, 6, lhov ? AppleUiRenderer.APPLE_BLUE : 0x260A84FF);
        g.drawString(font, logBtn, lx + 7, y + 12, lhov ? 0xFFFFFFFF : AppleUiRenderer.APPLE_BLUE, false);

        // Manual mute input bar
        int iy = y + 32;
        int addW = 64;
        int boxW = innerW - addW - 6;
        AppleUiRenderer.drawRoundedRect(g, x + pad, iy, boxW, 22, 7, muteInputFocused ? 0xFF232836 : AppleUiRenderer.BG_CARD);
        String disp = muteInput.isEmpty() && !muteInputFocused ? "输入玩家 ID 添加到黑名单..." : muteInput + (muteInputFocused ? "_" : "");
        g.drawString(font, disp, x + pad + 8, iy + 7, muteInput.isEmpty() && !muteInputFocused ? AppleUiRenderer.TEXT_TERTIARY : AppleUiRenderer.TEXT_PRIMARY, false);

        int ax = x + pad + boxW + 6;
        boolean ahov = isInside(mouseX, mouseY, ax, iy, addW, 22);
        AppleUiRenderer.drawRoundedRect(g, ax, iy, addW, 22, 7, ahov ? AppleUiRenderer.APPLE_RED : 0x33FF453A);
        g.drawString(font, "+ 拉黑", ax + 16, iy + 7, ahov ? 0xFFFFFFFF : AppleUiRenderer.APPLE_RED, false);

        // Player rows
        int listTop = iy + 28;
        int listH = (y + h - 10) - listTop;
        List<PlayerStatsManager.PlayerRecord> records = getFilteredStats();
        int rowH = 30;
        int gap = 5;
        int totalH = records.size() * (rowH + gap) + 6;
        int maxScroll = Math.max(0, totalH - listH);
        targetStatsScroll = Math.max(0, Math.min(targetStatsScroll, maxScroll));

        g.enableScissor(x + pad, listTop, x + w - pad, y + h - 8);
        if (records.isEmpty()) {
            g.drawString(font, "暂无记录（进入服务器聊天或击杀后自动统计）", x + pad + 4, listTop + 10, AppleUiRenderer.TEXT_TERTIARY, false);
        } else {
            int ry = Math.round(listTop - statsScroll);
            for (PlayerStatsManager.PlayerRecord rec : records) {
                if (ry + rowH >= listTop && ry <= y + h) {
                    boolean muted = PlayerStatsManager.isMuted(rec.name);
                    boolean hov = isInside(mouseX, mouseY, x + pad, ry, innerW, rowH);
                    AppleUiRenderer.drawRoundedRect(g, x + pad, ry, innerW, rowH, 8, hov ? AppleUiRenderer.BG_CARD_HOVER : AppleUiRenderer.BG_CARD);
                    g.drawString(font, rec.name, x + pad + 10, ry + 6, muted ? AppleUiRenderer.APPLE_RED : AppleUiRenderer.TEXT_PRIMARY, false);
                    String sub = String.format("发言 %d  ·  击杀 %d  ·  死亡 %d", rec.messages, rec.kills, rec.deaths);
                    g.drawString(font, sub, x + pad + 10, ry + 18, AppleUiRenderer.TEXT_TERTIARY, false);

                    String mLabel = muted ? "取消拉黑" : "拉黑";
                    int mw = Math.max(46, font.width(mLabel) + 14);
                    int mx = x + w - pad - mw - 8;
                    AppleUiRenderer.drawRoundedRect(g, mx, ry + 6, mw, 18, 6, muted ? AppleUiRenderer.APPLE_RED : 0xFF282D3B);
                    g.drawString(font, mLabel, mx + (mw - font.width(mLabel)) / 2, ry + 11, muted ? 0xFFFFFFFF : AppleUiRenderer.TEXT_SECONDARY, false);
                }
                ry += rowH + gap;
            }
        }
        g.disableScissor();
    }

    private void renderModuleList(GuiGraphics g, int x, int y, int w, int h, int mouseX, int mouseY, float dt) {
        List<Module> modules = getFilteredModules();
        int cardH = 48;
        int gap = 7;
        int totalH = modules.size() * (cardH + gap) + 12;
        int maxScroll = Math.max(0, totalH - h);
        targetModuleScroll = Math.max(0, Math.min(targetModuleScroll, maxScroll));

        g.enableScissor(x, y, x + w, y + h - 6);
        int cy = Math.round(y + 6 - moduleScroll);
        for (Module m : modules) {
            if (cy + cardH >= y && cy <= y + h) {
                drawModuleCard(g, m, x + 8, cy, w - 14, cardH, mouseX, mouseY, dt);
            }
            cy += cardH + gap;
        }
        g.disableScissor();
    }

    private void drawModuleCard(GuiGraphics g, Module m, int x, int y, int w, int h, int mouseX, int mouseY, float dt) {
        boolean selected = m == selectedModule;
        boolean hovered = isInside(mouseX, mouseY, x, y, w, h);

        int bg = selected ? 0xFF23293A : (hovered ? AppleUiRenderer.BG_CARD_HOVER : AppleUiRenderer.BG_CARD);
        if (selected) {
            AppleUiRenderer.drawRoundedRect(g, x - 1, y - 1, w + 2, h + 2, 11, AppleUiRenderer.APPLE_BLUE);
        }
        AppleUiRenderer.drawRoundedRect(g, x, y, w, h, 10, bg);

        g.drawString(font, m.getName(), x + 12, y + 11, m.isEnabled() ? AppleUiRenderer.TEXT_PRIMARY : AppleUiRenderer.TEXT_SECONDARY, false);
        g.drawString(font, m.getSubtitle(), x + 12, y + 26, AppleUiRenderer.TEXT_TERTIARY, false);

        int sw = 28, sh = 16;
        int sx = x + w - sw - 10;
        int sy = y + (h - sh) / 2;
        float prog = animValue("mod:" + m.getId(), m.isEnabled(), dt);
        AppleUiRenderer.drawPillSwitch(g, sx, sy, sw, sh, prog);
    }

    private void renderInspector(GuiGraphics g, int x, int y, int w, int h, int mouseX, int mouseY, float dt) {
        AppleUiRenderer.drawRoundedRect(g, x + 2, y + 4, w - 10, h - 12, 10, AppleUiRenderer.BG_INSPECTOR);
        if (selectedModule == null) {
            g.drawString(font, "选择一个模块进行配置", x + 18, y + 22, AppleUiRenderer.TEXT_TERTIARY, false);
            return;
        }

        int pad = 14;
        int contentW = w - pad * 2 - 4;
        int cy = y + 14;

        g.drawString(font, selectedModule.getName(), x + pad, cy, AppleUiRenderer.TEXT_PRIMARY, false);
        String keyLabel = bindingModule == selectedModule ? "按下按键..." : ("绑定: " + getKeyName(selectedModule.getKeybind()));
        int kw = font.width(keyLabel) + 14;
        int kx = x + w - pad - kw - 4;
        boolean keyHover = isInside(mouseX, mouseY, kx, cy - 3, kw, 16);
        AppleUiRenderer.drawRoundedRect(g, kx, cy - 3, kw, 16, 6,
                bindingModule == selectedModule ? AppleUiRenderer.APPLE_BLUE : (keyHover ? AppleUiRenderer.BG_CARD_HOVER : AppleUiRenderer.BG_CARD));
        g.drawString(font, keyLabel, kx + 7, cy + 1,
                bindingModule == selectedModule ? 0xFFFFFFFF : AppleUiRenderer.TEXT_SECONDARY, false);

        cy += 15;
        g.drawString(font, trimToWidth(selectedModule.getDescription(), contentW), x + pad, cy, AppleUiRenderer.TEXT_TERTIARY, false);
        cy += 16;

        int listTop = cy;
        int listH = (y + h - 12) - listTop;

        int totalContentH = calculateInspectorHeight();
        int maxScroll = Math.max(0, totalContentH - listH);
        targetInspectorScroll = Math.max(0, Math.min(targetInspectorScroll, maxScroll));

        g.enableScissor(x + 4, listTop, x + w - 10, y + h - 12);
        int ry = Math.round(listTop - inspectorScroll);

        if (selectedModule instanceof CustomCapeModule capeMod) {
            ry = renderCapeGallery(g, capeMod, x + pad, ry, contentW, mouseX, mouseY);
        } else if (selectedModule instanceof CustomSkinModule skinMod) {
            ry = renderSkinGallery(g, skinMod, x + pad, ry, contentW, mouseX, mouseY);
        }

        for (Setting<?> s : selectedModule.getSettings()) {
            if (!s.isVisible()) continue;
            if (selectedModule instanceof CustomCapeModule && s.getId().equals("selectedCape")) continue;
            if (selectedModule instanceof CustomSkinModule && s.getId().equals("selectedSkin")) continue;
            ry = renderSettingRow(g, s, x + pad, ry, contentW, mouseX, mouseY, dt);
        }

        g.disableScissor();
    }

    private int calculateInspectorHeight() {
        int h = 0;
        if (selectedModule instanceof CustomCapeModule) {
            int count = CapeManager.getAllCapes().size();
            int rows = (count + 1) / 2;
            h += 16 + rows * 52;
        } else if (selectedModule instanceof CustomSkinModule) {
            int count = SkinManager.getAllSkins().size();
            int rows = Math.max(1, (count + 1) / 2);
            h += 16 + rows * 42;
        }
        for (Setting<?> s : selectedModule.getSettings()) {
            if (!s.isVisible()) continue;
            if (selectedModule instanceof CustomCapeModule && s.getId().equals("selectedCape")) continue;
            if (selectedModule instanceof CustomSkinModule && s.getId().equals("selectedSkin")) continue;
            h += (s instanceof DoubleSetting) ? 36 : 28;
        }
        return h + 12;
    }

    private int renderCapeGallery(GuiGraphics g, CustomCapeModule capeMod, int x, int y, int w, int mouseX, int mouseY) {
        g.drawString(font, "官方与自定义披风库 (" + CapeManager.getAllCapes().size() + " 款)", x, y, AppleUiRenderer.TEXT_SECONDARY, false);
        y += 14;

        List<CapeManager.CapeEntry> capes = CapeManager.getAllCapes();
        int cols = 2;
        int gap = 6;
        int cardW = (w - gap) / cols;
        int cardH = 46;

        for (int i = 0; i < capes.size(); i++) {
            CapeManager.CapeEntry entry = capes.get(i);
            int col = i % cols;
            int row = i / cols;
            int cx = x + col * (cardW + gap);
            int cy = y + row * (cardH + gap);

            boolean active = entry.id().equals(capeMod.selectedCape.get());
            boolean hovered = isInside(mouseX, mouseY, cx, cy, cardW, cardH);
            if (active) {
                AppleUiRenderer.drawRoundedRect(g, cx - 1, cy - 1, cardW + 2, cardH + 2, 9, AppleUiRenderer.APPLE_BLUE);
            }
            AppleUiRenderer.drawRoundedRect(g, cx, cy, cardW, cardH, 8, hovered ? AppleUiRenderer.BG_CARD_HOVER : AppleUiRenderer.BG_CARD);

            g.blit(RenderPipelines.GUI_TEXTURED, entry.textureId(), cx + 7, cy + 7, 1.0F, 1.0F, 20, 32, 10, 16, 64, 32);
            String label = trimToWidth(entry.displayName(), cardW - 36);
            g.drawString(font, label, cx + 32, cy + 13, active ? AppleUiRenderer.TEXT_PRIMARY : AppleUiRenderer.TEXT_SECONDARY, false);
            g.drawString(font, entry.custom() ? "本地" : "内置", cx + 32, cy + 26, active ? AppleUiRenderer.APPLE_BLUE : AppleUiRenderer.TEXT_TERTIARY, false);
        }

        int rows = (capes.size() + 1) / 2;
        return y + rows * (cardH + gap) + 6;
    }

    private int renderSkinGallery(GuiGraphics g, CustomSkinModule skinMod, int x, int y, int w, int mouseX, int mouseY) {
        g.drawString(font, "本地皮肤库 (点击切换)", x, y, AppleUiRenderer.TEXT_SECONDARY, false);
        y += 14;

        List<SkinManager.SkinEntry> skins = SkinManager.getAllSkins();
        if (skins.isEmpty()) {
            g.drawString(font, "暂无皮肤，点击下方“导入本地皮肤 PNG...”", x, y + 6, AppleUiRenderer.TEXT_TERTIARY, false);
            return y + 26;
        }

        int cols = 2;
        int gap = 6;
        int cardW = (w - gap) / cols;
        int cardH = 36;

        for (int i = 0; i < skins.size(); i++) {
            SkinManager.SkinEntry entry = skins.get(i);
            int col = i % cols;
            int row = i / cols;
            int cx = x + col * (cardW + gap);
            int cy = y + row * (cardH + gap);

            boolean active = entry.id().equals(skinMod.selectedSkin.get());
            boolean hovered = isInside(mouseX, mouseY, cx, cy, cardW, cardH);
            if (active) {
                AppleUiRenderer.drawRoundedRect(g, cx - 1, cy - 1, cardW + 2, cardH + 2, 9, AppleUiRenderer.APPLE_BLUE);
            }
            AppleUiRenderer.drawRoundedRect(g, cx, cy, cardW, cardH, 8, hovered ? AppleUiRenderer.BG_CARD_HOVER : AppleUiRenderer.BG_CARD);

            g.blit(RenderPipelines.GUI_TEXTURED, entry.textureId(), cx + 6, cy + 6, 8.0F, 8.0F, 24, 24, 8, 8, 64, 64);
            String label = trimToWidth(entry.displayName(), cardW - 36);
            g.drawString(font, label, cx + 34, cy + 14, active ? AppleUiRenderer.TEXT_PRIMARY : AppleUiRenderer.TEXT_SECONDARY, false);
        }

        int rows = (skins.size() + 1) / 2;
        return y + rows * (cardH + gap) + 6;
    }

    private int renderSettingRow(GuiGraphics g, Setting<?> s, int x, int y, int w, int mouseX, int mouseY, float dt) {
        if (s instanceof BoolSetting bs) {
            int h = 24;
            boolean hover = isInside(mouseX, mouseY, x, y, w, h);
            AppleUiRenderer.drawRoundedRect(g, x, y, w, h, 7, hover ? AppleUiRenderer.BG_CARD_HOVER : AppleUiRenderer.BG_CARD);
            g.drawString(font, trimToWidth(bs.getName(), w - 40), x + 8, y + 8, bs.get() ? AppleUiRenderer.TEXT_PRIMARY : AppleUiRenderer.TEXT_SECONDARY, false);
            float prog = animValue("set:" + selectedModule.getId() + ":" + bs.getId(), bs.get(), dt);
            AppleUiRenderer.drawPillSwitch(g, x + w - 32, y + 5, 24, 14, prog);
            return y + 28;
        }

        if (s instanceof DoubleSetting ds) {
            AppleUiRenderer.drawRoundedRect(g, x, y, w, 32, 7, AppleUiRenderer.BG_CARD);
            g.drawString(font, ds.getName(), x + 8, y + 5, AppleUiRenderer.TEXT_SECONDARY, false);
            String valStr = String.format(Locale.ROOT, "%.2f", ds.get());
            g.drawString(font, valStr, x + w - font.width(valStr) - 8, y + 5, AppleUiRenderer.APPLE_BLUE, false);

            int bx = x + 8;
            int bw = w - 16;
            int barY = y + 20;
            AppleUiRenderer.drawRoundedRect(g, bx, barY, bw, 4, 2, AppleUiRenderer.TOGGLE_OFF);
            double pct = (ds.get() - ds.getMin()) / Math.max(0.0001, ds.getMax() - ds.getMin());
            int fillW = (int) Math.round(bw * pct);
            if (fillW > 0) AppleUiRenderer.drawRoundedRect(g, bx, barY, fillW, 4, 2, AppleUiRenderer.APPLE_BLUE);
            AppleUiRenderer.drawCircle(g, bx + fillW - 5, barY - 3, 10, 0xFFFFFFFF);
            return y + 36;
        }

        if (s instanceof EnumSetting<?> es) {
            AppleUiRenderer.drawRoundedRect(g, x, y, w, 24, 7, AppleUiRenderer.BG_CARD);
            g.drawString(font, es.getName(), x + 8, y + 8, AppleUiRenderer.TEXT_SECONDARY, false);
            String val = es.get().name();
            int bw = font.width(val) + 12;
            int bx = x + w - bw - 6;
            AppleUiRenderer.drawRoundedRect(g, bx, y + 4, bw, 16, 5, 0xFF282E3D);
            g.drawString(font, val, bx + 6, y + 8, AppleUiRenderer.APPLE_BLUE, false);
            return y + 28;
        }

        if (s instanceof ActionSetting as) {
            int h = 24;
            boolean hover = isInside(mouseX, mouseY, x, y, w, h);
            AppleUiRenderer.drawRoundedRect(g, x, y, w, h, 7, hover ? AppleUiRenderer.APPLE_BLUE : 0x260A84FF);
            int tw = font.width(as.getName());
            g.drawString(font, as.getName(), x + (w - tw) / 2, y + 8, hover ? 0xFFFFFFFF : AppleUiRenderer.APPLE_BLUE, false);
            return y + 28;
        }

        if (s instanceof StringSetting ss) {
            AppleUiRenderer.drawRoundedRect(g, x, y, w, 24, 7, AppleUiRenderer.BG_CARD);
            g.drawString(font, ss.getName() + ": " + ss.get(), x + 8, y + 8, AppleUiRenderer.TEXT_SECONDARY, false);
            return y + 28;
        }

        return y + 26;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x();
        double my = event.y();
        int button = event.button();

        int tx = winX + SIDEBAR_W + 4;
        int tw = winW - SIDEBAR_W - 12;
        if (isInside(mx, my, tx + 6, winY + 9, tw - 36, 22)) {
            searchFocused = true;
            bindingMenuKey = false;
            bindingModule = null;
            return true;
        } else {
            searchFocused = false;
        }

        if (isInside(mx, my, tx + tw - 22, winY + 9, 22, 22)) {
            onClose();
            return true;
        }

        // Sidebar category clicks
        int cy = winY + 42;
        if (isInside(mx, my, winX + 12, cy, SIDEBAR_W - 22, 24)) {
            keybindsTab = false;
            statsTab = false;
            selectedCategory = null;
            targetModuleScroll = 0;
            return true;
        }
        cy += 28;
        for (Category cat : Category.values()) {
            if (isInside(mx, my, winX + 12, cy, SIDEBAR_W - 22, 24)) {
                keybindsTab = false;
                statsTab = false;
                selectedCategory = cat;
                targetModuleScroll = 0;
                return true;
            }
            cy += 28;
        }
        cy += 6;
        if (isInside(mx, my, winX + 12, cy, SIDEBAR_W - 22, 24)) {
            statsTab = true;
            keybindsTab = false;
            targetStatsScroll = 0;
            return true;
        }
        cy += 28;
        if (isInside(mx, my, winX + 12, cy, SIDEBAR_W - 22, 24)) {
            keybindsTab = true;
            statsTab = false;
            return true;
        }

        int contentY = winY + TOPBAR_H;
        int contentH = winH - TOPBAR_H;

        if (statsTab) {
            int px = winX + SIDEBAR_W;
            int pw = winW - SIDEBAR_W;
            int pad = 14;
            int innerW = pw - pad * 2;
            String[] sorts = {"按发言", "按击杀", "按死亡", "黑名单"};
            int sx = px + pad;
            for (int i = 0; i < sorts.length; i++) {
                int bw = font.width(sorts[i]) + 14;
                if (isInside(mx, my, sx, contentY + 6, bw, 20)) {
                    statsSortMode = i;
                    targetStatsScroll = 0;
                    return true;
                }
                sx += bw + 6;
            }
            int lw = font.width("打开日志") + 14;
            if (isInside(mx, my, px + pw - pad - lw, contentY + 6, lw, 20)) {
                PlayerStatsManager.openLogsFolder();
                return true;
            }

            int iy = contentY + 32;
            int addW = 64;
            int boxW = innerW - addW - 6;
            if (isInside(mx, my, px + pad, iy, boxW, 22)) {
                muteInputFocused = true;
                searchFocused = false;
                return true;
            } else {
                muteInputFocused = false;
            }
            if (isInside(mx, my, px + pad + boxW + 6, iy, addW, 22)) {
                if (!muteInput.isBlank()) {
                    PlayerStatsManager.addMute(muteInput.trim());
                    muteInput = "";
                }
                return true;
            }

            int listTop = iy + 28;
            if (my >= listTop) {
                int ry = Math.round(listTop - statsScroll);
                for (PlayerStatsManager.PlayerRecord rec : getFilteredStats()) {
                    if (isInside(mx, my, px + pad, ry, innerW, 30)) {
                        PlayerStatsManager.toggleMute(rec.name);
                        return true;
                    }
                    ry += 35;
                }
            }
            return true;
        } else {
            muteInputFocused = false;
        }

        if (keybindsTab) {
            int pad = 14;
            int ry = contentY + 38;
            int rowW = (winW - SIDEBAR_W) - pad * 2;
            int rx = winX + SIDEBAR_W + pad;
            if (isInside(mx, my, rx, ry, rowW, 28)) {
                if (button == 1) {
                    ConfigManager.setMenuKey(GLFW.GLFW_KEY_F9);
                    bindingMenuKey = false;
                } else {
                    bindingMenuKey = !bindingMenuKey;
                    bindingModule = null;
                }
                return true;
            }
            ry += 34;
            for (Module m : ModuleManager.getAll()) {
                if (isInside(mx, my, rx, ry, rowW, 28)) {
                    if (button == 1) {
                        m.setKeybind(-1);
                        bindingModule = null;
                    } else {
                        bindingModule = (bindingModule == m) ? null : m;
                        bindingMenuKey = false;
                    }
                    return true;
                }
                ry += 34;
            }
            return true;
        }

        int listX = winX + SIDEBAR_W;
        if (isInside(mx, my, listX, contentY, MODULE_LIST_W, contentH)) {
            List<Module> modules = getFilteredModules();
            int cardH = 48;
            int gap = 7;
            int mcy = Math.round(contentY + 6 - moduleScroll);
            for (Module m : modules) {
                int cardX = listX + 8;
                int cardW = MODULE_LIST_W - 14;
                if (isInside(mx, my, cardX, mcy, cardW, cardH)) {
                    int sw = 28, sh = 16;
                    int sx = cardX + cardW - sw - 10;
                    int sy = mcy + (cardH - sh) / 2;
                    if (button == 1 || isInside(mx, my, sx - 4, sy - 4, sw + 8, sh + 8)) {
                        m.toggle();
                    } else {
                        selectedModule = m;
                        targetInspectorScroll = 0;
                        inspectorScroll = 0;
                        bindingModule = null;
                    }
                    return true;
                }
                mcy += cardH + gap;
            }
        }

        int inspX = listX + MODULE_LIST_W;
        int inspW = winW - SIDEBAR_W - MODULE_LIST_W;
        if (selectedModule != null && isInside(mx, my, inspX, contentY, inspW, contentH)) {
            int pad = 14;
            int contentW = inspW - pad * 2 - 4;
            int headerY = contentY + 14;

            String keyLabel = bindingModule == selectedModule ? "按下按键..." : ("绑定: " + getKeyName(selectedModule.getKeybind()));
            int kw = font.width(keyLabel) + 14;
            int kx = inspX + inspW - pad - kw - 4;
            if (isInside(mx, my, kx, headerY - 3, kw, 16)) {
                if (button == 1) {
                    selectedModule.setKeybind(-1);
                    bindingModule = null;
                } else {
                    bindingModule = (bindingModule == selectedModule) ? null : selectedModule;
                    bindingMenuKey = false;
                }
                return true;
            }

            int listTop = headerY + 31;
            if (my < listTop) return true;

            int ry = Math.round(listTop - inspectorScroll);

            if (selectedModule instanceof CustomCapeModule capeMod) {
                ry += 14;
                List<CapeManager.CapeEntry> capes = CapeManager.getAllCapes();
                int gap = 6;
                int cardW = (contentW - gap) / 2;
                int cardH = 46;
                for (int i = 0; i < capes.size(); i++) {
                    int col = i % 2;
                    int row = i / 2;
                    int cx = inspX + pad + col * (cardW + gap);
                    int cy2 = ry + row * (cardH + gap);
                    if (isInside(mx, my, cx, cy2, cardW, cardH)) {
                        capeMod.selectedCape.set(capes.get(i).id());
                        if (!capeMod.isEnabled()) capeMod.setEnabled(true);
                        return true;
                    }
                }
                int rows = (capes.size() + 1) / 2;
                ry += rows * (cardH + gap) + 6;
            } else if (selectedModule instanceof CustomSkinModule skinMod) {
                ry += 14;
                List<SkinManager.SkinEntry> skins = SkinManager.getAllSkins();
                if (skins.isEmpty()) {
                    ry += 26;
                } else {
                    int gap = 6;
                    int cardW = (contentW - gap) / 2;
                    int cardH = 36;
                    for (int i = 0; i < skins.size(); i++) {
                        int col = i % 2;
                        int row = i / 2;
                        int cx = inspX + pad + col * (cardW + gap);
                        int cy2 = ry + row * (cardH + gap);
                        if (isInside(mx, my, cx, cy2, cardW, cardH)) {
                            skinMod.selectedSkin.set(skins.get(i).id());
                            if (!skinMod.isEnabled()) skinMod.setEnabled(true);
                            return true;
                        }
                    }
                    int rows = (skins.size() + 1) / 2;
                    ry += rows * (cardH + gap) + 6;
                }
            }

            for (Setting<?> s : selectedModule.getSettings()) {
                if (!s.isVisible()) continue;
                if (selectedModule instanceof CustomCapeModule && s.getId().equals("selectedCape")) continue;
                if (selectedModule instanceof CustomSkinModule && s.getId().equals("selectedSkin")) continue;

                int rowH = (s instanceof DoubleSetting) ? 36 : 28;
                if (isInside(mx, my, inspX + pad, ry, contentW, rowH)) {
                    if (s instanceof BoolSetting bs) {
                        bs.toggle();
                    } else if (s instanceof EnumSetting<?> es) {
                        es.cycle(button == 1 ? -1 : 1);
                    } else if (s instanceof ActionSetting as) {
                        as.trigger();
                    } else if (s instanceof DoubleSetting ds) {
                        draggingSlider = ds;
                        updateSlider(ds, mx, inspX + pad + 8, contentW - 16);
                    }
                    return true;
                }
                ry += rowH;
            }
        }

        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (draggingSlider != null && selectedModule != null) {
            int inspX = winX + SIDEBAR_W + MODULE_LIST_W;
            int inspW = winW - SIDEBAR_W - MODULE_LIST_W;
            int contentW = inspW - 28 - 4;
            updateSlider(draggingSlider, event.x(), inspX + 14 + 8, contentW - 16);
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        draggingSlider = null;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int contentY = winY + TOPBAR_H;
        int contentH = winH - TOPBAR_H;
        int listX = winX + SIDEBAR_W;
        int inspX = listX + MODULE_LIST_W;
        int inspW = winW - SIDEBAR_W - MODULE_LIST_W;

        float delta = (float) (-scrollY * 24.0);
        if (statsTab && isInside(mouseX, mouseY, winX + SIDEBAR_W, contentY, winW - SIDEBAR_W, contentH)) {
            targetStatsScroll += delta;
            return true;
        }
        if (!keybindsTab && !statsTab && isInside(mouseX, mouseY, listX, contentY, MODULE_LIST_W, contentH)) {
            targetModuleScroll += delta;
            return true;
        }
        if (!keybindsTab && !statsTab && isInside(mouseX, mouseY, inspX, contentY, inspW, contentH)) {
            targetInspectorScroll += delta;
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (bindingMenuKey) {
            if (key != GLFW.GLFW_KEY_ESCAPE) {
                ConfigManager.setMenuKey(key);
            }
            bindingMenuKey = false;
            return true;
        }
        if (bindingModule != null) {
            if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_BACKSPACE) {
                bindingModule.setKeybind(-1);
            } else {
                bindingModule.setKeybind(key);
            }
            bindingModule = null;
            return true;
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
            return true;
        }

        if (searchFocused) {
            if (key == GLFW.GLFW_KEY_BACKSPACE && !searchQuery.isEmpty()) {
                searchQuery = searchQuery.substring(0, searchQuery.length() - 1);
                targetModuleScroll = 0;
                return true;
            }
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                searchFocused = false;
                return true;
            }
        }

        if (key == ConfigManager.getMenuKey()) {
            onClose();
            return true;
        }

        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (muteInputFocused && event.isAllowedChatCharacter() && muteInput.length() < 16) {
            muteInput += event.codepointAsString();
            return true;
        }
        if (searchFocused && event.isAllowedChatCharacter()) {
            searchQuery += event.codepointAsString();
            targetModuleScroll = 0;
            return true;
        }
        return super.charTyped(event);
    }

    private void updateSlider(DoubleSetting ds, double mouseX, int barX, int barW) {
        double pct = Math.max(0.0, Math.min(1.0, (mouseX - barX) / (double) barW));
        ds.set(ds.getMin() + pct * (ds.getMax() - ds.getMin()));
    }

    private boolean isInside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    private String trimToWidth(String text, int maxWidth) {
        if (font.width(text) <= maxWidth) return text;
        String ellipsis = "...";
        int target = maxWidth - font.width(ellipsis);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (font.width(sb.toString() + c) > target) break;
            sb.append(c);
        }
        return sb + ellipsis;
    }

    private static String getKeyName(int key) {
        if (key <= 0) return "未绑定";
        String name = GLFW.glfwGetKeyName(key, 0);
        if (name != null) return name.toUpperCase(Locale.ROOT);
        return switch (key) {
            case GLFW.GLFW_KEY_RIGHT_SHIFT -> "RSHIFT";
            case GLFW.GLFW_KEY_LEFT_SHIFT -> "LSHIFT";
            case GLFW.GLFW_KEY_RIGHT_CONTROL -> "RCTRL";
            case GLFW.GLFW_KEY_LEFT_CONTROL -> "LCTRL";
            case GLFW.GLFW_KEY_RIGHT_ALT -> "RALT";
            case GLFW.GLFW_KEY_LEFT_ALT -> "LALT";
            case GLFW.GLFW_KEY_SPACE -> "SPACE";
            case GLFW.GLFW_KEY_TAB -> "TAB";
            case GLFW.GLFW_KEY_F6 -> "F6";
            case GLFW.GLFW_KEY_F7 -> "F7";
            case GLFW.GLFW_KEY_F8 -> "F8";
            case GLFW.GLFW_KEY_F9 -> "F9";
            case GLFW.GLFW_KEY_F10 -> "F10";
            default -> "KEY_" + key;
        };
    }
}
