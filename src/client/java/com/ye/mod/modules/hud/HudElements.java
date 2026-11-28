package com.ye.mod.modules.hud;

import com.ye.mod.core.ConfigManager;
import com.ye.mod.core.Module;
import com.ye.mod.core.ModuleManager;
import com.ye.mod.core.YeZhCnDict;
import com.ye.mod.core.setting.BoolSetting;
import com.ye.mod.core.setting.DoubleSetting;
import com.ye.mod.core.setting.EnumSetting;
import com.ye.mod.modules.visual.GlobalColorModule;
import com.ye.mod.ui.AppleUiRenderer;
import com.ye.mod.ui.YeHdFont;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.phys.EntityHitResult;

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
 * 11 Modular HUD Widgets (+ WthitModule = 12 total in HUD tab!):
 * Includes Potions, Coords, Multi-Mode Speedometer (Text / Arc Gauge / Energy Bar / Wave Graph),
 * Time, FPS/Ping/CPS, Armor (0px bottom flush!), PvP Items, Inventory, Localized Chinese Item List (物品列表),
 * Module List, and 3D Paper-Doll PlayerView (玩家预览)!
 */
public final class HudElements {
    private HudElements() {}

    // ==================== 1. POTIONS HUD (药水面板) ====================
    public static final class PotionsHud extends HudElementModule {
        public enum Style {
            TABLE("表模式"),
            BAR("条模式");
            public final String label;
            Style(String label) { this.label = label; }
        }

        public final EnumSetting<Style> style = addSetting(new EnumSetting<>(
                "style", "呈现样式", "表模式 / 条模式", Style.TABLE
        ).functionGroup());
        public final BoolSetting hideVanilla = addSetting(new BoolSetting(
                "hideVanilla", "隐藏原版图标", "隐藏右上角原版方形药水框", true
        ).functionGroup());
        public final BoolSetting warnExpiring = addSetting(new BoolSetting(
                "warnExpiring", "临期闪烁提示", "剩余不足 8 秒时时间数字变红提醒", true
        ).functionGroup());
        public final BoolSetting compact = addSetting(new BoolSetting(
                "compact", "紧凑行高", "缩小行间距容纳更多状态", false
        ).functionGroup());

        private final Map<String, Integer> maxEffectDurations = new HashMap<>();

        public PotionsHud() {
            super("hud_potions_mod", "hud_potions", "药水面板", "Potions",
                    "药水状态表或倒计时进度条，支持原版图标隐藏",
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

                    AppleUiRenderer.drawCircle(g, x + 6.5f * sc, ry + 3.5f * sc, 4.5f * sc, eCol);
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
                    AppleUiRenderer.drawRoundedRect(g, bx, by, bw, bh, bh * 0.5f, 0xFF1E293B);
                    AppleUiRenderer.drawRoundedRect(g, bx, by, bw * pct, bh, bh * 0.5f, eCol);
                    ry += rowStep;
                }
            }
        }
    }

    // ==================== 2. COORDS HUD (双维坐标) ====================
    public static final class CoordsHud extends HudElementModule {
        public final BoolSetting multiLine = addSetting(new BoolSetting("multiLine", "三行分列排布", "开启为X/Y/Z三行，关闭为单行排布", true).functionGroup());
        public final BoolSetting showPrefix = addSetting(new BoolSetting("showPrefix", "显示XYZ字母", "显示 X / Y / Z 轴标签", true).functionGroup());
        public final BoolSetting showDecimals = addSetting(new BoolSetting("showDecimals", "精确到小数位", "显示精确到 0.1 的浮点坐标", false).functionGroup());
        public final BoolSetting showNetherSync = addSetting(new BoolSetting("showNetherSync", "主世界与下界换算", "括号内同步显示对应的×8或÷8跨维度坐标", true).functionGroup());
        public final BoolSetting showDirection = addSetting(new BoolSetting("showDirection", "附带朝向方位", "显示东南西北与正负坐标轴方向", false).functionGroup());
        public final BoolSetting showChunk = addSetting(new BoolSetting("showChunk", "显示区块坐标", "附带显示所在 Chunk (X, Z)", false).functionGroup());
        public final BoolSetting showBiome = addSetting(new BoolSetting("showBiome", "显示生物群系", "附带显示当前群系", false).functionGroup());

        public CoordsHud() {
            super("hud_coords_mod", "hud_coords", "坐标", "Coords",
                    "XYZ坐标面板，支持跨维度坐标同步换算、小数精度、朝向、区块与群系",
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

            boolean dec = showDecimals.get();
            boolean pref = showPrefix.get();
            double dx = mc.player.getX(), dy = mc.player.getY(), dz = mc.player.getZ();
            int px = mc.player.getBlockX(), py = mc.player.getBlockY(), pz = mc.player.getBlockZ();
            boolean isNether = mc.level.dimension() == Level.NETHER;
            boolean isOw = mc.level.dimension() == Level.OVERWORLD;
            int altX = isNether ? px * 8 : Math.floorDiv(px, 8);
            int altZ = isNether ? pz * 8 : Math.floorDiv(pz, 8);
            boolean canAlt = showNetherSync.get() && (isNether || isOw);

            String sx = dec ? String.format(Locale.ROOT, "%.1f", dx) : String.valueOf(px);
            String sy = dec ? String.format(Locale.ROOT, "%.1f", dy) : String.valueOf(py);
            String sz = dec ? String.format(Locale.ROOT, "%.1f", dz) : String.valueOf(pz);

            List<String> lines = new ArrayList<>();
            if (multiLine.get()) {
                lines.add((pref ? "X  " : "") + sx + (canAlt ? ("  §7(" + altX + ")") : ""));
                lines.add((pref ? "Y  " : "") + sy);
                lines.add((pref ? "Z  " : "") + sz + (canAlt ? ("  §7(" + altZ + ")") : ""));
            } else {
                String base = (pref ? "XYZ  " : "") + sx + ", " + sy + ", " + sz;
                lines.add(canAlt ? (base + "  §7(" + altX + ", " + altZ + ")") : base);
            }
            if (showDirection.get()) {
                lines.add("§7" + cardinalDir(mc.player.getYRot()));
            }
            if (showChunk.get()) {
                lines.add(ConfigManager.tr("§7区块: ", "§7Chunk: ") + (px >> 4) + ", " + (pz >> 4));
            }
            if (showBiome.get()) {
                Holder<Biome> b = mc.level.getBiome(mc.player.blockPosition());
                String bId = b.unwrapKey().map(k -> k.identifier().getPath()).orElse("plains");
                lines.add("§7" + bId);
            }

            float maxW = 36 * sc;
            for (String ln : lines) maxW = Math.max(maxW, YeHdFont.widthF(ln, fSize, false));
            float lineH = 11.5f * sc;
            int w = Math.round(maxW + 10 * sc);
            int h = Math.round(lines.size() * lineH + 4 * sc);
            int x = slot.resolveX(sw, w), y = slot.resolveY(sh, h);

            drawHudCard(g, x, y, w, h, editMode);
            float ry = y + 2.0f * sc;
            for (String ln : lines) {
                drawHudText(g, ln, x + 5 * sc, ry, fSize, col, false);
                ry += lineH;
            }
        }
    }

    // ==================== 3. SPEED HUD (移动速度 — 文字 / 仪表盘 / 能量条 / 波形图) ====================
    public static final class SpeedHud extends HudElementModule {
        public enum DisplayMode {
            TEXT("纯文字数值"),
            GAUGE_ARC("动态仪表盘(无字或带数)"),
            ENERGY_BAR("动态能量条"),
            WAVE_GRAPH("速度波形曲线");

            public final String label;
            DisplayMode(String label) { this.label = label; }
        }

        public enum SpeedUnit {
            KMH("KM/H (千米/时)", 72.0, "KM/H"),
            MPS("米/秒 (m/s)", 20.0, "m/s"),
            BPS("格/秒 (b/s)", 20.0, "格/秒"),
            BPT("格/刻 (b/t)", 1.0, "格/刻");

            public final String label;
            final double mult;
            final String suffix;

            SpeedUnit(String label, double mult, String suffix) {
                this.label = label;
                this.mult = mult;
                this.suffix = suffix;
            }
        }

        public final EnumSetting<DisplayMode> displayMode = addSetting(new EnumSetting<>(
                "displayMode", "表现形态", "文字数值 / 动态圆弧仪表盘 / 动态能量条 / 实时波形曲线", DisplayMode.TEXT
        ).functionGroup());

        public final EnumSetting<SpeedUnit> unit = addSetting(new EnumSetting<>(
                "unit", "速度单位", "KM/H · 米/秒 · 格/秒 · 格/刻", SpeedUnit.BPS
        ).functionGroup());

        public final BoolSetting showPrefix = addSetting(new BoolSetting(
                "showPrefix", "显示「移速：」前缀", "关闭后不显示前缀文字", true
        ).visible(() -> displayMode.get() == DisplayMode.TEXT).functionGroup());

        public final BoolSetting showUnit = addSetting(new BoolSetting(
                "showUnit", "显示速度单位", "关闭后只保留数字不显示单位后缀", true
        ).functionGroup());

        public final BoolSetting showNumberInGraphic = addSetting(new BoolSetting(
                "showNumInGraphic", "图形内附带数字", "关闭后纯用动画图形表达速度，完全无文字", true
        ).visible(() -> displayMode.get() != DisplayMode.TEXT).functionGroup());

        public final DoubleSetting decimals = addSetting(new DoubleSetting(
                "decimals", "小数位数", "0 = 整数，1 = 一位小数，2 = 两位小数", 2.0, 0.0, 2.0, 1.0
        ).functionGroup());

        public final BoolSetting includeVertical = addSetting(new BoolSetting(
                "includeVertical", "计入垂直升降", "鞘翅俯冲爬升时将Y轴速度一并计入合速度", false
        ).functionGroup());

        public final BoolSetting showPeak = addSetting(new BoolSetting(
                "showPeak", "显示最高峰值", "附带记录近期最高冲刺/飞行速度", false
        ).visible(() -> displayMode.get() == DisplayMode.TEXT).functionGroup());

        public final DoubleSetting maxGaugeBps = addSetting(new DoubleSetting(
                "maxGaugeBps", "图形满格基准(米/秒)", "仪表盘与能量条达到满格时的基准速度", 35.0, 8.0, 120.0, 2.0
        ).visible(() -> displayMode.get() != DisplayMode.TEXT).functionGroup());

        private double peakBpt = 0.0;
        private long peakTimeMs = 0L;
        private float smoothSpeedBps = 0.0f;
        private final float[] historyBps = new float[40];
        private int histIdx = 0;
        private long lastSampleMs = 0L;

        public SpeedHud() {
            super("hud_speed_mod", "hud_speed", "移动速度", "Speed",
                    "多形态移速监控：支持 KM/H、米/秒、格/秒、纯数字、无文字动态圆弧仪表盘、流光能量条与实时速度曲线",
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
            float bps = (float) (bpt * 20.0);
            smoothSpeedBps += (bps - smoothSpeedBps) * 0.22f;

            long now = System.currentTimeMillis();
            if (bpt >= peakBpt || now - peakTimeMs > 8000L) {
                peakBpt = bpt;
                peakTimeMs = now;
            }
            if (now - lastSampleMs >= 50L) {
                lastSampleMs = now;
                historyBps[histIdx] = smoothSpeedBps;
                histIdx = (histIdx + 1) % historyBps.length;
            }

            SpeedUnit u = unit.get();
            double val = (smoothSpeedBps / 20.0) * u.mult;
            int dec = (int) Math.round(decimals.get());
            String fmt = "%." + dec + "f";
            String numStr = String.format(Locale.ROOT, fmt, val);
            String uStr = showUnit.get() ? (" " + (ConfigManager.english && u == SpeedUnit.BPS ? "b/s" : u.suffix)) : "";

            float sc = scale.get().floatValue();
            int col = resolvePrimaryColor();
            DisplayMode dm = displayMode.get();

            if (dm == DisplayMode.TEXT) {
                String pref = showPrefix.get() ? ConfigManager.tr("移速: ", "Speed: ") : "";
                String txt = pref + numStr + uStr;
                if (showPeak.get()) {
                    txt += String.format(Locale.ROOT, " §7(%.1f)", peakBpt * u.mult);
                }
                float fSize = 8.8f * sc;
                int w = Math.round(YeHdFont.widthF(txt, fSize, false) + 10 * sc);
                int h = Math.round(15 * sc);
                int x = slot.resolveX(sw, w), y = slot.resolveY(sh, h);
                drawHudCard(g, x, y, w, h, editMode);
                drawHudText(g, txt, x + 5 * sc, y + 2.5f * sc, fSize, col, false);
            } else if (dm == DisplayMode.GAUGE_ARC) {
                // Pure graphical animated speedometer dial + needle!
                int size = Math.round(52 * sc);
                int x = slot.resolveX(sw, size), y = slot.resolveY(sh, size - Math.round(6 * sc));
                drawHudCard(g, x, y, size, size - Math.round(6 * sc), editMode);

                float cx = x + size * 0.5f;
                float cy = y + size * 0.54f;
                float rad = 19.5f * sc;
                float ratio = Math.max(0.0f, Math.min(1.0f, smoothSpeedBps / maxGaugeBps.get().floatValue()));

                // 24-tick radial gauge arc (-210 deg to +30 deg)
                int ticks = 24;
                int litTicks = Math.round(ratio * ticks);
                for (int i = 0; i <= ticks; i++) {
                    double ang = Math.toRadians(-210.0 + (i / (double) ticks) * 240.0);
                    float tx = cx + (float) Math.cos(ang) * rad;
                    float ty = cy + (float) Math.sin(ang) * rad;
                    int dotCol = (i <= litTicks)
                            ? (isUsingRainbowSync() ? ModuleManager.get(GlobalColorModule.class).getSpatialArgb(i * 0.35f) : col)
                            : 0x44475569;
                    float dotSz = (i == litTicks && ratio > 0.02f) ? 3.4f * sc : 2.3f * sc;
                    AppleUiRenderer.drawCircle(g, tx - dotSz * 0.5f, ty - dotSz * 0.5f, dotSz, dotCol);
                }

                // Animated needle dot chain from center hub
                double needleAng = Math.toRadians(-210.0 + ratio * 240.0);
                for (int s = 1; s <= 6; s++) {
                    float nr = (s / 6.0f) * (rad - 3.5f * sc);
                    float nx = cx + (float) Math.cos(needleAng) * nr;
                    float ny = cy + (float) Math.sin(needleAng) * nr;
                    AppleUiRenderer.drawCircle(g, nx - 1.1f * sc, ny - 1.1f * sc, 2.2f * sc, 0xFFF8FAFC);
                }
                AppleUiRenderer.drawCircle(g, cx - 2.4f * sc, cy - 2.4f * sc, 4.8f * sc, col);

                if (showNumberInGraphic.get()) {
                    YeHdFont.drawCentered(g, numStr + (showUnit.get() ? uStr : ""), cx, y + size - 15.5f * sc, 7.2f * sc, 0xFFF8FAFC, textShadow.get(), true);
                }
            } else if (dm == DisplayMode.ENERGY_BAR) {
                int w = Math.round(96 * sc);
                int h = Math.round((showNumberInGraphic.get() ? 20 : 10) * sc);
                int x = slot.resolveX(sw, w), y = slot.resolveY(sh, h);
                drawHudCard(g, x, y, w, h, editMode);

                float ratio = Math.max(0.02f, Math.min(1.0f, smoothSpeedBps / maxGaugeBps.get().floatValue()));
                float bx = x + 4 * sc, bw = w - 8 * sc;
                float bh = 4.6f * sc;
                float by = showNumberInGraphic.get() ? (y + h - bh - 3.5f * sc) : (y + (h - bh) * 0.5f);

                AppleUiRenderer.drawRoundedRect(g, bx, by, bw, bh, bh * 0.5f, 0x661E293B);
                AppleUiRenderer.drawRoundedRect(g, bx, by, bw * ratio, bh, bh * 0.5f, col);
                AppleUiRenderer.drawCircle(g, bx + bw * ratio - 2.5f * sc, by - 0.8f * sc, bh + 1.6f * sc, 0xFFFFFFFF);

                if (showNumberInGraphic.get()) {
                    drawHudText(g, numStr + uStr, bx, y + 2.0f * sc, 7.8f * sc, col, true);
                }
            } else {
                // WAVE_GRAPH: Live scrolling speed waveform
                int w = Math.round(92 * sc);
                int h = Math.round(28 * sc);
                int x = slot.resolveX(sw, w), y = slot.resolveY(sh, h);
                drawHudCard(g, x, y, w, h, editMode);

                float maxB = maxGaugeBps.get().floatValue();
                float gx0 = x + 4 * sc, gw = w - 8 * sc;
                float gyBot = y + h - 4 * sc, gh = h - 8 * sc;
                for (int i = 0; i < historyBps.length; i++) {
                    float v = historyBps[(histIdx + i) % historyBps.length];
                    float pct = Math.max(0.04f, Math.min(1.0f, v / maxB));
                    float barX = gx0 + (i / (float) historyBps.length) * gw;
                    float barH = gh * pct;
                    int barCol = isUsingRainbowSync() ? ModuleManager.get(GlobalColorModule.class).getSpatialArgb(i * 0.25f) : col;
                    AppleUiRenderer.drawRoundedRect(g, barX, gyBot - barH, Math.max(1.2f, (gw / historyBps.length) - 0.6f), barH, 0.8f, AppleUiRenderer.withAlpha(barCol, 0.82f));
                }
                if (showNumberInGraphic.get()) {
                    drawHudText(g, numStr + uStr, x + 5 * sc, y + 2.5f * sc, 7.6f * sc, 0xFFF8FAFC, true);
                }
            }
        }
    }

    // ==================== 4. TIME & ONLINE STATS HUD (时间统计) ====================
    public static final class TimeHud extends HudElementModule {
        private static final DateTimeFormatter FMT_24 = DateTimeFormatter.ofPattern("HH:mm:ss");
        private static final DateTimeFormatter FMT_12 = DateTimeFormatter.ofPattern("hh:mm:ss a", Locale.ENGLISH);

        public final BoolSetting showClock = addSetting(new BoolSetting("showClock", "显示现实时间", "显示电脑系统时钟", true).functionGroup());
        public final BoolSetting clock12h = addSetting(new BoolSetting("clock12h", "12小时制", "使用 AM/PM 格式", false).visible(showClock::get).functionGroup());
        public final BoolSetting showOnline = addSetting(new BoolSetting("showOnline", "显示在线时长", "统计本次游戏累计时长", true).functionGroup());
        public final BoolSetting showWorldDay = addSetting(new BoolSetting("showWorldDay", "显示世界天数", "显示当前存档游戏内天数", false).functionGroup());

        private final long sessionStartMs = System.currentTimeMillis();

        public TimeHud() {
            super("hud_time_mod", "hud_time", "时间", "Time",
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
            if (lines.isEmpty()) lines.add(ConfigManager.tr("时间统计", "Time"));

            float sc = scale.get().floatValue();
            float fSize = 8.8f * sc;
            float maxW = 48 * sc;
            for (String ln : lines) maxW = Math.max(maxW, YeHdFont.widthF(ln, fSize, false));
            float lineH = 11.5f * sc;
            int w = Math.round(maxW + 10 * sc);
            int h = Math.round(lines.size() * lineH + 4 * sc);
            int x = slot.resolveX(sw, w), y = slot.resolveY(sh, h);

            drawHudCard(g, x, y, w, h, editMode);
            int col = resolvePrimaryColor();
            float ry = y + 2.0f * sc;
            for (String ln : lines) {
                drawHudText(g, ln, x + 5 * sc, ry, fSize, col, false);
                ry += lineH;
            }
        }
    }

    // ==================== 5. FPS & PING HUD (帧率与延迟) ====================
    public static final class FpsPingHud extends HudElementModule {
        public final BoolSetting showFps = addSetting(new BoolSetting("showFps", "显示帧率", "实时显示当前 FPS", true).functionGroup());
        public final BoolSetting showPing = addSetting(new BoolSetting("showPing", "显示网络延迟", "读取当前服务器 Ping 毫秒数", true).functionGroup());
        public final BoolSetting showCps = addSetting(new BoolSetting("showCps", "显示按键CPS", "实时统计左键/右键每秒点击次数", false).functionGroup());
        public final BoolSetting showDirection = addSetting(new BoolSetting("showDirection", "显示方位角", "显示玩家当前朝向", true).functionGroup());
        public final BoolSetting showMem = addSetting(new BoolSetting("showMem", "显示内存占用", "显示 JVM 已用内存百分比", false).functionGroup());

        private final List<Long> leftClicks = new ArrayList<>();
        private boolean wasLeftDown = false;

        public FpsPingHud() {
            super("hud_info_mod", "hud_info", "帧率与延迟", "FPS & Ping",
                    "显示实时帧率、网络延迟、点击CPS、朝向轴线与内存占用",
                    true, false, ColorStyle.RUSHER_PINK, 4);
            addResetPositionButton();
        }

        @Override
        public void renderElement(GuiGraphics g, int sw, int sh, boolean editMode) {
            ConfigManager.HudSlot slot = getSlot();
            if (slot == null) return;

            long now = System.currentTimeMillis();
            if (mc.getWindow() != null) {
                boolean lDown = org.lwjgl.glfw.GLFW.glfwGetMouseButton(mc.getWindow().handle(), 0) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
                if (lDown && !wasLeftDown) leftClicks.add(now);
                wasLeftDown = lDown;
                leftClicks.removeIf(t -> now - t > 1000L);
            }

            List<String> parts = new ArrayList<>();
            if (showFps.get()) parts.add("FPS " + mc.getFps());
            if (showPing.get()) {
                int ping = 0;
                if (mc.getConnection() != null) {
                    PlayerInfo info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
                    if (info != null) ping = info.getLatency();
                }
                parts.add(ping + "ms");
            }
            if (showCps.get()) parts.add(leftClicks.size() + " CPS");
            if (showDirection.get()) parts.add(cardinalDir(mc.player.getYRot()));
            if (showMem.get()) {
                Runtime rt = Runtime.getRuntime();
                long pct = Math.round(((rt.totalMemory() - rt.freeMemory()) * 100.0) / Math.max(1L, rt.maxMemory()));
                parts.add(ConfigManager.tr("内存 ", "Mem ") + pct + "%");
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

    // ==================== 6. ARMOR DURABILITY HUD (装备耐久 — 0px 贴底对齐!) ====================
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

        public final BoolSetting horizontal = addSetting(new BoolSetting("horizontal", "横向排列", "开启为横向一排，关闭为纵向列表", true).functionGroup());
        public final BoolSetting showHands = addSetting(new BoolSetting("showHands", "包含主副手", "一并展示主手武器与副手物品耐久", false).functionGroup());
        public final EnumSetting<DurabilityMode> durMode = addSetting(new EnumSetting<>(
                "durMode", "数字格式", "百分比% / 剩余点数 / 仅原版耐久条", DurabilityMode.PERCENT
        ).functionGroup());
        public final BoolSetting lowBlink = addSetting(new BoolSetting("lowBlink", "低耐久闪烁", "耐久低于 20% 时闪烁提醒", true).functionGroup());

        public ArmorHud() {
            super("hud_armor_mod", "hud_armor", "装备耐久", "Armor",
                    "护甲与主副手实时耐久监控，支持完全贴底对齐快捷栏",
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
            boolean showTxt = durMode.get() != DurabilityMode.ICON_ONLY;
            // Tight exact height (23px with text, 17px icon-only) so dragging to bottom aligns 100% flush with hotbar!
            int w = horiz ? Math.round((count * 19 + 4) * sc) : Math.round(46 * sc);
            int h = horiz ? Math.round((showTxt ? 23 : 17) * sc) : Math.round((count * 18 + 4) * sc);
            int x = slot.resolveX(sw, w), y = slot.resolveY(sh, h);

            drawHudCard(g, x, y, w, h, editMode);

            var pose = g.pose();
            pose.pushMatrix();
            pose.translate(x, y);
            pose.scale(sc, sc);
            for (int i = 0; i < count; i++) {
                ItemStack st = stacks.get(i);
                int ix = horiz ? (2 + i * 19) : 2;
                int iy = horiz ? (showTxt ? 6 : 1) : (2 + i * 18);
                g.renderItem(st, ix, iy);
                g.renderItemDecorations(mc.font, st, ix, iy);
            }
            pose.popMatrix();

            if (showTxt) {
                float fSize = 6.9f * sc;
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
                        float cx = x + (2 + i * 19 + 8) * sc;
                        YeHdFont.drawCentered(g, txt, cx, y + 0.5f * sc, fSize, dCol, textShadow.get(), true);
                    } else {
                        drawHudText(g, txt, x + 21 * sc, y + (6 + i * 18) * sc, fSize * 1.1f, dCol, true);
                    }
                }
            }
        }
    }

    // ==================== 7. PVP ITEMS COUNTER HUD (物资计数) ====================
    public static final class PvpItemsHud extends HudElementModule {
        public final BoolSetting countTotem = addSetting(new BoolSetting("countTotem", "统计不死图腾", "统计全身不死图腾", true).functionGroup());
        public final BoolSetting countGapple = addSetting(new BoolSetting("countGapple", "统计金苹果", "统计附魔金苹果与金苹果", true).functionGroup());
        public final BoolSetting countRocket = addSetting(new BoolSetting("countRocket", "统计烟花火箭", "统计飞行烟花火箭", true).functionGroup());
        public final BoolSetting countXp = addSetting(new BoolSetting("countXp", "统计附魔之瓶", "统计修甲经验瓶", true).functionGroup());
        public final BoolSetting countCrystal = addSetting(new BoolSetting("countCrystal", "统计末影水晶", "统计末影水晶数量", true).functionGroup());
        public final BoolSetting countPearl = addSetting(new BoolSetting("countPearl", "统计末影珍珠", "统计传送珍珠数量", false).functionGroup());
        public final BoolSetting countObsidian = addSetting(new BoolSetting("countObsidian", "统计黑曜石", "统计背包黑曜石", false).functionGroup());
        public final BoolSetting twoRows = addSetting(new BoolSetting("twoRows", "双排网格布局", "物品较多时换为两排紧凑网格", false).functionGroup());
        public final BoolSetting showZero = addSetting(new BoolSetting("showZero", "零库存常显", "数量为 0 时仍保留图标并标红", false).functionGroup());

        public PvpItemsHud() {
            super("hud_items_mod", "hud_items", "物资计数", "PvP Items",
                    "战斗关键物资库存计数器（图腾/金果/烟花/经验瓶/水晶/珍珠/黑曜石）",
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
        public final BoolSetting includeHotbar = addSetting(new BoolSetting("includeHotbar", "包含快捷栏", "底部额外显示第4行快捷栏", false).functionGroup());
        public final BoolSetting showSlotCells = addSetting(new BoolSetting("showSlotCells", "显示格子底纹", "为每个背包格绘制槽位框", true).functionGroup());

        public InventoryHud() {
            super("hud_inv_mod", "hud_inv", "背包预览", "Inventory",
                    "屏幕实时透视 3×9 或 4×9 背包格子",
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
                int invRow = (row < 3) ? (row + 1) : 0;
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

    // ==================== 9. ITEM LIST HUD (物品列表 — 区块全范围 + 红/紫/白价值分级!) ====================
    public static final class NearbyDropsHud extends HudElementModule {
        public final BoolSetting unlimitedRange = addSetting(new BoolSetting("unlimitedRange", "加载范围全显", "默认显示已加载区块内的全部掉落物，关闭后按下方半径限制", true).functionGroup());
        public final DoubleSetting scanDist = addSetting(new DoubleSetting("scanDist", "扫描半径", "限制检测多少米内的地面物品", 96.0, 16.0, 256.0, 8.0).visible(() -> !unlimitedRange.get()).functionGroup());
        public final DoubleSetting maxRows = addSetting(new DoubleSetting("maxRows", "最大显示行数", "最多列出前多少种地面物品", 8.0, 2.0, 20.0, 1.0).functionGroup());
        public final BoolSetting tierColoring = addSetting(new BoolSetting("tierColoring", "价值分级高亮", "红=高价值(潜影盒/鞘翅等)，紫=有价值(烟花/黑曜石等)，白=普通", true).functionGroup());
        public final BoolSetting sortByTier = addSetting(new BoolSetting("sortByTier", "高价值优先排序", "将高价值与有价值物品排在列表最前方", true).functionGroup());
        public final BoolSetting showIcon = addSetting(new BoolSetting("showIcon", "显示物品图标", "每行前方渲染对应物品图标", true).functionGroup());
        public final BoolSetting showDistance = addSetting(new BoolSetting("showDistance", "显示距离米数", "末尾附带最近距离", true).functionGroup());

        public NearbyDropsHud() {
            super("hud_drops_mod", "hud_drops", "物品列表", "Item List",
                    "统计已加载区块内地面物品中文名称、图标、数量与距离，支持红/紫/白价值分级",
                    true, false, ColorStyle.ROYAL_GOLD, 8);
            addSetting(new com.ye.mod.core.setting.ActionSetting("editHighTier", "配置高价值物品(红)", "自定义红色高价值高亮物品", () -> {
                com.ye.mod.ui.YeQuickMenu.openBlockPickerModal(ConfigManager.tr("高价值物品 (红色)", "High Tier Items (Red)"), ConfigManager.dropTierHigh);
            }).functionGroup());
            addSetting(new com.ye.mod.core.setting.ActionSetting("editMidTier", "配置有价值物品(紫)", "自定义紫色有价值高亮物品", () -> {
                com.ye.mod.ui.YeQuickMenu.openBlockPickerModal(ConfigManager.tr("有价值物品 (紫色)", "Mid Tier Items (Purple)"), ConfigManager.dropTierMid);
            }).functionGroup());
            addResetPositionButton();
        }

        private int resolveTier(ItemStack stack) {
            if (stack.isEmpty()) return 0;
            String id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            if (ConfigManager.dropTierHigh.contains(id) || id.contains("shulker_box")) return 2;
            if (ConfigManager.dropTierMid.contains(id)) return 1;
            return 0;
        }

        @Override
        public void renderElement(GuiGraphics g, int sw, int sh, boolean editMode) {
            ConfigManager.HudSlot slot = getSlot();
            if (slot == null) return;

            record DropGroup(ItemStack sample, String name, int count, double minDist, int tier) {}
            Map<String, DropGroup> grouped = new LinkedHashMap<>();
            boolean unlim = unlimitedRange.get();
            double maxD = scanDist.get();
            for (Entity e : mc.level.entitiesForRendering()) {
                if (e instanceof ItemEntity ie && !ie.getItem().isEmpty()) {
                    double d = mc.player.distanceTo(ie);
                    if (!unlim && d > maxD) continue;
                    String nm = YeZhCnDict.itemName(ie.getItem());
                    int cnt = ie.getItem().getCount();
                    int tier = resolveTier(ie.getItem());
                    DropGroup prev = grouped.get(nm);
                    if (prev == null) {
                        grouped.put(nm, new DropGroup(ie.getItem().copyWithCount(1), nm, cnt, d, tier));
                    } else {
                        grouped.put(nm, new DropGroup(prev.sample, nm, prev.count + cnt, Math.min(prev.minDist, d), Math.max(prev.tier, tier)));
                    }
                }
            }

            List<DropGroup> list = new ArrayList<>(grouped.values());
            if (sortByTier.get()) {
                list.sort((a, b) -> {
                    int tc = Integer.compare(b.tier, a.tier);
                    return tc != 0 ? tc : Double.compare(a.minDist, b.minDist);
                });
            } else {
                list.sort(Comparator.comparingDouble(DropGroup::minDist));
            }
            int limit = (int) Math.round(maxRows.get());
            if (list.size() > limit) list = list.subList(0, limit);
            if (list.isEmpty() && !editMode) return;
            if (list.isEmpty()) {
                list.add(new DropGroup(new ItemStack(Items.SHULKER_BOX), ConfigManager.tr("潜影盒", "Shulker Box"), 2, 18.0, 2));
                list.add(new DropGroup(new ItemStack(Items.FIREWORK_ROCKET), ConfigManager.tr("烟花火箭", "Firework"), 64, 32.0, 1));
                list.add(new DropGroup(new ItemStack(Items.NETHERRACK), ConfigManager.tr("下界岩", "Netherrack"), 16, 48.0, 0));
            }

            float sc = scale.get().floatValue();
            float fSize = 8.4f * sc;
            boolean icons = showIcon.get();
            boolean distOn = showDistance.get();
            float iconOff = icons ? 14.0f * sc : 0.0f;

            List<String> texts = new ArrayList<>(list.size());
            float maxW = 54 * sc;
            for (DropGroup dg : list) {
                String s = distOn
                        ? String.format(Locale.ROOT, "%s ×%d §7(%.1fm)", dg.name, dg.count, dg.minDist)
                        : String.format(Locale.ROOT, "%s ×%d", dg.name, dg.count);
                texts.add(s);
                maxW = Math.max(maxW, iconOff + YeHdFont.widthF(s, fSize, false));
            }

            float rowH = 12.5f * sc;
            int w = Math.round(maxW + 10 * sc);
            int h = Math.round(list.size() * rowH + 4 * sc);
            int x = slot.resolveX(sw, w), y = slot.resolveY(sh, h);

            drawHudCard(g, x, y, w, h, editMode);
            int fallbackCol = resolvePrimaryColor();
            boolean useTiers = tierColoring.get();

            for (int i = 0; i < list.size(); i++) {
                DropGroup dg = list.get(i);
                int rowCol = !useTiers ? fallbackCol : switch (dg.tier) {
                    case 2 -> 0xFFEF4444;
                    case 1 -> 0xFFC084FC;
                    default -> 0xFFF1F5F9;
                };
                float ry = y + 2.0f * sc + i * rowH;
                if (icons) {
                    var pose = g.pose();
                    pose.pushMatrix();
                    pose.translate(x + 4 * sc, ry);
                    pose.scale(0.68f * sc, 0.68f * sc);
                    g.renderItem(dg.sample, 0, 0);
                    pose.popMatrix();
                }
                drawHudText(g, texts.get(i), x + 5 * sc + iconOff, ry + 0.5f * sc, fSize, rowCol, false);
            }
        }
    }

    // ==================== 10. ACTIVE MODULE LIST HUD (功能列表) ====================
    public static final class ModuleListHud extends HudElementModule {
        public final BoolSetting rainbowWave = addSetting(new BoolSetting("rainbowWave", "渐变", "模块名称随高度呈现流动渐变", true).functionGroup());
        public final BoolSetting sideStripe = addSetting(new BoolSetting("sideStripe", "边缘亮色竖条", "在对齐侧绘制流光边线", true).functionGroup());
        public final BoolSetting hideHudCategory = addSetting(new BoolSetting("hideHudCategory", "仅列功能模块", "不重复列出 HUD 子模块", true).functionGroup());

        public ModuleListHud() {
            super("hud_modules_mod", "hud_modules", "功能列表", "ArrayList",
                    "已开启功能模块列表，支持左右自适应对齐与渐变",
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
                YeHdFont.draw(g, nm, tx, ry, fSize, rowCol, textShadow.get(), false);
                if (sideStripe.get()) {
                    int sx = rightAlign ? (x + w - 2) : x;
                    g.fill(sx, Math.round(ry), sx + 2, Math.round(ry + rowH - 1), rowCol);
                }
                ry += rowH;
            }
        }
    }

    // ==================== 11. 3D PAPER-DOLL PLAYERVIEW HUD (玩家预览 — 宽敞翅膀框 + 多玩家状态!) ====================
    public static final class PlayerViewHud extends HudElementModule {
        public enum AngleMode {
            CLASSIC_ANGLE("立体微侧角"),
            FOLLOW_PITCH_YAW("跟随实时转头"),
            FRONT_STRAIGHT("正脸平视"),
            AUTO_TURNTABLE("360°自转展示");

            public final String label;
            AngleMode(String label) { this.label = label; }
        }

        public enum TargetChoice {
            SELF_ONLY("只看自己"),
            COMBAT_TARGET("对视/战斗时切敌方"),
            MULTI_NEARBY("多玩家同屏展示");

            public final String label;
            TargetChoice(String label) { this.label = label; }
        }

        public final EnumSetting<AngleMode> angleMode = addSetting(new EnumSetting<>(
                "angleMode", "朝向模式", "立体微侧角 / 跟随转头俯仰 / 正脸平视 / 360°自转", AngleMode.FOLLOW_PITCH_YAW
        ).functionGroup());

        public final EnumSetting<TargetChoice> targetChoice = addSetting(new EnumSetting<>(
                "targetChoice", "预览对象", "单人预览、对视敌方或周围多玩家同屏渲染", TargetChoice.SELF_ONLY
        ).functionGroup());

        public final DoubleSetting maxPlayers = addSetting(new DoubleSetting(
                "maxPlayers", "最多显示玩家数", "多玩家同屏模式下最多同时展示的玩家数量", 3.0, 2.0, 5.0, 1.0
        ).visible(() -> targetChoice.get() == TargetChoice.MULTI_NEARBY).functionGroup());

        public final DoubleSetting customYaw = addSetting(new DoubleSetting(
                "customYaw", "侧身角度", "调节立体侧身旋转偏角", 24.0, -90.0, 90.0, 2.0
        ).visible(() -> angleMode.get() == AngleMode.CLASSIC_ANGLE).functionGroup());

        public final BoolSetting showStatusFooter = addSetting(new BoolSetting(
                "showStatusFooter", "底部名称血量与Pop", "在模型脚下显示玩家ID、血量条与图腾Pop计数", true
        ).functionGroup());

        public PlayerViewHud() {
            super("hud_player_view_mod", "hud_player_view", "玩家预览", "Player View",
                    "屏幕实时渲染3D玩家纸娃娃（宽敞展示翅膀饰品，支持多玩家同屏、血量与Pop计数）",
                    false, false, ColorStyle.ICE_CYAN, 11);
            addResetPositionButton();
        }

        @Override
        public void renderElement(GuiGraphics g, int sw, int sh, boolean editMode) {
            ConfigManager.HudSlot slot = getSlot();
            if (slot == null) return;

            List<LivingEntity> subjects = new ArrayList<>();
            if (targetChoice.get() == TargetChoice.MULTI_NEARBY) {
                subjects.add(mc.player);
                int lim = (int) Math.round(maxPlayers.get());
                mc.level.players().stream()
                        .filter(p -> p != mc.player && !p.isSpectator())
                        .sorted(Comparator.comparingDouble(p -> mc.player.distanceTo(p)))
                        .limit(Math.max(0, lim - 1))
                        .forEach(subjects::add);
            } else if (targetChoice.get() == TargetChoice.COMBAT_TARGET
                    && mc.hitResult instanceof EntityHitResult ehr
                    && ehr.getEntity() instanceof LivingEntity le && le.isAlive()) {
                subjects.add(le);
            } else {
                subjects.add(mc.player);
            }

            float sc = scale.get().floatValue();
            boolean footer = showStatusFooter.get() || subjects.size() > 1;
            int cellW = Math.round(96 * sc);
            int gap = Math.round(6 * sc);
            int w = subjects.size() * cellW + Math.max(0, subjects.size() - 1) * gap;
            int h = Math.round((footer ? 112 : 96) * sc);
            int x = slot.resolveX(sw, w);
            int y = slot.resolveY(sh, h);

            drawHudCard(g, x, y, w, h, editMode);

            for (int idx = 0; idx < subjects.size(); idx++) {
                LivingEntity subject = subjects.get(idx);
                int cxBox = x + idx * (cellW + gap);
                int dollBottom = y + h - Math.round((footer ? 20 : 6) * sc);
                int dollScale = Math.max(11, Math.round(22 * sc));
                float cx = cxBox + cellW * 0.5f;
                float cy = y + (dollBottom - y) * 0.48f;

                float lookX, lookY;
                switch (angleMode.get()) {
                    case FRONT_STRAIGHT -> {
                        lookX = cx;
                        lookY = cy;
                    }
                    case CLASSIC_ANGLE -> {
                        lookX = cx - customYaw.get().floatValue() * 1.6f * sc;
                        lookY = cy - 6.0f * sc;
                    }
                    case AUTO_TURNTABLE -> {
                        double ang = ((System.currentTimeMillis() % 6000L) / 6000.0) * Math.PI * 2.0;
                        lookX = cx + (float) Math.sin(ang) * 90.0f * sc;
                        lookY = cy - 4.0f * sc;
                    }
                    default -> {
                        // Fixed non-mirrored look direction: turning right turns preview naturally right
                        float relYaw = net.minecraft.util.Mth.wrapDegrees(subject.getYRot());
                        lookX = cx + relYaw * 0.85f * sc;
                        lookY = cy - subject.getXRot() * 0.95f * sc;
                    }
                }

                try {
                    InventoryScreen.renderEntityInInventoryFollowsMouse(
                            g, cxBox + 2, y + 2, cxBox + cellW - 2, dollBottom,
                            dollScale, 0.0625f, lookX, lookY, subject
                    );
                } catch (Throwable ignored) {}

                if (footer) {
                    float hp = subject.getHealth() + subject.getAbsorptionAmount();
                    float pct = Math.max(0f, Math.min(1f, subject.getHealth() / Math.max(1f, subject.getMaxHealth())));
                    int pops = (subject instanceof net.minecraft.world.entity.player.Player pl)
                            ? com.ye.mod.modules.visual.HealthPlatesModule.getEntityPopCount(pl.getId()) : 0;
                    String label = subject.getName().getString();
                    if (pops > 0) label += " -" + pops;
                    String nm = YeHdFont.clipToWidth(label, 7.2f * sc, true, cellW - 8 * sc);
                    YeHdFont.drawCentered(g, nm, cx, dollBottom + 1.5f * sc, 7.2f * sc, resolvePrimaryColor(), textShadow.get(), true);

                    String hpStr = String.format(Locale.ROOT, "%.0f HP", hp);
                    YeHdFont.drawCentered(g, hpStr, cx, dollBottom + 9.5f * sc, 6.5f * sc, 0xFFCBD5E1, false, false);
                    AppleUiRenderer.drawRoundedRect(g, cxBox + 6 * sc, y + h - 4.2f * sc, cellW - 12 * sc, 2.6f * sc, 1.3f * sc, 0xFF1E293B);
                    AppleUiRenderer.drawRoundedRect(g, cxBox + 6 * sc, y + h - 4.2f * sc, (cellW - 12 * sc) * pct, 2.6f * sc, 1.3f * sc, 0xFF4ADE80);
                }
            }
        }
    }

    // ==================== 12. STORAGE COUNT HUD (储存数量) ====================
    public static final class StorageCountHud extends HudElementModule {
        public StorageCountHud() {
            super("hud_radar_mod", "hud_radar", "储存数量", "Storage Count",
                    "单独统计周围加载区块内的储存容器总数与潜影盒数量",
                    true, false, ColorStyle.ROYAL_GOLD, 12);
            addResetPositionButton();
        }

        @Override
        public void renderElement(GuiGraphics g, int sw, int sh, boolean editMode) {
            ConfigManager.HudSlot slot = getSlot();
            if (slot == null) return;
            com.ye.mod.modules.world.StashRadarModule sr = ModuleManager.get(com.ye.mod.modules.world.StashRadarModule.class);
            int total = (sr != null) ? sr.getTotalStorageCount() : 0;
            int shulkers = (sr != null) ? sr.getShulkerCount() : 0;
            if (!editMode && (sr == null || !sr.isEnabled()) && total == 0) return;

            String txt = ConfigManager.tr(
                    String.format("储存: %d (潜影盒 %d)", total, shulkers),
                    String.format("Storage: %d (Shulkers %d)", total, shulkers));
            float sc = scale.get().floatValue();
            float fSize = 8.6f * sc;
            int w = Math.round(YeHdFont.widthF(txt, fSize, false) + 12 * sc);
            int h = Math.round(15 * sc);
            int x = slot.resolveX(sw, w), y = slot.resolveY(sh, h);
            drawHudCard(g, x, y, w, h, editMode);
            drawHudText(g, txt, x + 6 * sc, y + 2.5f * sc, fSize, resolvePrimaryColor(), false);
        }
    }

    // ==================== SHARED HELPERS ====================

    private static String effectLabel(MobEffectInstance eff) {
        String base = YeZhCnDict.effectName(eff.getEffect().value());
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
