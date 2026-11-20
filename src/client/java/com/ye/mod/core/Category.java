package com.ye.mod.core;

/**
 * Module categories displayed in the Ye ClickGUI sidebar.
 */
public enum Category {
    RENDER("渲染", "RN", 0xFF38BDF8),
    VISUAL("视觉", "VS", 0xFF818CF8),
    PLAYER("玩家", "PL", 0xFF34D399),
    WORLD("世界", "WD", 0xFFFBBF24),
    SYSTEM("系统", "SY", 0xFFF472B6);

    private final String displayName;
    private final String badge;
    private final int accentColor;

    Category(String displayName, String badge, int accentColor) {
        this.displayName = displayName;
        this.badge = badge;
        this.accentColor = accentColor;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getBadge() {
        return badge;
    }

    public int getAccentColor() {
        return accentColor;
    }
}
