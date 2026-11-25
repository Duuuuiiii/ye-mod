package com.ye.mod.ui;

import com.mojang.blaze3d.platform.InputConstants;
import com.ye.mod.core.ConfigManager;
import com.ye.mod.core.Module;
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
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Clean Apple-styled modal screen for configuring a single module's settings, keybind, and cape/skin gallery.
 */
public class ModuleSettingsScreen extends Screen {
    private final Screen parent;
    private final Module module;

    private double scrollY = 0;
    private double maxScroll = 0;
    private boolean listeningKeybind = false;
    private DoubleSetting draggingSlider = null;
    private int sliderDragX = 0;
    private int sliderDragW = 120;

    private final List<HitRect> hitRects = new ArrayList<>();

    private record HitRect(int x, int y, int w, int h, Runnable onClick, DoubleSetting slider) {}

    public ModuleSettingsScreen(Screen parent, Module module) {
        super(Component.literal(module.getName() + " 设置"));
        this.parent = parent;
        this.module = module;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        ConfigManager.saveAsync();
        Minecraft.getInstance().setScreen(parent);
    }

    private int modalW() {
        return Math.min(580, this.width - 36);
    }

    private int modalH() {
        return Math.min(410, this.height - 36);
    }

    private int modalX() {
        return (this.width - modalW()) / 2;
    }

    private int modalY() {
        return (this.height - modalH()) / 2;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        hitRects.clear();

        // Backdrop
        g.fill(0, 0, this.width, this.height, AppleUiRenderer.BG_BACKDROP);

        int mx = modalX();
        int my = modalY();
        int mw = modalW();
        int mh = modalH();

        AppleUiRenderer.drawRoundedRect(g, mx, my, mw, mh, 14, AppleUiRenderer.BG_WINDOW);

        // Header
        int headerH = 50;
        AppleUiRenderer.drawRoundedRect(g, mx, my, mw, headerH, 14, AppleUiRenderer.BG_SIDEBAR);
        g.fill(mx, my + headerH - 1, mx + mw, my + headerH, 0x22FFFFFF);

        // Back button
        int backX = mx + 12;
        int backY = my + 13;
        int backW = 54;
        int backH = 24;
        boolean backHover = inBounds(mouseX, mouseY, backX, backY, backW, backH);
        AppleUiRenderer.drawRoundedRect(g, backX, backY, backW, backH, 7, backHover ? AppleUiRenderer.BG_CARD_HOVER : AppleUiRenderer.BG_CARD);
        g.drawCenteredString(this.font, "← 返回", backX + backW / 2, backY + 8, AppleUiRenderer.TEXT_PRIMARY);
        hitRects.add(new HitRect(backX, backY, backW, backH, this::onClose, null));

        // Title + subtitle
        int titleX = backX + backW + 12;
        g.drawString(this.font, module.getName(), titleX, my + 11, AppleUiRenderer.TEXT_PRIMARY, false);
        g.drawString(this.font, module.getSubtitle(), titleX, my + 26, AppleUiRenderer.TEXT_TERTIARY, false);

        // Module enable pill switch on top right
        int swW = 36;
        int swH = 20;
        int swX = mx + mw - 14 - swW;
        int swY = my + 15;
        AppleUiRenderer.drawPillSwitch(g, swX, swY, swW, swH, module.isEnabled() ? 1.0f : 0.0f);
        hitRects.add(new HitRect(swX, swY, swW, swH, () -> {
            module.toggle();
            ConfigManager.saveAsync();
        }, null));

        // Keybind pill next to switch
        String keyLabel = listeningKeybind ? "按下按键..." : ("绑定: " + getKeyName(module.getKeybind()));
        int keyW = Math.max(76, this.font.width(keyLabel) + 16);
        int keyX = swX - 10 - keyW;
        int keyY = my + 13;
        int keyH = 24;
        boolean keyHover = inBounds(mouseX, mouseY, keyX, keyY, keyW, keyH);
        int keyBg = listeningKeybind ? AppleUiRenderer.APPLE_BLUE : (keyHover ? AppleUiRenderer.BG_CARD_HOVER : AppleUiRenderer.BG_CARD);
        AppleUiRenderer.drawRoundedRect(g, keyX, keyY, keyW, keyH, 7, keyBg);
        g.drawCenteredString(this.font, keyLabel, keyX + keyW / 2, keyY + 8, AppleUiRenderer.TEXT_PRIMARY);
        hitRects.add(new HitRect(keyX, keyY, keyW, keyH, () -> listeningKeybind = !listeningKeybind, null));

        // Scrollable content area
        int contentX = mx + 14;
        int contentY = my + headerH + 8;
        int contentW = mw - 28;
        int contentH = mh - headerH - 18;

        g.enableScissor(mx, contentY, mx + mw, contentY + contentH);

        int curY = (int) Math.round(contentY - scrollY);

        // Description card
        AppleUiRenderer.drawRoundedRect(g, contentX, curY, contentW, 28, 8, AppleUiRenderer.BG_CARD);
        g.drawString(this.font, module.getDescription(), contentX + 10, curY + 10, AppleUiRenderer.TEXT_SECONDARY, false);
        curY += 36;

        // Cape gallery
        if (module instanceof CustomCapeModule capeMod) {
            curY = renderCapeGallery(g, capeMod, contentX, curY, contentW, contentY, contentY + contentH, mouseX, mouseY);
        }

        // Skin gallery
        if (module instanceof CustomSkinModule skinMod) {
            curY = renderSkinGallery(g, skinMod, contentX, curY, contentW, contentY, contentY + contentH, mouseX, mouseY);
        }

        // Settings list
        for (Setting<?> setting : module.getSettings()) {
            if ((module instanceof CustomCapeModule && setting.getId().equals("selectedCape"))
                    || (module instanceof CustomSkinModule && setting.getId().equals("selectedSkin"))) {
                continue;
            }
            curY = renderSettingRow(g, setting, contentX, curY, contentW, contentY, contentY + contentH, mouseX, mouseY);
        }

        g.disableScissor();

        int totalHeight = curY - (int) Math.round(contentY - scrollY);
        maxScroll = Math.max(0, totalHeight - contentH);
        scrollY = Math.max(0, Math.min(maxScroll, scrollY));

        // Scrollbar indicator
        if (maxScroll > 0) {
            int barH = Math.max(24, (int) ((double) contentH * contentH / (contentH + maxScroll)));
            int barY = contentY + (int) ((contentH - barH) * (scrollY / maxScroll));
            AppleUiRenderer.drawRoundedRect(g, mx + mw - 6, barY, 3, barH, 2, 0x55FFFFFF);
        }
    }

    private int renderCapeGallery(GuiGraphics g, CustomCapeModule capeMod, int x, int y, int w, int clipTop, int clipBottom, int mouseX, int mouseY) {
        g.drawString(this.font, "披风列表 (点击选择)", x + 2, y + 2, AppleUiRenderer.TEXT_SECONDARY, false);
        y += 16;

        List<CapeManager.CapeEntry> capes = CapeManager.getAllCapes();
        int cols = 3;
        int gap = 6;
        int cellW = (w - gap * (cols - 1)) / cols;
        int cellH = 34;

        String activeId = capeMod.selectedCape.get();
        for (int i = 0; i < capes.size(); i++) {
            CapeManager.CapeEntry entry = capes.get(i);
            int col = i % cols;
            int row = i / cols;
            int cx = x + col * (cellW + gap);
            int cy = y + row * (cellH + gap);

            if (cy + cellH >= clipTop && cy <= clipBottom) {
                boolean selected = entry.id().equals(activeId);
                boolean hover = inBounds(mouseX, mouseY, cx, cy, cellW, cellH);
                int bg = selected ? 0xFF1D4ED8 : (hover ? AppleUiRenderer.BG_CARD_HOVER : AppleUiRenderer.BG_CARD);
                AppleUiRenderer.drawRoundedRect(g, cx, cy, cellW, cellH, 8, bg);

                // Tiny cape preview swatch (u=1,v=1,10x16 on 64x32)
                try {
                    g.blit(RenderPipelines.GUI_TEXTURED, entry.textureId(), cx + 6, cy + 5, 1.0f, 1.0f, 15, 24, 10, 16, 64, 32);
                } catch (Exception ignored) {
                    AppleUiRenderer.drawRoundedRect(g, cx + 6, cy + 5, 15, 24, 3, AppleUiRenderer.APPLE_BLUE);
                }

                String label = trimToWidth(entry.displayName(), cellW - 28);
                g.drawString(this.font, label, cx + 26, cy + 13, AppleUiRenderer.TEXT_PRIMARY, false);

                if (cy >= clipTop && cy + cellH <= clipBottom) {
                    hitRects.add(new HitRect(cx, cy, cellW, cellH, () -> {
                        capeMod.selectedCape.set(entry.id());
                        if (!capeMod.isEnabled()) {
                            capeMod.setEnabled(true);
                        }
                        ConfigManager.saveAsync();
                    }, null));
                }
            }
        }

        int rows = (capes.size() + cols - 1) / cols;
        return y + rows * (cellH + gap) + 8;
    }

    private int renderSkinGallery(GuiGraphics g, CustomSkinModule skinMod, int x, int y, int w, int clipTop, int clipBottom, int mouseX, int mouseY) {
        g.drawString(this.font, "皮肤列表 (点击选择)", x + 2, y + 2, AppleUiRenderer.TEXT_SECONDARY, false);
        y += 16;

        List<SkinManager.SkinEntry> skins = SkinManager.getAllSkins();
        if (skins.isEmpty()) {
            AppleUiRenderer.drawRoundedRect(g, x, y, w, 30, 8, AppleUiRenderer.BG_CARD);
            g.drawString(this.font, "暂无本地皮肤，请点击下方「导入本地皮肤 PNG...」", x + 10, y + 11, AppleUiRenderer.TEXT_TERTIARY, false);
            return y + 38;
        }

        int cols = 3;
        int gap = 6;
        int cellW = (w - gap * (cols - 1)) / cols;
        int cellH = 34;

        String activeId = skinMod.selectedSkin.get();
        for (int i = 0; i < skins.size(); i++) {
            SkinManager.SkinEntry entry = skins.get(i);
            int col = i % cols;
            int row = i / cols;
            int cx = x + col * (cellW + gap);
            int cy = y + row * (cellH + gap);

            if (cy + cellH >= clipTop && cy <= clipBottom) {
                boolean selected = entry.id().equals(activeId);
                boolean hover = inBounds(mouseX, mouseY, cx, cy, cellW, cellH);
                int bg = selected ? 0xFF1D4ED8 : (hover ? AppleUiRenderer.BG_CARD_HOVER : AppleUiRenderer.BG_CARD);
                AppleUiRenderer.drawRoundedRect(g, cx, cy, cellW, cellH, 8, bg);

                // Face preview (u=8,v=8,8x8 on 64x64)
                try {
                    g.blit(RenderPipelines.GUI_TEXTURED, entry.textureId(), cx + 6, cy + 5, 8.0f, 8.0f, 24, 24, 8, 8, 64, 64);
                } catch (Exception ignored) {
                    AppleUiRenderer.drawRoundedRect(g, cx + 6, cy + 5, 24, 24, 4, AppleUiRenderer.APPLE_GREEN);
                }

                String label = trimToWidth(entry.displayName(), cellW - 36);
                g.drawString(this.font, label, cx + 34, cy + 13, AppleUiRenderer.TEXT_PRIMARY, false);

                if (cy >= clipTop && cy + cellH <= clipBottom) {
                    hitRects.add(new HitRect(cx, cy, cellW, cellH, () -> {
                        skinMod.selectedSkin.set(entry.id());
                        if (!skinMod.isEnabled()) {
                            skinMod.setEnabled(true);
                        }
                        ConfigManager.saveAsync();
                    }, null));
                }
            }
        }

        int rows = (skins.size() + cols - 1) / cols;
        return y + rows * (cellH + gap) + 8;
    }

    private int renderSettingRow(GuiGraphics g, Setting<?> setting, int x, int y, int w, int clipTop, int clipBottom, int mouseX, int mouseY) {
        int rowH = (setting instanceof DoubleSetting) ? 46 : 38;
        if (y + rowH >= clipTop && y <= clipBottom) {
            boolean hover = inBounds(mouseX, mouseY, x, y, w, rowH);
            AppleUiRenderer.drawRoundedRect(g, x, y, w, rowH, 8, hover ? AppleUiRenderer.BG_CARD_HOVER : AppleUiRenderer.BG_CARD);

            g.drawString(this.font, setting.getName(), x + 12, y + 7, AppleUiRenderer.TEXT_PRIMARY, false);
            String desc = trimToWidth(setting.getDescription(), w - 165);
            g.drawString(this.font, desc, x + 12, y + 21, AppleUiRenderer.TEXT_TERTIARY, false);

            boolean clickableInClip = y >= clipTop - 4 && y + rowH <= clipBottom + 4;

            if (setting instanceof BoolSetting boolSet) {
                int swW = 34;
                int swH = 18;
                int swX = x + w - 12 - swW;
                int swY = y + 10;
                AppleUiRenderer.drawPillSwitch(g, swX, swY, swW, swH, boolSet.get() ? 1.0f : 0.0f);
                if (clickableInClip) {
                    hitRects.add(new HitRect(x, y, w, rowH, () -> {
                        boolSet.toggle();
                        ConfigManager.saveAsync();
                    }, null));
                }
            } else if (setting instanceof DoubleSetting dblSet) {
                String valText = formatDouble(dblSet.get());
                g.drawString(this.font, valText, x + w - 12 - this.font.width(valText), y + 7, AppleUiRenderer.APPLE_BLUE, false);

                int barW = Math.min(180, w / 2 - 20);
                int barH = 6;
                int barX = x + w - 12 - barW;
                int barY = y + 32;
                double pct = (dblSet.get() - dblSet.getMin()) / Math.max(0.0001, dblSet.getMax() - dblSet.getMin());
                pct = Math.max(0.0, Math.min(1.0, pct));

                AppleUiRenderer.drawRoundedRect(g, barX, barY, barW, barH, 3, 0xFF334155);
                int fillW = Math.max(6, (int) Math.round(barW * pct));
                AppleUiRenderer.drawRoundedRect(g, barX, barY, fillW, barH, 3, AppleUiRenderer.APPLE_BLUE);
                AppleUiRenderer.drawCircle(g, barX + fillW - 5, barY - 2, 10, 0xFFFFFFFF);

                if (clickableInClip) {
                    hitRects.add(new HitRect(barX - 4, barY - 6, barW + 8, 18, null, dblSet));
                }
            } else if (setting instanceof EnumSetting<?> enumSet) {
                String val = "< " + enumSet.get().name() + " >";
                int btnW = Math.max(84, this.font.width(val) + 18);
                int btnH = 22;
                int btnX = x + w - 12 - btnW;
                int btnY = y + 8;
                AppleUiRenderer.drawRoundedRect(g, btnX, btnY, btnW, btnH, 6, 0xFF273244);
                g.drawCenteredString(this.font, val, btnX + btnW / 2, btnY + 7, AppleUiRenderer.TEXT_PRIMARY);
                if (clickableInClip) {
                    hitRects.add(new HitRect(x, y, w, rowH, () -> {
                        enumSet.cycle(1);
                        ConfigManager.saveAsync();
                    }, null));
                }
            } else if (setting instanceof ActionSetting actionSet) {
                int btnW = 74;
                int btnH = 22;
                int btnX = x + w - 12 - btnW;
                int btnY = y + 8;
                AppleUiRenderer.drawRoundedRect(g, btnX, btnY, btnW, btnH, 6, AppleUiRenderer.APPLE_BLUE);
                g.drawCenteredString(this.font, "执行", btnX + btnW / 2, btnY + 7, AppleUiRenderer.TEXT_PRIMARY);
                if (clickableInClip) {
                    hitRects.add(new HitRect(btnX, btnY, btnW, btnH, actionSet::trigger, null));
                }
            } else if (setting instanceof StringSetting strSet) {
                String val = trimToWidth(strSet.get(), 140);
                g.drawString(this.font, val, x + w - 12 - this.font.width(val), y + 15, AppleUiRenderer.TEXT_SECONDARY, false);
            }
        }
        return y + rowH + 6;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x();
        double my = event.y();
        if (event.button() == 0) {
            for (int i = hitRects.size() - 1; i >= 0; i--) {
                HitRect r = hitRects.get(i);
                if (inBounds(mx, my, r.x, r.y, r.w, r.h)) {
                    if (r.slider != null) {
                        draggingSlider = r.slider;
                        sliderDragX = r.x + 4;
                        sliderDragW = r.w - 8;
                        updateSlider(mx);
                        return true;
                    }
                    if (r.onClick != null) {
                        r.onClick.run();
                        return true;
                    }
                }
            }
        } else if (event.button() == 1) {
            onClose();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (draggingSlider != null && event.button() == 0) {
            updateSlider(event.x());
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (draggingSlider != null) {
            draggingSlider = null;
            ConfigManager.saveAsync();
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        this.scrollY = Math.max(0, Math.min(maxScroll, this.scrollY - scrollY * 22));
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (listeningKeybind) {
            int key = event.key();
            if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_DELETE || key == GLFW.GLFW_KEY_BACKSPACE) {
                module.setKeybind(-1);
            } else {
                module.setKeybind(key);
            }
            listeningKeybind = false;
            ConfigManager.saveAsync();
            return true;
        }
        return super.keyPressed(event);
    }

    private void updateSlider(double mouseX) {
        if (draggingSlider == null) return;
        double pct = (mouseX - sliderDragX) / Math.max(1, sliderDragW);
        pct = Math.max(0.0, Math.min(1.0, pct));
        double val = draggingSlider.getMin() + pct * (draggingSlider.getMax() - draggingSlider.getMin());
        draggingSlider.set(val);
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

    private static String formatDouble(double v) {
        if (Math.abs(v - Math.round(v)) < 0.001) {
            return String.valueOf((long) Math.round(v));
        }
        return String.format(java.util.Locale.ROOT, "%.2f", v);
    }

    public static String getKeyName(int key) {
        if (key <= 0) return "未绑定";
        try {
            return InputConstants.Type.KEYSYM.getOrCreate(key).getDisplayName().getString();
        } catch (Exception e) {
            return "Key " + key;
        }
    }
}
