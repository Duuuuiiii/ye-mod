package com.ye.mod.modules.visual;

import com.ye.mod.core.Category;
import com.ye.mod.core.Module;
import com.ye.mod.core.setting.BoolSetting;
import com.ye.mod.core.setting.DoubleSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.sounds.SoundEvents;

/**
 * Low-HP pulsing crimson vignette + optional heartbeat sound (#73).
 */
public class LowHpVignetteModule extends Module {
    public final DoubleSetting hpThreshold = addSetting(new DoubleSetting(
            "hpThreshold", "触发血量", "低于多少点血量开始红色心跳预警", 8.0, 2.0, 16.0, 1.0
    ));

    public final BoolSetting heartbeatSound = addSetting(new BoolSetting(
            "heartbeatSound", "心跳音效", "残血时伴随低沉心跳声提示", true
    ));

    private long lastBeatMs = 0L;

    public LowHpVignetteModule() {
        super("low_hp_vignette", "低血心跳氛围", "Low HP Pulse", "残血时屏幕边缘呼吸红晕+心跳提示音", Category.VISUAL);
    }

    public void render(GuiGraphics g) {
        if (!isEnabled()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;

        float hp = mc.player.getHealth();
        float thresh = hpThreshold.get().floatValue();
        if (hp > thresh || hp <= 0.0f) return;

        long now = System.currentTimeMillis();
        float severity = Math.max(0.2f, Math.min(1.0f, 1.0f - (hp / thresh)));
        double pulse = 0.55 + 0.45 * Math.sin((now / 260.0) * (1.0 + severity * 0.8));
        int a = Math.min(165, Math.round((float) (110 * severity * pulse)));

        int sw = g.guiWidth();
        int sh = g.guiHeight();
        int band = Math.max(20, sh / 7);
        int colEdge = (a << 24) | 0x00B91C1C;
        g.fillGradient(0, 0, sw, band, colEdge, 0x00B91C1C);
        g.fillGradient(0, sh - band, sw, sh, 0x00B91C1C, colEdge);

        long beatInterval = Math.round(950 - severity * 380);
        if (heartbeatSound.get() && now - lastBeatMs >= beatInterval) {
            lastBeatMs = now;
            mc.player.playSound(SoundEvents.WARDEN_HEARTBEAT, 0.35f * severity, 1.05f);
        }
    }
}
