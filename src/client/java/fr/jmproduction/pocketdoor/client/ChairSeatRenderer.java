package fr.jmproduction.pocketdoor.client;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.jmproduction.pocketdoor.entity.ChairSeatEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

public final class ChairSeatRenderer extends EntityRenderer<ChairSeatEntity> {
    private static final ResourceLocation DUMMY_TEXTURE =
            new ResourceLocation("minecraft", "textures/misc/white.png");

    public ChairSeatRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0F;
    }

    @Override
    public void render(ChairSeatEntity entity, float entityYaw, float partialTick,
                      PoseStack poseStack, MultiBufferSource buffers, int packedLight) {
        // The seat exists only to carry the player. It deliberately renders nothing.
    }

    @Override
    public ResourceLocation getTextureLocation(ChairSeatEntity entity) {
        return DUMMY_TEXTURE;
    }
}
