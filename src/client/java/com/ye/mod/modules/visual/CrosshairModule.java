package com.ye.mod.modules.visual;

import com.ye.mod.core.Category;
import com.ye.mod.core.Module;
import com.ye.mod.core.setting.BoolSetting;
import com.ye.mod.core.setting.DoubleSetting;
import com.ye.mod.core.setting.EnumSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Custom Crosshair Presets (#90) — all perfectly symmetric around screen center.
 */
public class CrosshairModule extends Module {
    public enum Preset {
        CLASSIC_CROSS("经典十字"),
        GAP_CROSS("缺口十字"),
        DOT_RING("圆环中心点"),
        DIAMOND("菱形准星"),
        PLUS_DOT("点+短线");

        public final String label;
        Preset(String label) { this.label = label; }
    }

    public enum ColorPreset {
        WHITE(0xFFFFFFFF),
        CYAN(0xFF22D3EE),
        GREEN(0xFF4ADE80),
        PINK(0xFFF472B6),
        GOLD(0xFFFACC15),
        RED(0xFFEF4444);

        public final int argb;
        ColorPreset(int argb) { this.argb = argb; }
    }

    public final EnumSetting<Preset> preset = addSetting(new EnumSetting<>(
            "preset", "准星样式", "5种对称准星预设", Preset.GAP_CROSS
    ));

    public final EnumSetting<ColorPreset> color = addSetting(new EnumSetting<>(
            "color", "准星颜色", "选择准星配色", ColorPreset.CYAN
    ));

    public final DoubleSetting size = addSetting(new DoubleSetting(
            "size", "线长大小", "准星线条长度", 5.0, 2.0, 14.0, 1.0
    ));

    public final DoubleSetting thickness = addSetting(new DoubleSetting(
            "thickness", "线条粗细", "准星线条像素粗细", 1.0, 1.0, 3.0, 1.0
    ));

    public final DoubleSetting gap = addSetting(new DoubleSetting(
            "gap", "中心间距", "中心留空像素", 3.0, 0.0, 10.0, 1.0
    ));

    public final BoolSetting outline = addSetting(new BoolSetting(
            "outline", "黑色描边", "给准星增加 1px 黑边提升对比度", true
    ));

    public CrosshairModule() {
        super("crosshair", "准星大全", "Crosshair", "5种对称精致准星预设，可调颜色/大小/粗细/间距", Category.VISUAL);
    }

    public void render(GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || !mc.options.getCameraType().isFirstPerson()) return;

        int cx = g.guiWidth() / 2;
        int cy = g.guiHeight() / 2;
        int len = Math.max(2, (int) Math.round(size.get()));
        int th = Math.max(1, (int) Math.round(thickness.get()));
        int gp = Math.max(0, (int) Math.round(gap.get()));
        int col = color.get().argb;
        boolean ol = outline.get();

        int halfTh = th / 2;
        int x0 = cx - halfTh;
        int y0 = cy - halfTh;

        switch (preset.get()) {
            case CLASSIC_CROSS -> {
                drawBar(g, cx - len, y0, cx + len + th, y0 + th, col, ol);
                drawBar(g, x0, cy - len, x0 + th, cy + len + th, col, ol);
            }
            case GAP_CROSS -> {
                drawBar(g, cx - gp - len, y0, cx - gp, y0 + th, col, ol);
                drawBar(g, cx + gp + 1, y0, cx + gp + 1 + len, y0 + th, col, ol);
                drawBar(g, x0, cy - gp - len, x0 + th, cy - gp, col, ol);
                drawBar(g, x0, cy + gp + 1, x0 + th, cy + gp + 1 + len, col, ol);
            }
            case DOT_RING -> {
                drawBar(g, cx - 1, cy - 1, cx + 1, cy + 1, col, ol);
                int r = Math.max(3, gp + len / 2);
                drawBar(g, cx - r, cy - 2, cx - r + 1, cy + 3, col, ol);
                drawBar(g, cx + r, cy - 2, cx + r + 1, cy + 3, col, ol);
                drawBar(g, cx - 2, cy - r, cx + 3, cy - r + 1, col, ol);
                drawBar(g, cx - 2, cy + r, cx + 3, cy + r + 1, col, ol);
            }
            case DIAMOND -> {
                int r = Math.max(3, len + gp / 2);
                for (int i = 0; i <= r; i++) {
                    int d = r - i;
                    drawBar(g, cx - i, cy - d, cx - i + 1, cy - d + 1, col, false);
                    drawBar(g, cx + i, cy - d, cx + i + 1, cy - d + 1, col, false);
                    drawBar(g, cx - i, cy + d, cx - i + 1, cy + d + 1, col, false);
                    drawBar(g, cx + i, cy + d, cx + i + 1, cy + d + 1, col, false);
                }
                drawBar(g, cx, cy, cx + 1, cy + 1, col, ol);
            }
            case PLUS_DOT -> {
                drawBar(g, cx, cy, cx + 1, cy + 1, col, ol);
                drawBar(g, cx - gp - len, cy, cx - gp, cy + 1, col, ol);
                drawBar(g, cx + gp + 1, cy, cx + gp + 1 + len, cy + 1, col, ol);
                drawBar(g, cx, cy - gp - len, cx + 1, cy - gp, col, ol);
                drawBar(g, cx, cy + gp + 1, cx + 1, cy + gp + 1 + len, col, ol);
            }
        }
    }

    private static void drawBar(GuiGraphics g, int x0, int y0, int x1, int y1, int col, boolean outline) {
        if (outline) {
            g.fill(x0 - 1, y0 - 1, x1 + 1, y1 + 1, 0xBB000000);
        }
        g.fill(x0, y0, x1, y1, col);
    }
}
