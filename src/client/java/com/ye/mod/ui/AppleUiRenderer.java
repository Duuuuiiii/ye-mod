package com.ye.mod.ui;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;

/**
 * 256x256 8x8-Supersampled SDF UI Renderer with 3D surface depth,
 * ambient drop shadows, specular top bevel highlights, and silky spring-lerp state animations.
 */
public final class AppleUiRenderer {
    public static final int TEXT_PRIMARY = 0xFFF5F7FA;
    public static final int APPLE_GREEN = 0xFF30D158;
    public static final int TOGGLE_OFF = 0xFF2D3342;

    public static boolean lightTheme = false;
    public static float themeAnim = 0.0f;

    private static final Map<String, Float> ANIM_CHANNELS = new HashMap<>();
    private static long lastFrameNs = System.nanoTime();
    private static float frameDt = 0.016f;

    private static final int TEX_SIZE = 256;
    private static final int CORNER_PX = 80;
    private static final Identifier ROUNDED_TEX = Identifier.fromNamespaceAndPath("ye", "ui/sdf_rounded_256");
    private static final Identifier CIRCLE_TEX = Identifier.fromNamespaceAndPath("ye", "ui/sdf_circle_128");
    private static boolean initialized = false;

    private AppleUiRenderer() {}

    public static void beginFrame() {
        long now = System.nanoTime();
        frameDt = Math.max(0.002f, Math.min(0.05f, (now - lastFrameNs) / 1_000_000_000.0f));
        lastFrameNs = now;
        float target = com.ye.mod.core.ConfigManager.lightTheme ? 1.0f : 0.0f;
        themeAnim = smooth(themeAnim, target, 14.0f, frameDt);
        lightTheme = com.ye.mod.core.ConfigManager.lightTheme;
    }

    public static float dt() {
        return frameDt;
    }

    public static float animChannel(String key, float target, float speed) {
        float cur = ANIM_CHANNELS.getOrDefault(key, target);
        float next = smooth(cur, target, speed, frameDt);
        ANIM_CHANNELS.put(key, next);
        return next;
    }

    public static int bgWindowTop() { return lerpColor(0xF412151E, 0xF8F8FAFC, themeAnim); }
    public static int bgWindowBot() { return lerpColor(0xF60B0D14, 0xF8EAEFF7, themeAnim); }
    public static int bgHeader()    { return lerpColor(0xFA090B11, 0xFCE2E8F2, themeAnim); }
    public static int bgCard()      { return lerpColor(0xFF171B26, 0xFFFFFFFF, themeAnim); }
    public static int bgCardBot()   { return lerpColor(0xFF12151F, 0xFFF1F5FB, themeAnim); }
    public static int bgCardHover() { return lerpColor(0xFF212737, 0xFFE6EEF8, themeAnim); }
    public static int borderOuter() { return lerpColor(0x36FFFFFF, 0x260F172A, themeAnim); }
    public static int bevelTop()    { return lerpColor(0x30FFFFFF, 0xDDFFFFFF, themeAnim); }
    public static int textPrimary() { return lerpColor(0xFFF5F7FA, 0xFF0F172A, themeAnim); }
    public static int textSecondary() { return lerpColor(0xFF94A3B8, 0xFF475569, themeAnim); }
    public static int textTertiary()  { return lerpColor(0xFF64748B, 0xFF64748B, themeAnim); }

    public static void toggleTheme() {
        com.ye.mod.core.ConfigManager.lightTheme = !com.ye.mod.core.ConfigManager.lightTheme;
        lightTheme = com.ye.mod.core.ConfigManager.lightTheme;
        com.ye.mod.core.ConfigManager.saveAsync();
    }

    public static void drawText(GuiGraphics g, Font font, String text, int x, int y, int color) {
        if (text == null) return;
        g.drawString(font, text, x, y, color, themeAnim < 0.5f);
    }

    public static void drawCenteredText(GuiGraphics g, Font font, String text, int cx, int y, int color) {
        if (text == null) return;
        drawText(g, font, text, cx - font.width(text) / 2, y, color);
    }

    public static void ensureInit() {
        if (initialized) return;
        initialized = true;
        Minecraft mc = Minecraft.getInstance();
        mc.getTextureManager().register(ROUNDED_TEX, new DynamicTexture(() -> "ye_sdf_rounded_256", buildRoundedImage()));
        mc.getTextureManager().register(CIRCLE_TEX, new DynamicTexture(() -> "ye_sdf_circle_128", buildCircleImage()));
    }

    private static NativeImage buildRoundedImage() {
        NativeImage img = new NativeImage(TEX_SIZE, TEX_SIZE, true);
        double r = CORNER_PX - 2.0;
        double half = TEX_SIZE / 2.0;
        double boxHalf = half - 2.0 - r;

        for (int y = 0; y < TEX_SIZE; y++) {
            for (int x = 0; x < TEX_SIZE; x++) {
                int cov = 0;
                for (int sy = 0; sy < 6; sy++) {
                    for (int sx = 0; sx < 6; sx++) {
                        double px = x + (sx + 0.5) / 6.0 - half;
                        double py = y + (sy + 0.5) / 6.0 - half;
                        double qx = Math.max(0.0, Math.abs(px) - boxHalf);
                        double qy = Math.max(0.0, Math.abs(py) - boxHalf);
                        if (Math.hypot(qx, qy) <= r) cov++;
                    }
                }
                int alpha = (cov * 255) / 36;
                img.setPixel(x, y, (alpha << 24) | 0x00FFFFFF);
            }
        }
        return img;
    }

    private static NativeImage buildCircleImage() {
        int s = 128;
        NativeImage img = new NativeImage(s, s, true);
        double half = s / 2.0;
        double r = half - 2.0;
        for (int y = 0; y < s; y++) {
            for (int x = 0; x < s; x++) {
                int cov = 0;
                for (int sy = 0; sy < 6; sy++) {
                    for (int sx = 0; sx < 6; sx++) {
                        double px = x + (sx + 0.5) / 6.0 - half;
                        double py = y + (sy + 0.5) / 6.0 - half;
                        if (Math.hypot(px, py) <= r) cov++;
                    }
                }
                int alpha = (cov * 255) / 36;
                img.setPixel(x, y, (alpha << 24) | 0x00FFFFFF);
            }
        }
        return img;
    }

    public static void drawRoundedRect(GuiGraphics g, int x, int y, int w, int h, int radius, int color) {
        if (w <= 0 || h <= 0) return;
        ensureInit();
        int r = Math.min(radius, Math.min(w / 2, h / 2));
        if (r <= 1) {
            g.fill(x, y, x + w, y + h, color);
            return;
        }
        int c = CORNER_PX;
        int midTex = TEX_SIZE - c * 2;
        int midW = w - r * 2;
        int midH = h - r * 2;

        g.blit(RenderPipelines.GUI_TEXTURED, ROUNDED_TEX, x, y, 0, 0, r, r, c, c, TEX_SIZE, TEX_SIZE, color);
        g.blit(RenderPipelines.GUI_TEXTURED, ROUNDED_TEX, x + w - r, y, TEX_SIZE - c, 0, r, r, c, c, TEX_SIZE, TEX_SIZE, color);
        g.blit(RenderPipelines.GUI_TEXTURED, ROUNDED_TEX, x, y + h - r, 0, TEX_SIZE - c, r, r, c, c, TEX_SIZE, TEX_SIZE, color);
        g.blit(RenderPipelines.GUI_TEXTURED, ROUNDED_TEX, x + w - r, y + h - r, TEX_SIZE - c, TEX_SIZE - c, r, r, c, c, TEX_SIZE, TEX_SIZE, color);

        if (midW > 0) {
            g.blit(RenderPipelines.GUI_TEXTURED, ROUNDED_TEX, x + r, y, c, 0, midW, r, midTex, c, TEX_SIZE, TEX_SIZE, color);
            g.blit(RenderPipelines.GUI_TEXTURED, ROUNDED_TEX, x + r, y + h - r, c, TEX_SIZE - c, midW, r, midTex, c, TEX_SIZE, TEX_SIZE, color);
        }
        if (midH > 0) {
            g.blit(RenderPipelines.GUI_TEXTURED, ROUNDED_TEX, x, y + r, 0, c, r, midH, c, midTex, TEX_SIZE, TEX_SIZE, color);
            g.blit(RenderPipelines.GUI_TEXTURED, ROUNDED_TEX, x + w - r, y + r, TEX_SIZE - c, c, r, midH, c, midTex, TEX_SIZE, TEX_SIZE, color);
        }
        if (midW > 0 && midH > 0) {
            g.fill(x + r, y + r, x + w - r, y + h - r, color);
        }
    }

    /**
     * Multi-layered 3D Window shell with soft outer drop-shadow, vertical depth gradient, and top specular rim.
     */
    public static void drawElevatedWindow(GuiGraphics g, int x, int y, int w, int h, int radius) {
        // Soft multi-pass ambient drop shadow
        drawRoundedRect(g, x - 3, y + 2, w + 6, h + 6, radius + 3, 0x24000000);
        drawRoundedRect(g, x - 1, y + 1, w + 2, h + 3, radius + 1, 0x44000000);
        // Main body + subtle bottom depth shade
        drawRoundedRect(g, x, y, w, h, radius, bgWindowTop());
        g.fillGradient(x + 2, y + radius, x + w - 2, y + h - radius, 0x00000000, 0x35000000);
        // Specular top rim & crisp hairline outline
        g.fill(x + radius, y, x + w - radius, y + 1, bevelTop());
        g.renderOutline(x, y, w, h, borderOuter());
    }

    /**
     * 3D Elevated Card with drop shadow, active color blend, subtle inner top highlight, and hover glow!
     */
    public static void drawElevatedCard(GuiGraphics g, int x, int y, int w, int h, int radius,
                                        float activeProg, float hoverProg, int accent) {
        // Subtle 3D under-shadow
        drawRoundedRect(g, x, y + 1, w, h + 1, radius, 0x36000000);

        int idleBg = lerpColor(bgCard(), bgCardHover(), hoverProg);
        int activeDark = darken(accent, 0.78f);
        int topCol = lerpColor(idleBg, accent, activeProg);
        int botCol = lerpColor(bgCardBot(), activeDark, activeProg);

        drawRoundedRect(g, x, y, w, h, radius, topCol);
        if (h > radius * 2) {
            g.fillGradient(x + 1, y + radius, x + w - 1, y + h - radius + 1, 0x00000000, withAlpha(botCol, 0.45f));
        }
        // Top 1px specular bevel highlight for tactile 3D depth
        int rim = lerpColor(bevelTop(), 0x66FFFFFF, activeProg);
        g.fill(x + Math.max(3, radius - 1), y, x + w - Math.max(3, radius - 1), y + 1, rim);

        if (hoverProg > 0.05f && activeProg < 0.5f) {
            g.renderOutline(x, y, w, h, withAlpha(accent, hoverProg * 0.48f));
        }
    }

    public static void drawCircle(GuiGraphics g, int x, int y, int size, int color) {
        ensureInit();
        g.blit(RenderPipelines.GUI_TEXTURED, CIRCLE_TEX, x, y, 0, 0, size, size, 128, 128, 128, 128, color);
    }

    public static void drawPillSwitch(GuiGraphics g, int x, int y, int w, int h, float progress, int accent) {
        int bg = lerpColor(TOGGLE_OFF, accent, progress);
        drawRoundedRect(g, x, y + 1, w, h, h / 2, 0x33000000);
        drawRoundedRect(g, x, y, w, h, h / 2, bg);
        int knobSize = h - 4;
        float travel = w - knobSize - 4;
        int knobX = Math.round(x + 2 + travel * progress);
        drawCircle(g, knobX, y + 3, knobSize, 0x38000000);
        drawCircle(g, knobX, y + 2, knobSize, 0xFFFFFFFF);
    }

    public static int darken(int argb, float factor) {
        int a = (argb >>> 24) & 0xFF;
        int r = Math.min(255, Math.round(((argb >>> 16) & 0xFF) * factor));
        int gr = Math.min(255, Math.round(((argb >>> 8) & 0xFF) * factor));
        int b = Math.min(255, Math.round((argb & 0xFF) * factor));
        return (a << 24) | (r << 16) | (gr << 8) | b;
    }

    public static float smooth(float current, float target, float speed, float dt) {
        float t = 1.0F - (float) Math.exp(-speed * Math.min(0.1F, dt));
        return current + (target - current) * t;
    }

    public static int lerpColor(int a, int b, float t) {
        t = Math.max(0.0F, Math.min(1.0F, t));
        int aa = (a >>> 24) & 0xFF, ar = (a >>> 16) & 0xFF, ag = (a >>> 8) & 0xFF, ab = a & 0xFF;
        int ba = (b >>> 24) & 0xFF, br = (b >>> 16) & 0xFF, bg = (b >>> 8) & 0xFF, bb = b & 0xFF;
        int oa = (int) (aa + (ba - aa) * t);
        int or = (int) (ar + (br - ar) * t);
        int og = (int) (ag + (bg - ag) * t);
        int ob = (int) (ab + (bb - ab) * t);
        return (oa << 24) | (or << 16) | (og << 8) | ob;
    }

    public static int withAlpha(int argb, float alphaMul) {
        int a = Math.round(((argb >>> 24) & 0xFF) * Math.max(0.0F, Math.min(1.0F, alphaMul)));
        return (a << 24) | (argb & 0x00FFFFFF);
    }
}
