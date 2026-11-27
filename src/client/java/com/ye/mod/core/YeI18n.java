package com.ye.mod.core;

import java.util.HashMap;
import java.util.Map;

/**
 * Lightweight bilingual lookup for Ye UI & HUD.
 * Default is 100% pure Chinese (ConfigManager.english == false);
 * toggling Language to English translates all UI labels, settings, and enum values cleanly to English.
 */
public final class YeI18n {
    private static final Map<String, String> ZH_TO_EN = new HashMap<>();

    static {
        // Common settings
        put("同步全局色彩", "Sync Global Color");
        put("仅滑翔显示", "Only When Gliding");
        put("大小", "Scale");
        put("不透明度", "Opacity");
        put("高级自定义", "Custom Tuning");
        put("仅自己", "Self Only");
        put("描边", "Outline");
        put("颜色", "Color");
        put("样式", "Style");
        put("预设", "Preset");
        put("数量", "Count");
        put("半径", "Radius");
        put("转速", "Spin Speed");
        put("密度", "Density");
        put("音效", "Hit Sound");
        put("臂长", "Arm Size");
        put("扫描半径", "Scan Radius");
        put("填充透明度", "Fill Alpha");
        put("高亮配色", "Highlight Color");
        put("翅膀大类", "Wing Category");
        put("羽翼材质", "Wing Texture");
        put("染色", "Tint");
        put("扇动速度", "Flap Speed");
        put("滑翔隐藏", "Hide Gliding");
        put("隐藏鞘翅", "Hide Elytra");
        put("隐藏披风", "Hide Cape");
        put("隐藏盔甲", "Hide Armor");
        put("替身模型", "Stand Model");
        put("左右偏移", "Offset X");
        put("上下偏移", "Offset Y");
        put("前后偏移", "Offset Z");
        put("预设配色", "Color Rank");
        put("起始高度", "Base Height");
        put("层间间距", "Layer Gap");
        put("第一人称隐藏", "Hide First Person");
        put("滑翔显示", "Show Gliding");

        // Global Color
        put("色彩模式", "Color Mode");
        put("色板选择", "Palette");
        put("红 (R)", "Red (R)");
        put("绿 (G)", "Green (G)");
        put("蓝 (B)", "Blue (B)");
        put("自定义呼吸副色", "Custom 2nd RGB");
        put("副色红 (R2)", "2nd Red (R2)");
        put("副色绿 (G2)", "2nd Green (G2)");
        put("副色蓝 (B2)", "2nd Blue (B2)");
        put("流光速度", "Cycle Speed");
        put("饱和度", "Saturation");
        put("追光相位差", "Phase Wave");
        put("同步菜单主题色", "Sync Menu Theme");
        put("一键开启全部同步", "Sync All Modules");
        put("一键关闭全部同步", "Unsync All Modules");

        // HUD
        put("配色", "Palette");
        put("HUD大小", "HUD Scale");
        put("药水样式", "Potion Mode");
        put("药水大小", "Potion Scale");
        put("药水效果", "Potion HUD");
        put("隐藏原版药水", "Hide Vanilla Potions");
        put("双维坐标", "Dual Coords");
        put("多行坐标", "Multi-line Coords");
        put("移动速度", "Speed");
        put("时间与在线", "Time & Online");
        put("帧率与延迟", "FPS & Ping");
        put("装备耐久", "Armor HUD");
        put("物资计数", "PvP Items");
        put("背包预览", "Inventory Grid");
        put("附近掉落物", "Nearby Drops");
        put("开启模块列表", "Active Modules");
        put("半透底板", "Card Backdrop");
        put("重置全部位置", "Reset All Positions");

        // AutoLog
        put("连爆图腾规则", "Totem Pop Rule");
        put("连爆次数阈值", "Max Totem Pops");
        put("连爆重置秒数", "Pop Reset Window (s)");
        put("剩余图腾规则", "Totems Left Rule");
        put("图腾少于等于", "Min Totems Left");
        put("低血量规则", "Low Health Rule");
        put("生命少于等于", "Health Threshold");
        put("计入吸收黄心", "Include Absorption");
        put("视野玩家规则", "Visual Range Player");
        put("玩家检测距离", "Player Range (m)");
        put("忽略白名单好友", "Ignore Friends");
        put("仅武装玩家触发", "Armed Players Only");
        put("背包烟花规则", "Fireworks Rule");
        put("烟花少于等于", "Min Fireworks");
        put("背包金苹果规则", "Golden Apples Rule");
        put("金苹果少于等于", "Min Golden Apples");
        put("背包经验瓶规则", "XP Bottles Rule");
        put("经验瓶少于等于", "Min XP Bottles");
        put("装备耐久规则", "Low Durability Rule");
        put("耐久低于百分比", "Min Durability (%)");
        put("坠入虚空规则", "Void Fall Rule");
        put("触发后自动关闭", "Auto-Disable on Log");
        put("进服保护秒数", "Join Grace Period (s)");
        put("准星加删好友", "Toggle Crosshair Friend");
        put("附近玩家加好友", "Add Nearby Friends");
        put("清空好友名单", "Clear Friends");

        // Entity ESP
        put("高亮方式", "Highlight Style");
        put("准星追踪线", "Tracer Lines");
        put("追踪线起点", "Tracer Origin");
        put("追踪线按距离变色", "Distance Color");
        put("信息微标牌", "2D Info Tag");
        put("玩家目标", "Players");
        put("好友独立配色", "Distinct Friend Color");
        put("敌对生物", "Hostile Mobs");
        put("友好与中立生物", "Passive Mobs");
        put("末影水晶", "End Crystals");
        put("地面掉落物", "Dropped Items");
        put("投掷物与载具", "Projectiles & Vehicles");
        put("最大透视距离", "Max ESP Range");
        put("透视填充浓度", "Box Fill Alpha");

        // Enum values
        put("关闭", "Off");
        put("满足即退(或)", "Trigger (OR)");
        put("组合满足(且)", "Combine (AND)");
        put("精选色板", "Palette");
        put("自定义RGB", "Custom RGB");
        put("流光彩虹", "Rainbow");
        put("双色呼吸", "Breathing");
        put("冰晶青", "Ice Cyan");
        put("赛博紫", "Cyber Purple");
        put("绯红赤", "Crimson");
        put("鎏金耀", "Royal Gold");
        put("翡翠绿", "Emerald");
        put("樱花粉", "Sakura Pink");
        put("纯净白", "Pure White");
        put("发光轮廓", "Shader Outline");
        put("战术四角框", "Tactical 2D");
        put("立体透视框", "3D Hitbox");
        put("轮廓+四角框", "Outline + 2D");
        put("轮廓+立体框", "Outline + 3D");
        put("全开叠加", "All Combined");
        put("屏幕正中", "Crosshair");
        put("屏幕底部", "Screen Bottom");
        put("表模式", "Table");
        put("条模式", "Bar");
        put("经典龙翼", "PvP Dragon");
        put("四节羽翼", "Avian Wings");
        put("原色", "Original");
        put("流光", "Chroma");
    }

    private YeI18n() {}

    private static void put(String zh, String en) {
        ZH_TO_EN.put(zh, en);
    }

    public static String text(String zh) {
        if (!ConfigManager.english || zh == null) return zh;
        return ZH_TO_EN.getOrDefault(zh, zh);
    }
}
