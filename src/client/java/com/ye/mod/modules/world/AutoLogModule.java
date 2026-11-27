package com.ye.mod.modules.world;

import com.ye.mod.core.Category;
import com.ye.mod.core.ConfigManager;
import com.ye.mod.core.FriendManager;
import com.ye.mod.core.Module;
import com.ye.mod.core.setting.ActionSetting;
import com.ye.mod.core.setting.BoolSetting;
import com.ye.mod.core.setting.DoubleSetting;
import com.ye.mod.core.setting.EnumSetting;
import com.ye.mod.ui.YeQuickMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * High-Flexibility Visual AutoLog Engine (自动下线):
 * - Every trigger can be independently set to OFF / OR ("满足即退(或)") / AND ("组合满足(且)")
 * - Live human-readable formula preview in UI
 * - Exact Totem Pop tracking, Remaining Totems, Health, Visual Range Players (with Friend Whitelist & Armed check),
 *   Inventory Item thresholds (Fireworks / Golden Apples / XP Bottles), Armor/Elytra Durability, and Void Fall
 * - Anti-relog-loop guard (auto-disable on trigger + post-join grace period) & rich Battle Report disconnect screen!
 */
public class AutoLogModule extends Module {
    public enum RuleMode {
        OFF("关闭"),
        OR("满足即退(或)"),
        AND("组合满足(且)");

        public final String label;
        RuleMode(String label) { this.label = label; }
    }

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final EquipmentSlot[] CHECKED_EQUIP = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    // 1. Totem Pop Count
    public final EnumSetting<RuleMode> popRule = addSetting(new EnumSetting<>(
            "popRule", "连爆图腾规则", "连续触发不死图腾达到次数时下线", RuleMode.OFF
    ));
    public final DoubleSetting maxPops = addSetting(new DoubleSetting(
            "maxPops", "连爆次数阈值", "在时间窗内爆掉多少个图腾触发", 2.0, 1.0, 10.0, 1.0
    ).visible(() -> popRule.get() != RuleMode.OFF));
    public final DoubleSetting popResetSec = addSetting(new DoubleSetting(
            "popResetSec", "连爆重置秒数", "超过该秒数未再爆图腾则自动清零计数", 30.0, 5.0, 120.0, 5.0
    ).visible(() -> popRule.get() != RuleMode.OFF));

    // 2. Remaining Totems
    public final EnumSetting<RuleMode> totemLeftRule = addSetting(new EnumSetting<>(
            "totemLeftRule", "剩余图腾规则", "全身（背包+副手）剩余不死图腾过少时下线", RuleMode.OR
    ));
    public final DoubleSetting minTotems = addSetting(new DoubleSetting(
            "minTotems", "图腾少于等于", "全身不死图腾 ≤ 此数量触发", 1.0, 0.0, 12.0, 1.0
    ).visible(() -> totemLeftRule.get() != RuleMode.OFF));

    // 3. Health
    public final EnumSetting<RuleMode> healthRule = addSetting(new EnumSetting<>(
            "healthRule", "低血量规则", "生命值跌破安全线时立即下线", RuleMode.OR
    ));
    public final DoubleSetting minHealth = addSetting(new DoubleSetting(
            "minHealth", "生命少于等于", "生命值 ≤ 此数值触发 (20 = 10颗心)", 6.0, 1.0, 20.0, 1.0
    ).visible(() -> healthRule.get() != RuleMode.OFF));
    public final BoolSetting includeAbsorption = addSetting(new BoolSetting(
            "includeAbsorption", "计入吸收黄心", "将金苹果提供的伤害吸收黄心一并计入有效血量", true
    ).visible(() -> healthRule.get() != RuleMode.OFF));

    // 4. Visual Range Stranger Player
    public final EnumSetting<RuleMode> playerRule = addSetting(new EnumSetting<>(
            "playerRule", "视野玩家规则", "视野/加载范围内出现非白名单玩家时触发", RuleMode.OFF
    ));
    public final DoubleSetting playerRange = addSetting(new DoubleSetting(
            "playerRange", "玩家检测距离", "检测多少米内的其他玩家", 96.0, 16.0, 256.0, 8.0
    ).visible(() -> playerRule.get() != RuleMode.OFF));
    public final BoolSetting ignoreFriends = addSetting(new BoolSetting(
            "ignoreFriends", "忽略白名单好友", "白名单中的好友进入视野不会触发下线", true
    ).visible(() -> playerRule.get() != RuleMode.OFF));
    public final BoolSetting armedOnly = addSetting(new BoolSetting(
            "armedOnly", "仅武装玩家触发", "仅当对方穿戴护甲或手持武器/水晶时才视为威胁", false
    ).visible(() -> playerRule.get() != RuleMode.OFF));

    // 5. Inventory Items: Fireworks, Golden Apples, XP Bottles
    public final EnumSetting<RuleMode> fireworkRule = addSetting(new EnumSetting<>(
            "fireworkRule", "背包烟花规则", "长途跑图或空战烟花火箭耗尽预警下线", RuleMode.OFF
    ));
    public final DoubleSetting minFireworks = addSetting(new DoubleSetting(
            "minFireworks", "烟花少于等于", "背包+副手烟花火箭 ≤ 此数量触发", 8.0, 0.0, 128.0, 4.0
    ).visible(() -> fireworkRule.get() != RuleMode.OFF));

    public final EnumSetting<RuleMode> gappleRule = addSetting(new EnumSetting<>(
            "gappleRule", "背包金苹果规则", "金苹果与附魔金苹果总数不足时下线", RuleMode.OFF
    ));
    public final DoubleSetting minGapples = addSetting(new DoubleSetting(
            "minGapples", "金苹果少于等于", "金苹果+附魔金苹果 ≤ 此数量触发", 4.0, 0.0, 64.0, 1.0
    ).visible(() -> gappleRule.get() != RuleMode.OFF));

    public final EnumSetting<RuleMode> xpBottleRule = addSetting(new EnumSetting<>(
            "xpBottleRule", "背包经验瓶规则", "修甲附魔之瓶不足时触发", RuleMode.OFF
    ));
    public final DoubleSetting minXpBottles = addSetting(new DoubleSetting(
            "minXpBottles", "经验瓶少于等于", "附魔之瓶 ≤ 此数量触发", 16.0, 0.0, 256.0, 8.0
    ).visible(() -> xpBottleRule.get() != RuleMode.OFF));

    // 6. Equipment / Elytra Durability & Void Protection
    public final EnumSetting<RuleMode> durabilityRule = addSetting(new EnumSetting<>(
            "durabilityRule", "装备耐久规则", "穿戴中的鞘翅或护甲濒临破碎时保命下线", RuleMode.OFF
    ));
    public final DoubleSetting minDurabilityPct = addSetting(new DoubleSetting(
            "minDurabilityPct", "耐久低于百分比", "任一穿戴护甲或鞘翅耐久 ≤ 此百分比触发", 8.0, 1.0, 50.0, 1.0
    ).visible(() -> durabilityRule.get() != RuleMode.OFF));

    public final EnumSetting<RuleMode> voidRule = addSetting(new EnumSetting<>(
            "voidRule", "坠入虚空规则", "不慎掉出世界底部基岩层坠入虚空时立刻秒退", RuleMode.OR
    ));

    // 7. Safety & Anti-Loop Protections
    public final BoolSetting autoDisableOnLog = addSetting(new BoolSetting(
            "autoDisableOnLog", "触发后自动关闭", "成功触发自动下线后自动关闭开关，彻底杜绝重连秒退死循环", true
    ));
    public final DoubleSetting joinGraceSec = addSetting(new DoubleSetting(
            "joinGraceSec", "进服保护秒数", "刚进入服务器前多少秒内暂不触发，给你时间补图腾/脱险", 12.0, 0.0, 60.0, 2.0
    ));

    private final Deque<Long> recentPopTimestamps = new ConcurrentLinkedDeque<>();
    private Level lastTrackedLevel = null;
    private long levelJoinMs = 0L;
    private long lastDisconnectMs = 0L;

    public AutoLogModule() {
        super("auto_log", "自动下线", "AutoLog", "或/且自由组合智能保命下线，含连爆图腾、视野玩家白名单与断线战报", Category.UTILITY);

        addSetting(new ActionSetting("toggleTargetFriend", "准星加删好友", "将准星对准的玩家加入或移出白名单", () -> {
            String res = FriendManager.toggleCrosshairPlayer();
            if (res == null) {
                YeQuickMenu.showToast(ConfigManager.tr("请将准星对准一名玩家 (或用 /ye friend <名字>)", "Aim at a player first"), 0xFFFACC15);
            } else if (res.startsWith("+")) {
                YeQuickMenu.showToast(ConfigManager.tr("✓ 已加入好友白名单: " + res.substring(1), "Added friend: " + res.substring(1)), 0xFF22C55E);
            } else {
                YeQuickMenu.showToast(ConfigManager.tr("已移出白名单: " + res.substring(1), "Removed friend: " + res.substring(1)), 0xFFEF4444);
            }
        }));

        addSetting(new ActionSetting("addNearbyFriends", "附近玩家加好友", "将周围32米内所有玩家一键加入白名单", () -> {
            int n = FriendManager.addNearbyPlayers(32.0);
            YeQuickMenu.showToast(ConfigManager.tr("已新增 " + n + " 名好友 (共 " + FriendManager.count() + " 人)",
                    "Added " + n + " friends (Total " + FriendManager.count() + ")"), 0xFF38BDF8);
        }));

        addSetting(new ActionSetting("clearFriends", "清空好友名单", "清空当前白名单中的所有玩家", () -> {
            FriendManager.clearFriends();
            YeQuickMenu.showToast(ConfigManager.tr("已清空好友白名单", "Cleared friend list"), 0xFF94A3B8);
        }));
    }

    @Override
    protected void onEnable() {
        recentPopTimestamps.clear();
        if (mc.level != null) {
            lastTrackedLevel = mc.level;
            // Give a brief 2s safety buffer if enabled while already low, or full grace on world join
            levelJoinMs = System.currentTimeMillis() - Math.round(Math.max(0, joinGraceSec.get() - 2.0) * 1000L);
        }
    }

    @Override
    protected void onDisable() {
        recentPopTimestamps.clear();
    }

    /**
     * Called from ParticleEngineMixin when vanilla spawns TOTEM_OF_UNDYING emitter on an entity!
     */
    public void onEntityTotemPop(Entity entity) {
        if (entity == null || mc.player == null || entity.getId() != mc.player.getId()) return;
        long now = System.currentTimeMillis();
        recentPopTimestamps.addLast(now);
        pruneOldPops(now);
        // Evaluate immediately on the exact packet frame so we don't even wait for end of tick!
        if (isEnabled()) {
            evaluateAndDisconnectIfNeeded();
        }
    }

    private void pruneOldPops(long now) {
        long windowMs = Math.round(popResetSec.get() * 1000.0);
        while (!recentPopTimestamps.isEmpty()) {
            Long head = recentPopTimestamps.peekFirst();
            if (head != null && now - head > windowMs) {
                recentPopTimestamps.pollFirst();
            } else {
                break;
            }
        }
    }

    public int getRecentPopCount() {
        pruneOldPops(System.currentTimeMillis());
        return recentPopTimestamps.size();
    }

    @Override
    public void onTick() {
        if (!isEnabled()) return;
        if (mc.player == null || mc.level == null || mc.getConnection() == null) {
            lastTrackedLevel = null;
            return;
        }
        if (mc.level != lastTrackedLevel) {
            lastTrackedLevel = mc.level;
            levelJoinMs = System.currentTimeMillis();
            recentPopTimestamps.clear();
        }
        evaluateAndDisconnectIfNeeded();
    }

    private void evaluateAndDisconnectIfNeeded() {
        if (mc.player == null || mc.level == null || mc.getConnection() == null) return;
        if (mc.player.isCreative() || mc.player.isSpectator() || mc.player.isDeadOrDying()) return;

        long now = System.currentTimeMillis();
        if (now - lastDisconnectMs < 3000L) return;
        long graceMs = Math.round(joinGraceSec.get() * 1000.0);
        if (now - levelJoinMs < graceMs) return;

        pruneOldPops(now);

        List<String> orReasons = new ArrayList<>();
        List<String> andReasons = new ArrayList<>();
        int totalAndRules = 0;
        int matchedAndRules = 0;

        // 1. Totem Pops
        int pops = recentPopTimestamps.size();
        int popLim = (int) Math.round(maxPops.get());
        evalRule(popRule.get(), pops >= popLim,
                ConfigManager.tr("连爆图腾 " + pops + " 次 (≥" + popLim + ")", "Totem pops " + pops + " (>=" + popLim + ")"),
                orReasons, andReasons);
        if (popRule.get() == RuleMode.AND) { totalAndRules++; if (pops >= popLim) matchedAndRules++; }

        // 2. Remaining Totems
        int totems = countItem(Items.TOTEM_OF_UNDYING);
        int totLim = (int) Math.round(minTotems.get());
        evalRule(totemLeftRule.get(), totems <= totLim,
                ConfigManager.tr("剩余图腾仅 " + totems + " 个 (≤" + totLim + ")", "Totems left " + totems + " (<=" + totLim + ")"),
                orReasons, andReasons);
        if (totemLeftRule.get() == RuleMode.AND) { totalAndRules++; if (totems <= totLim) matchedAndRules++; }

        // 3. Health
        float hp = mc.player.getHealth() + (includeAbsorption.get() ? mc.player.getAbsorptionAmount() : 0.0f);
        double hpLim = minHealth.get();
        evalRule(healthRule.get(), hp <= hpLim,
                String.format(Locale.ROOT, ConfigManager.tr("生命值 %.1f (≤%.0f)", "Health %.1f (<=%.0f)"), hp, hpLim),
                orReasons, andReasons);
        if (healthRule.get() == RuleMode.AND) { totalAndRules++; if (hp <= hpLim) matchedAndRules++; }

        // 4. Visual Range Player
        Player threat = findThreateningPlayer();
        boolean hasThreat = threat != null;
        String threatDesc = hasThreat
                ? ConfigManager.tr("视野出现玩家 " + threat.getGameProfile().name()
                        + String.format(Locale.ROOT, " (%.1fm)", mc.player.distanceTo(threat)),
                  "Player spotted: " + threat.getGameProfile().name())
                : "";
        evalRule(playerRule.get(), hasThreat, threatDesc, orReasons, andReasons);
        if (playerRule.get() == RuleMode.AND) { totalAndRules++; if (hasThreat) matchedAndRules++; }

        // 5. Fireworks
        int rockets = countItem(Items.FIREWORK_ROCKET);
        int rockLim = (int) Math.round(minFireworks.get());
        evalRule(fireworkRule.get(), rockets <= rockLim,
                ConfigManager.tr("剩余烟花 " + rockets + " 个 (≤" + rockLim + ")", "Fireworks " + rockets + " (<=" + rockLim + ")"),
                orReasons, andReasons);
        if (fireworkRule.get() == RuleMode.AND) { totalAndRules++; if (rockets <= rockLim) matchedAndRules++; }

        // 6. Golden Apples
        int gapples = countItem(Items.ENCHANTED_GOLDEN_APPLE) + countItem(Items.GOLDEN_APPLE);
        int gapLim = (int) Math.round(minGapples.get());
        evalRule(gappleRule.get(), gapples <= gapLim,
                ConfigManager.tr("剩余金苹果 " + gapples + " 个 (≤" + gapLim + ")", "Gapples " + gapples + " (<=" + gapLim + ")"),
                orReasons, andReasons);
        if (gappleRule.get() == RuleMode.AND) { totalAndRules++; if (gapples <= gapLim) matchedAndRules++; }

        // 7. XP Bottles
        int xp = countItem(Items.EXPERIENCE_BOTTLE);
        int xpLim = (int) Math.round(minXpBottles.get());
        evalRule(xpBottleRule.get(), xp <= xpLim,
                ConfigManager.tr("剩余经验瓶 " + xp + " 个 (≤" + xpLim + ")", "XP Bottles " + xp + " (<=" + xpLim + ")"),
                orReasons, andReasons);
        if (xpBottleRule.get() == RuleMode.AND) { totalAndRules++; if (xp <= xpLim) matchedAndRules++; }

        // 8. Equipment / Elytra Durability
        String lowGear = findLowDurabilityGear((int) Math.round(minDurabilityPct.get()));
        boolean gearCritical = lowGear != null;
        evalRule(durabilityRule.get(), gearCritical, lowGear != null ? lowGear : "", orReasons, andReasons);
        if (durabilityRule.get() == RuleMode.AND) { totalAndRules++; if (gearCritical) matchedAndRules++; }

        // 9. Void Fall
        boolean inVoid = mc.player.getY() < (mc.level.getMinY() - 2) && mc.player.getDeltaMovement().y < -0.08;
        evalRule(voidRule.get(), inVoid,
                ConfigManager.tr("坠入虚空底层 (Y=" + mc.player.getBlockY() + ")", "Void fall (Y=" + mc.player.getBlockY() + ")"),
                orReasons, andReasons);
        if (voidRule.get() == RuleMode.AND) { totalAndRules++; if (inVoid) matchedAndRules++; }

        String finalReason = null;
        if (!orReasons.isEmpty()) {
            finalReason = String.join(" / ", orReasons);
        } else if (totalAndRules > 0 && matchedAndRules == totalAndRules) {
            finalReason = String.join(ConfigManager.tr(" 且 ", " & "), andReasons);
        }

        if (finalReason != null) {
            executeDisconnectWithReport(finalReason, totems, hp, rockets, pops);
        }
    }

    private static void evalRule(RuleMode mode, boolean triggered, String desc,
                                 List<String> orReasons, List<String> andReasons) {
        if (!triggered) return;
        if (mode == RuleMode.OR) orReasons.add(desc);
        else if (mode == RuleMode.AND) andReasons.add(desc);
    }

    private void executeDisconnectWithReport(String reason, int totems, float hp, int rockets, int pops) {
        lastDisconnectMs = System.currentTimeMillis();
        boolean disabledSelf = autoDisableOnLog.get();
        if (disabledSelf) {
            setEnabled(false);
        }

        int px = mc.player.getBlockX();
        int py = mc.player.getBlockY();
        int pz = mc.player.getBlockZ();
        String dimName = getDimLabel(mc.level.dimension());

        List<String> nearbyNames = new ArrayList<>();
        for (Player p : mc.level.players()) {
            if (p == mc.player) continue;
            double d = mc.player.distanceTo(p);
            if (d <= 256.0) {
                boolean fr = FriendManager.isFriend(p);
                nearbyNames.add((fr ? "§a" : "§c") + p.getGameProfile().name()
                        + (fr ? ConfigManager.tr("[好友]", "[Friend]") : "")
                        + String.format(Locale.ROOT, "§7(%.0fm)", d));
            }
        }
        String nearbyStr = nearbyNames.isEmpty()
                ? ConfigManager.tr("§7无", "§7None")
                : String.join("§7, ", nearbyNames);

        MutableComponent report = Component.literal(ConfigManager.tr(
                "§b§l✦ Ye 自动下线保命战报 ✦\n\n",
                "§b§l✦ Ye AutoLog Battle Report ✦\n\n"))
                .append(Component.literal(ConfigManager.tr("§7触发原因: §c§l", "§7Trigger: §c§l") + reason + "\n"))
                .append(Component.literal(String.format(Locale.ROOT,
                        ConfigManager.tr("§7下线坐标: §f%s  X: %d, Y: %d, Z: %d\n", "§7Location: §f%s  X: %d, Y: %d, Z: %d\n"),
                        dimName, px, py, pz)))
                .append(Component.literal(String.format(Locale.ROOT,
                        ConfigManager.tr("§7角色状态: §a生命 %.1f  §6剩余图腾 %d  §b烟花 %d  §d连爆 %d次\n",
                                "§7Status: §aHP %.1f  §6Totems %d  §bRockets %d  §dPops %d\n"),
                        hp, totems, rockets, pops)))
                .append(Component.literal(ConfigManager.tr("§7周围玩家: ", "§7Nearby Players: ") + nearbyStr + "\n\n"))
                .append(Component.literal(ConfigManager.tr(
                        "§8触发时间 " + LocalTime.now().format(TIME_FMT)
                                + (disabledSelf ? " · 已自动关闭「自动下线」防止重连死循环" : ""),
                        "§8Time " + LocalTime.now().format(TIME_FMT)
                                + (disabledSelf ? " · AutoLog disabled to prevent relog loop" : ""))));

        mc.getConnection().getConnection().disconnect(report);
    }

    private Player findThreateningPlayer() {
        double maxDist = playerRange.get();
        boolean skipFriends = ignoreFriends.get();
        boolean checkArmed = armedOnly.get();
        Player closest = null;
        double bestDist = Double.MAX_VALUE;

        for (Player p : mc.level.players()) {
            if (p == mc.player || p.isSpectator() || p.isDeadOrDying()) continue;
            if (skipFriends && FriendManager.isFriend(p)) continue;
            double d = mc.player.distanceTo(p);
            if (d > maxDist) continue;
            if (checkArmed && !isPlayerArmed(p)) continue;
            if (d < bestDist) {
                bestDist = d;
                closest = p;
            }
        }
        return closest;
    }

    private static boolean isPlayerArmed(Player p) {
        for (EquipmentSlot slot : CHECKED_EQUIP) {
            if (!p.getItemBySlot(slot).isEmpty()) return true;
        }
        Item main = p.getMainHandItem().getItem();
        Item off = p.getOffhandItem().getItem();
        return main != Items.AIR || off == Items.TOTEM_OF_UNDYING || off == Items.END_CRYSTAL;
    }

    private String findLowDurabilityGear(int minPct) {
        for (EquipmentSlot slot : CHECKED_EQUIP) {
            ItemStack st = mc.player.getItemBySlot(slot);
            if (!st.isEmpty() && st.isDamageableItem()) {
                int max = st.getMaxDamage();
                int rem = Math.max(0, max - st.getDamageValue());
                int pct = Math.round((rem * 100.0f) / Math.max(1, max));
                if (pct <= minPct) {
                    return st.getHoverName().getString() + ConfigManager.tr(" 耐久仅 " + pct + "%", " durability " + pct + "%");
                }
            }
        }
        return null;
    }

    public int countItem(Item target) {
        if (mc.player == null) return 0;
        Inventory inv = mc.player.getInventory();
        int total = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack s = inv.getItem(i);
            if (!s.isEmpty() && s.is(target)) total += s.getCount();
        }
        ItemStack off = mc.player.getOffhandItem();
        if (!off.isEmpty() && off.is(target)) total += off.getCount();
        return total;
    }

    /**
     * Generates a crystal-clear live formula summary for the UI preview card!
     */
    public String buildFormulaSummary() {
        List<String> ors = new ArrayList<>();
        List<String> ands = new ArrayList<>();

        collectFormulaToken(popRule.get(), ConfigManager.tr("连爆≥" + Math.round(maxPops.get()), "Pop≥" + Math.round(maxPops.get())), ors, ands);
        collectFormulaToken(totemLeftRule.get(), ConfigManager.tr("图腾≤" + Math.round(minTotems.get()), "Totem≤" + Math.round(minTotems.get())), ors, ands);
        collectFormulaToken(healthRule.get(), ConfigManager.tr("血≤" + Math.round(minHealth.get()), "HP≤" + Math.round(minHealth.get())), ors, ands);
        collectFormulaToken(playerRule.get(), ConfigManager.tr("陌生玩家", "Player"), ors, ands);
        collectFormulaToken(fireworkRule.get(), ConfigManager.tr("烟花≤" + Math.round(minFireworks.get()), "Rocket≤" + Math.round(minFireworks.get())), ors, ands);
        collectFormulaToken(gappleRule.get(), ConfigManager.tr("金果≤" + Math.round(minGapples.get()), "Gapple≤" + Math.round(minGapples.get())), ors, ands);
        collectFormulaToken(xpBottleRule.get(), ConfigManager.tr("经验≤" + Math.round(minXpBottles.get()), "XP≤" + Math.round(minXpBottles.get())), ors, ands);
        collectFormulaToken(durabilityRule.get(), ConfigManager.tr("耐久≤" + Math.round(minDurabilityPct.get()) + "%", "Dur≤" + Math.round(minDurabilityPct.get()) + "%"), ors, ands);
        collectFormulaToken(voidRule.get(), ConfigManager.tr("虚空", "Void"), ors, ands);

        String orSep = ConfigManager.tr(" 或 ", " OR ");
        String andSep = ConfigManager.tr(" 且 ", " & ");
        List<String> top = new ArrayList<>(ors);
        if (!ands.isEmpty()) {
            top.add(ands.size() > 1 ? "(" + String.join(andSep, ands) + ")" : ands.get(0));
        }
        if (top.isEmpty()) {
            return ConfigManager.tr("未启用任何触发条件", "No active triggers");
        }
        return String.join(orSep, top);
    }

    private static void collectFormulaToken(RuleMode m, String token, List<String> ors, List<String> ands) {
        if (m == RuleMode.OR) ors.add(token);
        else if (m == RuleMode.AND) ands.add(token);
    }

    private static String getDimLabel(net.minecraft.resources.ResourceKey<Level> dim) {
        if (dim == Level.NETHER) return ConfigManager.tr("下界", "Nether");
        if (dim == Level.END) return ConfigManager.tr("末地", "The End");
        return ConfigManager.tr("主世界", "Overworld");
    }
}
