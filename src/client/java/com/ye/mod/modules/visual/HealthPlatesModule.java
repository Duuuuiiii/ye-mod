package com.ye.mod.modules.visual;

import com.ye.mod.core.Category;
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
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

/**
 * Jitter-Free Sub-Pixel Target Health Bar (血条):
 * - Uses exact partialTick interpolated 3D coordinates + float sub-pixel projection (ZERO integer snapping jitter!)
 * - Projects all 8 corners of entity AABB so overhead & side bars NEVER overlap the mob model!
 * - Supports 3 trigger modes: 看过后保留 X.X秒 (0.1s precision) / 仅盯着显示 / 视野内常显
 * - Smooth animated HP bar + trailing damage-drain animation!
 */
public class HealthPlatesModule extends Module {
    public enum PositionMode {
        OVERHEAD("生物头顶"),
        VERTICAL_SIDE("模型侧边"),
        TOP_SCREEN("屏幕顶部");

        public final String label;
        PositionMode(String label) { this.label = label; }
    }

    public enum StyleMode {
        DETAILED("详细面板"),
        BAR_ONLY("纯净条状");

        public final String label;
        StyleMode(String label) { this.label = label; }
    }

    public enum TriggerMode {
        LOOK_HOLD("看过后保留"),
        LOOK_ONLY("仅盯着显示"),
        IN_FOV("视野内常显");

        public final String label;
        TriggerMode(String label) { this.label = label; }
    }

    public final EnumSetting<PositionMode> posMode = addSetting(new EnumSetting<>(
            "posMode", "显示位置", "生物头顶 / 模型侧边 / 屏幕顶部", PositionMode.OVERHEAD
    ).functionGroup());

    public final EnumSetting<StyleMode> styleMode = addSetting(new EnumSetting<>(
            "styleMode", "面板样式", "详细面板(名称数值距离) / 纯净条状", StyleMode.DETAILED
    ).functionGroup());

    public final EnumSetting<TriggerMode> triggerMode = addSetting(new EnumSetting<>(
            "triggerMode", "触发方式", "看过后保留X秒 / 仅盯着时显示 / 视野内全部显示", TriggerMode.LOOK_HOLD
    ).functionGroup());

    public final DoubleSetting holdSeconds = addSetting(new DoubleSetting(
            "holdSeconds", "保留秒数", "准星移开或停止攻击后继续显示血条的秒数 (0.1s精度)", 3.5, 0.1, 15.0, 0.1
    ).visible(() -> triggerMode.get() == TriggerMode.LOOK_HOLD).functionGroup());

    public final DoubleSetting range = addSetting(new DoubleSetting(
            "range", "最大距离", "血条最大检测与显示距离", 28.0, 6.0, 64.0, 1.0
    ).functionGroup());

    public final BoolSetting smoothHpAnim = addSetting(new BoolSetting(
            "smoothHpAnim", "血量变化动画", "扣血与回血平滑过渡并显示掉血白色残影条", true
    ).functionGroup());

    public final DoubleSetting scale = addSetting(new DoubleSetting(
            "scale", "血条缩放", "调节血条整体尺寸", 1.0, 0.65, 1.60, 0.05
    ));

    public final BoolSetting syncGlobalColor = addSetting(new BoolSetting(
            "syncGlobalColor", "跟随同步色", "满血状态颜色跟随同步色模块", false
    ));

    private static final class HpAnimState {
        float mainPct;
        float trailPct;
        long lastUpdateNs;

        HpAnimState(float init) {
            this.mainPct = init;
            this.trailPct = init;
            this.lastUpdateNs = System.nanoTime();
        }
    }

    private int targetId = -1;
    private long lastSeenMs = 0L;
    private final Map<Integer, HpAnimState> animMap = new HashMap<>();

    public HealthPlatesModule() {
        super("health_plates", "血条", "Health Bar", "无抖动生物血条，自动避让生物模型边界，支持视野内常显、盯着显示或保留秒数", Category.UTILITY);
    }

    public void onAttack(Entity e) {
        if (e instanceof LivingEntity) {
            targetId = e.getId();
            lastSeenMs = System.currentTimeMillis();
        }
    }

    public void render(GuiGraphics g, float partialTick) {
        if (!isEnabled()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.options.hideGui) return;
        IdleWallpaperModule idle = ModuleManager.get(IdleWallpaperModule.class);
        if (idle != null && idle.shouldHideHud()) return;

        long nowMs = System.currentTimeMillis();
        boolean lookingNow = false;
        if (mc.hitResult instanceof EntityHitResult ehr && ehr.getEntity() instanceof LivingEntity le && le != mc.player) {
            if (mc.player.distanceTo(le) <= range.get()) {
                targetId = le.getId();
                lastSeenMs = nowMs;
                lookingNow = true;
            }
        }

        // Prune stale animation entries periodically
        if (animMap.size() > 64) {
            long nowNs = System.nanoTime();
            Iterator<Map.Entry<Integer, HpAnimState>> it = animMap.entrySet().iterator();
            while (it.hasNext()) {
                if (nowNs - it.next().getValue().lastUpdateNs > 10_000_000_000L) it.remove();
            }
        }

        PositionMode pm = posMode.get();
        StyleMode sm = styleMode.get();
        TriggerMode tm = triggerMode.get();
        int sw = g.guiWidth();
        int sh = g.guiHeight();

        if (tm == TriggerMode.IN_FOV && pm != PositionMode.TOP_SCREEN) {
            double maxD2 = range.get() * range.get();
            int count = 0;
            for (Entity ent : mc.level.entitiesForRendering()) {
                if (!(ent instanceof LivingEntity le) || ent == mc.player || !le.isAlive() || le.isInvisible()) continue;
                if (ent.distanceToSqr(mc.player) > maxD2) continue;
                if (pm == PositionMode.OVERHEAD) {
                    renderOverhead(g, le, sm, sw, sh, partialTick, 1.0f);
                } else {
                    renderVerticalSide(g, mc, le, sm, sw, sh, partialTick, 1.0f);
                }
                if (++count >= 24) break;
            }
            return;
        }

        if (targetId < 0) return;
        if (tm == TriggerMode.LOOK_ONLY && !lookingNow) return;

        long holdMs = Math.max(100L, Math.round(holdSeconds.get() * 1000.0));
        long elapsed = nowMs - lastSeenMs;
        if (tm == TriggerMode.LOOK_HOLD && elapsed > holdMs) return;

        Entity e = mc.level.getEntity(targetId);
        if (!(e instanceof LivingEntity le) || !le.isAlive() || mc.player.distanceTo(le) > range.get() + 4.0) return;

        float fadeAlpha = 1.0f;
        if (tm == TriggerMode.LOOK_HOLD && !lookingNow && holdMs > 250L && elapsed > holdMs - 220L) {
            fadeAlpha = Math.max(0.05f, (holdMs - elapsed) / 220.0f);
        }

        if (pm == PositionMode.OVERHEAD) {
            renderOverhead(g, le, sm, sw, sh, partialTick, fadeAlpha);
        } else if (pm == PositionMode.VERTICAL_SIDE) {
            renderVerticalSide(g, mc, le, sm, sw, sh, partialTick, fadeAlpha);
        } else {
            renderTopBar(g, mc, le, sm, sw, fadeAlpha);
        }
    }

    /**
     * Projects all 8 corners of the entity's interpolated AABB so the bar sits strictly outside the model silhouette!
     * Returns {centerX, minX, maxX, minY, maxY} in float sub-pixel GUI space, or null if behind camera.
     */
    private static float[] projectEntityScreenBounds(LivingEntity le, int sw, int sh, float partialTick) {
        double ix = Mth.lerp(partialTick, le.xo, le.getX());
        double iy = Mth.lerp(partialTick, le.yo, le.getY());
        double iz = Mth.lerp(partialTick, le.zo, le.getZ());
        AABB box = le.getBoundingBox().move(ix - le.getX(), iy - le.getY(), iz - le.getZ());

        float[] center = YeWorldFxRenderer.projectWorldToGui(ix, iy + le.getBbHeight() * 0.5, iz, sw, sh);
        if (center == null) return null;

        float minX = Float.POSITIVE_INFINITY, maxX = Float.NEGATIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY;
        int valid = 0;

        double[] xs = {box.minX, box.maxX};
        double[] ys = {box.minY, box.maxY + 0.08};
        double[] zs = {box.minZ, box.maxZ};
        for (double x : xs) {
            for (double y : ys) {
                for (double z : zs) {
                    float[] p = YeWorldFxRenderer.projectWorldToGui(x, y, z, sw, sh);
                    if (p != null) {
                        minX = Math.min(minX, p[0]);
                        maxX = Math.max(maxX, p[0]);
                        minY = Math.min(minY, p[1]);
                        maxY = Math.max(maxY, p[1]);
                        valid++;
                    }
                }
            }
        }
        if (valid == 0) return null;
        return new float[]{center[0], minX, maxX, minY, maxY};
    }

    private float[] resolveAnimatedHp(LivingEntity le) {
        float hp = Math.max(0.0f, le.getHealth());
        float maxHp = Math.max(1.0f, le.getMaxHealth());
        float rawPct = Math.max(0.0f, Math.min(1.0f, hp / maxHp));
        if (!smoothHpAnim.get()) {
            return new float[]{rawPct, rawPct};
        }
        long nowNs = System.nanoTime();
        HpAnimState st = animMap.computeIfAbsent(le.getId(), k -> new HpAnimState(rawPct));
        float dt = Math.max(0.001f, Math.min(0.05f, (nowNs - st.lastUpdateNs) / 1_000_000_000.0f));
        st.lastUpdateNs = nowNs;

        st.mainPct += (rawPct - st.mainPct) * (1.0f - (float) Math.exp(-18.0f * dt));
        if (rawPct > st.trailPct) {
            st.trailPct = st.mainPct;
        } else {
            st.trailPct += (rawPct - st.trailPct) * (1.0f - (float) Math.exp(-4.8f * dt));
        }
        return new float[]{st.mainPct, st.trailPct};
    }

    private void renderOverhead(GuiGraphics g, LivingEntity le, StyleMode sm, int sw, int sh, float partialTick, float alpha) {
        float[] b = projectEntityScreenBounds(le, sw, sh, partialTick);
        if (b == null) return;

        float sc = scale.get().floatValue();
        float cx = b[0];
        float topEdgeY = b[3] - 6.0f * sc; // Strictly above top of entity model AABB!

        float hp = Math.max(0f, le.getHealth());
        float maxHp = Math.max(1f, le.getMaxHealth());
        float[] pcts = resolveAnimatedHp(le);
        float pct = pcts[0], trail = pcts[1];
        int fillCol = AppleUiRenderer.withAlpha(hpColor(pct), alpha);
        int trailCol = AppleUiRenderer.withAlpha(0xFFFDE68A, alpha * 0.85f);

        if (sm == StyleMode.BAR_ONLY) {
            float bw = 56.0f * sc, bh = 5.0f * sc;
            float bx = cx - bw * 0.5f;
            float by = topEdgeY - bh;
            AppleUiRenderer.drawRoundedRect(g, bx - 1.2f, by - 1.2f, bw + 2.4f, bh + 2.4f, 2.8f * sc, AppleUiRenderer.withAlpha(0xD8090D16, alpha));
            if (trail > pct + 0.005f) {
                AppleUiRenderer.drawRoundedRect(g, bx, by, bw * trail, bh, 2.2f * sc, trailCol);
            }
            AppleUiRenderer.drawRoundedRect(g, bx, by, bw * pct, bh, 2.2f * sc, fillCol);
        } else {
            String name = le.getDisplayName().getString();
            String hpTxt = String.format(Locale.ROOT, "%.1f", hp);
            float fName = 8.4f * sc, fHp = 8.0f * sc;
            float w = Math.max(76 * sc, Math.min(142 * sc, YeHdFont.widthF(name, fName, true) + YeHdFont.widthF(hpTxt, fHp, true) + 22 * sc));
            float h = 21.0f * sc;
            float x = cx - w * 0.5f;
            float y = topEdgeY - h;

            AppleUiRenderer.drawRoundedRect(g, x, y, w, h, 5.0f * sc, AppleUiRenderer.withAlpha(0xDF0B101B, alpha));
            AppleUiRenderer.drawRoundedOutline(g, x, y, w, h, 5.0f * sc, 1.0f, AppleUiRenderer.withAlpha(0x30FFFFFF, alpha));

            float hpW = YeHdFont.widthF(hpTxt, fHp, true);
            String shortName = YeHdFont.clipToWidth(name, fName, true, w - hpW - 14 * sc);
            YeHdFont.draw(g, shortName, x + 5.5f * sc, y + 3.0f * sc, fName, AppleUiRenderer.withAlpha(0xFFF8FAFC, alpha), false, true);
            YeHdFont.draw(g, hpTxt, x + w - 5.5f * sc - hpW, y + 3.2f * sc, fHp, fillCol, false, true);

            float barW = w - 11.0f * sc, barH = 4.2f * sc;
            float bx = x + 5.5f * sc, by = y + 14.0f * sc;
            AppleUiRenderer.drawRoundedRect(g, bx, by, barW, barH, barH * 0.5f, AppleUiRenderer.withAlpha(0xFF1E293B, alpha));
            if (trail > pct + 0.005f) {
                AppleUiRenderer.drawRoundedRect(g, bx, by, barW * trail, barH, barH * 0.5f, trailCol);
            }
            AppleUiRenderer.drawRoundedRect(g, bx, by, barW * pct, barH, barH * 0.5f, fillCol);
        }
    }

    private void renderVerticalSide(GuiGraphics g, Minecraft mc, LivingEntity le, StyleMode sm, int sw, int sh, float partialTick, float alpha) {
        float[] b = projectEntityScreenBounds(le, sw, sh, partialTick);
        if (b == null) return;

        float sc = scale.get().floatValue();
        // Place strictly to the right of the entity's projected maxX (+8px gap) so it NEVER overlaps the entity body!
        float bx = Math.min(sw - 68 * sc, b[2] + 8.0f * sc);
        float cy = (b[3] + b[4]) * 0.5f;

        float hp = Math.max(0f, le.getHealth());
        float[] pcts = resolveAnimatedHp(le);
        float pct = pcts[0], trail = pcts[1];
        int fillCol = AppleUiRenderer.withAlpha(hpColor(pct), alpha);
        int trailCol = AppleUiRenderer.withAlpha(0xFFFDE68A, alpha * 0.85f);

        float barW = 5.5f * sc, barH = 46.0f * sc;
        float by = cy - barH * 0.5f;
        AppleUiRenderer.drawRoundedRect(g, bx - 1.5f * sc, by - 1.5f * sc, barW + 3.0f * sc, barH + 3.0f * sc, 3.0f * sc, AppleUiRenderer.withAlpha(0xDF0B101B, alpha));
        AppleUiRenderer.drawRoundedRect(g, bx, by, barW, barH, 2.2f * sc, AppleUiRenderer.withAlpha(0xFF1E293B, alpha));
        if (trail > pct + 0.005f) {
            float tH = barH * trail;
            AppleUiRenderer.drawRoundedRect(g, bx, by + (barH - tH), barW, tH, 2.2f * sc, trailCol);
        }
        float filled = barH * pct;
        AppleUiRenderer.drawRoundedRect(g, bx, by + (barH - filled), barW, filled, 2.2f * sc, fillCol);

        if (sm == StyleMode.DETAILED) {
            double dist = mc.player.distanceTo(le);
            String name = YeHdFont.clipToWidth(le.getDisplayName().getString(), 8.4f * sc, true, 82 * sc);
            YeHdFont.draw(g, name, bx + barW + 5.5f * sc, by + 2.0f * sc, 8.4f * sc, AppleUiRenderer.withAlpha(0xFFF8FAFC, alpha), true, true);
            YeHdFont.draw(g, String.format(Locale.ROOT, "%.1f HP", hp), bx + barW + 5.5f * sc, by + 14.0f * sc, 8.2f * sc, fillCol, true, true);
            YeHdFont.draw(g, String.format(Locale.ROOT, "%.1fm", dist), bx + barW + 5.5f * sc, by + 26.0f * sc, 8.0f * sc, AppleUiRenderer.withAlpha(0xFF94A3B8, alpha), true, false);
        }
    }

    private void renderTopBar(GuiGraphics g, Minecraft mc, LivingEntity le, StyleMode sm, int sw, float alpha) {
        float sc = scale.get().floatValue();
        float hp = Math.max(0f, le.getHealth());
        float maxHp = Math.max(1f, le.getMaxHealth());
        float[] pcts = resolveAnimatedHp(le);
        float pct = pcts[0], trail = pcts[1];
        int fillCol = AppleUiRenderer.withAlpha(hpColor(pct), alpha);
        int trailCol = AppleUiRenderer.withAlpha(0xFFFDE68A, alpha * 0.85f);

        if (sm == StyleMode.BAR_ONLY) {
            float w = 132 * sc, h = 6.5f * sc;
            float x = (sw - w) * 0.5f, y = 24.0f;
            AppleUiRenderer.drawRoundedRect(g, x - 1.5f, y - 1.5f, w + 3.0f, h + 3.0f, 3.8f * sc, AppleUiRenderer.withAlpha(0xD80B101B, alpha));
            if (trail > pct + 0.005f) AppleUiRenderer.drawRoundedRect(g, x, y, w * trail, h, 3.0f * sc, trailCol);
            AppleUiRenderer.drawRoundedRect(g, x, y, w * pct, h, 3.0f * sc, fillCol);
        } else {
            double dist = mc.player.distanceTo(le);
            float w = 156 * sc, h = 25 * sc;
            float x = (sw - w) * 0.5f, y = 22.0f;
            AppleUiRenderer.drawRoundedRect(g, x, y, w, h, 6.0f * sc, AppleUiRenderer.withAlpha(0xE20B101B, alpha));
            AppleUiRenderer.drawRoundedOutline(g, x, y, w, h, 6.0f * sc, 1.0f, AppleUiRenderer.withAlpha(0x2EFFFFFF, alpha));

            String hpStr = String.format(Locale.ROOT, "%.1f/%.0f · %.1fm", hp, maxHp, dist);
            float hpW = YeHdFont.widthF(hpStr, 8.0f * sc, false);
            String name = YeHdFont.clipToWidth(le.getDisplayName().getString(), 8.5f * sc, true, w - hpW - 16 * sc);
            YeHdFont.draw(g, name, x + 7 * sc, y + 4.0f * sc, 8.5f * sc, AppleUiRenderer.withAlpha(0xFFF8FAFC, alpha), false, true);
            YeHdFont.draw(g, hpStr, x + w - 7 * sc - hpW, y + 4.3f * sc, 8.0f * sc, AppleUiRenderer.withAlpha(0xFF94A3B8, alpha), false, false);

            float bw = w - 14 * sc, bh = 4.5f * sc;
            AppleUiRenderer.drawRoundedRect(g, x + 7 * sc, y + 16.5f * sc, bw, bh, bh * 0.5f, AppleUiRenderer.withAlpha(0xFF1E293B, alpha));
            if (trail > pct + 0.005f) AppleUiRenderer.drawRoundedRect(g, x + 7 * sc, y + 16.5f * sc, bw * trail, bh, bh * 0.5f, trailCol);
            AppleUiRenderer.drawRoundedRect(g, x + 7 * sc, y + 16.5f * sc, bw * pct, bh, bh * 0.5f, fillCol);
        }
    }

    private int hpColor(float pct) {
        if (syncGlobalColor.get() && pct > 0.55f) {
            return GlobalColorModule.resolveArgb(true, 0xFF22C55E, 1);
        }
        return pct > 0.55f ? 0xFF22C55E : (pct > 0.25f ? 0xFFF59E0B : 0xFFEF4444);
    }
}
