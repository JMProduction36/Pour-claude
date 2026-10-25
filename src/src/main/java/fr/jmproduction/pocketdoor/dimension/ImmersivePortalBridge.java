package fr.jmproduction.pocketdoor.dimension;

import fr.jmproduction.pocketdoor.entity.ModEntities;
import fr.jmproduction.pocketdoor.portal.Portal;
import fr.jmproduction.pocketdoor.portal.api.PortalAPI;
import com.mojang.math.Quaternion;
import com.mojang.math.Vector3f;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;

/**
 * Bridges Pocket Door to the internal portal implementation. Adapted from the
 * upstream version that used Immersive Portals; this version is self-contained
 * and uses the internal {@link Portal}/{@link PortalAPI} classes shipped with
 * the mod under {@code fr.jmproduction.pocketdoor.portal.*}.
 *
 * Bugfixes applied (compared to the initial IP-bound version):
 *  - Every public entry point tolerates a {@code null} exterior level, a
 *    {@code null} door position or a missing pocket dimension. A missing
 *    dependency is treated as "abort cleanly": no portal is spawned and the
 *    function returns without side effects.
 *  - {@link #cleanupAll} is the canonical "tear down BOTH sides" hook used by
 *    {@link fr.jmproduction.pocketdoor.block.PocketDoorBlock#onRemove} when
 *    one half of a door disappears. It is null-safe on every parameter.
 *  - {@link #open} / {@link #ensureOpen} are idempotent: a stale portal pair
 *    around the door is discarded before a new one is spawned.
 */
public final class ImmersivePortalBridge {
    private static final String TAG_PREFIX = "pocketdoor_visual_";
    private static final double PORTAL_WIDTH = 0.98D;
    private static final double PORTAL_HEIGHT = 1.99D;
    private static final double DESTINATION_DOOR_VIEW_OFFSET = 0.12D;
    private static final double PORTAL_PLANE_OFFSET = 0.05D;
    private static final Direction POCKET_DOOR_FACING = Direction.SOUTH;

    /** Registered Portal EntityType (set in ModEntities). */
    private static final EntityType<Portal> PORTAL_TYPE = ModEntities.PORTAL;

    private ImmersivePortalBridge() {}

    public static void open(ServerLevel exterior, BlockPos exteriorDoor, Direction facing,
                             ServerLevel pocket) {
        if (exterior == null || exteriorDoor == null || pocket == null || facing == null) {
            return;
        }
        removeForDoor(exterior, exteriorDoor);
        removePocketVisual(pocket);

        Vec3 exteriorCenter = center(exteriorDoor).add(0.0D, 0.95D, 0.0D);
        Vec3 pocketCenter = center(PocketOfficeGenerator.POCKET_DOOR_LOWER).add(0.0D, 0.95D, 0.0D);

        Vec3 exteriorPortalCenter = exteriorCenter.add(directionVector(facing).scale(PORTAL_PLANE_OFFSET));
        Vec3 pocketPortalCenter = pocketCenter.add(directionVector(POCKET_DOOR_FACING).scale(PORTAL_PLANE_OFFSET));
        Vec3 pocketViewPoint = pocketCenter.add(directionVector(POCKET_DOOR_FACING).scale(DESTINATION_DOOR_VIEW_OFFSET));
        Vec3 exteriorViewPoint = exteriorCenter.add(directionVector(facing).scale(DESTINATION_DOOR_VIEW_OFFSET));

        Vec3 axisW = widthAxis(facing);
        Vec3 axisH = new Vec3(0.0D, 1.0D, 0.0D);

        Portal p = new Portal(PORTAL_TYPE, exterior);
        p.setOriginPos(exteriorPortalCenter);
        p.setOrientationAndSize(axisW, axisH, PORTAL_WIDTH, PORTAL_HEIGHT);
        p.destinationWorld = PocketDimensions.POCKET_OFFICE;
        p.setDestination(pocketViewPoint);

        p.setRotationTransformation(new Quaternion(
                new Vector3f(0.0F, 1.0F, 0.0F),
                computeViewRotationDegrees(facing, POCKET_DOOR_FACING),
                true
        ));
        configure(p, TAG_PREFIX + "exterior");
        PortalAPI.spawnServerEntity(p);

        Portal reverse = new Portal(PORTAL_TYPE, pocket);
        reverse.setOriginPos(pocketPortalCenter);
        reverse.setDestination(exteriorViewPoint);
        reverse.setOrientationAndSize(
                widthAxis(POCKET_DOOR_FACING),
                axisH,
                PORTAL_WIDTH,
                PORTAL_HEIGHT
        );
        configure(reverse, TAG_PREFIX + "pocket");
        PortalAPI.spawnServerEntity(reverse);
    }

    /**
     * Idempotent: only spawns portal visuals if no portal pair is already present
     * around either endpoint. Safe to call with {@code null} arguments.
     */
    public static void ensureOpen(ServerLevel exterior, BlockPos exteriorDoor, Direction facing,
                                   ServerLevel pocket) {
        if (exterior == null || exteriorDoor == null || pocket == null || facing == null) {
            return;
        }
        AABB exteriorBox = new AABB(exteriorDoor).inflate(2.0D);
        AABB pocketBox = new AABB(PocketOfficeGenerator.POCKET_DOOR_LOWER).inflate(2.0D);
        boolean exteriorPresent = !exterior.getEntitiesOfClass(Portal.class, exteriorBox,
                po -> po.portalTag != null && po.portalTag.equals(TAG_PREFIX + "exterior")).isEmpty();
        boolean pocketPresent = !pocket.getEntitiesOfClass(Portal.class, pocketBox,
                po -> po.portalTag != null && po.portalTag.equals(TAG_PREFIX + "pocket")).isEmpty();
        if (!exteriorPresent || !pocketPresent) {
            open(exterior, exteriorDoor, facing, pocket);
        }
    }

    /**
     * Close the portals attached to one exterior door. Tolerates a {@code null}
     * exterior level — used from {@code PocketDoorBlock.onRemove} where the
     * server reference is not guaranteed.
     */
    public static void close(ServerLevel exterior, BlockPos exteriorDoor) {
        if (exteriorDoor == null) {
            return;
        }
        if (exterior != null) {
            removeForDoor(exterior, exteriorDoor);
        }
        ServerLevel pocket = null;
        if (exterior != null && exterior.getServer() != null) {
            pocket = exterior.getServer().getLevel(PocketDimensions.POCKET_OFFICE);
        }
        if (pocket != null) {
            removePocketVisual(pocket);
        }
    }

    /**
     * Tear down portals on BOTH sides. Tolerates {@code null} exterior level
     * (e.g. when called from a block-removal hook where the server reference
     * isn't immediately available). This is the canonical cleanup path used by
     * {@link fr.jmproduction.pocketdoor.block.PocketDoorBlock#onRemove}.
     */
    public static void cleanupAll(ServerLevel exterior, BlockPos exteriorDoor) {
        if (exteriorDoor == null) {
            return;
        }
        if (exterior != null) {
            removeForDoor(exterior, exteriorDoor);
        }
        net.minecraft.server.MinecraftServer server = null;
        if (exterior != null) {
            server = exterior.getServer();
        }
        if (server == null) {
            return;
        }
        ServerLevel pocket = server.getLevel(PocketDimensions.POCKET_OFFICE);
        if (pocket != null) {
            removePocketVisual(pocket);
        }
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
        if (level == null || door == null) return;
        AABB box = new AABB(door).inflate(2.0D);
        level.getEntitiesOfClass(Portal.class, box, p -> p.portalTag != null &&
                (p.portalTag.equals(TAG_PREFIX + "exterior") || p.portalTag.equals(TAG_PREFIX + "pocket")))
            .forEach(Portal::discard);
    }

    private static void removePocketVisual(ServerLevel pocket) {
        if (pocket == null) return;
        AABB box = new AABB(PocketOfficeGenerator.POCKET_DOOR_LOWER).inflate(2.0D);
        pocket.getEntitiesOfClass(Portal.class, box, p -> p.portalTag != null && p.portalTag.equals(TAG_PREFIX + "pocket"))
            .forEach(Portal::discard);
    }

    private static Vec3 center(BlockPos pos) {
        return new Vec3(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
    }

    private static Vec3 widthAxis(Direction facing) {
        return switch (facing) {
            case NORTH -> new Vec3(-1, 0, 0);
            case SOUTH -> new Vec3(1, 0, 0);
            case EAST  -> new Vec3(0, 0, -1);
            case WEST  -> new Vec3(0, 0, 1);
            default    -> new Vec3(1, 0, 0);
        };
    }

    private static Vec3 directionVector(Direction direction) {
        return new Vec3(direction.getStepX(), 0.0D, direction.getStepZ());
    }

    private static float computeViewRotationDegrees(Direction exteriorFacing, Direction pocketFacing) {
        float sourceViewYaw = exteriorFacing.getOpposite().toYRot();
        float targetViewYaw = pocketFacing.toYRot();
        return Mth.wrapDegrees(sourceViewYaw - targetViewYaw + 180.0F);
    }
}
