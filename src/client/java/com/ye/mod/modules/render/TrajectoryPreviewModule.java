package com.ye.mod.modules.render;

import com.ye.mod.core.Category;
import com.ye.mod.core.Module;
import com.ye.mod.core.setting.BoolSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.EggItem;
import net.minecraft.world.item.EnderpearlItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SnowballItem;
import net.minecraft.world.item.ThrowablePotionItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Predicts and renders 3D trajectory arcs and landing highlights for Ender Pearls,
 * Bows, Crossbows, Tridents, Potions, Snowballs, and Eggs.
 */
public class TrajectoryPreviewModule extends Module {
    public final BoolSetting highlightTarget = addSetting(new BoolSetting(
            "highlightTarget", "高亮落点方块/实体", "在抛射物预计命中的位置显示高亮框", true
    ));

    public TrajectoryPreviewModule() {
        super("trajectory_preview", "抛物线轨迹预测", "Trajectory Preview", "手持末影珍珠/弓箭/药水时实时预测 3D 飞行轨迹与落点", Category.RENDER);
    }

    public void emitGizmos() {
        if (!isEnabled()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        ItemStack stack = mc.player.getMainHandItem();
        if (!isTrackable(stack)) {
            stack = mc.player.getOffhandItem();
            if (!isTrackable(stack)) return;
        }

        Item item = stack.getItem();
        float velocity = 1.5f;
        float gravity = 0.03f;
        float drag = 0.99f;
        int color = 0xFF38BDF8;

        if (item instanceof BowItem) {
            if (!mc.player.isUsingItem()) return;
            int used = mc.player.getTicksUsingItem();
            float power = BowItem.getPowerForTime(used);
            if (power < 0.1f) return;
            velocity = power * 3.0f;
            gravity = 0.05f;
            color = 0xFF4ADE80;
        } else if (item instanceof CrossbowItem) {
            velocity = 3.15f;
            gravity = 0.05f;
            color = 0xFF4ADE80;
        } else if (item instanceof TridentItem) {
            if (!mc.player.isUsingItem()) return;
            velocity = 2.5f;
            gravity = 0.05f;
            color = 0xFF22D3EE;
        } else if (item instanceof ThrowablePotionItem) {
            velocity = 0.5f;
            gravity = 0.05f;
            color = 0xFFF472B6;
        } else if (item instanceof EnderpearlItem) {
            color = 0xFFA855F7;
        }

        float yaw = mc.player.getYRot();
        float pitch = mc.player.getXRot();
        double radYaw = Math.toRadians(yaw);
        double radPitch = Math.toRadians(pitch);

        Vec3 pos = mc.player.getEyePosition(1.0f).add(
                -Math.cos(radYaw) * 0.16,
                -0.10,
                -Math.sin(radYaw) * 0.16
        );

        double vx = -Mth.sin((float) radYaw) * Mth.cos((float) radPitch);
        double vy = -Mth.sin((float) radPitch);
        double vz = Mth.cos((float) radYaw) * Mth.cos((float) radPitch);
        Vec3 vel = new Vec3(vx, vy, vz).normalize().scale(velocity);

        Vec3 prev = pos;
        for (int step = 0; step < 120; step++) {
            Vec3 next = pos.add(vel);
            BlockHitResult blockHit = mc.level.clip(new ClipContext(
                    pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));

            Vec3 searchEnd = blockHit.getType() != HitResult.Type.MISS ? blockHit.getLocation() : next;
            AABB box = new AABB(pos, searchEnd).inflate(0.5);
            EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(
                    mc.player, pos, searchEnd, box,
                    e -> !e.isSpectator() && e.isPickable() && e != mc.player,
                    pos.distanceToSqr(searchEnd));

            if (entityHit != null) {
                Vec3 hitPos = entityHit.getLocation();
                Gizmos.line(prev, hitPos, 0xFFEF4444, 2.5f).setAlwaysOnTop();
                if (highlightTarget.get()) {
                    Entity target = entityHit.getEntity();
                    Gizmos.cuboid(target.getBoundingBox(), GizmoStyle.strokeAndFill(0xFFEF4444, 2.0f, 0x44EF4444)).setAlwaysOnTop();
                }
                return;
            }

            if (blockHit.getType() != HitResult.Type.MISS) {
                Vec3 hitPos = blockHit.getLocation();
                Gizmos.line(prev, hitPos, color, 2.2f).setAlwaysOnTop();
                if (highlightTarget.get()) {
                    BlockPos bp = blockHit.getBlockPos();
                    Gizmos.cuboid(bp, GizmoStyle.strokeAndFill(color, 2.0f, (0x44 << 24) | (color & 0x00FFFFFF))).setAlwaysOnTop();
                }
                return;
            }

            Gizmos.line(prev, next, color, 2.0f).setAlwaysOnTop();
            prev = next;
            pos = next;
            vel = vel.scale(drag).add(0, -gravity, 0);
        }
    }

    private static boolean isTrackable(ItemStack stack) {
        if (stack.isEmpty()) return false;
        Item item = stack.getItem();
        return item instanceof EnderpearlItem
                || item instanceof SnowballItem
                || item instanceof EggItem
                || item instanceof ThrowablePotionItem
                || item instanceof BowItem
                || item instanceof CrossbowItem
                || item instanceof TridentItem;
    }
}
