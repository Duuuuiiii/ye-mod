package com.ye.mod.modules.render;

import com.ye.mod.core.Category;
import com.ye.mod.core.Module;
import com.ye.mod.core.setting.BoolSetting;
import com.ye.mod.core.setting.DoubleSetting;
import com.ye.mod.core.setting.EnumSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.phys.Vec3;

import java.awt.Color;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Leaves a smooth 3D glowing motion trail behind the player while moving or elytra-flying.
 */
public class MotionTrailModule extends Module {
    public enum TrailColor {
        CYBER_BLUE(0xFF38BDF8),
        ENDER_PURPLE(0xFFA855F7),
        FLAME_GOLD(0xFFFBBF24),
        CRIMSON_RED(0xFFEF4444),
        CHROMA(0xFFFFFFFF);

        final int rgb;
        TrailColor(int rgb) { this.rgb = rgb; }
    }

    public final EnumSetting<TrailColor> color = addSetting(new EnumSetting<>(
            "color", "拖尾配色", "选择运动光带的配色风格（CHROMA 为动态彩虹）", TrailColor.CHROMA
    ));

    public final DoubleSetting length = addSetting(new DoubleSetting(
            "length", "拖尾长度", "保留最近多少个轨迹节点", 40.0, 10.0, 120.0, 5.0
    ));

    public final BoolSetting onlyWhenFlying = addSetting(new BoolSetting(
            "onlyWhenFlying", "仅飞行时显示", "只在鞘翅滑翔时渲染光带拖尾", false
    ));

    private record TrailPoint(Vec3 pos, long timeMs) {}
    private final Deque<TrailPoint> points = new ArrayDeque<>();
    private Vec3 lastAdded = null;

    public MotionTrailModule() {
        super("motion_trail", "光影运动拖尾", "Motion Trail", "移动或鞘翅飞行时在身后留下 3D 霓虹光带轨迹", Category.RENDER);
    }

    @Override
    protected void onDisable() {
        points.clear();
        lastAdded = null;
    }

    @Override
    public void onTick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            points.clear();
            lastAdded = null;
            return;
        }
        if (onlyWhenFlying.get() && !mc.player.isFallFlying()) {
            points.clear();
            lastAdded = null;
            return;
        }

        Vec3 cur = mc.player.position().add(0, mc.player.isFallFlying() ? 0.45 : 0.25, 0);
        if (lastAdded == null || cur.distanceToSqr(lastAdded) > 0.04) {
            points.addLast(new TrailPoint(cur, System.currentTimeMillis()));
            lastAdded = cur;
        }

        int maxPts = Math.max(10, (int) Math.round(length.get()));
        long cutoff = System.currentTimeMillis() - 2500L;
        while (points.size() > maxPts || (!points.isEmpty() && points.peekFirst().timeMs < cutoff)) {
            points.pollFirst();
        }
    }

    public void emitGizmos() {
        if (!isEnabled() || points.size() < 2) return;
        TrailPoint[] arr = points.toArray(new TrailPoint[0]);
        long now = System.currentTimeMillis();
        TrailColor tc = color.get();

        for (int i = 1; i < arr.length; i++) {
            float progress = (float) i / (arr.length - 1);
            int alpha = Math.max(25, Math.min(240, Math.round(progress * 235)));
            int rgb;
            if (tc == TrailColor.CHROMA) {
                float hue = (((now / 15) + i * 8) % 360) / 360.0f;
                rgb = Color.HSBtoRGB(hue, 0.85f, 1.0f) & 0x00FFFFFF;
            } else {
                rgb = tc.rgb & 0x00FFFFFF;
            }
            int argb = (alpha << 24) | rgb;
            Gizmos.line(arr[i - 1].pos, arr[i].pos, argb, 1.5f + progress * 2.5f);
        }
    }
}
