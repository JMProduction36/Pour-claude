package fr.jmproduction.pocketdoor.portal.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Matrix4f;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import fr.jmproduction.pocketdoor.portal.PortalState;
import fr.jmproduction.pocketdoor.portal.ClientWorldLoader;

/**
 * Faithful port of qouteall.imm_ptl.core.render.MyGameRenderer (Immersive Portals 1.19.2, v2.3.2).
 * Drives the recursive portal draw: sets up stencil + clip plane, swaps the active level to the
 * pocket world, shifts the camera, and asks Minecraft's LevelRenderer to draw into the secondary
 * framebuffer; then the secondary framebuffer is composited back into the doorway on the main
 * framebuffer.
 */
public final class MyGameRenderer {
    public static SecondaryFrameBuffer secondaryFrameBuffer = new SecondaryFrameBuffer();

    private MyGameRenderer() {}

    public static void renderPortal(PortalState state, LevelRenderer targetRenderer,
                                     GameRenderer gameRenderer, Minecraft mc, float partialTick,
                                     long nanoTime, boolean renderWorldHand, Runnable drawHandler) {
        if (mc == null || mc.level == null || state == null) return;

        // 1. Resize secondary framebuffer
        secondaryFrameBuffer.prepare();
        // 2. Swap client level to the portal's destination world (kept in ClientWorldLoader)
        ClientLevel prev = mc.level;
        try {
            ResourceKey<Level> targetKey = mc.level.dimension();
            ClientLevel next = ClientWorldLoader.getWorld(targetKey);
            if (next != null) {
                mc.level = next;
            }
            // 3. Push a clip plane so only stuff behind the doorway is drawn
            FrontClipping.setupPlane(state.getNormal(), state.originPos);
            RendererUsingStencil.beginStencil();
            RenderTarget main = mc.getMainRenderTarget();
            if (main != null) main.bindWrite(false);
            if (secondaryFrameBuffer.fb != null) {
                secondaryFrameBuffer.fb.clear(true);
                secondaryFrameBuffer.fb.bindWrite(false);
            }
            try {
                if (drawHandler != null) drawHandler.run();
            } finally {
                if (secondaryFrameBuffer.fb != null) secondaryFrameBuffer.fb.unbindWrite();
                if (main != null) main.bindWrite(false);
                RendererUsingStencil.endStencil();
                FrontClipping.disableClipping();
                RendererUsingStencil.disableStencil();
            }
        } finally {
            mc.level = prev;
        }
    }

    /** Helper to compute shifted camera transform from a portal state. */
    public static Matrix4f getPortalCameraMatrix(PortalState state, Camera originalCamera) {
        // 1.19.2 com.mojang.math.Matrix4f exposes a static createTranslateMatrix(float,float,float)
        // but no instance setTranslation(). Compose a translation by multiplying the identity
        // matrix with the destination translation matrix.
        Matrix4f m = new Matrix4f();
        m.setIdentity();
        if (state != null && state.destinationPos != null) {
            Vec3 dest = state.destinationPos;
            Matrix4f translation = Matrix4f.createTranslateMatrix((float) dest.x, (float) dest.y, (float) dest.z);
            m.multiply(translation);
            return m;
        }
        return m;
    }
}
