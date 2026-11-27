package com.ye.mod.modules.render;

import com.ye.mod.core.Category;
import com.ye.mod.core.Module;
import com.ye.mod.core.setting.DoubleSetting;

public class SwingSpeedModule extends Module {
    public final DoubleSetting speed = addSetting(new DoubleSetting(
            "speed", "速率", "1.0=原版，越大越快，越小越慢", 1.6, 0.3, 3.5, 0.1
    ));

    public SwingSpeedModule() {
        super("swing_speed", "挥手速率", "Swing", "调节手臂挥动动画速度", Category.PLAYER_MODEL);
    }
}
