package com.ye.mod.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Loads official Mojang Simplified Chinese (`minecraft/lang/zh_cn.json`) directly from the launcher's
 * local `assets/` cache so ALL items, blocks, entities, and potion effects display authentic Chinese names
 * even when the player sets Minecraft's vanilla language to English (`en_us`)!
 */
public final class YeZhCnDict {
    private static final Map<String, String> ZH_MAP = new ConcurrentHashMap<>(4096);
    private static volatile boolean initialized = false;

    private YeZhCnDict() {}

    public static void ensureInitAsync() {
        if (initialized) return;
        initialized = true;
        Thread t = new Thread(YeZhCnDict::loadFromAssetsCache, "ye-zhcn-loader");
        t.setDaemon(true);
        t.start();
    }

    private static void loadFromAssetsCache() {
        Path gameDir = FabricLoader.getInstance().getGameDir().toAbsolutePath();
        // Check gameDir/assets (standard .minecraft) and gameDir/../../assets (HMCL/PCL2 version isolation: .minecraft/versions/<ver>)
        Path[] candidates = new Path[]{
                gameDir.resolve("assets"),
                gameDir.getParent() != null && gameDir.getParent().getParent() != null
                        ? gameDir.getParent().getParent().resolve("assets") : null
        };

        for (Path assetsDir : candidates) {
            if (assetsDir == null) continue;
            Path indexesDir = assetsDir.resolve("indexes");
            Path objectsDir = assetsDir.resolve("objects");
            if (!Files.isDirectory(indexesDir) || !Files.isDirectory(objectsDir)) continue;

            try (Stream<Path> s = Files.list(indexesDir)) {
                List<Path> idxFiles = s.filter(p -> p.getFileName().toString().endsWith(".json"))
                        .sorted(Comparator.comparingLong((Path p) -> {
                            try { return -Files.getLastModifiedTime(p).toMillis(); } catch (Exception e) { return 0L; }
                        }))
                        .toList();

                for (Path idxFile : idxFiles) {
                    if (tryParseIndexForZhCn(idxFile, objectsDir)) {
                        return;
                    }
                }
            } catch (Exception ignored) {}
        }
    }

    private static boolean tryParseIndexForZhCn(Path idxFile, Path objectsDir) {
        try {
            JsonObject root = JsonParser.parseString(Files.readString(idxFile, StandardCharsets.UTF_8)).getAsJsonObject();
            if (!root.has("objects")) return false;
            JsonObject objs = root.getAsJsonObject("objects");
            if (!objs.has("minecraft/lang/zh_cn.json")) return false;

            JsonObject entry = objs.getAsJsonObject("minecraft/lang/zh_cn.json");
            String hash = entry.get("hash").getAsString();
            Path langFile = objectsDir.resolve(hash.substring(0, 2)).resolve(hash);
            if (!Files.isRegularFile(langFile)) return false;

            JsonObject langJson = JsonParser.parseString(Files.readString(langFile, StandardCharsets.UTF_8)).getAsJsonObject();
            for (Map.Entry<String, JsonElement> kv : langJson.entrySet()) {
                if (kv.getValue().isJsonPrimitive()) {
                    ZH_MAP.put(kv.getKey(), kv.getValue().getAsString());
                }
            }
            return !ZH_MAP.isEmpty();
        } catch (Exception ignored) {
            return false;
        }
    }

    public static String itemName(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "";
        if (ConfigManager.english) return stack.getHoverName().getString();
        ensureInitAsync();
        // Keep custom anvil-renamed names intact!
        if (stack.has(DataComponents.CUSTOM_NAME)) {
            return stack.getHoverName().getString();
        }
        String descId = stack.getItem().getDescriptionId();
        String zh = ZH_MAP.get(descId);
        if (zh != null && !zh.isEmpty()) return zh;
        return stack.getHoverName().getString();
    }

    public static String itemName(Item item) {
        if (item == null) return "";
        if (ConfigManager.english) return item.getName().getString();
        ensureInitAsync();
        String zh = ZH_MAP.get(item.getDescriptionId());
        return (zh != null && !zh.isEmpty()) ? zh : item.getName().getString();
    }

    public static String blockName(Block block) {
        if (block == null) return "";
        if (ConfigManager.english) return block.getName().getString();
        ensureInitAsync();
        String zh = ZH_MAP.get(block.getDescriptionId());
        return (zh != null && !zh.isEmpty()) ? zh : block.getName().getString();
    }

    public static String entityTypeName(EntityType<?> type) {
        if (type == null) return "";
        if (ConfigManager.english) return type.getDescription().getString();
        ensureInitAsync();
        String zh = ZH_MAP.get(type.getDescriptionId());
        return (zh != null && !zh.isEmpty()) ? zh : type.getDescription().getString();
    }

    public static String effectName(MobEffect effect) {
        if (effect == null) return "";
        if (ConfigManager.english) return effect.getDisplayName().getString();
        ensureInitAsync();
        String zh = ZH_MAP.get(effect.getDescriptionId());
        return (zh != null && !zh.isEmpty()) ? zh : effect.getDisplayName().getString();
    }
}
