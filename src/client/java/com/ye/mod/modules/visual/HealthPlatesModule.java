package com.ye.mod.modules.visual;

import com.ye.mod.core.Category;
import com.ye.mod.core.Module;
import com.ye.mod.ui.AppleUiRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.EntityHitResult;

/**
 * RPG Target Health Plate HUD (#7) showing target name, exact HP bar, and distance.
 */
public class HealthPlatesModule extends Module {
    private int targetId = -1;
    private long lastSeenMs = 0L;

    public HealthPlatesModule() {
        super("health_plates", "RPG目标血条", "Target HP Bar", "瞄准或攻击生物/玩家时顶部显示 RPG 血条与距离", Category.VISUAL);
    }

    public void onAttack(Entity e) {
        if (e instanceof LivingEntity) {
            targetId = e.getId();
            lastSeenMs = System.currentTimeMillis();
        }
    }

    public void render(GuiGraphics g) {
        if (!isEnabled()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.options.hideGui) return;

        if (mc.hitResult instanceof EntityHitResult ehr && ehr.getEntity() instanceof LivingEntity) {
            targetId = ehr.getEntity().getId();
            lastSeenMs = System.currentTimeMillis();
        }
        if (targetId < 0 || System.currentTimeMillis() - lastSeenMs > 4000L) return;

        Entity e = mc.level.getEntity(targetId);
        if (!(e instanceof LivingEntity le) || !le.isAlive()) return;

        float hp = le.getHealth();
        float maxHp = Math.max(1.0f, le.getMaxHealth());
        float pct = Math.max(0.0f, Math.min(1.0f, hp / maxHp));
        double dist = mc.player.distanceTo(le);

        int w = 136;
        int h = 26;
        int x = (g.guiWidth() - w) / 2;
        int y = 26;

        AppleUiRenderer.drawRoundedRect(g, x, y, w, h, 6, 0xCC0F172A);
        String name = le.getDisplayName().getString();
        if (mc.font.width(name) > 84) name = mc.font.plainSubstrByWidth(name, 82);
        AppleUiRenderer.drawText(g, mc.font, name, x + 6, y + 4, 0xFFF8FAFC);

        String hpStr = String.format("%.0f/%.0f · %.1fm", hp, maxHp, dist);
        AppleUiRenderer.drawText(g, mc.font, hpStr, x + w - 6 - mc.font.width(hpStr), y + 4, 0xFF94A3B8);

        int barX = x + 6;
        int barY = y + 16;
        int barW = w - 12;
        g.fill(barX, barY, barX + barW, barY + 5, 0xFF1E293B);
        int fillCol = pct > 0.5f ? 0xFF22C55E : (pct > 0.25f ? 0xFFF59E0B : 0xFFEF4444);
        g.fill(barX, barY, barX + Math.round(barW * pct), barY + 5, fillCol);
    }
}
