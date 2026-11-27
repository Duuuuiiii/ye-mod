package com.ye.mod.modules.system;

import com.mojang.blaze3d.platform.Window;
import com.ye.mod.core.Category;
import com.ye.mod.core.Module;
import com.ye.mod.core.setting.BoolSetting;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWVidMode;

/**
 * Switches the game window into true borderless windowed fullscreen cleanly on first toggle.
 */
public class BorderlessFullscreenModule extends Module {
    public final BoolSetting restoreBounds = addSetting(new BoolSetting(
            "restoreBounds", "关闭恢复尺寸", "退出无边框全屏时恢复原窗口位置与尺寸", true
    ));

    private int savedX = 100;
    private int savedY = 100;
    private int savedWidth = 1280;
    private int savedHeight = 720;
    private boolean hasSavedBounds = false;

    public BorderlessFullscreenModule() {
        super("borderless_fullscreen", "无边框全屏", "Borderless", "无边框全屏窗口，切屏无黑屏延迟", Category.UTILITY);
    }

    @Override
    protected void onEnable() {
        applyBorderless();
    }

    @Override
    protected void onDisable() {
        restoreWindowed();
    }

    public void applyOnStartupIfEnabled() {
        if (isEnabled()) {
            applyBorderless();
        }
    }

    private void applyBorderless() {
        Window window = mc.getWindow();
        if (window == null) return;
        long handle = window.handle();
        if (handle == 0L) return;

        long monitor = GLFW.glfwGetWindowMonitor(handle);
        if (monitor == 0L) {
            monitor = GLFW.glfwGetPrimaryMonitor();
        }
        if (monitor == 0L) return;

        GLFWVidMode vidMode = GLFW.glfwGetVideoMode(monitor);
        if (vidMode == null) return;

        int[] mx = new int[1], my = new int[1];
        GLFW.glfwGetMonitorPos(monitor, mx, my);

        if (!window.isFullscreen()) {
            int[] wx = new int[1], wy = new int[1], ww = new int[1], wh = new int[1];
            GLFW.glfwGetWindowPos(handle, wx, wy);
            GLFW.glfwGetWindowSize(handle, ww, wh);
            if (ww[0] > 200 && wh[0] > 200 && (ww[0] < vidMode.width() || wh[0] < vidMode.height())) {
                savedX = wx[0];
                savedY = wy[0];
                savedWidth = ww[0];
                savedHeight = wh[0];
                hasSavedBounds = true;
            }
        }

        // Exit exclusive fullscreen + unmaximize first so Windows DWM applies full monitor size on first toggle!
        if (GLFW.glfwGetWindowMonitor(handle) != 0L) {
            GLFW.glfwSetWindowMonitor(handle, 0L, mx[0], my[0], vidMode.width(), vidMode.height(), vidMode.refreshRate());
        }
        if (GLFW.glfwGetWindowAttrib(handle, GLFW.GLFW_MAXIMIZED) == GLFW.GLFW_TRUE) {
            GLFW.glfwRestoreWindow(handle);
        }

        GLFW.glfwSetWindowAttrib(handle, GLFW.GLFW_DECORATED, GLFW.GLFW_FALSE);
        GLFW.glfwSetWindowAttrib(handle, GLFW.GLFW_AUTO_ICONIFY, GLFW.GLFW_FALSE);
        GLFW.glfwSetWindowMonitor(handle, 0L, mx[0], my[0], vidMode.width(), vidMode.height(), vidMode.refreshRate());
        GLFW.glfwSetWindowPos(handle, mx[0], my[0]);
        GLFW.glfwSetWindowSize(handle, vidMode.width(), vidMode.height());

        // Re-apply on next client frame as well in case MC's internal fullscreen callback ran asynchronously
        final int targetX = mx[0];
        final int targetY = my[0];
        final int targetW = vidMode.width();
        final int targetH = vidMode.height();
        mc.execute(() -> {
            GLFW.glfwSetWindowAttrib(handle, GLFW.GLFW_DECORATED, GLFW.GLFW_FALSE);
            GLFW.glfwSetWindowPos(handle, targetX, targetY);
            GLFW.glfwSetWindowSize(handle, targetW, targetH);
            mc.resizeDisplay();
        });
    }

    private void restoreWindowed() {
        Window window = mc.getWindow();
        if (window == null) return;
        long handle = window.handle();
        if (handle == 0L) return;

        GLFW.glfwSetWindowAttrib(handle, GLFW.GLFW_DECORATED, GLFW.GLFW_TRUE);
        int rx = (restoreBounds.get() && hasSavedBounds) ? savedX : 120;
        int ry = (restoreBounds.get() && hasSavedBounds) ? savedY : 80;
        int rw = (restoreBounds.get() && hasSavedBounds) ? savedWidth : 1280;
        int rh = (restoreBounds.get() && hasSavedBounds) ? savedHeight : 720;
        GLFW.glfwSetWindowMonitor(handle, 0L, rx, ry, rw, rh, GLFW.GLFW_DONT_CARE);
        GLFW.glfwSetWindowPos(handle, rx, ry);
        GLFW.glfwSetWindowSize(handle, rw, rh);
        mc.execute(mc::resizeDisplay);
    }
}
