package com.ye.mod.modules.render;

import com.ye.mod.core.Category;
import com.ye.mod.core.Module;
import com.ye.mod.core.setting.DoubleSetting;

/**
 * Customizes arm swing animation speed (#20).
 */
public class SwingSpeedModule extends Module {
    public final DoubleSetting speed = addSetting(new DoubleSetting(
            "speed", "挥手速度", "1.0=原版，越大挥动越快，越小越慢动作", 1.6, 0.3, 3.5, 0.1
    ));

    public SwingSpeedModule() {
        super("swing_speed", "挥手速度", "Swing Speed", "自定义第一/三人称手臂挥动速度", Category.RENDER);
    }
}
