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
    private static volatile int menuKey = org.lwjgl.glfw.GLFW.GLFW_KEY_F9;
    public static volatile boolean lightTheme = false;
    public static volatile boolean blurBackground = true;
    public static volatile boolean english = false;

    public static final class HudSlot {
        public final String id;
        public final String zhName;
        public final String enName;
        public boolean enabled;
        public int x;
        public int y;

        public HudSlot(String id, String zhName, String enName, boolean enabled, int x, int y) {
            this.id = id;
            this.zhName = zhName;
            this.enName = enName;
            this.enabled = enabled;
            this.x = x;
            this.y = y;
        }
    }

    public static final java.util.Map<String, HudSlot> HUD_SLOTS = new java.util.LinkedHashMap<>();
    static {
        HUD_SLOTS.put("hud_info", new HudSlot("hud_info", "帧率与坐标", "FPS & Coords", true, 8, 8));
        HUD_SLOTS.put("hud_modules", new HudSlot("hud_modules", "已启用列表", "Active List", true, 8, 52));
        HUD_SLOTS.put("hud_portal", new HudSlot("hud_portal", "下界坐标", "Portal Coords", true, 8, 22));
        HUD_SLOTS.put("hud_radar", new HudSlot("hud_radar", "雷达计数", "Radar Count", true, 8, 36));
    }

    public static String tr(String zh, String en) {
        return english ? en : zh;
    }

    private ConfigManager() {}

    public static int getMenuKey() {
        return menuKey;
    }

    public static void setMenuKey(int key) {
        menuKey = key;
        saveAsync();
    }

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
            if (root.has("menuKey")) {
                menuKey = root.get("menuKey").getAsInt();
            }
            if (root.has("lightTheme")) {
                lightTheme = root.get("lightTheme").getAsBoolean();
            }
            if (root.has("blurBackground")) {
                blurBackground = root.get("blurBackground").getAsBoolean();
            }
            if (root.has("english")) {
                english = root.get("english").getAsBoolean();
            }
            if (root.has("hudSlots") && root.get("hudSlots").isJsonObject()) {
                JsonObject hObj = root.getAsJsonObject("hudSlots");
                for (HudSlot slot : HUD_SLOTS.values()) {
                    if (hObj.has(slot.id) && hObj.get(slot.id).isJsonObject()) {
                        JsonObject s = hObj.getAsJsonObject(slot.id);
                        if (s.has("enabled")) slot.enabled = s.get("enabled").getAsBoolean();
                        if (s.has("x")) slot.x = s.get("x").getAsInt();
                        if (s.has("y")) slot.y = s.get("y").getAsInt();
                    }
                }
            }
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

        root.addProperty("menuKey", menuKey);
        root.addProperty("lightTheme", lightTheme);
        root.addProperty("blurBackground", blurBackground);
        root.addProperty("english", english);

        JsonObject hObj = new JsonObject();
        for (HudSlot slot : HUD_SLOTS.values()) {
            JsonObject s = new JsonObject();
            s.addProperty("enabled", slot.enabled);
            s.addProperty("x", slot.x);
            s.addProperty("y", slot.y);
            hObj.add(slot.id, s);
        }
        root.add("hudSlots", hObj);
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
