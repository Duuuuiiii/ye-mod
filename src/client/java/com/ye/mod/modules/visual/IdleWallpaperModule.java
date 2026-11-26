package com.ye.mod.modules.visual;

import com.ye.mod.core.Category;
import com.ye.mod.core.Module;
import com.ye.mod.core.setting.DoubleSetting;
import net.minecraft.client.Minecraft;

/**
 * Idle Wallpaper Showcase (#10): after standing still for a few seconds,
 * smoothly orbits the camera 360 degrees around the player.
 */
public class IdleWallpaperModule extends Module {
    public final DoubleSetting idleSeconds = addSetting(new DoubleSetting(
            "idleSeconds", "触发秒数", "静止多少秒后进入环绕壁纸视角", 5.0, 2.0, 15.0, 1.0
    ));

    public final DoubleSetting distance = addSetting(new DoubleSetting(
            "distance", "环绕距离", "壁纸相机拉远距离", 4.0, 2.5, 7.0, 0.5
    ));

    private double lastX = Double.NaN, lastY = Double.NaN, lastZ = Double.NaN;
    private float lastYaw = Float.NaN, lastPitch = Float.NaN;
    private long lastActiveMs = System.currentTimeMillis();
    private long idleStartMs = 0L;
    private float blend = 0.0f;

    public IdleWallpaperModule() {
        super("idle_wallpaper", "挂机壁纸演出", "Idle Showcase", "站立不动几秒后自动拉远镜头缓慢 360° 环绕展示穿搭", Category.VISUAL);
    }

    @Override
    protected void onDisable() {
        blend = 0.0f;
        lastX = Double.NaN;
    }

    @Override
    public void onTick() {
        if (!isEnabled()) {
            blend = 0.0f;
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) {
            lastActiveMs = System.currentTimeMillis();
            blend = 0.0f;
            return;
        }

        double x = mc.player.getX(), y = mc.player.getY(), z = mc.player.getZ();
        float yaw = mc.player.getYRot(), pitch = mc.player.getXRot();
        boolean moved = Double.isNaN(lastX)
                || Math.abs(x - lastX) > 0.005 || Math.abs(y - lastY) > 0.005 || Math.abs(z - lastZ) > 0.005
                || Math.abs(yaw - lastYaw) > 0.2f || Math.abs(pitch - lastPitch) > 0.2f
                || mc.player.swingTime > 0;

        lastX = x; lastY = y; lastZ = z;
        lastYaw = yaw; lastPitch = pitch;

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

    public float getOrbitYaw() {
        Minecraft mc = Minecraft.getInstance();
        float base = mc.player != null ? mc.player.getYRot() : 0.0f;
        double sec = (System.currentTimeMillis() - idleStartMs) / 1000.0;
        return base + (float) (sec * 16.0);
    }

    public float getOrbitDistance() {
        blend += (1.0f - blend) * 0.06f;
        return distance.get().floatValue() * blend;
    }
}
