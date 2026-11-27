package com.ye.mod.modules.render;

import com.ye.mod.core.Category;
import com.ye.mod.core.Module;
import com.ye.mod.core.setting.BoolSetting;
import com.ye.mod.core.setting.DoubleSetting;
import com.ye.mod.core.setting.EnumSetting;

/**
 * Client-side visual replacement for elytra flight posture and mount models.
 */
public class FlightModelModule extends Module {
    public enum FlightPreset {
        MINI_DRAGON("末影龙"),
        MAGIC_BROOM("扫帚"),
        SWORD_SURF("御剑"),
        MINI_PHANTOM("幻翼"),
        UPRIGHT("踏空"),
        SUPERMAN("超人");

        public final String label;
        FlightPreset(String label) { this.label = label; }
    }

    public final EnumSetting<FlightPreset> preset = addSetting(new EnumSetting<>(
            "preset", "飞行坐骑", "末影龙 / 扫帚 / 御剑 / 幻翼 / 踏空 / 超人", FlightPreset.MINI_DRAGON
    ));

    public final DoubleSetting modelScale = addSetting(new DoubleSetting(
            "modelScale", "大小", "调整坐骑尺寸", 1.20, 0.6, 2.2, 0.05
    ));

    public final DoubleSetting wingSpeed = addSetting(new DoubleSetting(
            "wingSpeed", "扇速", "调整坐骑翅膀动作频率", 1.3, 0.3, 3.5, 0.1
    ));

    public final BoolSetting hideElytra = addSetting(new BoolSetting(
            "hideElytra", "隐藏鞘翅", "飞行时隐藏原版鞘翅", true
    ));

    public final BoolSetting onlySelf = addSetting(new BoolSetting(
            "onlySelf", "仅自己", "只替换本地玩家飞行模型", true
    ));

    public FlightModelModule() {
        super("flight_model", "飞行坐骑", "Flight", "鞘翅滑翔时替换为末影龙、扫帚、御剑等坐骑", Category.PLAYER_MODEL);
        setEnabled(true, true);
    }
}
