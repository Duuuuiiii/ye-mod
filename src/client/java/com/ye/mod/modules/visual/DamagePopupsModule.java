package com.ye.mod.modules.visual;

import com.ye.mod.core.Category;
import com.ye.mod.core.Module;
import com.ye.mod.ui.AppleUiRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * RPG Floating Damage Popups & Combo Counter (#5).
 */
public class DamagePopupsModule extends Module {
    private record Popup(String text, int color, long spawnMs, int offsetX) {}

    private final List<Popup> popups = new ArrayList<>();
    private int comboCount = 0;
    private long lastHitMs = 0L;
    private int lastTargetId = -1;
    private float lastTargetHp = -1f;

    public DamagePopupsModule() {
        super("damage_popups", "伤害飘字连击", "Damage & Combo", "攻击时飘出伤害/暴击数字与连击计数", Category.VISUAL);
    }

    public void onAttack(Entity target) {
        if (!isEnabled()) return;
        long now = System.currentTimeMillis();
        comboCount = (now - lastHitMs <= 2600L) ? comboCount + 1 : 1;
        lastHitMs = now;
        if (target instanceof LivingEntity le) {
            lastTargetId = le.getId();
            lastTargetHp = le.getHealth();
        }
        Minecraft mc = Minecraft.getInstance();
        boolean crit = mc.player != null && mc.player.fallDistance > 0.0f && !mc.player.onGround();
        String txt = crit ? "CRIT!" : ("HIT x" + comboCount);
        int col = crit ? 0xFFFACC15 : 0xFF38BDF8;
        int ox = ((comboCount % 3) - 1) * 18;
        popups.add(new Popup(txt, col, now, ox));
        if (popups.size() > 8) popups.remove(0);
    }

    @Override
    public void onTick() {
        if (!isEnabled() || lastTargetId < 0) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Entity e = mc.level.getEntity(lastTargetId);
        if (e instanceof LivingEntity le) {
            float hp = le.getHealth();
            if (lastTargetHp > hp && (lastTargetHp - hp) > 0.05f) {
                float dmg = lastTargetHp - hp;
                popups.add(new Popup(String.format("-%.1f", dmg), 0xFFEF4444, System.currentTimeMillis(), 14));
                if (popups.size() > 8) popups.remove(0);
                lastTargetHp = hp;
            }
        }
    }

    public void render(GuiGraphics g) {
        if (!isEnabled()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;
        long now = System.currentTimeMillis();
        int cx = g.guiWidth() / 2;
        int cy = g.guiHeight() / 2;

        if (comboCount > 1 && now - lastHitMs <= 2600L) {
            String cStr = comboCount + " COMBO";
            AppleUiRenderer.drawCenteredText(g, mc.font, cStr, cx, cy + 16, 0xFFFACC15);
        }

        Iterator<Popup> it = popups.iterator();
        while (it.hasNext()) {
            Popup p = it.next();
            long age = now - p.spawnMs;
            if (age > 950L) {
                it.remove();
                continue;
            }
            float t = age / 950.0f;
            int y = cy - 16 - Math.round(t * 28.0f);
            int alpha = Math.max(15, Math.round((1.0f - t) * 255.0f));
            int col = (alpha << 24) | (p.color & 0x00FFFFFF);
            g.drawCenteredString(mc.font, p.text, cx + p.offsetX, y, col);
        }
    }
}
