package com.ye.mod.modules.visual;

import com.ye.mod.core.Category;
import com.ye.mod.core.Module;
import com.ye.mod.core.setting.DoubleSetting;
import net.minecraft.resources.Identifier;

/**
 * Temporal Frame-Accumulation GPU Motion Blur (动态模糊):
 * Uses 1.21.11 persistent PostChain framebuffers for zero-overhead smooth camera motion blur!
 */
public class MotionBlurModule extends Module {
    public final DoubleSetting strength = addSetting(new DoubleSetting(
            "strength", "模糊强度", "调节转动视角时的动态残影柔化程度", 0.55, 0.20, 0.85, 0.05
    ).functionGroup());

    private static final Identifier[] BLUR_LEVELS = {
            Identifier.fromNamespaceAndPath("ye-mod", "mblur_1"),
            Identifier.fromNamespaceAndPath("ye-mod", "mblur_2"),
            Identifier.fromNamespaceAndPath("ye-mod", "mblur_3"),
            Identifier.fromNamespaceAndPath("ye-mod", "mblur_4"),
            Identifier.fromNamespaceAndPath("ye-mod", "mblur_5")
    };

    public MotionBlurModule() {
        super("motion_blur", "动态模糊", "Motion Blur", "视角快速转向时呈现帧间平滑动态模糊效果", Category.VISUAL);
    }

    public Identifier getActiveEffectId() {
        double s = strength.get();
        int idx;
        if (s < 0.32) idx = 0;
        else if (s < 0.48) idx = 1;
        else if (s < 0.62) idx = 2;
        else if (s < 0.76) idx = 3;
        else idx = 4;
        return BLUR_LEVELS[idx];
    }
}
