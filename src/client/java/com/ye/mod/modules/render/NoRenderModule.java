package com.ye.mod.modules.render;

import com.ye.mod.core.Category;
import com.ye.mod.core.Module;
import com.ye.mod.core.setting.BoolSetting;

/**
 * Granularly disables unwanted overlays, particles, portal GUI lock, and sounds.
 */
public class NoRenderModule extends Module {
    // Screen & Portal
    public final BoolSetting noPortalOverlay = addSetting(new BoolSetting(
            "noPortalOverlay", "屏蔽传送门紫屏", "不渲染地狱传送门紫色漩涡遮罩", true
    ));

    public final BoolSetting noPortalGuiLock = addSetting(new BoolSetting(
            "noPortalGuiLock", "允许传送门内开 GUI", "取消传送门扭曲动画并允许在传送门内打开背包/聊天/菜单", true
    ));

    public final BoolSetting noNausea = addSetting(new BoolSetting(
            "noNausea", "屏蔽反胃效果", "不渲染反胃/眩晕屏幕扭曲", true
    ));

    public final BoolSetting noFire = addSetting(new BoolSetting(
            "noFire", "屏蔽火焰遮罩", "着火时不渲染挡视野的第一人称火焰", true
    ));

    public final BoolSetting noWater = addSetting(new BoolSetting(
            "noWater", "屏蔽水下遮罩", "在水下时不渲染蓝色水波贴图遮罩", false
    ));

    public final BoolSetting noBlockOverlay = addSetting(new BoolSetting(
            "noBlockOverlay", "屏蔽窒息方块遮罩", "卡在方块内时不渲染全屏方块贴图", true
    ));

    public final BoolSetting noPumpkinAndSnow = addSetting(new BoolSetting(
            "noPumpkinAndSnow", "屏蔽南瓜/细雪遮罩", "不渲染戴南瓜头黑框与细雪冻伤边缘", true
    ));

    public final BoolSetting noSpyglassOverlay = addSetting(new BoolSetting(
            "noSpyglassOverlay", "屏蔽望远镜黑框", "使用望远镜时不渲染圆形黑框", false
    ));

    public final BoolSetting noVignette = addSetting(new BoolSetting(
            "noVignette", "屏蔽边缘暗角", "关闭屏幕四周暗角阴影", true
    ));

    public final BoolSetting noTotemPop = addSetting(new BoolSetting(
            "noTotemPop", "屏蔽图腾动画", "触发不死图腾时不渲染全屏旋转动画", false
    ));

    public final BoolSetting noBossBar = addSetting(new BoolSetting(
            "noBossBar", "屏蔽 Boss 血条", "隐藏顶部 Boss 血条显示", false
    ));

    // Particles
    public final BoolSetting noParticles = addSetting(new BoolSetting(
            "noParticles", "屏蔽全部粒子", "禁用所有客户端粒子生成与渲染，提升帧率", false
    ));

    public final BoolSetting noExplosionParticles = addSetting(new BoolSetting(
            "noExplosionParticles", "屏蔽爆炸粒子", "仅屏蔽 TNT/水晶等爆炸产生的大量烟雾粒子", true
    ));

    // Sounds
    public final BoolSetting mutePortalSound = addSetting(new BoolSetting(
            "mutePortalSound", "屏蔽传送门声音", "静音地狱传送门嗡嗡声与传送音效", true
    ));

    public final BoolSetting muteExplosionSound = addSetting(new BoolSetting(
            "muteExplosionSound", "屏蔽爆炸声音", "静音爆炸声效", false
    ));

    public final BoolSetting muteThunderSound = addSetting(new BoolSetting(
            "muteThunderSound", "屏蔽打雷声音", "静音雷电与打雷音效", false
    ));

    public final BoolSetting muteAllSound = addSetting(new BoolSetting(
            "muteAllSound", "静音全部声音", "一键屏蔽所有游戏声音", false
    ));

    public NoRenderModule() {
        super("no_render", "屏蔽渲染与声音", "No Render & Sound", "按需屏蔽传送门禁用GUI、扭曲、遮罩、粒子和指定声音", Category.RENDER);
    }
}
