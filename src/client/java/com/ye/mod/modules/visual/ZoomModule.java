package com.ye.mod.modules.visual;

import com.ye.mod.core.Category;
import com.ye.mod.core.Module;
import com.ye.mod.core.setting.BoolSetting;
import com.ye.mod.core.setting.DoubleSetting;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/**
 * Silky Logarithmic Perspective Zoom (放大镜):
 * - Exponential log-space interpolation so rapid key tapping NEVER stutters or snaps!
 * - Hold Zoom key ('C' by default, or module keybind if bound) + Scroll Wheel to smoothly zoom in/out up to 25x!
 * - Optional auto-reset magnification on release!
 */
public class ZoomModule extends Module {
    public final DoubleSetting zoomDivisor = addSetting(new DoubleSetting(
            "zoomDivisor", "基础倍率", "按下放大镜时的初始放大倍数", 3.8, 1.5, 12.0, 0.2
    ));

    public final DoubleSetting animSpeed = addSetting(new DoubleSetting(
            "animSpeed", "丝滑速度", "视野推拉阻尼过渡速度", 18.0, 6.0, 32.0, 1.0
    ));

    public final BoolSetting scrollAdjust = addSetting(new BoolSetting(
            "scrollAdjust", "滚轮调节倍率", "按住放大键时滑动滚轮可无级放大/缩小视野", true
    ));

    public final BoolSetting resetOnRelease = addSetting(new BoolSetting(
            "resetOnRelease", "松开重置倍率", "松开放大键后下次再按恢复为基础倍率", true
    ));

    private double currentLogDiv = 0.0; // 0.0 = log(1.0) = unzoomed
    private double dynamicScrollDiv = -1.0;
    private boolean wasHolding = false;
    private long lastNs = 0L;

    public ZoomModule() {
        super("zoom", "放大镜", "Zoom", "按住 C 键丝滑无断层变焦，支持滚轮无级放大缩小", Category.VISUAL);
        setEnabled(true, true);
    }

    public boolean isHoldingZoom() {
        if (!isEnabled()) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.screen != null || mc.getWindow() == null) return false;
        int key = getKeybind() > 0 ? getKeybind() : GLFW.GLFW_KEY_C;
        return GLFW.glfwGetKey(mc.getWindow().handle(), key) == GLFW.GLFW_PRESS;
    }

    /**
     * Intercepts mouse wheel while holding Zoom key! Returns true if consumed.
     */
    public boolean onMouseScroll(double scrollY) {
        if (!scrollAdjust.get() || scrollY == 0.0 || !isHoldingZoom()) return false;
        if (dynamicScrollDiv < 1.0) {
            dynamicScrollDiv = zoomDivisor.get();
        }
        double factor = Math.pow(1.18, scrollY);
        dynamicScrollDiv = Math.max(1.15, Math.min(28.0, dynamicScrollDiv * factor));
        return true;
    }

    public float adjustFov(float baseFov) {
        long now = System.nanoTime();
        double dt = lastNs == 0L ? 0.016 : Math.min(0.05, Math.max(0.0005, (now - lastNs) / 1_000_000_000.0));
        lastNs = now;

        boolean holding = isHoldingZoom();
        if (holding && !wasHolding) {
            if (dynamicScrollDiv < 1.0 || resetOnRelease.get()) {
                dynamicScrollDiv = zoomDivisor.get();
            }
        } else if (!holding && wasHolding && resetOnRelease.get()) {
            dynamicScrollDiv = zoomDivisor.get();
        }
        wasHolding = holding;

        if (!isEnabled() && Math.abs(currentLogDiv) < 0.001) {
            currentLogDiv = 0.0;
            return baseFov;
        }

        double activeDiv = holding ? (dynamicScrollDiv >= 1.0 ? dynamicScrollDiv : zoomDivisor.get()) : 1.0;
        double targetLog = Math.log(Math.max(1.0, activeDiv));

        // Exponential damping in log-FOV space: C1-continuous velocity even under rapid spam-clicking!
        double alpha = 1.0 - Math.exp(-animSpeed.get() * dt);
        currentLogDiv += (targetLog - currentLogDiv) * alpha;
        if (!holding && Math.abs(currentLogDiv) < 0.0008) {
            currentLogDiv = 0.0;
            return baseFov;
        }

        float zoomedFov = (float) (baseFov / Math.exp(currentLogDiv));
        return Math.max(2.5f, Math.min(170.0f, zoomedFov));
    }
}
