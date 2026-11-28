package com.ye.mod.modules.visual;

import com.ye.mod.core.Category;
import com.ye.mod.core.Module;
import com.ye.mod.core.setting.BoolSetting;
import com.ye.mod.core.setting.ColorSetting;
import com.ye.mod.core.setting.DoubleSetting;
import com.ye.mod.core.setting.EnumSetting;

/**
 * Shader Outline & Fill Module (着色器描边):
 * Outlines/fills players, mobs, and first-person held items with customizable color and width.
 */
public class ShaderOutlineModule extends Module {
    public enum RenderMode {
        OUTLINE("仅描边"),
        FILL("仅填充"),
        BOTH("描边+填充");

        public final String label;
        RenderMode(String label) { this.label = label; }
    }

    public final EnumSetting<RenderMode> renderMode = addSetting(new EnumSetting<>(
            "renderMode", "渲染方式", "边缘描边 / 纯色填充 / 描边+填充", RenderMode.OUTLINE
    ).functionGroup());

    public final BoolSetting targetPlayers = addSetting(new BoolSetting(
            "targetPlayers", "人物描边", "对玩家模型应用着色器描边或填充", true
    ).functionGroup());

    public final BoolSetting targetMobs = addSetting(new BoolSetting(
            "targetMobs", "生物描边", "对周围生物应用着色器描边或填充", false
    ).functionGroup());

    public final BoolSetting targetHandItems = addSetting(new BoolSetting(
            "targetHandItems", "第一人称手与物品", "为第一人称手臂与手持物品边缘添加描边/填充", true
    ).functionGroup());

    public final ColorSetting outlineColor = addSetting(new ColorSetting(
            "outlineColor", "描边填充颜色", "自定义着色器轮廓与填充色", 0xFF38BDF8
    ));

    public final DoubleSetting lineWidth = addSetting(new DoubleSetting(
            "lineWidth", "轮廓粗细", "调节描边线条宽度", 2.0, 0.5, 6.0, 0.2
    ));

    public ShaderOutlineModule() {
        super("shader_outline", "着色器描边", "Shader Outline", "为人物、第一人称手持物品与生物添加边缘描边或填充", Category.VISUAL);
    }
}
