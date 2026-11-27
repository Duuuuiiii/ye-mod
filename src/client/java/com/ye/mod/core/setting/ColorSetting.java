package com.ye.mod.core.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import java.awt.Color;
import java.util.Locale;

/**
 * Interactive ARGB / HSV Color Setting with 2D draggable picker + hex input support in UI.
 */
public class ColorSetting extends Setting<Integer> {
    private float hue;
    private float sat;
    private float bri;

    public ColorSetting(String id, String name, String description, int defaultArgb) {
        super(id, name, description, 0xFF000000 | (defaultArgb & 0x00FFFFFF));
        syncHsvFromRgb(this.value);
        displayGroup();
    }

    private void syncHsvFromRgb(int argb) {
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        float[] hsb = Color.RGBtoHSB(r, g, b, null);
        this.hue = hsb[0];
        this.sat = hsb[1];
        this.bri = hsb[2];
    }

    @Override
    public void set(Integer val) {
        int clean = 0xFF000000 | (val & 0x00FFFFFF);
        syncHsvFromRgb(clean);
        super.set(clean);
    }

    @Override
    public void resetSilent() {
        super.resetSilent();
        syncHsvFromRgb(this.value);
    }

    public void setHsv(float h, float s, float b) {
        this.hue = Math.max(0f, Math.min(1f, h));
        this.sat = Math.max(0f, Math.min(1f, s));
        this.bri = Math.max(0f, Math.min(1f, b));
        int rgb = Color.HSBtoRGB(this.hue, this.sat, this.bri) & 0x00FFFFFF;
        super.set(0xFF000000 | rgb);
    }

    public float getHue() { return hue; }
    public float getSat() { return sat; }
    public float getBri() { return bri; }

    public int getRgb() {
        return get() & 0x00FFFFFF;
    }

    public int getArgb() {
        return 0xFF000000 | (get() & 0x00FFFFFF);
    }

    public String getHex() {
        return String.format(Locale.ROOT, "#%06X", getRgb());
    }

    public boolean setHex(String hex) {
        if (hex == null) return false;
        String s = hex.trim();
        if (s.startsWith("#")) s = s.substring(1);
        if (s.length() != 6) return false;
        try {
            int rgb = Integer.parseInt(s, 16);
            set(0xFF000000 | rgb);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    @Override
    public JsonElement toJson() {
        return new JsonPrimitive(getHex());
    }

    @Override
    public void fromJson(JsonElement element) {
        if (element.isJsonPrimitive()) {
            if (element.getAsJsonPrimitive().isNumber()) {
                this.value = 0xFF000000 | (element.getAsInt() & 0x00FFFFFF);
                syncHsvFromRgb(this.value);
            } else {
                String str = element.getAsString().trim();
                if (str.startsWith("#")) str = str.substring(1);
                try {
                    this.value = 0xFF000000 | (Integer.parseInt(str, 16) & 0x00FFFFFF);
                    syncHsvFromRgb(this.value);
                } catch (Exception ignored) {}
            }
        }
    }
}
