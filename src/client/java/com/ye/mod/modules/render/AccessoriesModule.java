package com.ye.mod.modules.render;

import com.ye.mod.core.Category;
import com.ye.mod.core.Module;
import com.ye.mod.core.setting.BoolSetting;
import com.ye.mod.core.setting.DoubleSetting;
import com.ye.mod.core.setting.EnumSetting;

/**
 * 3D head/face accessories (Sunglasses, Top Hat, Crown, Halo, Cat Ears).
 */
public class AccessoriesModule extends Module {
    public enum AccessoryType {
        SUNGLASSES("酷炫墨镜"),
        CROWN("王者金冠"),
        TOP_HAT("绅士礼帽"),
        HALO("天使光环"),
        CAT_EARS("猫耳发箍");

        public final String label;
        AccessoryType(String label) { this.label = label; }
    }

    public final EnumSetting<AccessoryType> type = addSetting(new EnumSetting<>(
            "type", "挂件样式", "墨镜 / 金冠 / 礼帽 / 天使光环 / 猫耳", AccessoryType.SUNGLASSES
    ));

    public final DoubleSetting scale = addSetting(new DoubleSetting(
            "scale", "大小", "缩放头部挂件尺寸", 1.0, 0.5, 1.8, 0.05
    ));

    public final DoubleSetting offsetY = addSetting(new DoubleSetting(
            "offsetY", "上下位置", "微调挂件上下高度", 0.0, -0.4, 0.4, 0.02
    ));

    public final DoubleSetting offsetZ = addSetting(new DoubleSetting(
            "offsetZ", "前后位置", "微调挂件前后偏移", 0.0, -0.4, 0.4, 0.02
    ));

    public final BoolSetting onlySelf = addSetting(new BoolSetting(
            "onlySelf", "仅自己显示", "只给自己佩戴挂件", true
    ));

    public AccessoriesModule() {
        super("accessories", "头部挂件", "Accessories", "为角色佩戴 3D 墨镜、金冠、礼帽、光环或猫耳挂件", Category.RENDER);
    }
}
