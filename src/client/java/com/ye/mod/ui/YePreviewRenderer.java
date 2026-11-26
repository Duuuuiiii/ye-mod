package com.ye.mod.ui;

import com.ye.mod.core.Module;
import com.ye.mod.modules.render.AccessoriesModule;
import com.ye.mod.modules.render.DragonWingsModule;
import com.ye.mod.modules.render.FlightModelModule;
import com.ye.mod.modules.render.StandBuddyModule;
import com.ye.mod.modules.visual.AmbienceModule;
import com.ye.mod.modules.visual.BlackHoleSkyModule;
import com.ye.mod.modules.visual.CinematicBarsModule;
import com.ye.mod.modules.visual.CrosshairModule;
import com.ye.mod.modules.visual.HitCrosshairModule;
import com.ye.mod.modules.visual.LowHpVignetteModule;
import com.ye.mod.modules.visual.SoulRingsModule;
import com.ye.mod.modules.visual.SpeedLinesModule;
import com.ye.mod.modules.visual.StepDecalsModule;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * Renders live visual preview cards in Module Detail and miniature swatches in Dropdowns.
 */
public final class YePreviewRenderer {
    private YePreviewRenderer() {}

    public static int renderModulePreviewCard(GuiGraphics g, Font font, Module m, int x, int y, int w) {
        int h = 38;
        boolean supported = m instanceof DragonWingsModule || m instanceof AccessoriesModule
                || m instanceof CrosshairModule || m instanceof HitCrosshairModule
                || m instanceof AmbienceModule || m instanceof BlackHoleSkyModule
                || m instanceof SoulRingsModule || m instanceof StepDecalsModule
                || m instanceof StandBuddyModule || m instanceof FlightModelModule
                || m instanceof CinematicBarsModule || m instanceof LowHpVignetteModule
                || m instanceof SpeedLinesModule;
        if (!supported) return 0;

        AppleUiRenderer.drawRoundedRect(g, x, y, w, h, 6, 0xCC0B101B);
        g.renderOutline(x, y, w, h, 0x33FFFFFF);
        int px = x + 6;
        int py = y + 3;
        int pw = 56;
        int ph = 32;
        g.fill(px, py, px + pw, py + ph, 0xFF070A12);
        g.renderOutline(px, py, pw, ph, 0x44FFFFFF);

        int cx = px + pw / 2;
        int cy = py + ph / 2;
        long now = System.currentTimeMillis();
        String caption = m.getDescription();

        if (m instanceof DragonWingsModule wm) {
            Identifier tex = Identifier.fromNamespaceAndPath("ye-mod", wm.wingStyle.get().texPath);
            int tint = wm.getTintColor(now);
            g.blit(RenderPipelines.GUI_TEXTURED, tex, px + 4, py + 2, 0.0f, 14.0f, 48, 28, 64, 34, 64, 64, tint);
            caption = wm.wingStyle.get().label + " · " + wm.wingColor.get().name();
        } else if (m instanceof AccessoriesModule am) {
            // Mini Steve head + equipped Head/Face/Tail indicators
            g.fill(cx - 7, cy - 5, cx + 7, cy + 9, 0xFFE0AC87);
            g.fill(cx - 7, cy - 5, cx + 7, cy - 2, 0xFF3B2616);
            if (am.headItem.get() != AccessoriesModule.HeadItem.NONE) {
                g.fill(cx - 8, cy - 11, cx + 8, cy - 6, 0xFFFACC15);
            }
            if (am.faceItem.get() != AccessoriesModule.FaceItem.NONE) {
                g.fill(cx - 6, cy - 1, cx + 6, cy + 3, 0xFF22D3EE);
            }
            if (am.tailItem.get() != AccessoriesModule.TailItem.NONE) {
                g.fill(cx + 9, cy + 2, cx + 16, cy + 8, 0xFFF472B6);
            }
            caption = am.headItem.get().label + " / " + am.faceItem.get().label;
        } else if (m instanceof CrosshairModule cm) {
            int col = cm.color.get().argb;
            switch (cm.preset.get()) {
                case CLASSIC_CROSS -> {
                    g.fill(cx - 7, cy, cx + 8, cy + 1, col);
                    g.fill(cx, cy - 7, cx + 1, cy + 8, col);
                }
                case GAP_CROSS -> {
                    g.fill(cx - 8, cy, cx - 3, cy + 1, col);
                    g.fill(cx + 4, cy, cx + 9, cy + 1, col);
                    g.fill(cx, cy - 8, cx + 1, cy - 3, col);
                    g.fill(cx, cy + 4, cx + 1, cy + 9, col);
                }
                case DOT_RING -> {
                    g.fill(cx - 1, cy - 1, cx + 2, cy + 2, col);
                    g.renderOutline(cx - 6, cy - 6, 13, 13, col);
                }
                case DIAMOND -> {
                    for (int i = 0; i <= 6; i++) {
                        int d = 6 - i;
                        g.fill(cx - i, cy - d, cx - i + 1, cy - d + 1, col);
                        g.fill(cx + i, cy - d, cx + i + 1, cy - d + 1, col);
                        g.fill(cx - i, cy + d, cx - i + 1, cy + d + 1, col);
                        g.fill(cx + i, cy + d, cx + i + 1, cy + d + 1, col);
                    }
                }
                case PLUS_DOT -> {
                    g.fill(cx, cy, cx + 1, cy + 1, col);
                    g.fill(cx - 7, cy, cx - 3, cy + 1, col);
                    g.fill(cx + 4, cy, cx + 8, cy + 1, col);
                    g.fill(cx, cy - 7, cx + 1, cy - 3, col);
                    g.fill(cx, cy + 4, cx + 1, cy + 8, col);
                }
            }
            caption = cm.preset.get().label;
        } else if (m instanceof HitCrosshairModule hm) {
            int col = 0xFF000000 | hm.color.get().rgb;
            for (int i = 0; i < 6; i++) {
                int d = 3 + i;
                g.fill(cx - d - 1, cy - d - 1, cx - d + 1, cy - d + 1, col);
                g.fill(cx + d, cy - d - 1, cx + d + 2, cy - d + 1, col);
                g.fill(cx - d - 1, cy + d, cx - d + 1, cy + d + 2, col);
                g.fill(cx + d, cy + d, cx + d + 2, cy + d + 2, col);
            }
            caption = "对称 X 型命中反馈 (" + hm.color.get().name() + ")";
        } else if (m instanceof AmbienceModule am) {
            int sky = am.getSkyArgb();
            var fog = am.getFogColor();
            int fogArgb = 0xFF000000 | ((Math.round(fog.x * 255) & 0xFF) << 16)
                    | ((Math.round(fog.y * 255) & 0xFF) << 8)
                    | (Math.round(fog.z * 255) & 0xFF);
            g.fillGradient(px + 1, py + 1, px + pw - 1, py + ph - 1, sky, fogArgb);
            caption = am.theme.get().label;
        } else if (m instanceof BlackHoleSkyModule bh) {
            g.renderOutline(cx - 16, cy - 5, 32, 10, bh.theme.get().midCol);
            g.renderOutline(cx - 11, cy - 11, 22, 22, bh.theme.get().innerCol);
            g.fill(cx - 7, cy - 7, cx + 7, cy + 7, 0xFF000000);
            caption = bh.theme.get().label + " · 旋转吸积盘";
        } else if (m instanceof SoulRingsModule sr) {
            int count = Math.min(4, Math.max(1, (int) Math.round(sr.ringCount.get())));
            for (int i = 0; i < count; i++) {
                int r = 5 + i * 3;
                g.renderOutline(cx - r * 2, cy - r / 2, r * 4, r, 0xFF000000 | sr.pickRingRgb(i));
            }
            caption = sr.rank.get().label + " ×" + Math.round(sr.ringCount.get());
        } else if (m instanceof StepDecalsModule sd) {
            int col = switch (sd.style.get()) {
                case GOLDEN_LOTUS -> 0xFFFACC15;
                case FROST_CRACK -> 0xFF67E8F9;
                case LAVA_SCORCH -> 0xFFEA580C;
                case CYBER_RUNE -> 0xFF22D3EE;
            };
            g.renderOutline(cx - 10, cy - 10, 20, 20, col);
            g.renderOutline(cx - 5, cy - 5, 10, 10, col);
            caption = sd.style.get().label;
        } else if (m instanceof StandBuddyModule sb) {
            int col = sb.style.get().tintArgb | 0xCC000000;
            g.fill(cx - 5, cy - 11, cx + 5, cy - 3, col);
            g.fill(cx - 7, cy - 2, cx + 7, cy + 9, col);
            caption = sb.style.get().label;
        } else if (m instanceof FlightModelModule fm) {
            int col = switch (fm.preset.get()) {
                case MINI_DRAGON -> 0xFFA855F7;
                case MAGIC_BROOM -> 0xFFD97706;
                case SWORD_SURF -> 0xFF38BDF8;
                case MINI_PHANTOM -> 0xFF6366F1;
                default -> 0xFF4ADE80;
            };
            g.fill(cx - 16, cy + 3, cx + 16, cy + 6, col);
            g.fill(cx - 4, cy - 9, cx + 4, cy + 3, 0xFFE2E8F0);
            caption = fm.preset.get().label;
        } else if (m instanceof CinematicBarsModule) {
            g.fill(px + 2, py + 2, px + pw - 2, py + 8, 0xFF000000);
            g.fill(px + 2, py + ph - 8, px + pw - 2, py + ph - 2, 0xFF000000);
            g.fill(px + 2, py + 9, px + pw - 2, py + ph - 9, 0xFF1E293B);
        } else if (m instanceof LowHpVignetteModule) {
            g.fillGradient(px + 1, py + 1, px + pw - 1, py + 10, 0xCCEF4444, 0x00EF4444);
            g.fillGradient(px + 1, py + ph - 10, px + pw - 1, py + ph - 1, 0x00EF4444, 0xCCEF4444);
        } else if (m instanceof SpeedLinesModule sl) {
            int col = 0xFF000000 | sl.color.get().rgb;
            for (int i = 0; i < 6; i++) {
                g.fill(px + 3, py + 4 + i * 4, px + 14, py + 5 + i * 4, col);
                g.fill(px + pw - 14, py + 4 + i * 4, px + pw - 3, py + 5 + i * 4, col);
            }
        }

        int tx = px + pw + 8;
        int maxTw = w - (tx - x) - 6;
        String title = "实时效果预览";
        g.drawString(font, title, tx, py + 4, 0xFF94A3B8, false);
        if (font.width(caption) > maxTw) {
            caption = font.plainSubstrByWidth(caption, maxTw);
        }
        g.drawString(font, caption, tx, py + 16, 0xFFF8FAFC, false);
        return h + 4;
    }

    public static int renderOptionSwatch(GuiGraphics g, Enum<?> opt, int x, int y, int sz) {
        if (opt instanceof DragonWingsModule.WingStyle ws) {
            Identifier tex = Identifier.fromNamespaceAndPath("ye-mod", ws.texPath);
            g.blit(RenderPipelines.GUI_TEXTURED, tex, x, y, 0.0f, 14.0f, sz, sz, 64, 34, 64, 64);
            return sz + 4;
        }
        if (opt instanceof DragonWingsModule.WingColor wc) {
            g.fill(x, y, x + sz, y + sz, wc.tintArgb);
            return sz + 4;
        }
        if (opt instanceof AmbienceModule.Theme t) {
            g.fill(x, y, x + sz, y + sz, t.getSkyArgb());
            return sz + 4;
        }
        if (opt instanceof BlackHoleSkyModule.Theme t) {
            g.fill(x, y, x + sz, y + sz, t.getMidCol());
            return sz + 4;
        }
        if (opt instanceof StandBuddyModule.StandStyle ss) {
            g.fill(x, y, x + sz, y + sz, ss.tintArgb | 0xFF000000);
            return sz + 4;
        }
        if (opt instanceof CrosshairModule.ColorPreset cp) {
            g.fill(x, y, x + sz, y + sz, cp.argb);
            return sz + 4;
        }
        if (opt instanceof HitCrosshairModule.ColorPreset hp) {
            g.fill(x, y, x + sz, y + sz, 0xFF000000 | hp.rgb);
            return sz + 4;
        }
        if (opt instanceof SpeedLinesModule.ColorPreset sp) {
            g.fill(x, y, x + sz, y + sz, 0xFF000000 | sp.rgb);
            return sz + 4;
        }
        return 0;
    }
}
