package com.ye.mod.ui;

import com.mojang.blaze3d.platform.NativeImage;
import com.ye.mod.core.ConfigManager;
import com.ye.mod.core.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.awt.Color;
import java.util.HashMap;
import java.util.Map;

/**
 * Dual-Persona UI Renderer:
 * 1) SILKY (丝滑风): Tactile spring animations, golden-ratio rounded geometry, multi-layer shadows & specular sheen!
 * 2) MECHANICAL (机械风): Meteor/Rusher-inspired razor-sharp 90° right angles, 0ms instant response, high-density tactical grid!
 * Plus 3 Material Themes: DARK (深色), LIGHT (浅色), and GAUSSIAN_GLASS (高斯毛玻璃) + adjustable uiOpacity!
 * Also includes cached HSV Hue Bar & 2D Color Picker rendering + Crisp Vector Module Icons!
 */
public final class AppleUiRenderer {
    public static final int TEXT_PRIMARY = 0xFFF8FAFC;
    public static final int APPLE_GREEN = 0xFF30D158;
    public static final int TOGGLE_OFF = 0xFF283142;

    public static boolean lightTheme = false;
    public static float themeAnim = 0.0f;

    private static final Map<String, Float> ANIM_CHANNELS = new HashMap<>();
    private static final Map<Integer, StampTex> CORNER_STAMPS = new HashMap<>();
    private static final Map<Integer, StampTex> BORDER_STAMPS = new HashMap<>();
    private static final Map<Integer, StampTex> CIRCLE_STAMPS = new HashMap<>();

    private static Identifier hueBarTexId = null;
    private static Identifier resetIconTexId = null;

    private static long lastFrameNs = System.nanoTime();
    private static float frameDt = 0.016f;

    private record StampTex(Identifier id, int sizePx, DynamicTexture tex) {}

    private AppleUiRenderer() {}

    public static boolean isMechanical() {
        return ConfigManager.uiStyle == ConfigManager.UiStyle.MECHANICAL;
    }

    public static boolean isGlass() {
        return ConfigManager.uiTheme == ConfigManager.UiTheme.GAUSSIAN_GLASS;
    }

    public static void beginFrame() {
        long now = System.nanoTime();
        long diff = now - lastFrameNs;
        if (diff >= 1_500_000L) {
            frameDt = Math.max(0.001f, Math.min(0.05f, diff / 1_000_000_000.0f));
            lastFrameNs = now;
        }
        lightTheme = (ConfigManager.uiTheme == ConfigManager.UiTheme.LIGHT);
        float target = lightTheme ? 1.0f : 0.0f;
        themeAnim = isMechanical() ? target : smooth(themeAnim, target, 18.0f, frameDt);
    }

    public static float dt() {
        return frameDt;
    }

    public static float animChannel(String key, float target, float speed) {
        if (isMechanical()) {
            ANIM_CHANNELS.put(key, target);
            return target;
        }
        float cur = ANIM_CHANNELS.getOrDefault(key, target);
        float next = smooth(cur, target, speed, frameDt);
        ANIM_CHANNELS.put(key, next);
        return next;
    }

    public static int bgWindowTop() {
        float op = ConfigManager.uiOpacity;
        if (isGlass()) {
            int glassAlpha = Math.max(40, Math.min(220, Math.round(op * 155.0f)));
            return (glassAlpha << 24) | 0x000E1525;
        }
        int base = lerpColor(isMechanical() ? 0xFF0A0D14 : 0xFF0F141F, 0xFFF8FAFC, themeAnim);
        int a = Math.max(65, Math.min(255, Math.round(op * 252.0f)));
        return (a << 24) | (base & 0x00FFFFFF);
    }

    public static int bgHeader() {
        float op = ConfigManager.uiOpacity;
        if (isGlass()) {
            int ga = Math.max(55, Math.min(235, Math.round(op * 185.0f)));
            return (ga << 24) | 0x00090E1A;
        }
        int base = lerpColor(isMechanical() ? 0xFF070A10 : 0xFF0B0F18, 0xFFEAF0F8, themeAnim);
        int a = Math.max(85, Math.min(255, Math.round(Math.min(1.0f, op + 0.05f) * 254.0f)));
        return (a << 24) | (base & 0x00FFFFFF);
    }

    public static int bgCard() {
        float op = ConfigManager.uiOpacity;
        if (isGlass()) {
            int ga = Math.max(38, Math.min(210, Math.round(op * 135.0f)));
            return (ga << 24) | 0x001B253B;
        }
        int base = lerpColor(isMechanical() ? 0xFF131823 : 0xFF171D2B, 0xFFFFFFFF, themeAnim);
        int a = Math.max(75, Math.min(255, Math.round(Math.min(1.0f, op + 0.04f) * 255.0f)));
        return (a << 24) | (base & 0x00FFFFFF);
    }

    public static int bgCardHover() {
        float op = ConfigManager.uiOpacity;
        if (isGlass()) {
            int ga = Math.max(60, Math.min(230, Math.round(op * 175.0f)));
            return (ga << 24) | 0x00263452;
        }
        int base = lerpColor(isMechanical() ? 0xFF1C2434 : 0xFF21293B, 0xFFEAF1FB, themeAnim);
        int a = Math.max(95, Math.min(255, Math.round(Math.min(1.0f, op + 0.06f) * 255.0f)));
        return (a << 24) | (base & 0x00FFFFFF);
    }

    public static int borderOuter() {
        if (isGlass()) return 0x58BAE6FD;
        if (isMechanical()) return lightTheme ? 0x550F172A : 0x48475569;
        return lerpColor(0x36FFFFFF, 0x260F172A, themeAnim);
    }

    public static int borderSubtle() {
        if (isGlass()) return 0x36E0F2FE;
        if (isMechanical()) return lightTheme ? 0x300F172A : 0x34334155;
        return lerpColor(0x1EFFFFFF, 0x180F172A, themeAnim);
    }

    public static int textPrimary()   { return lerpColor(0xFFF8FAFC, 0xFF0F172A, themeAnim); }
    public static int textSecondary() { return lerpColor(0xFF94A3B8, 0xFF475569, themeAnim); }
    public static int textTertiary()  { return lerpColor(0xFF64748B, 0xFF64748B, themeAnim); }

    public static void cycleUiTheme() {
        ConfigManager.UiTheme[] vals = ConfigManager.UiTheme.values();
        ConfigManager.uiTheme = vals[(ConfigManager.uiTheme.ordinal() + 1) % vals.length];
        ConfigManager.lightTheme = (ConfigManager.uiTheme == ConfigManager.UiTheme.LIGHT);
        lightTheme = ConfigManager.lightTheme;
        ConfigManager.saveAsync();
    }

    // ==================== TYPOGRAPHY HELPERS ====================

    public static void drawText(GuiGraphics g, Font unusedMcFont, String text, int x, int y, int color) {
        YeHdFont.draw(g, text, x, y, 8.8f, color, themeAnim < 0.45f, false);
    }

    public static void drawBoldText(GuiGraphics g, String text, float x, float y, float size, int color) {
        YeHdFont.draw(g, text, x, y, size, color, themeAnim < 0.45f, true);
    }

    public static void drawSmoothText(GuiGraphics g, String text, float x, float y, float size, int color, boolean shadow) {
        YeHdFont.draw(g, text, x, y, size, color, shadow, false);
    }

    public static void drawCenteredSmooth(GuiGraphics g, String text, float cx, float y, float size, int color, boolean bold) {
        YeHdFont.drawCentered(g, text, cx, y, size, color, themeAnim < 0.45f, bold);
    }

    // ==================== 1:1 NATIVE PIXEL SDF SHAPES ====================

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

    public static void drawRoundedRect(GuiGraphics g, float x, float y, float w, float h, float radius, int color) {
        if (w <= 0.1f || h <= 0.1f || ((color >>> 24) & 0xFF) == 0) return;
        float gs = YeHdFont.guiScale();
        int pxX = Math.round(x * gs);
        int pxY = Math.round(y * gs);
        int pxW = Math.max(1, Math.round((x + w) * gs) - pxX);
        int pxH = Math.max(1, Math.round((y + h) * gs) - pxY);
        int rPx = isMechanical() ? 0 : Math.min(Math.round(radius * gs), Math.min(pxW / 2, pxH / 2));

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

        g.blit(RenderPipelines.GUI_TEXTURED, st.id, pxX, pxY, 0f, 0f, rPx, rPx, rPx, rPx, ts, ts, color);
        g.blit(RenderPipelines.GUI_TEXTURED, st.id, pxX + pxW - rPx, pxY, (float) rPx, 0f, rPx, rPx, rPx, rPx, ts, ts, color);
        g.blit(RenderPipelines.GUI_TEXTURED, st.id, pxX, pxY + pxH - rPx, 0f, (float) rPx, rPx, rPx, rPx, rPx, ts, ts, color);
        g.blit(RenderPipelines.GUI_TEXTURED, st.id, pxX + pxW - rPx, pxY + pxH - rPx, (float) rPx, (float) rPx, rPx, rPx, rPx, rPx, ts, ts, color);

        if (pxW > rPx * 2) g.fill(pxX + rPx, pxY, pxX + pxW - rPx, pxY + pxH, color);
        if (pxH > rPx * 2) {
            g.fill(pxX, pxY + rPx, pxX + rPx, pxY + pxH - rPx, color);
            g.fill(pxX + pxW - rPx, pxY + rPx, pxX + pxW, pxY + pxH - rPx, color);
        }

        pose.popMatrix();
    }

    public static void drawRoundedRect(GuiGraphics g, int x, int y, int w, int h, int radius, int color) {
        drawRoundedRect(g, (float) x, (float) y, (float) w, (float) h, (float) radius, color);
    }

    public static void drawRoundedOutline(GuiGraphics g, float x, float y, float w, float h, float radius, float strokePxF, int color) {
        if (w <= 0.1f || h <= 0.1f || ((color >>> 24) & 0xFF) == 0) return;
        float gs = YeHdFont.guiScale();
        int pxX = Math.round(x * gs);
        int pxY = Math.round(y * gs);
        int pxW = Math.max(2, Math.round((x + w) * gs) - pxX);
        int pxH = Math.max(2, Math.round((y + h) * gs) - pxY);
        int stPx = Math.max(1, Math.round(strokePxF));
        int rPx = isMechanical() ? 0 : Math.min(Math.round(radius * gs), Math.min(pxW / 2, pxH / 2));

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

    public static void drawElevatedWindow(GuiGraphics g, int x, int y, int w, int h, int radius) {
        if (!isMechanical()) {
            drawRoundedRect(g, x - 3.5f, y - 1.0f, w + 7.0f, h + 6.0f, radius + 3.5f, 0x1E000000);
            drawRoundedRect(g, x - 1.5f, y, w + 3.0f, h + 3.0f, radius + 1.5f, 0x34000000);
        }
        drawRoundedRect(g, x, y, w, h, radius, bgWindowTop());
        if (isGlass()) {
            // Subtle top glass frost reflection sheen
            g.fillGradient(x + 2, y + 2, x + w - 2, y + Math.min(48, h / 3), 0x1EFFFFFF, 0x00FFFFFF);
        }
        drawRoundedOutline(g, x, y, w, h, radius, isMechanical() ? 1.6f : 1.4f, borderOuter());
    }

    public static void drawElevatedCard(GuiGraphics g, int x, int y, int w, int h, int radius,
                                        float activeProg, float hoverProg, int accent) {
        if (!isMechanical()) {
            drawRoundedRect(g, x, y + 0.8f, w, h + 0.6f, radius, 0x20000000);
        }

        int idleBg = lerpColor(bgCard(), bgCardHover(), hoverProg);
        if (isMechanical()) {
            // Mechanical style: crisp tactical dark card + razor-sharp left accent bar when active!
            int mechActiveBg = lerpColor(idleBg, withAlpha(accent, 0.28f), activeProg);
            g.fill(x, y, x + w, y + h, mechActiveBg);
            int bCol = activeProg > 0.5f ? accent : (hoverProg > 0.5f ? withAlpha(accent, 0.8f) : borderSubtle());
            drawRoundedOutline(g, x, y, w, h, 0, 1.0f, bCol);
            if (activeProg > 0.5f) {
                g.fill(x, y, x + 2, y + h, accent);
            }
        } else {
            int fillCol = lerpColor(idleBg, accent, activeProg);
            drawRoundedRect(g, x, y, w, h, radius, fillCol);
            int borderCol = activeProg > 0.5f
                    ? withAlpha(0xFFFFFFFF, 0.26f)
                    : lerpColor(borderSubtle(), withAlpha(accent, 0.75f), hoverProg);
            drawRoundedOutline(g, x, y, w, h, radius, 1.0f, borderCol);
        }
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
        if (isMechanical()) {
            g.fill(x, y, x + w, y + h, bg);
            drawRoundedOutline(g, x, y, w, h, 0, 1.0f, progress > 0.5f ? accent : 0x5564748B);
            int kw = Math.max(6, w / 2 - 2);
            int kx = x + 2 + Math.round((w - kw - 4) * progress);
            g.fill(kx, y + 2, kx + kw, y + h - 2, 0xFFF8FAFC);
            return;
        }
        drawRoundedRect(g, x, y, w, h, h * 0.5f, bg);
        drawRoundedOutline(g, x, y, w, h, h * 0.5f, 1.0f, 0x24FFFFFF);
        float knobSize = h - 3.6f;
        float travel = w - knobSize - 3.6f;
        float knobX = x + 1.8f + travel * progress;
        float knobY = y + 1.8f;
        drawCircle(g, knobX, knobY + 0.5f, knobSize, 0x35000000);
        drawCircle(g, knobX, knobY, knobSize, 0xFFFFFFFF);
    }

    // ==================== 2D HSV COLOR PICKER RENDERER ====================

    private static Identifier getHueBarTexture() {
        if (hueBarTexId != null) return hueBarTexId;
        NativeImage img = new NativeImage(256, 4, true);
        for (int x = 0; x < 256; x++) {
            int rgb = Color.HSBtoRGB(x / 255.0f, 1.0f, 1.0f) & 0x00FFFFFF;
            for (int y = 0; y < 4; y++) {
                img.setPixel(x, y, 0xFF000000 | rgb);
            }
        }
        hueBarTexId = Identifier.fromNamespaceAndPath("ye", "hsv_hue_strip");
        Minecraft.getInstance().getTextureManager().register(hueBarTexId, new DynamicTexture(() -> "ye_hue_bar", img));
        return hueBarTexId;
    }

    /**
     * Draws a crisp 2D Saturation-Brightness picker box + Hue rainbow slider bar!
     */
    public static void drawHsvPicker(GuiGraphics g, int x, int y, int svW, int svH, int hueBarY, int hueBarH,
                                     float hue, float sat, float bri) {
        int pureHueArgb = 0xFF000000 | (Color.HSBtoRGB(hue, 1.0f, 1.0f) & 0x00FFFFFF);

        // Smooth 16-column Saturation x Brightness gradient square
        int strips = 24;
        for (int i = 0; i < strips; i++) {
            int sx0 = x + (i * svW) / strips;
            int sx1 = x + ((i + 1) * svW) / strips;
            float sMid = (i + 0.5f) / strips;
            int topCol = lerpColor(0xFFFFFFFF, pureHueArgb, sMid);
            g.fillGradient(sx0, y, sx1, y + svH, topCol, 0xFF000000);
        }
        drawRoundedOutline(g, x, y, svW, svH, isMechanical() ? 0 : 4, 1.0f, 0x44FFFFFF);

        // Reticle at (sat, 1 - bri)
        float cx = x + sat * svW;
        float cy = y + (1.0f - bri) * svH;
        drawCircle(g, cx - 4.0f, cy - 4.0f, 8.0f, 0xCC000000);
        drawCircle(g, cx - 3.0f, cy - 3.0f, 6.0f, 0xFFFFFFFF);

        // Hue Rainbow Strip
        g.blit(RenderPipelines.GUI_TEXTURED, getHueBarTexture(), x, hueBarY, 0f, 0f, svW, hueBarH, 256, 4, 256, 4);
        drawRoundedOutline(g, x, hueBarY, svW, hueBarH, isMechanical() ? 0 : 3, 1.0f, 0x44FFFFFF);
        float hx = x + hue * svW;
        g.fill(Math.round(hx - 1.5f), hueBarY - 1, Math.round(hx + 1.5f), hueBarY + hueBarH + 1, 0xFFFFFFFF);
    }

    private static Identifier getResetIconTexture() {
        if (resetIconTexId != null) return resetIconTexId;
        final int S = 64;
        NativeImage img = new NativeImage(S, S, true);
        double cx = S * 0.5, cy = S * 0.5;
        double rOuter = S * 0.36, rInner = S * 0.24, rMid = (rOuter + rInner) * 0.5;
        for (int py = 0; py < S; py++) {
            for (int px = 0; px < S; px++) {
                double dx = px + 0.5 - cx;
                double dy = py + 0.5 - cy;
                double dist = Math.hypot(dx, dy);
                double ang = Math.toDegrees(Math.atan2(dy, dx));
                if (ang < 0) ang += 360.0;
                // 290-degree circular arc (from 20 deg to 310 deg)
                double a = 0.0;
                if (ang >= 20.0 && ang <= 315.0) {
                    double dRing = Math.max(rInner - dist, dist - rOuter);
                    a = Math.max(a, Math.max(0.0, Math.min(1.0, 0.85 - dRing * 0.65)));
                }
                // Crisp arrowhead pointing clockwise at ~325 deg
                double tipX = cx + Math.cos(Math.toRadians(326)) * rMid;
                double tipY = cy + Math.sin(Math.toRadians(326)) * rMid;
                double b1X = cx + Math.cos(Math.toRadians(288)) * (rOuter + 5.5);
                double b1Y = cy + Math.sin(Math.toRadians(288)) * (rOuter + 5.5);
                double b2X = cx + Math.cos(Math.toRadians(288)) * (rInner - 5.5);
                double b2Y = cy + Math.sin(Math.toRadians(288)) * (rInner - 5.5);
                double dTri = signedDistTriangle(px + 0.5, py + 0.5, tipX, tipY, b1X, b1Y, b2X, b2Y);
                if (dTri < 1.3) {
                    a = Math.max(a, Math.max(0.0, Math.min(1.0, 0.9 - dTri * 0.7)));
                }
                int alpha = Math.max(0, Math.min(255, (int) Math.round(a * 255.0)));
                img.setPixel(px, py, (alpha << 24) | 0x00FFFFFF);
            }
        }
        resetIconTexId = Identifier.fromNamespaceAndPath("ye", "reset_circle_icon");
        Minecraft.getInstance().getTextureManager().register(resetIconTexId, new DynamicTexture(() -> "ye_reset_icon", img));
        return resetIconTexId;
    }

    private static double signedDistTriangle(double px, double py, double ax, double ay, double bx, double by, double cx, double cy) {
        double d1 = (px - bx) * (ay - by) - (ax - bx) * (py - by);
        double d2 = (px - cx) * (by - cy) - (bx - cx) * (py - cy);
        double d3 = (px - ax) * (cy - ay) - (cx - ax) * (py - ay);
        boolean hasNeg = (d1 < 0) || (d2 < 0) || (d3 < 0);
        boolean hasPos = (d1 > 0) || (d2 > 0) || (d3 > 0);
        if (!(hasNeg && hasPos)) return -1.0;
        return 2.0;
    }

    public static void drawResetCircleIcon(GuiGraphics g, float x, float y, float size, int col) {
        g.blit(RenderPipelines.GUI_TEXTURED, getResetIconTexture(),
                Math.round(x), Math.round(y), 0f, 0f, Math.round(size), Math.round(size), 64, 64, 64, 64, col);
    }

    // ==================== CRISP FUNCTIONAL VECTOR MODULE ICONS ====================

    public static void drawModuleIcon(GuiGraphics g, Module m, float x, float y, float sz, int col) {
        String id = m.getId();
        float cx = x + sz * 0.5f;
        float cy = y + sz * 0.5f;

        if (id.contains("wings") || id.equals("flight_model")) {
            // Swept Wing / Elytra Silhouette
            drawRoundedRect(g, x + 0.8f, y + 1.8f, sz * 0.40f, sz * 0.24f, 1.0f, col);
            drawRoundedRect(g, x + 1.8f, y + 4.8f, sz * 0.30f, sz * 0.22f, 1.0f, col);
            drawRoundedRect(g, x + sz - sz * 0.40f - 0.8f, y + 1.8f, sz * 0.40f, sz * 0.24f, 1.0f, col);
            drawRoundedRect(g, x + sz - sz * 0.30f - 1.8f, y + 4.8f, sz * 0.30f, sz * 0.22f, 1.0f, col);
        } else if (id.equals("custom_skin") || id.equals("body_scale") || id.equals("accessories") || id.equals("stand_buddy")) {
            // Player Head + Shoulders Silhouette
            drawRoundedRect(g, cx - sz * 0.22f, y + 1.2f, sz * 0.44f, sz * 0.40f, 1.5f, col);
            drawRoundedRect(g, x + 1.8f, y + sz * 0.58f, sz - 3.6f, sz * 0.32f, 1.5f, withAlpha(col, 0.85f));
        } else if (id.equals("custom_cape")) {
            // Flowing Cape
            drawRoundedRect(g, cx - sz * 0.26f, y + 1.2f, sz * 0.52f, sz - 2.4f, 1.2f, col);
        } else if (id.equals("soul_rings")) {
            // Stacked Concentric Rings
            drawRoundedOutline(g, x + 1.0f, y + 2.5f, sz - 2.0f, sz * 0.45f, 3.0f, 1.3f, col);
            drawRoundedOutline(g, x + 2.2f, y + 5.2f, sz - 4.4f, sz * 0.40f, 2.5f, 1.2f, withAlpha(col, 0.78f));
        } else if (id.equals("step_decals")) {
            // Dual Footprint Blocks
            drawRoundedRect(g, x + 1.8f, y + 1.8f, sz * 0.34f, sz * 0.48f, 1.0f, col);
            drawRoundedRect(g, x + sz * 0.52f, y + sz * 0.36f, sz * 0.34f, sz * 0.48f, 1.0f, withAlpha(col, 0.85f));
        } else if (id.equals("swing_speed") || id.equals("hit_feedback") || id.equals("damage_popups")) {
            // Sword / Strike Slash
            drawRoundedRect(g, x + 1.5f, cy - 1.0f, sz - 3.0f, 2.0f, 0.8f, col);
            drawRoundedRect(g, cx + 1.0f, y + 2.2f, 2.0f, sz - 4.4f, 0.8f, withAlpha(col, 0.85f));
        } else if (id.equals("crosshair") || id.equals("hit_crosshair")) {
            // Precision 4-way Crosshair
            drawRoundedRect(g, x + 1.0f, cy - 0.8f, sz * 0.30f, 1.6f, 0.5f, col);
            drawRoundedRect(g, x + sz * 0.70f - 1.0f, cy - 0.8f, sz * 0.30f, 1.6f, 0.5f, col);
            drawRoundedRect(g, cx - 0.8f, y + 1.0f, 1.6f, sz * 0.30f, 0.5f, col);
            drawRoundedRect(g, cx - 0.8f, y + sz * 0.70f - 1.0f, 1.6f, sz * 0.30f, 0.5f, col);
        } else if (id.equals("zoom") || id.equals("camera_tweaks")) {
            // Optical Lens + Ring
            drawRoundedOutline(g, x + 1.2f, y + 1.2f, sz - 2.4f, sz - 2.4f, (sz - 2.4f) * 0.5f, 1.4f, col);
            drawCircle(g, cx - 1.8f, cy - 1.8f, 3.6f, col);
        } else if (id.equals("auto_log") || id.equals("logout_ghosts")) {
            // Shield / Exit Door
            drawRoundedOutline(g, x + 1.8f, y + 1.4f, sz - 3.6f, sz - 2.8f, 2.0f, 1.4f, col);
            drawRoundedRect(g, cx - 1.2f, cy - 2.2f, 2.4f, 4.4f, 0.8f, col);
        } else if (id.equals("flight_speed") || id.equals("speed_lines")) {
            // Triple Speed Chevrons
            drawRoundedRect(g, x + 1.2f, y + 2.2f, sz * 0.55f, 1.8f, 0.8f, col);
            drawRoundedRect(g, x + 2.8f, cy - 0.9f, sz * 0.62f, 1.8f, 0.8f, col);
            drawRoundedRect(g, x + 1.2f, y + sz - 4.0f, sz * 0.55f, 1.8f, 0.8f, withAlpha(col, 0.8f));
        } else if (id.equals("quick_chat")) {
            // Chat Bubble
            drawRoundedRect(g, x + 1.2f, y + 1.8f, sz - 2.4f, sz * 0.64f, 2.0f, col);
            drawRoundedRect(g, x + 2.5f, y + sz * 0.78f, 2.6f, 2.2f, 0.6f, col);
        } else if (id.equals("stats")) {
            // Bar Chart
            drawRoundedRect(g, x + 1.5f, y + sz * 0.45f, 2.4f, sz * 0.45f, 0.8f, col);
            drawRoundedRect(g, cx - 1.2f, y + sz * 0.18f, 2.4f, sz * 0.72f, 0.8f, col);
            drawRoundedRect(g, x + sz - 3.9f, y + sz * 0.32f, 2.4f, sz * 0.58f, 0.8f, withAlpha(col, 0.85f));
        } else if (id.equals("health_plates")) {
            // HP Bar
            drawRoundedOutline(g, x + 1.0f, cy - 2.6f, sz - 2.0f, 5.2f, 2.0f, 1.2f, col);
            drawRoundedRect(g, x + 2.4f, cy - 1.4f, (sz - 4.8f) * 0.68f, 2.8f, 1.0f, col);
        } else if (id.contains("esp") || id.contains("radar") || id.contains("search") || id.contains("portal") || id.contains("lava")) {
            // 3D Cube / Scanner Box
            drawRoundedOutline(g, x + 1.5f, y + 1.5f, sz - 3f, sz - 3f, 1.8f, 1.4f, col);
            drawRoundedRect(g, cx - 2.0f, cy - 2.0f, 4.0f, 4.0f, 0.8f, withAlpha(col, 0.8f));
        } else if (id.equals("trajectory_preview")) {
            // Dotted Arc
            drawCircle(g, x + 1.5f, y + sz - 4.0f, 2.5f, col);
            drawCircle(g, cx - 1.2f, y + 2.0f, 2.4f, col);
            drawCircle(g, x + sz - 4.0f, cy + 0.5f, 2.8f, col);
        } else if (id.contains("color") || id.contains("glint") || id.contains("shader") || id.contains("ambience") || id.contains("sin_city") || id.contains("blur")) {
            // Optical Palette / Sun
            drawCircle(g, x + 1.5f, y + 2.5f, sz * 0.52f, col);
            drawCircle(g, x + sz * 0.38f, y + sz * 0.34f, sz * 0.50f, withAlpha(col, 0.65f));
        } else if (id.startsWith("hud_") || id.equals("wthit") || id.equals("player_ui")) {
            // Dashboard Layout Grid
            drawRoundedRect(g, x + 1.2f, y + 1.5f, sz * 0.42f, sz - 3f, 1.2f, col);
            drawRoundedRect(g, x + sz * 0.56f, y + 1.5f, sz * 0.34f, sz * 0.38f, 1.2f, col);
            drawRoundedRect(g, x + sz * 0.56f, y + sz * 0.56f, sz * 0.34f, sz * 0.30f, 1.2f, withAlpha(col, 0.72f));
        } else {
            // Utility Gear / Diamond
            drawRoundedOutline(g, x + 2.0f, y + 2.0f, sz - 4.0f, sz - 4.0f, 2.5f, 1.4f, col);
            drawCircle(g, cx - 1.4f, cy - 1.4f, 2.8f, col);
        }
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
