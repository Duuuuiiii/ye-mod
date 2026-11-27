package com.ye.mod.modules.visual;

import com.ye.mod.core.Category;
import com.ye.mod.core.Module;
import com.ye.mod.core.setting.BoolSetting;

/**
 * Locks dynamic FOV changes (sprint, speed, bow) and disables camera/screen distortion.
 */
public class NoFovModule extends Module {
    public final BoolSetting lockSprintFov = addSetting(new BoolSetting(
            "lockSprintFov", "固定冲刺/速度 FOV", "取消冲刺、速度药水、飞行等导致的视场角缩放", true
    ));

    public final BoolSetting lockBowFov = addSetting(new BoolSetting(
            "lockBowFov", "固定拉弓 FOV", "取消拉弓蓄力时的视场角拉近", false
    ));

    public final BoolSetting noHurtCam = addSetting(new BoolSetting(
            "noHurtCam", "取消受伤视角抖动", "屏蔽受到伤害时的屏幕倾斜抖动", true
    ));

    public final BoolSetting noViewBobbing = addSetting(new BoolSetting(
            "noViewBobbing", "取消行走晃动", "屏蔽移动时的视角上下晃动", false
    ));

    public final BoolSetting noDistortion = addSetting(new BoolSetting(
            "noDistortion", "取消屏幕扭曲效果", "强力屏蔽反胃、传送门等扭曲变形效果（无视原版选项 Bug）", true
    ));

    public NoFovModule() {
        super("no_fov", "固定视角", "No FOV", "锁定动态视场角并取消屏幕抖动扭曲", Category.UTILITY);
    }
}
