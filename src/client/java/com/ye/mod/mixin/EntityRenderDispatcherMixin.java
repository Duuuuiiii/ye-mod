package com.ye.mod.mixin;

import com.ye.mod.core.ModuleManager;
import com.ye.mod.modules.render.AntiLagModule;
import com.ye.mod.modules.render.EntityEspModule;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {

    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private <E extends Entity> void ye$antiLagEntityRender(E entity, Frustum frustum, double camX, double camY, double camZ,
                                                           CallbackInfoReturnable<Boolean> cir) {
        AntiLagModule antiLag = ModuleManager.get(AntiLagModule.class);
        if (antiLag != null && antiLag.shouldSkipEntity(entity)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "extractEntity", at = @At("RETURN"))
    private <E extends Entity> void ye$applyEntityOutlineEsp(E entity, float partialTick, CallbackInfoReturnable<EntityRenderState> cir) {
        EntityEspModule esp = ModuleManager.get(EntityEspModule.class);
        if (esp != null && esp.usesShaderOutline()) {
            esp.applyOutlineState(entity, cir.getReturnValue());
        }
    }
}
