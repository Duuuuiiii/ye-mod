package com.ye.mod.modules.render;

import com.ye.mod.core.Category;
import com.ye.mod.core.Module;
import com.ye.mod.core.setting.BoolSetting;
import com.ye.mod.core.setting.DoubleSetting;
import com.ye.mod.core.setting.EnumSetting;

/**
 * JoJo-style translucent glowing 3D Stand Buddy (#3) floating behind the player,
 * syncing movement and triggering Ora-Ora punch flurries on attack.
 */
public class StandBuddyModule extends Module {
    public enum StandStyle {
        STAR_PLATINUM("紫晶替身", 0xAA9333EA),
        THE_WORLD("黄金世界", 0xAAFACC15),
        SILVER_CHARIOT("银色战车", 0xAA38BDF8),
        ABYSS_SHADOW("深渊暗影", 0xAA1E1B2E);

        public final String label;
        public final int tintArgb;
        StandStyle(String label, int tintArgb) {
            this.label = label;
            this.tintArgb = tintArgb;
        }
    }

    public final EnumSetting<StandStyle> style = addSetting(new EnumSetting<>(
            "style", "替身配色", "紫晶替身 / 黄金世界 / 银色战车 / 深渊暗影", StandStyle.STAR_PLATINUM
    ));

    public final DoubleSetting scale = addSetting(new DoubleSetting(
            "scale", "替身大小", "缩放替身虚影体型", 1.15, 0.7, 1.8, 0.05
    ));

    public final BoolSetting onlySelf = addSetting(new BoolSetting(
            "onlySelf", "仅自己", "只给自己召唤替身", true
    ));

    public StandBuddyModule() {
        super("stand_buddy", "替身使者", "Stand Buddy", "JoJo 风格半透明发光 3D 替身虚影，攻击时同步出拳连打", Category.RENDER);
    }
}
