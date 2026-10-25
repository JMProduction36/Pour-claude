package fr.jmproduction.pocketdoor.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import fr.jmproduction.pocketdoor.block.ModBlocks;
import fr.jmproduction.pocketdoor.dimension.PocketDimensions;
import fr.jmproduction.pocketdoor.dimension.PocketOfficeGenerator;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.world.level.LightLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Client-only portal illusion. It deliberately does not load the other ClientLevel:
 * the pocket office (or a captured exterior) is rendered as geometry directly behind
 * the open doorway. This keeps the actual dimension transfer instant.
 */
public final class PocketPortalRenderer {
    private static final double SEARCH_RADIUS = 10.0D;
    private static final int OFFICE_LIGHT = LightTexture.FULL_BRIGHT;
    private static final int SNAPSHOT_RADIUS = 10;
    private static final int SNAPSHOT_VERTICAL_BELOW = 2;
    private static final int SNAPSHOT_VERTICAL_ABOVE = 10;
    private static final int SNAPSHOT_REFRESH_TICKS = 5;
    // Move the pocket-office preview one block to the right relative to the doorway.
    private static final double OFFICE_LATERAL_OFFSET = 1.0D;

    // Visible aperture used for portal clipping. The fake world is only rendered when
    // its geometry can actually project through this rectangle. Keeping a slightly
    // inset clip rectangle prevents the preview from leaking outside the real door frame.
    private static final double CLIP_HALF_WIDTH = 0.39D;
    private static final double CLIP_MIN_Y = 0.06D;
    private static final double CLIP_MAX_Y = 1.95D;
    private static final double CLIP_DEPTH = 0.055D;
    private static final double CLIP_EPSILON = 0.025D;

    private static final List<PreviewBlock> OFFICE_PREVIEW = buildOfficePreview();

    private static final List<PreviewBlock> exteriorSnapshot = new ArrayList<>();
    private static BlockPos snapshotDoor = null;
    private static Direction snapshotFacing = Direction.NORTH;
    private static long snapshotLastRefresh = Long.MIN_VALUE;

    private PocketPortalRenderer() {}

    public static void initialize() {
        WorldRenderEvents.AFTER_ENTITIES.register(PocketPortalRenderer::render);
    }

    private static void render(WorldRenderContext context) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null) return;

        if (level.dimension().equals(PocketDimensions.POCKET_OFFICE)) {
            renderPocketToExterior(context, level);
            return;
        }

        DoorRef door = findOpenExteriorDoor(level, mc.player.position());
        if (door == null) {
            exteriorSnapshot.clear();
            snapshotDoor = null;
            snapshotLastRefresh = Long.MIN_VALUE;
            return;
        }

        captureExteriorSnapshot(level, door);
        renderOfficeBehindExteriorDoor(context, level, door);
    }

    /**
     * Captures a small live block snapshot around the exterior doorway. The snapshot is
     * refreshed while the door remains open, rather than being frozen on the first frame.
     * This keeps the portal useful while the world around the doorway changes.
     */
    private static void captureExteriorSnapshot(ClientLevel level, DoorRef door) {
        long gameTime = level.getGameTime();
        boolean sameDoor = snapshotDoor != null
                && snapshotDoor.equals(door.lower)
                && snapshotFacing == door.facing;

        if (sameDoor && !exteriorSnapshot.isEmpty()
                && gameTime - snapshotLastRefresh < SNAPSHOT_REFRESH_TICKS) {
            return;
        }

        exteriorSnapshot.clear();
        snapshotDoor = door.lower;
        snapshotFacing = door.facing;
        snapshotLastRefresh = gameTime;

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = -SNAPSHOT_RADIUS; dx <= SNAPSHOT_RADIUS; dx++) {
            for (int dy = -SNAPSHOT_VERTICAL_BELOW; dy <= SNAPSHOT_VERTICAL_ABOVE; dy++) {
                for (int dz = -SNAPSHOT_RADIUS; dz <= SNAPSHOT_RADIUS; dz++) {
                    cursor.set(door.lower.getX() + dx, door.lower.getY() + dy, door.lower.getZ() + dz);
                    BlockState state = level.getBlockState(cursor);
                    if (state.isAir() || state.is(ModBlocks.POCKET_DOOR)) continue;

                    int blockLight = level.getBrightness(LightLayer.BLOCK, cursor);
                    int skyLight = level.getBrightness(LightLayer.SKY, cursor);
                    int light = LightTexture.pack(blockLight, skyLight);
                    exteriorSnapshot.add(new PreviewBlock(dx, dy, dz, state, light == 0 ? OFFICE_LIGHT : light));
                }
            }
        }
    }

    private static void renderOfficeBehindExteriorDoor(WorldRenderContext context, ClientLevel level, DoorRef door) {
        PoseStack matrices = context.matrixStack();
        Vec3 camera = context.camera().getPosition();
        Vec3 doorCenter = new Vec3(door.lower.getX() + 0.5D, door.lower.getY(), door.lower.getZ() + 0.5D);

        Direction interior = door.facing.getOpposite();
        Direction width = widthAxis(interior);

        // First, completely cover the real exterior world inside the doorway.
        // Without this aperture, the fake office geometry leaves gaps through which
        // the current world's blocks/walls are still visible.
        renderPortalBackdrop(context, matrices, camera, doorCenter, interior, width);

        PortalClip clip = PortalClip.create(camera, doorCenter, interior, width, CLIP_DEPTH,
                CLIP_HALF_WIDTH, CLIP_MIN_Y, CLIP_MAX_Y);

        renderBlocksOverlayClipped(context, matrices, camera, OFFICE_PREVIEW, preview -> {
            double depth = 15.5D - preview.z;
            double worldX = doorCenter.x + interior.getStepX() * depth + width.getStepX() * (preview.x - 8.5D + OFFICE_LATERAL_OFFSET);
            double worldZ = doorCenter.z + interior.getStepZ() * depth + width.getStepZ() * (preview.x - 8.5D + OFFICE_LATERAL_OFFSET);
            double worldY = door.lower.getY() - 1.0D + preview.y;
            return new Vec3(worldX, worldY, worldZ);
        }, clip);
    }

    /** Opaque inset mask that removes the actual exterior from the open doorway. */
    private static void renderPortalBackdrop(WorldRenderContext context, PoseStack matrices, Vec3 camera,
                                             Vec3 center, Direction depth, Direction width) {
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.getBuilder();

        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        final float r = 0.012F;
        final float g = 0.018F;
        final float b = 0.028F;
        final float a = 1.0F;
        final double portalDepth = 0.055D;
        final double halfWidth = 0.405D;
        final double minY = 0.045D;
        final double maxY = 1.965D;

        double x1 = center.x + depth.getStepX() * portalDepth + width.getStepX() * (-halfWidth) - camera.x;
        double x2 = center.x + depth.getStepX() * portalDepth + width.getStepX() * (halfWidth) - camera.x;
        double z1 = center.z + depth.getStepZ() * portalDepth + width.getStepZ() * (-halfWidth) - camera.z;
        double z2 = center.z + depth.getStepZ() * portalDepth + width.getStepZ() * (halfWidth) - camera.z;
        double y1 = center.y + minY - camera.y;
        double y2 = center.y + maxY - camera.y;

        matrices.pushPose();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        buffer.vertex(matrices.last().pose(), (float)x1, (float)y1, (float)z1).color(r, g, b, a).endVertex();
        buffer.vertex(matrices.last().pose(), (float)x2, (float)y1, (float)z2).color(r, g, b, a).endVertex();
        buffer.vertex(matrices.last().pose(), (float)x2, (float)y2, (float)z2).color(r, g, b, a).endVertex();
        buffer.vertex(matrices.last().pose(), (float)x1, (float)y2, (float)z1).color(r, g, b, a).endVertex();
        tesselator.end();
        matrices.popPose();

        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
    }

    private static void renderPocketToExterior(WorldRenderContext context, ClientLevel level) {
        DoorRef door = findPocketDoor(level);
        if (door == null || exteriorSnapshot.isEmpty() || snapshotDoor == null) return;

        PoseStack matrices = context.matrixStack();
        Vec3 camera = context.camera().getPosition();
        Vec3 pocketDoorCenter = new Vec3(
                PocketOfficeGenerator.POCKET_DOOR_LOWER.getX() + 0.5D,
                PocketOfficeGenerator.POCKET_DOOR_LOWER.getY(),
                PocketOfficeGenerator.POCKET_DOOR_LOWER.getZ() + 0.5D
        );

        Direction outward = Direction.SOUTH;
        Direction width = widthAxis(outward);

        List<PreviewBlock> visible = new ArrayList<>();
        Direction outwardNormal = snapshotFacing;
        Direction lateralAxis = widthAxis(outwardNormal);
        for (PreviewBlock block : exteriorSnapshot) {
            double depth = block.x * outwardNormal.getStepX() + block.z * outwardNormal.getStepZ();
            double lateral = block.x * lateralAxis.getStepX() + block.z * lateralAxis.getStepZ();

            // Do not render blocks that are on the destination-side of the portal plane.
            // Keep a little room laterally so the opening reads as the same doorway rather
            // than a hard rectangular cut through the entire office.
            if (depth < -0.15D) continue;
            if (Math.abs(lateral) > SNAPSHOT_RADIUS + 0.5D) continue;
            visible.add(block);
        }

        PortalClip clip = PortalClip.create(camera, pocketDoorCenter, outward, width, CLIP_DEPTH,
                CLIP_HALF_WIDTH, CLIP_MIN_Y, CLIP_MAX_Y);

        renderBlocksClipped(context, matrices, camera, visible, preview -> {
            double depth = preview.x * snapshotFacing.getStepX() + preview.z * snapshotFacing.getStepZ();
            double lateral = preview.x * width.getStepX() + preview.z * width.getStepZ();
            double worldX = pocketDoorCenter.x + outward.getStepX() * (depth + 0.5D) + width.getStepX() * lateral;
            double worldZ = pocketDoorCenter.z + outward.getStepZ() * (depth + 0.5D) + width.getStepZ() * lateral;
            double worldY = pocketDoorCenter.y + preview.y;
            return new Vec3(worldX, worldY, worldZ);
        }, clip);
    }

    private interface PositionMapper {
        Vec3 map(PreviewBlock block);
    }

    private static void renderBlocksClipped(WorldRenderContext context, PoseStack matrices, Vec3 camera,
                                            List<PreviewBlock> blocks, PositionMapper mapper, PortalClip clip) {
        renderBlocksInternal(context, matrices, camera, clipFilter(blocks, mapper, clip), mapper, false);
    }

    private static void renderBlocksOverlayClipped(WorldRenderContext context, PoseStack matrices, Vec3 camera,
                                                   List<PreviewBlock> blocks, PositionMapper mapper, PortalClip clip) {
        renderBlocksInternal(context, matrices, camera, clipFilter(blocks, mapper, clip), mapper, true);
    }

    private static List<PreviewBlock> clipFilter(List<PreviewBlock> blocks, PositionMapper mapper, PortalClip clip) {
        List<PreviewBlock> filtered = new ArrayList<>();
        for (PreviewBlock block : blocks) {
            Vec3 origin = mapper.map(block);
            if (clip.intersectsBlock(origin)) {
                filtered.add(block);
            }
        }
        return filtered;
    }

    private static void renderBlocks(WorldRenderContext context, PoseStack matrices, Vec3 camera,
                                     List<PreviewBlock> blocks, PositionMapper mapper) {
        renderBlocksInternal(context, matrices, camera, blocks, mapper, false);
    }

    private static void renderBlocksOverlay(WorldRenderContext context, PoseStack matrices, Vec3 camera,
                                            List<PreviewBlock> blocks, PositionMapper mapper) {
        renderBlocksInternal(context, matrices, camera, blocks, mapper, true);
    }

    private static void renderBlocksInternal(WorldRenderContext context, PoseStack matrices, Vec3 camera,
                                             List<PreviewBlock> blocks, PositionMapper mapper, boolean overlay) {
        BlockRenderDispatcher dispatcher = Minecraft.getInstance().getBlockRenderer();
        var consumers = context.consumers();

        List<PreviewBlock> renderList = new ArrayList<>(blocks);
        if (overlay) {
            // Painter's order because overlay rendering deliberately ignores the current
            // world's depth buffer. Farther office elements must be drawn first.
            renderList.sort((a, b) -> {
                Vec3 pa = mapper.map(a);
                Vec3 pb = mapper.map(b);
                return Double.compare(pb.distanceToSqr(camera), pa.distanceToSqr(camera));
            });
        }

        if (overlay) {
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
        } else {
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(515); // GL_LEQUAL
        }
        RenderSystem.disableCull();

        for (PreviewBlock block : renderList) {
            Vec3 pos = mapper.map(block);
            if (pos.distanceToSqr(camera) > (SEARCH_RADIUS + 8.0D) * (SEARCH_RADIUS + 8.0D)) continue;

            matrices.pushPose();
            matrices.translate(pos.x - camera.x, pos.y - camera.y, pos.z - camera.z);
            dispatcher.renderSingleBlock(block.state, matrices, consumers, block.light, OverlayTexture.NO_OVERLAY);
            matrices.popPose();
        }

        if (overlay) {
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
        }
    }

    private static Direction widthAxis(Direction depth) {
        return switch (depth) {
            case NORTH -> Direction.EAST;
            case SOUTH -> Direction.WEST;
            case EAST -> Direction.SOUTH;
            case WEST -> Direction.NORTH;
            default -> Direction.EAST;
        };
    }

    private static DoorRef findOpenExteriorDoor(ClientLevel level, Vec3 playerPos) {
        BlockPos base = Minecraft.getInstance().player.blockPosition();
        int r = (int) Math.ceil(SEARCH_RADIUS);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = base.getX() - r; x <= base.getX() + r; x++) {
            for (int y = Math.max(level.getMinBuildHeight(), base.getY() - 3); y <= Math.min(level.getMaxBuildHeight() - 1, base.getY() + 4); y++) {
                for (int z = base.getZ() - r; z <= base.getZ() + r; z++) {
                    cursor.set(x, y, z);
                    BlockState state = level.getBlockState(cursor);
                    if (!state.is(ModBlocks.POCKET_DOOR) || !state.getValue(DoorBlock.OPEN)) continue;
                    if (state.getValue(DoorBlock.HALF) != net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER) {
                        continue;
                    }
                    if (playerPos.distanceToSqr(Vec3.atCenterOf(cursor)) > SEARCH_RADIUS * SEARCH_RADIUS) continue;
                    return new DoorRef(cursor.immutable(), state.getValue(DoorBlock.FACING));
                }
            }
        }
        return null;
    }

    private static DoorRef findPocketDoor(ClientLevel level) {
        BlockPos lower = PocketOfficeGenerator.POCKET_DOOR_LOWER;
        BlockState state = level.getBlockState(lower);
        if (!state.is(ModBlocks.POCKET_DOOR) || !state.getValue(DoorBlock.OPEN)) return null;
        return new DoorRef(lower, state.getValue(DoorBlock.FACING));
    }

    private static List<PreviewBlock> buildOfficePreview() {
        List<PreviewBlock> list = new ArrayList<>();
        BlockState wall = ModBlocks.POCKET_OAK_PLANKS.defaultBlockState();

        // Shell.
        for (int x = 0; x <= 16; x++) {
            for (int z = 0; z <= 16; z++) {
                add(list, x, 0, z, wall);
                add(list, x, 9, z, wall);
            }
        }
        for (int y = 1; y <= 8; y++) {
            // The south/front facade is NOT part of the portal preview.
            // The real exterior facade and the real pocket door already occupy that
            // screen space; rendering this wall again would cover the office view.
            for (int z = 1; z <= 15; z++) {
                add(list, 0, y, z, wall);
                add(list, 16, y, z, wall);
            }
        }

        // Windows.
        for (int x = 3; x <= 5; x++) for (int y = 4; y <= 6; y++) add(list, x, y, 0, Blocks.GLASS.defaultBlockState());
        for (int x = 11; x <= 13; x++) for (int y = 4; y <= 6; y++) add(list, x, y, 0, Blocks.GLASS.defaultBlockState());
        for (int z = 5; z <= 7; z++) for (int y = 4; y <= 6; y++) {
            add(list, 0, y, z, Blocks.GLASS.defaultBlockState());
            add(list, 16, y, z, Blocks.GLASS.defaultBlockState());
        }

        // Desk and lectern.
        for (int x = 5; x <= 11; x++) for (int z = 2; z <= 3; z++) {
            add(list, x, 1, z, Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP));
        }
        for (int x = 6; x <= 10; x += 4) {
            add(list, x, 1, 2, Blocks.OAK_FENCE.defaultBlockState());
            add(list, x, 1, 3, Blocks.OAK_FENCE.defaultBlockState());
        }
        add(list, 8, 2, 2, Blocks.LECTERN.defaultBlockState().setValue(LecternBlock.FACING, Direction.SOUTH));

        // Lustre: central wooden cross with five hanging lanterns.
        add(list, 8, 8, 8, Blocks.OAK_FENCE.defaultBlockState());
        for (int x = 6; x <= 10; x += 2) add(list, x, 8, 8, Blocks.OAK_FENCE.defaultBlockState());
        for (int x = 6; x <= 10; x += 2) add(list, x, 7, 8, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
        add(list, 8, 7, 6, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
        add(list, 8, 7, 10, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));

        // Shelves, ladder, pots.
        Random random = new Random(0x504F434B45544F46L);
        for (int z = 2; z <= 14; z += 2) {
            int height = 1 + random.nextInt(3);
            if (!(z >= 5 && z <= 7)) for (int y = 1; y < 1 + height; y++) add(list, 1, y, z, Blocks.BOOKSHELF.defaultBlockState());
            if (!(z >= 5 && z <= 7)) for (int y = 1; y < 1 + height; y++) add(list, 15, y, z, Blocks.BOOKSHELF.defaultBlockState());
        }
        for (int x = 2; x <= 14; x++) {
            if ((x >= 3 && x <= 5) || (x >= 11 && x <= 13)) continue;
            int height = 1 + random.nextInt(2);
            for (int y = 1; y < 1 + height; y++) add(list, x, y, 1, Blocks.BOOKSHELF.defaultBlockState());
        }
        for (int y = 1; y <= 4; y++) add(list, 2, y, 15,
                Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.NORTH));
        add(list, 3, 1, 12, Blocks.POTTED_DANDELION.defaultBlockState());
        add(list, 13, 1, 12, Blocks.POTTED_POPPY.defaultBlockState());
        add(list, 2, 1, 14, Blocks.LANTERN.defaultBlockState());
        add(list, 14, 1, 10, Blocks.LANTERN.defaultBlockState());

        // Only the south-wall blocks occupying the actual doorway were removed above.
        // Keep all remaining interior geometry so the portal view has its real room shape.
        return list;
    }

    private static void add(List<PreviewBlock> list, int x, int y, int z, BlockState state) {
        list.add(new PreviewBlock(x, y, z, state, OFFICE_LIGHT));
    }

    private static void remove(List<PreviewBlock> list, int x, int y, int z) {
        list.removeIf(b -> b.x == x && b.y == y && b.z == z);
    }

    /**
     * A camera-side pyramid defined by the four edges of the real door opening.
     * Preview blocks are rendered only when their AABB intersects this volume, so
     * the fake world cannot spill outside the visible doorway.
     */
    private static final class PortalClip {
        private final Plane[] sidePlanes;
        private final Plane portalPlane;
        private final Vec3 camera;

        private PortalClip(Vec3 camera, Plane[] sidePlanes, Plane portalPlane) {
            this.camera = camera;
            this.sidePlanes = sidePlanes;
            this.portalPlane = portalPlane;
        }

        static PortalClip create(Vec3 camera, Vec3 center, Direction view, Direction width,
                                 double depthOffset, double halfWidth, double minY, double maxY) {
            Vec3 apertureCenter = center.add(view.getStepX() * depthOffset, depthOffset * 0.0D, view.getStepZ() * depthOffset);
            Vec3 leftBottom = apertureCenter.add(width.getStepX() * (-halfWidth), minY, width.getStepZ() * (-halfWidth));
            Vec3 rightBottom = apertureCenter.add(width.getStepX() * halfWidth, minY, width.getStepZ() * halfWidth);
            Vec3 leftTop = apertureCenter.add(width.getStepX() * (-halfWidth), maxY, width.getStepZ() * (-halfWidth));
            Vec3 rightTop = apertureCenter.add(width.getStepX() * halfWidth, maxY, width.getStepZ() * halfWidth);

            Vec3 insidePoint = apertureCenter.add(view.getStepX() * 4.0D, 0.95D, view.getStepZ() * 4.0D);
            Plane left = orientedPlane(camera, leftBottom, leftTop, insidePoint);
            Plane right = orientedPlane(camera, rightTop, rightBottom, insidePoint);
            Plane bottom = orientedPlane(camera, rightBottom, leftBottom, insidePoint);
            Plane top = orientedPlane(camera, leftTop, rightTop, insidePoint);
            Plane portal = new Plane(viewToNormal(view), -viewToNormal(view).dot(apertureCenter));

            return new PortalClip(camera, new Plane[]{left, right, bottom, top}, portal);
        }

        boolean intersectsBlock(Vec3 origin) {
            // Small safety margin around the full block AABB. This avoids clipping the
            // edges of bookshelves, frames, lamps, etc. while retaining a tight portal.
            final double minX = origin.x - 0.02D;
            final double maxX = origin.x + 1.02D;
            final double minY = origin.y - 0.02D;
            final double maxY = origin.y + 1.02D;
            final double minZ = origin.z - 0.02D;
            final double maxZ = origin.z + 1.02D;

            Vec3[] corners = new Vec3[]{
                    new Vec3(minX, minY, minZ), new Vec3(minX, minY, maxZ),
                    new Vec3(minX, maxY, minZ), new Vec3(minX, maxY, maxZ),
                    new Vec3(maxX, minY, minZ), new Vec3(maxX, minY, maxZ),
                    new Vec3(maxX, maxY, minZ), new Vec3(maxX, maxY, maxZ)
            };

            for (Plane plane : sidePlanes) {
                boolean anyInside = false;
                for (Vec3 corner : corners) {
                    if (plane.distance(corner) >= -CLIP_EPSILON) {
                        anyInside = true;
                        break;
                    }
                }
                if (!anyInside) return false;
            }

            // Keep only geometry on the far side of the doorway, i.e. in the direction
            // in which the player is looking through the portal.
            boolean behind = false;
            for (Vec3 corner : corners) {
                if (portalPlane.distance(corner) >= -CLIP_EPSILON) {
                    behind = true;
                    break;
                }
            }
            return behind;
        }

        private static Plane orientedPlane(Vec3 a, Vec3 b, Vec3 c, Vec3 insidePoint) {
            Vec3 normal = b.subtract(a).cross(c.subtract(a));
            if (normal.lengthSqr() < 1.0E-9D) {
                return new Plane(Vec3.ZERO, 0.0D);
            }
            normal = normal.normalize();
            double constant = -normal.dot(a);
            Plane plane = new Plane(normal, constant);
            if (plane.distance(insidePoint) < 0.0D) {
                normal = normal.scale(-1.0D);
                constant = -normal.dot(a);
                plane = new Plane(normal, constant);
            }
            return plane;
        }

        private static Vec3 viewToNormal(Direction view) {
            return new Vec3(view.getStepX(), 0.0D, view.getStepZ());
        }
    }

    private static final class Plane {
        final Vec3 normal;
        final double constant;

        Plane(Vec3 normal, double constant) {
            this.normal = normal;
            this.constant = constant;
        }

        double distance(Vec3 point) {
            return normal.dot(point) + constant;
        }
    }

    private static final class PreviewBlock {
        final int x, y, z;
        final BlockState state;
        final int light;

        PreviewBlock(int x, int y, int z, BlockState state, int light) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.state = state;
            this.light = light;
        }
    }

    private static final class DoorRef {
        final BlockPos lower;
        final Direction facing;

        DoorRef(BlockPos lower, Direction facing) {
            this.lower = lower;
            this.facing = facing;
        }
    }
}
