package fr.jmproduction.pocketdoor.dimension;

import fr.jmproduction.pocketdoor.PocketDoorMod;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

public final class PocketDimensions {
    public static final ResourceKey<Level> POCKET_OFFICE = ResourceKey.create(
            Registry.DIMENSION_REGISTRY,
            new ResourceLocation(PocketDoorMod.MOD_ID, "pocket_office")
    );

    private PocketDimensions() {
    }
}
