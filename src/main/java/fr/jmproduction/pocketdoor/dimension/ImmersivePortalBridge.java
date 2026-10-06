package fr.jmproduction.pocketdoor.dimension;

import qouteall.imm_ptl.core.api.PortalAPI;
import qouteall.imm_ptl.core.portal.Portal;
import com.mojang.math.Quaternion;
import com.mojang.math.Vector3f;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;

/** Bridges Pocket Door to Immersive Portals' real multi-world renderer. */
public final class ImmersivePortalBridge {
    private static final String TAG_PREFIX = "pocketdoor_visual_";
    private static final double PORTAL_WIDTH = 0.98D;
    private static final double PORTAL_HEIGHT = 1.99D;
    private static final double DESTINATION_DOOR_VIEW_OFFSET = 0.12D;
    /** Keep the portal plane almost flush with the physical door so the whole doorway is covered while the real door model
     * remains on the destination side of the portal clipping plane and is therefore visible. */
    private static final double PORTAL_PLANE_OFFSET = 0.05D;
    private static final Direction POCKET_DOOR_FACING = Direction.SOUTH;

    private ImmersivePortalBridge() {}

    public static void open(ServerLevel exterior, BlockPos exteriorDoor, Direction facing,
                             ServerLevel pocket) {
        removeForDoor(exterior, exteriorDoor);
        removePocketVisual(pocket);

        Vec3 exteriorCenter = center(exteriorDoor).add(0.0D, 0.95D, 0.0D);
        Vec3 pocketCenter = center(PocketOfficeGenerator.POCKET_DOOR_LOWER).add(0.0D, 0.95D, 0.0D);

        // The portal surface is just in front of each real door. This is important for the
        // live destination render: the actual Pocket Door must sit behind the portal plane,
        // otherwise Immersive Portals can clip the model itself (especially the open leaf).
        Vec3 exteriorPortalCenter = exteriorCenter.add(directionVector(facing).scale(PORTAL_PLANE_OFFSET));
        Vec3 pocketPortalCenter = pocketCenter.add(directionVector(POCKET_DOOR_FACING).scale(PORTAL_PLANE_OFFSET));
        // Put each destination camera on the FRONT side of the destination door.
        // This makes the real destination door model part of the portal view (closed
        // leaf or swung-open leaf) instead of leaving it behind the camera.
        Vec3 pocketViewPoint = pocketCenter.add(directionVector(POCKET_DOOR_FACING).scale(DESTINATION_DOOR_VIEW_OFFSET));
        Vec3 exteriorViewPoint = exteriorCenter.add(directionVector(facing).scale(DESTINATION_DOOR_VIEW_OFFSET));

        Vec3 axisW = widthAxis(facing);
        Vec3 axisH = new Vec3(0.0D, 1.0D, 0.0D);

        Portal p = new Portal(Portal.entityType, exterior);
        p.setOriginPos(exteriorPortalCenter);
        p.setOrientationAndSize(axisW, axisH, PORTAL_WIDTH, PORTAL_HEIGHT);
        p.setDestinationDimension(PocketDimensions.POCKET_OFFICE);
        p.setDestination(pocketViewPoint);

        // The exterior door may face in any horizontal direction while the pocket-office
        // doorway is fixed in the south wall. Keep the camera just INSIDE the destination
        // doorway so the real destination door (closed panel or swung-open leaf) is part of
        // the live portal view instead of being left on the clipped side of the portal plane.
        p.setRotationTransformation(new Quaternion(
                new Vector3f(0.0F, 1.0F, 0.0F),
                computeViewRotationDegrees(facing, POCKET_DOOR_FACING),
                true
        ));
        configure(p, TAG_PREFIX + "exterior");
        PortalAPI.spawnServerEntity(p);

        Portal reverse = PortalAPI.createReversePortal(p);

        // createReversePortal correctly flips the portal normal, but it preserves the
        // source doorway's plane. The pocket door is always on the south wall, so the
        // reverse portal must use the pocket doorway's plane instead of the exterior one.
        reverse.setOriginPos(pocketPortalCenter);
        reverse.setDestination(exteriorViewPoint);
        reverse.setOrientationAndSize(
                widthAxis(POCKET_DOOR_FACING),
                axisH.scale(-1.0D),
                PORTAL_WIDTH,
                PORTAL_HEIGHT
        );
        configure(reverse, TAG_PREFIX + "pocket");
        PortalAPI.spawnServerEntity(reverse);
    }


    public static void ensureOpen(ServerLevel exterior, BlockPos exteriorDoor, Direction facing, ServerLevel pocket) {
        AABB exteriorBox = new AABB(exteriorDoor).inflate(2.0D);
        AABB pocketBox = new AABB(PocketOfficeGenerator.POCKET_DOOR_LOWER).inflate(2.0D);
        boolean exteriorPresent = !exterior.getEntitiesOfClass(Portal.class, exteriorBox,
                p -> (TAG_PREFIX + "exterior").equals(p.portalTag)).isEmpty();
        boolean pocketPresent = !pocket.getEntitiesOfClass(Portal.class, pocketBox,
                p -> (TAG_PREFIX + "pocket").equals(p.portalTag)).isEmpty();
        if (!exteriorPresent || !pocketPresent) {
            open(exterior, exteriorDoor, facing, pocket);
        }
    }

    public static void close(ServerLevel exterior, BlockPos exteriorDoor) {
        removeForDoor(exterior, exteriorDoor);
        ServerLevel pocket = exterior.getServer().getLevel(PocketDimensions.POCKET_OFFICE);
        if (pocket != null) {
            removePocketVisual(pocket);
        }
    }

    public static void cleanupAll(ServerLevel exterior, BlockPos exteriorDoor) {
        removeForDoor(exterior, exteriorDoor);
        ServerLevel pocket = exterior.getServer().getLevel(PocketDimensions.POCKET_OFFICE);
        if (pocket != null) removePocketVisual(pocket);
    }

    private static void configure(Portal portal, String tag) {
        portal.portalTag = tag;
        portal.teleportable = true;
        portal.setInteractable(true);
        portal.hasCrossPortalCollision = false;
        portal.doRenderPlayer = false;
        portal.renderingMergable = false;
        portal.fuseView = false;
    }

    private static void removeForDoor(ServerLevel level, BlockPos door) {
        AABB box = new AABB(door).inflate(2.0D);
        level.getEntitiesOfClass(Portal.class, box, p -> p.portalTag != null &&
                (p.portalTag.equals(TAG_PREFIX + "exterior") || p.portalTag.equals(TAG_PREFIX + "pocket")))
            .forEach(Portal::discard);
    }

    private static void removePocketVisual(ServerLevel pocket) {
        AABB box = new AABB(PocketOfficeGenerator.POCKET_DOOR_LOWER).inflate(2.0D);
        pocket.getEntitiesOfClass(Portal.class, box, p -> p.portalTag != null && p.portalTag.equals(TAG_PREFIX + "pocket"))
            .forEach(Portal::discard);
    }

    private static Vec3 center(BlockPos pos) {
        return new Vec3(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
    }

    private static Vec3 widthAxis(Direction facing) {
        // axisW × axisH is the portal normal. With axisH pointing upward, these
        // vectors make the normal agree with the doorway's facing direction.
        return switch (facing) {
            case NORTH -> new Vec3(-1, 0, 0);
            case SOUTH -> new Vec3(1, 0, 0);
            case EAST -> new Vec3(0, 0, -1);
            case WEST -> new Vec3(0, 0, 1);
            default -> new Vec3(1, 0, 0);
        };
    }

    private static Vec3 directionVector(Direction direction) {
        return new Vec3(direction.getStepX(), 0.0D, direction.getStepZ());
    }

    private static float computeViewRotationDegrees(Direction exteriorFacing, Direction pocketFacing) {
        // Garde le retournement de 180° validé précédemment, mais inverse le sens de la
        // différence de yaw. Avec une porte placée à droite ou à gauche du joueur
        // (EAST/WEST), le quaternion doit tourner dans l'autre sens pour conserver le
        // même rendu du bureau au lieu de produire une vue inversée.
        float sourceViewYaw = exteriorFacing.getOpposite().toYRot();
        float targetViewYaw = pocketFacing.toYRot();
        return Mth.wrapDegrees(sourceViewYaw - targetViewYaw + 180.0F);
    }
}
