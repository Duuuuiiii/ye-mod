package com.ye.mod.core.setting;

import com.google.gson.JsonElement;
import com.ye.mod.core.ConfigManager;

import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Base typed setting for Ye modules, with automatic/explicit split between Functional (upper) and Display/Color (lower) groups.
 */
public abstract class Setting<T> {
    private final String id;
    private final String name;
    private final String description;
    private final T defaultValue;
    protected T value;
    private BooleanSupplier visible = () -> true;
    private Consumer<T> onChange;
    private Boolean explicitDisplayGroup = null;

    protected Setting(String id, String name, String description, T defaultValue) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.defaultValue = defaultValue;
        this.value = defaultValue;
    }

    public String getId() { return id; }
    public String getName() { return com.ye.mod.core.YeI18n.text(name); }
    public String getRawName() { return name; }
    public String getDescription() { return description; }
    public T getDefaultValue() { return defaultValue; }
    public T get() { return value; }

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

    public void resetSilent() {
        this.value = defaultValue;
        if (onChange != null) {
            onChange.accept(defaultValue);
        }
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

    @SuppressWarnings("unchecked")
    public <S extends Setting<T>> S displayGroup() {
        this.explicitDisplayGroup = Boolean.TRUE;
        return (S) this;
    }

    @SuppressWarnings("unchecked")
    public <S extends Setting<T>> S functionGroup() {
        this.explicitDisplayGroup = Boolean.FALSE;
        return (S) this;
    }

    /**
     * Determines whether this setting belongs to the lower "显示与色彩" section in module details.
     */
    public boolean isDisplayGroup() {
        if (explicitDisplayGroup != null) return explicitDisplayGroup;
        if (this instanceof ColorSetting) return true;
        String lowId = id.toLowerCase(Locale.ROOT);
        if (lowId.contains("color") || lowId.contains("rgb") || lowId.equals("red") || lowId.equals("green")
                || lowId.equals("blue") || lowId.equals("red2") || lowId.equals("green2") || lowId.equals("blue2")
                || lowId.contains("alpha") || lowId.contains("opacity") || lowId.contains("backdrop")
                || lowId.contains("border") || lowId.contains("shadow") || lowId.contains("corner")
                || lowId.contains("rainbow") || lowId.contains("tint") || lowId.contains("brightness")
                || lowId.contains("saturation") || lowId.contains("scale") || lowId.contains("hide")
                || lowId.contains("onlyself") || lowId.equals("resetslotpos")) {
            return true;
        }
        return name.contains("色") || name.contains("透明") || name.contains("底板")
                || name.contains("描边") || name.contains("阴影") || name.contains("圆角")
                || name.contains("彩虹") || name.contains("亮度") || name.contains("隐藏")
                || name.contains("大小") || name.contains("缩放");
    }

    public abstract JsonElement toJson();

    public abstract void fromJson(JsonElement element);
}
