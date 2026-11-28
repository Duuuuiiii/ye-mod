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

import java.awt.Color;
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
 * Multi-Profile, Per-Player Account Config System (v1.7.3):
 * - Dual UI Persona: SILKY (丝滑风) & MECHANICAL (机械风)
 * - 3 UI Themes: DARK (深色), LIGHT (浅色), GAUSSIAN_GLASS (高斯玻璃) + Opacity & Blur Strength
 * - Independent UI Theme Accent Color (with 2D HSV draggable picker) vs HUD/FX SyncColorModule
 * - Module Favorites (收藏) & Pinning (置顶)
 * - Per-profile Entity & Block matrix selection sets
 * - 0px edge-flush HUD clamping + 9-zone magnetic auto-snap
 */
public final class ConfigManager {
    public static final String MOD_VERSION = "1.7.6";
    public static final String DEFAULT_PROFILE = "default";
    public static final String SHARED_PROFILE = "main";
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
    private static volatile String activeProfile = SHARED_PROFILE;
    private static volatile String savedGlobalProfile = SHARED_PROFILE;

    public enum UiStyle {
        SILKY("丝滑风", "Silky"),
        MECHANICAL("机械风", "Mechanical");

        public final String zh;
        public final String en;
        UiStyle(String zh, String en) { this.zh = zh; this.en = en; }
    }

    public enum UiTheme {
        DARK("深色", "Dark"),
        LIGHT("浅色", "Light"),
        GAUSSIAN_GLASS("高斯玻璃", "Glass");

        public final String zh;
        public final String en;
        UiTheme(String zh, String en) { this.zh = zh; this.en = en; }
    }

    public enum ThemeAccent {
        BLUE("蓝", "Blue", 0xFF0A84FF),
        PURPLE("紫", "Purple", 0xFFA855F7),
        GOLD("金", "Gold", 0xFFF59E0B),
        RED("红", "Red", 0xFFEF4444),
        GREEN("绿", "Green", 0xFF10B981),
        PINK("粉", "Pink", 0xFFF472B6),
        CUSTOM("自定义", "Custom", 0xFF38BDF8);

        public final String zh;
        public final String en;
        public final int argb;

        ThemeAccent(String zh, String en, int argb) {
            this.zh = zh;
            this.en = en;
            this.argb = argb;
        }
    }

    private static volatile int menuKey = org.lwjgl.glfw.GLFW.GLFW_KEY_F9;
    private static volatile int mouseUnlockKey = org.lwjgl.glfw.GLFW.GLFW_KEY_F10;
    public static volatile UiStyle uiStyle = UiStyle.SILKY;
    public static volatile UiTheme uiTheme = UiTheme.DARK;
    public static volatile boolean lightTheme = false;
    public static volatile float uiOpacity = 0.92f;
    public static volatile float uiBlurStrength = 0.48f;
    public static volatile float glassBlurStrength = 10.0f;
    public static volatile boolean uiMinimized = false;
    public static volatile boolean rusherSidebar = true;
    public static volatile boolean useClientFont = false;
    public static volatile boolean english = false;
    public static volatile YeSoundPresets.UiSoundType uiClickSound = YeSoundPresets.UiSoundType.MECH_SWITCH;
    public static volatile float uiSoundVolume = 0.80f;
    public static volatile int widgetX = -1;
    public static volatile int widgetY = -1;

    public static volatile ThemeAccent themeAccent = ThemeAccent.BLUE;
    public static volatile float customAccentHue = 0.56f;
    public static volatile float customAccentSat = 0.78f;
    public static volatile float customAccentBri = 0.98f;

    // Favorites & Pinning
    public static final Set<String> pinnedModules = ConcurrentHashMap.newKeySet();
    public static final Set<String> favoriteModules = ConcurrentHashMap.newKeySet();

    // Target picker matrix sets (persisted per profile)
    public static final Set<String> customRadarIgnoreBlocks = ConcurrentHashMap.newKeySet();
    public static final Set<String> entityEspCustomTypes = ConcurrentHashMap.newKeySet();
    public static final Set<String> blockEspTargets = ConcurrentHashMap.newKeySet();
    public static final Set<String> blockSearchTargets = ConcurrentHashMap.newKeySet();
    public static final Set<String> dropTierHigh = ConcurrentHashMap.newKeySet();
    public static final Set<String> dropTierMid = ConcurrentHashMap.newKeySet();

    // Player PvP Stats (persisted)
    public static volatile int statPlayerKills = 0;
    public static volatile int statPlayerDeaths = 0;

    public static int getMouseUnlockKey() { return mouseUnlockKey; }
    public static void setMouseUnlockKey(int key) {
        mouseUnlockKey = key;
        saveAsync();
    }

    public static int getCustomAccentArgb() {
        int rgb = Color.HSBtoRGB(customAccentHue, customAccentSat, customAccentBri) & 0x00FFFFFF;
        return 0xFF000000 | rgb;
    }

    public static int getAccentColor() {
        if (themeAccent == ThemeAccent.CUSTOM) {
            return getCustomAccentArgb();
        }
        return themeAccent.argb;
    }

    public static boolean isPinned(Module m) {
        return m != null && pinnedModules.contains(m.getId());
    }

    public static boolean isFavorite(Module m) {
        return m != null && favoriteModules.contains(m.getId());
    }

    public static void togglePinned(Module m) {
        if (m == null) return;
        if (!pinnedModules.remove(m.getId())) {
            pinnedModules.add(m.getId());
        }
        saveAsync();
    }

    public static void toggleFavorite(Module m) {
        if (m == null) return;
        if (!favoriteModules.remove(m.getId())) {
            favoriteModules.add(m.getId());
        }
        saveAsync();
    }

    /**
     * HUD Slot with 0px edge clamping (can sit flush against the very bottom/top/left/right edge!)
     * and 9-zone magnetic snapping (4 corners, 4 edge midpoints, and screen center)!
     */
    public static final class HudSlot {
        public final String id;
        public final String zhName;
        public final String enName;
        public boolean enabled;
        public int x;
        public int y;
        public int anchorX; // 0 = Left, 1 = Center, 2 = Right
        public int anchorY; // 0 = Top,  1 = Middle, 2 = Bottom
        public transient int lastScreenX;
        public transient int lastScreenY;
        public transient int lastW = 64;
        public transient int lastH = 14;
        public transient boolean snappedThisDrag = false;

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
            lastScreenX = Math.max(0, Math.min(Math.max(0, sw - boxW), rx));
            lastW = boxW;
            return lastScreenX;
        }

        public int resolveY(int sh, int boxH) {
            int ry = switch (anchorY) {
                case 1 -> (sh - boxH) / 2 + y;
                case 2 -> sh - boxH - y;
                default -> y;
            };
            lastScreenY = Math.max(0, Math.min(Math.max(0, sh - boxH), ry));
            lastH = boxH;
            return lastScreenY;
        }

        public void updateFromScreenPos(int newScreenX, int newScreenY, int sw, int sh) {
            int clampedX = Math.max(0, Math.min(Math.max(0, sw - lastW), newScreenX));
            int clampedY = Math.max(0, Math.min(Math.max(0, sh - lastH), newScreenY));

            final int snapRadius = 9;
            boolean snapX = false;
            boolean snapY = false;

            int centerDistX = Math.abs((clampedX + lastW / 2) - sw / 2);
            if (clampedX <= 3) {
                anchorX = 0;
                x = 0;
                snapX = true;
            } else if (sw - (clampedX + lastW) <= 3) {
                anchorX = 2;
                x = 0;
                snapX = true;
            } else if (centerDistX <= snapRadius) {
                anchorX = 1;
                x = 0;
                snapX = true;
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

            int centerDistY = Math.abs((clampedY + lastH / 2) - sh / 2);
            if (clampedY <= 3) {
                anchorY = 0;
                y = 0;
                snapY = true;
            } else if (sh - (clampedY + lastH) <= 3) {
                anchorY = 2;
                y = 0;
                snapY = true;
            } else if (centerDistY <= snapRadius) {
                anchorY = 1;
                y = 0;
                snapY = true;
            } else if (clampedY + lastH / 2 < sh / 3) {
                anchorY = 0;
                y = clampedY;
            } else if (clampedY + lastH / 2 > (sh * 2) / 3) {
                anchorY = 2;
                y = sh - lastH - clampedY;
            } else {
                anchorY = 1;
                y = clampedY - (sh - lastH) / 2;
            }

            // Magnetic snap to other visible HUD boundaries (mutual edge snapping)
            for (HudSlot other : HUD_SLOTS.values()) {
                if (other == this || !other.enabled || other.lastW <= 0 || other.lastH <= 0) continue;
                int oL = other.lastScreenX, oR = other.lastScreenX + other.lastW;
                int oT = other.lastScreenY, oB = other.lastScreenY + other.lastH;
                boolean yNear = clampedY < oB + 12 && clampedY + lastH > oT - 12;
                boolean xNear = clampedX < oR + 12 && clampedX + lastW > oL - 12;
                if (yNear) {
                    if (Math.abs(clampedX - oR) <= 6) { clampedX = oR + 2; snapX = true; }
                    else if (Math.abs((clampedX + lastW) - oL) <= 6) { clampedX = oL - lastW - 2; snapX = true; }
                    else if (Math.abs(clampedX - oL) <= 5) { clampedX = oL; snapX = true; }
                }
                if (xNear) {
                    if (Math.abs(clampedY - oB) <= 6) { clampedY = oB + 2; snapY = true; }
                    else if (Math.abs((clampedY + lastH) - oT) <= 6) { clampedY = oT - lastH - 2; snapY = true; }
                    else if (Math.abs(clampedY - oT) <= 5) { clampedY = oT; snapY = true; }
                }
            }

            // Prevent overlap with other enabled HUDs by nudging to nearest free edge
            for (HudSlot other : HUD_SLOTS.values()) {
                if (other == this || !other.enabled || other.lastW <= 0 || other.lastH <= 0) continue;
                int oL = other.lastScreenX, oR = other.lastScreenX + other.lastW;
                int oT = other.lastScreenY, oB = other.lastScreenY + other.lastH;
                if (clampedX < oR && clampedX + lastW > oL && clampedY < oB && clampedY + lastH > oT) {
                    int pushLeft = (clampedX + lastW) - oL + 2;
                    int pushRight = oR - clampedX + 2;
                    int pushUp = (clampedY + lastH) - oT + 2;
                    int pushDown = oB - clampedY + 2;
                    int minPush = Math.min(Math.min(pushLeft, pushRight), Math.min(pushUp, pushDown));
                    if (minPush == pushLeft && oL - lastW - 2 >= 0) clampedX = oL - lastW - 2;
                    else if (minPush == pushRight && oR + 2 + lastW <= sw) clampedX = oR + 2;
                    else if (minPush == pushUp && oT - lastH - 2 >= 0) clampedY = oT - lastH - 2;
                    else if (oB + 2 + lastH <= sh) clampedY = oB + 2;
                }
            }
            clampedX = Math.max(0, Math.min(Math.max(0, sw - lastW), clampedX));
            clampedY = Math.max(0, Math.min(Math.max(0, sh - lastH), clampedY));

            snappedThisDrag = snapX || snapY;
        }
    }

    public static final Map<String, HudSlot> HUD_SLOTS = new LinkedHashMap<>();
    static {
        resetHudSlotsDefault();
    }

    public static void resetHudSlotsDefault() {
        HUD_SLOTS.clear();
        HUD_SLOTS.put("hud_time", new HudSlot("hud_time", "时间", "Time", true, 4, 4, 0, 0));
        HUD_SLOTS.put("hud_info", new HudSlot("hud_info", "帧率与延迟", "FPS / Ping / TPS", true, 4, 40, 0, 0));
        HUD_SLOTS.put("hud_speed", new HudSlot("hud_speed", "移动速度", "Speed", true, 0, 36, 1, 0));
        HUD_SLOTS.put("hud_coords", new HudSlot("hud_coords", "坐标", "Coords", true, 4, 2, 0, 2));
        HUD_SLOTS.put("hud_potions", new HudSlot("hud_potions", "药水面板", "Potions", true, 6, 6, 2, 0));
        HUD_SLOTS.put("hud_armor", new HudSlot("hud_armor", "装备耐久", "Armor", true, 102, 0, 1, 2));
        HUD_SLOTS.put("hud_items", new HudSlot("hud_items", "物资计数", "PvP Items", true, 6, 128, 2, 0));
        HUD_SLOTS.put("hud_inv", new HudSlot("hud_inv", "背包预览", "Inventory", false, 6, 168, 2, 0));
        HUD_SLOTS.put("hud_drops", new HudSlot("hud_drops", "物品列表", "Item List", true, 4, 110, 0, 0));
        HUD_SLOTS.put("hud_modules", new HudSlot("hud_modules", "功能列表", "Module List", false, 6, 6, 2, 2));
        HUD_SLOTS.put("hud_player_view", new HudSlot("hud_player_view", "玩家预览", "Player View", false, 6, 120, 0, 1));
        HUD_SLOTS.put("hud_wthit", new HudSlot("hud_wthit", "方块信息", "Block Info", false, 0, 6, 1, 0));
        HUD_SLOTS.put("hud_radar", new HudSlot("hud_radar", "储存数量", "Storage Count", true, 4, 82, 0, 0));
    }

    public static void resetSingleHudSlot(String slotId) {
        Map<String, HudSlot> copy = new LinkedHashMap<>(HUD_SLOTS);
        resetHudSlotsDefault();
        HudSlot fresh = HUD_SLOTS.get(slotId);
        HUD_SLOTS.clear();
        HUD_SLOTS.putAll(copy);
        if (fresh != null && HUD_SLOTS.containsKey(slotId)) {
            HudSlot cur = HUD_SLOTS.get(slotId);
            cur.x = fresh.x;
            cur.y = fresh.y;
            cur.anchorX = fresh.anchorX;
            cur.anchorY = fresh.anchorY;
        }
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

    public static Path getSkiesDir() {
        return getConfigDir().resolve("skies");
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
            Files.createDirectories(getSkiesDir());
        } catch (IOException e) {
            LOGGER.warn("Failed to create Ye config directories", e);
        }
    }

    public static void initDefaultTargetSets() {
        if (blockEspTargets.isEmpty()) {
            blockEspTargets.add("minecraft:diamond_ore");
            blockEspTargets.add("minecraft:deepslate_diamond_ore");
            blockEspTargets.add("minecraft:ancient_debris");
            blockEspTargets.add("minecraft:emerald_ore");
            blockEspTargets.add("minecraft:deepslate_emerald_ore");
            blockEspTargets.add("minecraft:spawner");
        }
        if (blockSearchTargets.isEmpty()) {
            blockSearchTargets.add("minecraft:ancient_debris");
            blockSearchTargets.add("minecraft:nether_portal");
            blockSearchTargets.add("minecraft:end_portal_frame");
        }
        if (dropTierHigh.isEmpty()) {
            dropTierHigh.add("minecraft:elytra");
            dropTierHigh.add("minecraft:totem_of_undying");
            dropTierHigh.add("minecraft:enchanted_golden_apple");
            dropTierHigh.add("minecraft:netherite_helmet");
            dropTierHigh.add("minecraft:netherite_chestplate");
            dropTierHigh.add("minecraft:netherite_leggings");
            dropTierHigh.add("minecraft:netherite_boots");
            dropTierHigh.add("minecraft:netherite_sword");
            dropTierHigh.add("minecraft:netherite_pickaxe");
            dropTierHigh.add("minecraft:netherite_ingot");
            dropTierHigh.add("minecraft:netherite_scrap");
            dropTierHigh.add("minecraft:ancient_debris");
            dropTierHigh.add("minecraft:beacon");
            dropTierHigh.add("minecraft:heavy_core");
            dropTierHigh.add("minecraft:mace");
            dropTierHigh.add("minecraft:trident");
            dropTierHigh.add("minecraft:shulker_box");
        }
        if (dropTierMid.isEmpty()) {
            dropTierMid.add("minecraft:firework_rocket");
            dropTierMid.add("minecraft:obsidian");
            dropTierMid.add("minecraft:crying_obsidian");
            dropTierMid.add("minecraft:end_crystal");
            dropTierMid.add("minecraft:golden_apple");
            dropTierMid.add("minecraft:ender_pearl");
            dropTierMid.add("minecraft:experience_bottle");
            dropTierMid.add("minecraft:diamond");
            dropTierMid.add("minecraft:respawn_anchor");
            dropTierMid.add("minecraft:glowstone");
            dropTierMid.add("minecraft:chorus_fruit");
        }
    }

    public static void initSystem(String initialUser) {
        ensureDirectories();
        initDefaultTargetSets();
        factorySnapshot = buildSnapshot();
        writeJsonToFile(getProfileFile(DEFAULT_PROFILE), factorySnapshot);

        loadMetaAndBackupIfUpgraded();

        currentAccountName = sanitizeProfileName(initialUser);
        String preferred = sanitizeProfileName(savedGlobalProfile);
        if (preferred.equalsIgnoreCase(DEFAULT_PROFILE) || preferred.isBlank()) {
            preferred = SHARED_PROFILE;
        }
        if (!Files.exists(getProfileFile(preferred))) {
            // Migrate existing account file if present, otherwise clone default
            Path oldAcct = getProfileFile(currentAccountName);
            if (Files.exists(oldAcct)) {
                try {
                    Files.copy(oldAcct, getProfileFile(preferred), StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException ignored) {
                    ensurePlayerProfileExists(preferred);
                }
            } else {
                ensurePlayerProfileExists(preferred);
            }
        }
        switchToProfile(preferred, false);
    }

    public static void markRuntimeReady() {
        runtimeReady = true;
    }

    public static void checkAccountSwitch(String detectedName) {
        if (detectedName == null || detectedName.isBlank()) return;
        currentAccountName = sanitizeProfileName(detectedName);
        // Same client / version folder shares one unified config regardless of player ID!
    }

    private static void ensurePlayerProfileExists(String playerName) {
        Path playerFile = getProfileFile(playerName);
        if (Files.exists(playerFile)) return;

        Path legacy = getLegacyConfigFile();
        if (Files.exists(legacy)) {
            try {
                Files.copy(legacy, playerFile, StandardCopyOption.REPLACE_EXISTING);
                return;
            } catch (IOException ignored) {}
        }

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
                if (obj.has("globalActiveProfile")) {
                    savedGlobalProfile = obj.get("globalActiveProfile").getAsString();
                }
            } catch (Exception ignored) {}
        }

        if (!MOD_VERSION.equals(prevVer)) {
            backupAllConfigs(prevVer.isEmpty() ? "pre_1.7.4" : prevVer);
            saveMeta();
        }
    }

    private static void backupAllConfigs(String tag) {
        try {
            String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            Path sub = getBackupsDir().resolve(tag + "_" + stamp);
            boolean copiedAny = false;
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
            meta.addProperty("globalActiveProfile", activeProfile);
            Files.writeString(getMetaFile(), GSON.toJson(meta), StandardCharsets.UTF_8);
        } catch (Exception ignored) {}
    }

    public static List<String> listAvailableProfiles() {
        ensureDirectories();
        List<String> names = new ArrayList<>();
        names.add(DEFAULT_PROFILE);
        names.add(SHARED_PROFILE);
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
            saveMeta();
            return true;
        } catch (IOException e) {
            LOGGER.warn("Failed to rename profile", e);
            return false;
        }
    }

    public static void resetCurrentToFactoryDefault() {
        if (factorySnapshot == null) return;
        if (activeProfile.equalsIgnoreCase(DEFAULT_PROFILE)) {
            activeProfile = SHARED_PROFILE;
            saveMeta();
        }
        applySnapshot(factorySnapshot, true);
        writeJsonToFile(getProfileFile(DEFAULT_PROFILE), factorySnapshot);
        saveNow();
    }

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
                activeProfile = SHARED_PROFILE;
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
        uiStyle = UiStyle.SILKY;
        uiTheme = UiTheme.DARK;
        lightTheme = false;
        uiOpacity = 0.92f;
        uiBlurStrength = 0.48f;
        glassBlurStrength = 10.0f;
        rusherSidebar = true;
        useClientFont = false;
        themeAccent = ThemeAccent.BLUE;
        customAccentHue = 0.56f;
        customAccentSat = 0.78f;
        customAccentBri = 0.98f;
        english = false;
        uiClickSound = YeSoundPresets.UiSoundType.MECH_SWITCH;
        uiSoundVolume = 0.80f;
        pinnedModules.clear();
        favoriteModules.clear();
        customRadarIgnoreBlocks.clear();
        entityEspCustomTypes.clear();
        blockEspTargets.clear();
        blockSearchTargets.clear();
        dropTierHigh.clear();
        dropTierMid.clear();
        initDefaultTargetSets();
        resetHudSlotsDefault();
        for (Module m : ModuleManager.getAll()) {
            for (Setting<?> s : m.getSettings()) s.resetSilent();
            m.setKeybindSilent(-1);
            m.setPresetCycleKeySilent(-1);
            m.applyLoadedEnabled(m.isDefaultEnabled(), fireCallbacks);
        }
    }

    private static void applySnapshot(JsonObject root, boolean fireCallbacks) {
        loading = true;
        try {
            resetInMemoryToFactoryDefaults(fireCallbacks);

            String savedVer = root.has("modVersion") ? root.get("modVersion").getAsString() : "";

            if (root.has("menuKey")) menuKey = root.get("menuKey").getAsInt();
            if (root.has("mouseUnlockKey")) mouseUnlockKey = root.get("mouseUnlockKey").getAsInt();
            if (root.has("widgetX")) widgetX = root.get("widgetX").getAsInt();
            if (root.has("widgetY")) widgetY = root.get("widgetY").getAsInt();
            if (root.has("uiStyle")) {
                try { uiStyle = UiStyle.valueOf(root.get("uiStyle").getAsString()); } catch (Exception ignored) {}
            }
            if (root.has("uiTheme")) {
                try { uiTheme = UiTheme.valueOf(root.get("uiTheme").getAsString()); } catch (Exception ignored) {}
            } else if (root.has("lightTheme") && root.get("lightTheme").getAsBoolean()) {
                uiTheme = UiTheme.LIGHT;
            }
            lightTheme = (uiTheme == UiTheme.LIGHT);

            if (root.has("uiOpacity")) uiOpacity = Math.max(0.25f, Math.min(1.0f, root.get("uiOpacity").getAsFloat()));
            if (root.has("uiBlurStrength")) uiBlurStrength = Math.max(0.0f, Math.min(1.0f, root.get("uiBlurStrength").getAsFloat()));
            if (root.has("glassBlurStrength")) glassBlurStrength = Math.max(2.0f, Math.min(20.0f, root.get("glassBlurStrength").getAsFloat()));
            if (root.has("rusherSidebar")) rusherSidebar = root.get("rusherSidebar").getAsBoolean();
            if (root.has("useClientFont")) useClientFont = root.get("useClientFont").getAsBoolean();
            if (root.has("statPlayerKills")) statPlayerKills = Math.max(0, root.get("statPlayerKills").getAsInt());
            if (root.has("statPlayerDeaths")) statPlayerDeaths = Math.max(0, root.get("statPlayerDeaths").getAsInt());

            if (root.has("themeAccent")) {
                String ta = root.get("themeAccent").getAsString();
                try {
                    themeAccent = switch (ta) {
                        case "APPLE_BLUE" -> ThemeAccent.BLUE;
                        case "CYBER_PURPLE" -> ThemeAccent.PURPLE;
                        case "CRIMSON" -> ThemeAccent.RED;
                        case "EMERALD" -> ThemeAccent.GREEN;
                        case "SAKURA" -> ThemeAccent.PINK;
                        default -> ThemeAccent.valueOf(ta);
                    };
                } catch (Exception ignored) {}
            }
            if (root.has("customAccentHue")) customAccentHue = root.get("customAccentHue").getAsFloat();
            if (root.has("customAccentSat")) customAccentSat = root.get("customAccentSat").getAsFloat();
            if (root.has("customAccentBri")) customAccentBri = root.get("customAccentBri").getAsFloat();
            if (root.has("english")) english = root.get("english").getAsBoolean();
            if (root.has("uiClickSound")) {
                try {
                    uiClickSound = YeSoundPresets.UiSoundType.valueOf(root.get("uiClickSound").getAsString());
                } catch (Exception ignored) {
                    uiClickSound = YeSoundPresets.UiSoundType.MECH_SWITCH;
                }
            }
            if (root.has("uiSoundVolume")) uiSoundVolume = Math.max(0.0f, Math.min(1.25f, root.get("uiSoundVolume").getAsFloat()));

            readStringSet(root, "pinnedModules", pinnedModules, true);
            readStringSet(root, "favoriteModules", favoriteModules, true);
            readStringSet(root, "customRadarIgnoreBlocks", customRadarIgnoreBlocks, true);
            readStringSet(root, "entityEspCustomTypes", entityEspCustomTypes, true);
            if (root.has("blockEspTargets")) readStringSet(root, "blockEspTargets", blockEspTargets, true);
            if (root.has("blockSearchTargets")) readStringSet(root, "blockSearchTargets", blockSearchTargets, true);
            if (root.has("dropTierHigh")) readStringSet(root, "dropTierHigh", dropTierHigh, true);
            if (root.has("dropTierMid")) readStringSet(root, "dropTierMid", dropTierMid, true);

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
                    try { module.setKeybindSilent(mObj.get("keybind").getAsInt()); } catch (Exception ignored) {}
                }
                if (mObj.has("presetCycleKey")) {
                    try { module.setPresetCycleKeySilent(mObj.get("presetCycleKey").getAsInt()); } catch (Exception ignored) {}
                }
                if (mObj.has("settings") && mObj.get("settings").isJsonObject()) {
                    JsonObject sObj = mObj.getAsJsonObject("settings");
                    for (Setting<?> setting : module.getSettings()) {
                        // Force oppressiveVignette OFF when migrating from <= 1.7.2 per user request!
                        if (savedVer.compareTo("1.7.3") < 0 && "ambience".equals(module.getId()) && "oppressiveVignette".equals(setting.getId())) {
                            continue;
                        }
                        if (sObj.has(setting.getId())) {
                            try {
                                setting.fromJson(sObj.get(setting.getId()));
                            } catch (Exception ignored) {}
                        }
                    }
                }
                if (mObj.has("enabled")) {
                    try {
                        boolean en = mObj.get("enabled").getAsBoolean();
                        if (savedVer.compareTo("1.7.4") < 0 && "ambience".equals(module.getId())) {
                            en = false;
                        }
                        module.applyLoadedEnabled(en, fireCallbacks);
                    } catch (Exception ignored) {}
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Partial error applying Ye config snapshot", e);
        } finally {
            loading = false;
        }
    }

    private static void readStringSet(JsonObject root, String key, Set<String> out, boolean clearFirst) {
        if (!root.has(key) || !root.get(key).isJsonArray()) return;
        if (clearFirst) out.clear();
        for (JsonElement el : root.getAsJsonArray(key)) {
            String s = el.getAsString().trim();
            if (!s.isEmpty()) out.add(s);
        }
    }

    private static void writeStringSet(JsonObject root, String key, Set<String> set) {
        JsonArray arr = new JsonArray();
        List<String> sorted = new ArrayList<>(set);
        sorted.sort(String.CASE_INSENSITIVE_ORDER);
        for (String s : sorted) arr.add(s);
        root.add(key, arr);
    }

    public static void saveAsync() {
        if (loading) return;
        if (activeProfile.equalsIgnoreCase(DEFAULT_PROFILE)) {
            activeProfile = SHARED_PROFILE;
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
        root.addProperty("uiStyle", uiStyle.name());
        root.addProperty("uiTheme", uiTheme.name());
        root.addProperty("lightTheme", uiTheme == UiTheme.LIGHT);
        root.addProperty("uiOpacity", uiOpacity);
        root.addProperty("uiBlurStrength", uiBlurStrength);
        root.addProperty("glassBlurStrength", glassBlurStrength);
        root.addProperty("rusherSidebar", rusherSidebar);
        root.addProperty("useClientFont", useClientFont);
        root.addProperty("statPlayerKills", statPlayerKills);
        root.addProperty("statPlayerDeaths", statPlayerDeaths);
        root.addProperty("themeAccent", themeAccent.name());
        root.addProperty("customAccentHue", customAccentHue);
        root.addProperty("customAccentSat", customAccentSat);
        root.addProperty("customAccentBri", customAccentBri);
        root.addProperty("english", english);
        root.addProperty("uiClickSound", uiClickSound.name());
        root.addProperty("uiSoundVolume", uiSoundVolume);

        writeStringSet(root, "pinnedModules", pinnedModules);
        writeStringSet(root, "favoriteModules", favoriteModules);
        writeStringSet(root, "customRadarIgnoreBlocks", customRadarIgnoreBlocks);
        writeStringSet(root, "entityEspCustomTypes", entityEspCustomTypes);
        writeStringSet(root, "blockEspTargets", blockEspTargets);
        writeStringSet(root, "blockSearchTargets", blockSearchTargets);
        writeStringSet(root, "dropTierHigh", dropTierHigh);
        writeStringSet(root, "dropTierMid", dropTierMid);

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
            mObj.addProperty("presetCycleKey", module.getPresetCycleKey());

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
