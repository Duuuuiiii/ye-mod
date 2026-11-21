package com.ye.mod.mixin;

import com.ye.mod.core.ModuleManager;
import com.ye.mod.modules.render.CustomCapeModule;
import com.ye.mod.modules.render.CustomSkinModule;
import com.ye.mod.modules.visual.NoFovModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractClientPlayer.class)
public abstract class AbstractClientPlayerMixin {

    @Inject(method = "getSkin", at = @At("RETURN"), cancellable = true)
    private void ye$overrideSkinAndCape(CallbackInfoReturnable<PlayerSkin> cir) {
        CustomCapeModule capeMod = ModuleManager.get(CustomCapeModule.class);
        CustomSkinModule skinMod = ModuleManager.get(CustomSkinModule.class);
        boolean applyCape = capeMod != null && capeMod.isEnabled();
        boolean applySkin = skinMod != null && skinMod.isEnabled();
        if (!applyCape && !applySkin) return;

        Minecraft mc = Minecraft.getInstance();
        boolean isSelf = (Object) this == mc.player;

        PlayerSkin original = cir.getReturnValue();
        ClientAsset.Texture bodyAsset = original.body();
        ClientAsset.Texture capeAsset = original.cape();
        ClientAsset.Texture elytraAsset = original.elytra();
        PlayerModelType model = original.model();
        boolean changed = false;

        if (applySkin && (!skinMod.onlySelf.get() || isSelf)) {
            Identifier skinTex = skinMod.getActiveSkinTexture();
            if (skinTex != null) {
                bodyAsset = new ClientAsset.ResourceTexture(skinTex);
                changed = true;
            }
            if (skinMod.modelMode.get() == CustomSkinModule.SkinModelMode.CLASSIC) {
                model = PlayerModelType.WIDE;
                changed = true;
            } else if (skinMod.modelMode.get() == CustomSkinModule.SkinModelMode.SLIM) {
                model = PlayerModelType.SLIM;
                changed = true;
            }
        }

        if (applyCape && (!capeMod.onlySelf.get() || isSelf)) {
            Identifier capeTex = capeMod.getActiveCapeTexture();
            if (capeTex != null) {
                capeAsset = new ClientAsset.ResourceTexture(capeTex);
                if (capeMod.applyToElytra.get()) {
                    elytraAsset = capeAsset;
                }
                changed = true;
            }
        }

        if (changed) {
            cir.setReturnValue(new PlayerSkin(
                    bodyAsset,
                    capeAsset,
                    elytraAsset,
                    model,
                    original.secure()
            ));
        }
    }

    @Inject(method = "getFieldOfViewModifier", at = @At("HEAD"), cancellable = true)
    private void ye$lockFovModifier(boolean isFirstPerson, float fovEffectScale, CallbackInfoReturnable<Float> cir) {
        NoFovModule noFov = ModuleManager.get(NoFovModule.class);
        if (noFov == null || !noFov.isEnabled()) return;

        AbstractClientPlayer self = (AbstractClientPlayer) (Object) this;
        boolean usingBowOrSpyglass = self.isUsingItem() &&
                (self.getUseItem().is(Items.BOW) || self.getUseItem().is(Items.SPYGLASS));

        if (usingBowOrSpyglass) {
            if (noFov.lockBowFov.get()) {
                cir.setReturnValue(1.0F);
            }
            return;
        }

        if (noFov.lockSprintFov.get()) {
            cir.setReturnValue(1.0F);
        }
    }
}
