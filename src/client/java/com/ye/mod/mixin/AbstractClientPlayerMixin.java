package com.ye.mod.mixin;

import com.ye.mod.core.ModuleManager;
import com.ye.mod.modules.render.CustomCapeModule;
import com.ye.mod.modules.visual.NoFovModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractClientPlayer.class)
public abstract class AbstractClientPlayerMixin {

    @Inject(method = "getSkin", at = @At("RETURN"), cancellable = true)
    private void ye$overrideCape(CallbackInfoReturnable<PlayerSkin> cir) {
        CustomCapeModule capeMod = ModuleManager.get(CustomCapeModule.class);
        if (capeMod == null || !capeMod.isEnabled()) return;

        Minecraft mc = Minecraft.getInstance();
        if (capeMod.onlySelf.get() && (Object) this != mc.player) return;

        Identifier capeTex = capeMod.getActiveCapeTexture();
        if (capeTex == null) return;

        PlayerSkin original = cir.getReturnValue();
        ClientAsset.Texture capeAsset = new ClientAsset.ResourceTexture(capeTex);
        ClientAsset.Texture elytraAsset = capeMod.applyToElytra.get() ? capeAsset : original.elytra();

        cir.setReturnValue(new PlayerSkin(
                original.body(),
                capeAsset,
                elytraAsset,
                original.model(),
                original.secure()
        ));
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
