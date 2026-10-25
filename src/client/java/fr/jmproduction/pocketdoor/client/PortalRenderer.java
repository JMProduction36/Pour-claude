package fr.jmproduction.pocketdoor.client;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.jmproduction.pocketdoor.portal.Portal;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/**
 * Minimal client renderer for the {@link Portal} entity. The portal itself is a
 * purely logical entity — the actual frame is drawn by {@link PocketPortalRenderer}
 * using {@code WorldRenderEvents} — so this renderer only exists to satisfy
 * Fabric's EntityRenderer registry and to keep the client from throwing
 * &quot;no renderer registered&quot; when a spawn packet arrives.
 */
public final class PortalRenderer extends EntityRenderer<Portal> {
    private static final ResourceLocation DUMMY_TEXTURE =
            new ResourceLocation("minecraft", "textures/misc/white.png");

    public PortalRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0F;
    }

    @Override
    public void render(Portal entity, float entityYaw, float partialTick,
                      PoseStack poseStack, MultiBufferSource buffers, int packedLight) {
        // Intentionally empty: portal visuals come from PocketPortalRenderer.
    }

    @Override
    public ResourceLocation getTextureLocation(Portal entity) {
        return DUMMY_TEXTURE;
    }
}
