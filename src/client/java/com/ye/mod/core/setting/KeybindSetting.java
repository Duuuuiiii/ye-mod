package com.ye.mod.core.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.ye.mod.core.KeyCombo;
import net.minecraft.client.Minecraft;

/**
 * Sub-keybind setting supporting single keys, mouse side buttons, and Ctrl/Shift/Alt combinations.
 */
public class KeybindSetting extends Setting<Integer> {
    private boolean prevDown = false;

    public KeybindSetting(String id, String name, String description, int defaultKeyCombo) {
        super(id, name, description, defaultKeyCombo);
    }

    public boolean isDown() {
        int code = get();
        if (code <= 0) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.screen != null || mc.getWindow() == null) return false;
        return KeyCombo.isComboDown(mc.getWindow().handle(), code);
    }

    /**
     * Edge-triggered press check on client tick when no GUI screen is open.
     */
    public boolean consumePress() {
        int code = get();
        if (code <= 0) {
            prevDown = false;
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getWindow() == null) {
            prevDown = false;
            return false;
        }
        boolean nowDown = KeyCombo.isComboDown(mc.getWindow().handle(), code);
        boolean pressed = (mc.screen == null) && nowDown && !prevDown;
        prevDown = nowDown;
        return pressed;
    }

    public String formatKey() {
        return KeyCombo.format(get());
    }

    @Override
    public JsonElement toJson() {
        return new JsonPrimitive(get());
    }

    @Override
    public void fromJson(JsonElement element) {
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
            this.value = element.getAsInt();
        }
    }
}
