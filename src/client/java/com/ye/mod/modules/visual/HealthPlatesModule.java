package com.ye.mod.modules.visual;

import com.ye.mod.core.Category;
import com.ye.mod.core.ConfigManager;
import com.ye.mod.core.Module;
import com.ye.mod.core.ModuleManager;
import com.ye.mod.core.setting.BoolSetting;
import com.ye.mod.core.setting.DoubleSetting;
import com.ye.mod.core.setting.EnumSetting;
import com.ye.mod.render.YeWorldFxRenderer;
import com.ye.mod.ui.AppleUiRenderer;
import com.ye.mod.ui.YeHdFont;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.EntityHitResult;

import java.util.Locale;

/**
 * Target Health Bar rendered with 1:1 physical-pixel YeHdFont and smooth rounded outlines!
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
                renderOverheadForEntity(g, le, sm, sw, sh);
                if (++count >= 24) break;
            }
            return;
        }

        if (targetId < 0 || System.currentTimeMillis() - lastSeenMs > 4000L) return;
        Entity e = mc.level.getEntity(targetId);
        if (!(e instanceof LivingEntity le) || !le.isAlive()) return;

        if (pm == PositionMode.OVERHEAD) {
            renderOverheadForEntity(g, le, sm, sw, sh);
        } else if (pm == PositionMode.VERTICAL_SIDE) {
            renderVerticalSide(g, mc, le, sm, sw, sh);
        } else {
            renderTopBar(g, mc, le, sm, sw);
        }
    }

    private static void renderOverheadForEntity(GuiGraphics g, LivingEntity le, StyleMode sm, int sw, int sh) {
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
            int bw = 54, bh = 5;
            int bx = cx - bw / 2, by = cy - bh / 2;
            AppleUiRenderer.drawRoundedRect(g, bx - 1, by - 1, bw + 2, bh + 2, 3, 0xCC090D16);
            AppleUiRenderer.drawRoundedRect(g, bx, by, bw * pct, bh, 2.5f, fillCol);
        } else {
            String name = le.getDisplayName().getString();
            String hpTxt = String.format(Locale.ROOT, "%.0f/%.0f", hp, maxHp);
            int w = Math.max(78, Math.min(136, YeHdFont.width(name, 8.2f, true) + YeHdFont.width(hpTxt, 8.0f, false) + 20));
            int h = 21;
            int x = cx - w / 2, y = cy - h;
            AppleUiRenderer.drawRoundedRect(g, x, y, w, h, 5, 0xD80B101B);
            AppleUiRenderer.drawRoundedOutline(g, x, y, w, h, 5, 1.0f, 0x28FFFFFF);

            float hpW = YeHdFont.widthF(hpTxt, 8.0f, false);
            String shortName = YeHdFont.clipToWidth(name, 8.2f, true, w - hpW - 14);
            YeHdFont.draw(g, shortName, x + 5, y + 3, 8.2f, 0xFFF8FAFC, false, true);
            YeHdFont.draw(g, hpTxt, x + w - 5 - hpW, y + 3.2f, 8.0f, 0xFFCBD5E1, false, false);

            AppleUiRenderer.drawRoundedRect(g, x + 5, y + 14, w - 10, 4, 2, 0xFF1E293B);
            AppleUiRenderer.drawRoundedRect(g, x + 5, y + 14, (w - 10) * pct, 4, 2, fillCol);
        }
    }

    private static void renderVerticalSide(GuiGraphics g, Minecraft mc, LivingEntity le, StyleMode sm, int sw, int sh) {
        float[] proj = YeWorldFxRenderer.projectWorldToGui(
                le.getX(), le.getY() + le.getBbHeight() * 0.55, le.getZ(), sw, sh);
        int bx = (proj != null) ? Math.round(proj[0]) + 22 : (sw / 2 + 24);
        int cy = (proj != null) ? Math.round(proj[1]) : (sh / 2);

        float hp = le.getHealth();
        float maxHp = Math.max(1.0f, le.getMaxHealth());
        float pct = Math.max(0.0f, Math.min(1.0f, hp / maxHp));
        int fillCol = hpColor(pct);

        int barW = 6, barH = 48;
        int by = cy - barH / 2;
        AppleUiRenderer.drawRoundedRect(g, bx - 2, by - 2, barW + 4, barH + 4, 3, 0xD80B101B);
        AppleUiRenderer.drawRoundedRect(g, bx, by, barW, barH, 2, 0xFF1E293B);
        float filled = barH * pct;
        AppleUiRenderer.drawRoundedRect(g, bx, by + (barH - filled), barW, filled, 2, fillCol);

        if (sm == StyleMode.DETAILED) {
            double dist = mc.player.distanceTo(le);
            String name = YeHdFont.clipToWidth(le.getDisplayName().getString(), 8.4f, true, 78);
            YeHdFont.draw(g, name, bx + barW + 6, by + 2, 8.4f, 0xFFF8FAFC, true, true);
            YeHdFont.draw(g, String.format(Locale.ROOT, "%.1f ❤", hp), bx + barW + 6, by + 14, 8.2f, fillCol, true, true);
            YeHdFont.draw(g, String.format(Locale.ROOT, "%.1fm", dist), bx + barW + 6, by + 26, 8.0f, 0xFF94A3B8, true, false);
        }
    }

    private static void renderTopBar(GuiGraphics g, Minecraft mc, LivingEntity le, StyleMode sm, int sw) {
        float hp = le.getHealth();
        float maxHp = Math.max(1.0f, le.getMaxHealth());
        float pct = Math.max(0.0f, Math.min(1.0f, hp / maxHp));
        int fillCol = hpColor(pct);

        if (sm == StyleMode.BAR_ONLY) {
            int w = 128, h = 7;
            int x = (sw - w) / 2, y = 24;
            AppleUiRenderer.drawRoundedRect(g, x - 2, y - 2, w + 4, h + 4, 4, 0xCC0B101B);
            AppleUiRenderer.drawRoundedRect(g, x, y, w * pct, h, 3.5f, fillCol);
        } else {
            double dist = mc.player.distanceTo(le);
            int w = 152, h = 26;
            int x = (sw - w) / 2, y = 24;
            AppleUiRenderer.drawRoundedRect(g, x, y, w, h, 6, 0xDF0B101B);
            AppleUiRenderer.drawRoundedOutline(g, x, y, w, h, 6, 1.0f, 0x2AFFFFFF);

            String hpStr = String.format(Locale.ROOT, "%.0f/%.0f · %.1fm", hp, maxHp, dist);
            float hpW = YeHdFont.widthF(hpStr, 8.0f, false);
            String name = YeHdFont.clipToWidth(le.getDisplayName().getString(), 8.5f, true, w - hpW - 16);
            YeHdFont.draw(g, name, x + 7, y + 4.5f, 8.5f, 0xFFF8FAFC, false, true);
            YeHdFont.draw(g, hpStr, x + w - 7 - hpW, y + 4.8f, 8.0f, 0xFF94A3B8, false, false);

            AppleUiRenderer.drawRoundedRect(g, x + 7, y + 17, w - 14, 4.5f, 2.2f, 0xFF1E293B);
            AppleUiRenderer.drawRoundedRect(g, x + 7, y + 17, (w - 14) * pct, 4.5f, 2.2f, fillCol);
        }
    }

    private static int hpColor(float pct) {
        return pct > 0.55f ? 0xFF22C55E : (pct > 0.25f ? 0xFFF59E0B : 0xFFEF4444);
    }
}
