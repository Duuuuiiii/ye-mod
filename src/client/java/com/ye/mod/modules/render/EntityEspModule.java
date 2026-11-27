package com.ye.mod.modules.render;

import com.ye.mod.core.Category;
import com.ye.mod.core.ConfigManager;
import com.ye.mod.core.FriendManager;
import com.ye.mod.core.Module;
import com.ye.mod.core.setting.ActionSetting;
import com.ye.mod.core.setting.BoolSetting;
import com.ye.mod.core.setting.DoubleSetting;
import com.ye.mod.core.setting.EnumSetting;
import com.ye.mod.modules.visual.GlobalColorModule;
import com.ye.mod.modules.visual.IdleWallpaperModule;
import com.ye.mod.render.YeWorldFxRenderer;
import com.ye.mod.ui.YeQuickMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.vehicle.VehicleEntity;
import net.minecraft.world.phys.AABB;

import java.util.Locale;

/**
 * Multi-Mode Entity Highlighting & Tactical ESP (实体透视):
 * 1) Native 1.21 Post-Shader Silhouette Outline (发光轮廓 — zero FPS drop, pixel-exact contour)
 * 2) 2D Screen-Space Tactical Corner Brackets + Mini HP Bar & Info Tag (战术四角框)
 * 3) 3D Through-Wall Interpolated Hitbox Wireframe + Translucent Fill (立体透视框)
 * 4) Combined Modes + Smooth Continuous Rotated Tracer Rays (准星追踪线)
 */
public class EntityEspModule extends Module {
    public enum EspStyle {
        OUTLINE("发光轮廓", true, false, false),
        CORNER_2D("战术四角框", false, true, false),
        BOX_3D("立体透视框", false, false, true),
        OUTLINE_2D("轮廓+四角框", true, true, false),
        OUTLINE_3D("轮廓+立体框", true, false, true),
        ALL("全开叠加", true, true, true);

        public final String label;
        public final boolean useOutline;
        public final boolean use2d;
        public final boolean use3d;

        EspStyle(String label, boolean useOutline, boolean use2d, boolean use3d) {
            this.label = label;
            this.useOutline = useOutline;
            this.use2d = use2d;
            this.use3d = use3d;
        }
    }

    public enum TracerOrigin {
        CROSSHAIR("屏幕正中"),
        BOTTOM("屏幕底部");

        public final String label;
        TracerOrigin(String label) { this.label = label; }
    }

    public final EnumSetting<EspStyle> style = addSetting(new EnumSetting<>(
            "style", "高亮方式", "发光轮廓 / 战术四角框 / 立体透视框 / 组合叠加", EspStyle.OUTLINE_2D
    ));

    public final BoolSetting syncGlobalColor = addSetting(new BoolSetting(
            "syncGlobalColor", "同步全局色彩", "主要目标高亮色跟随「全局色彩」模块（好友仍可保留独立色）", false
    ));

    public final BoolSetting tracers = addSetting(new BoolSetting(
            "tracers", "准星追踪线", "向屏幕内目标绘制平滑连续追踪射线", false
    ));

    public final EnumSetting<TracerOrigin> tracerOrigin = addSetting(new EnumSetting<>(
            "tracerOrigin", "追踪线起点", "从准星正中央或屏幕底部中心引出射线", TracerOrigin.CROSSHAIR
    ).visible(tracers::get));

    public final BoolSetting tracerDistColor = addSetting(new BoolSetting(
            "tracerDistColor", "追踪线按距离变色", "近处高危亮红 -> 中距琥珀金 -> 远处冰晶青", true
    ).visible(tracers::get));

    public final BoolSetting showInfoTag = addSetting(new BoolSetting(
            "showInfoTag", "信息微标牌", "在2D战术框上方显示紧凑名称、血量条与距离", true
    ).visible(() -> style.get().use2d));

    // Target Category Toggles + Ignore Self + Full Entity Matrix Picker!
    public final BoolSetting ignoreSelf = addSetting(new BoolSetting(
            "ignoreSelf", "忽略自己", "第三人称与灵魂出窍下不给自己渲染透视框", true
    ).functionGroup());

    public final BoolSetting players = addSetting(new BoolSetting(
            "players", "玩家目标", "透视高亮其他玩家", true
    ).functionGroup());
    public final BoolSetting distinctFriends = addSetting(new BoolSetting(
            "distinctFriends", "好友独立配色", "好友列表中的玩家显示翡翠绿并标注好友", true
    ).visible(players::get).functionGroup());

    public final BoolSetting hostiles = addSetting(new BoolSetting(
            "hostiles", "敌对生物", "批量勾选所有敌对怪物（也可在下方大面板内单独指定生物）", true
    ).functionGroup());
    public final BoolSetting passives = addSetting(new BoolSetting(
            "passives", "友好与中立生物", "批量勾选牛羊村民等普通生物（也可在大面板单独勾选如羊）", false
    ).functionGroup());
    public final BoolSetting crystals = addSetting(new BoolSetting(
            "crystals", "末影水晶", "高亮 PVP 关键末影水晶实体", true
    ));
    public final BoolSetting items = addSetting(new BoolSetting(
            "items", "地面掉落物", "高亮地面掉落物实体", false
    ));
    public final BoolSetting vehiclesAndProjectiles = addSetting(new BoolSetting(
            "vehiclesAndProjectiles", "投掷物与载具", "高亮末影珍珠、潜影弹、矿车与船只", false
    ));

    public final DoubleSetting maxRange = addSetting(new DoubleSetting(
            "maxRange", "最大透视距离", "超过此距离的实体不渲染透视", 128.0, 16.0, 256.0, 8.0
    ));
    public final DoubleSetting fillAlpha = addSetting(new DoubleSetting(
            "fillAlpha", "透视填充浓度", "3D立体框内部半透明上色强度", 0.20, 0.0, 0.60, 0.04
    ).visible(() -> style.get().use3d));

    private volatile boolean renderedOutlineThisFrame = false;

    public EntityEspModule() {
        super("entity_esp", "实体透视", "Entity ESP", "多模式实体高亮（发光轮廓 / 战术四角框 / 立体透视框 / 追踪射线），配全生物平铺选择大面板", Category.VISUAL);

        addSetting(new ActionSetting("openEntityMatrix", "打开生物目标选择大面板", "展开全生物分类平铺大面板，一键全选分类或单独勾选任意特定生物（如羊、苦力怕、村民）", () ->
                YeQuickMenu.openEntityPickerModal("实体透视 · 生物目标选择大面板", ConfigManager.entityEspCustomTypes)
        ).functionGroup());
    }

    public boolean usesShaderOutline() {
        return isEnabled() && style.get().useOutline;
    }

    public boolean consumeOutlineFlag() {
        boolean v = renderedOutlineThisFrame;
        renderedOutlineThisFrame = false;
        return v;
    }

    /**
     * Returns 0 if entity should NOT be highlighted, or opaque 0xFFRRGGBB color if matched!
     */
    public int classifyEntityArgb(Entity entity) {
        if (entity == null || mc.player == null || entity.isRemoved()) return 0;
        if (entity == mc.player) {
            if (ignoreSelf.get() || mc.options.getCameraType().isFirstPerson()) return 0;
            return GlobalColorModule.resolveArgb(syncGlobalColor.get(), 0xFF38BDF8, 0);
        }
        double maxDist = maxRange.get();
        if (mc.player.distanceToSqr(entity) > maxDist * maxDist) return 0;

        String typeKey = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
        boolean explicitlyPicked = ConfigManager.entityEspCustomTypes.contains(typeKey);

        if (entity instanceof Player p) {
            if ((!players.get() && !explicitlyPicked) || p.isSpectator()) return 0;
            if (distinctFriends.get() && FriendManager.isFriend(p)) {
                return 0xFF34D399;
            }
            return GlobalColorModule.resolveArgb(syncGlobalColor.get(), 0xFFFF3B5C, 0);
        }

        if (explicitlyPicked) {
            return GlobalColorModule.resolveArgb(syncGlobalColor.get(), 0xFF22D3EE, 2);
        }

        if (entity instanceof EndCrystal) {
            if (!crystals.get()) return 0;
            return GlobalColorModule.resolveArgb(syncGlobalColor.get(), 0xFFE879F9, 2);
        }

        if (entity instanceof ItemEntity) {
            if (!items.get()) return 0;
            return GlobalColorModule.resolveArgb(syncGlobalColor.get(), 0xFFFACC15, 3);
        }

        if (entity instanceof Projectile || entity instanceof VehicleEntity) {
            if (!vehiclesAndProjectiles.get()) return 0;
            return GlobalColorModule.resolveArgb(syncGlobalColor.get(), 0xFF38BDF8, 4);
        }

        if (entity instanceof LivingEntity living) {
            if (living.isDeadOrDying()) return 0;
            boolean isHostile = (entity instanceof Enemy)
                    || entity.getType().getCategory() == MobCategory.MONSTER;
            if (isHostile) {
                return hostiles.get() ? GlobalColorModule.resolveArgb(syncGlobalColor.get(), 0xFFF97316, 1) : 0;
            } else {
                return passives.get() ? 0xFF4ADE80 : 0;
            }
        }
        return 0;
    }

    /**
     * Hooked in EntityRenderDispatcherMixin.extractEntity at RETURN for native 1.21 post-shader outline!
     */
    public void applyOutlineState(Entity entity, EntityRenderState state) {
        if (!usesShaderOutline() || state == null) return;
        int col = classifyEntityArgb(entity);
        if (col != 0) {
            state.outlineColor = col;
            renderedOutlineThisFrame = true;
        }
    }

    /**
     * Hooked in DebugRendererMixin.emitGizmos for smooth interpolated 3D through-wall boxes!
     */
    public void emitGizmos(float partialTick) {
        if (!isEnabled() || !style.get().use3d || mc.level == null || mc.player == null) return;
        int fillA = Math.max(0, Math.min(255, (int) Math.round(fillAlpha.get() * 255.0)));

        for (Entity e : mc.level.entitiesForRendering()) {
            int argb = classifyEntityArgb(e);
            if (argb == 0) continue;
            int rgb = argb & 0x00FFFFFF;

            double ix = Mth.lerp(partialTick, e.xo, e.getX());
            double iy = Mth.lerp(partialTick, e.yo, e.getY());
            double iz = Mth.lerp(partialTick, e.zo, e.getZ());
            AABB box = e.getBoundingBox().move(ix - e.getX(), iy - e.getY(), iz - e.getZ()).inflate(0.03);

            int strokeCol = 0xEE000000 | rgb;
            int fillCol = (fillA << 24) | rgb;
            Gizmos.cuboid(box, GizmoStyle.strokeAndFill(strokeCol, 1.8f, fillCol)).setAlwaysOnTop();
        }
    }

    /**
     * Hooked in GuiMixin.render for crisp 2D Tactical Corner Brackets + Info Badges + Smooth Tracer Rays!
     */
    public void render2d(GuiGraphics g, float partialTick) {
        if (!isEnabled() || mc.level == null || mc.player == null || mc.options.hideGui) return;
        boolean do2d = style.get().use2d;
        boolean doTracers = tracers.get();
        if (!do2d && !doTracers) return;

        IdleWallpaperModule idle = com.ye.mod.core.ModuleManager.get(IdleWallpaperModule.class);
        if (idle != null && idle.shouldHideHud()) return;

        int sw = g.guiWidth();
        int sh = g.guiHeight();
        float originX = sw * 0.5f;
        float originY = (tracerOrigin.get() == TracerOrigin.BOTTOM) ? (sh - 28.0f) : (sh * 0.5f);

        for (Entity e : mc.level.entitiesForRendering()) {
            int baseArgb = classifyEntityArgb(e);
            if (baseArgb == 0) continue;

            double ix = Mth.lerp(partialTick, e.xo, e.getX());
            double iy = Mth.lerp(partialTick, e.yo, e.getY());
            double iz = Mth.lerp(partialTick, e.zo, e.getZ());
            float halfW = Math.max(0.22f, e.getBbWidth() * 0.55f);
            float fullH = Math.max(0.35f, e.getBbHeight() + 0.08f);

            if (doTracers) {
                float[] mid = YeWorldFxRenderer.projectWorldToGui(ix, iy + fullH * 0.5, iz, sw, sh);
                if (mid != null) {
                    int tCol = baseArgb;
                    if (tracerDistColor.get() && !(e instanceof Player p && distinctFriends.get() && FriendManager.isFriend(p))) {
                        float dist = mc.player.distanceTo(e);
                        tCol = distanceGradientArgb(dist);
                    }
                    drawSmoothRay(g, originX, originY, mid[0], mid[1], (0xCC << 24) | (tCol & 0x00FFFFFF));
                }
            }

            if (do2d) {
                // Project 8 corners of entity bounding box to find crisp 2D screen rectangle
                float minSx = Float.MAX_VALUE, minSy = Float.MAX_VALUE;
                float maxSx = -Float.MAX_VALUE, maxSy = -Float.MAX_VALUE;
                int validPts = 0;

                double[] xs = {ix - halfW, ix + halfW};
                double[] ys = {iy - 0.04, iy + fullH};
                double[] zs = {iz - halfW, iz + halfW};
                for (double px : xs) {
                    for (double py : ys) {
                        for (double pz : zs) {
                            float[] pr = YeWorldFxRenderer.projectWorldToGui(px, py, pz, sw, sh);
                            if (pr != null) {
                                validPts++;
                                if (pr[0] < minSx) minSx = pr[0];
                                if (pr[0] > maxSx) maxSx = pr[0];
                                if (pr[1] < minSy) minSy = pr[1];
                                if (pr[1] > maxSy) maxSy = pr[1];
                            }
                        }
                    }
                }
                if (validPts < 2) continue;

                int x0 = Math.round(minSx);
                int y0 = Math.round(minSy);
                int x1 = Math.max(x0 + 6, Math.round(maxSx));
                int y1 = Math.max(y0 + 8, Math.round(maxSy));
                int bw = x1 - x0;
                int bh = y1 - y0;
                if (bw > sw * 0.85 || bh > sh * 0.90) continue;

                drawTacticalCorners(g, x0, y0, bw, bh, baseArgb);

                if (e instanceof LivingEntity liv && liv.getMaxHealth() > 0 && bh >= 10) {
                    float hpPct = Mth.clamp(liv.getHealth() / liv.getMaxHealth(), 0.0f, 1.0f);
                    int hpCol = hpPct > 0.55f ? 0xFF4ADE80 : (hpPct > 0.25f ? 0xFFFACC15 : 0xFFEF4444);
                    int barH = Math.max(1, Math.round(bh * hpPct));
                    g.fill(x0 - 4, y0 - 1, x0 - 1, y1 + 1, 0xAA050810);
                    g.fill(x0 - 3, y1 - barH, x0 - 2, y1, hpCol);
                }

                if (showInfoTag.get() && (e instanceof Player || mc.player.distanceToSqr(e) <= 3600.0)) {
                    String name = (e instanceof ItemEntity ie)
                            ? (ie.getItem().getHoverName().getString() + "×" + ie.getItem().getCount())
                            : e.getName().getString();
                    if (e instanceof Player p && distinctFriends.get() && FriendManager.isFriend(p)) {
                        name = ConfigManager.tr("[好友] ", "[F] ") + name;
                    }
                    String tag = String.format(Locale.ROOT, "%s %.0fm", name, mc.player.distanceTo(e));
                    var pose = g.pose();
                    pose.pushMatrix();
                    pose.translate((x0 + x1) * 0.5f, y0 - 3.0f);
                    pose.scale(0.75f, 0.75f);
                    int tw = mc.font.width(tag);
                    g.fill(-tw / 2 - 3, -10, tw / 2 + 3, 0, 0xAA090D16);
                    g.drawString(mc.font, tag, -tw / 2, -9, baseArgb, false);
                    pose.popMatrix();
                }
            }
        }
    }

    public static void drawTacticalCorners(GuiGraphics g, int x, int y, int w, int h, int col) {
        int armX = Math.max(3, Math.min(10, w / 3));
        int armY = Math.max(3, Math.min(10, h / 3));
        int shadow = 0xAA000000;

        // Subtle dark 1px backing for crisp contrast on bright snow/sky
        g.fill(x - 1, y - 1, x + armX + 1, y + 2, shadow);
        g.fill(x - 1, y - 1, x + 2, y + armY + 1, shadow);
        g.fill(x + w - armX - 1, y - 1, x + w + 1, y + 2, shadow);
        g.fill(x + w - 2, y - 1, x + w + 1, y + armY + 1, shadow);
        g.fill(x - 1, y + h - 2, x + armX + 1, y + h + 1, shadow);
        g.fill(x - 1, y + h - armY - 1, x + 2, y + h + 1, shadow);
        g.fill(x + w - armX - 1, y + h - 2, x + w + 1, y + h + 1, shadow);
        g.fill(x + w - 2, y + h - armY - 1, x + w + 1, y + h + 1, shadow);

        // Crisp colored corner brackets
        g.fill(x, y, x + armX, y + 1, col);
        g.fill(x, y, x + 1, y + armY, col);
        g.fill(x + w - armX, y, x + w, y + 1, col);
        g.fill(x + w - 1, y, x + w, y + armY, col);
        g.fill(x, y + h - 1, x + armX, y + h, col);
        g.fill(x, y + h - armY, x + 1, y + h, col);
        g.fill(x + w - armX, y + h - 1, x + w, y + h, col);
        g.fill(x + w - 1, y + h - armY, x + w, y + h, col);
    }

    private static void drawSmoothRay(GuiGraphics g, float x0, float y0, float x1, float y1, int argb) {
        float dx = x1 - x0;
        float dy = y1 - y0;
        float len = (float) Math.hypot(dx, dy);
        if (len < 2.0f) return;
        float ang = (float) Math.atan2(dy, dx);
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(x0, y0);
        pose.rotate(ang);
        g.fill(0, 0, Math.round(len), 1, argb);
        pose.popMatrix();
    }

    private static int distanceGradientArgb(float dist) {
        if (dist <= 15.0f) return 0xFFEF4444;
        if (dist <= 45.0f) return 0xFFFACC15;
        return 0xFF38BDF8;
    }
}
