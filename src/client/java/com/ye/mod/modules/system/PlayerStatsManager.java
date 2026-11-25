package com.ye.mod.modules.system;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.ye.mod.core.ConfigManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.util.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tracks per-player chat, kill, and death statistics and writes readable local logs.
 * Always records in the background even when messages are hidden from chat.
 */
public final class PlayerStatsManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("ye-stats");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type STATS_TYPE = new TypeToken<Map<String, PlayerRecord>>() {}.getType();
    private static final Type MUTED_TYPE = new TypeToken<Set<String>>() {}.getType();
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final Pattern ANGLE_CHAT = Pattern.compile("^<([A-Za-z0-9_]{3,16})>\\s*(.+)$");
    private static final Pattern PREFIX_CHAT = Pattern.compile("^(?:\\[[^\\]]+\\]\\s*)*([A-Za-z0-9_]{3,16})\\s*[:>»]\\s*(.+)$");

    public enum MessageKind {
        PLAYER_CHAT,
        DEATH,
        ADVANCEMENT,
        JOIN_LEAVE,
        OTHER
    }

    public record ParsedMessage(MessageKind kind, String primaryPlayer, String secondaryPlayer, String plainText) {}

    public static final class PlayerRecord {
        public String name;
        public int messages;
        public int kills;
        public int deaths;
        public String lastSeen = "";

        public PlayerRecord() {}

        public PlayerRecord(String name) {
            this.name = name;
        }
    }

    private static final Map<String, PlayerRecord> STATS = new ConcurrentHashMap<>();
    private static final Set<String> MUTED_PLAYERS = ConcurrentHashMap.newKeySet();
    private static final ExecutorService IO_EXEC = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "ye-stats-io");
        t.setDaemon(true);
        return t;
    });

    private static volatile String activeServerKey = "";
    private static volatile boolean mutedLoaded = false;

    private PlayerStatsManager() {}

    public static void init() {
        loadMuted();
    }

    public static Path getStatsDir() {
        return ConfigManager.getConfigDir().resolve("stats");
    }

    public static Path getLogsDir() {
        return ConfigManager.getConfigDir().resolve("logs");
    }

    public static Path getMutedFile() {
        return ConfigManager.getConfigDir().resolve("muted_players.json");
    }

    public static void checkContext() {
        Minecraft mc = Minecraft.getInstance();
        String key = resolveServerKey(mc);
        if (!key.equals(activeServerKey)) {
            saveStatsNow();
            activeServerKey = key;
            STATS.clear();
            if (!key.isEmpty()) {
                loadStats(key);
            }
        }
    }

    public static Collection<PlayerRecord> getAllRecords() {
        return Collections.unmodifiableCollection(STATS.values());
    }

    public static boolean isMuted(String playerName) {
        if (playerName == null || playerName.isBlank()) return false;
        return MUTED_PLAYERS.contains(playerName.toLowerCase(Locale.ROOT));
    }

    public static Set<String> getMutedPlayers() {
        return Collections.unmodifiableSet(MUTED_PLAYERS);
    }

    public static void toggleMute(String playerName) {
        if (playerName == null || playerName.isBlank()) return;
        String lower = playerName.trim().toLowerCase(Locale.ROOT);
        if (!MUTED_PLAYERS.remove(lower)) {
            MUTED_PLAYERS.add(lower);
        }
        saveMutedAsync();
    }

    public static void addMute(String playerName) {
        if (playerName == null || playerName.isBlank()) return;
        MUTED_PLAYERS.add(playerName.trim().toLowerCase(Locale.ROOT));
        saveMutedAsync();
    }

    public static void clearCurrentStats() {
        STATS.clear();
        String key = activeServerKey;
        if (!key.isEmpty()) {
            IO_EXEC.submit(() -> {
                try {
                    Files.deleteIfExists(getStatsDir().resolve(key + ".json"));
                } catch (IOException ignored) {}
            });
        }
    }

    public static void openLogsFolder() {
        try {
            Files.createDirectories(getLogsDir());
        } catch (IOException ignored) {}
        Util.getPlatform().openPath(getLogsDir());
    }

    public static ParsedMessage analyzeAndRecord(Component message) {
        checkContext();
        String plain = message.getString();
        String now = LocalDateTime.now().format(TIME_FMT);

        // 1. Inspect TranslatableContents recursively
        TranslatableMatch tm = findTranslatable(message);
        if (tm != null) {
            if (tm.key.startsWith("chat.type.advancement.")) {
                return new ParsedMessage(MessageKind.ADVANCEMENT, tm.p1, null, plain);
            }
            if (tm.key.equals("multiplayer.player.joined")
                    || tm.key.equals("multiplayer.player.joined.renamed")
                    || tm.key.equals("multiplayer.player.left")) {
                return new ParsedMessage(MessageKind.JOIN_LEAVE, tm.p1, null, plain);
            }
            if (tm.key.startsWith("death.")) {
                String victimName = resolveRealPlayerId(tm.p1);
                String killerName = resolveRealPlayerId(tm.p2);
                if (victimName != null) {
                    PlayerRecord victim = getOrCreate(victimName);
                    victim.deaths++;
                    victim.lastSeen = now;
                }
                if (killerName != null) {
                    PlayerRecord killer = getOrCreate(killerName);
                    killer.kills++;
                    killer.lastSeen = now;
                }
                if (victimName != null || killerName != null) {
                    appendLog("kill_log.txt", "[" + now + "] " + stripFormatting(plain));
                    saveStatsAsync();
                }
                return new ParsedMessage(MessageKind.DEATH, victimName, killerName, plain);
            }
            if (tm.key.equals("chat.type.text") || tm.key.equals("chat.type.announcement")) {
                String sender = resolveRealPlayerId(tm.p1);
                if (sender != null) {
                    PlayerRecord rec = getOrCreate(sender);
                    rec.messages++;
                    rec.lastSeen = now;
                    appendLog("chat_log.txt", "[" + now + "] " + stripFormatting(plain));
                    saveStatsAsync();
                    return new ParsedMessage(MessageKind.PLAYER_CHAT, sender, null, plain);
                }
            }
        }

        // 2. Fallback regex for server-formatted chat (e.g. "<Name> msg" or "[Rank] Name: msg")
        String trimmed = stripFormatting(plain).trim();
        Matcher m1 = ANGLE_CHAT.matcher(trimmed);
        if (m1.matches()) {
            String sender = resolveRealPlayerId(m1.group(1));
            if (sender != null) {
                recordChat(sender, now, plain);
                return new ParsedMessage(MessageKind.PLAYER_CHAT, sender, null, plain);
            }
        }
        Matcher m2 = PREFIX_CHAT.matcher(trimmed);
        if (m2.matches()) {
            String sender = resolveRealPlayerId(m2.group(1));
            if (sender != null) {
                recordChat(sender, now, plain);
                return new ParsedMessage(MessageKind.PLAYER_CHAT, sender, null, plain);
            }
        }

        return new ParsedMessage(MessageKind.OTHER, null, null, plain);
    }

    private static void recordChat(String sender, String now, String plain) {
        PlayerRecord rec = getOrCreate(sender);
        rec.messages++;
        rec.lastSeen = now;
        appendLog("chat_log.txt", "[" + now + "] " + plain);
        saveStatsAsync();
    }

    private static PlayerRecord getOrCreate(String name) {
        String key = name.toLowerCase(Locale.ROOT);
        return STATS.computeIfAbsent(key, k -> new PlayerRecord(name));
    }

    private static String stripFormatting(String s) {
        return s == null ? "" : s.replaceAll("§.", "");
    }

    /**
     * Extracts a clean Minecraft player ID (no head glyphs/prefixes) and verifies it is an actual player.
     */
    private static String resolveRealPlayerId(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String clean = stripFormatting(raw).trim();
        Matcher m = Pattern.compile("([A-Za-z0-9_]{3,16})").matcher(clean);
        while (m.find()) {
            String candidate = m.group(1);
            if (isOnlinePlayer(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private static boolean isOnlinePlayer(String name) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) return false;
        for (PlayerInfo info : mc.getConnection().getOnlinePlayers()) {
            if (info.getProfile().name().equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    private record TranslatableMatch(String key, String p1, String p2) {}

    private static TranslatableMatch findTranslatable(Component comp) {
        if (comp.getContents() instanceof TranslatableContents tc) {
            String key = tc.getKey();
            if (key.startsWith("chat.type.advancement.")
                    || key.startsWith("death.")
                    || key.equals("chat.type.text")
                    || key.equals("chat.type.announcement")
                    || key.startsWith("multiplayer.player.")) {
                Object[] args = tc.getArgs();
                String p1 = args.length > 0 ? extractArgText(args[0]) : null;
                String p2 = args.length > 1 ? extractArgText(args[1]) : null;
                return new TranslatableMatch(key, p1, p2);
            }
            for (Object arg : tc.getArgs()) {
                if (arg instanceof Component sub) {
                    TranslatableMatch m = findTranslatable(sub);
                    if (m != null) return m;
                }
            }
        }
        for (Component sib : comp.getSiblings()) {
            TranslatableMatch m = findTranslatable(sib);
            if (m != null) return m;
        }
        return null;
    }

    private static String extractArgText(Object arg) {
        if (arg instanceof Component c) return c.getString().replaceAll("§.", "").trim();
        return arg != null ? arg.toString().replaceAll("§.", "").trim() : null;
    }

    private static String resolveServerKey(Minecraft mc) {
        if (mc.level == null) return "";
        if (mc.getCurrentServer() != null && mc.getCurrentServer().ip != null) {
            return mc.getCurrentServer().ip.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "_");
        }
        if (mc.getSingleplayerServer() != null) {
            return ("sp_" + mc.getSingleplayerServer().getWorldData().getLevelName())
                    .toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "_");
        }
        return "default";
    }

    private static void appendLog(String fileName, String line) {
        String key = activeServerKey.isEmpty() ? "default" : activeServerKey;
        IO_EXEC.submit(() -> {
            try {
                Path dir = getLogsDir().resolve(key);
                Files.createDirectories(dir);
                Files.writeString(dir.resolve(fileName), line + "\n", StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException e) {
                LOGGER.debug("Failed writing log {}: {}", fileName, e.getMessage());
            }
        });
    }

    private static void loadStats(String serverKey) {
        IO_EXEC.submit(() -> {
            Path file = getStatsDir().resolve(serverKey + ".json");
            if (!Files.exists(file)) return;
            try {
                String raw = Files.readString(file, StandardCharsets.UTF_8);
                Map<String, PlayerRecord> loaded = GSON.fromJson(raw, STATS_TYPE);
                if (loaded != null && serverKey.equals(activeServerKey)) {
                    STATS.putAll(loaded);
                }
            } catch (Exception e) {
                LOGGER.warn("Failed loading stats {}", file, e);
            }
        });
    }

    private static void saveStatsAsync() {
        String key = activeServerKey;
        if (key.isEmpty()) return;
        Map<String, PlayerRecord> copy = new ConcurrentHashMap<>(STATS);
        IO_EXEC.submit(() -> writeJson(getStatsDir().resolve(key + ".json"), copy));
    }

    public static void saveStatsNow() {
        String key = activeServerKey;
        if (key.isEmpty()) return;
        writeJson(getStatsDir().resolve(key + ".json"), new ConcurrentHashMap<>(STATS));
    }

    private static void loadMuted() {
        if (mutedLoaded) return;
        mutedLoaded = true;
        Path file = getMutedFile();
        if (!Files.exists(file)) return;
        try {
            String raw = Files.readString(file, StandardCharsets.UTF_8);
            Set<String> loaded = GSON.fromJson(raw, MUTED_TYPE);
            if (loaded != null) {
                MUTED_PLAYERS.addAll(loaded);
            }
        } catch (Exception e) {
            LOGGER.warn("Failed loading muted players", e);
        }
    }

    private static void saveMutedAsync() {
        Set<String> copy = new HashSet<>(MUTED_PLAYERS);
        IO_EXEC.submit(() -> writeJson(getMutedFile(), copy));
    }

    private static void writeJson(Path file, Object obj) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(obj), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.debug("Failed writing json {}: {}", file, e.getMessage());
        }
    }
}
