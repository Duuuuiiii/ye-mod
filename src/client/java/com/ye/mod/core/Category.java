package com.ye.mod.core;

/**
 * Module categories:
 * - PLAYER_MODEL / VISUAL / UTILITY are displayed together in one smooth vertical-scroll "功能" page!
 * - HUD modules are displayed in their own dedicated "HUD" page!
 */
public enum Category {
    PLAYER_MODEL("玩家模型", "Character"),
    VISUAL("视觉渲染", "Visuals"),
    UTILITY("特定功能", "Utility"),
    HUD("HUD组件", "HUD");

    public final String zh;
    public final String en;

    Category(String zh, String en) {
        this.zh = zh;
        this.en = en;
    }

    public String label() {
        return ConfigManager.tr(zh, en);
    }
}
