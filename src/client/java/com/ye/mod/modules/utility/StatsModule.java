package com.ye.mod.modules.utility;

import com.ye.mod.core.Category;
import com.ye.mod.core.ConfigManager;
import com.ye.mod.core.Module;
import com.ye.mod.core.setting.ActionSetting;
import com.ye.mod.core.setting.BoolSetting;
import com.ye.mod.ui.YeQuickMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Combat & Survival Statistics Module (统计):
 * Tracks cumulative Player Kills (击杀玩家) and Deaths by Players (被玩家击杀) across sessions.
 */
public class StatsModule extends Module {
    public final BoolSetting trackKills = addSetting(new BoolSetting(
            "trackKills", "记录击杀玩家", "统计击杀敌方玩家次数", true
    ).functionGroup());

    public final BoolSetting trackDeaths = addSetting(new BoolSetting(
            "trackDeaths", "记录被玩家击杀", "统计被敌方玩家击败次数", true
    ).functionGroup());

    public final BoolSetting showToastOnEvent = addSetting(new BoolSetting(
            "showToastOnEvent", "击杀/被杀提示", "发生击杀或被玩家击败时弹出统计提示", true
    ));

    private final Map<Integer, Long> recentlyAttackedPlayers = new HashMap<>();
    private boolean wasDead = false;
    private long lastPlayerDamageMs = 0L;
    private String lastAttackerName = "";

    public StatsModule() {
        super("stats", "统计", "Stats", "统计击杀玩家次数与被玩家击杀次数，支持持久化存档与随时清零", Category.UTILITY);
        setEnabled(true, true);

        addSetting(new ActionSetting("resetStats", "清零击杀与被杀统计", "将已记录的击杀玩家和被杀次数归零", () -> {
            ConfigManager.statPlayerKills = 0;
            ConfigManager.statPlayerDeaths = 0;
            ConfigManager.saveAsync();
            YeQuickMenu.showToast(ConfigManager.tr("已清零战绩统计 (0 / 0)", "Reset stats (0 / 0)"), 0xFF38BDF8);
        }).functionGroup());
    }

    public void onPlayerAttacked(Entity target) {
        if (!isEnabled() || !trackKills.get()) return;
        if (target instanceof Player p && p != Minecraft.getInstance().player) {
            recentlyAttackedPlayers.put(p.getId(), System.currentTimeMillis());
        }
    }

    @Override
    public void onTick() {
        if (!isEnabled()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        long now = System.currentTimeMillis();

        // Detect player kills from recently attacked targets
        if (trackKills.get() && !recentlyAttackedPlayers.isEmpty()) {
            Iterator<Map.Entry<Integer, Long>> it = recentlyAttackedPlayers.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<Integer, Long> entry = it.next();
                if (now - entry.getValue() > 8000L) {
                    it.remove();
                    continue;
                }
                Entity ent = mc.level.getEntity(entry.getKey());
                if (ent instanceof Player p && (p.isDeadOrDying() || !p.isAlive() || p.getHealth() <= 0.0f)) {
                    ConfigManager.statPlayerKills++;
                    ConfigManager.saveAsync();
                    if (showToastOnEvent.get()) {
                        YeQuickMenu.showToast(ConfigManager.tr(
                                "击杀玩家 +1 (总计 " + ConfigManager.statPlayerKills + ")",
                                "Player Kill +1 (Total " + ConfigManager.statPlayerKills + ")"
                        ), 0xFF22C55E);
                    }
                    it.remove();
                }
            }
        }

        // Track nearby enemy player attacker when hurt
        if (mc.player.hurtTime > 0) {
            Player nearest = null;
            double bestDist = 7.5;
            for (Player other : mc.level.players()) {
                if (other == mc.player || other.isSpectator()) continue;
                double d = mc.player.distanceTo(other);
                if (d < bestDist) {
                    bestDist = d;
                    nearest = other;
                }
            }
            if (nearest != null) {
                lastPlayerDamageMs = now;
                lastAttackerName = nearest.getGameProfile().name();
            }
        }

        // Detect local player death caused by another player
        boolean isDead = mc.player.isDeadOrDying() || mc.player.getHealth() <= 0.0f;
        if (trackDeaths.get() && isDead && !wasDead) {
            if (now - lastPlayerDamageMs <= 10000L) {
                ConfigManager.statPlayerDeaths++;
                ConfigManager.saveAsync();
                if (showToastOnEvent.get()) {
                    YeQuickMenu.showToast(ConfigManager.tr(
                            "被玩家击杀 +1 (总计 " + ConfigManager.statPlayerDeaths + ")",
                            "Killed by Player +1 (Total " + ConfigManager.statPlayerDeaths + ")"
                    ), 0xFFEF4444);
                }
            }
        }
        wasDead = isDead;
    }

    public static double getKdRatio() {
        return ConfigManager.statPlayerDeaths <= 0
                ? ConfigManager.statPlayerKills
                : (ConfigManager.statPlayerKills / (double) ConfigManager.statPlayerDeaths);
    }
}
