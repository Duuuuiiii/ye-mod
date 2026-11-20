package com.ye.mod.core;

import com.ye.mod.core.setting.Setting;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Base class for all modular features in Ye.
 */
public abstract class Module {
    protected final Minecraft mc = Minecraft.getInstance();

    private final String id;
    private final String name;
    private final String subtitle;
    private final String description;
    private final Category category;
    private final List<Setting<?>> settings = new ArrayList<>();

    private boolean enabled;
    private int keybind = -1;

    protected Module(String id, String name, String subtitle, String description, Category category) {
        this.id = id;
        this.name = name;
        this.subtitle = subtitle;
        this.description = description;
        this.category = category;
    }

    protected <S extends Setting<?>> S addSetting(S setting) {
        settings.add(setting);
        return setting;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getSubtitle() {
        return subtitle;
    }

    public String getDescription() {
        return description;
    }

    public Category getCategory() {
        return category;
    }

    public List<Setting<?>> getSettings() {
        return Collections.unmodifiableList(settings);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        setEnabled(enabled, false);
    }

    public void setEnabled(boolean enabled, boolean silent) {
        if (this.enabled == enabled) return;
        this.enabled = enabled;
        if (!silent) {
            if (enabled) {
                onEnable();
            } else {
                onDisable();
            }
            ConfigManager.saveAsync();
        }
    }

    public void toggle() {
        setEnabled(!enabled, false);
    }

    public int getKeybind() {
        return keybind;
    }

    public void setKeybind(int keybind) {
        this.keybind = keybind;
        ConfigManager.saveAsync();
    }

    public void setKeybindSilent(int keybind) {
        this.keybind = keybind;
    }

    protected void onEnable() {}

    protected void onDisable() {}

    public void onTick() {}
}
