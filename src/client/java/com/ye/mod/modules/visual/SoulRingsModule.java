package com.ye.mod.modules.visual;

import com.ye.mod.core.Category;
import com.ye.mod.core.Module;
import com.ye.mod.core.setting.BoolSetting;
import com.ye.mod.core.setting.DoubleSetting;
import com.ye.mod.core.setting.EnumSetting;

/**
 * 512x512 Ultra-HD Player-attached Soul Rings (魂环).
 * Clean preset mode by default; enabling "高级自定义" unlocks per-ring color, speed, and vertical spacing!
 */
public class SoulRingsModule extends Module {
    public enum RingRank {
        MIXED("混配"),
        GOD_GOLD("神金"),
        RED_100K("赤红"),
        PURPLE_1K("紫晶"),
        BLACK_10K("玄黑"),
        ICE_CYAN("冰蓝");

        public final String label;
        RingRank(String label) { this.label = label; }
    }

    public enum SingleColor {
        YELLOW("黄", 0xFACC15),
        PURPLE("紫", 0xA855F7),
        BLACK("黑", 0x272733),
        RED("红", 0xEF4444),
        GOLD("金", 0xF59E0B),
        CYAN("青", 0x22D3EE),
        WHITE("白", 0xF8FAFC),
        PINK("粉", 0xF472B6);

        public final String label;
        public final int rgb;
        SingleColor(String label, int rgb) {
            this.label = label;
            this.rgb = rgb;
        }
    }

    public final EnumSetting<RingRank> rank = addSetting(new EnumSetting<>(
            "rank", "预设配色", "整体魂环品阶预设", RingRank.MIXED
    ));

    public final BoolSetting syncGlobalColor = addSetting(new BoolSetting(
            "syncGlobalColor", "同步全局色彩", "所有魂环层统一跟随全局色彩与追光相位", false
    ));

    public final DoubleSetting ringCount = addSetting(new DoubleSetting(
            "ringCount", "数量", "悬浮魂环层数", 4.0, 1.0, 6.0, 1.0
    ));

    public final DoubleSetting radius = addSetting(new DoubleSetting(
            "radius", "半径", "魂环基础半径", 1.20, 0.6, 2.5, 0.05
    ));

    public final DoubleSetting spinSpeed = addSetting(new DoubleSetting(
            "spinSpeed", "转速", "整体旋转倍率", 1.0, 0.2, 3.0, 0.1
    ));

    public final BoolSetting customMode = addSetting(new BoolSetting(
            "customMode", "高级自定义", "单独调节每道魂环的颜色、独立转速与高度间距", false
    ));

    public final DoubleSetting baseHeight = addSetting(new DoubleSetting(
            "baseHeight", "起始高度", "第一道魂环相对高度", 1.38, 0.4, 2.0, 0.04
    ).visible(customMode::get));

    public final DoubleSetting verticalGap = addSetting(new DoubleSetting(
            "verticalGap", "层间间距", "相邻魂环上下间距", 0.24, 0.06, 0.50, 0.02
    ).visible(customMode::get));

    public final BoolSetting hideInFirstPerson = addSetting(new BoolSetting(
            "hideInFirstPerson", "第一人称隐藏", "第一人称视角隐藏魂环", true
    ).visible(customMode::get));

    public final BoolSetting showWhenGliding = addSetting(new BoolSetting(
            "showWhenGliding", "滑翔显示", "鞘翅滑翔时继续渲染魂环", true
    ).visible(customMode::get));

    @SuppressWarnings("unchecked")
    public final EnumSetting<SingleColor>[] perRingColor = new EnumSetting[6];
    public final DoubleSetting[] perRingSpeed = new DoubleSetting[6];

    public SoulRingsModule() {
        super("soul_rings", "魂环", "Soul Rings", "512×512 高清符文魂环，支持每环独立颜色与转速", Category.VISUAL);
        setEnabled(true, true);

        SingleColor[] defaults = {
                SingleColor.YELLOW, SingleColor.PURPLE, SingleColor.BLACK,
                SingleColor.RED, SingleColor.RED, SingleColor.GOLD
        };
        for (int i = 0; i < 6; i++) {
            final int ringIdx = i + 1;
            perRingColor[i] = addSetting(new EnumSetting<>(
                    "ringCol" + ringIdx, "第" + ringIdx + "环颜色", "单独指定颜色", defaults[i]
            ).visible(() -> customMode.get() && Math.round(ringCount.get()) >= ringIdx));

            perRingSpeed[i] = addSetting(new DoubleSetting(
                    "ringSpd" + ringIdx, "第" + ringIdx + "环转速", "正数顺时针，负数逆时针",
                    (i % 2 == 0) ? 1.0 : -1.0, -3.0, 3.0, 0.2
            ).visible(() -> customMode.get() && Math.round(ringCount.get()) >= ringIdx));
        }
    }

    public int pickRingRgb(int idx) {
        if (syncGlobalColor.get()) {
            return GlobalColorModule.resolveRgb(true, 0x38BDF8, idx);
        }
        if (customMode.get() && idx >= 0 && idx < perRingColor.length) {
            return perRingColor[idx].get().rgb;
        }
        return switch (rank.get()) {
            case PURPLE_1K -> 0xA855F7;
            case BLACK_10K -> 0x272733;
            case RED_100K -> 0xEF4444;
            case GOD_GOLD -> 0xFACC15;
            case ICE_CYAN -> 0x22D3EE;
            case MIXED -> {
                if (idx == 0) yield 0xFACC15;
                if (idx == 1) yield 0xA855F7;
                if (idx <= 3) yield 0x3F3F52;
                yield 0xEF4444;
            }
        };
    }

    public double pickRingSpeed(int idx) {
        double base = spinSpeed.get();
        if (customMode.get() && idx >= 0 && idx < perRingSpeed.length) {
            return base * perRingSpeed[idx].get();
        }
        return base * ((idx % 2 == 0) ? 1.0 : -1.0);
    }
}
