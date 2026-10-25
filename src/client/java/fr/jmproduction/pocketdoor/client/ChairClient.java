package fr.jmproduction.pocketdoor.client;

import fr.jmproduction.pocketdoor.block.ModBlockEntities;
import fr.jmproduction.pocketdoor.block.ModBlocks;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;

/**
 * Enregistre le rendu du modèle de la chaise (bloc et objet). Déclaré dans fabric.mod.json.
 * Le siège est un ArmorStand vanilla : aucun rendu d'entité n'est à enregistrer ici.
 */
public final class ChairClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        BlockEntityRenderers.register(ModBlockEntities.POCKET_CHAIR, ChairBlockEntityRenderer::new);
        BuiltinItemRendererRegistry.INSTANCE.register(ModBlocks.POCKET_CHAIR,
                (stack, mode, poseStack, buffers, light, overlay) ->
                        ChairBlockEntityRenderer.renderItem(poseStack, buffers, light, overlay));
    }
}
