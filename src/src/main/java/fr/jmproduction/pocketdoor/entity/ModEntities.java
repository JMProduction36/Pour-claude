package fr.jmproduction.pocketdoor.entity;

import fr.jmproduction.pocketdoor.PocketDoorMod;
import fr.jmproduction.pocketdoor.portal.Portal;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public final class ModEntities {
    public static final EntityType<ChairSeatEntity> CHAIR_SEAT = Registry.register(
            Registry.ENTITY_TYPE,
            new ResourceLocation(PocketDoorMod.MOD_ID, "chair_seat"),
            EntityType.Builder.<ChairSeatEntity>of(ChairSeatEntity::new, MobCategory.MISC)
                    .sized(0.01F, 0.01F)
                    .clientTrackingRange(8)
                    .updateInterval(1)
                    .build("chair_seat")
    );

    /**
     * Registered Portal EntityType. Tracking range / update interval match the
     * values used by Immersive Portals upstream (10 blocks / 4 ticks). The
     * bounding box is intentionally tiny because the portal is purely a
     * logical entity — the actual frame is drawn by PocketPortalRenderer
     * via WorldRenderEvents.
     *
     * <p>This registration is what made {@link Portal#TYPE} reachable from
     * {@link fr.jmproduction.pocketdoor.dimension.ImmersivePortalBridge} so
     * that {@code Portal#getAddEntityPacket()} no longer relies on the
     * {@code EntityType.ITEM} stand-in.</p>
     */
    public static final EntityType<Portal> PORTAL = (Portal.TYPE = Registry.register(
            Registry.ENTITY_TYPE,
            new ResourceLocation(PocketDoorMod.MOD_ID, "portal"),
            EntityType.Builder.<Portal>of(Portal::new, MobCategory.MISC)
                    .sized(0.01F, 0.01F)
                    .clientTrackingRange(10)
                    .updateInterval(4)
                    .build("portal")
    ));

    private ModEntities() {
    }

    public static void initialize() {
        PocketDoorMod.LOGGER.info("Registered Pocket Door seat and portal entity types.");
    }
}
