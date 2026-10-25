package fr.jmproduction.pocketdoor.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Matrix3f;
import com.mojang.math.Matrix4f;
import com.mojang.math.Vector3f;
import fr.jmproduction.pocketdoor.PocketDoorMod;
import fr.jmproduction.pocketdoor.block.ChairBlock;
import fr.jmproduction.pocketdoor.block.ChairBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class ChairBlockEntityRenderer implements BlockEntityRenderer<ChairBlockEntity> {
    private static final ResourceLocation MODEL =
            new ResourceLocation("pocketdoor", "furniture/chair.pdmesh");
    private static final String TEXTURE_DIR = "textures/block/furniture/chair/";
    private static final Pattern TEXTURE_NAME = Pattern.compile("[A-Za-z0-9_-]+\\.png");
    private static Mesh mesh;
    private static boolean attemptedLoad;

    private static final class Vertex {
        final float x, y, z, u, v;
        Vertex(float x, float y, float z, float u, float v) {
            this.x = x; this.y = y; this.z = z; this.u = u; this.v = v;
        }
    }

    private static final class Face {
        final float nx, ny, nz;
        final Vertex[] vertices;
        Face(float nx, float ny, float nz, Vertex[] vertices) {
            this.nx = nx; this.ny = ny; this.nz = nz; this.vertices = vertices;
        }
    }

    private static final class Mesh {
        final List<ResourceLocation> textures;
        final List<List<Face>> facesByTexture;
        Mesh(List<ResourceLocation> textures, List<List<Face>> facesByTexture) {
            this.textures = textures; this.facesByTexture = facesByTexture;
        }
    }

    public ChairBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(ChairBlockEntity blockEntity, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffers, int packedLight, int packedOverlay) {
        Direction facing = blockEntity.getBlockState().getValue(ChairBlock.FACING);
        renderMesh(facing, poseStack, buffers, packedLight, packedOverlay);
    }

    public static void renderItem(PoseStack poseStack, MultiBufferSource buffers,
                                  int packedLight, int packedOverlay) {
        renderMesh(Direction.SOUTH, poseStack, buffers, packedLight, packedOverlay);
    }

    private static void renderMesh(Direction facing, PoseStack poseStack, MultiBufferSource buffers,
                                   int packedLight, int packedOverlay) {
        Mesh current = getMesh();
        if (current == null) return;

        float rotation = switch (facing) {
            case NORTH -> 180.0F;
            case EAST -> 90.0F;
            case WEST -> 270.0F;
            default -> 0.0F;
        };

        poseStack.pushPose();
        poseStack.translate(0.5D, 0.0D, 0.5D);
        poseStack.mulPose(Vector3f.YP.rotationDegrees(rotation));
        // The Blockbench chair is about 21.5 model units tall; scale it to roughly 2 Minecraft blocks.
        poseStack.scale(1.0F / 10.75F, 1.0F / 10.75F, 1.0F / 10.75F);

        for (int i = 0; i < current.facesByTexture.size(); i++) {
            List<Face> faces = current.facesByTexture.get(i);
            if (faces.isEmpty()) continue;
            VertexConsumer consumer = buffers.getBuffer(
                    RenderType.entityCutoutNoCull(current.textures.get(i)));
            for (Face face : faces) {
                emitFace(consumer, poseStack, face, packedLight, packedOverlay);
            }
        }
        poseStack.popPose();
    }

    private static Mesh getMesh() {
        if (!attemptedLoad) {
            attemptedLoad = true;
            mesh = loadMesh();
        }
        return mesh;
    }

    private static Mesh loadMesh() {
        try {
            var resource = Minecraft.getInstance().getResourceManager().getResource(MODEL);
            if (resource.isEmpty()) {
                PocketDoorMod.LOGGER.error("Missing chair mesh resource: {}", MODEL);
                return null;
            }

            try (InputStream stream = resource.get().open(); DataInputStream in = new DataInputStream(stream)) {
                byte[] magic = new byte[4];
                in.readFully(magic);
                if (magic[0] != 'P' || magic[1] != 'D' || magic[2] != 'M' || magic[3] != '1') {
                    throw new IOException("Invalid chair mesh header");
                }

                int textureCount = in.readInt();
                if (textureCount < 1 || textureCount > 32) {
                    throw new IOException("Invalid chair texture count: " + textureCount);
                }

                List<ResourceLocation> textures = new ArrayList<>(textureCount);
                for (int i = 0; i < textureCount; i++) {
                    int length = in.readUnsignedShort();
                    byte[] nameBytes = new byte[length];
                    in.readFully(nameBytes);
                    String name = new String(nameBytes, StandardCharsets.UTF_8);
                    if (!TEXTURE_NAME.matcher(name).matches()) {
                        throw new IOException("Unexpected chair texture: " + name);
                    }
                    textures.add(new ResourceLocation("pocketdoor", TEXTURE_DIR + name));
                }

                List<List<Face>> facesByTexture = new ArrayList<>(textureCount);
                for (int i = 0; i < textureCount; i++) facesByTexture.add(new ArrayList<>());

                int faceCount = in.readInt();
                if (faceCount < 0 || faceCount > 100000) {
                    throw new IOException("Invalid chair face count: " + faceCount);
                }

                for (int i = 0; i < faceCount; i++) {
                    int textureIndex = in.readInt();
                    float nx = in.readFloat();
                    float ny = in.readFloat();
                    float nz = in.readFloat();
                    int vertexCount = in.readInt();
                    if (textureIndex < 0 || textureIndex >= textureCount
                            || vertexCount < 3 || vertexCount > 64) {
                        throw new IOException("Invalid chair face data");
                    }

                    Vertex[] vertices = new Vertex[vertexCount];
                    for (int v = 0; v < vertexCount; v++) {
                        float x = in.readFloat();
                        float y = in.readFloat();
                        float z = in.readFloat();
                        float u = in.readFloat();
                        float vCoord = in.readFloat();
                        // The exported Blockbench UVs are in texture pixels, while Minecraft expects 0..1.
                        float textureSize = "1.png".equals(nameForTexture(textures, textureIndex)) ? 32.0F : 16.0F;
                        vertices[v] = new Vertex(x, y, z, u / textureSize, vCoord / textureSize);
                    }
                    addAsQuads(facesByTexture.get(textureIndex), nx, ny, nz, vertices);
                }
                return new Mesh(textures, facesByTexture);
            }
        } catch (Exception e) {
            PocketDoorMod.LOGGER.error("Unable to load chair mesh {}", MODEL, e);
            return null;
        }
    }

    private static String nameForTexture(List<ResourceLocation> textures, int index) {
        return textures.get(index).getPath().substring(TEXTURE_DIR.length());
    }

    private static void addAsQuads(List<Face> output, float nx, float ny, float nz, Vertex[] vertices) {
        if (vertices.length == 4) {
            output.add(new Face(nx, ny, nz, vertices));
            return;
        }
        for (int i = 1; i < vertices.length - 1; i++) {
            output.add(new Face(nx, ny, nz,
                    new Vertex[]{vertices[0], vertices[i], vertices[i + 1], vertices[i + 1]}));
        }
    }

    private static void emitFace(VertexConsumer consumer, PoseStack poseStack, Face face,
                                 int packedLight, int packedOverlay) {
        PoseStack.Pose pose = poseStack.last();
        Matrix4f position = pose.pose();
        Matrix3f normal = pose.normal();
        for (Vertex vertex : face.vertices) {
            consumer.vertex(position, vertex.x, vertex.y, vertex.z)
                    .color(255, 255, 255, 255)
                    .uv(vertex.u, vertex.v)
                    .overlayCoords(packedOverlay)
                    .uv2(packedLight)
                    .normal(normal, face.nx, face.ny, face.nz)
                    .endVertex();
        }
    }
}
