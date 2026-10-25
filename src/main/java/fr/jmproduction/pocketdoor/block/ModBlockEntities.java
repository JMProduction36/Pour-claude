package fr.jmproduction.pocketdoor.block;

import fr.jmproduction.pocketdoor.PocketDoorMod;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntityType;

public final class ModBlockEntities {
    public static final BlockEntityType<TypewriterBlockEntity> TYPEWRITER = Registry.register(
            Registry.BLOCK_ENTITY_TYPE,
            new ResourceLocation(PocketDoorMod.MOD_ID, "typewriter"),
            BlockEntityType.Builder.of(TypewriterBlockEntity::new, ModBlocks.TYPEWRITER).build(null)
    );

    public static final BlockEntityType<FurnitureBlockEntity> FURNITURE = Registry.register(
            Registry.BLOCK_ENTITY_TYPE,
            new ResourceLocation(PocketDoorMod.MOD_ID, "furniture"),
            BlockEntityType.Builder.of(FurnitureBlockEntity::new, ModBlocks.DESK).build(null)
    );

    public static final BlockEntityType<ChairBlockEntity> POCKET_CHAIR = Registry.register(
            Registry.BLOCK_ENTITY_TYPE,
            new ResourceLocation(PocketDoorMod.MOD_ID, "pocket_chair"),
            BlockEntityType.Builder.of(ChairBlockEntity::new, ModBlocks.POCKET_CHAIR).build(null)
    );

    private ModBlockEntities() {
    }

    public static void initialize() {
        PocketDoorMod.LOGGER.info("Registered Pocket Door block entities.");
    }
}
