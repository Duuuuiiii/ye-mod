package com.ye.mod.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.ye.mod.core.setting.Setting;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Multi-Profile, Per-Player Account Config System with:
 * - Immutable Factory Default (configs/default.json)
 * - Custom Default Template for new accounts (configs/template.json)
 * - Per-Player automatic profile (<playerName>.json) + multi-slot profile switching/creation/renaming
 * - Lossless incremental schema merge + automatic version-upgrade backups (backups/)
 * - GZIP+Base64 compact clipboard share code ("YE1:...")
 */
public final class ConfigManager {
    public static final String MOD_VERSION = "1.7.1";
    public static final String DEFAULT_PROFILE = "default";
    public static final String TEMPLATE_FILE_STEM = "template";
    private static final String SHARE_PREFIX = "YE1:";

    private static final Logger LOGGER = LoggerFactory.getLogger("ye-config");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Gson COMPACT_GSON = new Gson();
    private static final ExecutorService IO_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "ye-config-io");
        t.setDaemon(true);
        return t;
    });

    private static volatile boolean loading = false;
    private static volatile boolean runtimeReady = false;
    private static JsonObject factorySnapshot = null;

    private static volatile String currentAccountName = "Player";
    private static volatile String activeProfile = "Player";
    private static final Map<String, String> playerPinnedProfiles = new ConcurrentHashMap<>();

    private static volatile int menuKey = org.lwjgl.glfw.GLFW.GLFW_KEY_F9;
    private static volatile int mouseUnlockKey = org.lwjgl.glfw.GLFW.GLFW_KEY_F10;
    public static volatile boolean lightTheme = false;
    public static volatile boolean blurBackground = true;
    public static volatile boolean english = false;
    public static volatile int widgetX = -1;
    public static volatile int widgetY = -1;
    public static final Set<String> customRadarIgnoreBlocks = ConcurrentHashMap.newKeySet();

    public enum ThemeAccent {
        APPLE_BLUE("蓝", "Blue", 0xFF0A84FF),
        CYBER_PURPLE("紫", "Purple", 0xFFA855F7),
        GOLD("金", "Gold", 0xFFF59E0B),
        CRIMSON("红", "Red", 0xFFEF4444),
        EMERALD("绿", "Green", 0xFF10B981),
        SAKURA("粉", "Pink", 0xFFF472B6);

        public final String zh;
        public final String en;
        public final int argb;

        ThemeAccent(String zh, String en, int argb) {
            this.zh = zh;
            this.en = en;
            this.argb = argb;
        }
    }

    public static volatile ThemeAccent themeAccent = ThemeAccent.APPLE_BLUE;

    public static int getMouseUnlockKey() { return mouseUnlockKey; }
    public static void setMouseUnlockKey(int key) {
        mouseUnlockKey = key;
        saveAsync();
    }

    public static int getAccentColor() { return themeAccent.argb; }

    public static final class HudSlot {
        public final String id;
        public final String zhName;
        public final String enName;
        public boolean enabled;
        public int x;
        public int y;
        public int anchorX;
        public int anchorY;
        public transient int lastScreenX;
        public transient int lastScreenY;
        public transient int lastW = 64;
        public transient int lastH = 14;

        public HudSlot(String id, String zhName, String enName, boolean enabled,
                       int x, int y, int anchorX, int anchorY) {
            this.id = id;
            this.zhName = zhName;
            this.enName = enName;
            this.enabled = enabled;
            this.x = x;
            this.y = y;
            this.anchorX = anchorX;
            this.anchorY = anchorY;
        }

        public int resolveX(int sw, int boxW) {
            int rx = switch (anchorX) {
                case 1 -> (sw - boxW) / 2 + x;
                case 2 -> sw - boxW - x;
                default -> x;
            };
            lastScreenX = Math.max(2, Math.min(Math.max(2, sw - boxW - 2), rx));
            lastW = boxW;
            return lastScreenX;
        }

        public int resolveY(int sh, int boxH) {
            int ry = (anchorY == 2) ? (sh - boxH - y) : y;
            lastScreenY = Math.max(2, Math.min(Math.max(2, sh - boxH - 2), ry));
            lastH = boxH;
            return lastScreenY;
        }

        public void updateFromScreenPos(int newScreenX, int newScreenY, int sw, int sh) {
            int clampedX = Math.max(2, Math.min(Math.max(2, sw - lastW - 2), newScreenX));
            int clampedY = Math.max(2, Math.min(Math.max(2, sh - lastH - 2), newScreenY));

            int centerDist = Math.abs((clampedX + lastW / 2) - sw / 2);
            if (clampedX <= 10) {
                anchorX = 0;
                x = 4;
            } else if (sw - (clampedX + lastW) <= 10) {
                anchorX = 2;
                x = 4;
            } else if (centerDist <= 8) {
                anchorX = 1;
                x = 0;
            } else if (clampedX + lastW / 2 < sw / 3) {
                anchorX = 0;
                x = clampedX;
            } else if (clampedX + lastW / 2 > (sw * 2) / 3) {
                anchorX = 2;
                x = sw - lastW - clampedX;
            } else {
                anchorX = 1;
                x = clampedX - (sw - lastW) / 2;
            }

            if (clampedY <= 10) {
                anchorY = 0;
                y = 4;
            } else if (sh - (clampedY + lastH) <= 10) {
                anchorY = 2;
                y = 4;
            } else if (clampedY + lastH / 2 > (sh * 2) / 3) {
                anchorY = 2;
                y = sh - lastH - clampedY;
            } else {
                anchorY = 0;
                y = clampedY;
            }
        }
    }

    public static final Map<String, HudSlot> HUD_SLOTS = new LinkedHashMap<>();
    static {
        resetHudSlotsDefault();
    }

    public static void resetHudSlotsDefault() {
        HUD_SLOTS.clear();
        HUD_SLOTS.put("hud_time", new HudSlot("hud_time", "时间与统计", "Time & Stats", true, 4, 4, 0, 0));
        HUD_SLOTS.put("hud_info", new HudSlot("hud_info", "帧率与延迟", "FPS / Ping / TPS", true, 4, 40, 0, 0));
        HUD_SLOTS.put("hud_speed", new HudSlot("hud_speed", "移动速度", "Speed", true, 0, 36, 1, 0));
        HUD_SLOTS.put("hud_coords", new HudSlot("hud_coords", "双维坐标", "Coords", true, 4, 4, 0, 2));
        HUD_SLOTS.put("hud_potions", new HudSlot("hud_potions", "药水面板", "Potions", true, 6, 6, 2, 0));
        HUD_SLOTS.put("hud_armor", new HudSlot("hud_armor", "装备耐久", "Armor", true, 102, 4, 1, 2));
        HUD_SLOTS.put("hud_items", new HudSlot("hud_items", "物资计数", "PvP Items", true, 6, 128, 2, 0));
        HUD_SLOTS.put("hud_inv", new HudSlot("hud_inv", "背包预览", "Inventory", false, 6, 168, 2, 0));
        HUD_SLOTS.put("hud_drops", new HudSlot("hud_drops", "附近掉落物", "Nearby Drops", true, 4, 110, 0, 0));
        HUD_SLOTS.put("hud_modules", new HudSlot("hud_modules", "开启列表", "Module List", false, 6, 6, 2, 2));
        HUD_SLOTS.put("hud_wthit", new HudSlot("hud_wthit", "方块信息", "WTHIT", true, 0, 6, 1, 0));
        HUD_SLOTS.put("hud_portal", new HudSlot("hud_portal", "坐标换算", "Portal Sync", true, 4, 68, 0, 0));
        HUD_SLOTS.put("hud_radar", new HudSlot("hud_radar", "雷达计数", "Radar Count", true, 4, 82, 0, 0));
    }

    public static String tr(String zh, String en) {
        return english ? en : zh;
    }

    private ConfigManager() {}

    public static int getMenuKey() { return menuKey; }
    public static void setMenuKey(int key) {
        menuKey = key;
        saveAsync();
    }

    public static String getCurrentAccountName() { return currentAccountName; }
    public static String getActiveProfile() { return activeProfile; }

    public static Path getConfigDir() {
        return FabricLoader.getInstance().getConfigDir().resolve("ye");
    }

    public static Path getConfigsDir() {
        return getConfigDir().resolve("configs");
    }

    public static Path getBackupsDir() {
        return getConfigDir().resolve("backups");
    }

    public static Path getCapesDir() {
        return getConfigDir().resolve("capes");
    }

    public static Path getMetaFile() {
        return getConfigDir().resolve("meta.json");
    }

    public static Path getLegacyConfigFile() {
        return getConfigDir().resolve("config.json");
    }

    public static Path getProfileFile(String profileName) {
        return getConfigsDir().resolve(sanitizeProfileName(profileName) + ".json");
    }

    public static String sanitizeProfileName(String raw) {
        if (raw == null || raw.isBlank()) return "Player";
        String cleaned = raw.trim().replaceAll("[\\\\/:*?\"<>|]", "_");
        return cleaned.isEmpty() ? "Player" : cleaned;
    }

    public static void ensureDirectories() {
        try {
            Files.createDirectories(getConfigDir());
            Files.createDirectories(getConfigsDir());
            Files.createDirectories(getBackupsDir());
            Files.createDirectories(getCapesDir());
        } catch (IOException e) {
            LOGGER.warn("Failed to create Ye config directories", e);
        }
    }

    /**
     * Called once after all modules register in YeClient.onInitializeClient():
     * 1) Captures immutable Factory Default snapshot & writes configs/default.json
     * 2) Checks meta.json; if mod version changed, backs up all existing configs into backups/
     * 3) Loads initial profile for the launcher's logged-in Minecraft user!
     */
    public static void initSystem(String initialUser) {
        ensureDirectories();
        // Capture pure factory defaults before touching any user file
        factorySnapshot = buildSnapshot();
        writeJsonToFile(getProfileFile(DEFAULT_PROFILE), factorySnapshot);

        loadMetaAndBackupIfUpgraded();

        String safeUser = sanitizeProfileName(initialUser);
        currentAccountName = safeUser;
        String preferred = playerPinnedProfiles.getOrDefault(safeUser.toLowerCase(Locale.ROOT), safeUser);
        ensurePlayerProfileExists(safeUser);
        if (!Files.exists(getProfileFile(preferred))) {
            preferred = safeUser;
        }
        switchToProfile(preferred, false);
    }

    public static void markRuntimeReady() {
        runtimeReady = true;
    }

    /**
     * Called on tick / join to detect Minecraft account switches automatically!
     */
    public static void checkAccountSwitch(String detectedName) {
        if (detectedName == null || detectedName.isBlank()) return;
        String safe = sanitizeProfileName(detectedName);
        if (safe.equalsIgnoreCase(currentAccountName)) return;

        // Save previous player's config first, then switch to the new player's profile!
        saveNow();
        currentAccountName = safe;
        ensurePlayerProfileExists(safe);
        String preferred = playerPinnedProfiles.getOrDefault(safe.toLowerCase(Locale.ROOT), safe);
        if (!Files.exists(getProfileFile(preferred))) {
            preferred = safe;
        }
        switchToProfile(preferred, runtimeReady);
        saveMeta();
    }

    private static void ensurePlayerProfileExists(String playerName) {
        Path playerFile = getProfileFile(playerName);
        if (Files.exists(playerFile)) return;

        // 1) Migrate legacy config/ye/config.json if present so existing users keep 100% of settings!
        Path legacy = getLegacyConfigFile();
        if (Files.exists(legacy)) {
            try {
                Files.copy(legacy, playerFile, StandardCopyOption.REPLACE_EXISTING);
                LOGGER.info("Migrated legacy config.json -> configs/{}.json", playerName);
                return;
            } catch (IOException ignored) {}
        }

        // 2) Inherit from user's custom template.json if set, otherwise factory default.json!
        Path templateFile = getProfileFile(TEMPLATE_FILE_STEM);
        if (Files.exists(templateFile)) {
            try {
                Files.copy(templateFile, playerFile, StandardCopyOption.REPLACE_EXISTING);
                return;
            } catch (IOException ignored) {}
        }
        if (factorySnapshot != null) {
            writeJsonToFile(playerFile, factorySnapshot);
        }
    }

    private static void loadMetaAndBackupIfUpgraded() {
        String prevVer = "";
        Path meta = getMetaFile();
        if (Files.exists(meta)) {
            try {
                JsonObject obj = JsonParser.parseString(Files.readString(meta, StandardCharsets.UTF_8)).getAsJsonObject();
                if (obj.has("lastModVersion")) prevVer = obj.get("lastModVersion").getAsString();
                if (obj.has("playerProfiles") && obj.get("playerProfiles").isJsonObject()) {
                    JsonObject pp = obj.getAsJsonObject("playerProfiles");
                    for (String k : pp.keySet()) {
                        playerPinnedProfiles.put(k.toLowerCase(Locale.ROOT), pp.get(k).getAsString());
                    }
                }
            } catch (Exception ignored) {}
        }

        if (!MOD_VERSION.equals(prevVer)) {
            backupAllConfigs(prevVer.isEmpty() ? "pre_1.7.1" : prevVer);
            saveMeta();
        }
    }

    private static void backupAllConfigs(String tag) {
        try {
            String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            Path sub = getBackupsDir().resolve(tag + "_" + stamp);
            boolean copiedAny = false;
            if (Files.exists(getLegacyConfigFile())) {
                Files.createDirectories(sub);
                Files.copy(getLegacyConfigFile(), sub.resolve("legacy_config.json"), StandardCopyOption.REPLACE_EXISTING);
                copiedAny = true;
            }
            if (Files.exists(getConfigsDir())) {
                try (Stream<Path> s = Files.list(getConfigsDir())) {
                    for (Path p : s.toList()) {
                        if (Files.isRegularFile(p) && p.getFileName().toString().endsWith(".json")) {
                            Files.createDirectories(sub);
                            Files.copy(p, sub.resolve(p.getFileName().toString()), StandardCopyOption.REPLACE_EXISTING);
                            copiedAny = true;
                        }
                    }
                }
            }
            if (copiedAny) {
                LOGGER.info("Backed up Ye configs before upgrade to {}", sub);
            }
        } catch (Exception e) {
            LOGGER.warn("Backup skipped", e);
        }
    }

    private static void saveMeta() {
        try {
            ensureDirectories();
            JsonObject meta = new JsonObject();
            meta.addProperty("lastModVersion", MOD_VERSION);
            JsonObject pp = new JsonObject();
            for (Map.Entry<String, String> entry : playerPinnedProfiles.entrySet()) {
                pp.addProperty(entry.getKey(), entry.getValue());
            }
            meta.add("playerProfiles", pp);
            Files.writeString(getMetaFile(), GSON.toJson(meta), StandardCharsets.UTF_8);
        } catch (Exception ignored) {}
    }

    public static List<String> listAvailableProfiles() {
        ensureDirectories();
        List<String> names = new ArrayList<>();
        names.add(DEFAULT_PROFILE);
        if (!currentAccountName.equalsIgnoreCase(DEFAULT_PROFILE)) {
            names.add(currentAccountName);
        }
        try (Stream<Path> files = Files.list(getConfigsDir())) {
            files.filter(p -> Files.isRegularFile(p) && p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json"))
                    .map(p -> {
                        String fn = p.getFileName().toString();
                        return fn.substring(0, fn.length() - 5);
                    })
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .forEach(stem -> {
                        if (stem.equalsIgnoreCase(TEMPLATE_FILE_STEM)) return;
                        boolean exists = names.stream().anyMatch(n -> n.equalsIgnoreCase(stem));
                        if (!exists) names.add(stem);
                    });
        } catch (IOException ignored) {}
        return names;
    }

    public static void switchToProfile(String profileName, boolean fireCallbacks) {
        String safe = sanitizeProfileName(profileName);
        activeProfile = safe;
        playerPinnedProfiles.put(currentAccountName.toLowerCase(Locale.ROOT), safe);
        saveMeta();

        if (safe.equalsIgnoreCase(DEFAULT_PROFILE)) {
            if (factorySnapshot != null) {
                applySnapshot(factorySnapshot, fireCallbacks);
            }
            return;
        }

        Path file = getProfileFile(safe);
        if (!Files.exists(file)) {
            ensurePlayerProfileExists(safe);
        }
        try {
            String raw = Files.readString(file, StandardCharsets.UTF_8);
            JsonElement parsed = JsonParser.parseString(raw);
            if (parsed.isJsonObject()) {
                applySnapshot(parsed.getAsJsonObject(), fireCallbacks);
                // Immediately write back lossless merged snapshot so new mod settings are populated on disk!
                writeJsonToFile(file, buildSnapshot());
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to load profile {}, keeping current state", safe, e);
        }
    }

    public static String createNewProfile(String customName) {
        String base = (customName == null || customName.isBlank()) ? (currentAccountName + "_") : sanitizeProfileName(customName);
        String candidate = base;
        if (customName == null || customName.isBlank()) {
            int idx = 2;
            while (Files.exists(getProfileFile(base + idx))) idx++;
            candidate = base + idx;
        } else if (candidate.equalsIgnoreCase(DEFAULT_PROFILE) || candidate.equalsIgnoreCase(TEMPLATE_FILE_STEM)) {
            candidate = candidate + "_1";
        }
        activeProfile = candidate;
        playerPinnedProfiles.put(currentAccountName.toLowerCase(Locale.ROOT), candidate);
        saveNow();
        saveMeta();
        return candidate;
    }

    public static boolean renameActiveProfile(String newRawName) {
        if (activeProfile.equalsIgnoreCase(DEFAULT_PROFILE)) return false;
        String target = sanitizeProfileName(newRawName);
        if (target.isEmpty() || target.equalsIgnoreCase(DEFAULT_PROFILE) || target.equalsIgnoreCase(TEMPLATE_FILE_STEM)) {
            return false;
        }
        if (target.equalsIgnoreCase(activeProfile)) return true;

        Path oldFile = getProfileFile(activeProfile);
        Path newFile = getProfileFile(target);
        try {
            saveNow();
            if (Files.exists(oldFile)) {
                Files.move(oldFile, newFile, StandardCopyOption.REPLACE_EXISTING);
            } else {
                writeJsonToFile(newFile, buildSnapshot());
            }
            activeProfile = target;
            playerPinnedProfiles.put(currentAccountName.toLowerCase(Locale.ROOT), target);
            saveMeta();
            return true;
        } catch (IOException e) {
            LOGGER.warn("Failed to rename profile", e);
            return false;
        }
    }

    /**
     * Resets the current profile back to immutable Factory Default (default.json)!
     */
    public static void resetCurrentToFactoryDefault() {
        if (factorySnapshot == null) return;
        if (activeProfile.equalsIgnoreCase(DEFAULT_PROFILE)) {
            activeProfile = currentAccountName;
            playerPinnedProfiles.put(currentAccountName.toLowerCase(Locale.ROOT), activeProfile);
            saveMeta();
        }
        applySnapshot(factorySnapshot, true);
        // Refresh default.json on disk as well to guarantee it's pristine
        writeJsonToFile(getProfileFile(DEFAULT_PROFILE), factorySnapshot);
        saveNow();
    }

    /**
     * Saves the user's current tuned settings as the Default Template (configs/template.json)
     * inherited by any future new accounts/profiles!
     */
    public static void saveCurrentAsDefaultTemplate() {
        ensureDirectories();
        writeJsonToFile(getProfileFile(TEMPLATE_FILE_STEM), buildSnapshot());
    }

    public static boolean hasCustomDefaultTemplate() {
        return Files.exists(getProfileFile(TEMPLATE_FILE_STEM));
    }

    public static void openConfigsFolder() {
        ensureDirectories();
        Util.getPlatform().openPath(getConfigsDir());
    }

    /**
     * Compresses current config into a compact shareable clipboard string ("YE1:...").
     */
    public static String exportShareCodeToClipboard() {
        try {
            String json = COMPACT_GSON.toJson(buildSnapshot());
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (GZIPOutputStream gzip = new GZIPOutputStream(baos)) {
                gzip.write(json.getBytes(StandardCharsets.UTF_8));
            }
            String code = SHARE_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(baos.toByteArray());
            Minecraft.getInstance().keyboardHandler.setClipboard(code);
            return code;
        } catch (Exception e) {
            LOGGER.warn("Failed to export share code", e);
            return null;
        }
    }

    /**
     * Imports a "YE1:..." share code (or raw JSON) from clipboard into the active profile!
     */
    public static boolean importShareCodeFromClipboard() {
        try {
            String clip = Minecraft.getInstance().keyboardHandler.getClipboard();
            if (clip == null || clip.isBlank()) return false;
            clip = clip.trim();

            String json;
            if (clip.startsWith(SHARE_PREFIX)) {
                byte[] compressed = Base64.getUrlDecoder().decode(clip.substring(SHARE_PREFIX.length()).trim());
                try (GZIPInputStream gis = new GZIPInputStream(new ByteArrayInputStream(compressed))) {
                    json = new String(gis.readAllBytes(), StandardCharsets.UTF_8);
                }
            } else if (clip.startsWith("{")) {
                json = clip;
            } else {
                return false;
            }

            JsonElement parsed = JsonParser.parseString(json);
            if (!parsed.isJsonObject()) return false;
            JsonObject obj = parsed.getAsJsonObject();
            if (!obj.has("modules")) return false;

            if (activeProfile.equalsIgnoreCase(DEFAULT_PROFILE)) {
                activeProfile = currentAccountName;
                playerPinnedProfiles.put(currentAccountName.toLowerCase(Locale.ROOT), activeProfile);
                saveMeta();
            }
            applySnapshot(obj, true);
            saveNow();
            return true;
        } catch (Exception e) {
            LOGGER.warn("Failed to import share code from clipboard", e);
            return false;
        }
    }

    private static void resetInMemoryToFactoryDefaults(boolean fireCallbacks) {
        menuKey = org.lwjgl.glfw.GLFW.GLFW_KEY_F9;
        mouseUnlockKey = org.lwjgl.glfw.GLFW.GLFW_KEY_F10;
        widgetX = -1;
        widgetY = -1;
        themeAccent = ThemeAccent.APPLE_BLUE;
        lightTheme = false;
        blurBackground = true;
        english = false;
        customRadarIgnoreBlocks.clear();
        resetHudSlotsDefault();
        for (Module m : ModuleManager.getAll()) {
            for (Setting<?> s : m.getSettings()) s.resetSilent();
            m.setKeybindSilent(-1);
            m.applyLoadedEnabled(m.isDefaultEnabled(), fireCallbacks);
        }
    }

    private static void applySnapshot(JsonObject root, boolean fireCallbacks) {
        loading = true;
        try {
            // Reset to clean factory defaults first so any new modules/settings added in updates have clean defaults!
            resetInMemoryToFactoryDefaults(fireCallbacks);

            if (root.has("menuKey")) menuKey = root.get("menuKey").getAsInt();
            if (root.has("mouseUnlockKey")) mouseUnlockKey = root.get("mouseUnlockKey").getAsInt();
            if (root.has("widgetX")) widgetX = root.get("widgetX").getAsInt();
            if (root.has("widgetY")) widgetY = root.get("widgetY").getAsInt();
            if (root.has("themeAccent")) {
                try {
                    themeAccent = ThemeAccent.valueOf(root.get("themeAccent").getAsString());
                } catch (Exception ignored) {}
            }
            if (root.has("lightTheme")) lightTheme = root.get("lightTheme").getAsBoolean();
            if (root.has("blurBackground")) blurBackground = root.get("blurBackground").getAsBoolean();
            if (root.has("english")) english = root.get("english").getAsBoolean();

            if (root.has("customRadarIgnoreBlocks") && root.get("customRadarIgnoreBlocks").isJsonArray()) {
                customRadarIgnoreBlocks.clear();
                for (JsonElement el : root.getAsJsonArray("customRadarIgnoreBlocks")) {
                    customRadarIgnoreBlocks.add(el.getAsString());
                }
            }
            if (root.has("hudSlots") && root.get("hudSlots").isJsonObject()) {
                JsonObject hObj = root.getAsJsonObject("hudSlots");
                for (HudSlot slot : HUD_SLOTS.values()) {
                    if (hObj.has(slot.id) && hObj.get(slot.id).isJsonObject()) {
                        try {
                            JsonObject s = hObj.getAsJsonObject(slot.id);
                            if (s.has("enabled")) slot.enabled = s.get("enabled").getAsBoolean();
                            if (s.has("x")) slot.x = s.get("x").getAsInt();
                            if (s.has("y")) slot.y = s.get("y").getAsInt();
                            if (s.has("ax")) slot.anchorX = s.get("ax").getAsInt();
                            if (s.has("ay")) slot.anchorY = s.get("ay").getAsInt();
                        } catch (Exception ignored) {}
                    }
                }
            }

            JsonObject modulesObj = root.has("modules") && root.get("modules").isJsonObject()
                    ? root.getAsJsonObject("modules") : null;
            if (modulesObj == null) return;

            for (Module module : ModuleManager.getAll()) {
                if (!modulesObj.has(module.getId()) || !modulesObj.get(module.getId()).isJsonObject()) {
                    continue;
                }
                JsonObject mObj = modulesObj.getAsJsonObject(module.getId());
                if (mObj.has("keybind")) {
                    try {
                        module.setKeybindSilent(mObj.get("keybind").getAsInt());
                    } catch (Exception ignored) {}
                }
                if (mObj.has("settings") && mObj.get("settings").isJsonObject()) {
                    JsonObject sObj = mObj.getAsJsonObject("settings");
                    for (Setting<?> setting : module.getSettings()) {
                        if (sObj.has(setting.getId())) {
                            try {
                                setting.fromJson(sObj.get(setting.getId()));
                            } catch (Exception ignored) {}
                        }
                    }
                }
                if (mObj.has("enabled")) {
                    try {
                        module.applyLoadedEnabled(mObj.get("enabled").getAsBoolean(), fireCallbacks);
                    } catch (Exception ignored) {}
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Partial error applying Ye config snapshot", e);
        } finally {
            loading = false;
        }
    }

    public static void saveAsync() {
        if (loading) return;
        // Protect immutable factory default.json: if user edits while viewing "default", fork into their player profile!
        if (activeProfile.equalsIgnoreCase(DEFAULT_PROFILE)) {
            activeProfile = currentAccountName;
            playerPinnedProfiles.put(currentAccountName.toLowerCase(Locale.ROOT), activeProfile);
            IO_EXECUTOR.submit(ConfigManager::saveMeta);
        }
        final Path targetFile = getProfileFile(activeProfile);
        final JsonObject snapshot = buildSnapshot();
        IO_EXECUTOR.submit(() -> writeJsonToFile(targetFile, snapshot));
    }

    public static void saveNow() {
        if (loading) return;
        if (activeProfile.equalsIgnoreCase(DEFAULT_PROFILE)) return;
        writeJsonToFile(getProfileFile(activeProfile), buildSnapshot());
    }

    private static JsonObject buildSnapshot() {
        JsonObject root = new JsonObject();
        root.addProperty("modVersion", MOD_VERSION);
        root.addProperty("menuKey", menuKey);
        root.addProperty("mouseUnlockKey", mouseUnlockKey);
        root.addProperty("widgetX", widgetX);
        root.addProperty("widgetY", widgetY);
        root.addProperty("themeAccent", themeAccent.name());
        root.addProperty("lightTheme", lightTheme);
        root.addProperty("blurBackground", blurBackground);
        root.addProperty("english", english);

        JsonArray ignoreArr = new JsonArray();
        for (String id : customRadarIgnoreBlocks) ignoreArr.add(id);
        root.add("customRadarIgnoreBlocks", ignoreArr);

        JsonObject hObj = new JsonObject();
        for (HudSlot slot : HUD_SLOTS.values()) {
            JsonObject s = new JsonObject();
            s.addProperty("enabled", slot.enabled);
            s.addProperty("x", slot.x);
            s.addProperty("y", slot.y);
            s.addProperty("ax", slot.anchorX);
            s.addProperty("ay", slot.anchorY);
            hObj.add(slot.id, s);
        }
        root.add("hudSlots", hObj);

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

    private static void writeJsonToFile(Path path, JsonObject snapshot) {
        try {
            ensureDirectories();
            Files.writeString(path, GSON.toJson(snapshot), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.warn("Failed to write Ye config file {}", path, e);
        }
    }
}
