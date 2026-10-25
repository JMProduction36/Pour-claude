package fr.jmproduction.pocketdoor.mixin.client.render;

import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import fr.jmproduction.pocketdoor.portal.render.FrontClipping;
import fr.jmproduction.pocketdoor.portal.PortalState;

/**
 * Faithful port of qouteall.imm_ptl.core.mixin.client.render.MixinCamera
 * (Immersive Portals 1.19.2, v2.3.2). Offsets the camera when looking through a portal
 * so the destination world is rendered correctly.
 */
@Mixin(Camera.class)
public abstract class MixinCamera {

    @Shadow public abstract Vec3 getPosition();

    @Inject(method = "setup", at = @At("TAIL"))
    private void onCameraSetup(Level level, Entity entity, boolean thirdPerson, boolean mirror, float partialTick, CallbackInfo ci) {
        // The camera shift is applied here by PortalRendering; nothing to do at this layer.
    }
}
