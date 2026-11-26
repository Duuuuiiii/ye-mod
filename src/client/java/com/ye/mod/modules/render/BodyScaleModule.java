package com.ye.mod.modules.render;

import com.ye.mod.core.Category;
import com.ye.mod.core.Module;
import com.ye.mod.core.setting.BoolSetting;
import com.ye.mod.core.setting.DoubleSetting;
import com.ye.mod.core.setting.EnumSetting;

/**
 * Player scale & big-head mode (#11 Mini, #12 Giant, #13 Big Head).
 */
public class BodyScaleModule extends Module {
    public enum ScalePreset {
        CUSTOM("自定义"),
        MINI("迷你小人 0.5x"),
        GIANT("巨人模式 2.0x"),
        BIG_HEAD("Q版大头");

        public final String label;
        ScalePreset(String label) { this.label = label; }
    }

    public final EnumSetting<ScalePreset> preset = addSetting(new EnumSetting<>(
            "preset", "体型预设", "迷你 / 巨人 / Q版大头 / 自定义", ScalePreset.MINI
    ));

    public final DoubleSetting bodyScale = addSetting(new DoubleSetting(
            "bodyScale", "身体大小", "自定义整体缩放倍率", 1.0, 0.35, 2.5, 0.05
    ));

    public final DoubleSetting headScale = addSetting(new DoubleSetting(
            "headScale", "头部大小", "单独缩放头部倍率", 1.0, 0.6, 2.4, 0.05
    ));

    public final BoolSetting onlySelf = addSetting(new BoolSetting(
            "onlySelf", "仅自己", "只对本地玩家生效", true
    ));

    public BodyScaleModule() {
        super("body_scale", "体型整活", "Body Scale", "迷你玩家、巨人模式、Q版大头", Category.RENDER);
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
