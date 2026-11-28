package com.ye.mod.ui;

import com.ye.mod.core.ConfigManager;
import com.ye.mod.core.KeyCombo;
import com.ye.mod.modules.hud.HudElementModule;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * Centered Large Mouse-UI & 9-Zone Magnetic HUD Interactive Canvas (F10):
 * - Z-priority hit-testing prevents any scrolled card click-through onto top tabs or modals!
 * - Supports 2D HSV Color Picker dragging, Double-Click inline number editing, Single/Combo/Mouse-Side-Button keybinds,
 *   and 9-Zone Magnetic HUD Snap Guides (4 corners, 4 edge midpoints, and screen center, flush down to 0px)!
 */
public class YeMouseScreen extends Screen {
    private ConfigManager.HudSlot draggingHudSlot = null;
    private int hudDragOffX = 0;
    private int hudDragOffY = 0;

    private boolean draggingMinBar = false;
    private int minDragOffX = 0, minDragOffY = 0;

    private YeQuickMenu.SliderBinding draggingSlider = null;
    private int sliderX = 0;
    private int sliderW = 100;

    private boolean draggingHsvSquare = false;
    private boolean draggingHueBar = false;

    private long lastClickMs = 0L;
    private double lastClickX = -100, lastClickY = -100;

    public YeMouseScreen() {
        super(Component.literal("Ye UI"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        boolean isDraggingHud = (draggingHudSlot != null);
        // Background blur (applies to Glass, Dark, Light based on uiBlurStrength; cancelled during HUD drag!)
        if (!isDraggingHud && !ConfigManager.uiMinimized && YeQuickMenu.isOpen()) {
            float blurStr = AppleUiRenderer.isGlass()
                    ? Math.max(0.15f, ConfigManager.uiBlurStrength)
                    : ConfigManager.uiBlurStrength;
            if (blurStr > 0.01f) {
                g.blurBeforeThisStratum();
                int veilAlpha = Math.round(blurStr * 135.0f);
                int veilRgb = AppleUiRenderer.lightTheme ? 0x00F1F5F9 : 0x00070B14;
                g.fill(0, 0, this.width, this.height, (veilAlpha << 24) | veilRgb);
            }
        }
        // Render all active HUDs ABOVE the background blur layer so they remain razor-sharp in HUD edit mode!
        HudElementModule.renderAllActive(g, true);
        if (isDraggingHud) {
            renderNineZoneSnapGuides(g, draggingHudSlot);
        }
        YeQuickMenu.render(g, mouseX, mouseY, true, isDraggingHud);
    }

    private void renderNineZoneSnapGuides(GuiGraphics g, ConfigManager.HudSlot slot) {
        int cx = this.width / 2;
        int cy = this.height / 2;
        int accent = ConfigManager.getAccentColor();
        int guideCol = AppleUiRenderer.withAlpha(accent, 0.55f);

        // Center crosshair axes when aligned
        if (slot.anchorX == 1 && slot.x == 0) {
            g.fill(cx, 0, cx + 1, this.height, guideCol);
        }
        if (slot.anchorY == 1 && slot.y == 0) {
            g.fill(0, cy, this.width, cy + 1, guideCol);
        }

        // 9 Magnetic Snap Zone Anchor Dots (4 corners, 4 edge midpoints, 1 center)
        int[] xs = {6, cx, this.width - 6};
        int[] ys = {6, cy, this.height - 6};
        for (int ix = 0; ix < 3; ix++) {
            for (int iy = 0; iy < 3; iy++) {
                boolean activeZone = (slot.anchorX == ix && slot.anchorY == iy && slot.x <= 4 && slot.y <= 4);
                float sz = activeZone ? 7.0f : 4.2f;
                int col = activeZone ? accent : 0x5594A3B8;
                AppleUiRenderer.drawCircle(g, xs[ix] - sz * 0.5f, ys[iy] - sz * 0.5f, sz, col);
            }
        }

        String nm = ConfigManager.english ? slot.enName : slot.zhName;
        String tip = nm + " · " + anchorLabel(slot);
        int tw = YeHdFont.width(tip, 8.6f, true) + 18;
        int ty = Math.min(this.height - 26, Math.max(8, slot.lastScreenY - 22));
        int tx = Math.max(6, Math.min(this.width - tw - 6, slot.lastScreenX + (slot.lastW - tw) / 2));
        AppleUiRenderer.drawRoundedRect(g, tx, ty, tw, 18, AppleUiRenderer.isMechanical() ? 0 : 6, 0xE80B0F19);
        AppleUiRenderer.drawRoundedOutline(g, tx, ty, tw, 18, AppleUiRenderer.isMechanical() ? 0 : 6, 1.0f, accent);
        YeHdFont.drawCenteredInBox(g, tip, tx, ty, tw, 18, 8.6f, accent, false, true);
    }

    private static String anchorLabel(ConfigManager.HudSlot s) {
        String h = switch (s.anchorX) {
            case 1 -> ConfigManager.tr("水平居中", "Center-X");
            case 2 -> ConfigManager.tr("右缘", "Right");
            default -> ConfigManager.tr("左缘", "Left");
        };
        String v = switch (s.anchorY) {
            case 1 -> ConfigManager.tr("垂直居中", "Center-Y");
            case 2 -> ConfigManager.tr("底边", "Bottom");
            default -> ConfigManager.tr("顶边", "Top");
        };
        return h + " · " + v;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x();
        double my = event.y();
        int btn = event.button();

        // 0. Mouse side buttons (MB4/MB5/Middle) while recording a keybind!
        if (YeQuickMenu.activeKeybindConsumer != null && btn >= 2) {
            long handle = MinecraftHolder.windowHandle();
            int packed = KeyCombo.pack(KeyCombo.MOUSE_OFFSET + btn,
                    KeyCombo.isCtrlDown(handle), KeyCombo.isShiftDown(handle), KeyCombo.isAltDown(handle));
            YeQuickMenu.activeKeybindConsumer.accept(packed);
            YeQuickMenu.stopKeybindListen();
            YeQuickMenu.playClick();
            return true;
        }

        // Detect fast double-click (< 340ms within 6px)
        long now = System.currentTimeMillis();
        boolean isDbl = doubleClick || (btn == 0 && (now - lastClickMs < 340L) && Math.hypot(mx - lastClickX, my - lastClickY) <= 6.0);
        lastClickMs = now;
        lastClickX = mx;
        lastClickY = my;

        // 0.5 Drag minimized "Yer" bar when clicking its left handle area
        if (btn == 0 && ConfigManager.uiMinimized && mx >= YeQuickMenu.minWinX && mx <= YeQuickMenu.minWinX + YeQuickMenu.MIN_WIN_W - 18
                && my >= YeQuickMenu.minWinY && my <= YeQuickMenu.minWinY + YeQuickMenu.MIN_WIN_H) {
            draggingMinBar = true;
            minDragOffX = (int) mx - YeQuickMenu.minWinX;
            minDragOffY = (int) my - YeQuickMenu.minWinY;
            return true;
        }

        // 1. Check 2D HSV Color Picker Interactive Dragging first if open!
        if (btn == 0 && (YeQuickMenu.openColorSetting != null || YeQuickMenu.openThemeCustomColor)) {
            int svX = YeQuickMenu.pickerX + 8;
            int svY = YeQuickMenu.pickerY + 8;
            int hueY = svY + YeQuickMenu.pickerSvH + 7;
            if (mx >= svX && mx <= svX + YeQuickMenu.pickerW && my >= svY && my <= svY + YeQuickMenu.pickerSvH) {
                draggingHsvSquare = true;
                updateHsvFromMouse(mx, my);
                return true;
            }
            if (mx >= svX && mx <= svX + YeQuickMenu.pickerW && my >= hueY - 2 && my <= hueY + 11) {
                draggingHueBar = true;
                updateHueFromMouse(mx);
                return true;
            }
        }

        // 2. Find highest zPriority matching HitRect!
        List<YeQuickMenu.HitRect> rects = YeQuickMenu.getHitRects();
        YeQuickMenu.HitRect best = null;
        for (int i = rects.size() - 1; i >= 0; i--) {
            YeQuickMenu.HitRect r = rects.get(i);
            if (mx >= r.x() && mx <= r.x() + r.w() && my >= r.y() && my <= r.y() + r.h()) {
                if (best == null || r.zPriority() > best.zPriority()) {
                    best = r;
                }
            }
        }

        if (best != null) {
            if (btn == 0) {
                if (isDbl && best.doubleClick() != null) {
                    best.doubleClick().run();
                    return true;
                }
                if (YeQuickMenu.textInputKind == 5) {
                    YeQuickMenu.commitInlineInputIfNumber();
                }
                if (best.slider() != null) {
                    draggingSlider = best.slider();
                    sliderX = best.x() + 3;
                    sliderW = best.w() - 6;
                    updateSlider(mx);
                    return true;
                }
                if (best.leftClick() != null) {
                    best.leftClick().run();
                    return true;
                }
            } else if (btn == 1 && best.rightClick() != null) {
                best.rightClick().run();
                return true;
            }
            if (best.zPriority() >= 5) {
                return true;
            }
        } else if (YeQuickMenu.textInputKind == 5) {
            YeQuickMenu.commitInlineInputIfNumber();
        }

        // 3. Screen HUD interaction outside menu: Left-click drags with 9-zone magnetic snap, Right-click opens HUD settings!
        if (!YeQuickMenu.isPointInsideMenuOrOverlay(mx, my, this.width, this.height)) {
            for (ConfigManager.HudSlot slot : ConfigManager.HUD_SLOTS.values()) {
                if (!slot.enabled || slot.lastW <= 0 || slot.lastH <= 0) continue;
                if (mx >= slot.lastScreenX && mx <= slot.lastScreenX + slot.lastW
                        && my >= slot.lastScreenY && my <= slot.lastScreenY + slot.lastH) {
                    if (btn == 1) {
                        HudElementModule targetHud = HudElementModule.findBySlotId(slot.id);
                        if (targetHud != null) {
                            YeQuickMenu.openHudElementSettings(targetHud);
                            return true;
                        }
                    } else if (btn == 0) {
                        draggingHudSlot = slot;
                        hudDragOffX = (int) mx - slot.lastScreenX;
                        hudDragOffY = (int) my - slot.lastScreenY;
                        YeQuickMenu.playClick();
                        return true;
                    }
                }
            }
        }

        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (draggingMinBar && event.button() == 0) {
            YeQuickMenu.minWinX = Math.max(4, Math.min(this.width - YeQuickMenu.MIN_WIN_W - 4, (int) event.x() - minDragOffX));
            YeQuickMenu.minWinY = Math.max(4, Math.min(this.height - YeQuickMenu.MIN_WIN_H - 4, (int) event.y() - minDragOffY));
            return true;
        }
        if (draggingHsvSquare && event.button() == 0) {
            updateHsvFromMouse(event.x(), event.y());
            return true;
        }
        if (draggingHueBar && event.button() == 0) {
            updateHueFromMouse(event.x());
            return true;
        }
        if (draggingSlider != null && event.button() == 0) {
            updateSlider(event.x());
            return true;
        }
        if (draggingHudSlot != null && event.button() == 0) {
            int nx = (int) event.x() - hudDragOffX;
            int ny = (int) event.y() - hudDragOffY;
            draggingHudSlot.updateFromScreenPos(nx, ny, this.width, this.height);
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (draggingMinBar) {
            draggingMinBar = false;
            return true;
        }
        if (draggingHsvSquare || draggingHueBar) {
            draggingHsvSquare = false;
            draggingHueBar = false;
            ConfigManager.saveAsync();
            return true;
        }
        if (draggingSlider != null) {
            draggingSlider = null;
            ConfigManager.saveAsync();
            return true;
        }
        if (draggingHudSlot != null) {
            draggingHudSlot = null;
            ConfigManager.saveAsync();
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        YeQuickMenu.addScroll(mouseX, mouseY, scrollY);
        return true;
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        String ch = event.codepointAsString();
        if (ch == null || ch.isEmpty()) return false;

        if (YePickerModal.isOpen() && YePickerModal.searchFocused) {
            if (YePickerModal.searchBuffer.length() < 28) {
                YePickerModal.searchBuffer += ch.replaceAll("[\\r\\n]", "");
            }
            return true;
        }

        if (YeQuickMenu.textInputKind != 0) {
            if (YeQuickMenu.textInputKind == 5) {
                // Numeric filter
                String clean = ch.replaceAll("[^0-9.\\-]", "");
                if (!clean.isEmpty() && YeQuickMenu.textInputBuffer.length() < 12) {
                    YeQuickMenu.textInputBuffer += clean;
                }
            } else if (YeQuickMenu.textInputBuffer.length() < 80) {
                String clean = (YeQuickMenu.textInputKind == 1 || YeQuickMenu.textInputKind == 2)
                        ? ch.replaceAll("[\\\\/:*?\"<>|\\r\\n]", "")
                        : ch.replaceAll("[\\r\\n]", "");
                YeQuickMenu.textInputBuffer += clean;
            }
            return true;
        }
        return super.charTyped(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        long handle = MinecraftHolder.windowHandle();

        // 1. Picker Modal Search Input
        if (YePickerModal.isOpen()) {
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                YeQuickMenu.playClick();
                YePickerModal.close();
                return true;
            }
            if (key == GLFW.GLFW_KEY_BACKSPACE && !YePickerModal.searchBuffer.isEmpty()) {
                YePickerModal.searchBuffer = YePickerModal.searchBuffer.substring(0, YePickerModal.searchBuffer.length() - 1);
                return true;
            }
            return true;
        }

        // 2. Inline Text / Number / Profile / Friend ID Input
        if (YeQuickMenu.textInputKind != 0) {
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                YeQuickMenu.confirmTextInput();
                return true;
            }
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                YeQuickMenu.playClick();
                YeQuickMenu.textInputKind = 0;
                return true;
            }
            if (key == GLFW.GLFW_KEY_BACKSPACE) {
                if (!YeQuickMenu.textInputBuffer.isEmpty()) {
                    YeQuickMenu.textInputBuffer = YeQuickMenu.textInputBuffer.substring(0, YeQuickMenu.textInputBuffer.length() - 1);
                }
                return true;
            }
            return true;
        }

        // 3. Single Key OR Combo Keybind Recording!
        if (YeQuickMenu.activeKeybindConsumer != null) {
            if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_DELETE || key == GLFW.GLFW_KEY_BACKSPACE) {
                YeQuickMenu.playClick();
                if (!YeQuickMenu.activeKeybindId.startsWith("glob_")) {
                    YeQuickMenu.activeKeybindConsumer.accept(-1);
                }
                YeQuickMenu.stopKeybindListen();
                return true;
            }
            if (KeyCombo.isModifierKey(key)) {
                // Remember modifier in case user wants to bind standalone LShift/RShift/LAlt; wait for non-mod key or keyRelease!
                YeQuickMenu.pendingModifierKeyAlone = key;
                return true;
            }
            int combo = KeyCombo.pack(key, KeyCombo.isCtrlDown(handle), KeyCombo.isShiftDown(handle), KeyCombo.isAltDown(handle));
            YeQuickMenu.activeKeybindConsumer.accept(combo);
            YeQuickMenu.stopKeybindListen();
            YeQuickMenu.playClick();
            return true;
        }

        // 4. Toggle Close on pressing F10 or F9 again!
        if (KeyCombo.matchesScreenKey(handle, key, ConfigManager.getMouseUnlockKey())
                || KeyCombo.matchesScreenKey(handle, key, ConfigManager.getMenuKey())) {
            YeQuickMenu.playClick();
            YeQuickMenu.setOpen(false);
            onClose();
            return true;
        }

        if (key == GLFW.GLFW_KEY_ESCAPE) {
            YeQuickMenu.handleEsc();
            if (!YeQuickMenu.isOpen()) {
                onClose();
            }
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean keyReleased(KeyEvent event) {
        // If user pressed and released a standalone modifier key (like RShift or LAlt) without pressing any other key, bind that single modifier key!
        if (YeQuickMenu.activeKeybindConsumer != null && YeQuickMenu.pendingModifierKeyAlone > 0
                && event.key() == YeQuickMenu.pendingModifierKeyAlone) {
            YeQuickMenu.activeKeybindConsumer.accept(YeQuickMenu.pendingModifierKeyAlone);
            YeQuickMenu.stopKeybindListen();
            YeQuickMenu.playClick();
            return true;
        }
        return super.keyReleased(event);
    }

    private void updateSlider(double mx) {
        if (draggingSlider == null) return;
        double pct = Math.max(0.0, Math.min(1.0, (mx - sliderX) / Math.max(1, sliderW)));
        draggingSlider.setValue(draggingSlider.getMin() + pct * (draggingSlider.getMax() - draggingSlider.getMin()));
    }

    private void updateHsvFromMouse(double mx, double my) {
        int svX = YeQuickMenu.pickerX + 8;
        int svY = YeQuickMenu.pickerY + 8;
        float sat = (float) Math.max(0.0, Math.min(1.0, (mx - svX) / YeQuickMenu.pickerW));
        float bri = 1.0f - (float) Math.max(0.0, Math.min(1.0, (my - svY) / YeQuickMenu.pickerSvH));
        if (YeQuickMenu.openColorSetting != null) {
            YeQuickMenu.openColorSetting.setHsv(YeQuickMenu.openColorSetting.getHue(), sat, bri);
        } else if (YeQuickMenu.openThemeCustomColor) {
            ConfigManager.customAccentSat = sat;
            ConfigManager.customAccentBri = bri;
        }
    }

    private void updateHueFromMouse(double mx) {
        int svX = YeQuickMenu.pickerX + 8;
        float hue = (float) Math.max(0.0, Math.min(1.0, (mx - svX) / YeQuickMenu.pickerW));
        if (YeQuickMenu.openColorSetting != null) {
            YeQuickMenu.openColorSetting.setHsv(hue, YeQuickMenu.openColorSetting.getSat(), YeQuickMenu.openColorSetting.getBri());
        } else if (YeQuickMenu.openThemeCustomColor) {
            ConfigManager.customAccentHue = hue;
        }
    }

    private static final class MinecraftHolder {
        static long windowHandle() {
            var mc = net.minecraft.client.Minecraft.getInstance();
            return (mc != null && mc.getWindow() != null) ? mc.getWindow().handle() : 0L;
        }
    }
}
