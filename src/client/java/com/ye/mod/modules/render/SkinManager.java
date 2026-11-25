package com.ye.mod.modules.render;

import com.mojang.blaze3d.platform.NativeImage;
import com.ye.mod.core.ConfigManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Manages user-provided local PNG skins under config/ye/skins/.
 */
public final class SkinManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("ye-skins");
    private static final Map<String, SkinEntry> SKINS = new LinkedHashMap<>();

    public record SkinEntry(String id, String displayName, Identifier textureId) {}

    private SkinManager() {}

    public static Path getSkinsDir() {
        return ConfigManager.getConfigDir().resolve("skins");
    }

    public static void init() {
        refreshSkins();
    }

    public static void refreshSkins() {
        SKINS.clear();
        registerPreset("preset:assassin", "暗夜刺客", 0xFF14161F, 0xFF1E2230, 0xFFA855F7, 0xFF2D1B4E);
        registerPreset("preset:cyber", "赛博霓虹", 0xFF0F172A, 0xFF1E293B, 0xFF38BDF8, 0xFF0284C7);
        registerPreset("preset:knight", "炽焰骑士", 0xFF271414, 0xFF451A1A, 0xFFF59E0B, 0xFF991B1B);
        registerPreset("preset:frost", "冰霜法师", 0xFF0C1E2E, 0xFF16324A, 0xFF67E8F9, 0xFF0E7490);
        try {
            Files.createDirectories(getSkinsDir());
        } catch (IOException e) {
            LOGGER.warn("Failed to create skins directory", e);
            return;
        }

        try (Stream<Path> files = Files.list(getSkinsDir())) {
            files.filter(p -> Files.isRegularFile(p) && p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".png"))
                    .sorted()
                    .forEach(SkinManager::loadSkin);
        } catch (IOException e) {
            LOGGER.warn("Failed to scan skins directory", e);
        }
    }

    private static void registerPreset(String id, String name, int darkCol, int midCol, int eyeCol, int accentCol) {
        String slug = id.replace("preset:", "");
        Identifier texId = Identifier.fromNamespaceAndPath("ye", "skins/preset_" + slug);
        NativeImage img = new NativeImage(64, 64, true);
        // Base fill
        for (int y = 0; y < 64; y++) {
            for (int x = 0; x < 64; x++) {
                boolean checker = ((x + y) & 1) == 0;
                img.setPixel(x, y, checker ? darkCol : midCol);
            }
        }
        // Head face (8..15, 8..15)
        for (int y = 8; y < 16; y++) {
            for (int x = 8; x < 16; x++) {
                img.setPixel(x, y, (y < 10) ? accentCol : ((y == 12 && (x == 9 || x == 10 || x == 13 || x == 14)) ? eyeCol : 0xFFE5C29F));
            }
        }
        // Torso chest accent (20..27, 20..31)
        for (int y = 20; y < 32; y++) {
            for (int x = 20; x < 28; x++) {
                img.setPixel(x, y, (y == 27 || x == 23 || x == 24) ? accentCol : midCol);
            }
        }
        DynamicTexture texture = new DynamicTexture(() -> "ye_skin_" + slug, img);
        Minecraft.getInstance().getTextureManager().register(texId, texture);
        SKINS.put(id, new SkinEntry(id, name, texId));
    }

    private static void loadSkin(Path path) {
        String fileName = path.getFileName().toString();
        String baseName = fileName.substring(0, fileName.length() - 4);
        String id = "skin:" + fileName;
        String safeSlug = baseName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_");
        Identifier texId = Identifier.fromNamespaceAndPath("ye", "skins/" + safeSlug);

        try (InputStream in = Files.newInputStream(path)) {
            NativeImage image = NativeImage.read(in);
            DynamicTexture texture = new DynamicTexture(() -> "ye_skin_" + safeSlug, image);
            Minecraft.getInstance().getTextureManager().register(texId, texture);
            SKINS.put(id, new SkinEntry(id, baseName, texId));
        } catch (Exception e) {
            LOGGER.warn("Failed to load custom skin: {}", path, e);
        }
    }

    public static List<SkinEntry> getAllSkins() {
        return Collections.unmodifiableList(new ArrayList<>(SKINS.values()));
    }

    public static SkinEntry getSkin(String id) {
        if (id == null || id.isEmpty()) {
            return SKINS.isEmpty() ? null : SKINS.values().iterator().next();
        }
        SkinEntry entry = SKINS.get(id);
        if (entry == null && !SKINS.isEmpty()) {
            return SKINS.values().iterator().next();
        }
        return entry;
    }
}
