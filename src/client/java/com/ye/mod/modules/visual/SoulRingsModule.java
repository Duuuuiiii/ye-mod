package com.ye.mod.modules.visual;

import com.ye.mod.core.Category;
import com.ye.mod.core.Module;
import com.ye.mod.core.setting.BoolSetting;
import com.ye.mod.core.setting.DoubleSetting;
import com.ye.mod.core.setting.EnumSetting;

/**
 * Player-attached rotating 3D textured Soul Rings (魂环) rendered directly inside YeCosmeticLayer
 * so they move 1:1 with the player with zero lag.
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
            "spinSpeed", "旋转速度", "魂环符文旋转速度", 1.0, 0.2, 3.0, 0.1
    ));

    public final BoolSetting hideInFirstPerson = addSetting(new BoolSetting(
            "hideInFirstPerson", "第一人称隐藏", "第一人称视角隐藏魂环", true
    ));

    public final BoolSetting showWhenGliding = addSetting(new BoolSetting(
            "showWhenGliding", "滑翔时显示", "鞘翅滑翔时是否继续渲染魂环", true
    ));

    public SoulRingsModule() {
        super("soul_rings", "炫酷魂环", "Soul Rings", "绑定玩家身上 0 延迟跟随的多层质感符文魂环", Category.VISUAL);
    }

    public int pickRingRgb(int idx) {
        return switch (rank.get()) {
            case THOUSAND_PURPLE -> 0xA855F7;
            case TEN_THOUSAND_BLACK -> 0x2E2E38;
            case HUNDRED_THOUSAND_RED -> 0xEF4444;
            case GOD_GOLD -> 0xFACC15;
            case MIXED -> {
                if (idx < 2) yield 0xFACC15;
                if (idx < 4) yield 0xA855F7;
                if (idx < 7) yield 0x2E2E38;
                yield 0xEF4444;
            }
        };
    }
}
