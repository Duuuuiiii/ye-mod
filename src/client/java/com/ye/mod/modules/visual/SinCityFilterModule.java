package com.ye.mod.modules.visual;

import com.ye.mod.core.Category;
import com.ye.mod.core.Module;
import com.ye.mod.core.setting.EnumSetting;
import net.minecraft.resources.Identifier;

/**
 * Multi-preset GLSL Stylized Post-Processing Filter:
 * - 无主之地漫染 (Borderlands Cel-Shading + Ink Outline)
 * - JoJo撞色 (JoJo Split-Toning + Manga Ink)
 * - 罪恶留红 (Sin City Selective Red + Manga Screentone)
 * - 赛博霓虹 (Cyberpunk Chromatic Aberration + Neon Glow)
 * - 复古胶片 (35mm Teal-Orange Cinema Film + Grain)
 * - CRT扫描线 (Retro Super-Secret Phosphor Scanlines)
 */
public class SinCityFilterModule extends Module {
    public enum FilterPreset {
        BORDERLANDS("无主漫染", "borderlands", 0xFFF59E0B, 0xFF0F172A),
        JOJO("JoJo撞色", "jojo", 0xFFD946EF, 0xFFFACC15),
        SIN_CITY("罪恶留红", "sincity", 0xFFEF4444, 0xFF18181B),
        CYBER_NEON("赛博霓虹", "cyber", 0xFF22D3EE, 0xFFA855F7),
        CINEMA_FILM("复古胶片", "cinema", 0xFFD97706, 0xFF0F2933),
        RETRO_CRT("CRT扫描", "crt", 0xFF4ADE80, 0xFF09140E);

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
            "preset", "预设", "无主漫染 / JoJo撞色 / 罪恶留红 / 赛博霓虹 / 复古胶片 / CRT扫描", FilterPreset.BORDERLANDS
    ));

    public SinCityFilterModule() {
        super("sin_city", "滤镜", "Filters", "6款真实 GLSL 后处理漫画与电影级视觉滤镜", Category.VISUAL);
    }

    public Identifier getActiveEffectId() {
        return preset.get().effectId;
    }
}
