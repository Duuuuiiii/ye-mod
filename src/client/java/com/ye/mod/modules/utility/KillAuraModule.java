package com.ye.mod.modules.utility;

import com.ye.mod.core.Category;
import com.ye.mod.core.FriendManager;
import com.ye.mod.core.Module;
import com.ye.mod.core.setting.BoolSetting;
import com.ye.mod.core.setting.DoubleSetting;
import com.ye.mod.core.setting.EnumSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;

/**
 * Anti-Cheat Friendly KillAura Module (杀戮光环):
 * Uses 1.9+ true attack cooldown sync + Silent Server Rotation packets (no camera snap!).
 */
public class KillAuraModule extends Module {
    public enum Priority {
        DISTANCE("最近距离"),
        LOWEST_HP("最低血量");

        public final String label;
        Priority(String label) { this.label = label; }
    }

    public final DoubleSetting range = addSetting(new DoubleSetting(
            "range", "攻击距离", "自动攻击目标的最大距离 (格)", 3.8, 2.5, 6.0, 0.1
    ).functionGroup());

    public final BoolSetting silentRotate = addSetting(new BoolSetting(
            "silentRotate", "静默转头(防检测)", "仅向服务端发送瞄准角度包，本地画面不晃动", true
    ).functionGroup());

    public final BoolSetting waitCooldown = addSetting(new BoolSetting(
            "waitCooldown", "同步1.9+攻击冷却", "等待武器蓄力冷却满后再挥刀，最大化伤害并防反作弊", true
    ).functionGroup());

    public final EnumSetting<Priority> priority = addSetting(new EnumSetting<>(
            "priority", "目标优先级", "优先锁定距离最近或血量最低的敌人", Priority.DISTANCE
    ).functionGroup());

    public final BoolSetting targetPlayers = addSetting(new BoolSetting(
            "targetPlayers", "攻击玩家", "自动锁定敌方玩家（自动跳过好友）", true
    ).functionGroup());

    public final BoolSetting targetHostiles = addSetting(new BoolSetting(
            "targetHostiles", "攻击敌对生物", "自动锁定僵尸、苦力怕等敌对怪物", true
    ).functionGroup());

    public final BoolSetting targetPassives = addSetting(new BoolSetting(
            "targetPassives", "攻击中立/动物", "勾选后也会攻击周围被动生物", false
    ).functionGroup());

    public final BoolSetting ignoreFriends = addSetting(new BoolSetting(
            "ignoreFriends", "忽略好友白名单", "不攻击好友列表内的玩家", true
    ).functionGroup());

    public KillAuraModule() {
        super("kill_aura", "杀戮光环", "KillAura", "静默转头与真实冷却同步自动攻击周围目标", Category.UTILITY);
    }

    @Override
    public void onTick() {
        if (!isEnabled()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.gameMode == null) return;
        if (mc.player.isDeadOrDying() || mc.player.isSpectator()) return;

        if (waitCooldown.get() && mc.player.getAttackStrengthScale(0.5f) < 0.92f) {
            return;
        }

        double maxR = range.get();
        double maxRSq = maxR * maxR;
        LivingEntity target = null;
        double bestScore = Double.MAX_VALUE;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof LivingEntity le && isValidTarget(mc.player, le, maxRSq)) {
                double score = (priority.get() == Priority.LOWEST_HP)
                        ? le.getHealth()
                        : mc.player.distanceToSqr(le);
                if (score < bestScore) {
                    bestScore = score;
                    target = le;
                }
            }
        }

        if (target == null) return;

        Vec3 eye = mc.player.getEyePosition(1.0f);
        Vec3 aim = target.getBoundingBox().getCenter();
        double dx = aim.x - eye.x, dy = aim.y - eye.y, dz = aim.z - eye.z;
        double horiz = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0f;
        float pitch = (float) -(Mth.atan2(dy, horiz) * (180.0 / Math.PI));

        if (silentRotate.get() && mc.getConnection() != null) {
            mc.getConnection().send(new ServerboundMovePlayerPacket.Rot(
                    yaw, pitch, mc.player.onGround(), mc.player.horizontalCollision));
        } else {
            mc.player.setYRot(yaw);
            mc.player.setXRot(pitch);
        }

        mc.gameMode.attack(mc.player, target);
        mc.player.swing(InteractionHand.MAIN_HAND);
    }

    private boolean isValidTarget(Player self, LivingEntity le, double maxRSq) {
        if (le == self || !le.isAlive() || le.isDeadOrDying() || le.isSpectator()) return false;
        if (self.distanceToSqr(le) > maxRSq) return false;
        if (le instanceof Player p) {
            if (!targetPlayers.get()) return false;
            return !ignoreFriends.get() || !FriendManager.isFriend(p);
        }
        if (le instanceof Enemy) return targetHostiles.get();
        return targetPassives.get();
    }
}
