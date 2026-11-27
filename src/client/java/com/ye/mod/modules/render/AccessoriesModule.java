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
 * 3D Head, Face & Tail Accessories with per-item transform controls.
 */
public class AccessoriesModule extends Module {
    public enum HeadItem {
        NONE("无"),
        CROWN("皇冠"),
        TOP_HAT("礼帽"),
        HALO("光环"),
        CAT_EARS("猫耳"),
        BUNNY_EARS("兔耳"),
        ANTLERS("鹿角"),
        DEVIL_HORNS("恶魔角"),
        DRAGON_HORNS("龙角"),
        GHOST_BUDDY("小幽灵"),
        SPROUT("嫩芽");

        public final String label;
        HeadItem(String label) { this.label = label; }
    }

    public enum FaceItem {
        NONE("无"),
        SUNGLASSES("墨镜"),
        CYBER_VISOR("护目镜"),
        FOX_MASK("狐面"),
        ROSE_MOUTH("玫瑰"),
        CLOWN_NOSE("红鼻"),
        EYEPATCH("眼罩");

        public final String label;
        FaceItem(String label) { this.label = label; }
    }

    public enum TailItem {
        NONE("无"),
        NINE_TAILS("九尾"),
        DEVIL_TAIL("恶魔尾"),
        DRAGON_TAIL("龙尾");

        public final String label;
        TailItem(String label) { this.label = label; }
    }

    public record ItemTransform(DoubleSetting scale, DoubleSetting y, DoubleSetting z) {}

    public final EnumSetting<HeadItem> headItem = addSetting(new EnumSetting<>(
            "headItem", "头部", "选择头部挂饰", HeadItem.CAT_EARS
    ));

    public final EnumSetting<FaceItem> faceItem = addSetting(new EnumSetting<>(
            "faceItem", "面部", "选择面部挂饰", FaceItem.SUNGLASSES
    ));

    public final EnumSetting<TailItem> tailItem = addSetting(new EnumSetting<>(
            "tailItem", "尾部", "选择尾部挂饰", TailItem.NONE
    ));

    public final BoolSetting customMode = addSetting(new BoolSetting(
            "customMode", "高级自定义", "展开各部位挂饰微调参数", false
    ));

    public final DoubleSetting tailScale = addSetting(new DoubleSetting(
            "tailScale", "尾巴大小", "缩放尾巴尺寸", 1.0, 0.5, 1.8, 0.05
    ));

    public final BoolSetting onlySelf = addSetting(new BoolSetting(
            "onlySelf", "仅自己", "只给自己佩戴挂件", true
    ));

    private final Map<HeadItem, ItemTransform> headTransforms = new EnumMap<>(HeadItem.class);
    private final Map<FaceItem, ItemTransform> faceTransforms = new EnumMap<>(FaceItem.class);

    public AccessoriesModule() {
        super("accessories", "挂饰", "Accessories", "头/面/尾立体挂饰，支持独立微调", Category.PLAYER_MODEL);
        setEnabled(true, true);

        for (HeadItem item : HeadItem.values()) {
            if (item == HeadItem.NONE) continue;
            String p = "h_" + item.name().toLowerCase();
            DoubleSetting s = addSetting(new DoubleSetting(p + "_s", item.label + "·大小", "独立缩放", 1.0, 0.5, 1.8, 0.05));
            DoubleSetting y = addSetting(new DoubleSetting(p + "_y", item.label + "·上下", "上下偏移", 0.0, -0.4, 0.4, 0.02));
            DoubleSetting z = addSetting(new DoubleSetting(p + "_z", item.label + "·前后", "前后偏移", 0.0, -0.4, 0.4, 0.02));
            headTransforms.put(item, new ItemTransform(s, y, z));
        }

        for (FaceItem item : FaceItem.values()) {
            if (item == FaceItem.NONE) continue;
            String p = "f_" + item.name().toLowerCase();
            DoubleSetting s = addSetting(new DoubleSetting(p + "_s", item.label + "·大小", "独立缩放", 1.0, 0.5, 1.8, 0.05));
            DoubleSetting y = addSetting(new DoubleSetting(p + "_y", item.label + "·上下", "上下偏移", 0.0, -0.4, 0.4, 0.02));
            DoubleSetting z = addSetting(new DoubleSetting(p + "_z", item.label + "·前后", "前后偏移", 0.0, -0.4, 0.4, 0.02));
            faceTransforms.put(item, new ItemTransform(s, y, z));
        }
    }

    public ItemTransform getHeadTransform(HeadItem item) {
        return headTransforms.get(item);
    }

    public ItemTransform getFaceTransform(FaceItem item) {
        return faceTransforms.get(item);
    }

    public List<Setting<?>> getVisibleSettings() {
        List<Setting<?>> out = new ArrayList<>();
        out.add(headItem);
        out.add(faceItem);
        out.add(tailItem);
        out.add(customMode);
        if (customMode.get()) {
            ItemTransform ht = headTransforms.get(headItem.get());
            if (ht != null) {
                out.add(ht.scale());
                out.add(ht.y());
                out.add(ht.z());
            }
            ItemTransform ft = faceTransforms.get(faceItem.get());
            if (ft != null) {
                out.add(ft.scale());
                out.add(ft.y());
                out.add(ft.z());
            }
            if (tailItem.get() != TailItem.NONE) {
                out.add(tailScale);
            }
            out.add(onlySelf);
        }
        return out;
    }
}
