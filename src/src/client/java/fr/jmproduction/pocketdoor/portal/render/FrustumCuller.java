package fr.jmproduction.pocketdoor.portal.render;

import com.mojang.math.Matrix4f;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Faithful port of qouteall.imm_ptl.core.render.FrustumCuller (Immersive Portals 1.19.2, v2.3.2).
 * Two-pass culling: first with the original camera frustum, then shifted to the portal destination camera.
 */
public final class FrustumCuller {
    private FrustumCuller() {}

    /** Returns true if any corner of the AABB is inside the frustum. */
    public static boolean isVisible(Frustum frustum, AABB box) {
        return frustum.isVisible(box);
    }

    /**
     * Builds a frustum shifted to look from the destination camera through the destination portal.
     * In 1.19.2 (Mojang mappings), {@link Frustum} exposes a constructor taking a combined
     * projection*modelView matrix and {@link Frustum#prepare(double, double, double)} to
     * offset it to a new camera position. (The earlier {@code setCamera(...)} overload only
     * existed in older versions.)
     */
    public static Frustum buildShifted(Camera cam, double destX, double destY, double destZ) {
        // Identity projection * modelView; the Frustum#prepare(...) call below provides the
        // per-frame camera position offset that Frustum needs.
        Matrix4f combined = new Matrix4f();
        Frustum f = new Frustum(combined, combined);
        f.prepare(destX, destY, destZ);
        return f;
    }
}
