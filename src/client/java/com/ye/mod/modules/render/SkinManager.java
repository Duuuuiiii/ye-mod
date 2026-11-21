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
