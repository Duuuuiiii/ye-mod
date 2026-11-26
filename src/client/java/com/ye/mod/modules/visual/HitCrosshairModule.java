package com.ye.mod.modules.visual;

import com.ye.mod.core.Category;
import com.ye.mod.core.Module;
import com.ye.mod.core.setting.DoubleSetting;
import com.ye.mod.core.setting.EnumSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Perfectly symmetric 4-way X Hitmarker around crosshair on entity attack (#62).
 */
public class HitCrosshairModule extends Module {
    public enum ColorPreset {
        WHITE(0xFFFFFF),
        CRIMSON(0xEF4444),
        GOLD(0xFACC15),
        CYAN(0x22D3EE);

        public final int rgb;
        ColorPreset(int rgb) { this.rgb = rgb; }
    }

    public final EnumSetting<ColorPreset> color = addSetting(new EnumSetting<>(
            "color", "命中颜色", "命中时 X 标记的颜色", ColorPreset.WHITE
    ));

    public final DoubleSetting size = addSetting(new DoubleSetting(
            "size", "展开大小", "对称 X 臂长", 6.0, 3.0, 12.0, 1.0
    ));

    private long lastHitMs = 0L;

    public HitCrosshairModule() {
        super("hit_crosshair", "命中准星反馈", "Hit Crosshair", "攻击命中时准星四周展开完全对称的 X 型打击反馈", Category.VISUAL);
    }

    public void triggerHit() {
        if (isEnabled()) {
            lastHitMs = System.currentTimeMillis();
        }
    }

    public void render(GuiGraphics g) {
        if (!isEnabled()) return;
        long elapsed = System.currentTimeMillis() - lastHitMs;
        if (elapsed < 0 || elapsed > 220L) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;

        float t = elapsed / 220.0f;
        int alpha = Math.max(0, Math.min(255, Math.round((1.0f - t) * 255.0f)));
        int col = (alpha << 24) | color.get().rgb;

        int cx = g.guiWidth() / 2;
        int cy = g.guiHeight() / 2;
        int arm = Math.max(3, (int) Math.round(size.get()));
        int innerGap = 3 + Math.round(t * 3.0f);

        // Strictly symmetric 4-quadrant diagonal strokes around exact center (cx, cy)
        for (int i = 0; i < arm; i++) {
            int d = innerGap + i;
            g.fill(cx - d - 1, cy - d - 1, cx - d + 1, cy - d + 1, col);
            g.fill(cx + d,     cy - d - 1, cx + d + 2, cy - d + 1, col);
            g.fill(cx - d - 1, cy + d,     cx - d + 1, cy + d + 2, col);
            g.fill(cx + d,     cy + d,     cx + d + 2, cy + d + 2, col);
        }
    }
}
