package com.ye.mod.ui;

import com.ye.mod.core.ConfigManager;
import com.ye.mod.core.FriendManager;
import com.ye.mod.core.Module;
import com.ye.mod.modules.hud.HudElementModule;
import com.ye.mod.modules.render.AccessoriesModule;
import com.ye.mod.modules.render.BodyScaleModule;
import com.ye.mod.modules.render.CustomCapeModule;
import com.ye.mod.modules.render.CustomSkinModule;
import com.ye.mod.modules.render.DragonWingsModule;
import com.ye.mod.modules.render.EntityEspModule;
import com.ye.mod.modules.render.FlightModelModule;
import com.ye.mod.modules.render.LavaEspModule;
import com.ye.mod.modules.render.StandBuddyModule;
import com.ye.mod.modules.render.SwingSpeedModule;
import com.ye.mod.modules.visual.AmbienceModule;
import com.ye.mod.modules.visual.CinematicBarsModule;
import com.ye.mod.modules.visual.CrosshairModule;
import com.ye.mod.modules.visual.DamagePopupsModule;
import com.ye.mod.modules.visual.GlobalColorModule;
import com.ye.mod.modules.visual.HealthPlatesModule;
import com.ye.mod.modules.visual.HitCrosshairModule;
import com.ye.mod.modules.visual.HitFeedbackModule;
import com.ye.mod.modules.visual.IdleWallpaperModule;
import com.ye.mod.modules.visual.LowHpVignetteModule;
import com.ye.mod.modules.visual.SinCityFilterModule;
import com.ye.mod.modules.visual.SoulAscendModule;
import com.ye.mod.modules.visual.SoulRingsModule;
import com.ye.mod.modules.visual.SpeedLinesModule;
import com.ye.mod.modules.visual.StepDecalsModule;
import com.ye.mod.modules.visual.VoxelShatterModule;
import com.ye.mod.modules.world.AutoLogModule;
import com.ye.mod.render.YeHighResTextures;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Real 3D Live Character & High-DPI Texture Showcase Renderer:
 * - Renders the actual live 3D rotating player (with Wings, Mounts, Stands, Soul Rings, Skins, Capes, Accessories)
 *   right inside the UI preview card via InventoryScreen.renderEntityInInventoryFollowsMouse!
 * - Uses 1:1 physical-pixel YeHdFont and smooth rounded outlines (zero crude placeholder rectangles!).
 */
public final class YePreviewRenderer {
    public static boolean isPreviewingWings = false;
    public static boolean isPreviewingMount = false;

    private YePreviewRenderer() {}

    private static boolean is3dCharacterModule(Module m) {
        return m instanceof DragonWingsModule || m instanceof FlightModelModule
                || m instanceof AccessoriesModule || m instanceof StandBuddyModule
                || m instanceof CustomSkinModule || m instanceof CustomCapeModule
                || m instanceof BodyScaleModule || m instanceof SwingSpeedModule
                || m instanceof SoulRingsModule;
    }

    public static int renderModulePreviewCard(GuiGraphics g, Font unusedFont, Module m, int x, int y, int w) {
        YeHighResTextures.ensureInit();
        boolean is3d = is3dCharacterModule(m);
        boolean supported = is3d || m instanceof AmbienceModule || m instanceof SinCityFilterModule
                || m instanceof CrosshairModule || m instanceof HitCrosshairModule
                || m instanceof HitFeedbackModule || m instanceof StepDecalsModule
                || m instanceof SpeedLinesModule || m instanceof VoxelShatterModule
                || m instanceof SoulAscendModule || m instanceof IdleWallpaperModule
                || m instanceof CinematicBarsModule || m instanceof LowHpVignetteModule
                || m instanceof HealthPlatesModule || m instanceof DamagePopupsModule
                || m instanceof GlobalColorModule || m instanceof AutoLogModule
                || m instanceof EntityEspModule || m instanceof HudElementModule;
        if (!supported) return 0;

        int h = is3d ? 58 : 44;
        AppleUiRenderer.drawRoundedRect(g, x, y + 1, w, h, 7, 0x30000000);
        AppleUiRenderer.drawRoundedRect(g, x, y, w, h, 7, AppleUiRenderer.lightTheme ? 0xFFF1F5FB : 0xFF131824);
        AppleUiRenderer.drawRoundedOutline(g, x, y, w, h, 7, 1.0f, AppleUiRenderer.borderSubtle());

        int px = x + 6;
        int py = y + 4;
        int pw = is3d ? 82 : 68;
        int ph = h - 8;
        AppleUiRenderer.drawRoundedRect(g, px, py, pw, ph, 5, 0xFF090D16);
        AppleUiRenderer.drawRoundedOutline(g, px, py, pw, ph, 5, 1.0f, 0x26FFFFFF);

        int cx = px + pw / 2;
        int cy = py + ph / 2;
        long now = System.currentTimeMillis();
        String caption = m.getDescription();
        Minecraft mc = Minecraft.getInstance();

        if (is3d && mc.player != null) {
            boolean prevEnabled = m.isEnabled();
            try {
                isPreviewingWings = (m instanceof DragonWingsModule);
                isPreviewingMount = (m instanceof FlightModelModule);
                float lookX = cx + (float) Math.sin(now / 650.0) * 38.0f;
                float lookY = cy - 6.0f + (float) Math.cos(now / 900.0) * 8.0f;
                int modelScale = (m instanceof FlightModelModule || m instanceof StandBuddyModule || m instanceof SoulRingsModule) ? 16 : 21;
                InventoryScreen.renderEntityInInventoryFollowsMouse(
                        g, px + 2, py + 2, px + pw - 2, py + ph - 2,
                        modelScale, 0.08f, lookX, lookY, mc.player
                );
            } catch (Throwable ignored) {
            } finally {
                isPreviewingWings = false;
                isPreviewingMount = false;
                if (m.isEnabled() != prevEnabled) m.setEnabled(prevEnabled, true);
            }

            if (m instanceof DragonWingsModule wm) {
                caption = wm.wingCategory.get().label + " · " + (wm.onlyWhenGliding.get() ? "仅滑翔展开" : wm.wingColor.get().label);
            } else if (m instanceof FlightModelModule fm) {
                caption = "3D实机坐骑: " + fm.preset.get().label;
            } else if (m instanceof StandBuddyModule sb) {
                caption = "3D替身模型: " + sb.modelType.get().label;
            } else if (m instanceof SoulRingsModule sr) {
                caption = (sr.customMode.get() ? "独立自定义" : sr.rank.get().label) + " · " + Math.round(sr.ringCount.get()) + "道魂环";
            } else if (m instanceof AccessoriesModule am) {
                caption = "头饰:" + am.headItem.get().label + " · 面饰:" + am.faceItem.get().label;
            }
        } else if (m instanceof AmbienceModule am) {
            g.enableScissor(px + 1, py + 1, px + pw - 1, py + ph - 1);
            g.fillGradient(px + 1, py + 1, px + pw - 1, py + ph - 1, am.getSkyArgb(), am.getHorizonArgb());
            if (am.showStars.get()) {
                for (int i = 0; i < 9; i++) {
                    int sx = px + 6 + (i * 13) % (pw - 12);
                    int sy = py + 4 + (i * 7) % (ph / 2);
                    g.fill(sx, sy, sx + 1, sy + 1, 0xCCFFFFFF);
                }
            }
            g.disableScissor();
            caption = am.theme.get().label + " · 天顶至地平线双色散射";
        } else if (m instanceof SinCityFilterModule fm) {
            SinCityFilterModule.FilterPreset fp = fm.preset.get();
            g.enableScissor(px + 1, py + 1, px + pw - 1, py + ph - 1);
            g.fillGradient(px + 1, py + 1, cx, py + ph - 1, 0xFF38BDF8, 0xFF22C55E);
            g.fillGradient(cx, py + 1, px + pw - 1, py + ph - 1, fp.previewCol1, fp.previewCol2);
            g.fill(cx, py + 1, cx + 1, py + ph - 1, 0xEEFFFFFF);
            g.disableScissor();
            caption = "无摩尔纹光学滤镜 · " + fp.label;
        } else if (m instanceof CrosshairModule cm) {
            CrosshairModule.renderAtCenter(g, cm.preset.get(), cx, cy, cm.getActiveArgb(), cm.outline.get());
            caption = cm.preset.get().label + " · 像素级对称准星";
        } else if (m instanceof HitCrosshairModule hm) {
            int hRgb = GlobalColorModule.resolveRgb(hm.syncGlobalColor.get(), hm.color.get().rgb, 3);
            HitCrosshairModule.renderSymmetricX(g, cx, cy, 3, (int) Math.round(hm.size.get()),
                    0xFF000000 | hRgb, hm.outline.get() ? 0xAA000000 : 0);
            caption = "正中央对称命中反馈 · " + hm.color.get().label;
        } else if (m instanceof HitFeedbackModule hf) {
            int col = 0xFF000000 | GlobalColorModule.resolveRgb(hf.syncGlobalColor.get(), hf.preset.get().outerRgb, 7);
            g.blit(RenderPipelines.GUI_TEXTURED, YeHighResTextures.HIT_SHOCKWAVE_TEX,
                    cx - 14, cy - 14, 0f, 0f, 28, 28, 256, 256, 256, 256, col);
            caption = hf.preset.get().label + " · 音效:" + hf.hitSoundType.get().label;
        } else if (m instanceof StepDecalsModule sd) {
            int outer = GlobalColorModule.resolveArgb(sd.syncGlobalColor.get(), sd.style.get().outerArgb, 6);
            int core = sd.syncGlobalColor.get() ? 0xFFFFFFFF : sd.style.get().coreArgb;
            AppleUiRenderer.drawRoundedOutline(g, cx - 12, cy - 12, 24, 24, 2, 2.0f, outer);
            AppleUiRenderer.drawRoundedRect(g, cx - 5, cy - 5, 10, 10, 2, core);
            caption = "立体发光方块矩阵 · " + sd.style.get().label;
        } else if (m instanceof SpeedLinesModule sl) {
            int col = 0xFF000000 | GlobalColorModule.resolveRgb(sl.syncGlobalColor.get(), sl.color.get().rgb, 4);
            var pose = g.pose();
            for (int i = 0; i < 8; i++) {
                pose.pushMatrix();
                pose.translate(cx, cy);
                pose.rotate((float) (i * Math.PI / 4.0));
                g.fill(9, 0, 22, 1, col);
                pose.popMatrix();
            }
            caption = "连续锋利速度流光 · " + sl.color.get().label;
        } else if (m instanceof VoxelShatterModule) {
            int[] cols = {0xFF4ADE80, 0xFF38BDF8, 0xFFFACC15, 0xFFEF4444};
            for (int i = 0; i < 6; i++) {
                float off = (float) Math.sin(now / 180.0 + i) * 4.0f;
                AppleUiRenderer.drawRoundedRect(g, cx - 18 + i * 6, cy - 3 + off, 5, 5, 1, cols[i % cols.length]);
            }
            caption = "144/240Hz 满帧连续物理爆散";
        } else if (m instanceof SoulAscendModule) {
            g.blit(RenderPipelines.GUI_TEXTURED, YeHighResTextures.SOUL_WISP_TEX,
                    cx - 10, cy - 13, 0f, 0f, 20, 26, 128, 128, 128, 128, 0xFF7DD3FC);
            caption = "击杀目标魂火升空";
        } else if (m instanceof GlobalColorModule gc) {
            AppleUiRenderer.drawRoundedRect(g, px + 6, py + 6, 16, ph - 12, 4, gc.getCurrentArgb(0));
            AppleUiRenderer.drawRoundedRect(g, px + 26, py + 6, 16, ph - 12, 4, gc.getCurrentArgb(2));
            AppleUiRenderer.drawRoundedRect(g, px + 46, py + 6, 16, ph - 12, 4, gc.getCurrentArgb(4));
            caption = gc.mode.get().label + " · 已联动 " + GlobalColorModule.countSyncedModules() + " 个模块";
        } else if (m instanceof AutoLogModule al) {
            g.renderItem(new ItemStack(Items.TOTEM_OF_UNDYING), cx - 8, cy - 8);
            caption = al.buildFormulaSummary();
        } else if (m instanceof EntityEspModule esp) {
            int col = GlobalColorModule.resolveArgb(esp.syncGlobalColor.get(), 0xFFFF3B5C, 0);
            if (mc.player != null) {
                InventoryScreen.renderEntityInInventoryFollowsMouse(g, px + 4, py + 2, px + pw - 4, py + ph - 2, 14, 0.05f, cx + 10, cy - 5, mc.player);
            }
            if (esp.style.get().use2d) {
                EntityEspModule.drawTacticalCorners(g, cx - 11, cy - 13, 22, 26, col);
            }
            if (esp.style.get().use3d) {
                AppleUiRenderer.drawRoundedOutline(g, cx - 9, cy - 12, 18, 24, 1, 1.2f, col);
            }
            caption = esp.style.get().label + " · 白名单:" + FriendManager.count() + "人";
        } else if (m instanceof HudElementModule hm) {
            int col = hm.resolvePrimaryColor();
            AppleUiRenderer.drawRoundedRect(g, px + 6, py + 7, pw - 12, ph - 14, 4, 0xCC111726);
            AppleUiRenderer.drawCenteredSmooth(g, hm.getName(), cx, cy - 4, 8.2f, col, true);
            caption = ConfigManager.tr("F10左键自由拖拽吸附 · 右键直达此面板", "Left-drag to move · Right-click to configure");
        } else if (m instanceof HealthPlatesModule hp) {
            AppleUiRenderer.drawRoundedRect(g, cx - 22, cy - 3, 44, 6, 3, 0xFF1E293B);
            AppleUiRenderer.drawRoundedRect(g, cx - 22, cy - 3, 32, 6, 3, 0xFF22C55E);
            caption = hp.posMode.get().label + " · " + hp.styleMode.get().label;
        } else if (m instanceof DamagePopupsModule dp) {
            YeHdFont.drawCentered(g, "-8.5", cx, cy - 6, 9.5f, 0xFFEF4444, true, true);
            caption = dp.mode.get().label + (dp.showCombo.get() ? " · 显示连击" : "");
        } else {
            YeHdFont.drawCentered(g, m.getName(), cx, cy - 4, 8.5f, ConfigManager.getAccentColor(), true, true);
        }

        float tx = px + pw + 9;
        float maxTw = w - (tx - x) - 8;
        String hdr = (m instanceof AutoLogModule)
                ? ConfigManager.tr("触发逻辑公式 · 好友白名单 " + FriendManager.count() + " 人", "Trigger Formula · Friends: " + FriendManager.count())
                : (is3d ? ConfigManager.tr("3D 实机动态预览", "Live 3D Preview") : ConfigManager.tr("效果预览", "Live Preview"));
        YeHdFont.draw(g, hdr, tx, py + (is3d ? 10 : 5), 8.0f, AppleUiRenderer.textSecondary(), false, false);
        YeHdFont.draw(g, YeHdFont.clipToWidth(caption, 8.8f, true, maxTw), tx, py + (is3d ? 25 : 19), 8.8f, AppleUiRenderer.textPrimary(), false, true);
        return h + 5;
    }

    public static int renderOptionSwatch(GuiGraphics g, Enum<?> opt, int x, int y, int sz) {
        if (opt instanceof CrosshairModule.Preset cp) {
            AppleUiRenderer.drawRoundedRect(g, x - 1, y - 1, 16, 16, 3, 0xFF070A12);
            CrosshairModule.renderAtCenter(g, cp, x + 7, y + 7, 0xFFFFFFFF, false);
            return 19;
        }
        if (opt instanceof DragonWingsModule.WingStyle ws) {
            Identifier tex = Identifier.fromNamespaceAndPath("ye-mod", ws.texPath);
            g.blit(RenderPipelines.GUI_TEXTURED, tex, x, y, 0.0f, 14.0f, sz, sz, 64, 34, 64, 64);
            return sz + 5;
        }
        if (opt instanceof DragonWingsModule.WingColor wc) {
            AppleUiRenderer.drawCircle(g, x, y, sz, wc.tintArgb);
            return sz + 5;
        }
        if (opt instanceof SinCityFilterModule.FilterPreset fp) {
            AppleUiRenderer.drawRoundedRect(g, x, y, sz, sz, 3, fp.previewCol1);
            return sz + 5;
        }
        if (opt instanceof StandBuddyModule.StandModelType sm) {
            AppleUiRenderer.drawCircle(g, x, y, sz, sm.accentColor);
            return sz + 5;
        }
        if (opt instanceof AmbienceModule.Theme t) {
            AppleUiRenderer.drawRoundedRect(g, x, y, sz, sz, 3, t.horizonArgb);
            return sz + 5;
        }
        if (opt instanceof SoulRingsModule.SingleColor sc) {
            AppleUiRenderer.drawCircle(g, x, y, sz, 0xFF000000 | sc.rgb);
            return sz + 5;
        }
        if (opt instanceof StepDecalsModule.Style st) {
            AppleUiRenderer.drawRoundedRect(g, x, y, sz, sz, 3, st.outerArgb);
            return sz + 5;
        }
        if (opt instanceof CrosshairModule.ColorPreset cp) {
            AppleUiRenderer.drawCircle(g, x, y, sz, cp.argb);
            return sz + 5;
        }
        if (opt instanceof HitCrosshairModule.ColorPreset hp) {
            AppleUiRenderer.drawCircle(g, x, y, sz, 0xFF000000 | hp.rgb);
            return sz + 5;
        }
        if (opt instanceof SpeedLinesModule.ColorPreset sp) {
            AppleUiRenderer.drawCircle(g, x, y, sz, 0xFF000000 | sp.rgb);
            return sz + 5;
        }
        if (opt instanceof HudElementModule.ColorStyle cs) {
            AppleUiRenderer.drawCircle(g, x, y, sz, cs.argb);
            return sz + 5;
        }
        if (opt instanceof GlobalColorModule.PresetPalette gp) {
            AppleUiRenderer.drawCircle(g, x, y, sz, gp.argb);
            return sz + 5;
        }
        return 0;
    }
}
