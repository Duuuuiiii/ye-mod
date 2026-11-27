package com.ye.mod.modules.render;

import com.ye.mod.core.Category;
import com.ye.mod.core.Module;
import com.ye.mod.core.setting.BoolSetting;
import com.ye.mod.core.setting.DoubleSetting;
import com.ye.mod.core.setting.EnumSetting;

/**
 * JoJo 3D Stand Module featuring 7 authentic high-detail 3D Stand models + Player Avatar Stand.
 */
public class StandBuddyModule extends Module {
    public enum StandModelType {
        STAR_PLATINUM("白金之星", "geo/star_platinum.geo.json", "textures/stands/star_platinum.png", 0xFFA855F7),
        THE_WORLD("世界", "geo/the_world.geo.json", "textures/stands/the_world.png", 0xFFFACC15),
        SILVER_CHARIOT("银色战车", "geo/silver_chariot.geo.json", "textures/stands/silver_chariot.png", 0xFF94A3B8),
        CRAZY_DIAMOND("疯狂钻石", "geo/crazy_diamond.geo.json", "textures/stands/crazy_diamond.png", 0xFFF472B6),
        GOLD_EXPERIENCE("黄金体验", "geo/gold_experience.geo.json", "textures/stands/gold_experience.png", 0xFFF59E0B),
        HIEROPHANT_GREEN("法皇之绿", "geo/hierophant_green.geo.json", "textures/stands/hierophant_green.png", 0xFF10B981),
        MAGICIANS_RED("魔术师之红", "geo/magicians_red.geo.json", "textures/stands/magicians_red.png", 0xFFEF4444),
        PLAYER_AVATAR("玩家虚影", "", "", 0xFF38BDF8);

        public final String label;
        public final String geoPath;
        public final String texPath;
        public final int accentColor;

        StandModelType(String label, String geoPath, String texPath, int accentColor) {
            this.label = label;
            this.geoPath = geoPath;
            this.texPath = texPath;
            this.accentColor = accentColor;
        }
    }

    public final EnumSetting<StandModelType> modelType = addSetting(new EnumSetting<>(
            "modelType", "替身模型", "7款真实JoJo替身3D模型 + 玩家虚影", StandModelType.STAR_PLATINUM
    ));

    public final DoubleSetting scale = addSetting(new DoubleSetting(
            "scale", "大小", "替身模型尺寸", 1.05, 0.6, 1.8, 0.05
    ));

    public final DoubleSetting alpha = addSetting(new DoubleSetting(
            "alpha", "不透明度", "替身实体灵体透明度", 0.88, 0.3, 1.0, 0.05
    ));

    public final BoolSetting syncGlobalColor = addSetting(new BoolSetting(
            "syncGlobalColor", "同步全局色彩", "让替身染上全局色彩的灵体光晕", false
    ));

    public final BoolSetting customMode = addSetting(new BoolSetting(
            "customMode", "高级自定义", "调节替身悬浮偏移与归属", false
    ));

    public final DoubleSetting offsetX = addSetting(new DoubleSetting(
            "offsetX", "左右偏移", "替身相对玩家左右位置", -0.75, -1.8, 1.8, 0.05
    ).visible(customMode::get));

    public final DoubleSetting offsetY = addSetting(new DoubleSetting(
            "offsetY", "上下偏移", "替身相对玩家高度位置", -0.28, -1.0, 0.8, 0.05
    ).visible(customMode::get));

    public final DoubleSetting offsetZ = addSetting(new DoubleSetting(
            "offsetZ", "前后偏移", "替身相对玩家前后距离", 0.72, -1.2, 1.8, 0.05
    ).visible(customMode::get));

    public final BoolSetting onlySelf = addSetting(new BoolSetting(
            "onlySelf", "仅自己", "只给自己召唤替身", true
    ).visible(customMode::get));

    public StandBuddyModule() {
        super("stand_buddy", "替身", "Stand", "真实 JoJo 替身使者高精度 3D 模型与连打演出", Category.PLAYER_MODEL);
    }
}
