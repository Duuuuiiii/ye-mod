package com.ye.mod.modules.render;

import com.ye.mod.core.Category;
import com.ye.mod.core.Module;
import com.ye.mod.core.setting.BoolSetting;
import com.ye.mod.core.setting.DoubleSetting;
import com.ye.mod.core.setting.EnumSetting;
import com.ye.mod.core.setting.Setting;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 3D Head & Face Accessories split into 2 mutually exclusive categories (1 Head + 1 Face),
 * with independent Scale / Y / Z position settings saved per accessory.
 */
public class AccessoriesModule extends Module {
    public enum HeadItem {
        NONE("无"),
        CROWN("王者金冠"),
        TOP_HAT("绅士礼帽"),
        HALO("天使光环"),
        CAT_EARS("猫耳发箍"),
        DEVIL_HORNS("恶魔双角"),
        DRAGON_HORNS("末影龙角"),
        SPROUT("头顶嫩芽");

        public final String label;
        HeadItem(String label) { this.label = label; }
    }

    public enum FaceItem {
        NONE("无"),
        SUNGLASSES("酷炫墨镜"),
        CYBER_VISOR("赛博护目镜"),
        FOX_MASK("和风狐面"),
        EYEPATCH("单边眼罩");

        public final String label;
        FaceItem(String label) { this.label = label; }
    }

    public record ItemTransform(DoubleSetting scale, DoubleSetting y, DoubleSetting z) {}

    public final EnumSetting<HeadItem> headItem = addSetting(new EnumSetting<>(
            "headItem", "头部挂件", "头部同时只能佩戴 1 个", HeadItem.CAT_EARS
    ));

    public final EnumSetting<FaceItem> faceItem = addSetting(new EnumSetting<>(
            "faceItem", "面部挂件", "面部同时只能佩戴 1 个", FaceItem.SUNGLASSES
    ));

    public final BoolSetting onlySelf = addSetting(new BoolSetting(
            "onlySelf", "仅自己", "只给自己佩戴挂件", true
    ));

    private final Map<HeadItem, ItemTransform> headTransforms = new EnumMap<>(HeadItem.class);
    private final Map<FaceItem, ItemTransform> faceTransforms = new EnumMap<>(FaceItem.class);

    public AccessoriesModule() {
        super("accessories", "头面挂件", "Accessories", "头部/面部各选 1 个挂件，每个挂件位置大小独立保存", Category.RENDER);

        for (HeadItem item : HeadItem.values()) {
            if (item == HeadItem.NONE) continue;
            String p = "h_" + item.name().toLowerCase();
            DoubleSetting s = addSetting(new DoubleSetting(p + "_s", item.label + "·大小", "独立缩放", 1.0, 0.5, 1.8, 0.05));
            DoubleSetting y = addSetting(new DoubleSetting(p + "_y", item.label + "·上下", "独立上下偏移", 0.0, -0.4, 0.4, 0.02));
            DoubleSetting z = addSetting(new DoubleSetting(p + "_z", item.label + "·前后", "独立前后偏移", 0.0, -0.4, 0.4, 0.02));
            headTransforms.put(item, new ItemTransform(s, y, z));
        }

        for (FaceItem item : FaceItem.values()) {
            if (item == FaceItem.NONE) continue;
            String p = "f_" + item.name().toLowerCase();
            DoubleSetting s = addSetting(new DoubleSetting(p + "_s", item.label + "·大小", "独立缩放", 1.0, 0.5, 1.8, 0.05));
            DoubleSetting y = addSetting(new DoubleSetting(p + "_y", item.label + "·上下", "独立上下偏移", 0.0, -0.4, 0.4, 0.02));
            DoubleSetting z = addSetting(new DoubleSetting(p + "_z", item.label + "·前后", "独立前后偏移", 0.0, -0.4, 0.4, 0.02));
            faceTransforms.put(item, new ItemTransform(s, y, z));
        }
    }

    public ItemTransform getHeadTransform(HeadItem item) {
        return headTransforms.get(item);
    }

    public ItemTransform getFaceTransform(FaceItem item) {
        return faceTransforms.get(item);
    }

    /**
     * Returns only the settings relevant to the currently equipped Head & Face items.
     */
    public List<Setting<?>> getVisibleSettings() {
        List<Setting<?>> out = new ArrayList<>();
        out.add(headItem);
        ItemTransform ht = headTransforms.get(headItem.get());
        if (ht != null) {
            out.add(ht.scale());
            out.add(ht.y());
            out.add(ht.z());
        }
        out.add(faceItem);
        ItemTransform ft = faceTransforms.get(faceItem.get());
        if (ft != null) {
            out.add(ft.scale());
            out.add(ft.y());
            out.add(ft.z());
        }
        out.add(onlySelf);
        return out;
    }
}
