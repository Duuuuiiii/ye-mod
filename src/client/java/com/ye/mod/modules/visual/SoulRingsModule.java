package com.ye.mod.modules.visual;

import com.ye.mod.core.Category;
import com.ye.mod.core.Module;
import com.ye.mod.core.setting.BoolSetting;
import com.ye.mod.core.setting.DoubleSetting;
import com.ye.mod.core.setting.EnumSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.phys.Vec3;

/**
 * Renders rotating Soul Rings (魂环) around the player with customizable count,
 * radius, speed, rank colors, and first-person hide toggle.
 */
public class SoulRingsModule extends Module {
    public enum RingRank {
        MIXED("斗罗混环"),
        THOUSAND_PURPLE("千年紫环"),
        TEN_THOUSAND_BLACK("万年黑环"),
        HUNDRED_THOUSAND_RED("十万年血红"),
        GOD_GOLD("百万年神金");

        public final String label;
        RingRank(String label) { this.label = label; }
    }

    public final EnumSetting<RingRank> rank = addSetting(new EnumSetting<>(
            "rank", "魂环配色", "斗罗混环 / 千年紫 / 万年黑 / 十万年红 / 百万年神金", RingRank.MIXED
    ));

    public final DoubleSetting ringCount = addSetting(new DoubleSetting(
            "ringCount", "魂环数量", "同时悬浮旋转的魂环层数", 4.0, 1.0, 9.0, 1.0
    ));

    public final DoubleSetting radius = addSetting(new DoubleSetting(
            "radius", "魂环半径", "魂环围绕玩家的半径大小", 1.15, 0.6, 2.5, 0.05
    ));

    public final DoubleSetting spinSpeed = addSetting(new DoubleSetting(
            "spinSpeed", "旋转速度", "魂环符文与波纹的转动速度", 1.0, 0.2, 3.0, 0.1
    ));

    public final BoolSetting hideInFirstPerson = addSetting(new BoolSetting(
            "hideInFirstPerson", "第一人称隐藏", "第一人称视角时隐藏魂环，避免遮挡视线", true
    ));

    public SoulRingsModule() {
        super("soul_rings", "炫酷魂环", "Soul Rings", "在玩家周身渲染旋转的多层能量魂环，支持第一人称自动隐藏", Category.VISUAL);
    }

    public void emitGizmos() {
        if (!isEnabled()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        if (hideInFirstPerson.get() && mc.options.getCameraType().isFirstPerson()) return;

        int count = Math.max(1, Math.min(9, (int) Math.round(ringCount.get())));
        float baseR = radius.get().floatValue();
        double speed = spinSpeed.get();
        long now = System.currentTimeMillis();
        Vec3 basePos = mc.player.position();
        RingRank r = rank.get();

        for (int i = 0; i < count; i++) {
            float yOff = 0.20f + i * 0.24f + (float) Math.sin((now / 400.0) * speed + i) * 0.06f;
            float ringR = baseR + (i % 2 == 0 ? 0.0f : 0.12f);
            int rgb = pickRingRgb(r, i);
            int strokeCol = 0xE6000000 | rgb;
            int glowCol = 0x66000000 | rgb;

            Vec3 center = basePos.add(0, yOff, 0);
            Gizmos.circle(center, ringR, GizmoStyle.stroke(strokeCol, 3.2f));
            Gizmos.circle(center, ringR * 0.94f, GizmoStyle.stroke(glowCol, 1.5f));

            // Orbiting energy nodes on each ring
            double angle = ((now / 700.0) * speed * ((i % 2 == 0) ? 1 : -1)) + i * 1.1;
            for (int k = 0; k < 3; k++) {
                double a = angle + k * (Math.PI * 2.0 / 3.0);
                Vec3 node = center.add(Math.cos(a) * ringR, 0, Math.sin(a) * ringR);
                Gizmos.point(node, 0xFFFFFFFF, 4.0f);
            }
        }
    }

    private static int pickRingRgb(RingRank rank, int idx) {
        return switch (rank) {
            case THOUSAND_PURPLE -> 0xA855F7;
            case TEN_THOUSAND_BLACK -> 0x27272A;
            case HUNDRED_THOUSAND_RED -> 0xEF4444;
            case GOD_GOLD -> 0xFACC15;
            case MIXED -> {
                if (idx < 2) yield 0xFACC15;
                if (idx < 4) yield 0xA855F7;
                if (idx < 7) yield 0x1E1B2E;
                yield 0xEF4444;
            }
        };
    }
}
