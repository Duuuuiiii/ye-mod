package com.ye.mod.core.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;

/**
 * Clickable action button rendered inside module settings.
 */
public class ActionSetting extends Setting<Void> {
    private final Runnable action;

    public ActionSetting(String id, String name, String description, Runnable action) {
        super(id, name, description, null);
        this.action = action;
    }

    public void trigger() {
        if (action != null) {
            action.run();
        }
    }

    @Override
    public JsonElement toJson() {
        return JsonNull.INSTANCE;
    }

    @Override
    public void fromJson(JsonElement element) {
        // Stateless action
    }
}
