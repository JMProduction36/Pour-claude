package fr.jmproduction.pocketdoor.block;

import fr.jmproduction.pocketdoor.PocketDoorMod;
import net.fabricmc.fabric.api.client.itemgroup.FabricItemGroupBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.List;

public final class ModItemGroups {
    private static final List<Block> ITEMS = new ArrayList<>();
    private static boolean initialized;

    public static final CreativeModeTab POCKETDOOR = FabricItemGroupBuilder.create(
            new ResourceLocation(PocketDoorMod.MOD_ID, "items"))
            .icon(() -> new ItemStack(ModBlocks.TYPEWRITER))
            .appendItems((stacks, group) -> {
                initialize();
                for (Block block : ITEMS) {
                    // Jamais deux fois le même objet dans l'onglet.
                    Item item = block.asItem();
                    if (stacks.stream().noneMatch(stack -> stack.is(item))) {
                        stacks.add(new ItemStack(item));
                    }
                }
            })
            .build();

    public static void add(Block block) {
        ITEMS.add(block);
    }

    public static void initialize() {
        if (initialized) return;
        add(ModBlocks.POCKET_DOOR);
        initialized = true;
    }

    private ModItemGroups() {
    }
}
