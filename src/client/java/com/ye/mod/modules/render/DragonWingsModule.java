package com.ye.mod.modules.render;

import com.ye.mod.core.Category;
import com.ye.mod.core.Module;
import com.ye.mod.core.setting.BoolSetting;
import com.ye.mod.core.setting.DoubleSetting;
import com.ye.mod.core.setting.EnumSetting;

/**
 * High-resolution sculpted 3D wings with 6 distinct styles and custom hide options.
 */
public class DragonWingsModule extends Module {
    public enum WingStyle {
        DRAGON("末影龙翼", "textures/wings/dragon.png"),
        ANGEL("圣洁天使翼", "textures/wings/angel.png"),
        BAT("暗夜蝠翼", "textures/wings/bat.png"),
        EVIL("深渊恶魔翼", "textures/wings/evil.png"),
        FIRE("烈焰火翼", "textures/wings/fire.png"),
        METALLIC("秘银机甲翼", "textures/wings/metallic.png"),
        PARROT("七彩羽翼", "textures/wings/parrot.png");

        public final String label;
        public final String texPath;
        WingStyle(String label, String texPath) {
            this.label = label;
            this.texPath = texPath;
        }
    }

    public enum WingColor {
        STYLE_DEFAULT(0xFFFFFFFF),
        OBSIDIAN_PURPLE(0xFFA855F7),
        BLOOD_RED(0xFFEF4444),
        ICE_BLUE(0xFF38BDF8),
        ROYAL_GOLD(0xFFFBBF24),
        CHROMA(0xFFFFFFFF);

        public final int tintArgb;
        WingColor(int tintArgb) { this.tintArgb = tintArgb; }
    }

    public final EnumSetting<WingStyle> wingStyle = addSetting(new EnumSetting<>(
            "wingStyle", "翅膀样式", "7种多关节立体翅膀样式", WingStyle.DRAGON
    ));

    public final EnumSetting<WingColor> wingColor = addSetting(new EnumSetting<>(
            "wingColor", "染色风格", "原色或自定义染色/七彩流光", WingColor.STYLE_DEFAULT
    ));

    public final DoubleSetting scale = addSetting(new DoubleSetting(
            "scale", "翅膀大小", "缩放翅膀整体尺寸", 1.0, 0.5, 2.0, 0.05
    ));

    public final DoubleSetting flapSpeed = addSetting(new DoubleSetting(
            "flapSpeed", "扇动速度", "控制翅膀扇动频率", 1.0, 0.3, 2.5, 0.1
    ));

    public final BoolSetting hideWithElytra = addSetting(new BoolSetting(
            "hideWithElytra", "滑翔隐藏", "鞘翅飞行时隐藏翅膀", false
    ));

    public final BoolSetting hideElytra = addSetting(new BoolSetting(
            "hideElytra", "不渲染鞘翅", "隐藏原版鞘翅", true
    ));

    public final BoolSetting hideCape = addSetting(new BoolSetting(
            "hideCape", "不渲染披风", "隐藏披风", true
    ));

    public final BoolSetting hideArmor = addSetting(new BoolSetting(
            "hideArmor", "不渲染盔甲", "隐藏玩家盔甲", false
    ));

    public final BoolSetting onlySelf = addSetting(new BoolSetting(
            "onlySelf", "仅自己", "只给自己渲染", true
    ));

    public DragonWingsModule() {
        super("dragon_wings", "炫酷翅膀", "Wings", "高精度纹理立体翅膀，6种样式，支持隐藏鞘翅/披风/盔甲", Category.RENDER);
    }

    public int getTintColor(long timeMs) {
        WingColor wc = wingColor.get();
        if (wc == WingColor.CHROMA) {
            float hue = ((timeMs % 4000L) / 4000.0f);
            int rgb = java.awt.Color.HSBtoRGB(hue, 0.72f, 1.0f) & 0x00FFFFFF;
            return 0xFF000000 | rgb;
        }
        return wc.tintArgb;
    }
}
