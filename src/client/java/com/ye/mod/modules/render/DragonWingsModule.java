package com.ye.mod.modules.render;

import com.ye.mod.core.Category;
import com.ye.mod.core.Module;
import com.ye.mod.core.setting.BoolSetting;
import com.ye.mod.core.setting.DoubleSetting;
import com.ye.mod.core.setting.EnumSetting;

/**
 * Renders intricate multi-jointed 3D wings angled cleanly behind the player's back.
 */
public class DragonWingsModule extends Module {
    public enum WingStyle {
        ENDER_DRAGON("末影龙翼"),
        SERAPH("炽天使羽翼"),
        DEMON("恶魔蝠翼"),
        CYBER_MECHA("赛博机甲翼");

        public final String label;
        WingStyle(String label) { this.label = label; }
    }

    public enum WingColor {
        OBSIDIAN_BLACK(0xFF14151E, 0xFF3E2C66),
        ENDER_PURPLE(0xFF281548, 0xFFA855F7),
        BLOOD_RED(0xFF360B10, 0xFFEF4444),
        ICE_BLUE(0xFF0D2842, 0xFF38BDF8),
        ROYAL_GOLD(0xFF443008, 0xFFFBBF24),
        CHROMA(0xFF1A1B26, 0xFFFFFFFF);

        public final int boneArgb;
        public final int membraneArgb;

        WingColor(int boneArgb, int membraneArgb) {
            this.boneArgb = boneArgb;
            this.membraneArgb = membraneArgb;
        }
    }

    public final EnumSetting<WingStyle> wingStyle = addSetting(new EnumSetting<>(
            "wingStyle", "翅膀样式", "ENDER_DRAGON=末影龙翼, SERAPH=炽天使羽翼, DEMON=恶魔蝠翼, CYBER_MECHA=赛博机甲翼",
            WingStyle.ENDER_DRAGON
    ));

    public final EnumSetting<WingColor> wingColor = addSetting(new EnumSetting<>(
            "wingColor", "翅膀配色", "选择翼骨与翼膜配色（CHROMA 为动态七彩流光）", WingColor.ENDER_PURPLE
    ));

    public final DoubleSetting scale = addSetting(new DoubleSetting(
            "scale", "翅膀大小", "缩放背部翅膀的整体尺寸", 1.05, 0.5, 2.0, 0.05
    ));

    public final DoubleSetting flapSpeed = addSetting(new DoubleSetting(
            "flapSpeed", "扇动速度", "控制翅膀的扇动频率", 1.0, 0.3, 2.5, 0.1
    ));

    public final BoolSetting hideWithElytra = addSetting(new BoolSetting(
            "hideWithElytra", "滑翔时隐藏", "鞘翅飞行时隐藏龙翅膀", false
    ));

    public final BoolSetting hideElytra = addSetting(new BoolSetting(
            "hideElytra", "不渲染鞘翅", "开启龙翼时隐藏原版鞘翅", true
    ));

    public final BoolSetting hideCape = addSetting(new BoolSetting(
            "hideCape", "不渲染披风", "开启龙翼时隐藏披风", true
    ));

    public final BoolSetting hideArmor = addSetting(new BoolSetting(
            "hideArmor", "不渲染盔甲", "开启龙翼时隐藏玩家盔甲", false
    ));

    public final BoolSetting onlySelf = addSetting(new BoolSetting(
            "onlySelf", "仅本地玩家", "只给自己渲染翅膀，不给其他玩家渲染", true
    ));

    public DragonWingsModule() {
        super("dragon_wings", "动态龙翅膀", "Dragon Wings", "背部渲染多关节精致 3D 翅膀，向后收束防穿模，支持 4 种样式与流光配色", Category.RENDER);
    }

    public int getMembraneColor(long timeMs) {
        WingColor wc = wingColor.get();
        if (wc == WingColor.CHROMA) {
            float hue = ((timeMs % 4000L) / 4000.0f);
            int rgb = java.awt.Color.HSBtoRGB(hue, 0.78f, 0.96f) & 0x00FFFFFF;
            return 0xD4000000 | rgb;
        }
        return (0xD4 << 24) | (wc.membraneArgb & 0x00FFFFFF);
    }

    public int getBoneColor(long timeMs) {
        WingColor wc = wingColor.get();
        if (wc == WingColor.CHROMA) {
            float hue = (((timeMs + 600L) % 4000L) / 4000.0f);
            int rgb = java.awt.Color.HSBtoRGB(hue, 0.65f, 0.42f) & 0x00FFFFFF;
            return 0xFF000000 | rgb;
        }
        return wc.boneArgb;
    }
}
