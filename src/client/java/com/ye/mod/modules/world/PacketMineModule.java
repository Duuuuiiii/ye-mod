package com.ye.mod.modules.world;

import com.ye.mod.core.Category;
import com.ye.mod.core.Module;
import com.ye.mod.core.setting.BoolSetting;
import com.ye.mod.core.setting.ColorSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * PacketMine Module (发包挖掘 — 3C3U / Grim 双发包模式):
 * Sends START + STOP destroy packets so mining continues server-side, with auto-remine support.
 */
public class PacketMineModule extends Module {
    public final BoolSetting autoRemine = addSetting(new BoolSetting(
            "autoRemine", "自动重挖", "方块被重新放置或未破坏时自动补发包继续挖掘", true
    ).functionGroup());

    public final ColorSetting mineColor = addSetting(new ColorSetting(
            "mineColor", "挖掘框颜色", "当前发包挖掘目标方块的标记颜色", 0xFFF97316
    ));

    private BlockPos activePos = null;
    private Direction activeDir = Direction.UP;
    private long startMs = 0L;
    private boolean wasAttackDown = false;

    public PacketMineModule() {
        super("packet_mine", "发包挖掘", "PacketMine", "3C3U/Grim 双发包后台挖掘，支持自动重挖与进度标记", Category.UTILITY);
    }

    @Override
    protected void onDisable() {
        activePos = null;
    }

    @Override
    public void onTick() {
        if (!isEnabled()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.getConnection() == null) return;

        boolean atkDown = mc.options.keyAttack.isDown();
        if (atkDown && !wasAttackDown && mc.hitResult instanceof BlockHitResult bhr && bhr.getType() == HitResult.Type.BLOCK) {
            BlockPos pos = bhr.getBlockPos();
            if (!mc.level.getBlockState(pos).isAir()) {
                activePos = pos;
                activeDir = bhr.getDirection();
                startMs = System.currentTimeMillis();
                sendMinePair(mc, activePos, activeDir);
            }
        }
        wasAttackDown = atkDown;

        if (activePos != null) {
            if (mc.player.distanceToSqr(activePos.getX() + 0.5, activePos.getY() + 0.5, activePos.getZ() + 0.5) > 36.0) {
                activePos = null;
                return;
            }
            if (mc.level.getBlockState(activePos).isAir()) {
                if (!autoRemine.get()) activePos = null;
                return;
            }
            if (autoRemine.get() && System.currentTimeMillis() - startMs > 1800L) {
                startMs = System.currentTimeMillis();
                sendMinePair(mc, activePos, activeDir);
            }
        }
    }

    private static void sendMinePair(Minecraft mc, BlockPos pos, Direction dir) {
        if (mc.getConnection() == null) return;
        mc.getConnection().send(new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, pos, dir));
        mc.getConnection().send(new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK, pos, dir));
    }

    public void emitGizmos() {
        if (!isEnabled() || activePos == null) return;
        int rgb = mineColor.getRgb();
        Gizmos.cuboid(activePos, GizmoStyle.strokeAndFill(0xEE000000 | rgb, 2.0f, 0x33000000 | rgb)).setAlwaysOnTop();
    }
}
