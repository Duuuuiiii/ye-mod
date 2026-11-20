package com.ye.mod.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.ye.mod.core.setting.Setting;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Handles JSON configuration persistence under config/ye/config.json.
 */
public final class ConfigManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("ye-config");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final ExecutorService IO_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "ye-config-io");
        t.setDaemon(true);
        return t;
    });

    private static volatile boolean loading = false;

    private ConfigManager() {}

    public static Path getConfigDir() {
        return FabricLoader.getInstance().getConfigDir().resolve("ye");
    }

    public static Path getCapesDir() {
        return getConfigDir().resolve("capes");
    }

    public static Path getConfigFile() {
        return getConfigDir().resolve("config.json");
    }

    public static void ensureDirectories() {
        try {
            Files.createDirectories(getConfigDir());
            Files.createDirectories(getCapesDir());
        } catch (IOException e) {
            LOGGER.warn("Failed to create Ye config directories", e);
        }
    }

    public static void load() {
        ensureDirectories();
        Path file = getConfigFile();
        if (!Files.exists(file)) {
            return;
        }

        loading = true;
        try {
            String raw = Files.readString(file, StandardCharsets.UTF_8);
            JsonElement parsed = JsonParser.parseString(raw);
            if (!parsed.isJsonObject()) return;

            JsonObject root = parsed.getAsJsonObject();
            JsonObject modulesObj = root.has("modules") && root.get("modules").isJsonObject()
                    ? root.getAsJsonObject("modules")
                    : null;
            if (modulesObj == null) return;

            for (Module module : ModuleManager.getAll()) {
                if (!modulesObj.has(module.getId()) || !modulesObj.get(module.getId()).isJsonObject()) {
                    continue;
                }
                JsonObject mObj = modulesObj.getAsJsonObject(module.getId());
                if (mObj.has("keybind")) {
                    module.setKeybindSilent(mObj.get("keybind").getAsInt());
                }
                if (mObj.has("settings") && mObj.get("settings").isJsonObject()) {
                    JsonObject sObj = mObj.getAsJsonObject("settings");
                    for (Setting<?> setting : module.getSettings()) {
                        if (sObj.has(setting.getId())) {
                            setting.fromJson(sObj.get(setting.getId()));
                        }
                    }
                }
                if (mObj.has("enabled")) {
                    boolean en = mObj.get("enabled").getAsBoolean();
                    module.setEnabled(en, true);
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to load Ye config", e);
        } finally {
            loading = false;
        }
    }

    public static void saveAsync() {
        if (loading) return;
        JsonObject snapshot = buildSnapshot();
        IO_EXECUTOR.submit(() -> writeSnapshot(snapshot));
    }

    public static void saveNow() {
        writeSnapshot(buildSnapshot());
    }

    private static JsonObject buildSnapshot() {
        JsonObject root = new JsonObject();
        JsonObject modulesObj = new JsonObject();

        for (Module module : ModuleManager.getAll()) {
            JsonObject mObj = new JsonObject();
            mObj.addProperty("enabled", module.isEnabled());
            mObj.addProperty("keybind", module.getKeybind());

            JsonObject sObj = new JsonObject();
            for (Setting<?> setting : module.getSettings()) {
                JsonElement val = setting.toJson();
                if (val != null && !val.isJsonNull()) {
                    sObj.add(setting.getId(), val);
                }
            }
            mObj.add("settings", sObj);
            modulesObj.add(module.getId(), mObj);
        }

        root.add("modules", modulesObj);
        return root;
    }

    private static void writeSnapshot(JsonObject snapshot) {
        try {
            ensureDirectories();
            Files.writeString(getConfigFile(), GSON.toJson(snapshot), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.warn("Failed to save Ye config", e);
        }
    }
}
