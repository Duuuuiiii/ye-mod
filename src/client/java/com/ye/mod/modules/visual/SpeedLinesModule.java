package com.ye.mod.modules.visual;

import com.ye.mod.core.Category;
import com.ye.mod.core.Module;
import com.ye.mod.core.setting.BoolSetting;
import com.ye.mod.core.setting.DoubleSetting;
import com.ye.mod.core.setting.EnumSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Continuous smooth tapered radial anime speed lines using rotated Matrix3x2fStack (never dots!).
 */
public class SpeedLinesModule extends Module {
    public enum ColorPreset {
        WHITE("白", 0xFFFFFF),
        CYAN("青", 0x38BDF8),
        GOLD("金", 0xFACC15),
        CRIMSON("红", 0xEF4444);

        public final String label;
        public final int rgb;
        ColorPreset(String label, int rgb) {
            this.label = label;
            this.rgb = rgb;
        }
    }

    public final EnumSetting<ColorPreset> color = addSetting(new EnumSetting<>(
            "color", "颜色", "白 / 青 / 金 / 红", ColorPreset.WHITE
    ));

    public final BoolSetting syncGlobalColor = addSetting(new BoolSetting(
            "syncGlobalColor", "同步全局色彩", "速度线颜色跟随全局色彩模块", false
    ));

    public final DoubleSetting density = addSetting(new DoubleSetting(
            "density", "密度", "四周流光射线数量", 36.0, 16.0, 64.0, 4.0
    ));

    private float intensity = 0.0f;

    public SpeedLinesModule() {
        super("speed_lines", "速度线", "Speed Lines", "高速冲刺与飞行时四周浮现连续锋利流光射线", Category.VISUAL);
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
        float target = fast ? (float) Math.min(1.0, Math.max(0.38, spd * 1.9)) : 0.0f;
        intensity += (target - intensity) * 0.15f;
        if (intensity < 0.03f) return;

        int sw = g.guiWidth();
        int sh = g.guiHeight();
        float cx = sw * 0.5f;
        float cy = sh * 0.5f;
        int maxR = (int) Math.ceil(Math.hypot(cx, cy)) + 12;
        long framePhase = System.currentTimeMillis() / 38L;
        int rgb = GlobalColorModule.resolveRgb(syncGlobalColor.get(), color.get().rgb, 4);
        int lines = Math.max(16, (int) Math.round(density.get()));

        var pose = g.pose();
        for (int i = 0; i < lines; i++) {
            long hash = (i * 1103515245L + framePhase * 12345L) & 0xFFFFL;
            float jitter = ((hash % 100) / 100.0f - 0.5f) * 0.09f;
            float ang = (float) ((i / (double) lines) * Math.PI * 2.0) + jitter;
            float innerRatio = 0.54f + ((hash >> 4) % 18) * 0.012f - intensity * 0.06f;
            int r0 = Math.round(maxR * innerRatio);
            int rMid = Math.round(r0 + (maxR - r0) * 0.35f);

            int peakAlpha = Math.min(185, Math.round(intensity * (95 + (hash % 75))));
            int tipAlpha = peakAlpha / 3;
            int colTip = (tipAlpha << 24) | rgb;
            int colMain = (peakAlpha << 24) | rgb;

            pose.pushMatrix();
            pose.translate(cx, cy);
            pose.rotate(ang);
            // Tapered continuous streak: fine inner needle -> wider bright outer ray
            g.fill(r0, 0, rMid, 1, colTip);
            g.fill(rMid, -1, maxR, 1, colMain);
            pose.popMatrix();
        }
    }
}
