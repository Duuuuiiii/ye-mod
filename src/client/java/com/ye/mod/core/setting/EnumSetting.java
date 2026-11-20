package com.ye.mod.core.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

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
        if (element != null && element.isJsonPrimitive()) {
            String name = element.getAsString();
            for (E c : constants) {
                if (c.name().equalsIgnoreCase(name)) {
                    this.value = c;
                    return;
                }
            }
        }
    }
}
