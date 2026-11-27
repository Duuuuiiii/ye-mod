package com.ye.mod.modules.visual;

import com.ye.mod.core.Category;
import com.ye.mod.core.Module;
import com.ye.mod.core.ModuleManager;
import com.ye.mod.core.setting.BoolSetting;
import com.ye.mod.core.setting.DoubleSetting;
import com.ye.mod.core.setting.EnumSetting;
import com.ye.mod.render.YeWorldFxRenderer;
import com.ye.mod.ui.AppleUiRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.EntityHitResult;

/**
 * Target Health Bar supporting:
 * - Position: 生物头顶 (3D world-projected overhead) / 竖状侧边 (Vertical beside entity/crosshair) / 屏幕顶部 (Top plate)
 * - Style: 详细 (Name + HP + Dist) / 纯条状 (Clean minimal bar)
 */
public class HealthPlatesModule extends Module {
    public enum PositionMode {
        OVERHEAD("生物头顶"),
        VERTICAL_SIDE("竖状旁边"),
        TOP_SCREEN("屏幕顶部");

        public final String label;
        PositionMode(String label) { this.label = label; }
    }

    public enum StyleMode {
        DETAILED("详细"),
        BAR_ONLY("纯条状");

        public final String label;
        StyleMode(String label) { this.label = label; }
    }

    public final EnumSetting<PositionMode> posMode = addSetting(new EnumSetting<>(
            "posMode", "位置", "生物头顶 / 竖状旁边 / 屏幕顶部", PositionMode.OVERHEAD
    ));

    public final EnumSetting<StyleMode> styleMode = addSetting(new EnumSetting<>(
            "styleMode", "样式", "详细(名称血量距离) / 纯条状", StyleMode.DETAILED
    ));

    public final BoolSetting showAllNearby = addSetting(new BoolSetting(
            "showAllNearby", "周围全部生物", "头顶模式下同时显示周围所有生物血条", false
    ).visible(() -> posMode.get() == PositionMode.OVERHEAD));

    public final DoubleSetting range = addSetting(new DoubleSetting(
            "range", "显示距离", "头顶血条最大显示距离", 24.0, 8.0, 64.0, 2.0
    ).visible(() -> posMode.get() == PositionMode.OVERHEAD));

    private int targetId = -1;
    private long lastSeenMs = 0L;

    public HealthPlatesModule() {
        super("health_plates", "血条", "Health Bar", "支持生物头顶、竖状侧边或顶部，可选详细或纯条状", Category.UTILITY);
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
        IdleWallpaperModule idle = ModuleManager.get(IdleWallpaperModule.class);
        if (idle != null && idle.shouldHideHud()) return;

        if (mc.hitResult instanceof EntityHitResult ehr && ehr.getEntity() instanceof LivingEntity) {
            targetId = ehr.getEntity().getId();
            lastSeenMs = System.currentTimeMillis();
        }

        PositionMode pm = posMode.get();
        StyleMode sm = styleMode.get();
        int sw = g.guiWidth();
        int sh = g.guiHeight();

        if (pm == PositionMode.OVERHEAD && showAllNearby.get()) {
            double maxD2 = range.get() * range.get();
            int count = 0;
            for (Entity ent : mc.level.entitiesForRendering()) {
                if (!(ent instanceof LivingEntity le) || ent == mc.player || !le.isAlive() || le.isInvisible()) continue;
                if (ent.distanceToSqr(mc.player) > maxD2) continue;
                renderOverheadForEntity(g, mc, le, sm, sw, sh);
                if (++count >= 24) break;
            }
            return;
        }

        if (targetId < 0 || System.currentTimeMillis() - lastSeenMs > 4000L) return;
        Entity e = mc.level.getEntity(targetId);
        if (!(e instanceof LivingEntity le) || !le.isAlive()) return;

        if (pm == PositionMode.OVERHEAD) {
            renderOverheadForEntity(g, mc, le, sm, sw, sh);
        } else if (pm == PositionMode.VERTICAL_SIDE) {
            renderVerticalSide(g, mc, le, sm, sw, sh);
        } else {
            renderTopBar(g, mc, le, sm, sw);
        }
    }

    private static void renderOverheadForEntity(GuiGraphics g, Minecraft mc, LivingEntity le,
                                                StyleMode sm, int sw, int sh) {
        float[] proj = YeWorldFxRenderer.projectWorldToGui(
                le.getX(), le.getY() + le.getBbHeight() + 0.42, le.getZ(), sw, sh);
        if (proj == null) return;
        int cx = Math.round(proj[0]);
        int cy = Math.round(proj[1]);

        float hp = le.getHealth();
        float maxHp = Math.max(1.0f, le.getMaxHealth());
        float pct = Math.max(0.0f, Math.min(1.0f, hp / maxHp));
        int fillCol = hpColor(pct);

        if (sm == StyleMode.BAR_ONLY) {
            int bw = 54;
            int bh = 5;
            int bx = cx - bw / 2;
            int by = cy - bh / 2;
            AppleUiRenderer.drawRoundedRect(g, bx - 1, by - 1, bw + 2, bh + 2, 3, 0xCC090D16);
            g.fill(bx, by, bx + Math.round(bw * pct), by + bh, fillCol);
        } else {
            String name = le.getDisplayName().getString();
            String hpTxt = String.format("%.0f/%.0f", hp, maxHp);
            int w = Math.max(76, Math.min(130, mc.font.width(name) + mc.font.width(hpTxt) + 18));
            int h = 21;
            int x = cx - w / 2;
            int y = cy - h;
            AppleUiRenderer.drawRoundedRect(g, x, y, w, h, 5, 0xCC0B101B);
            g.renderOutline(x, y, w, h, 0x33FFFFFF);
            String shortName = mc.font.plainSubstrByWidth(name, w - mc.font.width(hpTxt) - 12);
            g.drawString(mc.font, shortName, x + 5, y + 3, 0xFFF8FAFC, false);
            g.drawString(mc.font, hpTxt, x + w - 5 - mc.font.width(hpTxt), y + 3, 0xFFCBD5E1, false);
            int bx = x + 5, by = y + 14, bw = w - 10;
            g.fill(bx, by, bx + bw, by + 4, 0xFF1E293B);
            g.fill(bx, by, bx + Math.round(bw * pct), by + 4, fillCol);
        }
    }

    private static void renderVerticalSide(GuiGraphics g, Minecraft mc, LivingEntity le,
                                           StyleMode sm, int sw, int sh) {
        float[] proj = YeWorldFxRenderer.projectWorldToGui(
                le.getX(), le.getY() + le.getBbHeight() * 0.55, le.getZ(), sw, sh);
        int bx = (proj != null) ? Math.round(proj[0]) + 22 : (sw / 2 + 24);
        int cy = (proj != null) ? Math.round(proj[1]) : (sh / 2);

        float hp = le.getHealth();
        float maxHp = Math.max(1.0f, le.getMaxHealth());
        float pct = Math.max(0.0f, Math.min(1.0f, hp / maxHp));
        int fillCol = hpColor(pct);

        int barW = 6;
        int barH = 48;
        int by = cy - barH / 2;
        AppleUiRenderer.drawRoundedRect(g, bx - 2, by - 2, barW + 4, barH + 4, 3, 0xD80B101B);
        g.fill(bx, by, bx + barW, by + barH, 0xFF1E293B);
        int filled = Math.round(barH * pct);
        g.fill(bx, by + (barH - filled), bx + barW, by + barH, fillCol);

        if (sm == StyleMode.DETAILED) {
            double dist = mc.player.distanceTo(le);
            String name = mc.font.plainSubstrByWidth(le.getDisplayName().getString(), 72);
            g.drawString(mc.font, name, bx + barW + 6, by + 2, 0xFFF8FAFC, true);
            g.drawString(mc.font, String.format("%.1f ❤", hp), bx + barW + 6, by + 14, fillCol, true);
            g.drawString(mc.font, String.format("%.1fm", dist), bx + barW + 6, by + 26, 0xFF94A3B8, true);
        }
    }

    private static void renderTopBar(GuiGraphics g, Minecraft mc, LivingEntity le, StyleMode sm, int sw) {
        float hp = le.getHealth();
        float maxHp = Math.max(1.0f, le.getMaxHealth());
        float pct = Math.max(0.0f, Math.min(1.0f, hp / maxHp));
        int fillCol = hpColor(pct);

        if (sm == StyleMode.BAR_ONLY) {
            int w = 124, h = 7;
            int x = (sw - w) / 2, y = 24;
            AppleUiRenderer.drawRoundedRect(g, x - 2, y - 2, w + 4, h + 4, 4, 0xCC0B101B);
            g.fill(x, y, x + Math.round(w * pct), y + h, fillCol);
        } else {
            double dist = mc.player.distanceTo(le);
            int w = 144, h = 26;
            int x = (sw - w) / 2, y = 24;
            AppleUiRenderer.drawRoundedRect(g, x, y, w, h, 6, 0xD80B101B);
            g.renderOutline(x, y, w, h, 0x33FFFFFF);
            String name = mc.font.plainSubstrByWidth(le.getDisplayName().getString(), 80);
            g.drawString(mc.font, name, x + 6, y + 4, 0xFFF8FAFC, false);
            String hpStr = String.format("%.0f/%.0f · %.1fm", hp, maxHp, dist);
            g.drawString(mc.font, hpStr, x + w - 6 - mc.font.width(hpStr), y + 4, 0xFF94A3B8, false);
            g.fill(x + 6, y + 16, x + w - 6, y + 21, 0xFF1E293B);
            g.fill(x + 6, y + 16, x + 6 + Math.round((w - 12) * pct), y + 21, fillCol);
        }
    }

    private static int hpColor(float pct) {
        return pct > 0.55f ? 0xFF22C55E : (pct > 0.25f ? 0xFFF59E0B : 0xFFEF4444);
    }
}
