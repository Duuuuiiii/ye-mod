package com.ye.mod.modules.visual;

import com.ye.mod.core.Category;
import com.ye.mod.core.Module;
import net.minecraft.resources.Identifier;

/**
 * Sin City / Monochrome Manga Post-Processing Filter (#12).
 */
public class SinCityFilterModule extends Module {
    public static final Identifier EFFECT_ID = Identifier.fromNamespaceAndPath("ye-mod", "sincity");

    public SinCityFilterModule() {
        super("sin_city", "黑白漫画滤镜", "Sin City", "真实 GLSL 黑白高对比度漫画世界滤镜（保留彩色的 HUD 与准星）", Category.VISUAL);
    }
}
