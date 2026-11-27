package com.ye.mod.modules.render;

import com.ye.mod.core.Category;
import com.ye.mod.core.Module;
import com.ye.mod.core.setting.BoolSetting;
import com.ye.mod.core.setting.DoubleSetting;
import com.ye.mod.core.setting.EnumSetting;

/**
 * Dual-Category 3D Wings:
 * 1) PVP_DRAGON: Iconic Hypixel/Lunar/Badlion 2-bone curved Dragon Wings (Canelex port)
 * 2) AVIAN_FANTASY: 4-joint articulated Fantastic Wings (7 textures)
 */
public class DragonWingsModule extends Module {
    public enum WingCategory {
        PVP_DRAGON("经典龙翼"),
        AVIAN_FANTASY("四节羽翼");

        public final String label;
        WingCategory(String label) { this.label = label; }
    }

    public enum WingStyle {
        DRAGON("黑曜龙翼", "textures/wings/dragon.png"),
        ANGEL("圣白羽翼", "textures/wings/angel.png"),
        BAT("暗夜蝠翼", "textures/wings/bat.png"),
        EVIL("深渊魔翼", "textures/wings/evil.png"),
        FIRE("烈焰火翼", "textures/wings/fire.png"),
        METALLIC("机甲银翼", "textures/wings/metallic.png"),
        PARROT("虹彩羽翼", "textures/wings/parrot.png");

        public final String label;
        public final String texPath;
        WingStyle(String label, String texPath) {
            this.label = label;
            this.texPath = texPath;
        }
    }

    public enum WingColor {
        DEFAULT("原色", 0xFFFFFFFF),
        PURPLE("紫", 0xFFA855F7),
        RED("红", 0xFFEF4444),
        CYAN("青", 0xFF38BDF8),
        GOLD("金", 0xFFFBBF24),
        WHITE("白", 0xFFF8FAFC),
        CHROMA("流光", 0xFFFFFFFF);

        public final String label;
        public final int tintArgb;
        WingColor(String label, int tintArgb) {
            this.label = label;
            this.tintArgb = tintArgb;
        }
    }

    public final EnumSetting<WingCategory> wingCategory = addSetting(new EnumSetting<>(
            "wingCategory", "翅膀大类", "经典龙翼(Lunar/Badlion) / 四节羽翼", WingCategory.PVP_DRAGON
    ));

    public final EnumSetting<WingStyle> wingStyle = addSetting(new EnumSetting<>(
            "wingStyle", "羽翼材质", "四节羽翼专属材质样式", WingStyle.DRAGON
    ).visible(() -> wingCategory.get() == WingCategory.AVIAN_FANTASY));

    public final EnumSetting<WingColor> wingColor = addSetting(new EnumSetting<>(
            "wingColor", "染色", "原色或自定义染色/流光", WingColor.DEFAULT
    ));

    public final DoubleSetting scale = addSetting(new DoubleSetting(
            "scale", "大小", "缩放翅膀整体尺寸", 1.0, 0.5, 2.0, 0.05
    ));

    public final DoubleSetting flapSpeed = addSetting(new DoubleSetting(
            "flapSpeed", "扇动速度", "控制翅膀扇动频率", 1.0, 0.3, 2.5, 0.1
    ));

    public final BoolSetting customMode = addSetting(new BoolSetting(
            "customMode", "高级自定义", "展开遮挡与细节显示选项", false
    ));

    public final BoolSetting hideWithElytra = addSetting(new BoolSetting(
            "hideWithElytra", "滑翔隐藏", "鞘翅飞行时隐藏翅膀", false
    ).visible(customMode::get));

    public final BoolSetting hideElytra = addSetting(new BoolSetting(
            "hideElytra", "隐藏鞘翅", "隐藏原版鞘翅模型", true
    ).visible(customMode::get));

    public final BoolSetting hideCape = addSetting(new BoolSetting(
            "hideCape", "隐藏披风", "隐藏玩家披风", true
    ).visible(customMode::get));

    public final BoolSetting hideArmor = addSetting(new BoolSetting(
            "hideArmor", "隐藏盔甲", "隐藏胸甲防穿模", false
    ).visible(customMode::get));

    public final BoolSetting onlySelf = addSetting(new BoolSetting(
            "onlySelf", "仅自己", "只给自己渲染", true
    ).visible(customMode::get));

    public DragonWingsModule() {
        super("dragon_wings", "翅膀", "Wings", "双大类立体翅膀（Lunar经典龙翼 + 四节奇幻羽翼）", Category.PLAYER_MODEL);
        setEnabled(true, true);
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
