package fr.jmproduction.pocketdoor.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Matrix3f;
import com.mojang.math.Matrix4f;
import com.mojang.math.Vector3f;
import fr.jmproduction.pocketdoor.PocketDoorMod;
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
import java.util.regex.Pattern;

public final class TypewriterBlockEntityRenderer implements BlockEntityRenderer<TypewriterBlockEntity> {
    private static final ResourceLocation PAPER_MODEL =
            new ResourceLocation("pocketdoor", "typewriter_model.pdmesh");
    private static final ResourceLocation NO_PAPER_MODEL =
            new ResourceLocation("pocketdoor", "typewriter_model_empty.pdmesh");
    /** Les noms de textures viennent du .pdmesh : plus de liste à garder synchronisée à la main. */
    private static final String TEXTURE_DIR = "textures/block/typewriter/";
    private static final Pattern TEXTURE_NAME = Pattern.compile("typewriter_\\d{1,2}\\.png");
    private static final int MAX_TEXTURES = 32;
    private static Mesh paperMesh;
    /** Hitbox de la machine (en pixels) : 16 x 10 x 16. Le bloc réduit le modèle pour qu'il tienne dedans. */
    private static final float HITBOX_HEIGHT_PX = 10.0F;
    private static final float HITBOX_FOOTPRINT_PX = 16.0F;
    /** Échelle du bloc posé (1.0 = taille d'origine du maillage), calculée au chargement. */
    private static float blockFit = 1.0F;

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
        /** Dimensions réelles du maillage (pixels du .pdmesh), mesurées au chargement. */
        final float extentX;
        final float extentZ;
        final float height;
        /** Rattrape un éventuel écart d'échelle entre le modèle vide et le modèle avec papier. */
        float relativeScale = 1.0F;

        Mesh(List<ResourceLocation> textures, List<List<Face>> byTexture,
             float extentX, float extentZ, float height) {
            this.textures = textures;
            this.byTexture = byTexture;
            this.extentX = extentX;
            this.extentZ = extentZ;
            this.height = height;
        }
    }

    public TypewriterBlockEntityRenderer(BlockEntityRendererProvider.Context ignored) {
    }

    @Override
    public void render(TypewriterBlockEntity blockEntity, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffers, int packedLight, int packedOverlay) {
        renderMesh(getMesh(blockEntity.hasBook()),
                blockEntity.getBlockState().getValue(TypewriterBlock.FACING),
                blockFit / 16.0F, poseStack, buffers, packedLight, packedOverlay);
    }

    public static void renderItem(ItemStack item, PoseStack poseStack, MultiBufferSource buffers,
                                  int packedLight, int packedOverlay) {
        renderMesh(getMesh(hasBook(item)), net.minecraft.core.Direction.EAST,
                1.0F / 16.0F, poseStack, buffers, packedLight, packedOverlay);
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
        // Le maillage est centré sur x/z autour de 0 (voir l'export) : aucun décalage à ajouter.
        // L'avant du modèle (clavier) regarde vers +X ; on le tourne pour qu'il regarde vers "facing".
        poseStack.mulPose(Vector3f.YP.rotationDegrees(-facing.toYRot() - 90.0F));
        float s = scale * mesh.relativeScale;
        poseStack.scale(s, s, s);
        for (int textureIndex = 0; textureIndex < mesh.byTexture.size(); textureIndex++) {
            List<Face> faces = mesh.byTexture.get(textureIndex);
            if (faces.isEmpty()) continue;
            VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutoutNoCull(mesh.textures.get(textureIndex)));
            for (Face face : faces) emitFace(consumer, poseStack, face, packedLight, packedOverlay);
        }
        poseStack.popPose();
    }

    /** RenderType.entityCutoutNoCull dessine des QUADS : un triangle devient un quad dont le 4e sommet répète le 3e. */
    private static void addAsQuads(List<Face> out, float nx, float ny, float nz, Vertex[] v) {
        if (v.length == 4) {
            out.add(new Face(nx, ny, nz, v));
            return;
        }
        for (int i = 1; i < v.length - 1; i++) {
            out.add(new Face(nx, ny, nz, new Vertex[] {v[0], v[i], v[i + 1], v[i + 1]}));
        }
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
        loadMeshesOnce();
        if (hasBook) return paperMesh != null ? paperMesh : noPaperMesh;
        return noPaperMesh != null ? noPaperMesh : paperMesh;
    }

    private static void loadMeshesOnce() {
        if (attemptedPaperLoad && attemptedNoPaperLoad) return;
        if (!attemptedPaperLoad) {
            attemptedPaperLoad = true;
            paperMesh = loadMesh(PAPER_MODEL);
        }
        if (!attemptedNoPaperLoad) {
            attemptedNoPaperLoad = true;
            noPaperMesh = loadMesh(NO_PAPER_MODEL);
        }
        // Les deux modèles représentent la même machine : leur profondeur (z) doit être identique.
        if (paperMesh != null && noPaperMesh != null && noPaperMesh.extentZ > 0.0F && paperMesh.extentZ > 0.0F) {
            noPaperMesh.relativeScale = paperMesh.extentZ / noPaperMesh.extentZ;
        }
        // Échelle du bloc : le modèle le plus grand (avec papier) doit tenir dans la hitbox.
        Mesh reference = paperMesh != null ? paperMesh : noPaperMesh;
        if (reference != null && reference.height > 0.0F) {
            float horizontal = Math.max(reference.extentX, reference.extentZ);
            blockFit = Math.min(HITBOX_HEIGHT_PX / reference.height,
                    horizontal > 0.0F ? HITBOX_FOOTPRINT_PX / horizontal : Float.MAX_VALUE);
        }
    }

    private static Mesh loadMesh(ResourceLocation model) {
        try {
            var resource = Minecraft.getInstance().getResourceManager().getResource(model);
            if (resource.isEmpty()) {
                PocketDoorMod.LOGGER.error("Missing typewriter mesh resource: {}", model);
                return null;
            }
            try (InputStream stream = resource.get().open(); DataInputStream in = new DataInputStream(stream)) {
                byte[] magic = new byte[4];
                in.readFully(magic);
                if (magic[0] != 'P' || magic[1] != 'D' || magic[2] != 'M' || magic[3] != '1') {
                    throw new IOException("Invalid typewriter mesh header");
                }
                int textureCount = in.readInt();
                if (textureCount <= 0 || textureCount > MAX_TEXTURES) {
                    throw new IOException("Invalid typewriter texture count: " + textureCount);
                }
                List<ResourceLocation> textures = new ArrayList<>(textureCount);
                for (int i = 0; i < textureCount; i++) {
                    int length = in.readUnsignedShort();
                    byte[] bytes = new byte[length];
                    in.readFully(bytes);
                    String textureName = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
                    if (!TEXTURE_NAME.matcher(textureName).matches()) {
                        throw new IOException("Unexpected typewriter texture: " + textureName);
                    }
                    textures.add(new ResourceLocation("pocketdoor", TEXTURE_DIR + textureName));
                }
                List<List<Face>> byTexture = new ArrayList<>(textureCount);
                for (int i = 0; i < textureCount; i++) byTexture.add(new ArrayList<>());
                int faceCount = in.readInt();
                if (faceCount < 0 || faceCount > 100000) throw new IOException("Invalid typewriter face count");
                float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE;
                float minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
                float minZ = Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
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
                        minX = Math.min(minX, vertices[v].x);
                        maxX = Math.max(maxX, vertices[v].x);
                        minY = Math.min(minY, vertices[v].y);
                        maxY = Math.max(maxY, vertices[v].y);
                        minZ = Math.min(minZ, vertices[v].z);
                        maxZ = Math.max(maxZ, vertices[v].z);
                    }
                    addAsQuads(byTexture.get(textureIndex), nx, ny, nz, vertices);
                }
                return new Mesh(textures, byTexture, maxX - minX, maxZ - minZ, maxY - minY);
            }
        } catch (Exception e) {
            PocketDoorMod.LOGGER.error("Unable to load typewriter mesh {}", model, e);
            return null;
        }
    }
}
