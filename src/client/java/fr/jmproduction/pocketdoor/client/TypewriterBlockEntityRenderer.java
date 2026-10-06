package fr.jmproduction.pocketdoor.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Matrix3f;
import com.mojang.math.Matrix4f;
import fr.jmproduction.pocketdoor.block.TypewriterBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Renders the user's Blockbench typewriter model as a baked triangle mesh.
 * The Blockbench rotations, UVs and material textures are preprocessed into
 * typewriter_model.pdmesh so the game only has to stream the vertices once.
 */
public final class TypewriterBlockEntityRenderer implements BlockEntityRenderer<TypewriterBlockEntity> {
    private static final ResourceLocation MODEL =
            new ResourceLocation("pocketdoor", "typewriter_model.pdmesh");

    private static final class Vertex {
        final float x, y, z, u, v;
        Vertex(float x, float y, float z, float u, float v) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.u = u;
            this.v = v;
        }
    }

    private static final class Face {
        final int texture;
        final float nx, ny, nz;
        final Vertex[] vertices;
        Face(int texture, float nx, float ny, float nz, Vertex[] vertices) {
            this.texture = texture;
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

    private static Mesh mesh;
    private static boolean attemptedLoad;

    public TypewriterBlockEntityRenderer(BlockEntityRendererProvider.Context ignored) {
    }

    @Override
    public void render(TypewriterBlockEntity blockEntity, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffers, int packedLight, int packedOverlay) {
        Mesh loaded = getMesh();
        if (loaded == null) {
            return;
        }

        poseStack.pushPose();
        poseStack.translate(0.5D, 1.0D / 16.0D, 0.5D);

        // The source model is authored facing the desk. Keep it fixed in the office rather
        // than making it follow the player's camera.
        for (int textureIndex = 0; textureIndex < loaded.byTexture.size(); textureIndex++) {
            List<Face> faces = loaded.byTexture.get(textureIndex);
            if (faces.isEmpty()) continue;

            VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutoutNoCull(
                    loaded.textures.get(textureIndex)));
            for (Face face : faces) {
                emitFace(consumer, poseStack, face, packedLight, packedOverlay);
            }
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

    private static Mesh getMesh() {
        if (mesh != null || attemptedLoad) {
            return mesh;
        }
        attemptedLoad = true;

        try {
            Minecraft minecraft = Minecraft.getInstance();
            var resource = minecraft.getResourceManager().getResource(MODEL);
            if (resource.isEmpty()) {
                return null;
            }

            try (InputStream stream = resource.get().getInputStream();
                 DataInputStream in = new DataInputStream(stream)) {
                byte[] magic = new byte[4];
                in.readFully(magic);
                if (magic[0] != 'P' || magic[1] != 'D' || magic[2] != 'M' || magic[3] != '1') {
                    throw new IOException("Invalid Pocket Door typewriter mesh header");
                }

                int textureCount = in.readInt();
                if (textureCount <= 0 || textureCount > 16) {
                    throw new IOException("Invalid typewriter texture count: " + textureCount);
                }

                List<ResourceLocation> textures = new ArrayList<>(textureCount);
                for (int i = 0; i < textureCount; i++) {
                    int length = in.readUnsignedShort();
                    byte[] bytes = new byte[length];
                    in.readFully(bytes);
                    textures.add(new ResourceLocation("pocketdoor", "textures/block/typewriter/" + new String(bytes, java.nio.charset.StandardCharsets.UTF_8)));
                }

                List<List<Face>> byTexture = new ArrayList<>(textureCount);
                for (int i = 0; i < textureCount; i++) byTexture.add(new ArrayList<>());

                int faceCount = in.readInt();
                if (faceCount < 0 || faceCount > 100000) {
                    throw new IOException("Invalid typewriter face count: " + faceCount);
                }

                for (int i = 0; i < faceCount; i++) {
                    int tex = in.readInt();
                    float nx = in.readFloat();
                    float ny = in.readFloat();
                    float nz = in.readFloat();
                    int vertexCount = in.readInt();
                    if (tex < 0 || tex >= textureCount || vertexCount < 3 || vertexCount > 64) {
                        throw new IOException("Invalid typewriter face data");
                    }
                    Vertex[] vertices = new Vertex[vertexCount];
                    for (int v = 0; v < vertexCount; v++) {
                        vertices[v] = new Vertex(
                                in.readFloat(), in.readFloat(), in.readFloat(),
                                in.readFloat(), in.readFloat());
                    }
                    byTexture.get(tex).add(new Face(tex, nx, ny, nz, vertices));
                }

                mesh = new Mesh(textures, byTexture);
                return mesh;
            }
        } catch (Exception e) {
            // Do not crash Minecraft because a visual-only optional asset failed to load.
            Minecraft.getInstance().execute(() ->
                    System.err.println("[PocketDoor] Unable to load typewriter mesh: " + e));
            return null;
        }
    }

    @Override
    public boolean rendersOutsideBoundingBox(TypewriterBlockEntity blockEntity) {
        return true;
    }
}
