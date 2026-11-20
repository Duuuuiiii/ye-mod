package com.ye.mod.core.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

public class StringSetting extends Setting<String> {
    public StringSetting(String id, String name, String description, String defaultValue) {
        super(id, name, description, defaultValue);
    }

    @Override
    public JsonElement toJson() {
        return new JsonPrimitive(get());
    }

    @Override
    public void fromJson(JsonElement element) {
        if (element != null && element.isJsonPrimitive()) {
            this.value = element.getAsString();
        }
    }
}
