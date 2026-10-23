package fr.jmproduction.pocketdoor.entity;

import fr.jmproduction.pocketdoor.PocketDoorMod;
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

    private ModEntities() {
    }

    public static void initialize() {
        PocketDoorMod.LOGGER.info("Registered Pocket Door seat entity.");
    }
}
