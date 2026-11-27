package com.ye.mod.modules.visual;

import com.ye.mod.core.Category;
import com.ye.mod.core.Module;
import com.ye.mod.core.setting.BoolSetting;
import com.ye.mod.core.setting.DoubleSetting;
import com.ye.mod.core.setting.KeybindSetting;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

/**
 * Advanced Camera Tweaks Module (摄像机 — richer & smoother than Meteor's Camera Tweaks!):
 * - Camera Clip Through Blocks (第三人称穿墙无遮挡)
 * - Smooth Exponential-Spring Third-Person Distance (0.5m ~ 50.0m) + Mouse Wheel Zoom in 3rd Person!
 * - Custom XYZ Over-the-Shoulder Camera Framing (越肩视角偏移)
 * - Independent 360° FreeLook Orbit (自由视角按键：环顾四周不改变玩家移动朝向!)
 * - Hurt-Cam Shake Suppression & Smooth Perspective Transition!
 */
public class CameraTweaksModule extends Module {
    public final BoolSetting clipBlocks = addSetting(new BoolSetting(
            "clipBlocks", "镜头穿墙无阻挡", "第三人称视角忽略墙壁与树叶碰撞，镜头不再贴脸弹回", true
    ).functionGroup());

    public final DoubleSetting thirdPersonDist = addSetting(new DoubleSetting(
            "thirdPersonDist", "第三人称视距", "设置第三人称基础镜头距离 (0.5 ~ 50m)", 4.5, 0.5, 50.0, 0.5
    ).functionGroup());

    public final BoolSetting scrollDistance = addSetting(new BoolSetting(
            "scrollDistance", "滚轮推拉视距", "第三人称下通过滚轮自由调节镜头远近", true
    ).functionGroup());

    public final KeybindSetting scrollModKey = addSetting(new KeybindSetting(
            "scrollModKey", "视距滚轮辅助键", "配合滚轮调节第三人称视距，避免误切快捷栏", GLFW.GLFW_KEY_LEFT_ALT
    ).visible(scrollDistance::get).functionGroup());

    public final DoubleSetting shoulderOffsetX = addSetting(new DoubleSetting(
            "shoulderOffsetX", "左右越肩偏移", "横向偏移镜头构建越肩视角", 0.0, -2.5, 2.5, 0.05
    ).functionGroup());

    public final DoubleSetting shoulderOffsetY = addSetting(new DoubleSetting(
            "shoulderOffsetY", "上下高度偏移", "纵向抬高或降低第三人称镜头高度", 0.0, -1.5, 2.5, 0.05
    ).functionGroup());

    public final KeybindSetting freeLookKey = addSetting(new KeybindSetting(
            "freeLookKey", "自由环视按键", "开启360°独立环视且不改变角色移动朝向", -1
    ).functionGroup());

    public final BoolSetting smoothTransition = addSetting(new BoolSetting(
            "smoothTransition", "F5平滑推拉动画", "切换第一/第三人称时镜头丝滑推拉过渡", true
    ).functionGroup());

    public final BoolSetting noHurtShake = addSetting(new BoolSetting(
            "noHurtShake", "受伤镜头不晃动", "取消受击时的屏幕倾斜抖动", false
    ).functionGroup());

    private float currentDist = 0.0f;
    private long lastFrameNs = 0L;

    private boolean freeLooking = false;
    private float freeYaw = 0.0f;
    private float freePitch = 0.0f;
    private CameraType preFreeLookCam = null;

    public CameraTweaksModule() {
        super("camera_tweaks", "摄像机", "Camera", "第三人称穿墙、50米无级滚轮视距、越肩偏移、F5丝滑推拉与360°独立自由视角", Category.VISUAL);
    }

    @Override
    protected void onDisable() {
        stopFreeLook();
        currentDist = 0.0f;
    }

    @Override
    public void onTick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options == null) {
            stopFreeLook();
            return;
        }
        boolean wantFree = isEnabled() && freeLookKey.isDown();
        if (wantFree && !freeLooking) {
            freeLooking = true;
            freeYaw = mc.player.getYRot();
            freePitch = mc.player.getXRot();
            preFreeLookCam = mc.options.getCameraType();
            if (preFreeLookCam.isFirstPerson()) {
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            }
        } else if (!wantFree && freeLooking) {
            stopFreeLook();
        }
    }

    private void stopFreeLook() {
        if (!freeLooking) return;
        freeLooking = false;
        Minecraft mc = Minecraft.getInstance();
        if (preFreeLookCam != null && mc != null && mc.options != null) {
            mc.options.setCameraType(preFreeLookCam);
        }
        preFreeLookCam = null;
    }

    public boolean isFreeLookActive() {
        return isEnabled() && freeLooking;
    }

    public void onMouseTurnFreeLook(double deltaYaw, double deltaPitch) {
        freeYaw += (float) (deltaYaw * 0.15);
        freePitch = Mth.clamp(freePitch + (float) (deltaPitch * 0.15), -90.0f, 90.0f);
    }

    public float getFreeYaw() { return freeYaw; }
    public float getFreePitch() { return freePitch; }

    public boolean onMouseScroll(double scrollY) {
        if (!isEnabled() || !scrollDistance.get() || scrollY == 0.0) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.screen != null || mc.options == null) return false;
        if (mc.options.getCameraType().isFirstPerson() && !freeLooking) return false;

        if (scrollModKey.get() > 0 && !scrollModKey.isDown()) {
            return false;
        }
        double next = thirdPersonDist.get() - scrollY * Math.max(0.4, thirdPersonDist.get() * 0.12);
        thirdPersonDist.set(Math.max(0.5, Math.min(50.0, next)));
        return true;
    }

    public float computeSmoothedDistance(boolean thirdPerson) {
        long now = System.nanoTime();
        float dt = lastFrameNs == 0L ? 0.016f : Math.min(0.05f, Math.max(0.0005f, (now - lastFrameNs) / 1_000_000_000.0f));
        lastFrameNs = now;

        float target = thirdPerson ? thirdPersonDist.get().floatValue() : 0.0f;
        if (!smoothTransition.get()) {
            currentDist = target;
            return target;
        }
        currentDist += (target - currentDist) * (1.0f - (float) Math.exp(-16.0f * dt));
        return currentDist;
    }
}
