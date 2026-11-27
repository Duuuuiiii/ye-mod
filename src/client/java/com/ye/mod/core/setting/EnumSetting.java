package com.ye.mod.core.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import java.util.Locale;

public class EnumSetting<E extends Enum<E>> extends Setting<E> {
    private final E[] constants;

    public EnumSetting(String id, String name, String description, E defaultValue) {
        super(id, name, description, defaultValue);
        this.constants = defaultValue.getDeclaringClass().getEnumConstants();
    }

    public E[] getConstants() {
        return constants;
    }

    public void cycle(int direction) {
        int idx = get().ordinal();
        int next = (idx + direction + constants.length) % constants.length;
        set(constants[next]);
    }

    @Override
    public JsonElement toJson() {
        return new JsonPrimitive(get().name());
    }

    @Override
    public void fromJson(JsonElement element) {
        if (element == null || !element.isJsonPrimitive()) return;
        String raw = element.getAsString();
        if (raw == null || raw.isEmpty()) return;

        // 1. Exact case-insensitive match
        for (E c : constants) {
            if (c.name().equalsIgnoreCase(raw)) {
                this.value = c;
                return;
            }
        }
        // 2. Fuzzy compatibility across mod updates (e.g. STYLE_DEFAULT -> DEFAULT, OBSIDIAN_PURPLE -> PURPLE)
        String upper = raw.toUpperCase(Locale.ROOT);
        for (E c : constants) {
            String cn = c.name().toUpperCase(Locale.ROOT);
            if (upper.contains(cn) || cn.contains(upper)) {
                this.value = c;
                return;
            }
        }
    }
}
