package com.ye.mod.modules.visual;

import com.ye.mod.core.Category;
import com.ye.mod.core.Module;
import com.ye.mod.core.setting.BoolSetting;
import com.ye.mod.core.setting.ColorSetting;
import com.ye.mod.core.setting.DoubleSetting;
import com.ye.mod.core.setting.EnumSetting;

/**
 * 1024x1024 + Smooth 3D Curved Torus Player-attached Soul Rings (魂环) with HDR Brightness & Custom Color Picker!
 */
public class SoulRingsModule extends Module {
    public enum RingRank {
        MIXED("混配"),
        GOD_GOLD("神金"),
        RED_100K("赤红"),
        PURPLE_1K("紫晶"),
        BLACK_10K("玄黑"),
        ICE_CYAN("冰蓝"),
        CUSTOM_COLOR("单色自选");

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
            "rank", "预设配色", "魂环整体品阶配色", RingRank.MIXED
    ).functionGroup());

    public final DoubleSetting ringCount = addSetting(new DoubleSetting(
            "ringCount", "数量", "悬浮魂环层数", 4.0, 1.0, 6.0, 1.0
    ).functionGroup());

    public final DoubleSetting radius = addSetting(new DoubleSetting(
            "radius", "半径", "魂环基础半径", 1.18, 0.6, 2.5, 0.05
    ).functionGroup());

    public final DoubleSetting spinSpeed = addSetting(new DoubleSetting(
            "spinSpeed", "转速", "整体旋转倍率", 1.0, 0.2, 3.0, 0.1
    ).functionGroup());

    public final DoubleSetting brightness = addSetting(new DoubleSetting(
            "brightness", "发光亮度", "调节魂环辉光强度（调高开启高亮叠加层）", 1.45, 0.30, 3.00, 0.05
    ));

    public final ColorSetting customRingColor = addSetting(new ColorSetting(
            "customRingColor", "自定义环色", "单色自选模式下的魂环颜色", 0xFF38BDF8
    ).visible(() -> rank.get() == RingRank.CUSTOM_COLOR));

    public final BoolSetting syncGlobalColor = addSetting(new BoolSetting(
            "syncGlobalColor", "跟随同步色", "跟随同步色模块的彩虹渐变或自选色", false
    ));

    public final BoolSetting customMode = addSetting(new BoolSetting(
            "customMode", "高级自定义", "单独调节每道魂环颜色、独立转速与高度间距", false
    ).functionGroup());

    public final DoubleSetting baseHeight = addSetting(new DoubleSetting(
            "baseHeight", "起始高度", "第一道魂环相对高度", 1.38, 0.4, 2.0, 0.04
    ).visible(customMode::get).functionGroup());

    public final DoubleSetting verticalGap = addSetting(new DoubleSetting(
            "verticalGap", "层间间距", "相邻魂环上下间距", 0.24, 0.06, 0.50, 0.02
    ).visible(customMode::get).functionGroup());

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
        super("soul_rings", "魂环", "Soul Rings", "立体曲面圆环与符文光阵，支持独立发光亮度、每环配色与转速控制", Category.VISUAL);
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
            ).visible(() -> customMode.get() && Math.round(ringCount.get()) >= ringIdx).functionGroup());
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
            case BLACK_10K -> 0x3F3F56;
            case RED_100K -> 0xEF4444;
            case GOD_GOLD -> 0xFACC15;
            case ICE_CYAN -> 0x22D3EE;
            case CUSTOM_COLOR -> customRingColor.getRgb();
            case MIXED -> {
                if (idx == 0) yield 0xFACC15;
                if (idx == 1) yield 0xA855F7;
                if (idx <= 3) yield 0x474761;
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
