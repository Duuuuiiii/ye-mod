package com.ye.mod.modules.world;

import com.ye.mod.core.Category;
import com.ye.mod.core.ConfigManager;
import com.ye.mod.core.Module;
import com.ye.mod.core.ModuleManager;
import com.ye.mod.core.setting.BoolSetting;
import com.ye.mod.modules.visual.IdleWallpaperModule;
import com.ye.mod.ui.AppleUiRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * WTHIT ("What The Hell Is That" / 我在看什么) crosshair inspector card.
 * Displays 3D block icon, localized block/entity name, coordinates, recommended tool, and mining progress!
 */
public class WthitModule extends Module {
    public final BoolSetting showCoords = addSetting(new BoolSetting(
            "showCoords", "显示坐标", "卡片副标题显示目标方块坐标", true
    ));

    public final BoolSetting showToolHint = addSetting(new BoolSetting(
            "showToolHint", "挖掘工具提示", "显示推荐挖掘工具与实时挖掘进度%", true
    ));

    public final BoolSetting showEntities = addSetting(new BoolSetting(
            "showEntities", "包含实体信息", "指向生物或实体时也显示信息卡片", true
    ));

    public WthitModule() {
        super("wthit", "方块信息", "WTHIT", "实时显示准星指向的方块图标、名称、工具与挖掘进度", Category.UTILITY);
        setEnabled(true, true);
    }

    public void render(GuiGraphics g) {
        if (!isEnabled()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.options.hideGui || mc.hitResult == null) return;
        IdleWallpaperModule idle = ModuleManager.get(IdleWallpaperModule.class);
        if (idle != null && idle.shouldHideHud()) return;

        ConfigManager.HudSlot slot = ConfigManager.HUD_SLOTS.get("hud_wthit");
        if (slot != null && !slot.enabled) return;

        int sw = g.guiWidth();
        int sh = g.guiHeight();

        if (mc.hitResult.getType() == HitResult.Type.BLOCK && mc.hitResult instanceof BlockHitResult bhr) {
            BlockPos pos = bhr.getBlockPos();
            BlockState state = mc.level.getBlockState(pos);
            if (state.isAir()) return;

            ItemStack icon = state.getBlock().asItem().getDefaultInstance();
            String title = state.getBlock().getName().getString();
            StringBuilder sub = new StringBuilder();
            if (showToolHint.get()) {
                sub.append(resolveToolTag(state));
            }
            if (showCoords.get()) {
                if (!sub.isEmpty()) sub.append(" · ");
                sub.append(pos.getX()).append(", ").append(pos.getY()).append(", ").append(pos.getZ());
            }

            int breakStage = (mc.gameMode != null && mc.gameMode.isDestroying()) ? mc.gameMode.getDestroyStage() : -1;
            int breakPct = breakStage >= 0 ? Math.min(100, (breakStage + 1) * 10) : -1;
            if (breakPct > 0 && showToolHint.get()) {
                sub.append(" · ").append(breakPct).append("%");
            }

            String subStr = sub.toString();
            int hasIcon = icon.isEmpty() ? 0 : 20;
            int w = Math.max(96, hasIcon + Math.max(mc.font.width(title), mc.font.width(subStr)) + 16);
            int h = subStr.isEmpty() ? 22 : 28;
            if (breakPct > 0) h += 5;

            int x = slot != null ? slot.resolveX(sw, w) : (sw - w) / 2;
            int y = slot != null ? slot.resolveY(sh, h) : 6;

            // Layered depth card
            AppleUiRenderer.drawRoundedRect(g, x + 1, y + 2, w, h, 6, 0x55000000);
            AppleUiRenderer.drawRoundedRect(g, x, y, w, h, 6, 0xDF0D111A);
            g.fill(x + 4, y, x + w - 4, y + 1, 0x38FFFFFF);
            g.renderOutline(x, y, w, h, 0x26FFFFFF);

            if (!icon.isEmpty()) {
                g.renderItem(icon, x + 5, y + (h - (breakPct > 0 ? 5 : 0) - 16) / 2);
            }
            int tx = x + 7 + hasIcon;
            g.drawString(mc.font, title, tx, y + 5, 0xFFF8FAFC, false);
            if (!subStr.isEmpty()) {
                g.drawString(mc.font, subStr, tx, y + 16, 0xFF94A3B8, false);
            }
            if (breakPct > 0) {
                int bx = x + 5, by = y + h - 5, bw = w - 10;
                g.fill(bx, by, bx + bw, by + 3, 0xFF1E293B);
                g.fill(bx, by, bx + Math.round(bw * (breakPct / 100.0f)), by + 3, ConfigManager.getAccentColor());
            }
        } else if (showEntities.get() && mc.hitResult.getType() == HitResult.Type.ENTITY && mc.hitResult instanceof EntityHitResult ehr) {
            Entity ent = ehr.getEntity();
            String title = ent.getDisplayName().getString();
            String subStr;
            if (ent instanceof LivingEntity le) {
                subStr = String.format("❤ %.1f / %.1f · %.1fm", le.getHealth(), le.getMaxHealth(), mc.player.distanceTo(ent));
            } else {
                String id = BuiltInRegistries.ENTITY_TYPE.getKey(ent.getType()).getPath();
                subStr = String.format("%s · %.1fm", id, mc.player.distanceTo(ent));
            }
            int w = Math.max(92, Math.max(mc.font.width(title), mc.font.width(subStr)) + 16);
            int h = 26;
            int x = slot != null ? slot.resolveX(sw, w) : (sw - w) / 2;
            int y = slot != null ? slot.resolveY(sh, h) : 6;

            AppleUiRenderer.drawRoundedRect(g, x + 1, y + 2, w, h, 6, 0x55000000);
            AppleUiRenderer.drawRoundedRect(g, x, y, w, h, 6, 0xDF0D111A);
            g.renderOutline(x, y, w, h, 0x26FFFFFF);
            g.drawString(mc.font, title, x + 8, y + 4, 0xFFF8FAFC, false);
            g.drawString(mc.font, subStr, x + 8, y + 15, 0xFF94A3B8, false);
        }
    }

    private static String resolveToolTag(BlockState state) {
        if (state.getDestroySpeed(null, BlockPos.ZERO) < 0) return "不可破坏";
        if (state.is(BlockTags.MINEABLE_WITH_PICKAXE)) return "镐";
        if (state.is(BlockTags.MINEABLE_WITH_AXE)) return "斧";
        if (state.is(BlockTags.MINEABLE_WITH_SHOVEL)) return "锹";
        if (state.is(BlockTags.MINEABLE_WITH_HOE)) return "锄";
        return "徒手/任意";
    }
}
