package com.ye.mod.core;

/**
 * Three concise top-level module categories requested by user:
 * 玩家模型 / 视觉渲染 / 特定功能
 */
public enum Category {
    PLAYER_MODEL("玩家模型", "Model", 0xFF38BDF8),
    VISUAL("视觉渲染", "Visual", 0xFFA855F7),
    UTILITY("特定功能", "Utility", 0xFF10B981);

    private final String displayName;
    private final String englishName;
    private final int accentColor;

    Category(String displayName, String englishName, int accentColor) {
        this.displayName = displayName;
        this.englishName = englishName;
        this.accentColor = accentColor;
    }

    public String getDisplayName() {
        return ConfigManager.english ? englishName : displayName;
    }

    public String getBadge() {
        return englishName.substring(0, 2).toUpperCase();
    }

    public int getAccentColor() {
        return accentColor;
    }
}
