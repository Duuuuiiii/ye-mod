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
    private float openProgress = 0.0f;
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
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (ConfigManager.blurBackground) {
            super.renderBackground(g, mouseX, mouseY, partialTick);
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        hitRects.clear();
        AppleUiRenderer.updateThemeAnim();
        openProgress = AppleUiRenderer.smooth(openProgress, 1.0f, 16.0f, 0.03f);

        g.fill(0, 0, this.width, this.height, AppleUiRenderer.bgBackdrop());

        int slideY = Math.round((1.0f - openProgress) * 12.0f);
        g.pose().pushMatrix();
        g.pose().translate(0.0f, slideY);
        mouseY -= slideY;

        int mx = modalX();
        int my = modalY();
        int mw = modalW();
        int mh = modalH();

        AppleUiRenderer.drawPanel(g, mx, my, mw, mh, AppleUiRenderer.bgWindow());

        // Header
        int headerH = 44;
        g.fill(mx + 1, my + 1, mx + mw - 1, my + headerH, AppleUiRenderer.bgSidebar());
        g.fill(mx, my + headerH - 1, mx + mw, my + headerH, AppleUiRenderer.borderHairline());

        // Back button
        int backX = mx + 10;
        int backY = my + 11;
        int backW = 50;
        int backH = 22;
        boolean backHover = inBounds(mouseX, mouseY, backX, backY, backW, backH);
        AppleUiRenderer.drawPanel(g, backX, backY, backW, backH, backHover ? AppleUiRenderer.bgCardHover() : AppleUiRenderer.bgCard());
        AppleUiRenderer.drawCenteredText(g, this.font, ConfigManager.tr("← 返回", "← Back"), backX + backW / 2, backY + 7, AppleUiRenderer.textPrimary());
        hitRects.add(new HitRect(backX, backY, backW, backH, this::onClose, null));

        // Title
        int titleX = backX + backW + 10;
        String mTitle = ConfigManager.english ? module.getSubtitle() : module.getName();
        AppleUiRenderer.drawText(g, this.font, mTitle, titleX, my + 10, AppleUiRenderer.textPrimary());
        AppleUiRenderer.drawText(g, this.font, module.getSubtitle(), titleX, my + 24, AppleUiRenderer.textTertiary());

        // Module enable pill switch on top right
        int swW = 34;
        int swH = 18;
        int swX = mx + mw - 12 - swW;
        int swY = my + 13;
        AppleUiRenderer.drawPillSwitch(g, swX, swY, swW, swH, module.isEnabled() ? 1.0f : 0.0f);
        hitRects.add(new HitRect(swX, swY, swW, swH, () -> {
            module.toggle();
            ConfigManager.saveAsync();
        }, null));

        // Keybind pill next to switch
        String keyLabel = listeningKeybind ? ConfigManager.tr("按键...", "Key...") : (ConfigManager.tr("按键:", "Key:") + " " + getKeyName(module.getKeybind()));
        int keyW = Math.max(72, this.font.width(keyLabel) + 14);
        int keyX = swX - 8 - keyW;
        int keyY = my + 11;
        int keyH = 22;
        boolean keyHover = inBounds(mouseX, mouseY, keyX, keyY, keyW, keyH);
        int keyBg = listeningKeybind ? AppleUiRenderer.APPLE_BLUE : (keyHover ? AppleUiRenderer.bgCardHover() : AppleUiRenderer.bgCard());
        AppleUiRenderer.drawPanel(g, keyX, keyY, keyW, keyH, keyBg);
        AppleUiRenderer.drawCenteredText(g, this.font, keyLabel, keyX + keyW / 2, keyY + 7, AppleUiRenderer.textPrimary());
        hitRects.add(new HitRect(keyX, keyY, keyW, keyH, () -> listeningKeybind = !listeningKeybind, null));

        // Scrollable content area
        int contentX = mx + 12;
        int contentY = my + headerH + 8;
        int contentW = mw - 24;
        int contentH = mh - headerH - 16;

        g.enableScissor(mx, contentY, mx + mw, contentY + contentH);

        int curY = (int) Math.round(contentY - scrollY);

        // Description card
        AppleUiRenderer.drawPanel(g, contentX, curY, contentW, 24, AppleUiRenderer.bgCard());
        AppleUiRenderer.drawText(g, this.font, trimToWidth(module.getDescription(), contentW - 16), contentX + 8, curY + 8, AppleUiRenderer.textSecondary());
        curY += 30;

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
            g.fill(mx + mw - 5, barY, mx + mw - 2, barY + barH, 0x55FFFFFF);
        }
        g.pose().popMatrix();
    }

    private int renderCapeGallery(GuiGraphics g, CustomCapeModule capeMod, int x, int y, int w, int clipTop, int clipBottom, int mouseX, int mouseY) {
        AppleUiRenderer.drawText(g, this.font, ConfigManager.tr("披风列表", "Capes"), x + 2, y + 2, AppleUiRenderer.textSecondary());
        y += 14;

        List<CapeManager.CapeEntry> capes = CapeManager.getAllCapes();
        int cols = 3;
        int gap = 5;
        int cellW = (w - gap * (cols - 1)) / cols;
        int cellH = 32;

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
                int bg = selected ? AppleUiRenderer.activeRowBg(hover) : (hover ? AppleUiRenderer.bgCardHover() : AppleUiRenderer.bgCard());
                AppleUiRenderer.drawPanel(g, cx, cy, cellW, cellH, bg);

                try {
                    g.blit(RenderPipelines.GUI_TEXTURED, entry.textureId(), cx + 5, cy + 4, 1.0f, 1.0f, 15, 24, 10, 16, 64, 32);
                } catch (Exception ignored) {
                    g.fill(cx + 5, cy + 4, cx + 20, cy + 28, AppleUiRenderer.APPLE_BLUE);
                }

                String label = trimToWidth(entry.displayName(), cellW - 26);
                AppleUiRenderer.drawText(g, this.font, label, cx + 24, cy + 12, AppleUiRenderer.textPrimary());

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
        AppleUiRenderer.drawText(g, this.font, ConfigManager.tr("皮肤列表", "Skins"), x + 2, y + 2, AppleUiRenderer.textSecondary());
        y += 14;

        List<SkinManager.SkinEntry> skins = SkinManager.getAllSkins();
        if (skins.isEmpty()) {
            AppleUiRenderer.drawPanel(g, x, y, w, 28, AppleUiRenderer.bgCard());
            AppleUiRenderer.drawText(g, this.font, ConfigManager.tr("暂无本地皮肤", "No skins found"), x + 10, y + 10, AppleUiRenderer.textTertiary());
            return y + 34;
        }

        int cols = 3;
        int gap = 5;
        int cellW = (w - gap * (cols - 1)) / cols;
        int cellH = 32;

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
                int bg = selected ? AppleUiRenderer.activeRowBg(hover) : (hover ? AppleUiRenderer.bgCardHover() : AppleUiRenderer.bgCard());
                AppleUiRenderer.drawPanel(g, cx, cy, cellW, cellH, bg);

                try {
                    g.blit(RenderPipelines.GUI_TEXTURED, entry.textureId(), cx + 5, cy + 4, 8.0f, 8.0f, 24, 24, 8, 8, 64, 64);
                } catch (Exception ignored) {
                    g.fill(cx + 5, cy + 4, cx + 29, cy + 28, AppleUiRenderer.APPLE_GREEN);
                }

                String label = trimToWidth(entry.displayName(), cellW - 34);
                AppleUiRenderer.drawText(g, this.font, label, cx + 33, cy + 12, AppleUiRenderer.textPrimary());

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
        int rowH = (setting instanceof DoubleSetting) ? 42 : 34;
        if (y + rowH >= clipTop && y <= clipBottom) {
            boolean hover = inBounds(mouseX, mouseY, x, y, w, rowH);
            AppleUiRenderer.drawPanel(g, x, y, w, rowH, hover ? AppleUiRenderer.bgCardHover() : AppleUiRenderer.bgCard());

            AppleUiRenderer.drawText(g, this.font, setting.getName(), x + 10, y + 6, AppleUiRenderer.textPrimary());
            String desc = trimToWidth(setting.getDescription(), w - 160);
            AppleUiRenderer.drawText(g, this.font, desc, x + 10, y + 19, AppleUiRenderer.textTertiary());

            boolean clickableInClip = y >= clipTop - 4 && y + rowH <= clipBottom + 4;

            if (setting instanceof BoolSetting boolSet) {
                int swW = 32;
                int swH = 16;
                int swX = x + w - 10 - swW;
                int swY = y + 9;
                AppleUiRenderer.drawPillSwitch(g, swX, swY, swW, swH, boolSet.get() ? 1.0f : 0.0f);
                if (clickableInClip) {
                    hitRects.add(new HitRect(x, y, w, rowH, () -> {
                        boolSet.toggle();
                        ConfigManager.saveAsync();
                    }, null));
                }
            } else if (setting instanceof DoubleSetting dblSet) {
                String valText = formatDouble(dblSet.get());
                AppleUiRenderer.drawText(g, this.font, valText, x + w - 10 - this.font.width(valText), y + 6, AppleUiRenderer.APPLE_BLUE);

                int barW = Math.min(170, w / 2 - 20);
                int barH = 5;
                int barX = x + w - 10 - barW;
                int barY = y + 29;
                double pct = (dblSet.get() - dblSet.getMin()) / Math.max(0.0001, dblSet.getMax() - dblSet.getMin());
                pct = Math.max(0.0, Math.min(1.0, pct));

                g.fill(barX, barY, barX + barW, barY + barH, 0xFF334155);
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
