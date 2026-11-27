package com.ye.mod.modules.render;

import com.ye.mod.core.Category;
import com.ye.mod.core.Module;
import com.ye.mod.core.setting.BoolSetting;
import com.ye.mod.core.setting.DoubleSetting;
import com.ye.mod.core.setting.EnumSetting;

/**
 * Player scale & big-head mode — works seamlessly even during flight!
 */
public class BodyScaleModule extends Module {
    public enum ScalePreset {
        MINI("迷你 0.5x"),
        GIANT("巨人 1.85x"),
        BIG_HEAD("大头"),
        CUSTOM("自定义");

        public final String label;
        ScalePreset(String label) { this.label = label; }
    }

    public final EnumSetting<ScalePreset> preset = addSetting(new EnumSetting<>(
            "preset", "预设", "迷你 / 巨人 / 大头 / 自定义", ScalePreset.MINI
    ));

    public final DoubleSetting bodyScale = addSetting(new DoubleSetting(
            "bodyScale", "身体倍率", "自定义身体缩放", 1.0, 0.35, 2.5, 0.05
    ).visible(() -> preset.get() == ScalePreset.CUSTOM));

    public final DoubleSetting headScale = addSetting(new DoubleSetting(
            "headScale", "头部倍率", "自定义头部缩放", 1.0, 0.6, 2.4, 0.05
    ).visible(() -> preset.get() == ScalePreset.CUSTOM));

    public final BoolSetting onlySelf = addSetting(new BoolSetting(
            "onlySelf", "仅自己", "只对本地玩家生效", true
    ));

    public BodyScaleModule() {
        super("body_scale", "体型", "Scale", "自由调节玩家身体与头部比例", Category.PLAYER_MODEL);
    }

    public float getEffectiveBodyScale() {
        return switch (preset.get()) {
            case MINI -> 0.52f;
            case GIANT -> 1.85f;
            case BIG_HEAD -> 1.0f;
            case CUSTOM -> bodyScale.get().floatValue();
        };
    }

    public float getEffectiveHeadScale() {
        return switch (preset.get()) {
            case BIG_HEAD -> 1.65f;
            case CUSTOM -> headScale.get().floatValue();
            default -> 1.0f;
        };
    }
}
