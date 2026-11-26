package com.ye.mod.modules.visual;

import com.ye.mod.core.Category;
import com.ye.mod.core.Module;
import com.ye.mod.core.setting.EnumSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Anime-style radial speed lines (#7) when sprinting or flying at high speed.
 */
public class SpeedLinesModule extends Module {
    public enum ColorPreset {
        WHITE(0xFFFFFF),
        CYAN(0x38BDF8),
        GOLD(0xFACC15),
        CRIMSON(0xEF4444);

        public final int rgb;
        ColorPreset(int rgb) { this.rgb = rgb; }
    }

    public final EnumSetting<ColorPreset> color = addSetting(new EnumSetting<>(
            "color", "速度线颜色", "白 / 赛博青 / 鎏金 / 绯红", ColorPreset.WHITE
    ));

    private float intensity = 0.0f;

    public SpeedLinesModule() {
        super("speed_lines", "冲刺速度线", "Speed Lines", "疾跑或鞘翅高速飞行时屏幕四周出现动漫感流光速度线", Category.VISUAL);
    }

    public void render(GuiGraphics g) {
        if (!isEnabled()) {
            intensity = 0.0f;
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;

        double spd = mc.player.getDeltaMovement().horizontalDistance();
        boolean fast = mc.player.isSprinting() || mc.player.isFallFlying() || spd > 0.24;
        float target = fast ? (float) Math.min(1.0, Math.max(0.35, spd * 1.8)) : 0.0f;
        intensity += (target - intensity) * 0.14f;
        if (intensity < 0.03f) return;

        int sw = g.guiWidth();
        int sh = g.guiHeight();
        int cx = sw / 2;
        int cy = sh / 2;
        double maxR = Math.hypot(cx, cy);
        long tick = System.currentTimeMillis() / 45L;
        int rgb = color.get().rgb;

        int lines = 32;
        for (int i = 0; i < lines; i++) {
            double ang = (i / (double) lines) * Math.PI * 2.0 + ((tick * 17 + i * 31) % 7) * 0.025;
            double cos = Math.cos(ang);
            double sin = Math.sin(ang);
            double startRatio = 0.56 + ((i + tick) % 3) * 0.07;
            int a = Math.min(180, Math.round(intensity * (110 + ((i + tick) % 3) * 25)));
            int col = (a << 24) | rgb;

            int steps = 10;
            for (int s = 0; s < steps; s++) {
                double r = maxR * (startRatio + (1.0 - startRatio) * (s / (double) steps));
                int px = (int) Math.round(cx + cos * r);
                int py = (int) Math.round(cy + sin * r);
                g.fill(px, py, px + 2, py + 2, col);
            }
        }
    }
}
