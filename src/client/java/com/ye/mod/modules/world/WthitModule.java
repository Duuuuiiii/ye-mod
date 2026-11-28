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
import net.minecraft.world.item.Items;
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

    private float smoothBreakRatio = 0.0f;
    private BlockPos lastBreakPos = null;

    public WthitModule() {
        super("wthit", "hud_wthit", "方块信息", "Block Info",
                "显示准星指向方块与生物的中文名称、钻石工具图标、平滑挖掘进度及坐标",
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
                if (showCoords.get()) {
                    sub.append(pos.getX()).append(", ").append(pos.getY()).append(", ").append(pos.getZ());
                }
                int breakStage = (mc.gameMode != null && mc.gameMode.isDestroying()) ? mc.gameMode.getDestroyStage() : -1;
                if (!pos.equals(lastBreakPos)) {
                    lastBreakPos = pos;
                    smoothBreakRatio = 0.0f;
                }
                float targetRatio = breakStage >= 0 ? Math.min(1.0f, (breakStage + 1) / 10.0f) : 0.0f;
                smoothBreakRatio += (targetRatio - smoothBreakRatio) * 0.28f;
                int breakPct = breakStage >= 0 ? Math.max(1, Math.min(100, Math.round(smoothBreakRatio * 100.0f))) : -1;
                if (breakPct > 0 && showToolHint.get()) {
                    if (!sub.isEmpty()) sub.append(" · ");
                    sub.append(breakPct).append("%");
                }

                String subStr = sub.toString();
                ItemStack toolIcon = showToolHint.get() ? resolveToolIcon(state) : ItemStack.EMPTY;
                float padX = 7.0f * sc;
                float iconSize = icon.isEmpty() ? 0.0f : 16.0f * sc;
                float iconGap = icon.isEmpty() ? 0.0f : 6.0f * sc;
                float toolW = toolIcon.isEmpty() ? 0.0f : 14.0f * sc;
                float textBlockW = Math.max(YeHdFont.widthF(title, fTitle, true) + toolW, YeHdFont.widthF(subStr, fSub, false));
                int w = Math.round(Math.max(88 * sc, padX * 2.0f + iconSize + iconGap + textBlockW));
                int h = Math.round((subStr.isEmpty() ? 22 : 28) * sc + (breakPct > 0 ? 5 * sc : 0));
                int x = slot != null ? slot.resolveX(sw, w) : (sw - w) / 2;
                int y = slot != null ? slot.resolveY(sh, h) : 6;

                drawHudCard(g, x, y, w, h, editMode);
                float contentCenterY = y + (h - (breakPct > 0 ? 4.0f * sc : 0.0f)) * 0.5f;
                if (!icon.isEmpty()) {
                    var pose = g.pose();
                    pose.pushMatrix();
                    pose.translate(x + padX, contentCenterY - iconSize * 0.5f);
                    pose.scale(sc, sc);
                    g.renderItem(icon, 0, 0);
                    pose.popMatrix();
                }
                float tx = x + padX + iconSize + iconGap;
                if (subStr.isEmpty()) {
                    drawHudText(g, title, tx, contentCenterY - fTitle * 0.52f, fTitle, resolvePrimaryColor(), true);
                } else {
                    float totalTextH = fTitle + fSub + 2.0f * sc;
                    float topTy = contentCenterY - totalTextH * 0.5f;
                    drawHudText(g, title, tx, topTy, fTitle, resolvePrimaryColor(), true);
                    drawHudText(g, subStr, tx, topTy + fTitle + 1.5f * sc, fSub, 0xFF94A3B8, false);
                }
                if (breakPct > 0) {
                    float bx = x + 6 * sc, by = y + h - 5 * sc, bw = w - 12 * sc;
                    AppleUiRenderer.drawRoundedRect(g, bx, by, bw, 3 * sc, 1.5f * sc, 0xFF1E293B);
                    AppleUiRenderer.drawRoundedRect(g, bx, by, bw * Math.max(0.02f, smoothBreakRatio), 3 * sc, 1.5f * sc, resolvePrimaryColor());
                }
                if (!toolIcon.isEmpty()) {
                    var tp = g.pose();
                    tp.pushMatrix();
                    tp.translate(x + w - padX - 11.0f * sc, y + 3.5f * sc);
                    tp.scale(0.68f * sc, 0.68f * sc);
                    g.renderItem(toolIcon, 0, 0);
                    tp.popMatrix();
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

    private static ItemStack resolveToolIcon(BlockState state) {
        if (state.is(BlockTags.MINEABLE_WITH_PICKAXE)) return new ItemStack(Items.DIAMOND_PICKAXE);
        if (state.is(BlockTags.MINEABLE_WITH_AXE)) return new ItemStack(Items.DIAMOND_AXE);
        if (state.is(BlockTags.MINEABLE_WITH_SHOVEL)) return new ItemStack(Items.DIAMOND_SHOVEL);
        if (state.is(BlockTags.MINEABLE_WITH_HOE)) return new ItemStack(Items.DIAMOND_HOE);
        return ItemStack.EMPTY;
    }

    private static String resolveToolTag(BlockState s) {
        if (s.is(BlockTags.MINEABLE_WITH_PICKAXE)) return ConfigManager.tr("镐", "Pickaxe");
        if (s.is(BlockTags.MINEABLE_WITH_AXE)) return ConfigManager.tr("斧", "Axe");
        if (s.is(BlockTags.MINEABLE_WITH_SHOVEL)) return ConfigManager.tr("锹", "Shovel");
        if (s.is(BlockTags.MINEABLE_WITH_HOE)) return ConfigManager.tr("锄", "Hoe");
        return ConfigManager.tr("徒手", "Hand");
    }
}
