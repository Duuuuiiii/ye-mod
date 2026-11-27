package com.ye.mod.modules.world;

import com.ye.mod.core.ConfigManager;
import com.ye.mod.core.YeZhCnDict;
import com.ye.mod.core.setting.BoolSetting;
import com.ye.mod.modules.hud.HudElementModule;
import com.ye.mod.ui.AppleUiRenderer;
import com.ye.mod.ui.YeHdFont;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.Locale;

/**
 * Crosshair Block/Entity Inspector HUD Sub-Module (方块信息).
 */
public class WthitModule extends HudElementModule {
    public final BoolSetting showCoords = addSetting(new BoolSetting(
            "showCoords", "显示目标坐标", "副标题显示指向方块的精确坐标", true
    ).functionGroup());

    public final BoolSetting showToolHint = addSetting(new BoolSetting(
            "showToolHint", "挖掘工具与进度", "显示推荐挖掘工具与实时破坏百分比", true
    ).functionGroup());

    public final BoolSetting showEntities = addSetting(new BoolSetting(
            "showEntities", "包含生物实体", "准星指向生物或实体时显示血量与距离", true
    ).functionGroup());

    public WthitModule() {
        super("wthit", "hud_wthit", "方块信息", "Block Info",
                "显示准星指向方块与生物的中文名称、推荐工具、挖掘进度及坐标",
                false, true, ColorStyle.ICE_CYAN, 10);
        addResetPositionButton();
    }

    @Override
    public void renderElement(GuiGraphics g, int sw, int sh, boolean editMode) {
        ConfigManager.HudSlot slot = getSlot();
        float sc = scale.get().floatValue();
        float fTitle = 8.8f * sc;
        float fSub = 7.8f * sc;

        if (mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.BLOCK && mc.hitResult instanceof BlockHitResult bhr) {
            BlockPos pos = bhr.getBlockPos();
            BlockState state = mc.level.getBlockState(pos);
            if (!state.isAir()) {
                ItemStack icon = state.getBlock().asItem().getDefaultInstance();
                String title = YeZhCnDict.blockName(state.getBlock());
                StringBuilder sub = new StringBuilder();
                if (showToolHint.get()) sub.append(resolveToolTag(state));
                if (showCoords.get()) {
                    if (!sub.isEmpty()) sub.append(" · ");
                    sub.append(pos.getX()).append(", ").append(pos.getY()).append(", ").append(pos.getZ());
                }
                int breakStage = (mc.gameMode != null && mc.gameMode.isDestroying()) ? mc.gameMode.getDestroyStage() : -1;
                int breakPct = breakStage >= 0 ? Math.min(100, (breakStage + 1) * 10) : -1;
                if (breakPct > 0 && showToolHint.get()) sub.append(" · ").append(breakPct).append("%");

                String subStr = sub.toString();
                float iconW = icon.isEmpty() ? 0 : 20 * sc;
                int w = Math.round(Math.max(96 * sc, iconW + Math.max(YeHdFont.widthF(title, fTitle, true), YeHdFont.widthF(subStr, fSub, false)) + 16 * sc));
                int h = Math.round((subStr.isEmpty() ? 22 : 28) * sc + (breakPct > 0 ? 5 * sc : 0));
                int x = slot != null ? slot.resolveX(sw, w) : (sw - w) / 2;
                int y = slot != null ? slot.resolveY(sh, h) : 6;

                drawHudCard(g, x, y, w, h, editMode);
                if (!icon.isEmpty()) {
                    var pose = g.pose();
                    pose.pushMatrix();
                    pose.translate(x + 5 * sc, y + 5 * sc);
                    pose.scale(sc, sc);
                    g.renderItem(icon, 0, 0);
                    pose.popMatrix();
                }
                float tx = x + 7 * sc + iconW;
                drawHudText(g, title, tx, y + 4.5f * sc, fTitle, resolvePrimaryColor(), true);
                if (!subStr.isEmpty()) {
                    drawHudText(g, subStr, tx, y + 15.5f * sc, fSub, 0xFF94A3B8, false);
                }
                if (breakPct > 0) {
                    float bx = x + 6 * sc, by = y + h - 5 * sc, bw = w - 12 * sc;
                    AppleUiRenderer.drawRoundedRect(g, bx, by, bw, 3 * sc, 1.5f * sc, 0xFF1E293B);
                    AppleUiRenderer.drawRoundedRect(g, bx, by, bw * (breakPct / 100.0f), 3 * sc, 1.5f * sc, resolvePrimaryColor());
                }
                return;
            }
        }

        if (showEntities.get() && mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.ENTITY && mc.hitResult instanceof EntityHitResult ehr) {
            Entity ent = ehr.getEntity();
            String title = YeZhCnDict.entityTypeName(ent.getType());
            String subStr = (ent instanceof LivingEntity le)
                    ? String.format(Locale.ROOT, "%.1f / %.1f HP · %.1fm", le.getHealth(), le.getMaxHealth(), mc.player.distanceTo(ent))
                    : String.format(Locale.ROOT, "%.1fm", mc.player.distanceTo(ent));
            int w = Math.round(Math.max(92 * sc, Math.max(YeHdFont.widthF(title, fTitle, true), YeHdFont.widthF(subStr, fSub, false)) + 16 * sc));
            int h = Math.round(26 * sc);
            int x = slot != null ? slot.resolveX(sw, w) : (sw - w) / 2;
            int y = slot != null ? slot.resolveY(sh, h) : 6;

            drawHudCard(g, x, y, w, h, editMode);
            drawHudText(g, title, x + 8 * sc, y + 4 * sc, fTitle, resolvePrimaryColor(), true);
            drawHudText(g, subStr, x + 8 * sc, y + 14.5f * sc, fSub, 0xFF94A3B8, false);
            return;
        }

        if (editMode) {
            int w = Math.round(108 * sc), h = Math.round(26 * sc);
            int x = slot != null ? slot.resolveX(sw, w) : (sw - w) / 2;
            int y = slot != null ? slot.resolveY(sh, h) : 6;
            drawHudCard(g, x, y, w, h, true);
            drawHudText(g, ConfigManager.tr("草方块", "Grass Block"), x + 8 * sc, y + 4 * sc, fTitle, resolvePrimaryColor(), true);
            drawHudText(g, "0, 64, 0", x + 8 * sc, y + 14.5f * sc, fSub, 0xFF94A3B8, false);
        }
    }

    private static String resolveToolTag(BlockState s) {
        if (s.is(BlockTags.MINEABLE_WITH_PICKAXE)) return ConfigManager.tr("镐", "Pickaxe");
        if (s.is(BlockTags.MINEABLE_WITH_AXE)) return ConfigManager.tr("斧", "Axe");
        if (s.is(BlockTags.MINEABLE_WITH_SHOVEL)) return ConfigManager.tr("锹", "Shovel");
        if (s.is(BlockTags.MINEABLE_WITH_HOE)) return ConfigManager.tr("锄", "Hoe");
        return ConfigManager.tr("徒手", "Hand");
    }
}
