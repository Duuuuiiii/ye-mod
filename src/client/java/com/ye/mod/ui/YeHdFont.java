package com.ye.mod.ui;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 1:1 Native Monitor-Pixel Anti-Aliased Vector Font Engine (Microsoft YaHei UI / Segoe UI).
 * Bypasses Minecraft's 16x16 pixelated Unifont bitmap grid by rasterizing TrueType vector glyphs
 * at exact physical window pixel resolution and blitting 1 texel = 1 physical monitor pixel!
 */
public final class YeHdFont {
    private static final int PAGE_SIZE = 1024;
    private static final int PAD = 2;

    private static Font baseRegularAwt;
    private static Font baseBoldAwt;
    private static boolean fontInitialized = false;

    private static final List<AtlasPage> PAGES = new ArrayList<>();
    private static final Map<Long, Glyph> GLYPH_CACHE = new HashMap<>(4096);
    private static final Map<Integer, FontMetrics> METRICS_CACHE = new HashMap<>();
    private static BufferedImage scratchImg;
    private static Graphics2D scratchG2d;

    private record Glyph(int pageIdx, int u, int v, int w, int h, float advancePx, int ascentPx) {}

    private static final class AtlasPage {
        final int index;
        final Identifier texId;
        final NativeImage image;
        final DynamicTexture texture;
        int curX = PAD;
        int curY = PAD;
        int rowH = 0;
        boolean dirty = false;

        AtlasPage(int index) {
            this.index = index;
            this.texId = Identifier.fromNamespaceAndPath("ye", "hd_font_page_" + index);
            this.image = new NativeImage(PAGE_SIZE, PAGE_SIZE, true);
            // Initialize transparent white (0x00FFFFFF) to prevent dark fringes
            for (int y = 0; y < PAGE_SIZE; y++) {
                for (int x = 0; x < PAGE_SIZE; x++) {
                    this.image.setPixel(x, y, 0x00FFFFFF);
                }
            }
            this.texture = new DynamicTexture(() -> "ye_hd_font_" + index, this.image);
            Minecraft.getInstance().getTextureManager().register(this.texId, this.texture);
        }

        void flushIfDirty() {
            if (dirty) {
                dirty = false;
                texture.upload();
            }
        }
    }

    private YeHdFont() {}

    private static void ensureInit() {
        if (fontInitialized) return;
        fontInitialized = true;
        scratchImg = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
        scratchG2d = scratchImg.createGraphics();
        applyHints(scratchG2d);

        baseRegularAwt = loadBestCjkFont(Font.PLAIN);
        baseBoldAwt = loadBestCjkFont(Font.BOLD);
    }

    private static Font loadBestCjkFont(int style) {
        String[] winPaths = style == Font.BOLD
                ? new String[]{"C:\\Windows\\Fonts\\msyhbd.ttc", "C:\\Windows\\Fonts\\msyh.ttc", "C:\\Windows\\Fonts\\simhei.ttf"}
                : new String[]{"C:\\Windows\\Fonts\\msyh.ttc", "C:\\Windows\\Fonts\\Deng.ttf", "C:\\Windows\\Fonts\\simhei.ttf"};
        for (String p : winPaths) {
            File f = new File(p);
            if (f.isFile()) {
                try {
                    Font loaded = Font.createFont(Font.TRUETYPE_FONT, f);
                    return loaded.deriveFont(style, 18.0f);
                } catch (Exception ignored) {}
            }
        }
        String[] families = {"Microsoft YaHei UI", "Microsoft YaHei", "PingFang SC", "HarmonyOS Sans SC", "Segoe UI", "SansSerif"};
        for (String fam : families) {
            Font f = new Font(fam, style, 18);
            if (f.canDisplay('永') || fam.equals("SansSerif")) {
                return f;
            }
        }
        return new Font(Font.SANS_SERIF, style, 18);
    }

    private static void applyHints(Graphics2D g2) {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
    }

    public static float guiScale() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getWindow() == null) return 2.0f;
        return Math.max(1.0f, (float) mc.getWindow().getGuiScale());
    }

    private static Font deriveFontPx(int pxSize, boolean bold) {
        ensureInit();
        return (bold ? baseBoldAwt : baseRegularAwt).deriveFont(bold ? Font.BOLD : Font.PLAIN, (float) pxSize);
    }

    private static FontMetrics getMetrics(int pxSize, boolean bold) {
        int key = (pxSize << 1) | (bold ? 1 : 0);
        return METRICS_CACHE.computeIfAbsent(key, k -> {
            Font f = deriveFontPx(pxSize, bold);
            scratchG2d.setFont(f);
            return scratchG2d.getFontMetrics(f);
        });
    }

    private static Glyph getGlyph(char ch, int pxSize, boolean bold) {
        long key = (((long) ch) << 16) | ((long) (pxSize & 0x7FFF) << 1) | (bold ? 1L : 0L);
        Glyph cached = GLYPH_CACHE.get(key);
        if (cached != null) return cached;

        Font font = deriveFontPx(pxSize, bold);
        if (!font.canDisplay(ch)) {
            font = new Font(Font.SANS_SERIF, bold ? Font.BOLD : Font.PLAIN, pxSize);
        }
        scratchG2d.setFont(font);
        FontMetrics fm = scratchG2d.getFontMetrics();
        String s = String.valueOf(ch);
        float adv = (float) fm.getStringBounds(s, scratchG2d).getWidth();
        int gw = Math.max(2, (int) Math.ceil(adv) + 4);
        int gh = Math.max(2, fm.getAscent() + fm.getDescent() + 4);

        if (gw > scratchImg.getWidth() || gh > scratchImg.getHeight()) {
            scratchG2d.dispose();
            scratchImg = new BufferedImage(Math.max(gw + 16, scratchImg.getWidth()),
                    Math.max(gh + 16, scratchImg.getHeight()), BufferedImage.TYPE_INT_ARGB);
            scratchG2d = scratchImg.createGraphics();
            applyHints(scratchG2d);
            scratchG2d.setFont(font);
        }

        // Clear region to transparent
        scratchG2d.setBackground(new Color(255, 255, 255, 0));
        scratchG2d.clearRect(0, 0, gw, gh);
        scratchG2d.setColor(Color.WHITE);
        int baselineY = 1 + fm.getAscent();
        scratchG2d.drawString(s, 1, baselineY);

        AtlasPage page = findOrCreateSpace(gw, gh);
        int u = page.curX;
        int v = page.curY;
        for (int py = 0; py < gh; py++) {
            for (int px = 0; px < gw; px++) {
                int argb = scratchImg.getRGB(px, py);
                int alpha = (argb >>> 24) & 0xFF;
                // Store pure white RGB with smooth anti-aliased alpha so GPU vertex tinting is crystal clean!
                page.image.setPixel(u + px, v + py, (alpha << 24) | 0x00FFFFFF);
            }
        }
        page.curX += gw + PAD;
        page.rowH = Math.max(page.rowH, gh);
        page.dirty = true;

        Glyph g = new Glyph(page.index, u, v, gw, gh, adv, fm.getAscent());
        GLYPH_CACHE.put(key, g);
        return g;
    }

    private static AtlasPage findOrCreateSpace(int w, int h) {
        if (!PAGES.isEmpty()) {
            AtlasPage last = PAGES.get(PAGES.size() - 1);
            if (last.curX + w + PAD <= PAGE_SIZE && last.curY + h + PAD <= PAGE_SIZE) {
                return last;
            }
            if (last.curX + w + PAD > PAGE_SIZE && last.curY + last.rowH + PAD + h + PAD <= PAGE_SIZE) {
                last.curX = PAD;
                last.curY += last.rowH + PAD;
                last.rowH = 0;
                return last;
            }
        }
        AtlasPage created = new AtlasPage(PAGES.size());
        PAGES.add(created);
        return created;
    }

    private static String stripFormatting(String s) {
        if (s == null || s.indexOf('§') < 0) return s;
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '§' && i + 1 < s.length()) {
                i++;
                continue;
            }
            sb.append(c);
        }
        return sb.toString();
    }

    /**
     * Measures text width in logical GUI coordinates for the given logical font size (default UI body = 9.0f).
     */
    public static float widthF(String text, float logicalSize, boolean bold) {
        if (text == null || text.isEmpty()) return 0.0f;
        String clean = stripFormatting(text);
        float gs = guiScale();
        int pxSize = Math.max(10, Math.round(logicalSize * gs));
        FontMetrics fm = getMetrics(pxSize, bold);
        return (float) (fm.stringWidth(clean) / (double) gs);
    }

    public static int width(String text, float logicalSize, boolean bold) {
        return Math.round(widthF(text, logicalSize, bold));
    }

    public static int width(String text) {
        return width(text, 8.8f, false);
    }

    public static String clipToWidth(String text, float logicalSize, boolean bold, float maxLogicalW) {
        if (text == null || text.isEmpty()) return "";
        if (widthF(text, logicalSize, bold) <= maxLogicalW) return text;
        String ellipsis = "…";
        float ellW = widthF(ellipsis, logicalSize, bold);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (widthF(sb.toString() + c, logicalSize, bold) + ellW > maxLogicalW) {
                break;
            }
            sb.append(c);
        }
        return sb + ellipsis;
    }

    /**
     * Draws razor-sharp 1:1 physical-pixel vector text!
     */
    public static void draw(GuiGraphics g, String text, float logicalX, float logicalY,
                            float logicalSize, int argb, boolean shadow, boolean bold) {
        if (text == null || text.isEmpty()) return;
        ensureInit();
        float gs = guiScale();
        int pxSize = Math.max(10, Math.round(logicalSize * gs));

        // Pre-touch all glyphs and flush dirty pages BEFORE submitting blits
        int len = text.length();
        for (int i = 0; i < len; i++) {
            char c = text.charAt(i);
            if (c == '§' && i + 1 < len) {
                i++;
                continue;
            }
            getGlyph(c, pxSize, bold);
        }
        for (AtlasPage p : PAGES) {
            p.flushIfDirty();
        }

        int baseAlpha = (argb >>> 24) & 0xFF;
        if (baseAlpha == 0) baseAlpha = 0xFF;
        int curColor = (baseAlpha << 24) | (argb & 0x00FFFFFF);

        float startPxX = Math.round(logicalX * gs);
        float startPxY = Math.round((logicalY - 0.5f) * gs);

        var pose = g.pose();
        pose.pushMatrix();
        pose.scale(1.0f / gs, 1.0f / gs);

        if (shadow) {
            int shAlpha = Math.round(baseAlpha * 0.55f);
            int shCol = (shAlpha << 24) | 0x0005070C;
            int shOff = Math.max(1, Math.round(gs * 0.45f));
            emitRun(g, text, startPxX + shOff, startPxY + shOff, pxSize, bold, shCol, true);
        }
        emitRun(g, text, startPxX, startPxY, pxSize, bold, curColor, false);

        pose.popMatrix();
    }

    private static void emitRun(GuiGraphics g, String text, float startX, float startY,
                                int pxSize, boolean bold, int initialArgb, boolean forceColor) {
        float penX = startX;
        int col = initialArgb;
        int alphaBits = initialArgb & 0xFF000000;
        int len = text.length();

        for (int i = 0; i < len; i++) {
            char c = text.charAt(i);
            if (c == '§' && i + 1 < len) {
                char code = Character.toLowerCase(text.charAt(++i));
                if (!forceColor) {
                    int mcRgb = mcCodeToRgb(code, initialArgb & 0x00FFFFFF);
                    col = alphaBits | mcRgb;
                }
                continue;
            }
            Glyph gl = getGlyph(c, pxSize, bold);
            if (c != ' ' && gl.w > 0 && gl.h > 0) {
                AtlasPage page = PAGES.get(gl.pageIdx);
                int drawX = Math.round(penX) - 1;
                int drawY = Math.round(startY);
                g.blit(RenderPipelines.GUI_TEXTURED, page.texId,
                        drawX, drawY,
                        (float) gl.u, (float) gl.v,
                        gl.w, gl.h,
                        gl.w, gl.h,
                        PAGE_SIZE, PAGE_SIZE,
                        col);
            }
            penX += gl.advancePx;
        }
    }

    public static void drawCentered(GuiGraphics g, String text, float centerX, float logicalY,
                                    float logicalSize, int argb, boolean shadow, boolean bold) {
        float w = widthF(text, logicalSize, bold);
        draw(g, text, centerX - w * 0.5f, logicalY, logicalSize, argb, shadow, bold);
    }

    private static int mcCodeToRgb(char c, int fallbackRgb) {
        return switch (c) {
            case '0' -> 0x000000;
            case '1' -> 0x1D4ED8;
            case '2' -> 0x16A34A;
            case '3' -> 0x0891B2;
            case '4' -> 0xDC2626;
            case '5' -> 0x9333EA;
            case '6' -> 0xF59E0B;
            case '7' -> 0x94A3B8;
            case '8' -> 0x475569;
            case '9' -> 0x60A5FA;
            case 'a' -> 0x4ADE80;
            case 'b' -> 0x38BDF8;
            case 'c' -> 0xF87171;
            case 'd' -> 0xF472B6;
            case 'e' -> 0xFACC15;
            case 'f', 'r' -> fallbackRgb;
            default -> fallbackRgb;
        };
    }
}
