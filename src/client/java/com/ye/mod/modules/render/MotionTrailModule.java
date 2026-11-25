package com.ye.mod.modules.render;

import com.ye.mod.core.Category;
import com.ye.mod.core.Module;
import com.ye.mod.core.setting.BoolSetting;
import com.ye.mod.core.setting.DoubleSetting;
import com.ye.mod.core.setting.EnumSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Wide 3D ribbon motion trail rendered directly in YeCosmeticLayer (no thin GL lines),
 * with 4 Honor-of-Kings presets and forward-clipping prevention.
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
            "durationSec", "持续时间", "拖尾消散时间(秒)", 1.8, 0.5, 4.5, 0.1
    ));

    public final DoubleSetting width = addSetting(new DoubleSetting(
            "width", "光带宽度", "3D 宽幅光带的宽度(格)", 0.45, 0.15, 1.20, 0.05
    ));

    public final BoolSetting onlyWhenFlying = addSetting(new BoolSetting(
            "onlyWhenFlying", "仅飞行显示", "只在鞘翅滑翔时渲染拖尾", false
    ));

    public record TrailPoint(Vec3 pos, long timeMs) {}
    private final Deque<TrailPoint> points = new ArrayDeque<>();
    private Vec3 lastAdded = null;

    public MotionTrailModule() {
        super("motion_trail", "炫彩拖尾", "Motion Trail", "宽幅立体 3D 光带拖尾，多种预设，修复前后穿帮", Category.RENDER);
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

        // Offset slightly behind the player's back so it never spawns in front of the torso
        Vec3 look = mc.player.getLookAngle();
        Vec3 backOffset = new Vec3(-look.x, 0, -look.z);
        if (backOffset.lengthSqr() > 0.001) {
            backOffset = backOffset.normalize().scale(0.35);
        }
        Vec3 cur = mc.player.position().add(backOffset.x, 0.10, backOffset.z);

        if (lastAdded == null || cur.distanceToSqr(lastAdded) > 0.05) {
            points.addLast(new TrailPoint(cur, System.currentTimeMillis()));
            lastAdded = cur;
        }

        long maxAgeMs = Math.round(durationSec.get() * 1000.0);
        long cutoff = System.currentTimeMillis() - maxAgeMs;
        while (points.size() > 140 || (!points.isEmpty() && points.peekFirst().timeMs < cutoff)) {
            points.pollFirst();
        }
    }

    public List<TrailPoint> getPointsSnapshot() {
        return new ArrayList<>(points);
    }
}
