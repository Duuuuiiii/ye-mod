package com.ye.mod.mixin;

import com.ye.mod.core.ModuleManager;
import com.ye.mod.modules.render.AntiLagModule;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockEntityRenderDispatcher.class)
public abstract class BlockEntityRenderDispatcherMixin {

    @Inject(method = "tryExtractRenderState", at = @At("HEAD"), cancellable = true)
    private <E extends BlockEntity, S extends BlockEntityRenderState> void ye$antiLagBlockEntity(
            E blockEntity, float partialTick, ModelFeatureRenderer.CrumblingOverlay overlay,
            CallbackInfoReturnable<S> cir) {
        AntiLagModule antiLag = ModuleManager.get(AntiLagModule.class);
        if (antiLag != null && antiLag.shouldSkipBlockEntity(blockEntity)) {
            cir.setReturnValue(null);
        }
    }
}
