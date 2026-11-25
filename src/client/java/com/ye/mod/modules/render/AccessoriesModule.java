package com.ye.mod.modules.render;

import com.ye.mod.core.Category;
import com.ye.mod.core.Module;
import com.ye.mod.core.setting.BoolSetting;
import com.ye.mod.core.setting.DoubleSetting;

/**
 * Stackable 3D head & face accessories (11 items, any combination can be worn together).
 */
public class AccessoriesModule extends Module {
    public final BoolSetting sunglasses = addSetting(new BoolSetting("sunglasses", "酷炫墨镜", "佩戴墨镜", true));
    public final BoolSetting crown = addSetting(new BoolSetting("crown", "王者金冠", "佩戴金冠", false));
    public final BoolSetting topHat = addSetting(new BoolSetting("topHat", "绅士礼帽", "佩戴礼帽", false));
    public final BoolSetting halo = addSetting(new BoolSetting("halo", "天使光环", "佩戴光环", false));
    public final BoolSetting catEars = addSetting(new BoolSetting("catEars", "猫耳发箍", "佩戴猫耳", false));
    public final BoolSetting devilHorns = addSetting(new BoolSetting("devilHorns", "恶魔双角", "佩戴恶魔角", false));
    public final BoolSetting dragonHorns = addSetting(new BoolSetting("dragonHorns", "末影龙角", "佩戴龙角", false));
    public final BoolSetting cyberVisor = addSetting(new BoolSetting("cyberVisor", "赛博护目镜", "佩戴发光护目镜", false));
    public final BoolSetting foxMask = addSetting(new BoolSetting("foxMask", "和风狐面", "佩戴狐狸面具", false));
    public final BoolSetting eyepatch = addSetting(new BoolSetting("eyepatch", "单边眼罩", "佩戴眼罩", false));
    public final BoolSetting sprout = addSetting(new BoolSetting("sprout", "头顶嫩芽", "头顶小绿芽", false));

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
            "onlySelf", "仅自己", "只给自己佩戴挂件", true
    ));

    public AccessoriesModule() {
        super("accessories", "头部挂件", "Accessories", "11 种 3D 头部挂件，支持自由叠戴组合", Category.RENDER);
    }
}
