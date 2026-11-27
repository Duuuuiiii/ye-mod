package com.ye.mod.modules.visual;

import com.ye.mod.core.Category;
import com.ye.mod.core.Module;
import com.ye.mod.core.setting.DoubleSetting;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Smooth animated top/bottom widescreen cinema letterbox bars (#34).
 */
public class CinematicBarsModule extends Module {
    public final DoubleSetting barPercent = addSetting(new DoubleSetting(
            "barPercent", "黑边高度%", "上下电影黑边占屏幕高度比例", 10.0, 4.0, 20.0, 1.0
    ));

    private float anim = 0.0f;

    public CinematicBarsModule() {
        super("cinematic_bars", "电影黑边", "Cinema Bars", "上下宽银幕构图黑边", Category.VISUAL);
    }

    public void render(GuiGraphics g) {
        anim += ((isEnabled() ? 1.0f : 0.0f) - anim) * 0.14f;
        if (anim < 0.01f) return;
        int sh = g.guiHeight();
        int sw = g.guiWidth();
        int h = Math.round(sh * (barPercent.get().floatValue() / 100.0f) * anim);
        if (h <= 0) return;
        g.fill(0, 0, sw, h, 0xFF000000);
        g.fill(0, sh - h, sw, sh, 0xFF000000);
    }
}
