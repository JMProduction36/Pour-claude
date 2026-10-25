package fr.jmproduction.pocketdoor.portal.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.opengl.GL11;

/**
 * Faithful port of qouteall.imm_ptl.core.render.FrontClipping (Immersive Portals 1.19.2, v2.3.2).
 * Uses the GL clip plane so the world drawn through the door is correctly sliced at the doorway plane.
 */
public final class FrontClipping {
    public static boolean isClippingEnabled = false;
    private static final Minecraft mc = Minecraft.getInstance();
    private static double[] planeEq = new double[]{0,1,0,0};

    public static void disableClipping() {
        GL11.glDisable(GL11.GL_CLIP_PLANE0);
        isClippingEnabled = false;
    }

    public static void setupPlane(Vec3 normal, Vec3 point) {
        planeEq[0] = normal.x;
        planeEq[1] = normal.y;
        planeEq[2] = normal.z;
        planeEq[3] = -normal.dot(point);
        GL11.glClipPlane(GL11.GL_CLIP_PLANE0, planeEq);
        if (!isClippingEnabled) {
            GL11.glEnable(GL11.GL_CLIP_PLANE0);
            isClippingEnabled = true;
        }
    }

    public static void enableClipping() {
        if (!isClippingEnabled) {
            GL11.glEnable(GL11.GL_CLIP_PLANE0);
            isClippingEnabled = true;
        }
    }

    public static void init() {
        // on client init
    }
}
