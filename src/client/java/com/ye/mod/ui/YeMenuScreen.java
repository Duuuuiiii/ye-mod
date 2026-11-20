package com.ye.mod.ui;

import com.ye.mod.core.Category;
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
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Modern minimalist ClickGUI for Ye utility mod.
 */
public class YeMenuScreen extends Screen {
    // Palette
    private static final int BG_BACKDROP = 0x7806080E;
    private static final int BG_WINDOW = 0xF20E1118;
    private static final int BG_SIDEBAR = 0xFF0A0D13;
    private static final int BG_CARD = 0xFF151924;
    private static final int BG_CARD_HOVER = 0xFF1B2030;
    private static final int BG_INSPECTOR = 0xFF11151F;
    private static final int BORDER_SUBTLE = 0xFF23293A;
    private static final int ACCENT = 0xFF38BDF8;
    private static final int ACCENT_DIM = 0x3338BDF8;
    private static final int TEXT_PRIMARY = 0xFFF1F5F9;
    private static final int TEXT_MUTED = 0xFF94A3B8;
    private static final int TEXT_DIM = 0xFF64748B;
    private static final int TOGGLE_OFF = 0xFF283145;

    private Category selectedCategory = null; // null = All
    private Module selectedModule;
    private String searchQuery = "";
    private boolean searchFocused = false;
    private boolean bindingKey = false;

    private int moduleScroll = 0;
    private int inspectorScroll = 0;
    private DoubleSetting draggingSlider = null;

    private int winX, winY, winW, winH;
    private static final int SIDEBAR_W = 132;
    private static final int TOPBAR_H = 34;
    private static final int MODULE_LIST_W = 206;

    public YeMenuScreen() {
        super(Component.literal("Ye Menu"));
        List<Module> all = ModuleManager.getAll();
        if (!all.isEmpty()) {
            selectedModule = all.get(0);
        }
    }

    @Override
    protected void init() {
        winW = Math.min(580, this.width - 24);
        winH = Math.min(360, this.height - 24);
        winX = (this.width - winW) / 2;
        winY = (this.height - winH) / 2;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
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
        // Dimmed backdrop
        g.fill(0, 0, this.width, this.height, BG_BACKDROP);

        // Main window container
        g.fill(winX, winY, winX + winW, winY + winH, BG_WINDOW);
        g.renderOutline(winX, winY, winW, winH, BORDER_SUBTLE);
        // Top thin accent bar
        g.fill(winX, winY, winX + winW, winY + 2, ACCENT);

        renderSidebar(g, mouseX, mouseY);
        renderTopBar(g, mouseX, mouseY);

        int contentY = winY + TOPBAR_H;
        int contentH = winH - TOPBAR_H;
        int listX = winX + SIDEBAR_W;
        int inspX = listX + MODULE_LIST_W;
        int inspW = winW - SIDEBAR_W - MODULE_LIST_W;

        renderModuleList(g, listX, contentY, MODULE_LIST_W, contentH, mouseX, mouseY);
        renderInspector(g, inspX, contentY, inspW, contentH, mouseX, mouseY);
    }

    private void renderSidebar(GuiGraphics g, int mouseX, int mouseY) {
        g.fill(winX, winY + 2, winX + SIDEBAR_W, winY + winH, BG_SIDEBAR);
        g.fill(winX + SIDEBAR_W - 1, winY + 2, winX + SIDEBAR_W, winY + winH, BORDER_SUBTLE);

        // Brand header
        g.drawString(font, "ye", winX + 14, winY + 13, ACCENT, false);
        g.drawString(font, "3c3u · 1.21.11", winX + 14 + font.width("ye "), winY + 13, TEXT_DIM, false);

        // Category pills
        int cy = winY + 42;
        cy = drawCategoryPill(g, null, "全部功能", ModuleManager.getAll().size(), winX + 8, cy, SIDEBAR_W - 16, mouseX, mouseY);
        for (Category cat : Category.values()) {
            int count = ModuleManager.getByCategory(cat).size();
            cy = drawCategoryPill(g, cat, cat.getDisplayName(), count, winX + 8, cy, SIDEBAR_W - 16, mouseX, mouseY);
        }

        // Footer status
        int active = ModuleManager.getActiveCount();
        g.drawString(font, "已启用 " + active + " 项", winX + 12, winY + winH - 26, TEXT_MUTED, false);
        g.drawString(font, "RSHIFT 菜单", winX + 12, winY + winH - 14, TEXT_DIM, false);
    }

    private int drawCategoryPill(GuiGraphics g, Category cat, String label, int count, int x, int y, int w, int mouseX, int mouseY) {
        int h = 24;
        boolean selected = selectedCategory == cat;
        boolean hovered = isInside(mouseX, mouseY, x, y, w, h);

        if (selected) {
            g.fill(x, y, x + w, y + h, ACCENT_DIM);
            g.fill(x, y + 4, x + 2, y + h - 4, cat != null ? cat.getAccentColor() : ACCENT);
        } else if (hovered) {
            g.fill(x, y, x + w, y + h, 0x1AFFFFFF);
        }

        g.drawString(font, label, x + 10, y + 8, selected ? TEXT_PRIMARY : TEXT_MUTED, false);

        String countStr = String.valueOf(count);
        int cw = font.width(countStr);
        g.drawString(font, countStr, x + w - cw - 8, y + 8, selected ? ACCENT : TEXT_DIM, false);

        return y + h + 3;
    }

    private void renderTopBar(GuiGraphics g, int mouseX, int mouseY) {
        int tx = winX + SIDEBAR_W;
        int tw = winW - SIDEBAR_W;
        g.fill(tx, winY + 2, tx + tw, winY + TOPBAR_H, BG_SIDEBAR);
        g.fill(tx, winY + TOPBAR_H - 1, tx + tw, winY + TOPBAR_H, BORDER_SUBTLE);

        // Search input box
        int sx = tx + 10;
        int sy = winY + 7;
        int sw = tw - 44;
        int sh = 20;
        g.fill(sx, sy, sx + sw, sy + sh, BG_CARD);
        g.renderOutline(sx, sy, sw, sh, searchFocused ? ACCENT : BORDER_SUBTLE);

        String display = searchQuery.isEmpty() && !searchFocused ? "搜索功能或设置..." : searchQuery + (searchFocused ? "_" : "");
        int color = searchQuery.isEmpty() && !searchFocused ? TEXT_DIM : TEXT_PRIMARY;
        g.drawString(font, display, sx + 8, sy + 6, color, false);

        // Close button
        int cx = tx + tw - 26;
        int cy = winY + 7;
        boolean closeHover = isInside(mouseX, mouseY, cx, cy, 20, 20);
        if (closeHover) {
            g.fill(cx, cy, cx + 20, cy + 20, 0x33EF4444);
        }
        g.drawString(font, "×", cx + 7, cy + 6, closeHover ? 0xFFFCA5A5 : TEXT_MUTED, false);
    }

    private void renderModuleList(GuiGraphics g, int x, int y, int w, int h, int mouseX, int mouseY) {
        g.fill(x + w - 1, y, x + w, y + h, BORDER_SUBTLE);

        List<Module> modules = getFilteredModules();
        int cardH = 44;
        int gap = 6;
        int totalH = modules.size() * (cardH + gap) + 8;
        int maxScroll = Math.max(0, totalH - h);
        moduleScroll = Math.max(0, Math.min(moduleScroll, maxScroll));

        g.enableScissor(x, y, x + w - 1, y + h);
        int cy = y + 8 - moduleScroll;
        for (Module m : modules) {
            if (cy + cardH >= y && cy <= y + h) {
                drawModuleCard(g, m, x + 8, cy, w - 17, cardH, mouseX, mouseY);
            }
            cy += cardH + gap;
        }
        g.disableScissor();
    }

    private void drawModuleCard(GuiGraphics g, Module m, int x, int y, int w, int h, int mouseX, int mouseY) {
        boolean selected = m == selectedModule;
        boolean hovered = isInside(mouseX, mouseY, x, y, w, h);

        g.fill(x, y, x + w, y + h, hovered ? BG_CARD_HOVER : BG_CARD);
        g.renderOutline(x, y, w, h, selected ? ACCENT : BORDER_SUBTLE);

        if (m.isEnabled()) {
            g.fill(x, y + 6, x + 2, y + h - 6, m.getCategory().getAccentColor());
        }

        g.drawString(font, m.getName(), x + 10, y + 9, m.isEnabled() ? TEXT_PRIMARY : TEXT_MUTED, false);
        g.drawString(font, m.getSubtitle(), x + 10, y + 23, TEXT_DIM, false);

        // Toggle switch pill on the right
        int sw = 26;
        int sh = 13;
        int sx = x + w - sw - 10;
        int sy = y + (h - sh) / 2;
        drawSwitch(g, sx, sy, sw, sh, m.isEnabled());
    }

    private void renderInspector(GuiGraphics g, int x, int y, int w, int h, int mouseX, int mouseY) {
        g.fill(x, y, x + w, y + h, BG_INSPECTOR);
        if (selectedModule == null) {
            g.drawString(font, "选择一个模块进行配置", x + 16, y + 20, TEXT_DIM, false);
            return;
        }

        int pad = 12;
        int contentW = w - pad * 2;
        int cy = y + 12;

        // Module header
        g.drawString(font, selectedModule.getName(), x + pad, cy, TEXT_PRIMARY, false);
        String keyLabel = bindingKey ? "按下按键..." : ("绑定: " + getKeyName(selectedModule.getKeybind()));
        int kw = font.width(keyLabel) + 10;
        int kx = x + w - pad - kw;
        boolean keyHover = isInside(mouseX, mouseY, kx, cy - 2, kw, 14);
        g.fill(kx, cy - 2, kx + kw, cy + 12, bindingKey ? ACCENT_DIM : (keyHover ? BG_CARD_HOVER : BG_CARD));
        g.renderOutline(kx, cy - 2, kw, 14, bindingKey ? ACCENT : BORDER_SUBTLE);
        g.drawString(font, keyLabel, kx + 5, cy + 1, bindingKey ? ACCENT : TEXT_MUTED, false);

        cy += 14;
        g.drawString(font, trimToWidth(selectedModule.getDescription(), contentW), x + pad, cy, TEXT_DIM, false);
        cy += 14;
        g.fill(x + pad, cy, x + w - pad, cy + 1, BORDER_SUBTLE);
        cy += 8;

        int listTop = cy;
        int listH = (y + h) - listTop - 6;

        // Calculate total inspector height for scrolling
        int totalContentH = calculateInspectorHeight(contentW);
        int maxScroll = Math.max(0, totalContentH - listH);
        inspectorScroll = Math.max(0, Math.min(inspectorScroll, maxScroll));

        g.enableScissor(x, listTop, x + w, y + h);
        int ry = listTop - inspectorScroll;

        // Special visual cape picker grid when CustomCapeModule is selected
        if (selectedModule instanceof CustomCapeModule capeMod) {
            ry = renderCapeGallery(g, capeMod, x + pad, ry, contentW, mouseX, mouseY);
        }

        for (Setting<?> s : selectedModule.getSettings()) {
            if (!s.isVisible()) continue;
            // Skip raw selectedCape string row since the visual cape gallery handles it
            if (selectedModule instanceof CustomCapeModule && s.getId().equals("selectedCape")) continue;

            ry = renderSettingRow(g, s, x + pad, ry, contentW, mouseX, mouseY);
        }

        g.disableScissor();
    }

    private int calculateInspectorHeight(int contentW) {
        int h = 0;
        if (selectedModule instanceof CustomCapeModule) {
            int count = CapeManager.getAllCapes().size();
            int rows = (count + 1) / 2;
            h += 16 + rows * 52;
        }
        for (Setting<?> s : selectedModule.getSettings()) {
            if (!s.isVisible()) continue;
            if (selectedModule instanceof CustomCapeModule && s.getId().equals("selectedCape")) continue;
            h += (s instanceof DoubleSetting) ? 34 : 26;
        }
        return h + 12;
    }

    private int renderCapeGallery(GuiGraphics g, CustomCapeModule capeMod, int x, int y, int w, int mouseX, int mouseY) {
        g.drawString(font, "披风预览库 (点击切换)", x, y, TEXT_MUTED, false);
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
            g.fill(cx, cy, cx + cardW, cy + cardH, hovered ? BG_CARD_HOVER : BG_CARD);
            g.renderOutline(cx, cy, cardW, cardH, active ? ACCENT : BORDER_SUBTLE);

            // Draw front cape preview (10x16 UV from 64x32 texture)
            int texW = 20;
            int texH = 32;
            g.blit(RenderPipelines.GUI_TEXTURED, entry.textureId(), cx + 6, cy + 7, 1.0F, 1.0F, texW, texH, 10, 16, 64, 32);

            String label = trimToWidth(entry.displayName(), cardW - 34);
            g.drawString(font, label, cx + 30, cy + 13, active ? TEXT_PRIMARY : TEXT_MUTED, false);
            g.drawString(font, entry.custom() ? "本地" : "预设", cx + 30, cy + 26, active ? ACCENT : TEXT_DIM, false);
        }

        int rows = (capes.size() + 1) / 2;
        return y + rows * (cardH + gap) + 6;
    }

    private int renderSettingRow(GuiGraphics g, Setting<?> s, int x, int y, int w, int mouseX, int mouseY) {
        if (s instanceof BoolSetting bs) {
            int h = 22;
            boolean hover = isInside(mouseX, mouseY, x, y, w, h);
            if (hover) g.fill(x - 4, y, x + w + 4, y + h, 0x10FFFFFF);
            g.drawString(font, trimToWidth(bs.getName(), w - 34), x, y + 7, bs.get() ? TEXT_PRIMARY : TEXT_MUTED, false);
            drawSwitch(g, x + w - 24, y + 5, 24, 12, bs.get());
            return y + 26;
        }

        if (s instanceof DoubleSetting ds) {
            g.drawString(font, ds.getName(), x, y + 2, TEXT_MUTED, false);
            String valStr = String.format(Locale.ROOT, "%.2f", ds.get());
            g.drawString(font, valStr, x + w - font.width(valStr), y + 2, ACCENT, false);

            int barY = y + 16;
            g.fill(x, barY, x + w, barY + 4, TOGGLE_OFF);
            double pct = (ds.get() - ds.getMin()) / Math.max(0.0001, ds.getMax() - ds.getMin());
            int fillW = (int) Math.round(w * pct);
            g.fill(x, barY, x + fillW, barY + 4, ACCENT);
            g.fill(x + fillW - 2, barY - 2, x + fillW + 2, barY + 6, TEXT_PRIMARY);
            return y + 34;
        }

        if (s instanceof EnumSetting<?> es) {
            g.drawString(font, es.getName(), x, y + 7, TEXT_MUTED, false);
            String val = es.get().name();
            int bw = font.width(val) + 12;
            int bx = x + w - bw;
            g.fill(bx, y + 3, bx + bw, y + 19, BG_CARD);
            g.renderOutline(bx, y + 3, bw, 16, BORDER_SUBTLE);
            g.drawString(font, val, bx + 6, y + 7, ACCENT, false);
            return y + 26;
        }

        if (s instanceof ActionSetting as) {
            int h = 20;
            boolean hover = isInside(mouseX, mouseY, x, y + 2, w, h);
            g.fill(x, y + 2, x + w, y + 2 + h, hover ? ACCENT_DIM : BG_CARD);
            g.renderOutline(x, y + 2, w, h, hover ? ACCENT : BORDER_SUBTLE);
            int tw = font.width(as.getName());
            g.drawString(font, as.getName(), x + (w - tw) / 2, y + 8, hover ? TEXT_PRIMARY : ACCENT, false);
            return y + 26;
        }

        if (s instanceof StringSetting ss) {
            g.drawString(font, ss.getName() + ": " + ss.get(), x, y + 7, TEXT_MUTED, false);
            return y + 26;
        }

        return y + 24;
    }

    private void drawSwitch(GuiGraphics g, int x, int y, int w, int h, boolean on) {
        g.fill(x, y, x + w, y + h, on ? ACCENT : TOGGLE_OFF);
        int knobSize = h - 4;
        int knobX = on ? (x + w - knobSize - 2) : (x + 2);
        g.fill(knobX, y + 2, knobX + knobSize, y + 2 + knobSize, TEXT_PRIMARY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x();
        double my = event.y();
        int button = event.button();

        // Search box focus
        int tx = winX + SIDEBAR_W;
        int tw = winW - SIDEBAR_W;
        if (isInside(mx, my, tx + 10, winY + 7, tw - 44, 20)) {
            searchFocused = true;
            bindingKey = false;
            return true;
        } else {
            searchFocused = false;
        }

        // Close button
        if (isInside(mx, my, tx + tw - 26, winY + 7, 20, 20)) {
            onClose();
            return true;
        }

        // Sidebar categories
        int cy = winY + 42;
        if (isInside(mx, my, winX + 8, cy, SIDEBAR_W - 16, 24)) {
            selectedCategory = null;
            moduleScroll = 0;
            return true;
        }
        cy += 27;
        for (Category cat : Category.values()) {
            if (isInside(mx, my, winX + 8, cy, SIDEBAR_W - 16, 24)) {
                selectedCategory = cat;
                moduleScroll = 0;
                return true;
            }
            cy += 27;
        }

        int contentY = winY + TOPBAR_H;
        int contentH = winH - TOPBAR_H;
        int listX = winX + SIDEBAR_W;

        // Module list clicks
        if (isInside(mx, my, listX, contentY, MODULE_LIST_W, contentH)) {
            List<Module> modules = getFilteredModules();
            int cardH = 44;
            int gap = 6;
            int mcy = contentY + 8 - moduleScroll;
            for (Module m : modules) {
                int cardX = listX + 8;
                int cardW = MODULE_LIST_W - 17;
                if (isInside(mx, my, cardX, mcy, cardW, cardH)) {
                    int sw = 26, sh = 13;
                    int sx = cardX + cardW - sw - 10;
                    int sy = mcy + (cardH - sh) / 2;
                    if (button == 1 || isInside(mx, my, sx - 4, sy - 4, sw + 8, sh + 8)) {
                        m.toggle();
                    } else {
                        selectedModule = m;
                        inspectorScroll = 0;
                        bindingKey = false;
                    }
                    return true;
                }
                mcy += cardH + gap;
            }
        }

        // Inspector clicks
        int inspX = listX + MODULE_LIST_W;
        int inspW = winW - SIDEBAR_W - MODULE_LIST_W;
        if (selectedModule != null && isInside(mx, my, inspX, contentY, inspW, contentH)) {
            int pad = 12;
            int contentW = inspW - pad * 2;
            int headerY = contentY + 12;

            // Keybind button
            String keyLabel = bindingKey ? "按下按键..." : ("绑定: " + getKeyName(selectedModule.getKeybind()));
            int kw = font.width(keyLabel) + 10;
            int kx = inspX + inspW - pad - kw;
            if (isInside(mx, my, kx, headerY - 2, kw, 14)) {
                if (button == 1) {
                    selectedModule.setKeybind(-1);
                    bindingKey = false;
                } else {
                    bindingKey = !bindingKey;
                }
                return true;
            }

            int listTop = headerY + 37;
            if (my < listTop) return true;

            int ry = listTop - inspectorScroll;

            // Cape gallery clicks
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
                        if (!capeMod.isEnabled()) {
                            capeMod.setEnabled(true);
                        }
                        return true;
                    }
                }
                int rows = (capes.size() + 1) / 2;
                ry += rows * (cardH + gap) + 6;
            }

            for (Setting<?> s : selectedModule.getSettings()) {
                if (!s.isVisible()) continue;
                if (selectedModule instanceof CustomCapeModule && s.getId().equals("selectedCape")) continue;

                int rowH = (s instanceof DoubleSetting) ? 34 : 26;
                if (isInside(mx, my, inspX + pad - 4, ry, contentW + 8, rowH)) {
                    if (s instanceof BoolSetting bs) {
                        bs.toggle();
                    } else if (s instanceof EnumSetting<?> es) {
                        es.cycle(button == 1 ? -1 : 1);
                    } else if (s instanceof ActionSetting as) {
                        as.trigger();
                    } else if (s instanceof DoubleSetting ds) {
                        draggingSlider = ds;
                        updateSlider(ds, mx, inspX + pad, contentW);
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
            updateSlider(draggingSlider, event.x(), inspX + 12, inspW - 24);
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

        int delta = (int) (-scrollY * 18);
        if (isInside(mouseX, mouseY, listX, contentY, MODULE_LIST_W, contentH)) {
            moduleScroll += delta;
            return true;
        }
        if (isInside(mouseX, mouseY, inspX, contentY, inspW, contentH)) {
            inspectorScroll += delta;
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (bindingKey && selectedModule != null) {
            if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_BACKSPACE) {
                selectedModule.setKeybind(-1);
            } else {
                selectedModule.setKeybind(key);
            }
            bindingKey = false;
            return true;
        }

        if (searchFocused) {
            if (key == GLFW.GLFW_KEY_BACKSPACE && !searchQuery.isEmpty()) {
                searchQuery = searchQuery.substring(0, searchQuery.length() - 1);
                moduleScroll = 0;
                return true;
            }
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                searchFocused = false;
                return true;
            }
        }

        if (key == GLFW.GLFW_KEY_RIGHT_SHIFT) {
            onClose();
            return true;
        }

        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (searchFocused && event.isAllowedChatCharacter()) {
            searchQuery += event.codepointAsString();
            moduleScroll = 0;
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
            default -> "KEY_" + key;
        };
    }
}
