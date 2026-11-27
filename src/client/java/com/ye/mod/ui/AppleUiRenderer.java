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
 * 1:1 Native Monitor-Pixel Vector UI Renderer:
 * - Renders rounded rects, true rounded hairline borders (NEVER sharp square outlines!),
 *   soft Gaussian drop shadows, pill switches, and circles at exact 1:1 physical screen pixels!
 * - Zero UV edge bleeding (eliminating bottom white lines) and zero corner square artifacts!
 * - Integrates YeHdFont for desktop-grade anti-aliased Microsoft YaHei UI / Segoe UI typography.
 */
public final class AppleUiRenderer {
    public static final int TEXT_PRIMARY = 0xFFF8FAFC;
    public static final int APPLE_GREEN = 0xFF30D158;
    public static final int TOGGLE_OFF = 0xFF2B3242;

    public static boolean lightTheme = false;
    public static float themeAnim = 0.0f;

    private static final Map<String, Float> ANIM_CHANNELS = new HashMap<>();
    private static final Map<Integer, StampTex> CORNER_STAMPS = new HashMap<>();
    private static final Map<Integer, StampTex> BORDER_STAMPS = new HashMap<>();
    private static final Map<Integer, StampTex> CIRCLE_STAMPS = new HashMap<>();

    private static long lastFrameNs = System.nanoTime();
    private static float frameDt = 0.016f;

    private record StampTex(Identifier id, int sizePx, DynamicTexture tex) {}

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

    public static int bgWindowTop()   { return lerpColor(0xF610141D, 0xFAF8FAFC, themeAnim); }
    public static int bgHeader()      { return lerpColor(0xFA0B0E16, 0xFCEBF0F7, themeAnim); }
    public static int bgCard()        { return lerpColor(0xFF171C28, 0xFFFFFFFF, themeAnim); }
    public static int bgCardHover()   { return lerpColor(0xFF212838, 0xFFEAF1FB, themeAnim); }
    public static int borderOuter()   { return lerpColor(0x32FFFFFF, 0x240F172A, themeAnim); }
    public static int borderSubtle()  { return lerpColor(0x1EFFFFFF, 0x180F172A, themeAnim); }
    public static int textPrimary()   { return lerpColor(0xFFF8FAFC, 0xFF0F172A, themeAnim); }
    public static int textSecondary() { return lerpColor(0xFF94A3B8, 0xFF475569, themeAnim); }
    public static int textTertiary()  { return lerpColor(0xFF64748B, 0xFF64748B, themeAnim); }

    public static void toggleTheme() {
        com.ye.mod.core.ConfigManager.lightTheme = !com.ye.mod.core.ConfigManager.lightTheme;
        lightTheme = com.ye.mod.core.ConfigManager.lightTheme;
        com.ye.mod.core.ConfigManager.saveAsync();
    }

    // ==================== HIGH-DPI VECTOR TYPOGRAPHY ====================

    public static void drawText(GuiGraphics g, Font unusedMcFont, String text, int x, int y, int color) {
        YeHdFont.draw(g, text, x, y - 0.5f, 8.8f, color, themeAnim < 0.45f, false);
    }

    public static void drawBoldText(GuiGraphics g, String text, float x, float y, float size, int color) {
        YeHdFont.draw(g, text, x, y, size, color, themeAnim < 0.45f, true);
    }

    public static void drawSmoothText(GuiGraphics g, String text, float x, float y, float size, int color, boolean shadow) {
        YeHdFont.draw(g, text, x, y, size, color, shadow, false);
    }

    public static void drawCenteredText(GuiGraphics g, Font unusedMcFont, String text, int cx, int y, int color) {
        YeHdFont.drawCentered(g, text, cx, y - 0.5f, 8.8f, color, themeAnim < 0.45f, false);
    }

    public static void drawCenteredSmooth(GuiGraphics g, String text, float cx, float y, float size, int color, boolean bold) {
        YeHdFont.drawCentered(g, text, cx, y, size, color, themeAnim < 0.45f, bold);
    }

    // ==================== 1:1 NATIVE PIXEL SDF ROUNDED SHAPES ====================

    private static StampTex getCornerStamp(int rPx) {
        int clamped = Math.max(2, Math.min(96, rPx));
        return CORNER_STAMPS.computeIfAbsent(clamped, r -> {
            int s = r * 2;
            NativeImage img = new NativeImage(s, s, true);
            double rad = r - 0.35;
            for (int y = 0; y < s; y++) {
                for (int x = 0; x < s; x++) {
                    int cov = 0;
                    for (int sy = 0; sy < 8; sy++) {
                        double py = y + (sy + 0.5) / 8.0 - r;
                        for (int sx = 0; sx < 8; sx++) {
                            double px = x + (sx + 0.5) / 8.0 - r;
                            if (px * px + py * py <= rad * rad) cov++;
                        }
                    }
                    int a = (cov * 255 + 32) / 64;
                    img.setPixel(x, y, (a << 24) | 0x00FFFFFF);
                }
            }
            Identifier id = Identifier.fromNamespaceAndPath("ye", "sdf_corner_px_" + r);
            DynamicTexture dt = new DynamicTexture(() -> "ye_sdf_corner_" + r, img);
            Minecraft.getInstance().getTextureManager().register(id, dt);
            return new StampTex(id, s, dt);
        });
    }

    private static StampTex getBorderCornerStamp(int rPx, int strokePx) {
        int rClamped = Math.max(2, Math.min(96, rPx));
        int stClamped = Math.max(1, Math.min(6, strokePx));
        int key = (rClamped << 4) | stClamped;
        return BORDER_STAMPS.computeIfAbsent(key, k -> {
            int s = rClamped * 2;
            NativeImage img = new NativeImage(s, s, true);
            double outerR = rClamped - 0.35;
            double innerR = Math.max(0.0, outerR - stClamped);
            double outerSq = outerR * outerR;
            double innerSq = innerR * innerR;
            for (int y = 0; y < s; y++) {
                for (int x = 0; x < s; x++) {
                    int cov = 0;
                    for (int sy = 0; sy < 8; sy++) {
                        double py = y + (sy + 0.5) / 8.0 - rClamped;
                        for (int sx = 0; sx < 8; sx++) {
                            double px = x + (sx + 0.5) / 8.0 - rClamped;
                            double dSq = px * px + py * py;
                            if (dSq <= outerSq && dSq >= innerSq) cov++;
                        }
                    }
                    int a = (cov * 255 + 32) / 64;
                    img.setPixel(x, y, (a << 24) | 0x00FFFFFF);
                }
            }
            Identifier id = Identifier.fromNamespaceAndPath("ye", "sdf_border_px_" + rClamped + "_" + stClamped);
            DynamicTexture dt = new DynamicTexture(() -> "ye_sdf_border_" + rClamped, img);
            Minecraft.getInstance().getTextureManager().register(id, dt);
            return new StampTex(id, s, dt);
        });
    }

    private static StampTex getCircleStamp(int diamPx) {
        int d = Math.max(4, Math.min(128, diamPx));
        return CIRCLE_STAMPS.computeIfAbsent(d, sz -> {
            NativeImage img = new NativeImage(sz, sz, true);
            double half = sz * 0.5;
            double rad = half - 0.4;
            double radSq = rad * rad;
            for (int y = 0; y < sz; y++) {
                for (int x = 0; x < sz; x++) {
                    int cov = 0;
                    for (int sy = 0; sy < 8; sy++) {
                        double py = y + (sy + 0.5) / 8.0 - half;
                        for (int sx = 0; sx < 8; sx++) {
                            double px = x + (sx + 0.5) / 8.0 - half;
                            if (px * px + py * py <= radSq) cov++;
                        }
                    }
                    int a = (cov * 255 + 32) / 64;
                    img.setPixel(x, y, (a << 24) | 0x00FFFFFF);
                }
            }
            Identifier id = Identifier.fromNamespaceAndPath("ye", "sdf_disc_px_" + sz);
            DynamicTexture dt = new DynamicTexture(() -> "ye_sdf_disc_" + sz, img);
            Minecraft.getInstance().getTextureManager().register(id, dt);
            return new StampTex(id, sz, dt);
        });
    }

    /**
     * Seam-free, 1:1 physical-pixel anti-aliased rounded rectangle!
     * Zero overlap between quads (eliminating alpha double-blending seams & bottom white lines!).
     */
    public static void drawRoundedRect(GuiGraphics g, float x, float y, float w, float h, float radius, int color) {
        if (w <= 0.1f || h <= 0.1f || ((color >>> 24) & 0xFF) == 0) return;
        float gs = YeHdFont.guiScale();
        int pxX = Math.round(x * gs);
        int pxY = Math.round(y * gs);
        int pxW = Math.max(1, Math.round((x + w) * gs) - pxX);
        int pxH = Math.max(1, Math.round((y + h) * gs) - pxY);
        int rPx = Math.min(Math.round(radius * gs), Math.min(pxW / 2, pxH / 2));

        var pose = g.pose();
        pose.pushMatrix();
        pose.scale(1.0f / gs, 1.0f / gs);

        if (rPx <= 1) {
            g.fill(pxX, pxY, pxX + pxW, pxY + pxH, color);
            pose.popMatrix();
            return;
        }

        StampTex st = getCornerStamp(rPx);
        int ts = st.sizePx;

        // 4 exact 1:1 quarter-circle corners (non-overlapping!)
        g.blit(RenderPipelines.GUI_TEXTURED, st.id, pxX, pxY, 0f, 0f, rPx, rPx, rPx, rPx, ts, ts, color);
        g.blit(RenderPipelines.GUI_TEXTURED, st.id, pxX + pxW - rPx, pxY, (float) rPx, 0f, rPx, rPx, rPx, rPx, ts, ts, color);
        g.blit(RenderPipelines.GUI_TEXTURED, st.id, pxX, pxY + pxH - rPx, 0f, (float) rPx, rPx, rPx, rPx, rPx, ts, ts, color);
        g.blit(RenderPipelines.GUI_TEXTURED, st.id, pxX + pxW - rPx, pxY + pxH - rPx, (float) rPx, (float) rPx, rPx, rPx, rPx, rPx, ts, ts, color);

        // 3 exact non-overlapping interior solid fills (zero UV edge sampling = zero white lines!)
        if (pxW > rPx * 2) {
            g.fill(pxX + rPx, pxY, pxX + pxW - rPx, pxY + pxH, color);
        }
        if (pxH > rPx * 2) {
            g.fill(pxX, pxY + rPx, pxX + rPx, pxY + pxH - rPx, color);
            g.fill(pxX + pxW - rPx, pxY + rPx, pxX + pxW, pxY + pxH - rPx, color);
        }

        pose.popMatrix();
    }

    public static void drawRoundedRect(GuiGraphics g, int x, int y, int w, int h, int radius, int color) {
        drawRoundedRect(g, (float) x, (float) y, (float) w, (float) h, (float) radius, color);
    }

    /**
     * True anti-aliased rounded hairline border — NEVER protrudes sharp 90-degree corners!
     */
    public static void drawRoundedOutline(GuiGraphics g, float x, float y, float w, float h, float radius, float strokePxF, int color) {
        if (w <= 0.1f || h <= 0.1f || ((color >>> 24) & 0xFF) == 0) return;
        float gs = YeHdFont.guiScale();
        int pxX = Math.round(x * gs);
        int pxY = Math.round(y * gs);
        int pxW = Math.max(2, Math.round((x + w) * gs) - pxX);
        int pxH = Math.max(2, Math.round((y + h) * gs) - pxY);
        int stPx = Math.max(1, Math.round(strokePxF));
        int rPx = Math.min(Math.round(radius * gs), Math.min(pxW / 2, pxH / 2));

        var pose = g.pose();
        pose.pushMatrix();
        pose.scale(1.0f / gs, 1.0f / gs);

        if (rPx <= 1) {
            g.fill(pxX, pxY, pxX + pxW, pxY + stPx, color);
            g.fill(pxX, pxY + pxH - stPx, pxX + pxW, pxY + pxH, color);
            g.fill(pxX, pxY + stPx, pxX + stPx, pxY + pxH - stPx, color);
            g.fill(pxX + pxW - stPx, pxY + stPx, pxX + pxW, pxY + pxH - stPx, color);
            pose.popMatrix();
            return;
        }

        StampTex st = getBorderCornerStamp(rPx, stPx);
        int ts = st.sizePx;

        g.blit(RenderPipelines.GUI_TEXTURED, st.id, pxX, pxY, 0f, 0f, rPx, rPx, rPx, rPx, ts, ts, color);
        g.blit(RenderPipelines.GUI_TEXTURED, st.id, pxX + pxW - rPx, pxY, (float) rPx, 0f, rPx, rPx, rPx, rPx, ts, ts, color);
        g.blit(RenderPipelines.GUI_TEXTURED, st.id, pxX, pxY + pxH - rPx, 0f, (float) rPx, rPx, rPx, rPx, rPx, ts, ts, color);
        g.blit(RenderPipelines.GUI_TEXTURED, st.id, pxX + pxW - rPx, pxY + pxH - rPx, (float) rPx, (float) rPx, rPx, rPx, rPx, rPx, ts, ts, color);

        if (pxW > rPx * 2) {
            g.fill(pxX + rPx, pxY, pxX + pxW - rPx, pxY + stPx, color);
            g.fill(pxX + rPx, pxY + pxH - stPx, pxX + pxW - rPx, pxY + pxH, color);
        }
        if (pxH > rPx * 2) {
            g.fill(pxX, pxY + rPx, pxX + stPx, pxY + pxH - rPx, color);
            g.fill(pxX + pxW - stPx, pxY + rPx, pxX + pxW, pxY + pxH - rPx, color);
        }

        pose.popMatrix();
    }

    /**
     * Clean modern desktop window shell with multi-layer soft rounded shadow and true rounded border!
     */
    public static void drawElevatedWindow(GuiGraphics g, int x, int y, int w, int h, int radius) {
        drawRoundedRect(g, x - 3.0f, y - 1.0f, w + 6.0f, h + 5.0f, radius + 3.0f, 0x16000000);
        drawRoundedRect(g, x - 1.5f, y, w + 3.0f, h + 3.0f, radius + 1.5f, 0x2C000000);
        drawRoundedRect(g, x, y, w, h, radius, bgWindowTop());
        drawRoundedOutline(g, x, y, w, h, radius, 1.4f, borderOuter());
    }

    /**
     * Clean elevated card with zero bottom white line and true rounded hover/active border!
     */
    public static void drawElevatedCard(GuiGraphics g, int x, int y, int w, int h, int radius,
                                        float activeProg, float hoverProg, int accent) {
        drawRoundedRect(g, x, y + 0.8f, w, h + 0.6f, radius, 0x22000000);

        int idleBg = lerpColor(bgCard(), bgCardHover(), hoverProg);
        int fillCol = lerpColor(idleBg, accent, activeProg);
        drawRoundedRect(g, x, y, w, h, radius, fillCol);

        int borderCol = activeProg > 0.5f
                ? withAlpha(0xFFFFFFFF, 0.22f)
                : lerpColor(borderSubtle(), withAlpha(accent, 0.72f), hoverProg);
        drawRoundedOutline(g, x, y, w, h, radius, 1.0f, borderCol);
    }

    public static void drawCircle(GuiGraphics g, float x, float y, float size, int color) {
        float gs = YeHdFont.guiScale();
        int pxX = Math.round(x * gs);
        int pxY = Math.round(y * gs);
        int dPx = Math.max(2, Math.round(size * gs));
        StampTex st = getCircleStamp(dPx);

        var pose = g.pose();
        pose.pushMatrix();
        pose.scale(1.0f / gs, 1.0f / gs);
        g.blit(RenderPipelines.GUI_TEXTURED, st.id, pxX, pxY, 0f, 0f, dPx, dPx, dPx, dPx, st.sizePx, st.sizePx, color);
        pose.popMatrix();
    }

    public static void drawCircle(GuiGraphics g, int x, int y, int size, int color) {
        drawCircle(g, (float) x, (float) y, (float) size, color);
    }

    public static void drawPillSwitch(GuiGraphics g, int x, int y, int w, int h, float progress, int accent) {
        int bg = lerpColor(TOGGLE_OFF, accent, progress);
        drawRoundedRect(g, x, y, w, h, h * 0.5f, bg);
        drawRoundedOutline(g, x, y, w, h, h * 0.5f, 1.0f, 0x24FFFFFF);
        float knobSize = h - 3.6f;
        float travel = w - knobSize - 3.6f;
        float knobX = x + 1.8f + travel * progress;
        float knobY = y + 1.8f;
        drawCircle(g, knobX, knobY + 0.5f, knobSize, 0x35000000);
        drawCircle(g, knobX, knobY, knobSize, 0xFFFFFFFF);
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
