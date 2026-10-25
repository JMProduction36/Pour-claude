package fr.jmproduction.pocketdoor.mixin.client.render;

import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import fr.jmproduction.pocketdoor.portal.render.FrontClipping;

/**
 * Faithful port of qouteall.imm_ptl.core.mixin.client.render.MixinGameRenderer
 * (Immersive Portals 1.19.2, v2.3.2). Ensures FrontClipping state is reset on every frame.
 */
@Mixin(GameRenderer.class)
public abstract class MixinGameRenderer {

    @Inject(method = "render", at = @At("HEAD"))
    private void onRenderStart(float partialTick, long nanoTime, boolean renderLevel, CallbackInfo ci) {
        if (FrontClipping.isClippingEnabled) {
            FrontClipping.disableClipping();
        }
    }
}
