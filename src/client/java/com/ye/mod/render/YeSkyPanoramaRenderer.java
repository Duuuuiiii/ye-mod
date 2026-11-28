package com.ye.mod.render;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.ye.mod.core.ConfigManager;
import com.ye.mod.modules.visual.AmbienceModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;
import java.util.stream.Stream;

/**
 * 360-Degree Seamless Equirectangular Panorama Sky Dome Renderer (1024x512 painted atmospheric art + user custom PNG/JPG).
 * Renders a smooth 40x20 tessellated spherical sky dome with zero cube seams and zero particle pixelation!
 */
public final class YeSkyPanoramaRenderer {
    private static final Map<AmbienceModule.SkyStyle, Identifier> PANO_TEXTURES = new EnumMap<>(AmbienceModule.SkyStyle.class);
    private static Identifier customSkyId = null;
    private static long lastCustomCheckMs = 0L;
    private static String loadedCustomFile = "";

    private YeSkyPanoramaRenderer() {}

    public static Identifier getOrBuildPanorama(AmbienceModule.SkyStyle style) {
        if (style == AmbienceModule.SkyStyle.GRADIENT_ONLY) return null;
        if (style == AmbienceModule.SkyStyle.CUSTOM_IMAGE) {
            return getOrLoadCustomPanorama();
        }
        return PANO_TEXTURES.computeIfAbsent(style, YeSkyPanoramaRenderer::generatePanorama);
    }

    public static void reloadCustomSky() {
        loadedCustomFile = "";
        lastCustomCheckMs = 0L;
    }

    private static Identifier getOrLoadCustomPanorama() {
        long now = System.currentTimeMillis();
        if (customSkyId != null && now - lastCustomCheckMs < 5000L) {
            return customSkyId;
        }
        lastCustomCheckMs = now;
        Path dir = ConfigManager.getSkiesDir();
        if (!Files.exists(dir)) return getOrBuildPanorama(AmbienceModule.SkyStyle.GALAXY_NEBULA);

        try (Stream<Path> s = Files.list(dir)) {
            Path first = s.filter(Files::isRegularFile)
                    .filter(p -> {
                        String n = p.getFileName().toString().toLowerCase();
                        return n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg");
                    })
                    .findFirst().orElse(null);
            if (first == null) {
                return getOrBuildPanorama(AmbienceModule.SkyStyle.GALAXY_NEBULA);
            }
            String fn = first.getFileName().toString() + "_" + Files.getLastModifiedTime(first).toMillis();
            if (fn.equals(loadedCustomFile) && customSkyId != null) {
                return customSkyId;
            }
            try (InputStream in = Files.newInputStream(first)) {
                BufferedImage bi = ImageIO.read(in);
                if (bi != null) {
                    int w = Math.min(2048, bi.getWidth());
                    int h = Math.min(1024, bi.getHeight());
                    NativeImage img = new NativeImage(w, h, true);
                    for (int y = 0; y < h; y++) {
                        int sy = (y * bi.getHeight()) / h;
                        for (int x = 0; x < w; x++) {
                            int sx = (x * bi.getWidth()) / w;
                            int argb = bi.getRGB(sx, sy);
                            img.setPixel(x, y, 0xFF000000 | (argb & 0x00FFFFFF));
                        }
                    }
                    Identifier id = Identifier.fromNamespaceAndPath("ye", "sky_custom_" + Math.abs(fn.hashCode()));
                    Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "ye_custom_sky", img));
                    customSkyId = id;
                    loadedCustomFile = fn;
                    return id;
                }
            }
        } catch (Exception ignored) {}
        return getOrBuildPanorama(AmbienceModule.SkyStyle.GALAXY_NEBULA);
    }

    private static Identifier generatePanorama(AmbienceModule.SkyStyle style) {
        final int W = 1024;
        final int H = 512;
        NativeImage img = new NativeImage(W, H, true);

        for (int py = 0; py < H; py++) {
            // v=0 -> zenith (+Y), v=0.5 -> horizon (Y=0), v=1 -> nadir (-Y)
            double v = (py + 0.5) / H;
            double theta = v * Math.PI;
            double sinT = Math.sin(theta);
            double dy = Math.cos(theta); // +1 top, 0 horizon, -1 bottom

            for (int px = 0; px < W; px++) {
                double u = (px + 0.5) / W;
                double phi = u * Math.PI * 2.0;
                double dx = sinT * Math.cos(phi);
                double dz = sinT * Math.sin(phi);

                int rgb = switch (style) {
                    case GALAXY_NEBULA -> paintGalaxyNebula(dx, dy, dz);
                    case AURORA_STARRY -> paintAuroraSky(dx, dy, dz);
                    case SAKURA_CLOUDS -> paintSakuraClouds(dx, dy, dz);
                    case AZURE_CUMULUS -> paintAzureCumulus(dx, dy, dz);
                    default -> 0x1E293B;
                };
                img.setPixel(px, py, 0xFF000000 | rgb);
            }
        }

        // Sprinkle crisp anti-aliased multi-size stars on night/twilight panoramas
        if (style == AmbienceModule.SkyStyle.GALAXY_NEBULA
                || style == AmbienceModule.SkyStyle.AURORA_STARRY
                || style == AmbienceModule.SkyStyle.SAKURA_CLOUDS) {
            int starCount = (style == AmbienceModule.SkyStyle.SAKURA_CLOUDS) ? 550 : 2200;
            long seed = 0x5EE1234L + style.ordinal() * 9973L;
            for (int i = 0; i < starCount; i++) {
                seed = seed * 6364136223846793005L + 1442695040888963407L;
                int sx = (int) ((seed >>> 32) & (W - 1));
                seed = seed * 6364136223846793005L + 1442695040888963407L;
                int sy = (int) ((seed >>> 33) % (int) (H * 0.58));
                if (style == AmbienceModule.SkyStyle.SAKURA_CLOUDS && sy > H * 0.34) continue;
                float fadeH = 1.0f - (sy / (H * 0.58f));
                float mag = (((seed >>> 16) & 0xFF) / 255.0f) * fadeH;
                if (mag < 0.12f) continue;

                int tintType = (int) (seed & 3);
                int sr = tintType == 0 ? 255 : (tintType == 1 ? 195 : 255);
                int sg = tintType == 0 ? 245 : (tintType == 1 ? 228 : 205);
                int sb = tintType == 0 ? 255 : (tintType == 1 ? 255 : 235);

                float rad = mag > 0.78f ? 1.8f : 1.0f;
                int ir = (int) Math.ceil(rad);
                for (int oy = -ir; oy <= ir; oy++) {
                    int yy = Math.max(0, Math.min(H - 1, sy + oy));
                    for (int ox = -ir; ox <= ir; ox++) {
                        int xx = (sx + ox + W) & (W - 1);
                        double d = Math.hypot(ox, oy);
                        if (d > rad) continue;
                        float str = (float) Math.pow(1.0 - d / (rad + 0.25), 1.8) * mag;
                        int base = img.getPixel(xx, yy);
                        int br = Math.min(255, ((base >> 16) & 0xFF) + Math.round(sr * str));
                        int bg = Math.min(255, ((base >> 8) & 0xFF) + Math.round(sg * str));
                        int bb = Math.min(255, (base & 0xFF) + Math.round(sb * str));
                        img.setPixel(xx, yy, 0xFF000000 | (br << 16) | (bg << 8) | bb);
                    }
                }
            }
        }

        Identifier id = Identifier.fromNamespaceAndPath("ye", "sky_pano_" + style.name().toLowerCase());
        Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "ye_sky_" + style.name(), img));
        return id;
    }

    private static int paintGalaxyNebula(double dx, double dy, double dz) {
        double h = Math.max(-0.25, dy);
        double horizonGlow = Math.exp(-Math.pow(Math.abs(dy + 0.02) / 0.22, 2.0));
        double r = 24 + horizonGlow * 68;
        double g = 28 + horizonGlow * 76;
        double b = 56 + horizonGlow * 124;

        // Tilted Milky Way galactic plane
        double plane = dx * 0.52 + dy * 0.64 - dz * 0.56;
        double band = Math.exp(-Math.pow(plane / 0.28, 2.0)) * Math.max(0.0, (h + 0.25) / 1.25);
        double n1 = fbm3(dx * 3.2, dy * 3.2, dz * 3.2);
        double n2 = fbm3(dx * 6.8 + 4.1, dy * 6.8 - 2.3, dz * 6.8 + 1.7);
        double dust = smoothstep(0.38, 0.72, n2);

        double nebViolet = band * (0.45 + 0.85 * n1) * (1.0 - dust * 0.58);
        double nebCyan = Math.exp(-Math.pow((plane - 0.10) / 0.20, 2.0)) * smoothstep(0.42, 0.85, n1) * 0.85;
        double core = Math.exp(-((dx - 0.55) * (dx - 0.55) + (dy - 0.35) * (dy - 0.35) + (dz + 0.62) * (dz + 0.62)) * 2.8) * (1.0 - dust * 0.45);

        r += nebViolet * 155 + nebCyan * 35 + core * 235;
        g += nebViolet * 58 + nebCyan * 175 + core * 168;
        b += nebViolet * 210 + nebCyan * 235 + core * 195;
        return packRgb(r, g, b);
    }

    private static int paintAuroraSky(double dx, double dy, double dz) {
        double horizon = Math.exp(-Math.pow((dy + 0.03) / 0.26, 2.0));
        double r = 18 + horizon * 34;
        double g = 42 + horizon * 102;
        double b = 72 + horizon * 118;

        if (dy > -0.08) {
            double wave = Math.sin(dx * 4.5 + dz * 2.8 + fbm3(dx * 2.4, 0.5, dz * 2.4) * 3.5);
            double curtain = Math.exp(-Math.pow(wave / 0.32, 2.0));
            double vert = smoothstep(-0.04, 0.22, dy) * Math.exp(-Math.max(0.0, dy - 0.22) * 2.8);
            double streaks = 0.55 + 0.45 * Math.sin(Math.atan2(dz, dx) * 28.0 + fbm3(dx * 8.0, dy * 2.0, dz * 8.0) * 4.0);
            double aur = curtain * vert * streaks;

            double topPurp = smoothstep(0.24, 0.62, dy);
            r += aur * (22 + topPurp * 165);
            g += aur * (235 * (1.0 - topPurp * 0.55));
            b += aur * (165 + topPurp * 85);
        }
        return packRgb(r, g, b);
    }

    private static int paintSakuraClouds(double dx, double dy, double dz) {
        // Makoto Shinkai anime twilight gradient + glowing sunlit cumulus clouds
        double t = clamp01((dy + 0.15) / 1.15);
        double r = lerp(255, 48, Math.pow(t, 0.62));
        double g = lerp(138, 36, Math.pow(t, 0.72));
        double b = lerp(168, 118, Math.pow(t, 0.52));

        // Sun glow near dx=0.75, dy=0.10, dz=0.65
        double sunDot = clamp01(dx * 0.75 + dy * 0.12 + dz * 0.65);
        double sunGlow = Math.pow(sunDot, 12.0) * 0.85 + Math.pow(sunDot, 64.0) * 0.95;
        r += sunGlow * 140;
        g += sunGlow * 95;
        b += sunGlow * 45;

        // Volumetric cumulus cloud banks near horizon (-0.12 < dy < 0.48)
        if (dy > -0.22 && dy < 0.56) {
            double cn = fbm3(dx * 3.4 + 1.2, dy * 5.2, dz * 3.4 - 0.8);
            double detail = fbm3(dx * 8.5, dy * 10.0, dz * 8.5) * 0.35;
            double heightMask = smoothstep(-0.20, 0.04, dy) * (1.0 - smoothstep(0.18, 0.54, dy));
            double cloud = smoothstep(0.36, 0.68, (cn * 0.78 + detail) * (0.55 + heightMask * 0.75));
            if (cloud > 0.01) {
                double lit = smoothstep(-0.02, 0.28, dy) * 0.65 + Math.pow(sunDot, 4.0) * 0.55;
                double cr = lerp(118, 255, clamp01(lit));
                double cg = lerp(74, 212, clamp01(lit));
                double cb = lerp(128, 218, clamp01(lit));
                r = lerp(r, cr, cloud * 0.92);
                g = lerp(g, cg, cloud * 0.92);
                b = lerp(b, cb, cloud * 0.92);
            }
        }
        return packRgb(r, g, b);
    }

    private static int paintAzureCumulus(double dx, double dy, double dz) {
        double t = clamp01((dy + 0.12) / 1.12);
        double r = lerp(186, 28, Math.pow(t, 0.58));
        double g = lerp(228, 108, Math.pow(t, 0.64));
        double b = lerp(255, 228, Math.pow(t, 0.48));

        if (dy > -0.18 && dy < 0.65) {
            double cn = fbm3(dx * 3.0 - 2.1, dy * 4.6, dz * 3.0 + 1.5);
            double det = fbm3(dx * 7.8, dy * 9.2, dz * 7.8) * 0.32;
            double band = smoothstep(-0.16, 0.05, dy) * (1.0 - smoothstep(0.22, 0.62, dy));
            double cloud = smoothstep(0.38, 0.66, (cn * 0.80 + det) * (0.52 + band * 0.78));
            if (cloud > 0.01) {
                double shade = clamp01(0.58 + dy * 1.15 + det * 0.55);
                double cr = lerp(178, 255, shade);
                double cg = lerp(204, 255, shade);
                double cb = lerp(236, 255, shade);
                r = lerp(r, cr, cloud * 0.94);
                g = lerp(g, cg, cloud * 0.94);
                b = lerp(b, cb, cloud * 0.94);
            }
        }
        return packRgb(r, g, b);
    }

    /**
     * Renders the 360° spherical panorama sky dome strictly during SkyRenderer.renderSkyDisc (sky pass, before world terrain)!
     * Because the sky pass runs before terrain/chunks with depth write disabled, it NEVER clips distant terrain or turns the world into a sphere!
     */
    public static void renderSkyDomeInSkyPass(AmbienceModule amb) {
        Identifier tex = getOrBuildPanorama(amb.skyStyle.get());
        if (tex == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.renderBuffers() == null) return;

        float bri = Math.max(0.5f, amb.skyBrightness.get().floatValue());
        int cByte = Math.max(40, Math.min(255, Math.round(Math.min(1.0f, bri) * 255.0f)));
        int tint = 0xFF000000 | (cByte << 16) | (cByte << 8) | cByte;

        float rotDeg = (float) (((System.currentTimeMillis() / 1000.0) * amb.panoRotateSpeed.get()) % 360.0);
        final float radius = 88.0f;
        final int segU = 40;
        final int segV = 20;

        PoseStack ps = new PoseStack();
        ps.mulPose(Axis.YP.rotationDegrees(rotDeg));
        var buf = mc.renderBuffers().bufferSource();
        com.mojang.blaze3d.opengl.GlStateManager._depthMask(false);
        VertexConsumer vc = buf.getBuffer(RenderTypes.entityTranslucent(tex));
        PoseStack.Pose pose = ps.last();

        for (int iv = 0; iv < segV; iv++) {
            float v0 = iv / (float) segV;
            float v1 = (iv + 1) / (float) segV;
            double th0 = v0 * Math.PI;
            double th1 = v1 * Math.PI;
            float y0 = (float) (Math.cos(th0) * radius);
            float y1 = (float) (Math.cos(th1) * radius);
            float r0 = (float) (Math.sin(th0) * radius);
            float r1 = (float) (Math.sin(th1) * radius);

            for (int iu = 0; iu < segU; iu++) {
                float u0 = iu / (float) segU;
                float u1 = (iu + 1) / (float) segU;
                double ph0 = u0 * Math.PI * 2.0;
                double ph1 = u1 * Math.PI * 2.0;

                float x00 = (float) (r0 * Math.cos(ph0)), z00 = (float) (r0 * Math.sin(ph0));
                float x10 = (float) (r0 * Math.cos(ph1)), z10 = (float) (r0 * Math.sin(ph1));
                float x11 = (float) (r1 * Math.cos(ph1)), z11 = (float) (r1 * Math.sin(ph1));
                float x01 = (float) (r1 * Math.cos(ph0)), z01 = (float) (r1 * Math.sin(ph0));

                emitSkyVert(pose, vc, x00, y0, z00, u0, v0, tint);
                emitSkyVert(pose, vc, x10, y0, z10, u1, v0, tint);
                emitSkyVert(pose, vc, x11, y1, z11, u1, v1, tint);
                emitSkyVert(pose, vc, x01, y1, z01, u0, v1, tint);
            }
        }
        buf.endBatch(RenderTypes.entityTranslucent(tex));
        com.mojang.blaze3d.opengl.GlStateManager._depthMask(true);
    }

    private static void emitSkyVert(PoseStack.Pose pose, VertexConsumer vc, float x, float y, float z, float u, float v, int argb) {
        vc.addVertex(pose, x, y, z)
                .setColor(argb)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(0xF000F0)
                .setNormal(pose, 0f, 1f, 0f);
    }

    private static double fbm3(double x, double y, double z) {
        double sum = 0.0;
        double amp = 0.52;
        double freq = 1.0;
        for (int i = 0; i < 4; i++) {
            sum += amp * valueNoise3(x * freq, y * freq, z * freq);
            freq *= 2.05;
            amp *= 0.5;
        }
        return sum;
    }

    private static double valueNoise3(double x, double y, double z) {
        int ix = (int) Math.floor(x), iy = (int) Math.floor(y), iz = (int) Math.floor(z);
        double fx = x - ix, fy = y - iy, fz = z - iz;
        double ux = fx * fx * (3.0 - 2.0 * fx);
        double uy = fy * fy * (3.0 - 2.0 * fy);
        double uz = fz * fz * (3.0 - 2.0 * fz);

        double n000 = hash3(ix, iy, iz), n100 = hash3(ix + 1, iy, iz);
        double n010 = hash3(ix, iy + 1, iz), n110 = hash3(ix + 1, iy + 1, iz);
        double n001 = hash3(ix, iy, iz + 1), n101 = hash3(ix + 1, iy, iz + 1);
        double n011 = hash3(ix, iy + 1, iz + 1), n111 = hash3(ix + 1, iy + 1, iz + 1);

        double nx00 = lerp(n000, n100, ux), nx10 = lerp(n010, n110, ux);
        double nx01 = lerp(n001, n101, ux), nx11 = lerp(n011, n111, ux);
        double nxy0 = lerp(nx00, nx10, uy), nxy1 = lerp(nx01, nx11, uy);
        return lerp(nxy0, nxy1, uz);
    }

    private static double hash3(int x, int y, int z) {
        int h = x * 374761393 + y * 668265263 + z * 1274126177;
        h = (h ^ (h >> 13)) * 1274126177;
        return ((h ^ (h >> 16)) & 0x7FFFFFFF) / (double) 0x7FFFFFFF;
    }

    private static double clamp01(double v) { return Math.max(0.0, Math.min(1.0, v)); }
    private static double lerp(double a, double b, double t) { return a + (b - a) * t; }
    private static double smoothstep(double e0, double e1, double x) {
        double t = clamp01((x - e0) / (e1 - e0));
        return t * t * (3.0 - 2.0 * t);
    }

    private static int packRgb(double r, double g, double b) {
        int ir = Math.max(0, Math.min(255, (int) Math.round(r)));
        int ig = Math.max(0, Math.min(255, (int) Math.round(g)));
        int ib = Math.max(0, Math.min(255, (int) Math.round(b)));
        return (ir << 16) | (ig << 8) | ib;
    }
}
