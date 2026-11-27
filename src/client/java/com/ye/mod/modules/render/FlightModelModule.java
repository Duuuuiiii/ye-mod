package com.ye.mod.modules.render;

import com.ye.mod.core.Category;
import com.ye.mod.core.Module;
import com.ye.mod.core.setting.BoolSetting;
import com.ye.mod.core.setting.DoubleSetting;
import com.ye.mod.core.setting.EnumSetting;

/**
 * Rich 10-Mount Elytra Flight Replacement System (飞行坐骑):
 * - 末影龙 / 筋斗祥云 / 机甲推进翼 / 星轨法阵盘 / 仙侠御剑 / 魔法扫帚 / 巨型手里剑 / 折纸滑翔机 / 暗夜幻翼 / 超人踏空
 * - Full custom controls: Scale, Anim Speed, Trail Glow, Global Color Sync, Y/Z Offsets, Pitch Tilt!
 */
public class FlightModelModule extends Module {
    public enum FlightPreset {
        MINI_DRAGON("末影龙", false, false),
        CLOUD_NIMBUS("筋斗祥云", true, false),
        MECHA_JET("机甲推进翼", false, true),
        STAR_ASTROLABE("星轨法阵盘", false, true),
        SWORD_SURF("仙侠御剑", false, true),
        MAGIC_BROOM("魔法扫帚", true, false),
        GIANT_SHURIKEN("巨型手里剑", false, true),
        PAPER_GLIDER("折纸滑翔机", false, true),
        MINI_PHANTOM("暗夜幻翼", false, false),
        SUPERMAN("超人姿态", false, false);

        public final String label;
        public final boolean seatedPose;
        public final boolean standingSurfPose;

        FlightPreset(String label, boolean seatedPose, boolean standingSurfPose) {
            this.label = label;
            this.seatedPose = seatedPose;
            this.standingSurfPose = standingSurfPose;
        }
    }

    public final EnumSetting<FlightPreset> preset = addSetting(new EnumSetting<>(
            "preset", "坐骑款式", "10款高精度立体飞行坐骑与姿态", FlightPreset.CLOUD_NIMBUS
    ));

    public final BoolSetting syncGlobalColor = addSetting(new BoolSetting(
            "syncGlobalColor", "同步全局色彩", "坐骑能量核心、祥云金纹或喷气尾焰跟随全局色彩", false
    ));

    public final DoubleSetting modelScale = addSetting(new DoubleSetting(
            "modelScale", "坐骑大小", "整体缩放坐骑尺寸", 1.15, 0.5, 2.4, 0.05
    ));

    public final DoubleSetting wingSpeed = addSetting(new DoubleSetting(
            "wingSpeed", "动作速率", "控制振翅、法阵旋转或喷气脉动频率", 1.25, 0.2, 3.5, 0.05
    ));

    public final BoolSetting energyTrail = addSetting(new BoolSetting(
            "energyTrail", "流光尾焰", "渲染祥云雷纹、喷气等离子尾焰或御剑剑气环", true
    ));

    public final BoolSetting customMode = addSetting(new BoolSetting(
            "customMode", "高级自定义", "展开高低/前后位置微调、俯仰角与遮挡设置", false
    ));

    public final DoubleSetting offsetY = addSetting(new DoubleSetting(
            "offsetY", "高低微调", "上下微调坐骑与角色贴合高度", 0.0, -0.8, 0.8, 0.02
    ).visible(customMode::get));

    public final DoubleSetting offsetZ = addSetting(new DoubleSetting(
            "offsetZ", "前后微调", "前后微调坐骑重心位置", 0.0, -0.8, 0.8, 0.02
    ).visible(customMode::get));

    public final DoubleSetting tiltAngle = addSetting(new DoubleSetting(
            "tiltAngle", "俯仰倾角", "调节滑翔时坐骑整体迎风倾角", 0.0, -35.0, 35.0, 1.0
    ).visible(customMode::get));

    public final BoolSetting hideElytra = addSetting(new BoolSetting(
            "hideElytra", "隐藏原版鞘翅", "滑翔时自动隐藏背后的原版鞘翅", true
    ).visible(customMode::get));

    public final BoolSetting onlySelf = addSetting(new BoolSetting(
            "onlySelf", "仅自己生效", "只替换你自己的滑翔坐骑", true
    ).visible(customMode::get));

    public FlightModelModule() {
        super("flight_model", "飞行坐骑", "Flight Mount", "10款立体飞行坐骑（祥云/机甲翼/星轨盘/御剑/手里剑/纸飞机/末影龙等）", Category.PLAYER_MODEL);
        setEnabled(true, true);
    }
}
