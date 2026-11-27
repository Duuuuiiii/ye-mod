package com.ye.mod.modules.visual;

import com.ye.mod.core.Category;
import com.ye.mod.core.Module;
import com.ye.mod.core.setting.DoubleSetting;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/**
 * Smooth Optifine-style perspective zoom (#33), activated by holding 'C' key.
 */
public class ZoomModule extends Module {
    public final DoubleSetting zoomDivisor = addSetting(new DoubleSetting(
            "zoomDivisor", "放大倍数", "FOV 缩小倍率（越大看得越远）", 3.5, 1.5, 8.0, 0.5
    ));

    private float smoothFactor = 1.0f;
    private long lastNs = 0L;

    public ZoomModule() {
        super("zoom", "放大镜", "Zoom", "按住 C 键平滑拉近远景视野", Category.VISUAL);
    }

    public float adjustFov(float baseFov) {
        if (!isEnabled()) {
            smoothFactor = 1.0f;
            return baseFov;
        }
        Minecraft mc = Minecraft.getInstance();
        boolean zooming = mc.screen == null && mc.getWindow() != null
                && GLFW.glfwGetKey(mc.getWindow().handle(), GLFW.GLFW_KEY_C) == GLFW.GLFW_PRESS;

        float target = zooming ? (1.0f / zoomDivisor.get().floatValue()) : 1.0f;
        long now = System.nanoTime();
        float dt = lastNs == 0L ? 0.016f : Math.min(0.05f, (now - lastNs) / 1_000_000_000.0f);
        lastNs = now;
        smoothFactor += (target - smoothFactor) * Math.min(1.0f, dt * 14.0f);
        return baseFov * smoothFactor;
    }
}
