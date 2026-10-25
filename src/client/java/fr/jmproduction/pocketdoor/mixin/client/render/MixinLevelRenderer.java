package fr.jmproduction.pocketdoor.mixin.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Matrix4f;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import fr.jmproduction.pocketdoor.portal.render.FrontClipping;

/**
 * Faithful port of qouteall.imm_ptl.core.mixin.client.render.MixinLevelRenderer
 * (Immersive Portals 1.19.2, v2.3.2). Disables the clip plane before opaque passes and re-enables
 * the stencil so the secondary render is correctly masked.
 */
@Mixin(LevelRenderer.class)
public abstract class MixinLevelRenderer {

    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void onRenderLevelStart(PoseStack m, float partialTick, long nano,
                                    boolean renderBlock, Camera camera,
                                    GameRenderer gameRenderer, LightTexture lm,
                                    Matrix4f proj, CallbackInfo ci) {
        if (FrontClipping.isClippingEnabled) {
            FrontClipping.disableClipping();
        }
    }
}
