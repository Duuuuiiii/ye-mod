package com.ye.mod.modules.hud;

import com.ye.mod.core.ConfigManager;
import com.ye.mod.core.Module;
import com.ye.mod.core.ModuleManager;
import com.ye.mod.core.setting.BoolSetting;
import com.ye.mod.core.setting.DoubleSetting;
import com.ye.mod.core.setting.EnumSetting;
import com.ye.mod.ui.YeHdFont;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;

import java.awt.Color;
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
 * 10 Crisp Native-DPI Vector HUD Sub-Modules (+ WthitModule = 11 total in the HUD tab!).
 * Right-clicking any HUD widget on screen in F10 mode opens its dedicated settings page directly!
 */
public final class HudElements {
    private HudElements() {}

    // ==================== 1. POTIONS HUD (药水效果) ====================
    public static final class PotionsHud extends HudElementModule {
        public enum Style {
            TABLE("表模式"),
            BAR("条模式");
            public final String label;
            Style(String label) { this.label = label; }
        }

        public final EnumSetting<Style> style = addSetting(new EnumSetting<>(
                "style", "呈现样式", "表模式(默认对齐表格) / 条模式(动态倒计时条)", Style.TABLE
        ));
        public final BoolSetting hideVanilla = addSetting(new BoolSetting(
                "hideVanilla", "隐藏原版图标", "隐藏屏幕右上角原版方形药水框", true
        ));
        public final BoolSetting warnExpiring = addSetting(new BoolSetting(
                "warnExpiring", "临期闪烁提示", "剩余不足 8 秒时时间数字变红提醒", true
        ));
        public final BoolSetting compact = addSetting(new BoolSetting(
                "compact", "紧凑行高", "缩小行间距容纳更多药水状态", false
        ));

        private final Map<String, Integer> maxEffectDurations = new HashMap<>();

        public PotionsHud() {
            super("hud_potions_mod", "hud_potions", "药水面板", "Potions HUD",
                    "高清矢量药水状态表/进度条，支持独立缩放与原版图标隐藏",
                    true, true, ColorStyle.ICE_CYAN, 0);
            addResetPositionButton();
        }

        public boolean shouldHideVanillaPotions() {
            return isEnabled() && hideVanilla.get();
        }

        @Override
        public void renderElement(GuiGraphics g, int sw, int sh, boolean editMode) {
            ConfigManager.HudSlot slot = getSlot();
            if (slot == null) return;

            Collection<MobEffectInstance> active = mc.player.getActiveEffects();
            if (active.isEmpty() && !editMode) return;

            float sc = scale.get().floatValue();
            float fSize = 8.6f * sc;
            int accent = resolvePrimaryColor();

            List<MobEffectInstance> sorted = new ArrayList<>(active);
            sorted.sort(Comparator.comparingInt(MobEffectInstance::getDuration).reversed());

            if (sorted.isEmpty()) {
                int w = Math.round(118 * sc), h = Math.round(28 * sc);
                int x = slot.resolveX(sw, w), y = slot.resolveY(sh, h);
                drawHudCard(g, x, y, w, h, true);
                drawHudText(g, ConfigManager.tr("迅捷 II", "Speed II"), x + 7 * sc, y + 7 * sc, fSize, accent, false);
                drawHudText(g, "01:30", x + w - 34 * sc, y + 7 * sc, fSize, 0xFFCBD5E1, false);
                return;
            }

            if (style.get() == Style.TABLE) {
                float maxNameW = 48 * sc;
                for (MobEffectInstance eff : sorted) {
                    maxNameW = Math.max(maxNameW, YeHdFont.widthF(effectLabel(eff), fSize, false));
                }
                int w = Math.round(maxNameW + 56 * sc);
                float rowStep = (compact.get() ? 12.0f : 14.5f) * sc;
                float hdrH = 16.5f * sc;
                int h = Math.round(hdrH + sorted.size() * rowStep + 4 * sc);
                int x = slot.resolveX(sw, w), y = slot.resolveY(sh, h);

                drawHudCard(g, x, y, w, h, editMode);
                String hdrL = ConfigManager.tr("状态效果", "Effects");
                String hdrR = ConfigManager.tr("剩余", "Time");
                drawHudText(g, hdrL, x + 7 * sc, y + 3.5f * sc, fSize * 0.94f, accent, true);
                drawHudText(g, hdrR, x + w - 7 * sc - YeHdFont.widthF(hdrR, fSize * 0.94f, false), y + 3.5f * sc, fSize * 0.94f, 0xFF94A3B8, false);
                g.fill(Math.round(x + 6 * sc), Math.round(y + hdrH - 1), Math.round(x + w - 6 * sc), Math.round(y + hdrH), 0x22FFFFFF);

                float ry = y + hdrH + 2 * sc;
                for (MobEffectInstance eff : sorted) {
                    int eCol = 0xFF000000 | (eff.getEffect().value().getColor() & 0x00FFFFFF);
                    String name = effectLabel(eff);
                    String dur = formatDuration(eff);
                    boolean low = warnExpiring.get() && !eff.isInfiniteDuration() && eff.getDuration() <= 160;
                    int durCol = low && ((System.currentTimeMillis() / 250L) % 2 == 0) ? 0xFFF87171 : 0xFFCBD5E1;

                    com.ye.mod.ui.AppleUiRenderer.drawCircle(g, x + 6.5f * sc, ry + 3.5f * sc, 4.5f * sc, eCol);
                    drawHudText(g, name, x + 14 * sc, ry + 1 * sc, fSize, 0xFFF8FAFC, false);
                    drawHudText(g, dur, x + w - 7 * sc - YeHdFont.widthF(dur, fSize, false), ry + 1 * sc, fSize, durCol, false);
                    ry += rowStep;
                }
            } else {
                int w = Math.round(132 * sc);
                float rowStep = (compact.get() ? 17.5f : 20.0f) * sc;
                int h = Math.round(6 * sc + sorted.size() * rowStep);
                int x = slot.resolveX(sw, w), y = slot.resolveY(sh, h);

                drawHudCard(g, x, y, w, h, editMode);
                float ry = y + 4 * sc;
                for (MobEffectInstance eff : sorted) {
                    String key = eff.getEffect().getRegisteredName();
                    int curDur = eff.getDuration();
                    int maxDur = Math.max(curDur, maxEffectDurations.getOrDefault(key, Math.max(200, curDur)));
                    maxEffectDurations.put(key, maxDur);
                    float pct = eff.isInfiniteDuration() ? 1.0f : Math.max(0.03f, Math.min(1.0f, curDur / (float) maxDur));

                    int eCol = 0xFF000000 | (eff.getEffect().value().getColor() & 0x00FFFFFF);
                    String name = YeHdFont.clipToWidth(effectLabel(eff), fSize, false, 84 * sc);
                    String dur = formatDuration(eff);
                    drawHudText(g, name, x + 6 * sc, ry, fSize, 0xFFF8FAFC, false);
                    drawHudText(g, dur, x + w - 6 * sc - YeHdFont.widthF(dur, fSize, false), ry, fSize, 0xFFCBD5E1, false);

                    float bx = x + 6 * sc, by = ry + 12 * sc, bw = w - 12 * sc, bh = Math.max(2.5f, 3.2f * sc);
                    com.ye.mod.ui.AppleUiRenderer.drawRoundedRect(g, bx, by, bw, bh, bh * 0.5f, 0xFF1E293B);
                    com.ye.mod.ui.AppleUiRenderer.drawRoundedRect(g, bx, by, bw * pct, bh, bh * 0.5f, eCol);
                    ry += rowStep;
                }
            }
        }
    }

    // ==================== 2. COORDS HUD (双维坐标) ====================
    public static final class CoordsHud extends HudElementModule {
        public final BoolSetting multiLine = addSetting(new BoolSetting(
                "multiLine", "三行对齐模式", "开启为X/Y/Z三行排布，关闭为单行紧凑排布", true
        ));
        public final BoolSetting showNetherSync = addSetting(new BoolSetting(
                "showNetherSync", "主世界↔下界换算", "括号内同步显示对应的×8或÷8跨维度坐标", true
        ));
        public final BoolSetting showChunk = addSetting(new BoolSetting(
                "showChunk", "显示区块坐标", "附带显示当前所在 Chunk (X, Z)", false
        ));
        public final BoolSetting showBiome = addSetting(new BoolSetting(
                "showBiome", "显示当前群系", "底部附带显示脚下生物群系名称", false
        ));

        public CoordsHud() {
            super("hud_coords_mod", "hud_coords", "双维坐标", "Coords HUD",
                    "高清矢量 XYZ 坐标，自带主世界↔下界×8同步换算、区块与群系显示",
                    true, false, ColorStyle.RUSHER_PINK, 1);
            addResetPositionButton();
        }

        @Override
        public void renderElement(GuiGraphics g, int sw, int sh, boolean editMode) {
            ConfigManager.HudSlot slot = getSlot();
            if (slot == null) return;

            float sc = scale.get().floatValue();
            float fSize = 8.8f * sc;
            int col = resolvePrimaryColor();

            int px = mc.player.getBlockX(), py = mc.player.getBlockY(), pz = mc.player.getBlockZ();
            boolean isNether = mc.level.dimension() == Level.NETHER;
            boolean isOw = mc.level.dimension() == Level.OVERWORLD;
            int altX = isNether ? px * 8 : Math.floorDiv(px, 8);
            int altZ = isNether ? pz * 8 : Math.floorDiv(pz, 8);
            boolean canAlt = showNetherSync.get() && (isNether || isOw);

            List<String> lines = new ArrayList<>();
            if (multiLine.get()) {
                lines.add(canAlt ? String.format("X  %d  §7(%d)", px, altX) : String.format("X  %d", px));
                lines.add(String.format("Y  %d", py));
                lines.add(canAlt ? String.format("Z  %d  §7(%d)", pz, altZ) : String.format("Z  %d", pz));
            } else {
                lines.add(canAlt
                        ? String.format("XYZ  %d, %d, %d  §7(%d, %d)", px, py, pz, altX, altZ)
                        : String.format("XYZ  %d, %d, %d", px, py, pz));
            }
            if (showChunk.get()) {
                lines.add(ConfigManager.tr("§7区块: ", "§7Chunk: ") + (px >> 4) + ", " + (pz >> 4));
            }
            if (showBiome.get()) {
                Holder<Biome> b = mc.level.getBiome(mc.player.blockPosition());
                String bId = b.unwrapKey().map(k -> k.identifier().getPath()).orElse("plains");
                lines.add("§7" + bId);
            }

            float maxW = 42 * sc;
            for (String ln : lines) maxW = Math.max(maxW, YeHdFont.widthF(ln, fSize, false));
            float lineH = 11.5f * sc;
            int w = Math.round(maxW + 10 * sc);
            int h = Math.round(lines.size() * lineH + 5 * sc);
            int x = slot.resolveX(sw, w), y = slot.resolveY(sh, h);

            drawHudCard(g, x, y, w, h, editMode);
            float ry = y + 2.5f * sc;
            for (String ln : lines) {
                drawHudText(g, ln, x + 5 * sc, ry, fSize, col, false);
                ry += lineH;
            }
        }
    }

    // ==================== 3. SPEED HUD (移动速度) ====================
    public static final class SpeedHud extends HudElementModule {
        public enum SpeedUnit {
            BPS("格/秒", 20.0, "格/秒", "b/s"),
            KMH("千米/时", 72.0, "km/h", "km/h"),
            BPT("格/刻", 1.0, "格/刻", "b/t");

            public final String label;
            final double mult;
            final String zhUnit;
            final String enUnit;

            SpeedUnit(String label, double mult, String zhUnit, String enUnit) {
                this.label = label;
                this.mult = mult;
                this.zhUnit = zhUnit;
                this.enUnit = enUnit;
            }
        }

        public final EnumSetting<SpeedUnit> unit = addSetting(new EnumSetting<>(
                "unit", "速度单位", "格/秒 · 千米/时 · 格/刻", SpeedUnit.BPS
        ));
        public final BoolSetting includeVertical = addSetting(new BoolSetting(
                "includeVertical", "计入垂直升降", "鞘翅俯冲爬升时将Y轴速度一并计入合速度", false
        ));
        public final BoolSetting showPeak = addSetting(new BoolSetting(
                "showPeak", "显示最高峰值", "附带记录近期最高冲刺/飞行速度", false
        ));

        private double peakBpt = 0.0;
        private long peakTimeMs = 0L;

        public SpeedHud() {
            super("hud_speed_mod", "hud_speed", "移动速度", "Speed HUD",
                    "实时高精度移速监控，支持多单位切换、垂直速度与峰值记录",
                    true, false, ColorStyle.RUSHER_PINK, 2);
            addResetPositionButton();
        }

        @Override
        public void renderElement(GuiGraphics g, int sw, int sh, boolean editMode) {
            ConfigManager.HudSlot slot = getSlot();
            if (slot == null) return;

            double dx = mc.player.getX() - mc.player.xo;
            double dy = includeVertical.get() ? (mc.player.getY() - mc.player.yo) : 0.0;
            double dz = mc.player.getZ() - mc.player.zo;
            double bpt = Math.sqrt(dx * dx + dy * dy + dz * dz);

            long now = System.currentTimeMillis();
            if (bpt >= peakBpt || now - peakTimeMs > 8000L) {
                peakBpt = bpt;
                peakTimeMs = now;
            }

            SpeedUnit u = unit.get();
            double val = bpt * u.mult;
            String uStr = ConfigManager.english ? u.enUnit : u.zhUnit;
            String txt = ConfigManager.tr("移速: ", "Speed: ") + String.format(Locale.ROOT, "%.2f %s", val, uStr);
            if (showPeak.get()) {
                txt += String.format(Locale.ROOT, " §7(峰值 %.1f)", peakBpt * u.mult);
            }

            float sc = scale.get().floatValue();
            float fSize = 8.8f * sc;
            int w = Math.round(YeHdFont.widthF(txt, fSize, false) + 10 * sc);
            int h = Math.round(15 * sc);
            int x = slot.resolveX(sw, w), y = slot.resolveY(sh, h);

            drawHudCard(g, x, y, w, h, editMode);
            drawHudText(g, txt, x + 5 * sc, y + 2.5f * sc, fSize, resolvePrimaryColor(), false);
        }
    }

    // ==================== 4. TIME & ONLINE STATS HUD (时间与在线) ====================
    public static final class TimeHud extends HudElementModule {
        private static final DateTimeFormatter FMT_24 = DateTimeFormatter.ofPattern("HH:mm:ss");
        private static final DateTimeFormatter FMT_12 = DateTimeFormatter.ofPattern("hh:mm:ss a", Locale.ENGLISH);

        public final BoolSetting showClock = addSetting(new BoolSetting("showClock", "显示现实时间", "显示当前电脑系统时钟", true));
        public final BoolSetting clock12h = addSetting(new BoolSetting("clock12h", "12小时制", "使用 AM/PM 12小时制时间格式", false).visible(showClock::get));
        public final BoolSetting showOnline = addSetting(new BoolSetting("showOnline", "显示在线时长", "统计本次启动游戏累计游玩时长", true));
        public final BoolSetting showWorldDay = addSetting(new BoolSetting("showWorldDay", "显示世界天数", "显示当前存档/服务器游戏内天数", false));

        private final long sessionStartMs = System.currentTimeMillis();

        public TimeHud() {
            super("hud_time_mod", "hud_time", "时间统计", "Time HUD",
                    "显示系统时钟、本次在线计时与世界天数",
                    true, false, ColorStyle.RUSHER_PINK, 3);
            addResetPositionButton();
        }

        @Override
        public void renderElement(GuiGraphics g, int sw, int sh, boolean editMode) {
            ConfigManager.HudSlot slot = getSlot();
            if (slot == null) return;

            List<String> lines = new ArrayList<>();
            if (showClock.get()) {
                String t = LocalTime.now().format(clock12h.get() ? FMT_12 : FMT_24);
                lines.add(ConfigManager.tr("时间: ", "Time: ") + t);
            }
            if (showOnline.get()) {
                long sec = Math.max(0L, (System.currentTimeMillis() - sessionStartMs) / 1000L);
                lines.add(String.format(ConfigManager.tr("在线: %02d:%02d:%02d", "Online: %02d:%02d:%02d"),
                        sec / 3600, (sec % 3600) / 60, sec % 60));
            }
            if (showWorldDay.get()) {
                long day = mc.level.getDayTime() / 24000L;
                lines.add(ConfigManager.tr("天数: 第 " + day + " 天", "Day: " + day));
            }
            if (lines.isEmpty()) lines.add(ConfigManager.tr("时间统计", "Time HUD"));

            float sc = scale.get().floatValue();
            float fSize = 8.8f * sc;
            float maxW = 48 * sc;
            for (String ln : lines) maxW = Math.max(maxW, YeHdFont.widthF(ln, fSize, false));
            float lineH = 11.5f * sc;
            int w = Math.round(maxW + 10 * sc);
            int h = Math.round(lines.size() * lineH + 5 * sc);
            int x = slot.resolveX(sw, w), y = slot.resolveY(sh, h);

            drawHudCard(g, x, y, w, h, editMode);
            int col = resolvePrimaryColor();
            float ry = y + 2.5f * sc;
            for (String ln : lines) {
                drawHudText(g, ln, x + 5 * sc, ry, fSize, col, false);
                ry += lineH;
            }
        }
    }

    // ==================== 5. FPS & PING HUD (帧率与延迟) ====================
    public static final class FpsPingHud extends HudElementModule {
        public final BoolSetting showFps = addSetting(new BoolSetting("showFps", "显示帧率", "实时显示当前 FPS", true));
        public final BoolSetting showPing = addSetting(new BoolSetting("showPing", "显示网络延迟", "实时读取当前服务器 Ping 毫秒数", true));
        public final BoolSetting showDirection = addSetting(new BoolSetting("showDirection", "显示方位角", "显示玩家当前朝向（东南西北与正负轴）", true));
        public final BoolSetting showMem = addSetting(new BoolSetting("showMem", "显示内存占用", "显示 JVM 已用内存百分比", false));

        public FpsPingHud() {
            super("hud_info_mod", "hud_info", "帧率与延迟", "FPS & Ping",
                    "实时显示高清帧率、网络延迟、朝向轴线与内存占用",
                    true, false, ColorStyle.RUSHER_PINK, 4);
            addResetPositionButton();
        }

        @Override
        public void renderElement(GuiGraphics g, int sw, int sh, boolean editMode) {
            ConfigManager.HudSlot slot = getSlot();
            if (slot == null) return;

            List<String> parts = new ArrayList<>();
            if (showFps.get()) {
                parts.add((ConfigManager.english ? "FPS " : "帧率 ") + mc.getFps());
            }
            if (showPing.get()) {
                int ping = 0;
                if (mc.getConnection() != null) {
                    PlayerInfo info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
                    if (info != null) ping = info.getLatency();
                }
                parts.add((ConfigManager.english ? "Ping " : "延迟 ") + ping + "ms");
            }
            if (showDirection.get()) {
                parts.add(cardinalDir(mc.player.getYRot()));
            }
            if (showMem.get()) {
                Runtime rt = Runtime.getRuntime();
                long pct = Math.round(((rt.totalMemory() - rt.freeMemory()) * 100.0) / Math.max(1L, rt.maxMemory()));
                parts.add((ConfigManager.english ? "Mem " : "内存 ") + pct + "%");
            }
            String txt = parts.isEmpty() ? "FPS" : String.join(" · ", parts);

            float sc = scale.get().floatValue();
            float fSize = 8.8f * sc;
            int w = Math.round(YeHdFont.widthF(txt, fSize, false) + 10 * sc);
            int h = Math.round(15 * sc);
            int x = slot.resolveX(sw, w), y = slot.resolveY(sh, h);

            drawHudCard(g, x, y, w, h, editMode);
            drawHudText(g, txt, x + 5 * sc, y + 2.5f * sc, fSize, resolvePrimaryColor(), false);
        }
    }

    // ==================== 6. ARMOR DURABILITY HUD (装备耐久) ====================
    public static final class ArmorHud extends HudElementModule {
        public enum DurabilityMode {
            PERCENT("百分比%"),
            RAW_POINTS("剩余点数"),
            ICON_ONLY("仅耐久条");
            public final String label;
            DurabilityMode(String label) { this.label = label; }
        }

        private static final EquipmentSlot[] ARMOR_SLOTS = {
                EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
        };

        public final BoolSetting horizontal = addSetting(new BoolSetting("horizontal", "横向排列", "开启为横向一排，关闭为纵向列表", true));
        public final BoolSetting showHands = addSetting(new BoolSetting("showHands", "包含主副手", "一并展示主手武器与副手物品耐久", false));
        public final EnumSetting<DurabilityMode> durMode = addSetting(new EnumSetting<>(
                "durMode", "数字格式", "百分比% / 剩余点数 / 仅原版耐久条", DurabilityMode.PERCENT
        ));
        public final BoolSetting lowBlink = addSetting(new BoolSetting("lowBlink", "低耐久闪烁", "护甲或鞘翅耐久低于 20% 时红色闪烁警示", true));

        public ArmorHud() {
            super("hud_armor_mod", "hud_armor", "装备耐久", "Armor HUD",
                    "护甲与主副手实时耐久监控，支持横纵排布、点数/百分比与低耐久预警",
                    true, false, ColorStyle.EMERALD, 5);
            addResetPositionButton();
        }

        @Override
        public void renderElement(GuiGraphics g, int sw, int sh, boolean editMode) {
            ConfigManager.HudSlot slot = getSlot();
            if (slot == null) return;

            List<ItemStack> stacks = new ArrayList<>();
            for (EquipmentSlot eq : ARMOR_SLOTS) {
                ItemStack s = mc.player.getItemBySlot(eq);
                if (!s.isEmpty()) stacks.add(s);
            }
            if (showHands.get()) {
                if (!mc.player.getMainHandItem().isEmpty()) stacks.add(mc.player.getMainHandItem());
                if (!mc.player.getOffhandItem().isEmpty()) stacks.add(mc.player.getOffhandItem());
            }
            if (stacks.isEmpty() && editMode) {
                stacks.add(new ItemStack(Items.NETHERITE_CHESTPLATE));
                stacks.add(new ItemStack(Items.ELYTRA));
            }
            if (stacks.isEmpty()) return;

            float sc = scale.get().floatValue();
            boolean horiz = horizontal.get();
            int count = stacks.size();
            int w = horiz ? Math.round((count * 20 + 6) * sc) : Math.round(48 * sc);
            int h = horiz ? Math.round(26 * sc) : Math.round((count * 19 + 6) * sc);
            int x = slot.resolveX(sw, w), y = slot.resolveY(sh, h);

            drawHudCard(g, x, y, w, h, editMode);

            var pose = g.pose();
            pose.pushMatrix();
            pose.translate(x, y);
            pose.scale(sc, sc);
            for (int i = 0; i < count; i++) {
                ItemStack st = stacks.get(i);
                int ix = horiz ? (3 + i * 20) : 3;
                int iy = horiz ? 8 : (3 + i * 19);
                g.renderItem(st, ix, iy);
                g.renderItemDecorations(mc.font, st, ix, iy);
            }
            pose.popMatrix();

            if (durMode.get() != DurabilityMode.ICON_ONLY) {
                float fSize = 7.0f * sc;
                for (int i = 0; i < count; i++) {
                    ItemStack st = stacks.get(i);
                    if (!st.isDamageableItem()) continue;
                    int max = st.getMaxDamage();
                    int rem = Math.max(0, max - st.getDamageValue());
                    int pct = Math.round((rem * 100.0f) / Math.max(1, max));
                    int dCol = pct > 55 ? 0xFF4ADE80 : (pct > 22 ? 0xFFFACC15 : 0xFFEF4444);
                    if (pct <= 20 && lowBlink.get() && ((System.currentTimeMillis() / 220L) % 2 == 0)) {
                        dCol = 0xFFFFFFFF;
                    }
                    String txt = (durMode.get() == DurabilityMode.PERCENT) ? (pct + "%") : String.valueOf(rem);
                    if (horiz) {
                        float cx = x + (3 + i * 20 + 8) * sc;
                        YeHdFont.drawCentered(g, txt, cx, y + 1 * sc, fSize, dCol, textShadow.get(), true);
                    } else {
                        drawHudText(g, txt, x + 22 * sc, y + (7 + i * 19) * sc, fSize * 1.1f, dCol, true);
                    }
                }
            }
        }
    }

    // ==================== 7. PVP ITEMS COUNTER HUD (物资计数) ====================
    public static final class PvpItemsHud extends HudElementModule {
        public final BoolSetting countTotem = addSetting(new BoolSetting("countTotem", "统计不死图腾", "统计全身不死图腾", true));
        public final BoolSetting countGapple = addSetting(new BoolSetting("countGapple", "统计金苹果", "统计附魔金苹果与金苹果", true));
        public final BoolSetting countRocket = addSetting(new BoolSetting("countRocket", "统计烟花火箭", "统计飞行烟花火箭", true));
        public final BoolSetting countXp = addSetting(new BoolSetting("countXp", "统计附魔之瓶", "统计修甲经验瓶", true));
        public final BoolSetting countCrystal = addSetting(new BoolSetting("countCrystal", "统计末影水晶", "统计末影水晶数量", true));
        public final BoolSetting countPearl = addSetting(new BoolSetting("countPearl", "统计末影珍珠", "统计传送珍珠数量", false));
        public final BoolSetting countObsidian = addSetting(new BoolSetting("countObsidian", "统计黑曜石", "统计背包黑曜石", false));
        public final BoolSetting twoRows = addSetting(new BoolSetting("twoRows", "双排网格布局", "物品较多时自动换为两排紧凑网格", false));
        public final BoolSetting showZero = addSetting(new BoolSetting("showZero", "零库存常显", "数量为 0 时仍保留图标并标红 0", false));

        public PvpItemsHud() {
            super("hud_items_mod", "hud_items", "物资计数", "PvP Items HUD",
                    "可自选物资种类的战斗库存计数器（图腾/金果/烟花/经验瓶/水晶/珍珠/黑曜石）",
                    true, true, ColorStyle.PURE_WHITE, 6);
            addResetPositionButton();
        }

        private record Tracked(Item iconItem, int total) {}

        @Override
        public void renderElement(GuiGraphics g, int sw, int sh, boolean editMode) {
            ConfigManager.HudSlot slot = getSlot();
            if (slot == null) return;

            List<Tracked> list = new ArrayList<>();
            addTrackedItem(list, countTotem.get(), Items.TOTEM_OF_UNDYING);
            if (countGapple.get()) {
                int ench = countInv(Items.ENCHANTED_GOLDEN_APPLE);
                int norm = countInv(Items.GOLDEN_APPLE);
                if (ench + norm > 0 || showZero.get()) {
                    list.add(new Tracked(ench > 0 ? Items.ENCHANTED_GOLDEN_APPLE : Items.GOLDEN_APPLE, ench + norm));
                }
            }
            addTrackedItem(list, countRocket.get(), Items.FIREWORK_ROCKET);
            addTrackedItem(list, countXp.get(), Items.EXPERIENCE_BOTTLE);
            addTrackedItem(list, countCrystal.get(), Items.END_CRYSTAL);
            addTrackedItem(list, countPearl.get(), Items.ENDER_PEARL);
            addTrackedItem(list, countObsidian.get(), Items.OBSIDIAN);

            if (list.isEmpty() && editMode) {
                list.add(new Tracked(Items.TOTEM_OF_UNDYING, 9));
                list.add(new Tracked(Items.FIREWORK_ROCKET, 64));
            }
            if (list.isEmpty()) return;

            float sc = scale.get().floatValue();
            int cols = (twoRows.get() && list.size() > 3) ? ((list.size() + 1) / 2) : list.size();
            int rows = (list.size() + cols - 1) / cols;
            int w = Math.round((cols * 21 + 6) * sc);
            int h = Math.round((rows * 20 + 4) * sc);
            int x = slot.resolveX(sw, w), y = slot.resolveY(sh, h);

            drawHudCard(g, x, y, w, h, editMode);

            var pose = g.pose();
            pose.pushMatrix();
            pose.translate(x, y);
            pose.scale(sc, sc);
            for (int i = 0; i < list.size(); i++) {
                int c = i % cols, r = i / cols;
                g.renderItem(new ItemStack(list.get(i).iconItem), 3 + c * 21, 2 + r * 20);
            }
            pose.popMatrix();

            float fSize = 7.6f * sc;
            for (int i = 0; i < list.size(); i++) {
                int c = i % cols, r = i / cols;
                int cnt = list.get(i).total;
                String s = String.valueOf(cnt);
                int col = cnt == 0 ? 0xFFF87171 : 0xFFF8FAFC;
                float tx = x + (3 + c * 21 + 17) * sc - YeHdFont.widthF(s, fSize, true);
                float ty = y + (11 + r * 20) * sc;
                drawHudText(g, s, tx, ty, fSize, col, true);
            }
        }

        private void addTrackedItem(List<Tracked> list, boolean on, Item item) {
            if (!on) return;
            int cnt = countInv(item);
            if (cnt > 0 || showZero.get()) {
                list.add(new Tracked(item, cnt));
            }
        }

        private int countInv(Item item) {
            Inventory inv = mc.player.getInventory();
            int total = 0;
            for (int i = 0; i < 36; i++) {
                ItemStack s = inv.getItem(i);
                if (!s.isEmpty() && s.is(item)) total += s.getCount();
            }
            ItemStack off = mc.player.getOffhandItem();
            if (!off.isEmpty() && off.is(item)) total += off.getCount();
            return total;
        }
    }

    // ==================== 8. INVENTORY PREVIEW HUD (背包预览) ====================
    public static final class InventoryHud extends HudElementModule {
        public final BoolSetting includeHotbar = addSetting(new BoolSetting("includeHotbar", "包含快捷栏", "底部额外显示第4行快捷栏物品", false));
        public final BoolSetting showSlotCells = addSetting(new BoolSetting("showSlotCells", "显示格子底纹", "为每个背包格绘制微透圆角槽位", true));

        public InventoryHud() {
            super("hud_inv_mod", "hud_inv", "背包预览", "Inventory HUD",
                    "屏幕实时透视 3×9（或 4×9）背包库存格子",
                    false, true, ColorStyle.ICE_CYAN, 7);
            addResetPositionButton();
        }

        @Override
        public void renderElement(GuiGraphics g, int sw, int sh, boolean editMode) {
            ConfigManager.HudSlot slot = getSlot();
            if (slot == null) return;

            float sc = scale.get().floatValue();
            int rows = includeHotbar.get() ? 4 : 3;
            int rawW = 9 * 18 + 6;
            int rawH = rows * 18 + 6 + (includeHotbar.get() ? 3 : 0);
            int w = Math.round(rawW * sc);
            int h = Math.round(rawH * sc);
            int x = slot.resolveX(sw, w), y = slot.resolveY(sh, h);

            drawHudCard(g, x, y, w, h, editMode);

            var pose = g.pose();
            pose.pushMatrix();
            pose.translate(x, y);
            pose.scale(sc, sc);
            Inventory inv = mc.player.getInventory();
            for (int row = 0; row < rows; row++) {
                int invRow = (row < 3) ? (row + 1) : 0; // row 3 -> hotbar (0..8)
                int sy = 3 + row * 18 + (row == 3 ? 3 : 0);
                for (int col = 0; col < 9; col++) {
                    int idx = invRow * 9 + col;
                    int sx = 3 + col * 18;
                    if (showSlotCells.get()) {
                        g.fill(sx, sy, sx + 16, sy + 16, 0x2E1E293B);
                    }
                    ItemStack st = inv.getItem(idx);
                    if (!st.isEmpty()) {
                        g.renderItem(st, sx, sy);
                        g.renderItemDecorations(mc.font, st, sx, sy);
                    }
                }
            }
            pose.popMatrix();
        }
    }

    // ==================== 9. NEARBY DROPS HUD (附近掉落物) ====================
    public static final class NearbyDropsHud extends HudElementModule {
        public final DoubleSetting scanDist = addSetting(new DoubleSetting("scanDist", "扫描半径", "检测周围多少米内的地面掉落物", 64.0, 16.0, 128.0, 8.0));
        public final DoubleSetting maxRows = addSetting(new DoubleSetting("maxRows", "最大显示行数", "最多列出前多少种掉落物", 6.0, 2.0, 12.0, 1.0));
        public final BoolSetting showIcon = addSetting(new BoolSetting("showIcon", "显示物品小图标", "每行前方渲染对应3D物品小图标", true));

        public NearbyDropsHud() {
            super("hud_drops_mod", "hud_drops", "附近掉落物", "Drops HUD",
                    "聚合统计周围地面掉落物图标、总数量与最近距离",
                    true, false, ColorStyle.ROYAL_GOLD, 8);
            addResetPositionButton();
        }

        @Override
        public void renderElement(GuiGraphics g, int sw, int sh, boolean editMode) {
            ConfigManager.HudSlot slot = getSlot();
            if (slot == null) return;

            record DropGroup(ItemStack sample, String name, int count, double minDist) {}
            Map<String, DropGroup> grouped = new LinkedHashMap<>();
            double maxD = scanDist.get();
            for (Entity e : mc.level.entitiesForRendering()) {
                if (e instanceof ItemEntity ie && !ie.getItem().isEmpty()) {
                    double d = mc.player.distanceTo(ie);
                    if (d > maxD) continue;
                    String nm = ie.getItem().getHoverName().getString();
                    int cnt = ie.getItem().getCount();
                    DropGroup prev = grouped.get(nm);
                    if (prev == null) {
                        grouped.put(nm, new DropGroup(ie.getItem().copyWithCount(1), nm, cnt, d));
                    } else {
                        grouped.put(nm, new DropGroup(prev.sample, nm, prev.count + cnt, Math.min(prev.minDist, d)));
                    }
                }
            }

            List<DropGroup> list = new ArrayList<>(grouped.values());
            list.sort(Comparator.comparingDouble(DropGroup::minDist));
            int limit = (int) Math.round(maxRows.get());
            if (list.size() > limit) list = list.subList(0, limit);
            if (list.isEmpty() && !editMode) return;
            if (list.isEmpty()) {
                list.add(new DropGroup(new ItemStack(Items.DIAMOND), ConfigManager.tr("钻石", "Diamond"), 4, 12.5));
            }

            float sc = scale.get().floatValue();
            float fSize = 8.4f * sc;
            boolean icons = showIcon.get();
            float iconOff = icons ? 14.0f * sc : 0.0f;

            List<String> texts = new ArrayList<>(list.size());
            float maxW = 54 * sc;
            for (DropGroup dg : list) {
                String s = String.format(Locale.ROOT, "%s ×%d §7(%.1fm)", dg.name, dg.count, dg.minDist);
                texts.add(s);
                maxW = Math.max(maxW, iconOff + YeHdFont.widthF(s, fSize, false));
            }

            float rowH = 12.5f * sc;
            int w = Math.round(maxW + 10 * sc);
            int h = Math.round(list.size() * rowH + 5 * sc);
            int x = slot.resolveX(sw, w), y = slot.resolveY(sh, h);

            drawHudCard(g, x, y, w, h, editMode);
            int col = resolvePrimaryColor();

            for (int i = 0; i < list.size(); i++) {
                float ry = y + 2.5f * sc + i * rowH;
                if (icons) {
                    var pose = g.pose();
                    pose.pushMatrix();
                    pose.translate(x + 4 * sc, ry);
                    pose.scale(0.68f * sc, 0.68f * sc);
                    g.renderItem(list.get(i).sample, 0, 0);
                    pose.popMatrix();
                }
                drawHudText(g, texts.get(i), x + 5 * sc + iconOff, ry + 0.5f * sc, fSize, col, false);
            }
        }
    }

    // ==================== 10. ACTIVE MODULE LIST HUD (开启模块表) ====================
    public static final class ModuleListHud extends HudElementModule {
        public final BoolSetting rainbowWave = addSetting(new BoolSetting("rainbowWave", "流光彩虹渐变", "模块名称随高度呈现柔和流光波浪", true));
        public final BoolSetting sideStripe = addSetting(new BoolSetting("sideStripe", "边缘亮色竖条", "在对齐侧绘制流光强调线", true));
        public final BoolSetting hideHudCategory = addSetting(new BoolSetting("hideHudCategory", "仅列出功能模块", "不在列表中重复显示 HUD 子模块", true));

        public ModuleListHud() {
            super("hud_modules_mod", "hud_modules", "开启列表", "ArrayList HUD",
                    "Rusher风格已开启功能列表，支持自适应左右对齐与流光波浪",
                    false, false, ColorStyle.ICE_CYAN, 9);
            addResetPositionButton();
        }

        @Override
        public void renderElement(GuiGraphics g, int sw, int sh, boolean editMode) {
            ConfigManager.HudSlot slot = getSlot();
            if (slot == null) return;

            float sc = scale.get().floatValue();
            float fSize = 8.6f * sc;

            List<Module> active = ModuleManager.getAll().stream()
                    .filter(m -> m.isEnabled() && m != this && (!hideHudCategory.get() || !(m instanceof HudElementModule)))
                    .sorted((a, b) -> Float.compare(
                            YeHdFont.widthF(ConfigManager.english ? b.getSubtitle() : b.getName(), fSize, false),
                            YeHdFont.widthF(ConfigManager.english ? a.getSubtitle() : a.getName(), fSize, false)))
                    .toList();
            if (active.isEmpty() && !editMode) return;

            float maxW = 54 * sc;
            for (Module m : active) {
                String nm = ConfigManager.english ? m.getSubtitle() : m.getName();
                maxW = Math.max(maxW, YeHdFont.widthF(nm, fSize, false) + 12 * sc);
            }
            float rowH = 12.0f * sc;
            int w = Math.round(maxW);
            int h = Math.round(Math.max(15 * sc, active.size() * rowH + 4 * sc));
            int x = slot.resolveX(sw, w), y = slot.resolveY(sh, h);
            boolean rightAlign = (slot.anchorX == 2);

            drawHudCard(g, x, y, w, h, editMode);
            long now = System.currentTimeMillis();
            int baseCol = resolvePrimaryColor();

            float ry = y + 2 * sc;
            for (int i = 0; i < active.size(); i++) {
                Module m = active.get(i);
                String nm = ConfigManager.english ? m.getSubtitle() : m.getName();
                int rowCol = baseCol;
                if (rainbowWave.get() && !syncGlobalColor.get()) {
                    float hue = (((now / 3800.0f) - i * 0.045f) % 1.0f + 1.0f) % 1.0f;
                    rowCol = 0xFF000000 | (Color.HSBtoRGB(hue, 0.72f, 1.0f) & 0x00FFFFFF);
                }
                float tw = YeHdFont.widthF(nm, fSize, false);
                float tx = rightAlign ? (x + w - 6 * sc - tw) : (x + 6 * sc);
                drawHudText(g, nm, tx, ry, fSize, rowCol, false);
                if (sideStripe.get()) {
                    int sx = rightAlign ? (x + w - 2) : x;
                    g.fill(sx, Math.round(ry), sx + 2, Math.round(ry + rowH - 1), rowCol);
                }
                ry += rowH;
            }
        }
    }

    // ==================== SHARED HELPERS ====================

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
        if (ConfigManager.english) {
            if (norm >= 315 || norm < 45) return "S +Z";
            if (norm < 135) return "W -X";
            if (norm < 225) return "N -Z";
            return "E +X";
        }
        if (norm >= 315 || norm < 45) return "南 +Z";
        if (norm < 135) return "西 -X";
        if (norm < 225) return "北 -Z";
        return "东 +X";
    }
}
