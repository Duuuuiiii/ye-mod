package com.ye.mod.render;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/**
 * Generates ultra-crisp 512x512 & 256x256 anti-aliased procedural textures for:
 * - 512x512 Gargantua Black Hole Accretion Disk & Lensing Halo
 * - 512x512 Multi-layered Runic Soul Rings
 * - 256x256 Ground Decals (Lava Fissure, Cyber Matrix, Void Astrolabe, Starlight Sigil)
 * - 256x256 Layered Hit Shockwave & Anamorphic Star Flare
 * - 128x128 Ethereal Ascending Soul Flame
 */
public final class YeHighResTextures {
    public static final Identifier RING_ARRAY_TEX = Identifier.fromNamespaceAndPath("ye", "textures/hr_ring_array_512");
    public static final Identifier BLACK_HOLE_DISK_TEX = Identifier.fromNamespaceAndPath("ye", "textures/hr_black_hole_disk");
    public static final Identifier BLACK_HOLE_CORE_TEX = Identifier.fromNamespaceAndPath("ye", "textures/hr_black_hole_core");
    public static final Identifier STEP_LAVA_TEX = Identifier.fromNamespaceAndPath("ye", "textures/hr_step_lava");
    public static final Identifier STEP_CYBER_TEX = Identifier.fromNamespaceAndPath("ye", "textures/hr_step_cyber");
    public static final Identifier STEP_VOID_TEX = Identifier.fromNamespaceAndPath("ye", "textures/hr_step_void");
    public static final Identifier STEP_STAR_TEX = Identifier.fromNamespaceAndPath("ye", "textures/hr_step_star");
    public static final Identifier HIT_SHOCKWAVE_TEX = Identifier.fromNamespaceAndPath("ye", "textures/hr_hit_shockwave");
    public static final Identifier SOUL_WISP_TEX = Identifier.fromNamespaceAndPath("ye", "textures/hr_soul_wisp");
    public static final Identifier SWORD_RUNE_TEX = Identifier.fromNamespaceAndPath("ye", "textures/hr_sword_rune");

    private static boolean initialized = false;

    private YeHighResTextures() {}

    public static void ensureInit() {
        if (initialized) return;
        initialized = true;
        var tm = Minecraft.getInstance().getTextureManager();
        tm.register(RING_ARRAY_TEX, new DynamicTexture(() -> "ye_hr_ring_512", buildRingArray512()));
        tm.register(BLACK_HOLE_DISK_TEX, new DynamicTexture(() -> "ye_hr_bh_disk", buildBlackHoleDisk512()));
        tm.register(BLACK_HOLE_CORE_TEX, new DynamicTexture(() -> "ye_hr_bh_core", buildBlackHoleCore512()));
        tm.register(STEP_LAVA_TEX, new DynamicTexture(() -> "ye_hr_step_lava", buildStepLava256()));
        tm.register(STEP_CYBER_TEX, new DynamicTexture(() -> "ye_hr_step_cyber", buildStepCyber256()));
        tm.register(STEP_VOID_TEX, new DynamicTexture(() -> "ye_hr_step_void", buildStepVoid256()));
        tm.register(STEP_STAR_TEX, new DynamicTexture(() -> "ye_hr_step_star", buildStepStar256()));
        tm.register(HIT_SHOCKWAVE_TEX, new DynamicTexture(() -> "ye_hr_hit_wave", buildHitShockwave256()));
        tm.register(SOUL_WISP_TEX, new DynamicTexture(() -> "ye_hr_soul_wisp", buildSoulWisp128()));
        tm.register(SWORD_RUNE_TEX, new DynamicTexture(() -> "ye_hr_sword", buildSwordTexture()));
    }

    private static NativeImage buildRingArray512() {
        int S = 1024;
        NativeImage img = new NativeImage(S, S, true);
        double half = (S - 1) * 0.5;
        for (int py = 0; py < S; py++) {
            for (int px = 0; px < S; px++) {
                double dx = (px - half) / half;
                double dy = (py - half) / half;
                double r = Math.hypot(dx, dy);
                if (r > 0.996) {
                    img.setPixel(px, py, 0);
                    continue;
                }
                double ang = Math.atan2(dy, dx);
                double val = 0.0;

                // Razor-sharp 1024x1024 multi-tiered geometric rings + luminous aura
                val = Math.max(val, ringGlow(r, 0.91, 0.009, 0.038));
                val = Math.max(val, ringGlow(r, 0.855, 0.005, 0.020) * 0.92);
                val = Math.max(val, ringGlow(r, 0.715, 0.007, 0.026) * 0.96);
                val = Math.max(val, ringGlow(r, 0.56, 0.005, 0.018) * 0.85);

                // 36 crisp high-resolution celestial rune glyphs between r=0.73 and r=0.845
                if (r > 0.73 && r < 0.845) {
                    double seg = ((ang + Math.PI) / (2.0 * Math.PI)) * 36.0;
                    int idx = (int) Math.floor(seg);
                    double u = seg - idx;
                    double v = (r - 0.73) / 0.115;
                    if (u > 0.14 && u < 0.86 && v > 0.10 && v < 0.90) {
                        double d1 = Math.abs(u - 0.5);
                        double d2 = Math.abs(v - ((idx % 2 == 0) ? 0.30 : 0.70));
                        double d3 = Math.abs((u - 0.22) - v * 0.56);
                        double stroke = Math.min(d1, Math.min(d2, (idx % 3 == 0) ? d3 : 1.0));
                        if (stroke < 0.065) {
                            val = Math.max(val, Math.min(1.0, (0.065 - stroke) / 0.025));
                        }
                    }
                }

                // Smooth anti-aliased 12-pointed star filigree inside r <= 0.715
                if (r >= 0.42 && r <= 0.718) {
                    for (int k = 0; k < 12; k++) {
                        double a1 = k * (Math.PI / 6.0);
                        double a2 = a1 + (5.0 * Math.PI / 6.0);
                        double d = distToSegment(dx, dy, Math.cos(a1) * 0.712, Math.sin(a1) * 0.712,
                                Math.cos(a2) * 0.712, Math.sin(a2) * 0.712);
                        if (d < 0.010) {
                            val = Math.max(val, (1.0 - d / 0.010) * 0.94);
                        }
                    }
                }

                // Crown-like spirit flame crests outside main ring
                double wave = 0.91 + 0.058 * Math.sin(ang * 16.0) + 0.022 * Math.cos(ang * 32.0);
                if (r > 0.89 && r < wave) {
                    val = Math.max(val, 0.62 * (1.0 - (r - 0.89) / 0.095));
                }

                int a = clamp255(val * 255.0);
                int bright = clamp255(215 + val * 40.0);
                img.setPixel(px, py, argb(a, bright, bright, 255));
            }
        }
        return img;
    }

    private static NativeImage buildBlackHoleCore512() {
        int S = 512;
        NativeImage img = new NativeImage(S, S, true);
        double half = (S - 1) * 0.5;
        for (int py = 0; py < S; py++) {
            for (int px = 0; px < S; px++) {
                double dx = (px - half) / half;
                double dy = (py - half) / half;
                double r = Math.hypot(dx, dy);
                if (r > 0.98) {
                    img.setPixel(px, py, 0);
                    continue;
                }
                // Pitch-black shadow inside r < 0.34
                if (r < 0.335) {
                    img.setPixel(px, py, 0xFF020205);
                    continue;
                }
                // Razor-sharp relativistic photon ring + Einstein gravitational lensing halo
                double photon = Math.exp(-Math.pow((r - 0.352) / 0.016, 2));
                double halo = Math.exp(-Math.pow((r - 0.39) / 0.085, 2)) * 0.72;
                double outerGlow = Math.exp(-Math.pow((r - 0.42) / 0.24, 2)) * 0.34;
                double glow = Math.min(1.0, photon + halo + outerGlow);
                // Smooth transition at shadow edge
                double edgeDark = r < 0.352 ? (0.352 - r) / 0.017 : 0.0;
                int a = clamp255(Math.max(edgeDark, glow) * 255.0);
                int lum = clamp255(glow * 255.0 * (1.0 - edgeDark * 0.85));
                img.setPixel(px, py, argb(a, lum, lum, lum));
            }
        }
        return img;
    }

    private static NativeImage buildBlackHoleDisk512() {
        int S = 512;
        NativeImage img = new NativeImage(S, S, true);
        double half = (S - 1) * 0.5;
        for (int py = 0; py < S; py++) {
            for (int px = 0; px < S; px++) {
                double dx = (px - half) / half;
                double dy = (py - half) / half;
                double r = Math.hypot(dx, dy);
                if (r < 0.26 || r > 0.99) {
                    img.setPixel(px, py, 0);
                    continue;
                }
                double ang = Math.atan2(dy, dx);
                // Logarithmic spiral plasma filaments in accretion disk
                double spiral1 = Math.sin(ang * 5.0 - Math.log(r + 0.05) * 18.0);
                double spiral2 = Math.cos(ang * 9.0 - Math.log(r + 0.05) * 28.0);
                double rings = 0.65 + 0.35 * Math.sin(r * 46.0 + spiral1 * 1.4);
                double innerBoost = Math.exp(-Math.pow((r - 0.34) / 0.14, 2)) * 0.65;
                double envelope = Math.sin(Math.PI * Math.min(1.0, (r - 0.26) / 0.73));
                envelope = Math.pow(Math.max(0.0, envelope), 0.75);

                double intensity = Math.min(1.0, envelope * (0.52 * rings + 0.24 * (spiral2 * 0.5 + 0.5)) + innerBoost);
                // Smooth inner cutout near event horizon
                if (r < 0.31) intensity *= (r - 0.26) / 0.05;

                int a = clamp255(intensity * 250.0);
                int c = clamp255(165 + intensity * 90.0);
                img.setPixel(px, py, argb(a, 255, c, clamp255(c * 0.82)));
            }
        }
        return img;
    }

    private static NativeImage buildStepLava256() {
        int S = 256;
        NativeImage img = new NativeImage(S, S, true);
        double half = (S - 1) * 0.5;
        for (int py = 0; py < S; py++) {
            for (int px = 0; px < S; px++) {
                double dx = (px - half) / half, dy = (py - half) / half;
                double r = Math.hypot(dx, dy);
                if (r > 0.96) { img.setPixel(px, py, 0); continue; }
                double ang = Math.atan2(dy, dx);
                // Jagged volcanic cracks + glowing magma core
                double crack = 0.0;
                for (int k = 0; k < 7; k++) {
                    double targetAng = -Math.PI + k * (2.0 * Math.PI / 7.0) + 0.18 * Math.sin(r * 14.0 + k * 2.1);
                    double da = Math.abs(wrapAngle(ang - targetAng)) * r;
                    if (da < 0.038 && r < 0.86) {
                        crack = Math.max(crack, 1.0 - da / 0.038);
                    }
                }
                double core = Math.exp(-Math.pow(r / 0.28, 2));
                double rim = ringGlow(r, 0.72, 0.015, 0.05) * 0.65;
                double glow = Math.min(1.0, Math.max(core, Math.max(crack * (1.0 - r * 0.4), rim)));
                double crust = Math.max(0.0, (0.85 - r) * 0.45);

                int a = clamp255(Math.max(glow, crust) * 245.0);
                int red = clamp255(45 + glow * 210.0);
                int grn = clamp255(15 + Math.pow(glow, 1.6) * 215.0);
                int blu = clamp255(Math.pow(glow, 3.2) * 160.0);
                img.setPixel(px, py, argb(a, red, grn, blu));
            }
        }
        return img;
    }

    private static NativeImage buildStepCyber256() {
        int S = 256;
        NativeImage img = new NativeImage(S, S, true);
        double half = (S - 1) * 0.5;
        for (int py = 0; py < S; py++) {
            for (int px = 0; px < S; px++) {
                double dx = (px - half) / half, dy = (py - half) / half;
                double ax = Math.abs(dx), ay = Math.abs(dy);
                // Hexagonal distance metric
                double hex = Math.max(ax * 0.866025 + ay * 0.5, ay);
                if (hex > 0.94) { img.setPixel(px, py, 0); continue; }

                double v = 0.0;
                v = Math.max(v, ringGlow(hex, 0.84, 0.016, 0.04));
                v = Math.max(v, ringGlow(hex, 0.56, 0.012, 0.03) * 0.9);
                v = Math.max(v, ringGlow(hex, 0.26, 0.014, 0.03) * 0.95);
                // Circuit traces along 6 hex axes
                for (int k = 0; k < 6; k++) {
                    double a = k * (Math.PI / 3.0);
                    double d = distToSegment(dx, dy, Math.cos(a) * 0.26, Math.sin(a) * 0.26,
                            Math.cos(a) * 0.84, Math.sin(a) * 0.84);
                    if (d < 0.018) v = Math.max(v, (1.0 - d / 0.018) * 0.88);
                }
                int al = clamp255(v * 245.0);
                img.setPixel(px, py, argb(al, clamp255(60 + v * 195), clamp255(210 + v * 45), 255));
            }
        }
        return img;
    }

    private static NativeImage buildStepVoid256() {
        int S = 256;
        NativeImage img = new NativeImage(S, S, true);
        double half = (S - 1) * 0.5;
        for (int py = 0; py < S; py++) {
            for (int px = 0; px < S; px++) {
                double dx = (px - half) / half, dy = (py - half) / half;
                double r = Math.hypot(dx, dy);
                if (r > 0.96) { img.setPixel(px, py, 0); continue; }
                double ang = Math.atan2(dy, dx);
                double v = ringGlow(r, 0.86, 0.016, 0.045);
                v = Math.max(v, ringGlow(r, 0.62, 0.012, 0.035));
                // Swirling 4-arm void vortex + astrolabe arcs
                double swirl = Math.sin(ang * 4.0 + r * 8.0);
                if (r > 0.18 && r < 0.62 && swirl > 0.55) {
                    v = Math.max(v, (swirl - 0.55) / 0.45 * 0.85);
                }
                v = Math.max(v, Math.exp(-Math.pow(r / 0.16, 2)) * 0.95);
                int al = clamp255(v * 245.0);
                img.setPixel(px, py, argb(al, clamp255(180 + v * 75), clamp255(90 + v * 130), 255));
            }
        }
        return img;
    }

    private static NativeImage buildStepStar256() {
        int S = 256;
        NativeImage img = new NativeImage(S, S, true);
        double half = (S - 1) * 0.5;
        for (int py = 0; py < S; py++) {
            for (int px = 0; px < S; px++) {
                double dx = (px - half) / half, dy = (py - half) / half;
                double r = Math.hypot(dx, dy);
                if (r > 0.96) { img.setPixel(px, py, 0); continue; }
                double v = ringGlow(r, 0.82, 0.014, 0.04);
                // Crisp 4-pointed compass star + diagonal minor star
                double star4 = Math.exp(-Math.pow(Math.abs(dx) * 9.0, 1.4)) * Math.max(0.0, 1.0 - Math.abs(dy) * 1.1)
                        + Math.exp(-Math.pow(Math.abs(dy) * 9.0, 1.4)) * Math.max(0.0, 1.0 - Math.abs(dx) * 1.1);
                double rx = (dx + dy) * 0.7071, ry = (dx - dy) * 0.7071;
                double starDiag = (Math.exp(-Math.pow(Math.abs(rx) * 13.0, 1.4)) * Math.max(0.0, 0.7 - Math.abs(ry))
                        + Math.exp(-Math.pow(Math.abs(ry) * 13.0, 1.4)) * Math.max(0.0, 0.7 - Math.abs(rx))) * 0.75;
                v = Math.min(1.0, Math.max(v, star4 + starDiag));
                int al = clamp255(v * 250.0);
                img.setPixel(px, py, argb(al, 255, clamp255(225 + v * 30), clamp255(150 + v * 105)));
            }
        }
        return img;
    }

    private static NativeImage buildHitShockwave256() {
        int S = 256;
        NativeImage img = new NativeImage(S, S, true);
        double half = (S - 1) * 0.5;
        for (int py = 0; py < S; py++) {
            for (int px = 0; px < S; px++) {
                double dx = (px - half) / half, dy = (py - half) / half;
                double r = Math.hypot(dx, dy);
                if (r > 0.98) { img.setPixel(px, py, 0); continue; }
                // Outer crisp shockwave + inner echo wave + 4-ray star burst
                double wave1 = ringGlow(r, 0.82, 0.022, 0.075);
                double wave2 = ringGlow(r, 0.52, 0.015, 0.045) * 0.8;
                double flare = (Math.exp(-Math.abs(dx) * 14.0) * Math.max(0, 0.92 - Math.abs(dy))
                        + Math.exp(-Math.abs(dy) * 14.0) * Math.max(0, 0.92 - Math.abs(dx)));
                double core = Math.exp(-Math.pow(r / 0.16, 2));
                double v = Math.min(1.0, Math.max(Math.max(wave1, wave2), Math.min(1.0, flare + core)));
                int al = clamp255(v * 255.0);
                img.setPixel(px, py, argb(al, 255, 255, 255));
            }
        }
        return img;
    }

    private static NativeImage buildSoulWisp128() {
        int S = 128;
        NativeImage img = new NativeImage(S, S, true);
        double half = (S - 1) * 0.5;
        for (int py = 0; py < S; py++) {
            for (int px = 0; px < S; px++) {
                double dx = (px - half) / half;
                double dy = (py - half) / half; // -1 top, +1 bottom
                // Teardrop spirit flame: round glowing head near y=-0.15, tapered tail toward y=+0.95
                double widthAtY = dy < 0 ? 0.42 * Math.sqrt(Math.max(0, 1.0 - Math.pow((dy + 0.15) / 0.65, 2)))
                        : 0.42 * Math.max(0, 1.0 - dy / 0.95);
                double sway = dy > 0 ? 0.10 * Math.sin(dy * 5.0) * dy : 0.0;
                double dist = Math.abs(dx - sway) / Math.max(0.04, widthAtY);
                double v = (dy > -0.78 && dy < 0.95 && dist < 1.3) ? Math.exp(-dist * dist * 2.2) : 0.0;
                int al = clamp255(v * 235.0);
                img.setPixel(px, py, argb(al, clamp255(180 + v * 75), clamp255(230 + v * 25), 255));
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

    private static double ringGlow(double r, double target, double coreHalfWidth, double bloomWidth) {
        double d = Math.abs(r - target);
        if (d <= coreHalfWidth) return 1.0;
        if (d >= bloomWidth) return 0.0;
        double t = (d - coreHalfWidth) / (bloomWidth - coreHalfWidth);
        return Math.pow(1.0 - t, 2.0) * 0.72;
    }

    private static double wrapAngle(double a) {
        while (a > Math.PI) a -= Math.PI * 2.0;
        while (a < -Math.PI) a += Math.PI * 2.0;
        return a;
    }

    private static double distToSegment(double px, double py, double x1, double y1, double x2, double y2) {
        double dx = x2 - x1, dy = y2 - y1;
        double lenSq = dx * dx + dy * dy;
        double t = lenSq == 0 ? 0 : Math.max(0, Math.min(1, ((px - x1) * dx + (py - y1) * dy) / lenSq));
        return Math.hypot(px - (x1 + t * dx), py - (y1 + t * dy));
    }

    private static int clamp255(double v) {
        return Math.max(0, Math.min(255, (int) Math.round(v)));
    }

    private static int argb(int a, int r, int g, int b) {
        return ((a & 0xFF) << 24) | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
    }
}
