package com.ye.mod.modules.visual;

import com.ye.mod.core.Category;
import com.ye.mod.core.Module;
import com.ye.mod.core.setting.EnumSetting;
import net.minecraft.resources.Identifier;

/**
 * 6 Zero-Moiré Distinct Optical Post-Processing Filters:
 * - 梦幻柔光 (HDR Bright-Pass Bloom — natural hues, luminous highlights)
 * - 超清锐化 (CAS Texture Sharpening + Clarity)
 * - 原色漫描 (Anti-aliased Ink Outline keeping 100% original world colors)
 * - 极速光学 (Radial Motion & Edge Prism Dispersion — crystal sharp crosshair)
 * - 光学景深 (Center Focus + Creamy Peripheral Bokeh Blur)
 * - 黑白留红 (Moiré-free Silver Noir + Selective Vivid Scarlet)
 */
public class SinCityFilterModule extends Module {
    public enum FilterPreset {
        DREAM_BLOOM("梦幻柔光", "jojo", 0xFF38BDF8, 0xFFFDE047),
        CAS_SHARPEN("超清锐化", "crt", 0xFF4ADE80, 0xFF0EA5E9),
        NATURAL_INK("原色漫描", "borderlands", 0xFFF8FAFC, 0xFF334155),
        SPEED_OPTICS("极速光学", "cyber", 0xFF22D3EE, 0xFFF43F5E),
        CINEMA_BOKEH("光学景深", "cinema", 0xFFFBBF24, 0xFF1E293B),
        SIN_CITY("黑白留红", "sincity", 0xFFEF4444, 0xFF18181B);

        public final String label;
        public final Identifier effectId;
        public final int previewCol1;
        public final int previewCol2;

        FilterPreset(String label, String path, int previewCol1, int previewCol2) {
            this.label = label;
            this.effectId = Identifier.fromNamespaceAndPath("ye-mod", path);
            this.previewCol1 = previewCol1;
            this.previewCol2 = previewCol2;
        }
    }

    public final EnumSetting<FilterPreset> preset = addSetting(new EnumSetting<>(
            "preset", "光学预设", "梦幻柔光 / 超清锐化 / 原色漫描 / 极速光学 / 光学景深 / 黑白留红", FilterPreset.DREAM_BLOOM
    ));

    public SinCityFilterModule() {
        super("sin_city", "滤镜", "Filters", "6款无摩尔纹光学后期滤镜（柔光/锐化/原色漫描/极速光学/景深/留红）", Category.VISUAL);
    }

    public Identifier getActiveEffectId() {
        return preset.get().effectId;
    }
}
