package com.ye.mod.render;

import com.mojang.blaze3d.platform.NativeImage;
import com.ye.mod.modules.render.DragonWingsModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.util.EnumMap;
import java.util.Map;

/**
 * Generates crisp 256x256 high-resolution procedural textures for:
 * - 6 Wing Styles (Seraph Gold, Demon Crimson, Ice Crystal, Cyber Neon, Fairy Prism, Ender Dragon)
 * - Intricate Magic-Array Soul Rings (Hexagram + Rune Glyphs + Flame Aura)
 * - Multi-strand Energy Trail Streams
 */
public final class YeHighResTextures {
    private static final Map<DragonWingsModule.WingStyle, Identifier> WING_TEX = new EnumMap<>(DragonWingsModule.WingStyle.class);
    public static final Identifier RING_ARRAY_TEX = Identifier.fromNamespaceAndPath("ye", "textures/hr_ring_array");
    public static final Identifier TRAIL_STREAM_TEX = Identifier.fromNamespaceAndPath("ye", "textures/hr_trail_stream");
    public static final Identifier SWORD_RUNE_TEX = Identifier.fromNamespaceAndPath("ye", "textures/hr_sword_rune");

    private static boolean initialized = false;

    private YeHighResTextures() {}

    public static void ensureInit() {
        if (initialized) return;
        initialized = true;

        for (DragonWingsModule.WingStyle style : DragonWingsModule.WingStyle.values()) {
            Identifier id = Identifier.fromNamespaceAndPath("ye", "textures/hr_wing_" + style.name().toLowerCase());
            NativeImage img = buildWingTexture(style);
            Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "ye_hr_" + style.name(), img));
            WING_TEX.put(style, id);
        }

        Minecraft.getInstance().getTextureManager().register(RING_ARRAY_TEX, new DynamicTexture(() -> "ye_hr_ring", buildRingArrayTexture()));
        Minecraft.getInstance().getTextureManager().register(TRAIL_STREAM_TEX, new DynamicTexture(() -> "ye_hr_trail", buildTrailTexture()));
        Minecraft.getInstance().getTextureManager().register(SWORD_RUNE_TEX, new DynamicTexture(() -> "ye_hr_sword", buildSwordTexture()));
    }

    public static Identifier getWingTexture(DragonWingsModule.WingStyle style) {
        ensureInit();
        return WING_TEX.getOrDefault(style, WING_TEX.get(DragonWingsModule.WingStyle.SERAPH_GOLD));
    }

    private static NativeImage buildWingTexture(DragonWingsModule.WingStyle style) {
        int S = 256;
        NativeImage img = new NativeImage(S, S, true);

        for (int py = 0; py < S; py++) {
            double v = py / (double) (S - 1); // 0 = top, 1 = bottom
            for (int px = 0; px < S; px++) {
                double u = px / (double) (S - 1); // 0 = inner spine, 1 = outer tip

                // Wing silhouette envelope (swept upward/outward curve + scalloped feather/barb lower edge)
                double topEdge = 0.28 - 0.22 * Math.pow(u, 0.75) + 0.08 * Math.sin(u * Math.PI);
                double scallop = 0.06 * Math.abs(Math.sin(u * Math.PI * 5.0));
                double bottomEdge = 0.55 + 0.36 * (1.0 - Math.pow(u - 0.15, 2)) - scallop;
                if (style == DragonWingsModule.WingStyle.FAIRY_PRISM) {
                    topEdge = 0.12 + 0.14 * Math.pow(u - 0.4, 2);
                    bottomEdge = 0.88 - 0.25 * u - 0.08 * Math.sin(u * Math.PI * 3.0);
                } else if (style == DragonWingsModule.WingStyle.ICE_CRYSTAL) {
                    double spike = 0.08 * ((int) (u * 12) % 2 == 0 ? 1 : -1) * u;
                    topEdge = 0.22 - 0.18 * u + spike * 0.4;
                    bottomEdge = 0.72 - 0.22 * u + spike;
                }

                if (v < topEdge || v > bottomEdge || u < 0.02 || u > 0.98) {
                    img.setPixel(px, py, 0);
                    continue;
                }

                double relV = (v - topEdge) / Math.max(0.01, bottomEdge - topEdge);
                int argb = switch (style) {
                    case SERAPH_GOLD -> paintSeraph(u, relV);
                    case DEMON_CRIMSON -> paintDemon(u, relV);
                    case ICE_CRYSTAL -> paintIceCrystal(u, relV);
                    case CYBER_NEON -> paintCyber(u, relV);
                    case FAIRY_PRISM -> paintFairy(u, relV);
                    case ENDER_DRAGON -> paintEnder(u, relV);
                };

                // Soft anti-aliased silhouette edge
                double edgeDist = Math.min(v - topEdge, bottomEdge - v);
                if (edgeDist < 0.022) {
                    int a = (argb >>> 24) & 0xFF;
                    a = (int) Math.round(a * (edgeDist / 0.022));
                    argb = (a << 24) | (argb & 0x00FFFFFF);
                }
                img.setPixel(px, py, argb);
            }
        }
        return img;
    }

    private static int paintSeraph(double u, double rv) {
        double featherPhase = (u * 10.0 + rv * 0.8) % 1.0;
        boolean shaft = Math.abs(featherPhase - 0.5) < 0.07;
        boolean goldTrim = rv < 0.14 || u < 0.12;
        if (goldTrim) {
            int g = 210 + (int) (40 * Math.sin(u * 20));
            return argb(250, 255, Math.min(255, g), 110);
        }
        int base = shaft ? 255 : (225 + (int) (25 * (1.0 - rv)));
        int b = shaft ? 215 : Math.min(255, base + 5);
        return argb(245, base, base, b);
    }

    private static int paintDemon(double u, double rv) {
        boolean rib = (rv < 0.12) || (Math.abs((u * 4.5 - rv * 0.4) % 1.0 - 0.5) < 0.05);
        if (rib) {
            return argb(255, 32, 24, 34);
        }
        int r = (int) (135 + 110 * (1.0 - rv));
        int g = (int) (18 + 30 * (1.0 - rv));
        return argb(235, r, g, 28);
    }

    private static int paintIceCrystal(double u, double rv) {
        int cell = ((int) (u * 14) + (int) (rv * 10)) % 3;
        boolean edge = ((u * 14) % 1.0 < 0.08) || ((rv * 10) % 1.0 < 0.08);
        if (edge) return argb(250, 220, 248, 255);
        return switch (cell) {
            case 0 -> argb(230, 56, 189, 248);
            case 1 -> argb(235, 37, 99, 235);
            default -> argb(225, 103, 232, 249);
        };
    }

    private static int paintCyber(double u, double rv) {
        boolean frame = rv < 0.15 || u < 0.10;
        boolean circuit = ((int) (u * 24) % 4 == 0 && rv > 0.25 && rv < 0.75) || (Math.abs(rv - 0.5) < 0.04);
        if (frame) return argb(250, 28, 34, 48);
        if (circuit) return argb(255, 56, 225, 255);
        return argb(200, 15, 35, 68);
    }

    private static int paintFairy(double u, double rv) {
        boolean vein = (rv < 0.08) || (Math.abs(Math.sin(u * 14 + rv * 6)) < 0.12);
        if (vein) return argb(245, 255, 235, 250);
        int r = (int) (245 - 65 * u);
        int g = (int) (130 + 95 * rv);
        int b = (int) (220 + 35 * u);
        return argb(210, r, g, b);
    }

    private static int paintEnder(double u, double rv) {
        boolean bone = (rv < 0.13) || (Math.abs((u * 4.0) % 1.0) < 0.06);
        if (bone) return argb(255, 26, 22, 34);
        int r = (int) (75 + 85 * (1.0 - rv));
        int g = (int) (25 + 25 * (1.0 - rv));
        int b = (int) (135 + 100 * (1.0 - rv));
        return argb(235, r, g, b);
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
