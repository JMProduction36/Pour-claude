package fr.jmproduction.pocketdoor.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Matrix3f;
import com.mojang.math.Matrix4f;
import com.mojang.math.Vector3f;
import fr.jmproduction.pocketdoor.block.TypewriterBlock;
import fr.jmproduction.pocketdoor.block.TypewriterBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public final class TypewriterBlockEntityRenderer implements BlockEntityRenderer<TypewriterBlockEntity> {
    private static final ResourceLocation PAPER_MODEL =
            new ResourceLocation("pocketdoor", "typewriter_model.pdmesh");
    private static final ResourceLocation NO_PAPER_MODEL =
            new ResourceLocation("pocketdoor", "typewriter_model_empty.pdmesh");
    private static final ResourceLocation[] TEXTURE_PATHS = {
            new ResourceLocation("pocketdoor", "textures/block/typewriter/typewriter_0.png"),
            new ResourceLocation("pocketdoor", "textures/block/typewriter/typewriter_1.png"),
            new ResourceLocation("pocketdoor", "textures/block/typewriter/typewriter_2.png"),
            new ResourceLocation("pocketdoor", "textures/block/typewriter/typewriter_3.png"),
            new ResourceLocation("pocketdoor", "textures/block/typewriter/typewriter_4.png"),
            new ResourceLocation("pocketdoor", "textures/block/typewriter/typewriter_5.png"),
            new ResourceLocation("pocketdoor", "textures/block/typewriter/typewriter_7.png"),
            new ResourceLocation("pocketdoor", "textures/block/typewriter/typewriter_8.png")
    };
    private static Mesh paperMesh;
    private static Mesh noPaperMesh;
    private static boolean attemptedPaperLoad;
    private static boolean attemptedNoPaperLoad;

    private static final class Vertex {
        final float x;
        final float y;
        final float z;
        final float u;
        final float v;

        Vertex(float x, float y, float z, float u, float v) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.u = u;
            this.v = v;
        }
    }

    private static final class Face {
        final float nx;
        final float ny;
        final float nz;
        final Vertex[] vertices;

        Face(float nx, float ny, float nz, Vertex[] vertices) {
            this.nx = nx;
            this.ny = ny;
            this.nz = nz;
            this.vertices = vertices;
        }
    }

    private static final class Mesh {
        final List<ResourceLocation> textures;
        final List<List<Face>> byTexture;

        Mesh(List<ResourceLocation> textures, List<List<Face>> byTexture) {
            this.textures = textures;
            this.byTexture = byTexture;
        }
    }

    public TypewriterBlockEntityRenderer(BlockEntityRendererProvider.Context ignored) {
    }

    @Override
    public void render(TypewriterBlockEntity blockEntity, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffers, int packedLight, int packedOverlay) {
        renderMesh(getMesh(blockEntity.hasBook()),
                blockEntity.getBlockState().getValue(TypewriterBlock.FACING),
                1.0F / 16.0F, poseStack, buffers, packedLight, packedOverlay);
    }

    public static void renderItem(ItemStack item, PoseStack poseStack, MultiBufferSource buffers,
                                  int packedLight, int packedOverlay) {
        renderMesh(getMesh(hasBook(item)), net.minecraft.core.Direction.SOUTH,
                0.42F / 16.0F, poseStack, buffers, packedLight, packedOverlay);
    }

    private static boolean hasBook(ItemStack stack) {
        if (!stack.hasTag() || !stack.getTag().contains("BlockEntityTag", 10)) return false;
        net.minecraft.nbt.CompoundTag blockEntityTag = stack.getTag().getCompound("BlockEntityTag");
        return blockEntityTag.getBoolean("HasBook") && blockEntityTag.contains("Book", 10);
    }

    private static void renderMesh(Mesh mesh, net.minecraft.core.Direction facing, float scale,
                                   PoseStack poseStack, MultiBufferSource buffers,
                                   int packedLight, int packedOverlay) {
        if (mesh == null) return;

        poseStack.pushPose();
        poseStack.translate(0.5D, 0.0D, 0.5D);
        poseStack.mulPose(Vector3f.YP.rotationDegrees(facing.toYRot()));
        poseStack.scale(scale, scale, scale);
        poseStack.translate(-8.0D, 0.0D, -8.0D);
        for (int textureIndex = 0; textureIndex < mesh.byTexture.size(); textureIndex++) {
            List<Face> faces = mesh.byTexture.get(textureIndex);
            if (faces.isEmpty()) continue;
            VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutoutNoCull(mesh.textures.get(textureIndex)));
            for (Face face : faces) emitFace(consumer, poseStack, face, packedLight, packedOverlay);
        }
        poseStack.popPose();
    }

    private static void emitFace(VertexConsumer consumer, PoseStack poseStack, Face face,
                                 int packedLight, int packedOverlay) {
        PoseStack.Pose pose = poseStack.last();
        Matrix4f poseMatrix = pose.pose();
        Matrix3f normalMatrix = pose.normal();
        for (Vertex vertex : face.vertices) {
            consumer.vertex(poseMatrix, vertex.x, vertex.y, vertex.z)
                    .color(255, 255, 255, 255)
                    .uv(vertex.u, vertex.v)
                    .overlayCoords(packedOverlay)
                    .uv2(packedLight)
                    .normal(normalMatrix, face.nx, face.ny, face.nz)
                    .endVertex();
        }
    }

    private static Mesh getMesh(boolean hasBook) {
        if (hasBook) {
            if (paperMesh == null && !attemptedPaperLoad) {
                attemptedPaperLoad = true;
                paperMesh = loadMesh(PAPER_MODEL);
            }
            if (paperMesh != null) return paperMesh;
            return getMesh(false);
        }
        if (noPaperMesh == null && !attemptedNoPaperLoad) {
            attemptedNoPaperLoad = true;
            noPaperMesh = loadMesh(NO_PAPER_MODEL);
        }
        if (noPaperMesh != null) return noPaperMesh;
        if (paperMesh == null && !attemptedPaperLoad) {
            attemptedPaperLoad = true;
            paperMesh = loadMesh(PAPER_MODEL);
        }
        return paperMesh;
    }

    private static Mesh loadMesh(ResourceLocation model) {
        try {
            var resource = Minecraft.getInstance().getResourceManager().getResource(model);
            if (resource.isEmpty()) return null;
            try (InputStream stream = resource.get().open(); DataInputStream in = new DataInputStream(stream)) {
                byte[] magic = new byte[4];
                in.readFully(magic);
                if (magic[0] != 'P' || magic[1] != 'D' || magic[2] != 'M' || magic[3] != '1') {
                    throw new IOException("Invalid typewriter mesh header");
                }
                int textureCount = in.readInt();
                if (textureCount <= 0 || textureCount > TEXTURE_PATHS.length) {
                    throw new IOException("Invalid typewriter texture count: " + textureCount);
                }
                List<ResourceLocation> textures = new ArrayList<>(textureCount);
                for (int i = 0; i < textureCount; i++) {
                    int length = in.readUnsignedShort();
                    byte[] bytes = new byte[length];
                    in.readFully(bytes);
                    String textureName = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
                    String expectedName = TEXTURE_PATHS[i].getPath();
                    expectedName = expectedName.substring(expectedName.lastIndexOf('/') + 1);
                    if (!expectedName.equals(textureName)) {
                        throw new IOException("Unexpected typewriter texture: " + textureName);
                    }
                    textures.add(TEXTURE_PATHS[i]);
                }
                List<List<Face>> byTexture = new ArrayList<>(textureCount);
                for (int i = 0; i < textureCount; i++) byTexture.add(new ArrayList<>());
                int faceCount = in.readInt();
                if (faceCount < 0 || faceCount > 100000) throw new IOException("Invalid typewriter face count");
                for (int i = 0; i < faceCount; i++) {
                    int textureIndex = in.readInt();
                    float nx = in.readFloat();
                    float ny = in.readFloat();
                    float nz = in.readFloat();
                    int vertexCount = in.readInt();
                    if (textureIndex < 0 || textureIndex >= textureCount || vertexCount < 3 || vertexCount > 64) {
                        throw new IOException("Invalid typewriter face data");
                    }
                    Vertex[] vertices = new Vertex[vertexCount];
                    for (int v = 0; v < vertexCount; v++) {
                        vertices[v] = new Vertex(in.readFloat(), in.readFloat(), in.readFloat(),
                                in.readFloat(), in.readFloat());
                    }
                    byTexture.get(textureIndex).add(new Face(nx, ny, nz, vertices));
                }
                return new Mesh(textures, byTexture);
            }
        } catch (Exception e) {
            Minecraft.getInstance().execute(() ->
                    System.err.println("[PocketDoor] Unable to load typewriter mesh: " + e));
            return null;
        }
    }

    @Override
    public boolean shouldRenderOffScreen(TypewriterBlockEntity blockEntity) {
        return true;
    }
}
