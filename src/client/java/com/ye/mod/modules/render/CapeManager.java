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
 * Manages built-in preset capes and user-provided local PNG capes.
 */
public final class CapeManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("ye-capes");
    private static final Map<String, CapeEntry> CAPES = new LinkedHashMap<>();
    private static boolean presetsInitialized = false;

    public record CapeEntry(String id, String displayName, Identifier textureId, boolean custom) {}

    private CapeManager() {}

    public static void init() {
        ensurePresets();
        refreshCustomCapes();
    }

    private static void ensurePresets() {
        if (presetsInitialized) return;
        presetsInitialized = true;

        registerPreset("preset:3c3u", "3c3u 暗影", CapeManager::draw3c3uCape);
        registerPreset("preset:obsidian_gold", "黑曜鎏金", CapeManager::drawObsidianGoldCape);
        registerPreset("preset:cyber_neon", "赛博霓虹", CapeManager::drawCyberNeonCape);
        registerPreset("preset:crimson", "绯红星芒", CapeManager::drawCrimsonCape);
        registerPreset("preset:starlight", "星夜极简", CapeManager::drawStarlightCape);
    }

    public static void refreshCustomCapes() {
        ensurePresets();
        // Remove old custom entries
        CAPES.entrySet().removeIf(e -> e.getValue().custom());

        ConfigManager.ensureDirectories();
        Path capesDir = ConfigManager.getCapesDir();
        try (Stream<Path> files = Files.list(capesDir)) {
            files.filter(p -> Files.isRegularFile(p) && p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".png"))
                    .sorted()
                    .forEach(CapeManager::loadCustomCape);
        } catch (IOException e) {
            LOGGER.warn("Failed to scan capes directory", e);
        }
    }

    private static void loadCustomCape(Path path) {
        String fileName = path.getFileName().toString();
        String baseName = fileName.substring(0, fileName.length() - 4);
        String id = "custom:" + fileName;
        String safeSlug = baseName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_");
        Identifier texId = Identifier.fromNamespaceAndPath("ye", "capes/custom_" + safeSlug);

        try (InputStream in = Files.newInputStream(path)) {
            NativeImage image = NativeImage.read(in);
            DynamicTexture texture = new DynamicTexture(() -> "ye_cape_" + safeSlug, image);
            Minecraft.getInstance().getTextureManager().register(texId, texture);
            CAPES.put(id, new CapeEntry(id, baseName, texId, true));
        } catch (Exception e) {
            LOGGER.warn("Failed to load custom cape: {}", path, e);
        }
    }

    private static void registerPreset(String id, String displayName, CapePainter painter) {
        String slug = id.replace("preset:", "");
        Identifier texId = Identifier.fromNamespaceAndPath("ye", "capes/preset_" + slug);
        NativeImage img = new NativeImage(64, 32, true);
        // Fill base transparent/dark
        for (int y = 0; y < 32; y++) {
            for (int x = 0; x < 64; x++) {
                img.setPixel(x, y, 0xFF0E1117);
            }
        }
        painter.paint(img);
        DynamicTexture texture = new DynamicTexture(() -> "ye_cape_" + slug, img);
        Minecraft.getInstance().getTextureManager().register(texId, texture);
        CAPES.put(id, new CapeEntry(id, displayName, texId, false));
    }

    public static List<CapeEntry> getAllCapes() {
        ensurePresets();
        return Collections.unmodifiableList(new ArrayList<>(CAPES.values()));
    }

    public static CapeEntry getCape(String id) {
        ensurePresets();
        CapeEntry entry = CAPES.get(id);
        if (entry == null && !CAPES.isEmpty()) {
            return CAPES.values().iterator().next();
        }
        return entry;
    }

    @FunctionalInterface
    private interface CapePainter {
        void paint(NativeImage img);
    }

    // Minecraft cape UV: Front face is x=[1..10], y=[1..16]; Back face is x=[12..21], y=[1..16]
    private static void fillBothFaces(NativeImage img, PixelShader shader) {
        // Front face (1..10, 1..16) -> 10x16
        for (int dy = 0; dy < 16; dy++) {
            for (int dx = 0; dx < 10; dx++) {
                int color = shader.color(dx, dy);
                img.setPixel(1 + dx, 1 + dy, color);
                img.setPixel(12 + dx, 1 + dy, color);
            }
        }
        // Edges (top, bottom, left, right)
        for (int dx = 0; dx < 10; dx++) {
            img.setPixel(1 + dx, 0, shader.color(dx, 0));
            img.setPixel(11 + dx, 0, shader.color(dx, 15));
        }
        for (int dy = 0; dy < 16; dy++) {
            img.setPixel(0, 1 + dy, shader.color(0, dy));
            img.setPixel(11, 1 + dy, shader.color(9, dy));
        }
    }

    @FunctionalInterface
    private interface PixelShader {
        int color(int x, int y);
    }

    private static void draw3c3uCape(NativeImage img) {
        fillBothFaces(img, (x, y) -> {
            int base = lerpColor(0xFF0F131F, 0xFF1A1F35, y / 15f);
            // Subtle border
            if (x == 0 || x == 9 || y == 0 || y == 15) {
                return lerpColor(0xFF38BDF8, 0xFF818CF8, y / 15f);
            }
            // Minimalist diamond emblem in center (x=4..5, y=5..10)
            int dist = Math.abs(x * 2 - 9) + Math.abs(y - 7);
            if (dist <= 3) {
                return dist == 3 ? 0xFF38BDF8 : 0xFFE0F2FE;
            }
            return base;
        });
    }

    private static void drawObsidianGoldCape(NativeImage img) {
        fillBothFaces(img, (x, y) -> {
            if (x == 0 || x == 9 || y == 0 || y == 15) {
                return 0xFFFBBF24;
            }
            if ((x == 4 || x == 5) && y >= 4 && y <= 11) {
                return 0xFFFDE68A;
            }
            if (y == 7 && x >= 2 && x <= 7) {
                return 0xFFF59E0B;
            }
            return ((x + y) % 3 == 0) ? 0xFF1E1B2E : 0xFF13111C;
        });
    }

    private static void drawCyberNeonCape(NativeImage img) {
        fillBothFaces(img, (x, y) -> {
            if (Math.abs((x * 1.5f) - y + 2) < 1.4f) {
                return lerpColor(0xFF22D3EE, 0xFFF472B6, y / 15f);
            }
            if (y == 0 || y == 15) {
                return 0xFF22D3EE;
            }
            return lerpColor(0xFF090D16, 0xFF161B2E, y / 15f);
        });
    }

    private static void drawCrimsonCape(NativeImage img) {
        fillBothFaces(img, (x, y) -> {
            if (x == 0 || x == 9 || y == 0 || y == 15) {
                return 0xFFEF4444;
            }
            if ((x == 4 || x == 5) && (y == 6 || y == 7)) {
                return 0xFFFEF2F2;
            }
            if ((x == 3 || x == 6) && (y == 5 || y == 8)) {
                return 0xFFFCA5A5;
            }
            return lerpColor(0xFF2A0A12, 0xFF5C101E, y / 15f);
        });
    }

    private static void drawStarlightCape(NativeImage img) {
        fillBothFaces(img, (x, y) -> {
            if ((x == 2 && y == 3) || (x == 7 && y == 5) || (x == 4 && y == 10) || (x == 6 && y == 13)) {
                return 0xFFF8FAFC;
            }
            if ((x == 5 && y == 4) || (x == 3 && y == 8)) {
                return 0xFF93C5FD;
            }
            return lerpColor(0xFF0B1026, 0xFF1E293B, y / 15f);
        });
    }

    private static int lerpColor(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int aa = (a >>> 24) & 0xFF, ar = (a >>> 16) & 0xFF, ag = (a >>> 8) & 0xFF, ab = a & 0xFF;
        int ba = (b >>> 24) & 0xFF, br = (b >>> 16) & 0xFF, bg = (b >>> 8) & 0xFF, bb = b & 0xFF;
        int oa = (int) (aa + (ba - aa) * t);
        int or = (int) (ar + (br - ar) * t);
        int og = (int) (ag + (bg - ag) * t);
        int ob = (int) (ab + (bb - ab) * t);
        return (oa << 24) | (or << 16) | (og << 8) | ob;
    }
}
