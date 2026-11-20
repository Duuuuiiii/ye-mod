package com.ye.mod.core.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

public class DoubleSetting extends Setting<Double> {
    private final double min;
    private final double max;
    private final double step;

    public DoubleSetting(String id, String name, String description, double defaultValue, double min, double max, double step) {
        super(id, name, description, defaultValue);
        this.min = min;
        this.max = max;
        this.step = step;
    }

    public double getMin() {
        return min;
    }

    public double getMax() {
        return max;
    }

    public double getStep() {
        return step;
    }

    @Override
    public void set(Double value) {
        double clamped = Math.max(min, Math.min(max, value));
        if (step > 0) {
            clamped = Math.round(clamped / step) * step;
        }
        super.set(clamped);
    }

    @Override
    public JsonElement toJson() {
        return new JsonPrimitive(get());
    }

    @Override
    public void fromJson(JsonElement element) {
        if (element != null && element.isJsonPrimitive()) {
            double v = element.getAsDouble();
            this.value = Math.max(min, Math.min(max, v));
        }
    }
}
