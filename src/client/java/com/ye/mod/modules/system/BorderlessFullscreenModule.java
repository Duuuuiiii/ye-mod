package com.ye.mod.modules.system;

import com.mojang.blaze3d.platform.Window;
import com.ye.mod.core.Category;
import com.ye.mod.core.Module;
import com.ye.mod.core.setting.BoolSetting;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWVidMode;

/**
 * Switches the game window into true borderless windowed fullscreen.
 */
public class BorderlessFullscreenModule extends Module {
    public final BoolSetting restoreBounds = addSetting(new BoolSetting(
            "restoreBounds", "关闭时恢复窗口大小", "退出无边框全屏时恢复之前的窗口位置与尺寸", true
    ));

    private int savedX = 100;
    private int savedY = 100;
    private int savedWidth = 1280;
    private int savedHeight = 720;
    private boolean hasSavedBounds = false;

    public BorderlessFullscreenModule() {
        super("borderless_fullscreen", "无边框全屏", "Borderless Fullscreen", "将游戏切换为无边框全屏窗口模式，切屏无黑屏延迟", Category.SYSTEM);
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

        if (window.isFullscreen()) {
            window.toggleFullScreen();
        }

        int[] wx = new int[1], wy = new int[1], ww = new int[1], wh = new int[1];
        GLFW.glfwGetWindowPos(handle, wx, wy);
        GLFW.glfwGetWindowSize(handle, ww, wh);
        if (ww[0] > 0 && wh[0] > 0) {
            savedX = wx[0];
            savedY = wy[0];
            savedWidth = ww[0];
            savedHeight = wh[0];
            hasSavedBounds = true;
        }

        long monitor = GLFW.glfwGetWindowMonitor(handle);
        if (monitor == 0L) {
            monitor = GLFW.glfwGetPrimaryMonitor();
        }
        if (monitor == 0L) return;

        GLFWVidMode vidMode = GLFW.glfwGetVideoMode(monitor);
        if (vidMode == null) return;

        int[] mx = new int[1], my = new int[1];
        GLFW.glfwGetMonitorPos(monitor, mx, my);

        GLFW.glfwSetWindowAttrib(handle, GLFW.GLFW_DECORATED, GLFW.GLFW_FALSE);
        GLFW.glfwSetWindowPos(handle, mx[0], my[0]);
        GLFW.glfwSetWindowSize(handle, vidMode.width(), vidMode.height());
    }

    private void restoreWindowed() {
        Window window = mc.getWindow();
        if (window == null) return;
        long handle = window.handle();
        if (handle == 0L) return;

        GLFW.glfwSetWindowAttrib(handle, GLFW.GLFW_DECORATED, GLFW.GLFW_TRUE);
        if (restoreBounds.get() && hasSavedBounds) {
            GLFW.glfwSetWindowPos(handle, savedX, savedY);
            GLFW.glfwSetWindowSize(handle, savedWidth, savedHeight);
        }
    }
}
