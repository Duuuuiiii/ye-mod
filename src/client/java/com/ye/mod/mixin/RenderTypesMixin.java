package com.ye.mod.mixin;

import com.ye.mod.core.ModuleManager;
import com.ye.mod.modules.visual.EnchantGlintModule;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RenderTypes.class)
public abstract class RenderTypesMixin {

    @Inject(method = "glint", at = @At("HEAD"), cancellable = true)
    private static void ye$customGlint(CallbackInfoReturnable<RenderType> cir) {
        EnchantGlintModule mod = ModuleManager.get(EnchantGlintModule.class);
        if (mod != null) {
            RenderType rt = mod.overrideItemGlint(0);
            if (rt != null) cir.setReturnValue(rt);
        }
    }

    @Inject(method = "glintTranslucent", at = @At("HEAD"), cancellable = true)
    private static void ye$customGlintTranslucent(CallbackInfoReturnable<RenderType> cir) {
        EnchantGlintModule mod = ModuleManager.get(EnchantGlintModule.class);
        if (mod != null) {
            RenderType rt = mod.overrideItemGlint(1);
            if (rt != null) cir.setReturnValue(rt);
        }
    }

    @Inject(method = "entityGlint", at = @At("HEAD"), cancellable = true)
    private static void ye$customEntityGlint(CallbackInfoReturnable<RenderType> cir) {
        EnchantGlintModule mod = ModuleManager.get(EnchantGlintModule.class);
        if (mod != null) {
            RenderType rt = mod.overrideItemGlint(2);
            if (rt != null) cir.setReturnValue(rt);
        }
    }

    @Inject(method = "armorEntityGlint", at = @At("HEAD"), cancellable = true)
    private static void ye$customArmorGlint(CallbackInfoReturnable<RenderType> cir) {
        EnchantGlintModule mod = ModuleManager.get(EnchantGlintModule.class);
        if (mod != null) {
            RenderType rt = mod.overrideArmorGlint();
            if (rt != null) cir.setReturnValue(rt);
        }
    }
}
