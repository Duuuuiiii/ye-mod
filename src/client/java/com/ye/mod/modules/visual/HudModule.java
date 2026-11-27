package com.ye.mod.modules.visual;

import com.ye.mod.core.Category;
import com.ye.mod.core.ConfigManager;
import com.ye.mod.core.Module;
import com.ye.mod.core.ModuleManager;
import com.ye.mod.core.setting.ActionSetting;
import com.ye.mod.core.setting.BoolSetting;
import com.ye.mod.core.setting.DoubleSetting;
import com.ye.mod.core.setting.EnumSetting;
import com.ye.mod.ui.AppleUiRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * RusherHack-inspired Full HUD System:
 * - Draggable & edge/center snapping HUD slots in F10 mouse mode
 * - Global HUD scale + dedicated Potion HUD scale
 * - Potion HUD in Table mode (default) or Progress-Bar mode
 * - Dual-dimension Coords, Speed (b/s), Time & Online timer, FPS/Ping/Dir,
 *   Armor durability, PvP Item counters, 3x9 Inventory preview, Nearby Drops, Module ArrayList!
 */
public class HudModule extends Module {
    public enum PotionStyle {
        TABLE("表模式"),
        BAR("条模式");

        public final String label;
        PotionStyle(String label) { this.label = label; }
    }

    public enum HudPalette {
        RUSHER("紫粉", 0xFFE879F9),
        THEME("主题色", 0xFF0A84FF),
        CYAN("青", 0xFF38BDF8),
        GOLD("金", 0xFFFACC15),
        GREEN("绿", 0xFF4ADE80),
        WHITE("白", 0xFFF8FAFC);

        public final String label;
        public final int argb;
        HudPalette(String label, int argb) {
            this.label = label;
            this.argb = argb;
        }
    }

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };
    private static final Item[] PVP_TRACKED_ITEMS = {
            Items.TOTEM_OF_UNDYING, Items.ENCHANTED_GOLDEN_APPLE, Items.GOLDEN_APPLE,
            Items.EXPERIENCE_BOTTLE, Items.FIREWORK_ROCKET, Items.END_CRYSTAL
    };

    public final EnumSetting<HudPalette> palette = addSetting(new EnumSetting<>(
            "palette", "配色", "紫粉(Rusher) / 跟随主题 / 青 / 金 / 绿 / 白", HudPalette.RUSHER
    ));

    public final DoubleSetting hudScale = addSetting(new DoubleSetting(
            "hudScale", "HUD大小", "整体 HUD 缩放比例", 1.0, 0.65, 1.60, 0.05
    ));

    public final EnumSetting<PotionStyle> potionStyle = addSetting(new EnumSetting<>(
            "potionStyle", "药水样式", "表模式(默认) / 条模式", PotionStyle.TABLE
    ));

    public final DoubleSetting potionScale = addSetting(new DoubleSetting(
            "potionScale", "药水大小", "药水效果 HUD 独立缩放比例", 1.0, 0.65, 1.60, 0.05
    ));

    public final BoolSetting showPotions = addSetting(new BoolSetting("showPotions", "药水效果", "显示自定义药水表/条面板", true));
    public final BoolSetting hideVanillaPotions = addSetting(new BoolSetting("hideVanillaPotions", "隐藏原版药水", "隐藏右上角原版方形药水图标", true));
    public final BoolSetting showCoords = addSetting(new BoolSetting("showCoords", "双维坐标", "显示主世界/下界同步 XYZ 坐标", true));
    public final BoolSetting coordsMultiLine = addSetting(new BoolSetting("coordsMultiLine", "多行坐标", "Rusher风格三行对齐坐标", true).visible(showCoords::get));
    public final BoolSetting showSpeed = addSetting(new BoolSetting("showSpeed", "移动速度", "实时显示 Speed (b/s)", true));
    public final BoolSetting showTimeStats = addSetting(new BoolSetting("showTimeStats", "时间与在线", "显示系统时间与在线时长", true));
    public final BoolSetting showFpsPing = addSetting(new BoolSetting("showFpsPing", "帧率与延迟", "显示 FPS、Ping 与朝向", true));
    public final BoolSetting showArmor = addSetting(new BoolSetting("showArmor", "装备耐久", "显示全身护甲图标与耐久百分比", true));
    public final BoolSetting showPvpItems = addSetting(new BoolSetting("showPvpItems", "物资计数", "统计背包图腾/金苹果/经验瓶/烟花数量", true));
    public final BoolSetting showInventory = addSetting(new BoolSetting("showInventory", "背包预览", "在屏幕上实时透视自身 3×9 背包格", false));
    public final BoolSetting showNearbyDrops = addSetting(new BoolSetting("showNearbyDrops", "附近掉落物", "列出周围地面掉落物名称数量与距离", true));
    public final BoolSetting showModuleList = addSetting(new BoolSetting("showModuleList", "开启模块列表", "显示当前已启用模块列表", false));
    public final BoolSetting cardBackground = addSetting(new BoolSetting("cardBackground", "半透底板", "给文字类 HUD 增加微透深色圆角底板", false));

    private final long sessionStartMs = System.currentTimeMillis();
    private final Map<String, Integer> maxEffectDurations = new HashMap<>();

    public HudModule() {
        super("hud", "HUD", "HUD", "Rusher风格全能HUD（支持F10自由拖拽吸附、大小缩放、药水表/条切换）", Category.UTILITY);
        setEnabled(true, true);
        addSetting(new ActionSetting("resetLayout", "重置全部位置", "将所有 HUD 组件恢复默认吸附位置", () -> {
            ConfigManager.resetHudSlotsDefault();
            syncSlotFlags();
            ConfigManager.saveAsync();
        }));
    }

    public boolean shouldHideVanillaPotions() {
        return isEnabled() && showPotions.get() && hideVanillaPotions.get();
    }

    private int primaryColor() {
        HudPalette p = palette.get();
        return p == HudPalette.THEME ? ConfigManager.getAccentColor() : p.argb;
    }

    public void syncSlotFlags() {
        setSlotEnabled("hud_potions", showPotions.get());
        setSlotEnabled("hud_coords", showCoords.get());
        setSlotEnabled("hud_speed", showSpeed.get());
        setSlotEnabled("hud_time", showTimeStats.get());
        setSlotEnabled("hud_info", showFpsPing.get());
        setSlotEnabled("hud_armor", showArmor.get());
        setSlotEnabled("hud_items", showPvpItems.get());
        setSlotEnabled("hud_inv", showInventory.get());
        setSlotEnabled("hud_drops", showNearbyDrops.get());
        setSlotEnabled("hud_modules", showModuleList.get());
    }

    private static void setSlotEnabled(String id, boolean v) {
        ConfigManager.HudSlot s = ConfigManager.HUD_SLOTS.get(id);
        if (s != null) s.enabled = v;
    }

    public void render(GuiGraphics g, boolean editMode) {
        if (!isEnabled() || mc.options.hideGui || mc.player == null || mc.level == null) return;
        IdleWallpaperModule idle = ModuleManager.get(IdleWallpaperModule.class);
        if (!editMode && idle != null && idle.shouldHideHud()) return;

        syncSlotFlags();
        int sw = g.guiWidth();
        int sh = g.guiHeight();
        int col = primaryColor();
        float sc = hudScale.get().floatValue();
        float potSc = potionScale.get().floatValue();

        if (showTimeStats.get()) renderTimeStats(g, sw, sh, col, sc, editMode);
        if (showFpsPing.get()) renderFpsPing(g, sw, sh, col, sc, editMode);
        if (showSpeed.get()) renderSpeed(g, sw, sh, col, sc, editMode);
        if (showCoords.get()) renderCoords(g, sw, sh, col, sc, editMode);
        if (showPotions.get()) renderPotions(g, sw, sh, col, potSc, editMode);
        if (showArmor.get()) renderArmor(g, sw, sh, sc, editMode);
        if (showPvpItems.get()) renderPvpItems(g, sw, sh, sc, editMode);
        if (showInventory.get()) renderInventoryGrid(g, sw, sh, sc, editMode);
        if (showNearbyDrops.get()) renderNearbyDrops(g, sw, sh, sc, editMode);
        if (showModuleList.get()) renderModuleList(g, sw, sh, col, sc, editMode);
    }

    private void renderTimeStats(GuiGraphics g, int sw, int sh, int col, float sc, boolean edit) {
        ConfigManager.HudSlot slot = ConfigManager.HUD_SLOTS.get("hud_time");
        if (slot == null) return;
        String l1 = "Time: " + LocalTime.now().format(TIME_FMT);
        long sec = Math.max(0L, (System.currentTimeMillis() - sessionStartMs) / 1000L);
        String l2 = String.format("Online: %02d:%02d:%02d", sec / 3600, (sec % 3600) / 60, sec % 60);
        int rawW = Math.max(mc.font.width(l1), mc.font.width(l2)) + 6;
        int rawH = 22;
        int w = Math.round(rawW * sc), h = Math.round(rawH * sc);
        int x = slot.resolveX(sw, w), y = slot.resolveY(sh, h);

        beginScaled(g, x, y, sc, rawW, rawH, edit, slot.zhName);
        if (cardBackground.get()) AppleUiRenderer.drawRoundedRect(g, 0, 0, rawW, rawH, 4, 0x77090D16);
        g.drawString(mc.font, l1, 2, 2, col, true);
        g.drawString(mc.font, l2, 2, 12, col, true);
        endScaled(g);
    }

    private void renderFpsPing(GuiGraphics g, int sw, int sh, int col, float sc, boolean edit) {
        ConfigManager.HudSlot slot = ConfigManager.HUD_SLOTS.get("hud_info");
        if (slot == null) return;
        int ping = 0;
        if (mc.getConnection() != null) {
            PlayerInfo info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
            if (info != null) ping = info.getLatency();
        }
        String dir = cardinalDir(mc.player.getYRot());
        String txt = String.format("FPS %d · Ping %dms · %s", mc.getFps(), ping, dir);
        int rawW = mc.font.width(txt) + 6, rawH = 13;
        int w = Math.round(rawW * sc), h = Math.round(rawH * sc);
        int x = slot.resolveX(sw, w), y = slot.resolveY(sh, h);

        beginScaled(g, x, y, sc, rawW, rawH, edit, slot.zhName);
        if (cardBackground.get()) AppleUiRenderer.drawRoundedRect(g, 0, 0, rawW, rawH, 4, 0x77090D16);
        g.drawString(mc.font, txt, 2, 2, col, true);
        endScaled(g);
    }

    private void renderSpeed(GuiGraphics g, int sw, int sh, int col, float sc, boolean edit) {
        ConfigManager.HudSlot slot = ConfigManager.HUD_SLOTS.get("hud_speed");
        if (slot == null) return;
        double dx = mc.player.getX() - mc.player.xo;
        double dz = mc.player.getZ() - mc.player.zo;
        double bps = Math.hypot(dx, dz) * 20.0;
        String txt = String.format(Locale.ROOT, "Speed: %.2f b/s", bps);
        int rawW = mc.font.width(txt) + 6, rawH = 13;
        int w = Math.round(rawW * sc), h = Math.round(rawH * sc);
        int x = slot.resolveX(sw, w), y = slot.resolveY(sh, h);

        beginScaled(g, x, y, sc, rawW, rawH, edit, slot.zhName);
        if (cardBackground.get()) AppleUiRenderer.drawRoundedRect(g, 0, 0, rawW, rawH, 4, 0x77090D16);
        g.drawString(mc.font, txt, 2, 2, col, true);
        endScaled(g);
    }

    private void renderCoords(GuiGraphics g, int sw, int sh, int col, float sc, boolean edit) {
        ConfigManager.HudSlot slot = ConfigManager.HUD_SLOTS.get("hud_coords");
        if (slot == null) return;
        int px = mc.player.getBlockX(), py = mc.player.getBlockY(), pz = mc.player.getBlockZ();
        boolean isNether = mc.level.dimension() == Level.NETHER;
        boolean isOw = mc.level.dimension() == Level.OVERWORLD;
        int altX = isNether ? px * 8 : Math.floorDiv(px, 8);
        int altZ = isNether ? pz * 8 : Math.floorDiv(pz, 8);
        boolean showAlt = isNether || isOw;

        if (coordsMultiLine.get()) {
            String lx = showAlt ? String.format("X %d (%d)", px, altX) : ("X " + px);
            String ly = "Y " + py;
            String lz = showAlt ? String.format("Z %d (%d)", pz, altZ) : ("Z " + pz);
            int rawW = Math.max(mc.font.width(lx), Math.max(mc.font.width(ly), mc.font.width(lz))) + 6;
            int rawH = 32;
            int w = Math.round(rawW * sc), h = Math.round(rawH * sc);
            int x = slot.resolveX(sw, w), y = slot.resolveY(sh, h);

            beginScaled(g, x, y, sc, rawW, rawH, edit, slot.zhName);
            if (cardBackground.get()) AppleUiRenderer.drawRoundedRect(g, 0, 0, rawW, rawH, 4, 0x77090D16);
            g.drawString(mc.font, lx, 2, 2, col, true);
            g.drawString(mc.font, ly, 2, 12, col, true);
            g.drawString(mc.font, lz, 2, 22, col, true);
            endScaled(g);
        } else {
            String single = showAlt
                    ? String.format("XYZ %d, %d, %d (%d, %d)", px, py, pz, altX, altZ)
                    : String.format("XYZ %d, %d, %d", px, py, pz);
            int rawW = mc.font.width(single) + 6, rawH = 13;
            int w = Math.round(rawW * sc), h = Math.round(rawH * sc);
            int x = slot.resolveX(sw, w), y = slot.resolveY(sh, h);

            beginScaled(g, x, y, sc, rawW, rawH, edit, slot.zhName);
            if (cardBackground.get()) AppleUiRenderer.drawRoundedRect(g, 0, 0, rawW, rawH, 4, 0x77090D16);
            g.drawString(mc.font, single, 2, 2, col, true);
            endScaled(g);
        }
    }

    private void renderPotions(GuiGraphics g, int sw, int sh, int accent, float sc, boolean edit) {
        ConfigManager.HudSlot slot = ConfigManager.HUD_SLOTS.get("hud_potions");
        if (slot == null) return;

        Collection<MobEffectInstance> active = mc.player.getActiveEffects();
        if (active.isEmpty() && !edit) return;

        List<MobEffectInstance> sorted = new ArrayList<>(active);
        sorted.sort(Comparator.comparingInt(MobEffectInstance::getDuration).reversed());
        boolean isTable = (potionStyle.get() == PotionStyle.TABLE);

        if (sorted.isEmpty()) {
            // Preview placeholder in F10 edit mode
            int rawW = 112, rawH = 26;
            int w = Math.round(rawW * sc), h = Math.round(rawH * sc);
            int x = slot.resolveX(sw, w), y = slot.resolveY(sh, h);
            beginScaled(g, x, y, sc, rawW, rawH, true, slot.zhName);
            AppleUiRenderer.drawRoundedRect(g, 0, 0, rawW, rawH, 5, 0xCC0D111A);
            g.drawString(mc.font, "速度 II", 6, 8, 0xFF38BDF8, false);
            g.drawString(mc.font, "01:30", rawW - 34, 8, 0xFFCBD5E1, false);
            endScaled(g);
            return;
        }

        if (isTable) {
            // TABLE MODE (默认表模式): Clean multi-column table with status indicator, effect + level, and aligned mm:ss column
            int maxNameW = 48;
            for (MobEffectInstance eff : sorted) {
                String label = effectLabel(eff);
                maxNameW = Math.max(maxNameW, mc.font.width(label));
            }
            int rawW = maxNameW + 52;
            int rowH = 14;
            int rawH = 16 + sorted.size() * rowH;
            int w = Math.round(rawW * sc), h = Math.round(rawH * sc);
            int x = slot.resolveX(sw, w), y = slot.resolveY(sh, h);

            beginScaled(g, x, y, sc, rawW, rawH, edit, slot.zhName);
            AppleUiRenderer.drawRoundedRect(g, 1, 2, rawW, rawH, 6, 0x55000000);
            AppleUiRenderer.drawRoundedRect(g, 0, 0, rawW, rawH, 6, 0xDC0D111A);
            g.renderOutline(0, 0, rawW, rawH, 0x26FFFFFF);
            g.drawString(mc.font, "状态效果", 6, 4, accent, false);
            g.drawString(mc.font, "剩余", rawW - 6 - mc.font.width("剩余"), 4, 0xFF94A3B8, false);
            g.fill(4, 14, rawW - 4, 15, 0x22FFFFFF);

            int ry = 16;
            for (MobEffectInstance eff : sorted) {
                int eCol = 0xFF000000 | (eff.getEffect().value().getColor() & 0x00FFFFFF);
                String name = effectLabel(eff);
                String dur = formatDuration(eff);
                g.fill(6, ry + 4, 9, ry + 9, eCol);
                g.drawString(mc.font, name, 13, ry + 3, 0xFFF8FAFC, false);
                g.drawString(mc.font, dur, rawW - 6 - mc.font.width(dur), ry + 3, 0xFFCBD5E1, false);
                ry += rowH;
            }
            endScaled(g);
        } else {
            // BAR MODE (条模式): Sleek countdown progress bar per effect
            int rawW = 122;
            int rowH = 19;
            int rawH = 4 + sorted.size() * rowH;
            int w = Math.round(rawW * sc), h = Math.round(rawH * sc);
            int x = slot.resolveX(sw, w), y = slot.resolveY(sh, h);

            beginScaled(g, x, y, sc, rawW, rawH, edit, slot.zhName);
            AppleUiRenderer.drawRoundedRect(g, 0, 0, rawW, rawH, 6, 0xD40D111A);
            g.renderOutline(0, 0, rawW, rawH, 0x26FFFFFF);

            int ry = 3;
            for (MobEffectInstance eff : sorted) {
                String key = eff.getEffect().getRegisteredName();
                int curDur = eff.getDuration();
                int maxDur = Math.max(curDur, maxEffectDurations.getOrDefault(key, Math.max(200, curDur)));
                maxEffectDurations.put(key, maxDur);
                float pct = eff.isInfiniteDuration() ? 1.0f : Math.max(0.02f, Math.min(1.0f, curDur / (float) maxDur));

                int eCol = 0xFF000000 | (eff.getEffect().value().getColor() & 0x00FFFFFF);
                String name = mc.font.plainSubstrByWidth(effectLabel(eff), 78);
                String dur = formatDuration(eff);
                g.drawString(mc.font, name, 5, ry + 1, 0xFFF8FAFC, false);
                g.drawString(mc.font, dur, rawW - 5 - mc.font.width(dur), ry + 1, 0xFFCBD5E1, false);
                g.fill(5, ry + 12, rawW - 5, ry + 16, 0xFF1E293B);
                g.fill(5, ry + 12, 5 + Math.round((rawW - 10) * pct), ry + 16, eCol);
                ry += rowH;
            }
            endScaled(g);
        }
    }

    private void renderArmor(GuiGraphics g, int sw, int sh, float sc, boolean edit) {
        ConfigManager.HudSlot slot = ConfigManager.HUD_SLOTS.get("hud_armor");
        if (slot == null) return;
        int rawW = 78, rawH = 24;
        int w = Math.round(rawW * sc), h = Math.round(rawH * sc);
        int x = slot.resolveX(sw, w), y = slot.resolveY(sh, h);

        beginScaled(g, x, y, sc, rawW, rawH, edit, slot.zhName);
        int ix = 2;
        for (EquipmentSlot eq : ARMOR_SLOTS) {
            ItemStack st = mc.player.getItemBySlot(eq);
            if (!st.isEmpty()) {
                g.renderItem(st, ix, 6);
                g.renderItemDecorations(mc.font, st, ix, 6);
                if (st.isDamageableItem()) {
                    int max = st.getMaxDamage();
                    int rem = Math.max(0, max - st.getDamageValue());
                    int pct = Math.round((rem * 100.0f) / Math.max(1, max));
                    int dCol = pct > 55 ? 0xFF4ADE80 : (pct > 25 ? 0xFFFACC15 : 0xFFEF4444);
                    String pTxt = String.valueOf(pct);
                    var pose = g.pose();
                    pose.pushMatrix();
                    pose.translate(ix + 8, 1);
                    pose.scale(0.65f, 0.65f);
                    g.drawString(mc.font, pTxt, -mc.font.width(pTxt) / 2, 0, dCol, true);
                    pose.popMatrix();
                }
            }
            ix += 19;
        }
        endScaled(g);
    }

    private void renderPvpItems(GuiGraphics g, int sw, int sh, float sc, boolean edit) {
        ConfigManager.HudSlot slot = ConfigManager.HUD_SLOTS.get("hud_items");
        if (slot == null) return;
        Inventory inv = mc.player.getInventory();
        List<int[]> counts = new ArrayList<>();
        List<ItemStack> icons = new ArrayList<>();
        for (Item item : PVP_TRACKED_ITEMS) {
            int total = 0;
            for (int i = 0; i < 36; i++) {
                ItemStack s = inv.getItem(i);
                if (!s.isEmpty() && s.is(item)) total += s.getCount();
            }
            ItemStack off = mc.player.getOffhandItem();
            if (!off.isEmpty() && off.is(item)) total += off.getCount();
            if (total > 0) {
                icons.add(new ItemStack(item));
                counts.add(new int[]{total});
            }
        }
        if (icons.isEmpty() && edit) {
            icons.add(new ItemStack(Items.TOTEM_OF_UNDYING));
            counts.add(new int[]{8});
        }
        if (icons.isEmpty()) return;

        int rawW = icons.size() * 20 + 4, rawH = 20;
        int w = Math.round(rawW * sc), h = Math.round(rawH * sc);
        int x = slot.resolveX(sw, w), y = slot.resolveY(sh, h);

        beginScaled(g, x, y, sc, rawW, rawH, edit, slot.zhName);
        if (cardBackground.get()) AppleUiRenderer.drawRoundedRect(g, 0, 0, rawW, rawH, 4, 0x88090D16);
        for (int i = 0; i < icons.size(); i++) {
            int ix = 2 + i * 20;
            g.renderItem(icons.get(i), ix, 2);
            String cStr = String.valueOf(counts.get(i)[0]);
            g.drawString(mc.font, cStr, ix + 17 - mc.font.width(cStr), 10, 0xFFF8FAFC, true);
        }
        endScaled(g);
    }

    private void renderInventoryGrid(GuiGraphics g, int sw, int sh, float sc, boolean edit) {
        ConfigManager.HudSlot slot = ConfigManager.HUD_SLOTS.get("hud_inv");
        if (slot == null) return;
        int rawW = 9 * 18 + 6, rawH = 3 * 18 + 6;
        int w = Math.round(rawW * sc), h = Math.round(rawH * sc);
        int x = slot.resolveX(sw, w), y = slot.resolveY(sh, h);

        beginScaled(g, x, y, sc, rawW, rawH, edit, slot.zhName);
        AppleUiRenderer.drawRoundedRect(g, 0, 0, rawW, rawH, 5, 0x99090D16);
        g.renderOutline(0, 0, rawW, rawH, 0x33FFFFFF);

        Inventory inv = mc.player.getInventory();
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int idx = 9 + row * 9 + col;
                int sx = 3 + col * 18;
                int sy = 3 + row * 18;
                g.fill(sx, sy, sx + 16, sy + 16, 0x331E293B);
                ItemStack st = inv.getItem(idx);
                if (!st.isEmpty()) {
                    g.renderItem(st, sx, sy);
                    g.renderItemDecorations(mc.font, st, sx, sy);
                }
            }
        }
        endScaled(g);
    }

    private void renderNearbyDrops(GuiGraphics g, int sw, int sh, float sc, boolean edit) {
        ConfigManager.HudSlot slot = ConfigManager.HUD_SLOTS.get("hud_drops");
        if (slot == null) return;

        record DropGroup(String name, int count, double minDist) {}
        Map<String, DropGroup> grouped = new LinkedHashMap<>();
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof ItemEntity ie && !ie.getItem().isEmpty()) {
                double d = mc.player.distanceTo(ie);
                if (d > 128.0) continue;
                String nm = ie.getItem().getHoverName().getString();
                int cnt = ie.getItem().getCount();
                DropGroup prev = grouped.get(nm);
                if (prev == null) {
                    grouped.put(nm, new DropGroup(nm, cnt, d));
                } else {
                    grouped.put(nm, new DropGroup(nm, prev.count + cnt, Math.min(prev.minDist, d)));
                }
            }
        }

        List<DropGroup> list = new ArrayList<>(grouped.values());
        list.sort(Comparator.comparingDouble(DropGroup::minDist));
        if (list.size() > 6) list = list.subList(0, 6);
        if (list.isEmpty() && !edit) return;

        List<String> lines = new ArrayList<>();
        if (list.isEmpty()) {
            lines.add("掉落物示例 ×4 (12.5 m)");
        } else {
            for (DropGroup dg : list) {
                lines.add(String.format(Locale.ROOT, "%s ×%d (%.1f m)", dg.name, dg.count, dg.minDist));
            }
        }

        int rawW = 64;
        for (String s : lines) rawW = Math.max(rawW, mc.font.width(s) + 6);
        int rawH = lines.size() * 11 + 2;
        int w = Math.round(rawW * sc), h = Math.round(rawH * sc);
        int x = slot.resolveX(sw, w), y = slot.resolveY(sh, h);

        beginScaled(g, x, y, sc, rawW, rawH, edit, slot.zhName);
        if (cardBackground.get()) AppleUiRenderer.drawRoundedRect(g, 0, 0, rawW, rawH, 4, 0x77090D16);
        int ry = 2;
        for (String line : lines) {
            g.drawString(mc.font, line, 2, ry, 0xFFFACC15, true);
            ry += 11;
        }
        endScaled(g);
    }

    private void renderModuleList(GuiGraphics g, int sw, int sh, int col, float sc, boolean edit) {
        ConfigManager.HudSlot slot = ConfigManager.HUD_SLOTS.get("hud_modules");
        if (slot == null) return;
        List<Module> active = ModuleManager.getAll().stream()
                .filter(m -> m.isEnabled() && m != this)
                .sorted((a, b) -> Integer.compare(
                        mc.font.width(ConfigManager.english ? b.getSubtitle() : b.getName()),
                        mc.font.width(ConfigManager.english ? a.getSubtitle() : a.getName())))
                .toList();
        if (active.isEmpty() && !edit) return;

        int rawW = 56;
        for (Module m : active) {
            String nm = ConfigManager.english ? m.getSubtitle() : m.getName();
            rawW = Math.max(rawW, mc.font.width(nm) + 6);
        }
        int rawH = Math.max(14, active.size() * 11 + 2);
        int w = Math.round(rawW * sc), h = Math.round(rawH * sc);
        int x = slot.resolveX(sw, w), y = slot.resolveY(sh, h);
        boolean rightAlign = (slot.anchorX == 2);

        beginScaled(g, x, y, sc, rawW, rawH, edit, slot.zhName);
        int ry = 2;
        for (Module m : active) {
            String nm = ConfigManager.english ? m.getSubtitle() : m.getName();
            int tx = rightAlign ? (rawW - 2 - mc.font.width(nm)) : 2;
            g.drawString(mc.font, nm, tx, ry, col, true);
            ry += 11;
        }
        endScaled(g);
    }

    private void beginScaled(GuiGraphics g, int x, int y, float sc, int rawW, int rawH, boolean edit, String label) {
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(sc, sc);
        if (edit) {
            g.fill(0, 0, rawW, rawH, 0x380A84FF);
            g.renderOutline(0, 0, rawW, rawH, 0xCC38BDF8);
        }
    }

    private static void endScaled(GuiGraphics g) {
        g.pose().popMatrix();
    }

    private static String effectLabel(MobEffectInstance eff) {
        String base = eff.getEffect().value().getDisplayName().getString();
        int amp = eff.getAmplifier();
        if (amp <= 0) return base;
        String roman = switch (amp) {
            case 1 -> " II";
            case 2 -> " III";
            case 3 -> " IV";
            case 4 -> " V";
            default -> " " + (amp + 1);
        };
        return base + roman;
    }

    private static String formatDuration(MobEffectInstance eff) {
        if (eff.isInfiniteDuration()) return "∞";
        int totalSec = Math.max(0, eff.getDuration() / 20);
        return String.format("%02d:%02d", totalSec / 60, totalSec % 60);
    }

    private static String cardinalDir(float yaw) {
        float norm = ((yaw % 360.0f) + 360.0f) % 360.0f;
        if (norm >= 315 || norm < 45) return "S +Z";
        if (norm < 135) return "W -X";
        if (norm < 225) return "N -Z";
        return "E +X";
    }
}
