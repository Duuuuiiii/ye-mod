package com.ye.mod.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shared persistent Friend Whitelist (stored in config/ye/friends.json).
 * Used by AutoLog (ignore friends in visual range) and EntityESP (highlight friends in distinct color).
 */
public final class FriendManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Set<String> FRIENDS_LOWER = ConcurrentHashMap.newKeySet();
    private static final MapNameStore DISPLAY_NAMES = new MapNameStore();

    private static final class MapNameStore {
        private final ConcurrentHashMap<String, String> map = new ConcurrentHashMap<>();
        void put(String name) { map.put(name.toLowerCase(Locale.ROOT), name); }
        void remove(String name) { map.remove(name.toLowerCase(Locale.ROOT)); }
        void clear() { map.clear(); }
        List<String> valuesSorted() {
            List<String> out = new ArrayList<>(map.values());
            out.sort(String.CASE_INSENSITIVE_ORDER);
            return out;
        }
    }

    private FriendManager() {}

    public static Path getFriendsFile() {
        return ConfigManager.getConfigDir().resolve("friends.json");
    }

    public static void init() {
        ConfigManager.ensureDirectories();
        Path file = getFriendsFile();
        if (!Files.exists(file)) return;
        try {
            String raw = Files.readString(file, StandardCharsets.UTF_8);
            JsonElement el = JsonParser.parseString(raw);
            if (el.isJsonArray()) {
                FRIENDS_LOWER.clear();
                DISPLAY_NAMES.clear();
                for (JsonElement item : el.getAsJsonArray()) {
                    String n = item.getAsString().trim();
                    if (!n.isEmpty()) {
                        FRIENDS_LOWER.add(n.toLowerCase(Locale.ROOT));
                        DISPLAY_NAMES.put(n);
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    public static void save() {
        try {
            ConfigManager.ensureDirectories();
            JsonArray arr = new JsonArray();
            for (String n : DISPLAY_NAMES.valuesSorted()) {
                arr.add(n);
            }
            Files.writeString(getFriendsFile(), GSON.toJson(arr), StandardCharsets.UTF_8);
        } catch (Exception ignored) {}
    }

    public static boolean isFriend(String name) {
        if (name == null || name.isEmpty()) return false;
        return FRIENDS_LOWER.contains(name.toLowerCase(Locale.ROOT));
    }

    public static boolean isFriend(Player player) {
        if (player == null) return false;
        return isFriend(player.getGameProfile().name());
    }

    public static boolean addFriend(String name) {
        if (name == null || name.isBlank()) return false;
        String clean = name.trim();
        boolean added = FRIENDS_LOWER.add(clean.toLowerCase(Locale.ROOT));
        DISPLAY_NAMES.put(clean);
        save();
        return added;
    }

    public static boolean removeFriend(String name) {
        if (name == null || name.isBlank()) return false;
        String clean = name.trim();
        boolean removed = FRIENDS_LOWER.remove(clean.toLowerCase(Locale.ROOT));
        DISPLAY_NAMES.remove(clean);
        save();
        return removed;
    }

    public static boolean toggleFriend(String name) {
        if (isFriend(name)) {
            removeFriend(name);
            return false;
        } else {
            addFriend(name);
            return true;
        }
    }

    public static void clearFriends() {
        FRIENDS_LOWER.clear();
        DISPLAY_NAMES.clear();
        save();
    }

    public static List<String> getFriends() {
        return Collections.unmodifiableList(DISPLAY_NAMES.valuesSorted());
    }

    public static int count() {
        return FRIENDS_LOWER.size();
    }

    /**
     * Toggles friend status of the player under the crosshair, or returns null if not looking at a player.
     */
    public static String toggleCrosshairPlayer() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.hitResult instanceof EntityHitResult ehr) {
            Entity target = ehr.getEntity();
            if (target instanceof Player p && p != mc.player) {
                String name = p.getGameProfile().name();
                boolean nowFriend = toggleFriend(name);
                return (nowFriend ? "+" : "-") + name;
            }
        }
        return null;
    }

    /**
     * Adds all currently visible players within 32 blocks as friends (convenient for team setup!).
     */
    public static int addNearbyPlayers(double maxDist) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return 0;
        int added = 0;
        for (Player p : mc.level.players()) {
            if (p == mc.player) continue;
            if (mc.player.distanceTo(p) <= maxDist) {
                if (addFriend(p.getGameProfile().name())) {
                    added++;
                }
            }
        }
        return added;
    }
}
