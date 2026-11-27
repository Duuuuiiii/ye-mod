package com.ye.mod.core;

import com.ye.mod.ui.YeQuickMenu;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Central registry and lifecycle dispatcher for all Ye modules, supporting single/combo keybinds and quick preset cycling.
 */
public final class ModuleManager {
    private static final List<Module> MODULES = new ArrayList<>();
    private static final Map<Class<? extends Module>, Module> BY_CLASS = new HashMap<>();
    private static final Map<String, Module> BY_ID = new HashMap<>();
    private static final Map<String, Boolean> PREV_TOGGLE_DOWN = new HashMap<>();
    private static final Map<String, Boolean> PREV_CYCLE_DOWN = new HashMap<>();

    private ModuleManager() {}

    public static void register(Module module) {
        module.captureDefaultState();
        MODULES.add(module);
        BY_CLASS.put(module.getClass(), module);
        BY_ID.put(module.getId(), module);
    }

    public static List<Module> getAll() {
        return Collections.unmodifiableList(MODULES);
    }

    public static List<Module> getByCategory(Category category) {
        List<Module> result = new ArrayList<>();
        for (Module m : MODULES) {
            if (m.getCategory() == category) {
                result.add(m);
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    public static <T extends Module> T get(Class<T> clazz) {
        return (T) BY_CLASS.get(clazz);
    }

    public static Module getById(String id) {
        return BY_ID.get(id);
    }

    public static int getActiveCount() {
        int count = 0;
        for (Module m : MODULES) {
            if (m.isEnabled()) count++;
        }
        return count;
    }

    public static void onClientTick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;

        if (mc.getWindow() != null) {
            long handle = mc.getWindow().handle();
            boolean inGameNoGui = (mc.screen == null);

            for (Module m : MODULES) {
                int key = m.getKeybind();
                if (key > 0 && !m.isHoldOnlyKeybind()) {
                    boolean down = KeyCombo.isComboDown(handle, key);
                    boolean wasDown = PREV_TOGGLE_DOWN.getOrDefault(m.getId(), false);
                    if (inGameNoGui && down && !wasDown) {
                        m.toggle();
                        YeQuickMenu.playClick();
                    }
                    PREV_TOGGLE_DOWN.put(m.getId(), down);
                } else {
                    PREV_TOGGLE_DOWN.remove(m.getId());
                }

                int cycKey = m.getPresetCycleKey();
                if (cycKey > 0 && m.hasCycleablePreset()) {
                    boolean cDown = KeyCombo.isComboDown(handle, cycKey);
                    boolean cWas = PREV_CYCLE_DOWN.getOrDefault(m.getId(), false);
                    if (inGameNoGui && cDown && !cWas) {
                        String nextLbl = m.cycleNextPreset();
                        YeQuickMenu.playClick();
                        if (nextLbl != null) {
                            YeQuickMenu.showToast((ConfigManager.english ? m.getSubtitle() : m.getName()) + " → " + nextLbl,
                                    ConfigManager.getAccentColor());
                        }
                    }
                    PREV_CYCLE_DOWN.put(m.getId(), cDown);
                } else {
                    PREV_CYCLE_DOWN.remove(m.getId());
                }
            }
        }

        for (Module m : MODULES) {
            if (m.isEnabled()) {
                m.onTick();
            }
        }
    }
}
