package fr.jmproduction.pocketdoor.portal;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import com.mojang.math.Quaternion;

/**
 * Faithful port of qouteall.imm_ptl.core.portal.PortalState (Immersive Portals 1.19.2, v2.3.2).
 * Holds the geometry / transformation of a portal pair. Adapted to the Mojang-mappings API.
 */
public class PortalState {
    public Vec3 originPos;
    public Vec3 destinationPos;
    public Quaternion rotation;
    public double width;
    public double height;
    public Vec3 axisW;
    public Vec3 axisH;

    public PortalState() {}

    public PortalState(Vec3 originPos, Vec3 destinationPos,
                       Quaternion rotation, Vec3 axisW, Vec3 axisH,
                       double width, double height) {
        this.originPos = originPos;
        this.destinationPos = destinationPos;
        this.rotation = rotation;
        this.axisW = axisW;
        this.axisH = axisH;
        this.width = width;
        this.height = height;
    }

    public AABB getOriginPortalBox() {
        Vec3 w = axisW.scale(width * 0.5);
        Vec3 h = axisH.scale(height * 0.5);
        AABB b = new AABB(originPos.add(w).add(h), originPos.subtract(w).subtract(h));
        return b;
    }

    public Direction getFacing() {
        Vec3 n = getNormal();
        return Direction.getNearest(n.x, n.y, n.z);
    }

    public Vec3 getNormal() {
        // axisW x axisH gives plane normal
        return new Vec3(
            axisW.y * axisH.z - axisW.z * axisH.y,
            axisW.z * axisH.x - axisW.x * axisH.z,
            axisW.x * axisH.y - axisW.y * axisH.x
        );
    }
}
