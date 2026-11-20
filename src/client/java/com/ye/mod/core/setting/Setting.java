package com.ye.mod.core.setting;

import com.google.gson.JsonElement;
import com.ye.mod.core.ConfigManager;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Base typed setting for Ye modules.
 */
public abstract class Setting<T> {
    private final String id;
    private final String name;
    private final String description;
    private final T defaultValue;
    protected T value;
    private BooleanSupplier visible = () -> true;
    private Consumer<T> onChange;

    protected Setting(String id, String name, String description, T defaultValue) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.defaultValue = defaultValue;
        this.value = defaultValue;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public T getDefaultValue() {
        return defaultValue;
    }

    public T get() {
        return value;
    }

    public void set(T value) {
        this.value = value;
        if (onChange != null) {
            onChange.accept(value);
        }
        ConfigManager.saveAsync();
    }

    public void reset() {
        set(defaultValue);
    }

    public boolean isVisible() {
        return visible.getAsBoolean();
    }

    @SuppressWarnings("unchecked")
    public <S extends Setting<T>> S visible(BooleanSupplier visible) {
        this.visible = visible;
        return (S) this;
    }

    @SuppressWarnings("unchecked")
    public <S extends Setting<T>> S onChange(Consumer<T> onChange) {
        this.onChange = onChange;
        return (S) this;
    }

    public abstract JsonElement toJson();

    public abstract void fromJson(JsonElement element);
}
