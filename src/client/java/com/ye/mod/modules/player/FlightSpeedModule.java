package com.ye.mod.modules.player;

import com.ye.mod.core.Category;
import com.ye.mod.core.ConfigManager;
import com.ye.mod.core.Module;
import com.ye.mod.core.setting.BoolSetting;
import com.ye.mod.core.setting.DoubleSetting;
import com.ye.mod.core.setting.KeybindSetting;
import com.ye.mod.ui.YeQuickMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Locale;

/**
 * Universal Flight Speed Controller (飞行速度):
 * - Controls Creative Flight, Spectator Flight, Elytra Horizontal Glide, AND Freecam / Out-of-Body (灵魂出窍 — Meteor & Tweakeroo compatible!)
 * - Quick Preset Hotkeys: e.g. Key '7' -> 0.01 speed, Key '0' -> 0.60 speed, Key '9' -> 1.50 speed!
 * - Hold Key + Scroll Wheel to adjust flight speed smoothly in real-time!
 * - Flight Deceleration / Anti-Drift (飞行减速：松开方向键立即刹停不滑行)!
 */
public class FlightSpeedModule extends Module {
    public final DoubleSetting currentSpeed = addSetting(new DoubleSetting(
            "currentSpeed", "当前飞行速度", "基础飞行速度数值 (原版默认 0.05)", 0.15, 0.005, 5.00, 0.01
    ).functionGroup());

    public final BoolSetting applyCreative = addSetting(new BoolSetting(
            "applyCreative", "接管创造与旁观飞行", "控制创造模式与旁观者模式的飞行移速", true
    ).functionGroup());

    public final BoolSetting applyFreecam = addSetting(new BoolSetting(
            "applyFreecam", "同步灵魂出窍速度", "同步控制 Meteor / Tweakeroo 灵魂出窍镜头移速", true
    ).functionGroup());

    public final BoolSetting applyElytra = addSetting(new BoolSetting(
            "applyElytra", "接管鞘翅水平巡航", "鞘翅滑翔时按方向键按设定速度平滑巡航", false
    ).functionGroup());

    public final BoolSetting antiDrift = addSetting(new BoolSetting(
            "antiDrift", "飞行减速急停", "松开方向键与升降键时消除飞行滑行惯性", true
    ).functionGroup());

    public final DoubleSetting decelFactor = addSetting(new DoubleSetting(
            "decelFactor", "减速刹停力度", "1.0 为松手瞬间零惯性急停，数值越小滑行越柔和", 0.92, 0.20, 1.00, 0.04
    ).visible(antiDrift::get).functionGroup());

    public final KeybindSetting holdScrollKey = addSetting(new KeybindSetting(
            "holdScrollKey", "滚轮调速辅助键", "配合鼠标滚轮无级增减飞行速度", GLFW.GLFW_KEY_LEFT_CONTROL
    ).functionGroup());

    // Quick Preset Slots (e.g. 7 -> 0.01, 0 -> 0.60, 8 -> 0.05)
    public final KeybindSetting presetKey1 = addSetting(new KeybindSetting(
            "presetKey1", "微调档快捷键", "切换至精细微移速度档位", -1
    ).functionGroup());
    public final DoubleSetting presetVal1 = addSetting(new DoubleSetting(
            "presetVal1", "微调档速度", "微调档对应的飞行速度值", 0.01, 0.002, 2.00, 0.01
    ).functionGroup());

    public final KeybindSetting presetKey2 = addSetting(new KeybindSetting(
            "presetKey2", "标准档快捷键", "切换至原版标准速度档位", -1
    ).functionGroup());
    public final DoubleSetting presetVal2 = addSetting(new DoubleSetting(
            "presetVal2", "标准档速度", "标准档对应的飞行速度值", 0.05, 0.005, 2.00, 0.01
    ).functionGroup());

    public final KeybindSetting presetKey3 = addSetting(new KeybindSetting(
            "presetKey3", "高速档快捷键", "切换至高速巡航速度档位", GLFW.GLFW_KEY_0
    ).functionGroup());
    public final DoubleSetting presetVal3 = addSetting(new DoubleSetting(
            "presetVal3", "高速档速度", "高速档对应的飞行速度值", 0.60, 0.05, 5.00, 0.05
    ).functionGroup());

    private float savedVanillaFlySpeed = 0.05f;

    public FlightSpeedModule() {
        super("flight_speed", "飞行速度", "Flight Speed", "统一控制创造、旁观、灵魂出窍与鞘翅飞行速度，支持多档预设、无级调速与减速制动", Category.UTILITY);
    }

    @Override
    protected void onEnable() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            savedVanillaFlySpeed = mc.player.getAbilities().getFlyingSpeed();
        }
    }

    @Override
    protected void onDisable() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.getAbilities().setFlyingSpeed(0.05f);
        }
    }

    public boolean onMouseScroll(double scrollY) {
        if (!isEnabled() || scrollY == 0.0) return false;
        if (holdScrollKey.get() <= 0 || !holdScrollKey.isDown()) return false;

        double cur = currentSpeed.get();
        double delta = scrollY * Math.max(0.005, cur * 0.16);
        double next = Math.max(0.005, Math.min(5.00, Math.round((cur + delta) * 1000.0) / 1000.0));
        currentSpeed.set(next);
        syncExternalFreecamSpeed(next);
        YeQuickMenu.showToast(String.format(Locale.ROOT, "飞行速度: %.3f", next), ConfigManager.getAccentColor());
        return true;
    }

    @Override
    public void onTick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        if (presetKey1.consumePress()) applyPreset(presetVal1.get());
        if (presetKey2.consumePress()) applyPreset(presetVal2.get());
        if (presetKey3.consumePress()) applyPreset(presetVal3.get());

        float spd = currentSpeed.get().floatValue();

        if (applyCreative.get()) {
            mc.player.getAbilities().setFlyingSpeed(spd);
        }

        if (applyFreecam.get()) {
            syncExternalFreecamSpeed(spd);
            // Also directly scale detached Freecam camera entity velocity if present
            if (mc.getCameraEntity() != null && mc.getCameraEntity() != mc.player) {
                float yawRad = (float) Math.toRadians(mc.getCameraEntity().getYRot());
                double fwd = (mc.options.keyUp.isDown() ? 1 : 0) - (mc.options.keyDown.isDown() ? 1 : 0);
                double str = (mc.options.keyLeft.isDown() ? 1 : 0) - (mc.options.keyRight.isDown() ? 1 : 0);
                double vert = (mc.options.keyJump.isDown() ? 1 : 0) - (mc.options.keyShift.isDown() ? 1 : 0);
                if (fwd != 0 || str != 0 || vert != 0) {
                    double bpt = spd * 10.0;
                    double vx = (-Math.sin(yawRad) * fwd + Math.cos(yawRad) * str) * bpt;
                    double vz = (Math.cos(yawRad) * fwd + Math.sin(yawRad) * str) * bpt;
                    mc.getCameraEntity().setDeltaMovement(vx, vert * bpt, vz);
                }
            }
        }

        boolean isFlyingNow = mc.player.getAbilities().flying || mc.player.isSpectator();
        if (antiDrift.get() && isFlyingNow) {
            boolean anyHoriz = mc.options.keyUp.isDown() || mc.options.keyDown.isDown()
                    || mc.options.keyLeft.isDown() || mc.options.keyRight.isDown();
            boolean anyVert = mc.options.keyJump.isDown() || mc.options.keyShift.isDown();
            Vec3 vel = mc.player.getDeltaMovement();
            double keep = 1.0 - decelFactor.get();
            double nx = anyHoriz ? vel.x : vel.x * keep;
            double nz = anyHoriz ? vel.z : vel.z * keep;
            double ny = anyVert ? vel.y : vel.y * keep;
            mc.player.setDeltaMovement(nx, ny, nz);
        }

        if (applyElytra.get() && mc.player.isFallFlying()) {
            float yawRad = (float) Math.toRadians(mc.player.getYRot());
            double forward = (mc.options.keyUp.isDown() ? 1 : 0) - (mc.options.keyDown.isDown() ? 1 : 0);
            double strafe = (mc.options.keyLeft.isDown() ? 1 : 0) - (mc.options.keyRight.isDown() ? 1 : 0);
            if (forward != 0 || strafe != 0) {
                double len = Math.hypot(forward, strafe);
                forward /= len;
                strafe /= len;
                double blocksPerTick = spd * 10.0;
                double vx = (-Math.sin(yawRad) * forward + Math.cos(yawRad) * strafe) * blocksPerTick;
                double vz = (Math.cos(yawRad) * forward + Math.sin(yawRad) * strafe) * blocksPerTick;
                Vec3 cur = mc.player.getDeltaMovement();
                mc.player.setDeltaMovement(vx, cur.y, vz);
            } else if (antiDrift.get()) {
                Vec3 cur = mc.player.getDeltaMovement();
                double keep = 1.0 - decelFactor.get();
                mc.player.setDeltaMovement(cur.x * keep, cur.y, cur.z * keep);
            }
        }
    }

    private void applyPreset(double targetSpd) {
        currentSpeed.set(targetSpd);
        syncExternalFreecamSpeed(targetSpd);
        YeQuickMenu.playClick();
        YeQuickMenu.showToast(String.format(Locale.ROOT, "切换飞行速度 → %.3f", targetSpd), ConfigManager.getAccentColor());
    }

    /**
     * Best-effort reflection bridge to sync speed with Tweakeroo (`freeCameraPlayerMovementSpeed`) and Meteor (`Freecam.speed`)!
     */
    private static void syncExternalFreecamSpeed(double yeSpeed) {
        // 1) Tweakeroo Configs.Generic.FREE_CAMERA_PLAYER_MOVEMENT_SPEED (blocks/tick scale ~ yeSpeed * 10)
        try {
            Class<?> cfgGeneric = Class.forName("fi.dy.masa.tweakeroo.config.Configs$Generic");
            Field f = cfgGeneric.getField("FREE_CAMERA_PLAYER_MOVEMENT_SPEED");
            Object opt = f.get(null);
            if (opt != null) {
                Method setDouble = opt.getClass().getMethod("setDoubleValue", double.class);
                setDouble.invoke(opt, Math.max(0.02, yeSpeed * 10.0));
            }
        } catch (Throwable ignored) {}

        // 2) Meteor Client Freecam speed setting
        try {
            Class<?> modulesCls = Class.forName("meteordevelopment.meteorclient.systems.modules.Modules");
            Object mgr = modulesCls.getMethod("get").invoke(null);
            Class<?> freecamCls = Class.forName("meteordevelopment.meteorclient.systems.modules.render.Freecam");
            Object fc = modulesCls.getMethod("get", Class.class).invoke(mgr, freecamCls);
            if (fc != null) {
                Field spdField = freecamCls.getDeclaredField("speed");
                spdField.setAccessible(true);
                Object setting = spdField.get(fc);
                if (setting != null) {
                    setting.getClass().getMethod("set", Object.class).invoke(setting, yeSpeed * 10.0);
                }
            }
        } catch (Throwable ignored) {}

        // 3) Standalone Freecam mod (net.xolt.freecam)
        try {
            Class<?> modCfg = Class.forName("net.xolt.freecam.config.ModConfig");
            Object inst = modCfg.getField("INSTANCE").get(null);
            if (inst != null) {
                Object movement = inst.getClass().getField("movement").get(inst);
                if (movement != null) {
                    double v = Math.max(0.1, yeSpeed * 10.0);
                    movement.getClass().getField("horizontalSpeed").setDouble(movement, v);
                    movement.getClass().getField("verticalSpeed").setDouble(movement, v);
                }
            }
        } catch (Throwable ignored) {}
    }
}
