package com.ye.mod.modules.render;

import com.ye.mod.core.Category;
import com.ye.mod.core.Module;
import com.ye.mod.core.setting.BoolSetting;
import com.ye.mod.core.setting.DoubleSetting;
import com.ye.mod.core.setting.EnumSetting;

/**
 * Renders animated 3D dragon wings on the player's back with customizable colors and flap speed.
 */
public class DragonWingsModule extends Module {
    public enum WingColor {
        OBSIDIAN_BLACK(0xFF1A1B26, 0xFF3B2D54),
        ENDER_PURPLE(0xFF2D1B4E, 0xFFA855F7),
        BLOOD_RED(0xFF3A0E12, 0xFFEF4444),
        ICE_BLUE(0xFF0F2942, 0xFF38BDF8),
        ROYAL_GOLD(0xFF42300A, 0xFFFBBF24),
        CHROMA(0xFF1E1E2E, 0xFFFFFFFF);

        public final int boneArgb;
        public final int membraneArgb;

        WingColor(int boneArgb, int membraneArgb) {
            this.boneArgb = boneArgb;
            this.membraneArgb = membraneArgb;
        }
    }

    public final EnumSetting<WingColor> wingColor = addSetting(new EnumSetting<>(
            "wingColor", "翅膀颜色", "选择龙翅膀的翼骨与翼膜配色（CHROMA 为动态七彩流光）", WingColor.ENDER_PURPLE
    ));

    public final DoubleSetting scale = addSetting(new DoubleSetting(
            "scale", "翅膀大小", "缩放背部龙翅膀的整体尺寸", 1.0, 0.5, 2.0, 0.05
    ));

    public final DoubleSetting flapSpeed = addSetting(new DoubleSetting(
            "flapSpeed", "扇动速度", "控制龙翅膀的扇动频率", 1.0, 0.3, 2.5, 0.1
    ));

    public final BoolSetting hideWithElytra = addSetting(new BoolSetting(
            "hideWithElytra", "滑翔时隐藏龙翅", "鞘翅飞行时暂时隐藏龙翅膀", false
    ));

    public final BoolSetting onlySelf = addSetting(new BoolSetting(
            "onlySelf", "仅本地玩家", "只给自己渲染龙翅膀，不给其他玩家渲染", true
    ));

    public DragonWingsModule() {
        super("dragon_wings", "动态龙翅膀", "Dragon Wings", "在玩家背部渲染会随动作动态扇动的 3D 龙翅膀，支持多种配色", Category.RENDER);
    }

    public int getMembraneColor(long timeMs) {
        WingColor wc = wingColor.get();
        if (wc == WingColor.CHROMA) {
            float hue = ((timeMs % 4000L) / 4000.0f);
            int rgb = java.awt.Color.HSBtoRGB(hue, 0.78f, 0.96f) & 0x00FFFFFF;
            return 0xCC000000 | rgb;
        }
        return (0xCC << 24) | (wc.membraneArgb & 0x00FFFFFF);
    }

    public int getBoneColor(long timeMs) {
        WingColor wc = wingColor.get();
        if (wc == WingColor.CHROMA) {
            float hue = (((timeMs + 600L) % 4000L) / 4000.0f);
            int rgb = java.awt.Color.HSBtoRGB(hue, 0.65f, 0.45f) & 0x00FFFFFF;
            return 0xFF000000 | rgb;
        }
        return wc.boneArgb;
    }
}
