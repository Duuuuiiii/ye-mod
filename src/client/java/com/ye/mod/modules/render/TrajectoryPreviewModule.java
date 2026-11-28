package com.ye.mod.modules.render;

import com.ye.mod.core.Category;
import com.ye.mod.core.Module;
import com.ye.mod.core.setting.BoolSetting;
import com.ye.mod.core.setting.ColorSetting;
import com.ye.mod.core.setting.DoubleSetting;
import com.ye.mod.core.setting.EnumSetting;
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
    public enum EndMarkerMode {
        SPHERE("3D 落点球"),
        STANDALONE_CUBE("独立标记方块"),
        BLOCK_BOX("目标方块框");

        public final String label;
        EndMarkerMode(String label) { this.label = label; }
    }

    public final BoolSetting highlightTarget = addSetting(new BoolSetting(
            "highlightTarget", "显示落点标记", "在抛射物预计命中的位置显示高亮标记", true
    ).functionGroup());

    public final EnumSetting<EndMarkerMode> endMarkerMode = addSetting(new EnumSetting<>(
            "endMarkerMode", "落点标记样式", "3D 落点球 / 独立标记方块 / 世界目标方块框", EndMarkerMode.SPHERE
    ).visible(highlightTarget::get).functionGroup());

    public final DoubleSetting markerSize = addSetting(new DoubleSetting(
            "markerSize", "落点标记大小", "调节独立球体或独立方块标记的尺寸", 0.45, 0.15, 1.80, 0.05
    ).visible(() -> highlightTarget.get() && endMarkerMode.get() != EndMarkerMode.BLOCK_BOX).functionGroup());

    public final ColorSetting lineColor = addSetting(new ColorSetting(
            "lineColor", "轨迹线颜色", "自定义弹道预测线与落点颜色", 0xFF38BDF8
    ));

    public final DoubleSetting lineWidth = addSetting(new DoubleSetting(
            "lineWidth", "轨迹线粗细", "调节弹道预测线渲染宽度", 2.2, 0.8, 6.0, 0.2
    ));

    public TrajectoryPreviewModule() {
        super("trajectory_preview", "弹道预测", "Trajectory", "预测珍珠、弓箭与药水飞行落点", Category.UTILITY);
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
        int color = lineColor.getArgb();
        float lw = lineWidth.get().floatValue();

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

        float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        float yaw = mc.player.getViewYRot(pt);
        float pitch = mc.player.getViewXRot(pt);
        double radYaw = Math.toRadians(yaw);
        double radPitch = Math.toRadians(pitch);

        Vec3 pos = mc.player.getEyePosition(pt).add(
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
                Gizmos.line(prev, hitPos, color, lw).setAlwaysOnTop();
                if (highlightTarget.get()) {
                    drawEndMarker(hitPos, blockHit.getBlockPos(), color);
                }
                return;
            }

            Gizmos.line(prev, next, color, lw).setAlwaysOnTop();
            prev = next;
            pos = next;
            vel = vel.scale(drag).add(0, -gravity, 0);
        }
    }

    private void drawEndMarker(Vec3 hitPos, BlockPos bp, int color) {
        EndMarkerMode mode = endMarkerMode.get();
        if (mode == EndMarkerMode.BLOCK_BOX) {
            Gizmos.cuboid(bp, GizmoStyle.strokeAndFill(color, 2.0f, (0x44 << 24) | (color & 0x00FFFFFF))).setAlwaysOnTop();
            return;
        }
        double sz = markerSize.get();
        if (mode == EndMarkerMode.STANDALONE_CUBE) {
            AABB box = AABB.ofSize(hitPos, sz, sz, sz);
            Gizmos.cuboid(box, GizmoStyle.strokeAndFill(color, 2.0f, (0x55 << 24) | (color & 0x00FFFFFF))).setAlwaysOnTop();
            return;
        }
        // 3D Solid-Filled Sphere at exact hitPos
        double r = sz * 0.5;
        int fillCol = (0x88 << 24) | (color & 0x00FFFFFF);
        final int latSteps = 6;
        for (int i = 0; i < latSteps; i++) {
            double t0 = -1.0 + 2.0 * i / latSteps;
            double t1 = -1.0 + 2.0 * (i + 1) / latSteps;
            double y0 = r * t0, y1 = r * t1;
            double radMid = r * Math.sqrt(Math.max(0.05, 1.0 - ((t0 + t1) * 0.5) * ((t0 + t1) * 0.5)));
            AABB slice = new AABB(hitPos.x - radMid, hitPos.y + y0, hitPos.z - radMid,
                    hitPos.x + radMid, hitPos.y + y1, hitPos.z + radMid);
            Gizmos.cuboid(slice, GizmoStyle.fill(fillCol)).setAlwaysOnTop();
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
