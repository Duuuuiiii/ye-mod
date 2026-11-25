package com.ye.mod.ui;

import com.ye.mod.core.ConfigManager;
import com.ye.mod.core.setting.DoubleSetting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * Transparent mouse-unlock overlay (toggled with F10) over the live YeQuickMenu widget.
 * Allows dragging the widget, dragging sliders, and selecting dropdown items while
 * seeing live character changes in the center of the screen!
 */
public class YeMouseScreen extends Screen {
    private boolean draggingWindow = false;
    private int dragOffX = 0;
    private int dragOffY = 0;

    private DoubleSetting draggingSlider = null;
    private int sliderX = 0;
    private int sliderW = 100;

    public YeMouseScreen() {
        super(Component.literal("Ye Mouse"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // Transparent: never blur or darken the live world so the player sees cosmetic changes clearly!
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        YeQuickMenu.render(g, mouseX, mouseY, true);
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
                        sliderX = r.x() + 2;
                        sliderW = r.w() - 4;
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

        // Drag window by header bar
        int wx = YeQuickMenu.getWinX(this.width);
        int wy = YeQuickMenu.getWinY(this.height);
        if (event.button() == 0 && mx >= wx && mx <= wx + YeQuickMenu.WIN_W && my >= wy && my <= wy + 24) {
            draggingWindow = true;
            dragOffX = (int) mx - wx;
            dragOffY = (int) my - wy;
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
        if (draggingWindow && event.button() == 0) {
            ConfigManager.widgetX = Math.max(8, Math.min(this.width - YeQuickMenu.WIN_W - 8, (int) event.x() - dragOffX));
            ConfigManager.widgetY = Math.max(8, Math.min(this.height - YeQuickMenu.WIN_H - 8, (int) event.y() - dragOffY));
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
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        YeQuickMenu.addScroll(scrollY);
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
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
