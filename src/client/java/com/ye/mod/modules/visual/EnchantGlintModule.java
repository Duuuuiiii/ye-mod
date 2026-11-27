package com.ye.mod.modules.visual;

import com.mojang.blaze3d.platform.NativeImage;
import com.ye.mod.core.Category;
import com.ye.mod.core.Module;
import com.ye.mod.core.setting.BoolSetting;
import com.ye.mod.core.setting.ColorSetting;
import com.ye.mod.core.setting.DoubleSetting;
import com.ye.mod.core.setting.EnumSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.LayeringTransform;
import net.minecraft.client.renderer.rendertype.OutputTarget;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.TextureTransform;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import org.joml.Matrix4f;

import java.awt.Color;

/**
 * Custom Enchantment Glint Module (附魔光效):
 * Modifies armor, held item, GUI item, and dropped item enchantment glint color, rainbow flow,
 * brightness, scroll speed, glint density/scale, or clean no-glint mode!
 */
public class EnchantGlintModule extends Module {
    public enum GlintMode {
        CUSTOM_COLOR("自定义颜色"),
        RAINBOW("彩虹流光"),
        SYNC_COLOR("跟随同步色"),
        HIDE_GLINT("隐藏附魔光效");

        public final String label;
        GlintMode(String label) { this.label = label; }
    }

    public final EnumSetting<GlintMode> glintMode = addSetting(new EnumSetting<>(
            "glintMode", "光效模式", "自定义颜色 / 彩虹流光 / 跟随同步色 / 隐藏附魔光效", GlintMode.CUSTOM_COLOR
    ).functionGroup());

    public final BoolSetting applyItems = addSetting(new BoolSetting(
            "applyItems", "作用于手持与物品", "修改武器、工具、金苹果及背包物品附魔光效", true
    ).functionGroup());

    public final BoolSetting applyArmor = addSetting(new BoolSetting(
            "applyArmor", "作用于全身盔甲", "修改玩家与生物穿戴盔甲的附魔流光", true
    ).functionGroup());

    public final DoubleSetting scrollSpeed = addSetting(new DoubleSetting(
            "scrollSpeed", "流动速度", "附魔流光表面滚动速率", 1.2, 0.0, 4.0, 0.1
    ).visible(() -> glintMode.get() != GlintMode.HIDE_GLINT).functionGroup());

    public final DoubleSetting glintScale = addSetting(new DoubleSetting(
            "glintScale", "纹理密度", "调节附魔流光条纹粗细与密度", 1.0, 0.3, 3.0, 0.1
    ).visible(() -> glintMode.get() != GlintMode.HIDE_GLINT).functionGroup());

    public final ColorSetting customColor = addSetting(new ColorSetting(
            "customColor", "附魔颜色", "自由拖动选择附魔光效色调", 0xFF38BDF8
    ).visible(() -> glintMode.get() == GlintMode.CUSTOM_COLOR));

    public final DoubleSetting brightness = addSetting(new DoubleSetting(
            "brightness", "光效亮度", "调节附魔发光明亮程度", 1.25, 0.15, 2.50, 0.05
    ).visible(() -> glintMode.get() != GlintMode.HIDE_GLINT));

    private static final int TEX_SIZE = 128;
    private static float[] basePattern = null;
    private static DynamicTexture dynTex = null;
    private static Identifier dynTexId = null;
    private static int lastUploadedRgb = -1;
    private static long lastUploadMs = 0L;

    private static RenderType customGlint;
    private static RenderType customGlintTranslucent;
    private static RenderType customEntityGlint;
    private static RenderType customArmorGlint;

    public EnchantGlintModule() {
        super("enchant_glint", "附魔光效", "Enchant Glint", "自定义盔甲与物品附魔流光颜色、彩虹渐变、亮度、流速与纹理密度", Category.VISUAL);
    }

    private static void ensureGlintPipelineReady() {
        if (dynTex != null) return;
        basePattern = new float[TEX_SIZE * TEX_SIZE];
        for (int y = 0; y < TEX_SIZE; y++) {
            double ny = (y / (double) TEX_SIZE) * Math.PI * 2.0;
            for (int x = 0; x < TEX_SIZE; x++) {
                double nx = (x / (double) TEX_SIZE) * Math.PI * 2.0;
                // Multi-harmonic seamless diagonal silk shimmer matching Minecraft's glint character at higher crispness
                double w1 = 0.5 + 0.5 * Math.sin(nx * 2.0 + ny * 3.0 + Math.sin(nx * 3.0 - ny));
                double w2 = 0.5 + 0.5 * Math.cos(nx * 4.0 - ny * 2.0 + Math.cos(ny * 3.0));
                double streak = Math.pow(w1 * 0.62 + w2 * 0.38, 2.35);
                basePattern[y * TEX_SIZE + x] = (float) Math.min(1.0, streak * 1.35);
            }
        }

        NativeImage img = new NativeImage(TEX_SIZE, TEX_SIZE, true);
        dynTex = new DynamicTexture(() -> "ye_custom_glint", img);
        dynTexId = Identifier.fromNamespaceAndPath("ye", "custom_enchant_glint");
        Minecraft.getInstance().getTextureManager().register(dynTexId, dynTex);

        TextureTransform itemXform = new TextureTransform("ye_glint", () -> buildGlintMatrix(8.0f));
        TextureTransform entityXform = new TextureTransform("ye_entity_glint", () -> buildGlintMatrix(0.16f));
        TextureTransform armorXform = new TextureTransform("ye_armor_glint", () -> buildGlintMatrix(0.16f));

        customGlint = RenderType.create("ye_glint",
                RenderSetup.builder(RenderPipelines.GLINT)
                        .withTexture("Sampler0", dynTexId)
                        .setTextureTransform(itemXform)
                        .createRenderSetup());

        customGlintTranslucent = RenderType.create("ye_glint_translucent",
                RenderSetup.builder(RenderPipelines.GLINT)
                        .withTexture("Sampler0", dynTexId)
                        .setTextureTransform(itemXform)
                        .setOutputTarget(OutputTarget.ITEM_ENTITY_TARGET)
                        .createRenderSetup());

        customEntityGlint = RenderType.create("ye_entity_glint",
                RenderSetup.builder(RenderPipelines.GLINT)
                        .withTexture("Sampler0", dynTexId)
                        .setTextureTransform(entityXform)
                        .createRenderSetup());

        customArmorGlint = RenderType.create("ye_armor_entity_glint",
                RenderSetup.builder(RenderPipelines.GLINT)
                        .withTexture("Sampler0", dynTexId)
                        .setTextureTransform(armorXform)
                        .setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
                        .createRenderSetup());
    }

    private static Matrix4f buildGlintMatrix(float baseScale) {
        EnchantGlintModule mod = com.ye.mod.core.ModuleManager.get(EnchantGlintModule.class);
        float spd = (mod != null) ? mod.scrollSpeed.get().floatValue() : 1.0f;
        float dens = (mod != null) ? mod.glintScale.get().floatValue() : 1.0f;
        long t = (long) (Util.getMillis() * spd * 8.0);
        float uOff = (t % 110000L) / 110000.0f;
        float vOff = (t % 30000L) / 30000.0f;
        float s = baseScale * dens;
        return new Matrix4f()
                .translation(-uOff, vOff, 0.0f)
                .rotateZ((float) Math.toRadians(10.0))
                .scale(s);
    }

    private void refreshDynamicTextureIfNeeded() {
        ensureGlintPipelineReady();
        long now = System.currentTimeMillis();
        int targetRgb;
        GlintMode gm = glintMode.get();
        if (gm == GlintMode.HIDE_GLINT) {
            targetRgb = 0x000000;
        } else {
            int rawRgb = switch (gm) {
                case CUSTOM_COLOR -> customColor.getRgb();
                case SYNC_COLOR -> GlobalColorModule.resolveRgb(true, customColor.getRgb(), 0);
                case RAINBOW -> Color.HSBtoRGB((now % 3600L) / 3600.0f, 0.80f, 1.0f) & 0x00FFFFFF;
                default -> 0x38BDF8;
            };
            float bri = brightness.get().floatValue();
            int r = Math.min(255, Math.round(((rawRgb >> 16) & 0xFF) * bri));
            int g = Math.min(255, Math.round(((rawRgb >> 8) & 0xFF) * bri));
            int b = Math.min(255, Math.round((rawRgb & 0xFF) * bri));
            targetRgb = (r << 16) | (g << 8) | b;
        }

        if (targetRgb == lastUploadedRgb) return;
        if ((gm == GlintMode.RAINBOW || gm == GlintMode.SYNC_COLOR) && now - lastUploadMs < 32L) return;
        lastUploadedRgb = targetRgb;
        lastUploadMs = now;

        NativeImage img = dynTex.getPixels();
        if (img == null) return;
        int tr = (targetRgb >> 16) & 0xFF;
        int tg = (targetRgb >> 8) & 0xFF;
        int tb = targetRgb & 0xFF;

        for (int y = 0; y < TEX_SIZE; y++) {
            int row = y * TEX_SIZE;
            for (int x = 0; x < TEX_SIZE; x++) {
                float p = basePattern[row + x];
                int pr = Math.min(255, Math.round(tr * p));
                int pg = Math.min(255, Math.round(tg * p));
                int pb = Math.min(255, Math.round(tb * p));
                int pa = targetRgb == 0 ? 0 : Math.min(255, Math.round(p * 255.0f));
                img.setPixel(x, y, (pa << 24) | (pr << 16) | (pg << 8) | pb);
            }
        }
        dynTex.upload();
    }

    public RenderType overrideItemGlint(int variant) {
        if (!isEnabled() || !applyItems.get()) return null;
        refreshDynamicTextureIfNeeded();
        return switch (variant) {
            case 1 -> customGlintTranslucent;
            case 2 -> customEntityGlint;
            default -> customGlint;
        };
    }

    public RenderType overrideArmorGlint() {
        if (!isEnabled() || !applyArmor.get()) return null;
        refreshDynamicTextureIfNeeded();
        return customArmorGlint;
    }
}
