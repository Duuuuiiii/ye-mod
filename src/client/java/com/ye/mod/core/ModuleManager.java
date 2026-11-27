package com.ye.mod.core;

import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Central registry and lifecycle dispatcher for all Ye modules.
 */
public final class ModuleManager {
    private static final List<Module> MODULES = new ArrayList<>();
    private static final Map<Class<? extends Module>, Module> BY_CLASS = new HashMap<>();
    private static final Map<String, Module> BY_ID = new HashMap<>();
    private static final Map<Integer, Boolean> PREVIOUS_KEY_STATES = new HashMap<>();

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

        if (mc.screen == null && mc.getWindow() != null) {
            long handle = mc.getWindow().handle();
            for (Module m : MODULES) {
                int key = m.getKeybind();
                if (key > 0) {
                    boolean down = GLFW.glfwGetKey(handle, key) == GLFW.GLFW_PRESS;
                    boolean wasDown = PREVIOUS_KEY_STATES.getOrDefault(key, false);
                    if (down && !wasDown) {
                        m.toggle();
                    }
                    PREVIOUS_KEY_STATES.put(key, down);
                }
            }
        } else {
            PREVIOUS_KEY_STATES.clear();
        }

        for (Module m : MODULES) {
            if (m.isEnabled()) {
                m.onTick();
            }
        }
    }
}
