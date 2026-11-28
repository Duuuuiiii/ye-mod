package com.ye.mod.modules.visual;

import com.ye.mod.core.Category;
import com.ye.mod.core.KeyCombo;
import com.ye.mod.core.Module;
import com.ye.mod.core.setting.BoolSetting;
import com.ye.mod.core.setting.DoubleSetting;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/**
 * Perspective Zoom (放大镜):
 * Overrides isHoldOnlyKeybind() = true so pressing/holding the zoom hotkey NEVER toggles the module off on odd presses!
 */
public class ZoomModule extends Module {
    public final DoubleSetting zoomDivisor = addSetting(new DoubleSetting(
            "zoomDivisor", "基础倍率", "按下放大镜时的初始放大倍数", 3.8, 1.5, 12.0, 0.2
    ).functionGroup());

    public final DoubleSetting animSpeed = addSetting(new DoubleSetting(
            "animSpeed", "变焦速度", "视野推拉过渡速率", 18.0, 6.0, 32.0, 1.0
    ).functionGroup());

    public final BoolSetting scrollAdjust = addSetting(new BoolSetting(
            "scrollAdjust", "滚轮调节倍率", "变焦状态下通过滚轮无级缩放视野", true
    ).functionGroup());

    public final BoolSetting resetOnRelease = addSetting(new BoolSetting(
            "resetOnRelease", "松开重置倍率", "结束变焦后恢复初始倍率", true
    ).functionGroup());

    private double currentLogDiv = 0.0;
    private double dynamicScrollDiv = -1.0;
    private boolean wasHolding = false;
    private long lastNs = 0L;

    public double getActiveDivisor() {
        return Math.exp(currentLogDiv);
    }

    public ZoomModule() {
        super("zoom", "放大镜", "Zoom", "平滑变焦放大远景视野，支持无级倍率调节", Category.VISUAL);
        setEnabled(true, true);
        setKeybindSilent(GLFW.GLFW_KEY_C);
    }

    @Override
    public boolean isHoldOnlyKeybind() {
        return true;
    }

    public boolean isHoldingZoom() {
        if (!isEnabled()) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.screen != null || mc.getWindow() == null) return false;
        int key = getKeybind() > 0 ? getKeybind() : GLFW.GLFW_KEY_C;
        return KeyCombo.isComboDown(mc.getWindow().handle(), key);
    }

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
