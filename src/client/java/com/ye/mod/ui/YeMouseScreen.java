package com.ye.mod.ui;

import com.ye.mod.core.ConfigManager;
import com.ye.mod.core.ModuleManager;
import com.ye.mod.core.setting.DoubleSetting;
import com.ye.mod.modules.visual.HudModule;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * Transparent mouse-UI & RusherHack-style HUD Layout Editor (toggled with F10):
 * - Drag & magnetic-snap ANY HUD widget directly on screen with live snap alignment guides!
 * - Supports inline text typing for creating/renaming Config Profiles!
 */
public class YeMouseScreen extends Screen {
    private boolean draggingWindow = false;
    private int dragOffX = 0;
    private int dragOffY = 0;

    private ConfigManager.HudSlot draggingHudSlot = null;
    private int hudDragOffX = 0;
    private int hudDragOffY = 0;

    private DoubleSetting draggingSlider = null;
    private int sliderX = 0;
    private int sliderW = 100;

    public YeMouseScreen() {
        super(Component.literal("Ye UI"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // Keep live 3D world & character crystal clear while tuning visuals and dragging HUD!
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (draggingHudSlot != null) {
            int cx = this.width / 2;
            int cy = this.height / 2;
            if (draggingHudSlot.anchorX == 1 && draggingHudSlot.x == 0) {
                g.fill(cx, 0, cx + 1, this.height, 0xAA38BDF8);
            }
            if (draggingHudSlot.anchorX == 0 && draggingHudSlot.x <= 6) {
                g.fill(4, 0, 5, this.height, 0x8838BDF8);
            } else if (draggingHudSlot.anchorX == 2 && draggingHudSlot.x <= 6) {
                g.fill(this.width - 5, 0, this.width - 4, this.height, 0x8838BDF8);
            }
            String tip = draggingHudSlot.zhName + " (" + anchorLabel(draggingHudSlot) + ")";
            g.drawCenteredString(this.font, tip, cx, cy + 28, 0xFF38BDF8);
        }

        YeQuickMenu.render(g, mouseX, mouseY, true);
    }

    private static String anchorLabel(ConfigManager.HudSlot s) {
        String h = switch (s.anchorX) {
            case 1 -> "居中对齐";
            case 2 -> "右吸附";
            default -> "左吸附";
        };
        String v = (s.anchorY == 2) ? "底吸附" : "顶吸附";
        return h + " · " + v;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x();
        double my = event.y();

        List<YeQuickMenu.HitRect> rects = YeQuickMenu.getHitRects();
        for (int i = rects.size() - 1; i >= 0; i--) {
            YeQuickMenu.HitRect r = rects.get(i);
            if (mx >= r.x() && mx <= r.x() + r.w() && my >= r.y() && my <= r.y() + r.h()) {
                if (event.button() == 0) {
                    if (r.slider() != null) {
                        draggingSlider = r.slider();
                        sliderX = r.x() + 3;
                        sliderW = r.w() - 6;
                        updateSlider(mx);
                        return true;
                    }
                    if (r.leftClick() != null) {
                        r.leftClick().run();
                        return true;
                    }
                } else if (event.button() == 1 && r.rightClick() != null) {
                    r.rightClick().run();
                    return true;
                }
            }
        }

        int wx = YeQuickMenu.getWinX(this.width);
        int wy = YeQuickMenu.getWinY(this.height);
        if (event.button() == 0 && mx >= wx && mx <= wx + YeQuickMenu.WIN_W && my >= wy && my <= wy + 29) {
            draggingWindow = true;
            dragOffX = (int) mx - wx;
            dragOffY = (int) my - wy;
            return true;
        }

        if (event.button() == 0 && !YeQuickMenu.isPointInsideMenuOrDropdown(mx, my, this.width, this.height)) {
            HudModule hm = ModuleManager.get(HudModule.class);
            if (hm != null && hm.isEnabled()) {
                for (ConfigManager.HudSlot slot : ConfigManager.HUD_SLOTS.values()) {
                    if (!slot.enabled || slot.lastW <= 0 || slot.lastH <= 0) continue;
                    if (mx >= slot.lastScreenX && mx <= slot.lastScreenX + slot.lastW
                            && my >= slot.lastScreenY && my <= slot.lastScreenY + slot.lastH) {
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
        if (draggingSlider != null && event.button() == 0) {
            updateSlider(event.x());
            return true;
        }
        if (draggingWindow && event.button() == 0) {
            ConfigManager.widgetX = Math.max(8, Math.min(this.width - YeQuickMenu.WIN_W - 8, (int) event.x() - dragOffX));
            ConfigManager.widgetY = Math.max(8, Math.min(this.height - YeQuickMenu.WIN_H - 8, (int) event.y() - dragOffY));
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
        if (draggingSlider != null) {
            draggingSlider = null;
            ConfigManager.saveAsync();
            return true;
        }
        if (draggingWindow) {
            draggingWindow = false;
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
        if (YeQuickMenu.profileInputMode != 0) {
            String ch = event.codepointAsString();
            if (ch != null && !ch.isEmpty() && YeQuickMenu.profileInputBuffer.length() < 24) {
                String clean = ch.replaceAll("[\\\\/:*?\"<>|\\r\\n]", "");
                YeQuickMenu.profileInputBuffer += clean;
            }
            return true;
        }
        return super.charTyped(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();

        // Inline Profile Name Editor
        if (YeQuickMenu.profileInputMode != 0) {
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                YeQuickMenu.confirmProfileInput();
                return true;
            }
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                YeQuickMenu.playClick();
                YeQuickMenu.profileInputMode = 0;
                return true;
            }
            if (key == GLFW.GLFW_KEY_BACKSPACE) {
                if (!YeQuickMenu.profileInputBuffer.isEmpty()) {
                    YeQuickMenu.profileInputBuffer = YeQuickMenu.profileInputBuffer.substring(
                            0, YeQuickMenu.profileInputBuffer.length() - 1);
                }
                return true;
            }
            return true;
        }

        // Per-module keybind capture
        if (YeQuickMenu.listeningModuleKey != null) {
            YeQuickMenu.playClick();
            if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_DELETE || key == GLFW.GLFW_KEY_BACKSPACE) {
                YeQuickMenu.listeningModuleKey.setKeybind(-1);
            } else {
                YeQuickMenu.listeningModuleKey.setKeybind(key);
            }
            YeQuickMenu.listeningModuleKey = null;
            return true;
        }

        // Global UI keybind capture
        if (YeQuickMenu.listeningKeySlot != 0) {
            YeQuickMenu.playClick();
            if (key != GLFW.GLFW_KEY_ESCAPE) {
                if (YeQuickMenu.listeningKeySlot == 1) ConfigManager.setMenuKey(key);
                else if (YeQuickMenu.listeningKeySlot == 2) ConfigManager.setMouseUnlockKey(key);
            }
            YeQuickMenu.listeningKeySlot = 0;
            return true;
        }

        if (key == ConfigManager.getMouseUnlockKey()) {
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
        if (key == ConfigManager.getMenuKey()) {
            YeQuickMenu.setOpen(false);
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    private void updateSlider(double mx) {
        if (draggingSlider == null) return;
        double pct = Math.max(0.0, Math.min(1.0, (mx - sliderX) / Math.max(1, sliderW)));
        draggingSlider.set(draggingSlider.getMin() + pct * (draggingSlider.getMax() - draggingSlider.getMin()));
    }
}
