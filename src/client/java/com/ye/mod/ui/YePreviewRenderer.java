package com.ye.mod.ui;

import com.ye.mod.core.ConfigManager;
import com.ye.mod.core.FriendManager;
import com.ye.mod.core.Module;
import com.ye.mod.modules.hud.HudElementModule;
import com.ye.mod.modules.player.FlightSpeedModule;
import com.ye.mod.modules.player.PlayerUiModule;
import com.ye.mod.modules.render.AccessoriesModule;
import com.ye.mod.modules.render.BodyScaleModule;
import com.ye.mod.modules.render.CustomCapeModule;
import com.ye.mod.modules.render.CustomSkinModule;
import com.ye.mod.modules.render.DragonWingsModule;
import com.ye.mod.modules.render.EntityEspModule;
import com.ye.mod.modules.render.FlightModelModule;
import com.ye.mod.modules.render.StandBuddyModule;
import com.ye.mod.modules.render.SwingSpeedModule;
import com.ye.mod.modules.visual.AmbienceModule;
import com.ye.mod.modules.visual.CameraTweaksModule;
import com.ye.mod.modules.visual.CinematicBarsModule;
import com.ye.mod.modules.visual.CrosshairModule;
import com.ye.mod.modules.visual.DamagePopupsModule;
import com.ye.mod.modules.visual.EnchantGlintModule;
import com.ye.mod.modules.visual.GlobalColorModule;
import com.ye.mod.modules.visual.HealthPlatesModule;
import com.ye.mod.modules.visual.HitCrosshairModule;
import com.ye.mod.modules.visual.HitFeedbackModule;
import com.ye.mod.modules.visual.IdleWallpaperModule;
import com.ye.mod.modules.visual.LowHpVignetteModule;
import com.ye.mod.modules.visual.MotionBlurModule;
import com.ye.mod.modules.visual.ShaderPackModule;
import com.ye.mod.modules.visual.SinCityFilterModule;
import com.ye.mod.modules.visual.SoulAscendModule;
import com.ye.mod.modules.visual.SoulRingsModule;
import com.ye.mod.modules.visual.SpeedLinesModule;
import com.ye.mod.modules.visual.StepDecalsModule;
import com.ye.mod.modules.visual.VoxelShatterModule;
import com.ye.mod.modules.world.AutoLogModule;
import com.ye.mod.modules.world.BlockEspModule;
import com.ye.mod.modules.world.BlockSearchModule;
import com.ye.mod.modules.world.StashRadarModule;
import com.ye.mod.render.YeHighResTextures;
import com.ye.mod.render.YeSkyPanoramaRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Live 3D Character & Real Texture Preview Card Renderer:
 * Zero keybind/how-to-operate hints, zero boastful FPS labels — pure live visual previews and clean functional descriptions!
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
        if (!m.isEnabled()) {
            // Only show live preview when the module is actually enabled! Otherwise show clean compact intro bar.
            int h = 24;
            int rad = AppleUiRenderer.isMechanical() ? 0 : 6;
            AppleUiRenderer.drawRoundedRect(g, x, y, w, h, rad, AppleUiRenderer.bgCard());
            AppleUiRenderer.drawRoundedOutline(g, x, y, w, h, rad, 1.0f, AppleUiRenderer.borderSubtle());
            YeHdFont.draw(g, YeHdFont.clipToWidth(m.getDescription(), 8.3f, false, w - 16),
                    x + 8, YeHdFont.vCenterY(y, h, 8.3f), 8.3f, AppleUiRenderer.textSecondary(), false, false);
            return h + 5;
        }
        YeHighResTextures.ensureInit();
        boolean is3d = is3dCharacterModule(m);

        int h = is3d ? 58 : 44;
        int rad = AppleUiRenderer.isMechanical() ? 0 : 7;
        AppleUiRenderer.drawRoundedRect(g, x, y, w, h, rad, AppleUiRenderer.lightTheme ? 0xFFF1F5FB : 0xFF121723);
        AppleUiRenderer.drawRoundedOutline(g, x, y, w, h, rad, 1.0f, AppleUiRenderer.borderSubtle());

        int px = x + 6;
        int py = y + 4;
        int pw = is3d ? 82 : 68;
        int ph = h - 8;
        AppleUiRenderer.drawRoundedRect(g, px, py, pw, ph, AppleUiRenderer.isMechanical() ? 0 : 5, 0xFF090D16);
        AppleUiRenderer.drawRoundedOutline(g, px, py, pw, ph, AppleUiRenderer.isMechanical() ? 0 : 5, 1.0f, 0x26FFFFFF);

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
                caption = wm.wingCategory.get().label + " · " + (wm.onlyWhenGliding.get() ? "滑翔时展开" : wm.wingColor.get().label);
            } else if (m instanceof FlightModelModule fm) {
                caption = "当前坐骑: " + fm.preset.get().label;
            } else if (m instanceof StandBuddyModule sb) {
                caption = "当前替身: " + sb.modelType.get().label;
            } else if (m instanceof SoulRingsModule sr) {
                caption = (sr.customMode.get() ? "分环独立配置" : sr.rank.get().label) + " · " + Math.round(sr.ringCount.get()) + "道魂环";
            } else if (m instanceof AccessoriesModule am) {
                caption = "头饰: " + am.headItem.get().label + " · 面饰: " + am.faceItem.get().label;
            }
        } else if (m instanceof AmbienceModule am) {
            Identifier pano = YeSkyPanoramaRenderer.getOrBuildPanorama(am.skyStyle.get());
            if (pano != null) {
                g.blit(RenderPipelines.GUI_TEXTURED, pano, px + 1, py + 1, 180f, 60f, pw - 2, ph - 2, 320, 180, 1024, 512);
            } else {
                g.fillGradient(px + 1, py + 1, px + pw - 1, py + ph - 1, am.getSkyArgb(), am.getHorizonArgb());
            }
            caption = am.skyStyle.get().label;
        } else if (m instanceof ShaderPackModule sm) {
            g.fillGradient(px + 1, py + 1, px + pw - 1, py + ph - 1, 0xFF1D4ED8, 0xFFF59E0B);
            YeHdFont.drawCentered(g, "Shader", cx, cy - 4, 8.2f, 0xFFFFFFFF, true, true);
            caption = sm.pack.get().label;
        } else if (m instanceof EnchantGlintModule eg) {
            ItemStack sword = new ItemStack(Items.NETHERITE_SWORD);
            g.renderItem(sword, cx - 8, cy - 8);
            caption = eg.glintMode.get().label + " · 亮度 " + String.format("%.2f", eg.brightness.get());
        } else if (m instanceof MotionBlurModule mb) {
            for (int i = -2; i <= 2; i++) {
                AppleUiRenderer.drawRoundedRect(g, cx - 8 + i * 4, cy - 6, 16, 12, 3, AppleUiRenderer.withAlpha(ConfigManager.getAccentColor(), 0.25f + (2 - Math.abs(i)) * 0.22f));
            }
            caption = "动态残影柔化 · 强度 " + Math.round(mb.strength.get() * 100) + "%";
        } else if (m instanceof SinCityFilterModule fm) {
            SinCityFilterModule.FilterPreset fp = fm.preset.get();
            g.fillGradient(px + 1, py + 1, cx, py + ph - 1, 0xFF38BDF8, 0xFF22C55E);
            g.fillGradient(cx, py + 1, px + pw - 1, py + ph - 1, fp.previewCol1, fp.previewCol2);
            g.fill(cx, py + 1, cx + 1, py + ph - 1, 0xEEFFFFFF);
            caption = "当前滤镜: " + fp.label;
        } else if (m instanceof CrosshairModule cm) {
            CrosshairModule.renderAtCenter(g, cm.preset.get(), cx, cy, cm.getActiveArgb(), cm.outline.get());
            caption = cm.preset.get().label;
        } else if (m instanceof HitCrosshairModule hm) {
            int hRgb = GlobalColorModule.resolveRgb(hm.syncGlobalColor.get(), hm.color.get().rgb, 3);
            HitCrosshairModule.renderSymmetricX(g, cx, cy, 3, (int) Math.round(hm.size.get()),
                    0xFF000000 | hRgb, hm.outline.get() ? 0xAA000000 : 0);
            caption = "命中反馈准星 · " + hm.color.get().label;
        } else if (m instanceof HitFeedbackModule hf) {
            int col = 0xFF000000 | GlobalColorModule.resolveRgb(hf.syncGlobalColor.get(), hf.preset.get().outerRgb, 7);
            g.blit(RenderPipelines.GUI_TEXTURED, YeHighResTextures.HIT_SHOCKWAVE_TEX,
                    cx - 14, cy - 14, 0f, 0f, 28, 28, 256, 256, 256, 256, col);
            caption = "普攻: " + hf.normalHitSound.get().label + " · 暴击: " + hf.critHitSound.get().label;
        } else if (m instanceof StepDecalsModule sd) {
            int outer = GlobalColorModule.resolveArgb(sd.syncGlobalColor.get(), sd.style.get().outerArgb, 6);
            int core = sd.syncGlobalColor.get() ? 0xFFFFFFFF : sd.style.get().coreArgb;
            float sc = Math.max(0.55f, Math.min(1.45f, sd.scale.get().floatValue() * 3.0f));
            // Real perspective 3D block-matrix footsteps preview matching in-world rendering!
            float lcx = cx - 10, lcy = cy + 3;
            float rcx = cx + 10, rcy = cy - 4;
            float s1 = 10 * sc, s2 = 5 * sc;
            AppleUiRenderer.drawRoundedRect(g, lcx - s1 * 0.5f, lcy - s1 * 0.35f, s1, s1 * 0.7f, 1.5f, AppleUiRenderer.withAlpha(outer, 0.45f));
            AppleUiRenderer.drawRoundedOutline(g, lcx - s1 * 0.5f, lcy - s1 * 0.35f, s1, s1 * 0.7f, 1.5f, 1.5f, outer);
            AppleUiRenderer.drawRoundedRect(g, lcx - s2 * 0.5f, lcy - s2 * 0.35f, s2, s2 * 0.7f, 1.0f, core);

            AppleUiRenderer.drawRoundedRect(g, rcx - s1 * 0.5f, rcy - s1 * 0.35f, s1, s1 * 0.7f, 1.5f, AppleUiRenderer.withAlpha(outer, 0.45f));
            AppleUiRenderer.drawRoundedOutline(g, rcx - s1 * 0.5f, rcy - s1 * 0.35f, s1, s1 * 0.7f, 1.5f, 1.5f, outer);
            AppleUiRenderer.drawRoundedRect(g, rcx - s2 * 0.5f, rcy - s2 * 0.35f, s2, s2 * 0.7f, 1.0f, core);
            caption = sd.style.get().label + " · 尺寸 " + String.format("%.2f", sd.scale.get());
        } else if (m instanceof com.ye.mod.modules.utility.StatsModule) {
            YeHdFont.drawCentered(g, ConfigManager.statPlayerKills + " / " + ConfigManager.statPlayerDeaths, cx, cy - 4, 9.2f, 0xFF38BDF8, true, true);
            caption = String.format(java.util.Locale.ROOT, "击杀玩家: %d · 被击杀: %d · K/D %.2f",
                    ConfigManager.statPlayerKills, ConfigManager.statPlayerDeaths, com.ye.mod.modules.utility.StatsModule.getKdRatio());
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
            caption = "冲刺与滑翔边缘速度流光";
        } else if (m instanceof VoxelShatterModule) {
            int[] cols = {0xFF4ADE80, 0xFF38BDF8, 0xFFFACC15, 0xFFEF4444};
            for (int i = 0; i < 6; i++) {
                float off = (float) Math.sin(now / 180.0 + i) * 4.0f;
                AppleUiRenderer.drawRoundedRect(g, cx - 18 + i * 6, cy - 3 + off, 5, 5, 1, cols[i % cols.length]);
            }
            caption = "击败生物时碎裂为物理体素方块";
        } else if (m instanceof SoulAscendModule) {
            g.blit(RenderPipelines.GUI_TEXTURED, YeHighResTextures.SOUL_WISP_TEX,
                    cx - 10, cy - 13, 0f, 0f, 20, 26, 128, 128, 128, 128, 0xFF7DD3FC);
            caption = "击败目标魂火缓缓升空";
        } else if (m instanceof GlobalColorModule gc) {
            AppleUiRenderer.drawRoundedRect(g, px + 6, py + 6, 16, ph - 12, 4, gc.getCurrentArgb(0));
            AppleUiRenderer.drawRoundedRect(g, px + 26, py + 6, 16, ph - 12, 4, gc.getCurrentArgb(2));
            AppleUiRenderer.drawRoundedRect(g, px + 46, py + 6, 16, ph - 12, 4, gc.getCurrentArgb(4));
            caption = gc.mode.get().label + " · 已联动 " + GlobalColorModule.countSyncedModules() + " 个组件";
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
            caption = esp.style.get().label + " · 单独勾选生物: " + ConfigManager.entityEspCustomTypes.size() + "种";
        } else if (m instanceof StashRadarModule sr) {
            g.renderItem(new ItemStack(Items.SHULKER_BOX), cx - 16, cy - 8);
            g.renderItem(new ItemStack(Items.CHEST), cx + 1, cy - 8);
            caption = "当前编辑: " + sr.editingKind.get().label;
        } else if (m instanceof BlockEspModule) {
            g.renderItem(new ItemStack(Items.DIAMOND_ORE), cx - 8, cy - 8);
            caption = "已选透视方块: " + ConfigManager.blockEspTargets.size() + " 种";
        } else if (m instanceof BlockSearchModule bsm) {
            g.renderItem(new ItemStack(Items.COMPASS), cx - 8, cy - 8);
            caption = "已搜寻到目标坐标: " + bsm.getResults().size() + " 处";
        } else if (m instanceof FlightSpeedModule fs) {
            g.renderItem(new ItemStack(Items.FEATHER), cx - 8, cy - 8);
            caption = String.format("当前飞行速度: %.3f", fs.currentSpeed.get());
        } else if (m instanceof CameraTweaksModule ct) {
            g.renderItem(new ItemStack(Items.SPYGLASS), cx - 8, cy - 8);
            caption = String.format("第三人称视距: %.1fm", ct.thirdPersonDist.get());
        } else if (m instanceof PlayerUiModule) {
            g.renderItem(new ItemStack(Items.GOLDEN_APPLE), cx - 8, cy - 8);
            caption = m.getDescription();
        } else if (m instanceof HudElementModule hm) {
            int col = hm.resolvePrimaryColor();
            AppleUiRenderer.drawRoundedRect(g, px + 6, py + 7, pw - 12, ph - 14, 4, 0xCC111726);
            AppleUiRenderer.drawCenteredSmooth(g, hm.getName(), cx, cy - 4, 8.2f, col, true);
            caption = m.getDescription();
        } else if (m instanceof HealthPlatesModule hp) {
            AppleUiRenderer.drawRoundedRect(g, cx - 22, cy - 3, 44, 6, 3, 0xFF1E293B);
            AppleUiRenderer.drawRoundedRect(g, cx - 22, cy - 3, 32, 6, 3, 0xFF22C55E);
            caption = hp.posMode.get().label + " · " + hp.triggerMode.get().label;
        } else if (m instanceof DamagePopupsModule dp) {
            YeHdFont.drawCentered(g, "-8.5", cx, cy - 5, 9.5f, 0xFFEF4444, true, true);
            caption = dp.mode.get().label + (dp.showCombo.get() ? " · 连击统计" : "");
        } else {
            AppleUiRenderer.drawModuleIcon(g, m, cx - 7, cy - 7, 14, ConfigManager.getAccentColor());
        }

        float tx = px + pw + 9;
        float maxTw = w - (tx - x) - 8;
        String hdr = (m instanceof AutoLogModule)
                ? ConfigManager.tr("触发逻辑 · 好友 " + FriendManager.count() + " 人", "Trigger Logic · Friends: " + FriendManager.count())
                : ConfigManager.tr("功能介绍与预览", "Overview & Preview");
        YeHdFont.draw(g, hdr, tx, py + (is3d ? 10 : 6), 8.0f, AppleUiRenderer.textSecondary(), false, false);
        YeHdFont.draw(g, YeHdFont.clipToWidth(caption, 8.6f, true, maxTw), tx, py + (is3d ? 25 : 20), 8.6f, AppleUiRenderer.textPrimary(), false, true);
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
        if (opt instanceof AmbienceModule.Theme at) {
            AppleUiRenderer.drawRoundedRect(g, x, y, sz, sz, 3, at.getSkyArgb());
            return sz + 5;
        }
        if (opt instanceof GlobalColorModule.PresetPalette pp) {
            AppleUiRenderer.drawCircle(g, x, y, sz, pp.argb);
            return sz + 5;
        }
        if (opt instanceof HudElementModule.ColorStyle hcs && hcs != HudElementModule.ColorStyle.SYNC && hcs != HudElementModule.ColorStyle.CUSTOM) {
            AppleUiRenderer.drawCircle(g, x, y, sz, hcs.argb);
            return sz + 5;
        }
        return 0;
    }
}
