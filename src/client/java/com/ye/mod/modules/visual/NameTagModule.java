package com.ye.mod.modules.visual;

import com.ye.mod.core.Category;
import com.ye.mod.core.ConfigManager;
import com.ye.mod.core.FriendManager;
import com.ye.mod.core.Module;
import com.ye.mod.core.setting.BoolSetting;
import com.ye.mod.core.setting.ColorSetting;
import com.ye.mod.core.setting.DoubleSetting;
import com.ye.mod.render.YeWorldFxRenderer;
import com.ye.mod.ui.AppleUiRenderer;
import com.ye.mod.ui.YeHdFont;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * RusherHack-style Player NameTag Module (玩家名牌):
 * Smooth partialTick-interpolated projection (zero jitter!), crisp bordered tag with Health,
 * Totem Pops, Ping antenna + ms, Distance, Friend highlight, and Armor/Hand items.
 */
public class NameTagModule extends Module {
    public final BoolSetting showHealth = addSetting(new BoolSetting("showHealth", "显示血量数值", "附带吸收黄心总生命值", true).functionGroup());
    public final BoolSetting showPops = addSetting(new BoolSetting("showPops", "显示图腾Pop", "显示该玩家本局累计爆图腾次数", true).functionGroup());
    public final BoolSetting showPing = addSetting(new BoolSetting("showPing", "显示延迟(信号+ms)", "在名牌旁绘制信号格与毫秒延迟", true).functionGroup());
    public final BoolSetting showDistance = addSetting(new BoolSetting("showDistance", "显示距离米数", "显示与该玩家的精确距离", true).functionGroup());
    public final BoolSetting showArmor = addSetting(new BoolSetting("showArmor", "显示装备与手持", "在名牌上方渲染穿戴盔甲与主副手物品", true).functionGroup());
    public final BoolSetting ignoreSelf = addSetting(new BoolSetting("ignoreSelf", "忽略自己", "第三人称时不渲染自己头顶名牌", true).functionGroup());
    public final DoubleSetting scale = addSetting(new DoubleSetting("scale", "名牌缩放", "调节玩家名牌整体大小", 1.0, 0.65, 1.80, 0.05));
    public final ColorSetting borderColor = addSetting(new ColorSetting("borderColor", "边框颜色", "名牌外框默认颜色", 0xFF38BDF8));

    public NameTagModule() {
        super("name_tags", "玩家名牌", "NameTags", "显示玩家ID、血量、图腾Pop数、信号延迟、距离与装备", Category.VISUAL);
        setEnabled(true, true);
    }

    public void render2d(GuiGraphics g, float partialTick) {
        if (!isEnabled()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.options.hideGui) return;

        float sc = scale.get().floatValue();
        int sw = g.guiWidth(), sh = g.guiHeight();

        for (Player p : mc.level.players()) {
            if (p.isSpectator()) continue;
            if (ignoreSelf.get() && p == mc.player) continue;

            double ix = Mth.lerp(partialTick, p.xo, p.getX());
            double iy = Mth.lerp(partialTick, p.yo, p.getY()) + p.getBbHeight() + 0.42;
            double iz = Mth.lerp(partialTick, p.zo, p.getZ());

            float[] scr = YeWorldFxRenderer.projectWorldToGui(ix, iy, iz, sw, sh);
            if (scr == null) continue;

            boolean isFriend = FriendManager.isFriend(p);
            String name = p.getGameProfile().name();
            float hp = p.getHealth() + p.getAbsorptionAmount();
            int pops = HealthPlatesModule.getEntityPopCount(p.getId());
            double dist = Math.sqrt(mc.player.distanceToSqr(ix, Mth.lerp(partialTick, p.yo, p.getY()), iz));

            int ping = -1;
            if (mc.getConnection() != null) {
                PlayerInfo info = mc.getConnection().getPlayerInfo(p.getUUID());
                if (info != null) ping = info.getLatency();
            }

            StringBuilder sb = new StringBuilder();
            sb.append(name);
            if (showHealth.get()) sb.append(String.format(Locale.ROOT, " %.0fHP", hp));
            if (showPops.get() && pops > 0) sb.append(" -").append(pops);
            if (showDistance.get()) sb.append(String.format(Locale.ROOT, " %.0fm", dist));
            String label = sb.toString();

            float fSize = 8.2f * sc;
            float pingW = (showPing.get() && ping >= 0) ? (YeHdFont.widthF(ping + "ms", 7.4f * sc, false) + 14 * sc) : 0f;
            float textW = YeHdFont.widthF(label, fSize, true);
            int boxW = Math.round(textW + pingW + 12 * sc);
            int boxH = Math.round(14 * sc);
            int bx = Math.round(scr[0] - boxW * 0.5f);
            int by = Math.round(scr[1] - boxH);

            int bCol = isFriend ? 0xFF34D399 : borderColor.getRgb() | 0xFF000000;
            AppleUiRenderer.drawRoundedRect(g, bx, by, boxW, boxH, 3.5f * sc, 0xCC0A0E18);
            AppleUiRenderer.drawRoundedOutline(g, bx, by, boxW, boxH, 3.5f * sc, 1.0f, bCol);

            int nameCol = isFriend ? 0xFF34D399 : (hp <= 8.0f ? 0xFFEF4444 : (hp <= 14.0f ? 0xFFFACC15 : 0xFFF8FAFC));
            YeHdFont.draw(g, label, bx + 5 * sc, by + (boxH - fSize) * 0.45f, fSize, nameCol, true, true);

            if (showPing.get() && ping >= 0) {
                float px = bx + 7 * sc + textW;
                int sigCol = ping < 80 ? 0xFF4ADE80 : (ping < 180 ? 0xFFFACC15 : 0xFFEF4444);
                int bars = ping < 80 ? 3 : (ping < 180 ? 2 : 1);
                for (int b = 0; b < 3; b++) {
                    float bh = (3 + b * 2.5f) * sc;
                    float barX = px + b * 2.5f * sc;
                    float barY = by + boxH - 3.5f * sc - bh;
                    g.fill(Math.round(barX), Math.round(barY), Math.round(barX + 1.8f * sc), Math.round(barY + bh),
                            b < bars ? sigCol : 0x4464748B);
                }
                YeHdFont.draw(g, ping + "ms", px + 9 * sc, by + (boxH - 7.4f * sc) * 0.48f, 7.4f * sc, sigCol, false, false);
            }

            if (showArmor.get()) {
                List<ItemStack> gear = new ArrayList<>(6);
                ItemStack main = p.getMainHandItem();
                if (!main.isEmpty()) gear.add(main);
                for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                    ItemStack st = p.getItemBySlot(slot);
                    if (!st.isEmpty()) gear.add(st);
                }
                ItemStack off = p.getOffhandItem();
                if (!off.isEmpty()) gear.add(off);
                if (!gear.isEmpty()) {
                    float itemSc = 0.65f * sc;
                    float step = 11.5f * sc;
                    float gx0 = scr[0] - (gear.size() * step) * 0.5f;
                    float gy0 = by - 12.0f * sc;
                    for (int i = 0; i < gear.size(); i++) {
                        var pose = g.pose();
                        pose.pushMatrix();
                        pose.translate(gx0 + i * step, gy0);
                        pose.scale(itemSc, itemSc);
                        g.renderItem(gear.get(i), 0, 0);
                        pose.popMatrix();
                    }
                }
            }
        }
    }
}
