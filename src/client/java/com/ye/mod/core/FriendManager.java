package com.ye.mod.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.ye.mod.ui.YeQuickMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

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
 * Supports dedicated top-level Friends tab + close-range Middle-Click Friend toggle while walking!
 */
public final class FriendManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Set<String> FRIENDS_LOWER = ConcurrentHashMap.newKeySet();
    private static final MapNameStore DISPLAY_NAMES = new MapNameStore();

    public static volatile boolean middleClickAdd = false;
    public static final double MIDDLE_CLICK_DIST = 4.5;
    private static boolean prevMiddleDown = false;

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
            FRIENDS_LOWER.clear();
            DISPLAY_NAMES.clear();
            if (el.isJsonArray()) {
                for (JsonElement item : el.getAsJsonArray()) {
                    String n = item.getAsString().trim();
                    if (!n.isEmpty()) {
                        FRIENDS_LOWER.add(n.toLowerCase(Locale.ROOT));
                        DISPLAY_NAMES.put(n);
                    }
                }
            } else if (el.isJsonObject()) {
                JsonObject obj = el.getAsJsonObject();
                if (obj.has("middleClickAdd")) middleClickAdd = obj.get("middleClickAdd").getAsBoolean();
                if (obj.has("friends") && obj.get("friends").isJsonArray()) {
                    for (JsonElement item : obj.getAsJsonArray("friends")) {
                        String n = item.getAsString().trim();
                        if (!n.isEmpty()) {
                            FRIENDS_LOWER.add(n.toLowerCase(Locale.ROOT));
                            DISPLAY_NAMES.put(n);
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    public static void save() {
        try {
            ConfigManager.ensureDirectories();
            JsonObject root = new JsonObject();
            root.addProperty("middleClickAdd", middleClickAdd);
            JsonArray arr = new JsonArray();
            for (String n : DISPLAY_NAMES.valuesSorted()) {
                arr.add(n);
            }
            root.add("friends", arr);
            Files.writeString(getFriendsFile(), GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (Exception ignored) {}
    }

    public static void onClientTick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getWindow() == null) return;
        boolean mDown = GLFW.glfwGetMouseButton(mc.getWindow().handle(), GLFW.GLFW_MOUSE_BUTTON_MIDDLE) == GLFW.GLFW_PRESS;
        if (middleClickAdd && mc.screen == null && mc.player != null && mc.level != null && mDown && !prevMiddleDown) {
            Player hitPlayer = raycastClosePlayer(mc, MIDDLE_CLICK_DIST);
            if (hitPlayer != null) {
                String name = hitPlayer.getGameProfile().name();
                boolean added = toggleFriend(name);
                YeQuickMenu.playClick();
                if (added) {
                    YeQuickMenu.showToast(ConfigManager.tr("已添加好友: ", "Added Friend: ") + name, 0xFF34D399);
                } else {
                    YeQuickMenu.showToast(ConfigManager.tr("已移除好友: ", "Removed Friend: ") + name, 0xFFEF4444);
                }
            }
        }
        prevMiddleDown = mDown;
    }

    private static Player raycastClosePlayer(Minecraft mc, double maxDist) {
        if (mc.hitResult instanceof EntityHitResult ehr && ehr.getEntity() instanceof Player p && p != mc.player) {
            if (mc.player.distanceTo(p) <= maxDist) return p;
        }
        Vec3 eye = mc.player.getEyePosition(1.0f);
        Vec3 look = mc.player.getViewVector(1.0f);
        Vec3 end = eye.add(look.scale(maxDist));
        AABB searchBox = mc.player.getBoundingBox().expandTowards(look.scale(maxDist)).inflate(1.0);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(
                mc.player, eye, end, searchBox,
                e -> e instanceof Player && e != mc.player && !e.isSpectator(),
                maxDist * maxDist
        );
        if (hit != null && hit.getEntity() instanceof Player p) {
            return p;
        }
        return null;
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

    public static String toggleCrosshairPlayer() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return null;
        Player p = raycastClosePlayer(mc, MIDDLE_CLICK_DIST);
        if (p != null) {
            String name = p.getGameProfile().name();
            boolean nowFriend = toggleFriend(name);
            return (nowFriend ? "+" : "-") + name;
        }
        return null;
    }

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
