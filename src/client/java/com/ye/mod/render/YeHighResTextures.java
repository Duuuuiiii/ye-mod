package com.ye.mod.render;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/**
 * Generates crisp 256x256 high-resolution procedural textures for:
 * - Intricate Magic-Array Soul Rings (Hexagram + Rune Glyphs + Flame Aura)
 * - Sword Runes
 */
public final class YeHighResTextures {
    public static final Identifier RING_ARRAY_TEX = Identifier.fromNamespaceAndPath("ye", "textures/hr_ring_array");
    public static final Identifier TRAIL_STREAM_TEX = Identifier.fromNamespaceAndPath("ye", "textures/hr_trail_stream");
    public static final Identifier SWORD_RUNE_TEX = Identifier.fromNamespaceAndPath("ye", "textures/hr_sword_rune");

    private static boolean initialized = false;

    private YeHighResTextures() {}

    public static void ensureInit() {
        if (initialized) return;
        initialized = true;

        Minecraft.getInstance().getTextureManager().register(RING_ARRAY_TEX, new DynamicTexture(() -> "ye_hr_ring", buildRingArrayTexture()));
        Minecraft.getInstance().getTextureManager().register(TRAIL_STREAM_TEX, new DynamicTexture(() -> "ye_hr_trail", buildTrailTexture()));
        Minecraft.getInstance().getTextureManager().register(SWORD_RUNE_TEX, new DynamicTexture(() -> "ye_hr_sword", buildSwordTexture()));
    }

    private static NativeImage buildRingArrayTexture() {
        int S = 256;
        NativeImage img = new NativeImage(S, S, true);
        double half = (S - 1) * 0.5;
        for (int py = 0; py < S; py++) {
            for (int px = 0; px < S; px++) {
                double dx = (px - half) / half;
                double dy = (py - half) / half;
                double r = Math.hypot(dx, dy);
                double ang = Math.atan2(dy, dx);

                if (r > 0.99) {
                    img.setPixel(px, py, 0);
                    continue;
                }

                double intensity = 0.0;
                // Outer glowing magic ring + inner circle
                if (Math.abs(r - 0.88) < 0.032) intensity = Math.max(intensity, 1.0 - Math.abs(r - 0.88) / 0.032);
                if (Math.abs(r - 0.72) < 0.022) intensity = Math.max(intensity, 1.0 - Math.abs(r - 0.72) / 0.022);
                if (Math.abs(r - 0.48) < 0.018) intensity = Math.max(intensity, 0.85 * (1.0 - Math.abs(r - 0.48) / 0.018));

                // 12 Rune segments between r=0.73 and r=0.87
                if (r > 0.74 && r < 0.86) {
                    double seg = ((ang + Math.PI) / (2.0 * Math.PI)) * 12.0;
                    double frac = seg - Math.floor(seg);
                    if (frac > 0.18 && frac < 0.82 && ((int) (r * 40) % 2 == 0 || Math.abs(frac - 0.5) < 0.08)) {
                        intensity = Math.max(intensity, 0.92);
                    }
                }

                // Hexagram (2 overlapping triangles) inside r <= 0.71
                if (r <= 0.71 && r >= 0.34) {
                    for (int t = 0; t < 6; t++) {
                        double a1 = t * (Math.PI / 3.0);
                        double a2 = a1 + (2.0 * Math.PI / 3.0);
                        double dLine = distToSegment(dx, dy, Math.cos(a1) * 0.70, Math.sin(a1) * 0.70,
                                Math.cos(a2) * 0.70, Math.sin(a2) * 0.70);
                        if (dLine < 0.018) {
                            intensity = Math.max(intensity, 1.0 - dLine / 0.018);
                        }
                    }
                }

                // Soft outer petal flame aura
                double flame = 0.92 + 0.06 * Math.sin(ang * 8.0);
                if (r > 0.86 && r < flame) {
                    intensity = Math.max(intensity, 0.42 * (1.0 - (r - 0.86) / 0.14));
                }

                int a = Math.max(0, Math.min(255, (int) Math.round(intensity * 255)));
                img.setPixel(px, py, (a << 24) | 0x00FFFFFF);
            }
        }
        return img;
    }

    private static NativeImage buildTrailTexture() {
        int S = 128;
        NativeImage img = new NativeImage(S, S, true);
        for (int y = 0; y < S; y++) {
            double v = (y / (double) (S - 1)) * 2.0 - 1.0; // -1..1 across ribbon width
            for (int x = 0; x < S; x++) {
                double u = x / (double) (S - 1);
                // 3 intertwined fine glowing streams + soft core glow
                double s1 = Math.exp(-Math.pow((v - 0.35 * Math.sin(u * Math.PI * 2.0)) * 5.5, 2));
                double s2 = Math.exp(-Math.pow((v + 0.35 * Math.sin(u * Math.PI * 2.0 + 1.2)) * 5.5, 2));
                double core = Math.exp(-Math.pow(v * 2.4, 2)) * 0.65;
                double val = Math.min(1.0, s1 + s2 + core);
                int a = (int) Math.round(val * 240);
                int c = (int) Math.round(180 + 75 * val);
                img.setPixel(x, y, argb(a, c, c, 255));
            }
        }
        return img;
    }

    private static NativeImage buildSwordTexture() {
        int S = 128;
        NativeImage img = new NativeImage(S, S, true);
        for (int y = 0; y < S; y++) {
            for (int x = 0; x < S; x++) {
                int dist = Math.abs(x - 64);
                boolean rune = dist < 8 && ((y % 16) < 6);
                int c = rune ? 255 : (dist < 18 ? 225 : 175);
                img.setPixel(x, y, argb(255, c, c, 255));
            }
        }
        return img;
    }

    private static double distToSegment(double px, double py, double x1, double y1, double x2, double y2) {
        double dx = x2 - x1, dy = y2 - y1;
        double lenSq = dx * dx + dy * dy;
        double t = lenSq == 0 ? 0 : Math.max(0, Math.min(1, ((px - x1) * dx + (py - y1) * dy) / lenSq));
        return Math.hypot(px - (x1 + t * dx), py - (y1 + t * dy));
    }

    private static int argb(int a, int r, int g, int b) {
        return ((a & 0xFF) << 24) | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
    }
}
