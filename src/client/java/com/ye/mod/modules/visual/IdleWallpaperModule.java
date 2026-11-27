package com.ye.mod.modules.visual;

import com.ye.mod.core.Category;
import com.ye.mod.core.Module;
import com.ye.mod.core.setting.BoolSetting;
import com.ye.mod.core.setting.DoubleSetting;
import com.ye.mod.core.setting.EnumSetting;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/**
 * Cinematic Idle Showcase with HUD/Hand auto-hide & alternating out-of-body camera shots.
 * Restores instantly on ANY player input, mouse movement, or rotation!
 */
public class IdleWallpaperModule extends Module {
    public enum CameraStyle {
        AUTO_MIX("穿插切换"),
        SOUL_DRIFT("灵魂出窍"),
        ORBIT_360("环绕运镜"),
        HERO_LOW("仰角特写"),
        SPIRAL_TOP("高空俯旋");

        public final String label;
        CameraStyle(String label) { this.label = label; }
    }

    public final EnumSetting<CameraStyle> cameraStyle = addSetting(new EnumSetting<>(
            "cameraStyle", "运镜模式", "自动穿插多种电影运镜或固定风格", CameraStyle.AUTO_MIX
    ));

    public final DoubleSetting idleSeconds = addSetting(new DoubleSetting(
            "idleSeconds", "触发秒数", "无操作多少秒后进入挂机演出", 5.0, 2.0, 20.0, 1.0
    ));

    public final DoubleSetting distance = addSetting(new DoubleSetting(
            "distance", "镜头距离", "第三人称运镜拉远基准距离", 4.2, 2.2, 8.5, 0.2
    ));

    public final BoolSetting hideHud = addSetting(new BoolSetting(
            "hideHud", "隐藏HUD", "挂机时隐藏界面 UI，动一下立即恢复", true
    ));

    public final BoolSetting hideHand = addSetting(new BoolSetting(
            "hideHand", "隐藏手部", "挂机时隐藏第一人称手部模型", true
    ));

    private double lastX = Double.NaN, lastY = Double.NaN, lastZ = Double.NaN;
    private float lastYaw = Float.NaN, lastPitch = Float.NaN;
    private double lastCursorX = Double.NaN, lastCursorY = Double.NaN;
    private long lastActiveMs = System.currentTimeMillis();
    private long idleStartMs = 0L;
    private float blend = 0.0f;

    public IdleWallpaperModule() {
        super("idle_wallpaper", "挂机演出", "Idle Show", "静止自动进入灵魂出窍穿插运镜并隐藏UI，动一下即刻恢复", Category.VISUAL);
    }

    @Override
    protected void onDisable() {
        blend = 0.0f;
        idleStartMs = 0L;
        lastX = Double.NaN;
    }

    @Override
    public void onTick() {
        if (!isEnabled()) {
            blend = 0.0f;
            idleStartMs = 0L;
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null || mc.getWindow() == null) {
            lastActiveMs = System.currentTimeMillis();
            idleStartMs = 0L;
            blend = 0.0f;
            return;
        }

        long handle = mc.getWindow().handle();
        double[] cx = new double[1], cy = new double[1];
        GLFW.glfwGetCursorPos(handle, cx, cy);

        double x = mc.player.getX(), y = mc.player.getY(), z = mc.player.getZ();
        float yaw = mc.player.getYRot(), pitch = mc.player.getXRot();
        boolean anyKeyOrMouse = mc.options.keyUp.isDown() || mc.options.keyDown.isDown()
                || mc.options.keyLeft.isDown() || mc.options.keyRight.isDown()
                || mc.options.keyJump.isDown() || mc.options.keyShift.isDown()
                || mc.options.keyAttack.isDown() || mc.options.keyUse.isDown()
                || GLFW.glfwGetMouseButton(handle, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS
                || GLFW.glfwGetMouseButton(handle, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;

        boolean moved = Double.isNaN(lastX)
                || anyKeyOrMouse
                || Math.abs(x - lastX) > 0.004 || Math.abs(y - lastY) > 0.004 || Math.abs(z - lastZ) > 0.004
                || Math.abs(yaw - lastYaw) > 0.15f || Math.abs(pitch - lastPitch) > 0.15f
                || (!Double.isNaN(lastCursorX) && (Math.hypot(cx[0] - lastCursorX, cy[0] - lastCursorY) > 1.5))
                || mc.player.swingTime > 0;

        lastX = x; lastY = y; lastZ = z;
        lastYaw = yaw; lastPitch = pitch;
        lastCursorX = cx[0]; lastCursorY = cy[0];

        long now = System.currentTimeMillis();
        if (moved) {
            lastActiveMs = now;
            idleStartMs = 0L;
            blend = 0.0f;
        } else if (now - lastActiveMs >= Math.round(idleSeconds.get() * 1000.0)) {
            if (idleStartMs == 0L) idleStartMs = now;
        }
    }

    public boolean isIdleActive() {
        return isEnabled() && idleStartMs > 0L;
    }

    public boolean shouldHideHud() {
        return isIdleActive() && hideHud.get();
    }

    public boolean shouldHideHand() {
        return isIdleActive() && hideHand.get();
    }

    public record ShotPose(float yaw, float pitch, float zoomDist, float verticalOffset, float sideOffset) {}

    public ShotPose computeShotPose(float baseYaw) {
        blend += (1.0f - blend) * 0.055f;
        double sec = (System.currentTimeMillis() - idleStartMs) / 1000.0;
        float baseDist = distance.get().floatValue() * blend;

        CameraStyle mode = cameraStyle.get();
        if (mode == CameraStyle.AUTO_MIX) {
            // Smoothly cycle through 4 cinematic shots every 7 seconds
            int stage = ((int) (sec / 7.0)) % 4;
            mode = switch (stage) {
                case 0 -> CameraStyle.SOUL_DRIFT;
                case 1 -> CameraStyle.HERO_LOW;
                case 2 -> CameraStyle.SPIRAL_TOP;
                default -> CameraStyle.ORBIT_360;
            };
        }

        return switch (mode) {
            case SOUL_DRIFT -> new ShotPose(
                    baseYaw + (float) (sec * 14.0 + Math.sin(sec * 0.6) * 22.0),
                    (float) (16.0 + Math.sin(sec * 0.7) * 14.0),
                    baseDist * (float) (1.25 + 0.30 * Math.sin(sec * 0.5)),
                    (float) (0.55 + 0.45 * Math.sin(sec * 0.6)),
                    (float) (Math.cos(sec * 0.5) * 0.65) * blend
            );
            case HERO_LOW -> new ShotPose(
                    baseYaw + 155.0f + (float) (Math.sin(sec * 0.55) * 38.0),
                    -14.0f,
                    baseDist * 0.78f,
                    -0.42f * blend,
                    0.35f * blend
            );
            case SPIRAL_TOP -> new ShotPose(
                    baseYaw + (float) (sec * 24.0),
                    48.0f,
                    baseDist * 1.45f,
                    0.85f * blend,
                    0.0f
            );
            default -> new ShotPose(
                    baseYaw + (float) (sec * 18.0),
                    12.0f,
                    baseDist,
                    0.10f * blend,
                    0.0f
            );
        };
    }
}
