package com.ye.mod.modules.world;

import com.ye.mod.core.Category;
import com.ye.mod.core.Module;
import com.ye.mod.core.setting.BoolSetting;
import com.ye.mod.core.setting.ColorSetting;
import com.ye.mod.core.setting.EnumSetting;
import com.ye.mod.render.YeWorldFxRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Block Break Progress Visualizer (挖掘进度):
 * - CENTER_GROW_BOX: Translucent cube growing from block center to full block size
 * - FACE_PROGRESS_BAR: Vanilla-style light-gray horizontal bar filling across the block face
 */
public class BreakProgressModule extends Module {
    public enum Mode {
        CENTER_GROW_BOX("中心放大方块"),
        FACE_PROGRESS_BAR("方块面进度条"),
        BOTH("双效叠加");

        public final String label;
        Mode(String label) { this.label = label; }
    }

    public final EnumSetting<Mode> mode = addSetting(new EnumSetting<>(
            "mode", "显示模式", "中心放大透视方块 / 方块面横向进度条 / 双效叠加", Mode.BOTH
    ).functionGroup());

    public final BoolSetting smoothAnim = addSetting(new BoolSetting(
            "smoothAnim", "平滑动画", "开启后进度平滑增长，关闭则按原版阶段一格一格跳动", true
    ).functionGroup());

    public final ColorSetting boxColor = addSetting(new ColorSetting(
            "boxColor", "方块高亮颜色", "中心放大方块边框与填充颜色", 0xFF38BDF8
    ));

    private float smoothProgress = 0.0f;
    private BlockPos lastPos = null;

    public BreakProgressModule() {
        super("break_progress", "挖掘进度", "Break Progress", "在正在挖掘的方块上渲染中心放大方块或横向进度条", Category.UTILITY);
    }

    public void emitGizmos() {
        if (!isEnabled()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameMode == null || !mc.gameMode.isDestroying()) {
            smoothProgress = 0.0f;
            lastPos = null;
            return;
        }
        if (!(mc.hitResult instanceof BlockHitResult bhr) || bhr.getType() != HitResult.Type.BLOCK) return;
        BlockPos pos = bhr.getBlockPos();
        if (!pos.equals(lastPos)) {
            lastPos = pos;
            smoothProgress = 0.0f;
        }
        int stage = mc.gameMode.getDestroyStage();
        if (stage < 0) return;
        float target = Math.min(1.0f, (stage + 1) / 10.0f);
        smoothProgress = smoothAnim.get() ? (smoothProgress + (target - smoothProgress) * 0.25f) : target;

        Mode m = mode.get();
        if (m == Mode.CENTER_GROW_BOX || m == Mode.BOTH) {
            double half = 0.5 * Math.max(0.06, smoothProgress);
            AABB box = new AABB(
                    pos.getX() + 0.5 - half, pos.getY() + 0.5 - half, pos.getZ() + 0.5 - half,
                    pos.getX() + 0.5 + half, pos.getY() + 0.5 + half, pos.getZ() + 0.5 + half
            );
            int rgb = boxColor.getRgb();
            Gizmos.cuboid(box, GizmoStyle.strokeAndFill(0xEE000000 | rgb, 1.8f, 0x44000000 | rgb)).setAlwaysOnTop();
        }
    }

    public void render2d(GuiGraphics g) {
        if (!isEnabled()) return;
        Mode m = mode.get();
        if (m != Mode.FACE_PROGRESS_BAR && m != Mode.BOTH) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameMode == null || !mc.gameMode.isDestroying() || lastPos == null) return;

        float[] scr = YeWorldFxRenderer.projectWorldToGui(
                lastPos.getX() + 0.5, lastPos.getY() + 0.5, lastPos.getZ() + 0.5, g.guiWidth(), g.guiHeight());
        if (scr == null) return;

        int bw = 44, bh = 6;
        int bx = Math.round(scr[0] - bw * 0.5f);
        int by = Math.round(scr[1] - bh * 0.5f);
        g.fill(bx - 1, by - 1, bx + bw + 1, by + bh + 1, 0xCC1E1E24);
        g.fill(bx, by, bx + bw, by + bh, 0x883A3A44);
        int fillW = Math.max(1, Math.round(bw * Math.max(0.0f, Math.min(1.0f, smoothProgress))));
        g.fill(bx, by, bx + fillW, by + bh, 0xFFE2E8F0);
    }
}
