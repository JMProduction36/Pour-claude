package fr.jmproduction.pocketdoor.client;

import fr.jmproduction.pocketdoor.block.ModBlockEntities;
import fr.jmproduction.pocketdoor.block.ModBlocks;
import fr.jmproduction.pocketdoor.entity.ModEntities;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;

public final class ChairClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        BlockEntityRenderers.register(ModBlockEntities.POCKET_CHAIR, ChairBlockEntityRenderer::new);
        EntityRendererRegistry.register(ModEntities.CHAIR_SEAT, ChairSeatRenderer::new);
        BuiltinItemRendererRegistry.INSTANCE.register(ModBlocks.POCKET_CHAIR,
                (stack, mode, poseStack, buffers, light, overlay) ->
                        ChairBlockEntityRenderer.renderItem(poseStack, buffers, light, overlay));
    }
}
