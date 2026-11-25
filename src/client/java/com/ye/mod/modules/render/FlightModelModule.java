package com.ye.mod.modules.render;

import com.ye.mod.core.Category;
import com.ye.mod.core.Module;
import com.ye.mod.core.setting.BoolSetting;
import com.ye.mod.core.setting.DoubleSetting;
import com.ye.mod.core.setting.EnumSetting;

/**
 * Client-side visual-only replacement for elytra flight posture and mount models.
 * Does not alter actual flight physics, speed, or server hitboxes.
 */
public class FlightModelModule extends Module {
    public enum FlightPreset {
        MINI_DRAGON("骑乘小末影龙"),
        UPRIGHT("直立御风"),
        SWORD_SURF("御剑飞行"),
        MINI_PHANTOM("骑乘幻翼"),
        SUPERMAN("超人俯冲");

        public final String label;
        FlightPreset(String label) { this.label = label; }
    }

    public final EnumSetting<FlightPreset> preset = addSetting(new EnumSetting<>(
            "preset", "飞行模型预设", "MINI_DRAGON=骑小末影龙, UPRIGHT=直立飞行, SWORD_SURF=御剑, MINI_PHANTOM=骑幻翼, SUPERMAN=超人",
            FlightPreset.MINI_DRAGON
    ));

    public final DoubleSetting modelScale = addSetting(new DoubleSetting(
            "modelScale", "坐骑大小", "调整末影龙/幻翼/飞剑模型的大小", 1.25, 0.6, 2.2, 0.05
    ));

    public final DoubleSetting wingSpeed = addSetting(new DoubleSetting(
            "wingSpeed", "翅膀扇速", "调整末影龙/幻翼翅膀扇动速度", 1.3, 0.3, 3.5, 0.1
    ));

    public final BoolSetting hideElytra = addSetting(new BoolSetting(
            "hideElytra", "隐藏原版鞘翅", "使用自定义飞行模型时隐藏背上的原版鞘翅", true
    ));

    public final BoolSetting onlySelf = addSetting(new BoolSetting(
            "onlySelf", "仅本地玩家", "只替换你自己的鞘翅飞行视觉模型", true
    ));

    public FlightModelModule() {
        super("flight_model", "飞行模型替换", "Flight Model", "仅本地渲染：鞘翅飞行时替换为骑小末影龙、直立、御剑等模型，实际飞行不变", Category.RENDER);
    }
}
