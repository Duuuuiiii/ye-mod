package com.ye.mod.ui;

import com.ye.mod.core.Module;
import com.ye.mod.modules.render.AccessoriesModule;
import com.ye.mod.modules.render.BodyScaleModule;
import com.ye.mod.modules.render.DragonWingsModule;
import com.ye.mod.modules.render.FlightModelModule;
import com.ye.mod.modules.render.StandBuddyModule;
import com.ye.mod.modules.visual.AmbienceModule;
import com.ye.mod.modules.visual.BlackHoleSkyModule;
import com.ye.mod.modules.visual.CinematicBarsModule;
import com.ye.mod.modules.visual.CrosshairModule;
import com.ye.mod.modules.visual.DamagePopupsModule;
import com.ye.mod.modules.visual.HealthPlatesModule;
import com.ye.mod.modules.visual.HitCrosshairModule;
import com.ye.mod.modules.visual.HitFeedbackModule;
import com.ye.mod.modules.visual.HudModule;
import com.ye.mod.modules.visual.IdleWallpaperModule;
import com.ye.mod.modules.visual.LowHpVignetteModule;
import com.ye.mod.modules.visual.SinCityFilterModule;
import com.ye.mod.modules.visual.SoulAscendModule;
import com.ye.mod.modules.visual.SoulRingsModule;
import com.ye.mod.modules.visual.SpeedLinesModule;
import com.ye.mod.modules.visual.StepDecalsModule;
import com.ye.mod.modules.visual.VoxelShatterModule;
import com.ye.mod.modules.world.WthitModule;
import com.ye.mod.render.YeHighResTextures;
import com.ye.mod.render.wings.YePvpDragonWingsModel;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Renders high-resolution live preview cards for ALL visual, cosmetic, and HUD modules,
 * plus crisp swatches & mini crosshair previews in dropdowns.
 */
public final class YePreviewRenderer {
    private YePreviewRenderer() {}

    public static int renderModulePreviewCard(GuiGraphics g, Font font, Module m, int x, int y, int w) {
        YeHighResTextures.ensureInit();
        boolean supported = m instanceof DragonWingsModule || m instanceof AccessoriesModule
                || m instanceof FlightModelModule || m instanceof StandBuddyModule
                || m instanceof BodyScaleModule || m instanceof SoulRingsModule
                || m instanceof AmbienceModule || m instanceof SinCityFilterModule
                || m instanceof BlackHoleSkyModule || m instanceof CrosshairModule
                || m instanceof HitCrosshairModule || m instanceof HitFeedbackModule
                || m instanceof StepDecalsModule || m instanceof SpeedLinesModule
                || m instanceof VoxelShatterModule || m instanceof SoulAscendModule
                || m instanceof IdleWallpaperModule || m instanceof CinematicBarsModule
                || m instanceof LowHpVignetteModule || m instanceof HudModule
                || m instanceof WthitModule || m instanceof HealthPlatesModule
                || m instanceof DamagePopupsModule;
        if (!supported) return 0;

        int h = 44;
        AppleUiRenderer.drawRoundedRect(g, x, y + 1, w, h + 1, 7, 0x44000000);
        AppleUiRenderer.drawRoundedRect(g, x, y, w, h, 7, 0xE80D111B);
        g.fill(x + 6, y, x + w - 6, y + 1, 0x2EFFFFFF);
        g.renderOutline(x, y, w, h, 0x28FFFFFF);

        int px = x + 6;
        int py = y + 4;
        int pw = 66;
        int ph = 36;
        AppleUiRenderer.drawRoundedRect(g, px, py, pw, ph, 5, 0xFF060910);
        g.renderOutline(px, py, pw, ph, 0x33FFFFFF);

        int cx = px + pw / 2;
        int cy = py + ph / 2;
        long now = System.currentTimeMillis();
        String caption = m.getDescription();

        if (m instanceof DragonWingsModule wm) {
            int tint = wm.getTintColor(now);
            if (wm.wingCategory.get() == DragonWingsModule.WingCategory.PVP_DRAGON) {
                g.blit(RenderPipelines.GUI_TEXTURED, YePvpDragonWingsModel.PVP_DRAGON_TEX,
                        px + 10, py + 3, 0f, 80f, 22, 28, 100, 180, 300, 300, tint);
                g.blit(RenderPipelines.GUI_TEXTURED, YePvpDragonWingsModel.PVP_DRAGON_TEX,
                        px + 34, py + 3, 100f, 80f, 22, 28, -100, 180, 300, 300, tint);
                caption = wm.wingCategory.get().label + " · " + wm.wingColor.get().label;
            } else {
                Identifier tex = Identifier.fromNamespaceAndPath("ye-mod", wm.wingStyle.get().texPath);
                g.blit(RenderPipelines.GUI_TEXTURED, tex, px + 5, py + 3, 0.0f, 14.0f, 56, 30, 64, 34, 64, 64, tint);
                caption = wm.wingCategory.get().label + " · " + wm.wingStyle.get().label;
            }
        } else if (m instanceof StandBuddyModule sb) {
            StandBuddyModule.StandModelType mt = sb.modelType.get();
            // Aura glow behind portrait
            AppleUiRenderer.drawCircle(g, cx - 15, cy - 15, 30, (0x55 << 24) | (mt.accentColor & 0x00FFFFFF));
            if (mt != StandBuddyModule.StandModelType.PLAYER_AVATAR) {
                Identifier tex = Identifier.fromNamespaceAndPath("ye-mod", mt.texPath);
                // Blit 8x8 Stand face from 128x128 entity texture at UV (8, 8)
                g.blit(RenderPipelines.GUI_TEXTURED, tex, cx - 10, cy - 10, 8.0f, 8.0f, 20, 20, 8, 8, 128, 128);
            } else {
                g.fill(cx - 8, cy - 9, cx + 8, cy + 9, 0xCC38BDF8);
            }
            caption = "替身模型: " + mt.label;
        } else if (m instanceof SinCityFilterModule fm) {
            SinCityFilterModule.FilterPreset fp = fm.preset.get();
            // Split-view before/after stylized shader preview
            g.fillGradient(px + 2, py + 2, cx, py + ph - 2, 0xFF38BDF8, 0xFF22C55E);
            g.fillGradient(cx, py + 2, px + pw - 2, py + ph - 2, fp.previewCol1, fp.previewCol2);
            g.fill(cx, py + 2, cx + 1, py + ph - 2, 0xFFFFFFFF);
            if (fp == SinCityFilterModule.FilterPreset.SIN_CITY) {
                g.fill(cx + 8, cy - 4, cx + 18, cy + 6, 0xFFEF4444);
            } else if (fp == SinCityFilterModule.FilterPreset.BORDERLANDS || fp == SinCityFilterModule.FilterPreset.JOJO) {
                g.renderOutline(cx + 6, cy - 8, 18, 16, 0xFF050508);
            } else if (fp == SinCityFilterModule.FilterPreset.RETRO_CRT) {
                for (int sy = py + 3; sy < py + ph - 2; sy += 3) g.fill(cx + 1, sy, px + pw - 2, sy + 1, 0x66000000);
            }
            caption = "GLSL滤镜: " + fp.label;
        } else if (m instanceof BlackHoleSkyModule bh) {
            int mid = 0xFF000000 | (bh.theme.get().midCol & 0x00FFFFFF);
            int inner = 0xFF000000 | (bh.theme.get().innerCol & 0x00FFFFFF);
            g.blit(RenderPipelines.GUI_TEXTURED, YeHighResTextures.BLACK_HOLE_DISK_TEX,
                    cx - 24, cy - 8, 0f, 0f, 48, 16, 512, 512, 512, 512, mid);
            g.blit(RenderPipelines.GUI_TEXTURED, YeHighResTextures.BLACK_HOLE_CORE_TEX,
                    cx - 13, cy - 13, 0f, 0f, 26, 26, 512, 512, 512, 512, inner);
            caption = "512×512 " + bh.theme.get().label;
        } else if (m instanceof SoulRingsModule sr) {
            int count = Math.min(3, Math.max(1, (int) Math.round(sr.ringCount.get())));
            for (int i = 0; i < count; i++) {
                int rw = 24 + i * 10;
                int rh = 10 + i * 4;
                int col = 0xFF000000 | sr.pickRingRgb(i);
                g.blit(RenderPipelines.GUI_TEXTURED, YeHighResTextures.RING_ARRAY_TEX,
                        cx - rw / 2, cy - rh / 2 + (i - 1) * 3, 0f, 0f, rw, rh, 512, 512, 512, 512, col);
            }
            caption = (sr.customMode.get() ? "独立自定义" : sr.rank.get().label) + " ×" + Math.round(sr.ringCount.get());
        } else if (m instanceof StepDecalsModule sd) {
            Identifier tex = switch (sd.style.get()) {
                case LAVA_SCORCH -> YeHighResTextures.STEP_LAVA_TEX;
                case CYBER_RUNE -> YeHighResTextures.STEP_CYBER_TEX;
                case VOID_ASTROLABE -> YeHighResTextures.STEP_VOID_TEX;
                case STAR_SIGIL -> YeHighResTextures.STEP_STAR_TEX;
            };
            g.blit(RenderPipelines.GUI_TEXTURED, tex, cx - 15, cy - 15, 0f, 0f, 30, 30, 256, 256, 256, 256);
            caption = "地面印记: " + sd.style.get().label;
        } else if (m instanceof HitFeedbackModule hf) {
            int col = 0xFF000000 | hf.preset.get().outerRgb;
            g.blit(RenderPipelines.GUI_TEXTURED, YeHighResTextures.HIT_SHOCKWAVE_TEX,
                    cx - 15, cy - 15, 0f, 0f, 30, 30, 256, 256, 256, 256, col);
            caption = "冲击特效: " + hf.preset.get().label;
        } else if (m instanceof CrosshairModule cm) {
            CrosshairModule.renderAtCenter(g, cm.preset.get(), cx, cy, cm.color.get().argb, cm.outline.get());
            caption = cm.preset.get().label + " · 四向像素对称";
        } else if (m instanceof HitCrosshairModule hm) {
            HitCrosshairModule.renderSymmetricX(g, cx, cy, 3, (int) Math.round(hm.size.get()),
                    0xFF000000 | hm.color.get().rgb, hm.outline.get() ? 0xAA000000 : 0);
            caption = "正中对称 X 标记 · " + hm.color.get().label;
        } else if (m instanceof AmbienceModule am) {
            int sky = am.getSkyArgb();
            var fog = am.getFogColor();
            int fogArgb = 0xFF000000 | ((Math.round(fog.x * 255) & 0xFF) << 16)
                    | ((Math.round(fog.y * 255) & 0xFF) << 8)
                    | (Math.round(fog.z * 255) & 0xFF);
            g.fillGradient(px + 2, py + 2, px + pw - 2, py + ph - 2, sky, fogArgb);
            caption = am.theme.get().label + (am.overridePacks.get() ? " · 覆盖材质包" : "");
        } else if (m instanceof AccessoriesModule am) {
            g.fill(cx - 7, cy - 5, cx + 7, cy + 9, 0xFFE0AC87);
            g.fill(cx - 7, cy - 5, cx + 7, cy - 2, 0xFF3B2616);
            if (am.headItem.get() != AccessoriesModule.HeadItem.NONE) g.fill(cx - 8, cy - 11, cx + 8, cy - 6, 0xFFFACC15);
            if (am.faceItem.get() != AccessoriesModule.FaceItem.NONE) g.fill(cx - 6, cy - 1, cx + 6, cy + 3, 0xFF22D3EE);
            if (am.tailItem.get() != AccessoriesModule.TailItem.NONE) g.fill(cx + 9, cy + 2, cx + 16, cy + 8, 0xFFF472B6);
            caption = am.headItem.get().label + " / " + am.faceItem.get().label;
        } else if (m instanceof FlightModelModule fm) {
            int col = switch (fm.preset.get()) {
                case MINI_DRAGON -> 0xFFA855F7;
                case MAGIC_BROOM -> 0xFFD97706;
                case SWORD_SURF -> 0xFF38BDF8;
                case MINI_PHANTOM -> 0xFF6366F1;
                default -> 0xFF4ADE80;
            };
            g.fill(cx - 18, cy + 3, cx + 18, cy + 6, col);
            g.fill(cx - 4, cy - 8, cx + 4, cy + 3, 0xFFE2E8F0);
            caption = "坐骑: " + fm.preset.get().label;
        } else if (m instanceof BodyScaleModule bs) {
            g.fill(cx - 14, cy - 3, cx - 8, cy + 10, 0xFF94A3B8);
            g.fill(cx + 4, cy - 11, cx + 14, cy + 10, 0xFF38BDF8);
            caption = "比例: " + bs.preset.get().label;
        } else if (m instanceof SpeedLinesModule sl) {
            int col = 0xFF000000 | sl.color.get().rgb;
            var pose = g.pose();
            for (int i = 0; i < 8; i++) {
                pose.pushMatrix();
                pose.translate(cx, cy);
                pose.rotate((float) (i * Math.PI / 4.0));
                g.fill(10, 0, 22, 1, col);
                pose.popMatrix();
            }
            caption = "连续流光射线 · " + sl.color.get().label;
        } else if (m instanceof VoxelShatterModule) {
            int[] cols = {0xFF4ADE80, 0xFF38BDF8, 0xFFFACC15, 0xFFEF4444};
            for (int i = 0; i < 8; i++) {
                int bx = cx - 16 + (i * 5) % 28;
                int by = cy - 9 + ((i * 7) % 18);
                g.fill(bx, by, bx + 4, by + 4, cols[i % cols.length]);
            }
            caption = "单批次高速碎块爆散";
        } else if (m instanceof SoulAscendModule) {
            g.blit(RenderPipelines.GUI_TEXTURED, YeHighResTextures.SOUL_WISP_TEX,
                    cx - 10, cy - 13, 0f, 0f, 20, 26, 128, 128, 128, 128, 0xFF7DD3FC);
            caption = "灵体魂火摇曳升空";
        } else if (m instanceof IdleWallpaperModule iw) {
            AppleUiRenderer.drawCircle(g, cx - 12, cy - 12, 24, 0x4438BDF8);
            g.fill(cx - 3, cy - 5, cx + 3, cy + 6, 0xFFF8FAFC);
            caption = "运镜: " + iw.cameraStyle.get().label;
        } else if (m instanceof CinematicBarsModule) {
            g.fill(px + 2, py + 2, px + pw - 2, py + 9, 0xFF000000);
            g.fill(px + 2, py + ph - 9, px + pw - 2, py + ph - 2, 0xFF000000);
            g.fill(px + 2, py + 10, px + pw - 2, py + ph - 10, 0xFF1E293B);
        } else if (m instanceof LowHpVignetteModule) {
            g.fillGradient(px + 2, py + 2, px + pw - 2, py + 12, 0xCCEF4444, 0x00EF4444);
            g.fillGradient(px + 2, py + ph - 12, px + pw - 2, py + ph - 2, 0x00EF4444, 0xCCEF4444);
        } else if (m instanceof WthitModule) {
            g.renderItem(new ItemStack(Items.DIAMOND_ORE), px + 6, cy - 8);
            g.fill(px + 26, cy - 5, px + pw - 6, cy - 2, 0xFFF8FAFC);
            g.fill(px + 26, cy + 2, px + pw - 12, cy + 5, 0xFF38BDF8);
            caption = "准星方块图标与挖掘进度";
        } else if (m instanceof HealthPlatesModule hp) {
            if (hp.posMode.get() == HealthPlatesModule.PositionMode.VERTICAL_SIDE) {
                g.fill(cx - 3, cy - 12, cx + 3, cy + 12, 0xFF1E293B);
                g.fill(cx - 3, cy - 4, cx + 3, cy + 12, 0xFF22C55E);
            } else {
                g.fill(cx - 22, cy - 3, cx + 22, cy + 3, 0xFF1E293B);
                g.fill(cx - 22, cy - 3, cx + 10, cy + 3, 0xFF22C55E);
            }
            caption = hp.posMode.get().label + " · " + hp.styleMode.get().label;
        } else if (m instanceof DamagePopupsModule dp) {
            g.drawCenteredString(font, "-8.5", cx, cy - 8, 0xFFEF4444);
            if (dp.showCombo.get()) g.drawCenteredString(font, "4x", cx, cy + 2, 0xFFFACC15);
            caption = dp.mode.get().label + (dp.showCombo.get() ? " + 连击" : "");
        } else if (m instanceof HudModule hm) {
            g.fill(px + 6, py + 6, px + 32, py + 9, hm.palette.get().argb);
            g.fill(px + 6, py + 13, px + 56, py + 17, 0xFF38BDF8);
            g.fill(px + 6, py + 21, px + 46, py + 25, 0xFF4ADE80);
            caption = "药水:" + hm.potionStyle.get().label + " · F10可拖拽吸附";
        }

        int tx = px + pw + 8;
        int maxTw = w - (tx - x) - 8;
        g.drawString(font, "实时预览", tx, py + 5, 0xFF94A3B8, false);
        if (font.width(caption) > maxTw) {
            caption = font.plainSubstrByWidth(caption, maxTw);
        }
        g.drawString(font, caption, tx, py + 19, 0xFFF8FAFC, false);
        return h + 5;
    }

    public static int renderOptionSwatch(GuiGraphics g, Enum<?> opt, int x, int y, int sz) {
        if (opt instanceof CrosshairModule.Preset cp) {
            g.fill(x - 1, y - 1, x + 16, y + 16, 0xFF070A12);
            CrosshairModule.renderAtCenter(g, cp, x + 7, y + 7, 0xFFFFFFFF, false);
            return 18;
        }
        if (opt instanceof DragonWingsModule.WingStyle ws) {
            Identifier tex = Identifier.fromNamespaceAndPath("ye-mod", ws.texPath);
            g.blit(RenderPipelines.GUI_TEXTURED, tex, x, y, 0.0f, 14.0f, sz, sz, 64, 34, 64, 64);
            return sz + 4;
        }
        if (opt instanceof DragonWingsModule.WingColor wc) {
            g.fill(x, y, x + sz, y + sz, wc.tintArgb);
            return sz + 4;
        }
        if (opt instanceof SinCityFilterModule.FilterPreset fp) {
            g.fillGradient(x, y, x + sz, y + sz, fp.previewCol1, fp.previewCol2);
            return sz + 4;
        }
        if (opt instanceof StandBuddyModule.StandModelType sm) {
            g.fill(x, y, x + sz, y + sz, sm.accentColor);
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
        if (opt instanceof SoulRingsModule.SingleColor sc) {
            g.fill(x, y, x + sz, y + sz, 0xFF000000 | sc.rgb);
            return sz + 4;
        }
        if (opt instanceof StepDecalsModule.Style st) {
            g.fill(x, y, x + sz, y + sz, st.previewRgb);
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
        if (opt instanceof HudModule.HudPalette hp) {
            g.fill(x, y, x + sz, y + sz, hp.argb);
            return sz + 4;
        }
        return 0;
    }
}
