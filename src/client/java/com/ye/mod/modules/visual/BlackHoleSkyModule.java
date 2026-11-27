package com.ye.mod.modules.visual;

import com.ye.mod.core.Category;
import com.ye.mod.core.Module;
import com.ye.mod.core.setting.DoubleSetting;
import com.ye.mod.core.setting.EnumSetting;

/**
 * 512x512 Gargantua Sky Black Hole rendered via YeWorldFxRenderer with smooth
 * relativistic accretion disk, pitch-black event horizon, and Einstein lensing halo.
 */
public class BlackHoleSkyModule extends Module {
    public enum Theme {
        GARGANTUA("黑金奇点", 0xFFFFF3C4, 0xFFF59E0B),
        VOID_PURPLE("虚空紫洞", 0xFFF5D0FE, 0xFFA855F7),
        BLOOD_ECLIPSE("猩红日蚀", 0xFFFECACA, 0xFFEF4444),
        QUASAR_CYAN("类星冰蓝", 0xFFE0F2FE, 0xFF38BDF8);

        public final String label;
        public final int innerCol;
        public final int midCol;

        public int getMidCol() { return midCol; }

        Theme(String label, int innerCol, int midCol) {
            this.label = label;
            this.innerCol = innerCol;
            this.midCol = midCol;
        }
    }

    public final EnumSetting<Theme> theme = addSetting(new EnumSetting<>(
            "theme", "配色", "黑金奇点 / 虚空紫洞 / 猩红日蚀 / 类星冰蓝", Theme.GARGANTUA
    ));

    public final DoubleSetting size = addSetting(new DoubleSetting(
            "size", "尺寸", "黑洞与吸积盘尺寸", 1.15, 0.5, 2.5, 0.05
    ));

    public final DoubleSetting spinSpeed = addSetting(new DoubleSetting(
            "spinSpeed", "转速", "吸积盘等离子流旋转速度", 1.0, 0.2, 3.0, 0.1
    ));

    public final DoubleSetting elevation = addSetting(new DoubleSetting(
            "elevation", "仰角高度", "黑洞悬挂高度", 66.0, 35.0, 110.0, 2.0
    ));

    public BlackHoleSkyModule() {
        super("black_hole_sky", "黑洞", "Black Hole", "512×512 高清相对论吸积盘与引力透镜黑洞", Category.VISUAL);
    }
}
