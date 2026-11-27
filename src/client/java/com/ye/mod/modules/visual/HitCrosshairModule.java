package com.ye.mod.modules.visual;

import com.ye.mod.core.Category;
import com.ye.mod.core.Module;
import com.ye.mod.core.setting.BoolSetting;
import com.ye.mod.core.setting.DoubleSetting;
import com.ye.mod.core.setting.EnumSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Pixel-exact 4-quadrant symmetric X Hitmarker centered around ((guiWidth - 1) / 2, (guiHeight - 1) / 2).
 */
public class HitCrosshairModule extends Module {
    public enum ColorPreset {
        WHITE("白", 0xFFFFFF),
        CRIMSON("红", 0xEF4444),
        GOLD("金", 0xFACC15),
        CYAN("青", 0x22D3EE);

        public final String label;
        public final int rgb;
        ColorPreset(String label, int rgb) {
            this.label = label;
            this.rgb = rgb;
        }
    }

    public final EnumSetting<ColorPreset> color = addSetting(new EnumSetting<>(
            "color", "颜色", "命中时 X 标记颜色", ColorPreset.WHITE
    ));

    public final DoubleSetting size = addSetting(new DoubleSetting(
            "size", "臂长", "对称 X 斜线像素长度", 5.0, 3.0, 10.0, 1.0
    ));

    public final BoolSetting outline = addSetting(new BoolSetting(
            "outline", "描边", "增加暗色轮廓防止混入亮色背景", true
    ));

    private long lastHitMs = 0L;

    public HitCrosshairModule() {
        super("hit_crosshair", "命中准星", "Hitmarker", "命中目标时准星正中央展开严格对称 X 反馈", Category.VISUAL);
    }

    public void triggerHit() {
        if (isEnabled()) {
            lastHitMs = System.currentTimeMillis();
        }
    }

    public void render(GuiGraphics g) {
        if (!isEnabled()) return;
        long elapsed = System.currentTimeMillis() - lastHitMs;
        if (elapsed < 0 || elapsed > 230L) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;

        float t = elapsed / 230.0f;
        int alpha = Math.max(0, Math.min(255, Math.round((1.0f - t) * 255.0f)));
        int col = (alpha << 24) | color.get().rgb;
        int outCol = (Math.round(alpha * 0.65f) << 24);

        // Exact vanilla crosshair center pixel!
        int cx = (g.guiWidth() - 1) / 2;
        int cy = (g.guiHeight() - 1) / 2;
        int arm = Math.max(3, (int) Math.round(size.get()));
        int innerGap = 3 + Math.round(t * 2.0f);

        renderSymmetricX(g, cx, cy, innerGap, arm, col, outline.get() ? outCol : 0);
    }

    public static void renderSymmetricX(GuiGraphics g, int cx, int cy, int gap, int arm, int col, int outlineCol) {
        if (outlineCol != 0) {
            for (int i = 0; i < arm; i++) {
                int d = gap + i;
                g.fill(cx - d - 1, cy - d - 1, cx - d + 2, cy - d + 2, outlineCol);
                g.fill(cx + d - 1, cy - d - 1, cx + d + 2, cy - d + 2, outlineCol);
                g.fill(cx - d - 1, cy + d - 1, cx - d + 2, cy + d + 2, outlineCol);
                g.fill(cx + d - 1, cy + d - 1, cx + d + 2, cy + d + 2, outlineCol);
            }
        }
        for (int i = 0; i < arm; i++) {
            int d = gap + i;
            g.fill(cx - d, cy - d, cx - d + 1, cy - d + 1, col);
            g.fill(cx + d, cy - d, cx + d + 1, cy - d + 1, col);
            g.fill(cx - d, cy + d, cx - d + 1, cy + d + 1, col);
            g.fill(cx + d, cy + d, cx + d + 1, cy + d + 1, col);
        }
    }
}
