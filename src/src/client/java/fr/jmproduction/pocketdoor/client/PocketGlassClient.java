package fr.jmproduction.pocketdoor.client;

import fr.jmproduction.pocketdoor.block.ModBlocks;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.minecraft.client.renderer.RenderType;

/** Keeps the custom blast-proof Pocket Office glass rendered translucently like vanilla glass. */
public final class PocketGlassClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.POCKET_GLASS, RenderType.translucent());
    }
}
