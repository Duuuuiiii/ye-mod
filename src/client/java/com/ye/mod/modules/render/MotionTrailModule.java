package com.ye.mod.modules.render;

import com.ye.mod.core.Category;
import com.ye.mod.core.Module;
import com.ye.mod.core.setting.BoolSetting;
import com.ye.mod.core.setting.DoubleSetting;
import com.ye.mod.core.setting.EnumSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Honor-of-Kings inspired 3D motion trail with 4 styled presets,
 * configurable duration/width, and first-person camera clipping fix.
 */
public class MotionTrailModule extends Module {
    public enum TrailPreset {
        GALAXY_RIBBON("星河光带"),
        PHOENIX_FLAME("烈焰凤尾"),
        VOID_LIGHTNING("虚空雷电"),
        SAKURA_STREAM("樱落流光");

        public final String label;
        TrailPreset(String label) { this.label = label; }
    }

    public final EnumSetting<TrailPreset> preset = addSetting(new EnumSetting<>(
            "preset", "拖尾预设", "星河光带 / 烈焰凤尾 / 虚空雷电 / 樱落流光", TrailPreset.GALAXY_RIBBON
    ));

    public final DoubleSetting durationSec = addSetting(new DoubleSetting(
            "durationSec", "持续时间(秒)", "拖尾在空中停留消散的时间", 2.2, 0.5, 5.0, 0.1
    ));

    public final DoubleSetting width = addSetting(new DoubleSetting(
            "width", "光带宽度", "拖尾光带的粗细与张力", 4.5, 1.5, 9.0, 0.5
    ));

    public final BoolSetting onlyWhenFlying = addSetting(new BoolSetting(
            "onlyWhenFlying", "仅飞行显示", "只在鞘翅滑翔时渲染拖尾", false
    ));

    private record TrailPoint(Vec3 pos, long timeMs) {}
    private final Deque<TrailPoint> points = new ArrayDeque<>();
    private Vec3 lastAdded = null;

    public MotionTrailModule() {
        super("motion_trail", "炫彩拖尾", "Motion Trail", "王者荣耀风多种宽幅光带拖尾，修复第一人称前遮挡", Category.RENDER);
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

        Vec3 cur = mc.player.position().add(0, mc.player.isFallFlying() ? 0.45 : 0.28, 0);
        if (lastAdded == null || cur.distanceToSqr(lastAdded) > 0.03) {
            points.addLast(new TrailPoint(cur, System.currentTimeMillis()));
            lastAdded = cur;
        }

        long maxAgeMs = Math.round(durationSec.get() * 1000.0);
        long cutoff = System.currentTimeMillis() - maxAgeMs;
        while (points.size() > 160 || (!points.isEmpty() && points.peekFirst().timeMs < cutoff)) {
            points.pollFirst();
        }
    }

    public void emitGizmos() {
        if (!isEnabled() || points.size() < 2) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        boolean firstPerson = mc.options.getCameraType().isFirstPerson();
        Vec3 eyePos = mc.player.getEyePosition(1.0f);
        Vec3 lookVec = mc.player.getLookAngle();
        Vec3 playerPos = mc.player.position();

        TrailPoint[] arr = points.toArray(new TrailPoint[0]);
        long now = System.currentTimeMillis();
        long maxAgeMs = Math.max(300L, Math.round(durationSec.get() * 1000.0));
        float baseW = width.get().floatValue();
        TrailPreset p = preset.get();

        int coreRgb;
        int outerRgb;
        switch (p) {
            case PHOENIX_FLAME -> { coreRgb = 0xFDE047; outerRgb = 0xEF4444; }
            case VOID_LIGHTNING -> { coreRgb = 0xE879F9; outerRgb = 0x7C3AED; }
            case SAKURA_STREAM -> { coreRgb = 0xFFF1F2; outerRgb = 0xF472B6; }
            default -> { coreRgb = 0x7DD3FC; outerRgb = 0x6366F1; }
        }

        for (int i = 1; i < arr.length; i++) {
            Vec3 p0 = arr[i - 1].pos;
            Vec3 p1 = arr[i].pos;

            // First-person fix: never render segments close to or in front of the player's camera!
            if (firstPerson) {
                if (p1.distanceToSqr(playerPos) < 1.35 || p0.distanceToSqr(playerPos) < 1.35) continue;
                if (p1.subtract(eyePos).dot(lookVec) > -0.15) continue;
            }

            float ageFrac = Math.max(0.0f, Math.min(1.0f, 1.0f - (float) (now - arr[i].timeMs) / maxAgeMs));
            int coreA = Math.max(20, Math.round(ageFrac * 240));
            int outerA = Math.max(12, Math.round(ageFrac * 135));

            if (p == TrailPreset.VOID_LIGHTNING) {
                float jx = (float) Math.sin(i * 2.3 + now * 0.01) * 0.14f * ageFrac;
                float jy = (float) Math.cos(i * 1.9 + now * 0.01) * 0.14f * ageFrac;
                Vec3 jp0 = p0.add(jx, jy, 0);
                Vec3 jp1 = p1.add(-jx, -jy, 0);
                Gizmos.line(jp0, jp1, (outerA << 24) | outerRgb, baseW * 1.6f * ageFrac);
                Gizmos.line(jp0, jp1, (coreA << 24) | coreRgb, Math.max(1.2f, baseW * 0.55f * ageFrac));
            } else {
                // Wide twin-ribbon effect
                Gizmos.line(p0, p1, (outerA << 24) | outerRgb, baseW * 1.7f * ageFrac);
                Gizmos.line(p0.add(0, 0.12, 0), p1.add(0, 0.12, 0), (coreA << 24) | coreRgb, Math.max(1.2f, baseW * 0.7f * ageFrac));
                if ((i % 4 == 0) && (p == TrailPreset.GALAXY_RIBBON || p == TrailPreset.SAKURA_STREAM)) {
                    double ox = Math.sin(i * 1.7) * 0.25;
                    double oz = Math.cos(i * 1.7) * 0.25;
                    Gizmos.point(p1.add(ox, 0.18, oz), (coreA << 24) | coreRgb, 3.0f * ageFrac);
                }
            }
        }
    }
}
