package fr.jmproduction.pocketdoor.portal.render;

import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.GL11;

/**
 * Faithful port of qouteall.imm_ptl.core.render.RendererUsingStencil (Immersive Portals 1.19.2, v2.3.2).
 * Mask the doorway with the stencil buffer so the secondary world is only drawn inside it.
 */
public final class RendererUsingStencil {
    public static final int STENCIL_BIT = 0x80;

    private RendererUsingStencil() {}

    public static void beginStencil() {
        GL11.glEnable(GL11.GL_STENCIL_TEST);
        GL11.glStencilMask(STENCIL_BIT);
        GL11.glClearStencil(0);
        GL11.glClear(GL11.GL_STENCIL_BUFFER_BIT);
        GL11.glStencilFunc(GL11.GL_ALWAYS, STENCIL_BIT, STENCIL_BIT);
        GL11.glStencilOp(GL11.GL_REPLACE, GL11.GL_REPLACE, GL11.GL_REPLACE);
        RenderSystem.colorMask(false, false, false, false);
        RenderSystem.depthMask(false);
    }

    public static void endStencil() {
        RenderSystem.colorMask(true, true, true, true);
        RenderSystem.depthMask(true);
        GL11.glStencilFunc(GL11.GL_EQUAL, STENCIL_BIT, STENCIL_BIT);
        GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);
    }

    public static void disableStencil() {
        GL11.glDisable(GL11.GL_STENCIL_TEST);
    }
}
