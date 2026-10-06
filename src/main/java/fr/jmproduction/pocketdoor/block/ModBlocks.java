package fr.jmproduction.pocketdoor.block;

import fr.jmproduction.pocketdoor.PocketDoorMod;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;

public final class ModBlocks {
    public static final Block POCKET_DOOR = register(
            "pocket_door",
            new PocketDoorBlock(BlockBehaviour.Properties.copy(Blocks.OAK_DOOR)
                    .strength(-1.0F, 3600000.0F)
                    .noLootTable())
    );

    /** Looks exactly like vanilla oak planks but cannot be broken by players or explosions. */
    public static final Block POCKET_OAK_PLANKS = registerBlockOnly(
            "pocket_oak_planks",
            new PocketWallBlock(BlockBehaviour.Properties.copy(Blocks.OAK_PLANKS)
                    .strength(-1.0F, 3600000.0F)
                    .noLootTable())
    );

    /** Invisible seat block used by the procedural office. */
    public static final Block POCKET_CHAIR = registerBlockOnly(
            "pocket_chair",
            new ChairBlock(BlockBehaviour.Properties.copy(Blocks.OAK_PLANKS)
                    .noOcclusion()
                    .strength(0.2F))
    );

    public static final Block TYPEWRITER = register(
            "typewriter",
            new TypewriterBlock(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK)
                    .strength(2.0F)),
            true
    );

    private ModBlocks() {
    }

    private static Block register(String name, Block block) {
        return register(name, block, false);
    }

    private static Block register(String name, Block block, boolean includeInItemGroup) {
        ResourceLocation id = new ResourceLocation(PocketDoorMod.MOD_ID, name);
        Registry.register(Registry.BLOCK, id, block);
        Registry.register(Registry.ITEM, id, new BlockItem(block,
                includeInItemGroup ? new Item.Properties().tab(ModItemGroups.POCKETDOOR) : new Item.Properties()));
        if (includeInItemGroup) {
            ModItemGroups.add(block);
        }
        return block;
    }

    private static Block registerBlockOnly(String name, Block block) {
        ResourceLocation id = new ResourceLocation(PocketDoorMod.MOD_ID, name);
        Registry.register(Registry.BLOCK, id, block);
        return block;
    }

    public static void initialize() {
        PocketDoorMod.LOGGER.info("Registered Pocket Door blocks and pocket-office blocks.");
    }

}
