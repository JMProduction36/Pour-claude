package fr.jmproduction.pocketdoor.client;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.jmproduction.pocketdoor.block.ModBlocks;
import fr.jmproduction.pocketdoor.dimension.PocketDimensions;
import fr.jmproduction.pocketdoor.dimension.PocketOfficeGenerator;
import fr.jmproduction.pocketdoor.portal.ClientWorldLoader;
import fr.jmproduction.pocketdoor.portal.Portal;
import fr.jmproduction.pocketdoor.portal.PortalState;
import fr.jmproduction.pocketdoor.portal.api.PortalAPI;
import fr.jmproduction.pocketdoor.portal.render.MyGameRenderer;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/**
 * Portal renderer that delegates to the internal MyGameRenderer + PortalAPI stack
 * (a faithful port of qouteall.imm_ptl.core.render.MyGameRenderer / PortalAPI from
 * Immersive Portals 1.19.2 v2.3.2). The previous snapshot preview that re-built the
 * pocket office from raw block states has been removed: open doors now produce a
 * real, live render of the destination dimension through the doorway.
 */
public final class PocketPortalRenderer {
    private static final double SEARCH_RADIUS = 10.0D;

    private PocketPortalRenderer() {}

    public static void initialize() {
        WorldRenderEvents.AFTER_ENTITIES.register(PocketPortalRenderer::render);
    }

    private static void render(WorldRenderContext context) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null) return;

        Optional<PortalViewState> view = findOpenDoor(level, mc.player.position());
        if (view.isEmpty()) {
            return;
        }

        // Make sure the destination level is loaded into the client world cache so the
        // secondary framebuffer can be filled without showing a Mojang loading screen.
        ClientWorldLoader.getWorld(view.get().destination);

        PortalState state = view.get().toPortalState();
        // Delegate to the MyGameRenderer / PortalAPI stack. This is what performs the
        // stencil + clip-plane + secondary framebuffer dance used by upstream.
        PortalAPI.beforeRender(view.get().portal);
        try {
            MyGameRenderer.renderPortal(
                    state,
                    mc.levelRenderer,
                    mc.gameRenderer,
                    mc,
                    mc.getFrameTime(),
                    System.nanoTime(),
                    true,
                    () -> mc.levelRenderer.renderLevel(
                            new PoseStack(),
                            mc.getFrameTime(),
                            System.nanoTime(),
                            // 1.19.2 (Mojang mappings): LevelRenderer#shouldShowEntities() does not exist;
                            // we always want entities drawn through the doorway.
                            true,
                            mc.gameRenderer.getMainCamera(),
                            mc.gameRenderer,
                            mc.gameRenderer.lightTexture(),
                            context.matrixStack().last().pose())
            );
        } finally {
            PortalAPI.afterRender(view.get().portal);
        }
    }

    private static Optional<PortalViewState> findOpenDoor(ClientLevel level, Vec3 playerPos) {
        // Pocket-side view: look outward through the pocket door toward the exterior.
        if (level.dimension().equals(PocketDimensions.POCKET_OFFICE)) {
            BlockPos lower = PocketOfficeGenerator.POCKET_DOOR_LOWER;
            BlockState state = level.getBlockState(lower);
            if (state.is(ModBlocks.POCKET_DOOR) && state.getValue(DoorBlock.OPEN)) {
                Portal p = findNearestPortalAnyDestination(level, lower);
                if (p != null) {
                    return Optional.of(new PortalViewState(p, p.destinationWorld));
                }
            }
            return Optional.empty();
        }

        // Exterior view: look inward through the linked door toward the pocket office.
        BlockPos base = Minecraft.getInstance().player.blockPosition();
        int r = (int) Math.ceil(SEARCH_RADIUS);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = base.getX() - r; x <= base.getX() + r; x++) {
            for (int y = Math.max(level.getMinBuildHeight(), base.getY() - 3);
                 y <= Math.min(level.getMaxBuildHeight() - 1, base.getY() + 4); y++) {
                for (int z = base.getZ() - r; z <= base.getZ() + r; z++) {
                    cursor.set(x, y, z);
                    BlockState state = level.getBlockState(cursor);
                    if (!state.is(ModBlocks.POCKET_DOOR) || !state.getValue(DoorBlock.OPEN)) continue;
                    if (state.getValue(DoorBlock.HALF)
                            != net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER) {
                        continue;
                    }
                    if (playerPos.distanceToSqr(Vec3.atCenterOf(cursor)) > SEARCH_RADIUS * SEARCH_RADIUS) {
                        continue;
                    }
                    Portal p = findNearestPortal(level, cursor.immutable(), PocketDimensions.POCKET_OFFICE);
                    if (p != null) {
                        return Optional.of(new PortalViewState(p, p.destinationWorld));
                    }
                }
            }
        }
        return Optional.empty();
    }

    private static Portal findNearestPortal(ClientLevel level, BlockPos doorPos,
                                            net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> destination) {
        Portal best = null;
        double bestDist = SEARCH_RADIUS * SEARCH_RADIUS;
        for (Portal p : level.getEntitiesOfClass(Portal.class,
                new net.minecraft.world.phys.AABB(doorPos).inflate(2.0D))) {
            if (p.destinationWorld == null || !p.destinationWorld.equals(destination)) continue;
            double d = p.distanceToSqr(Vec3.atCenterOf(doorPos));
            if (d <= bestDist) {
                best = p;
                bestDist = d;
            }
        }
        return best;
    }

    /** Variant of {@link #findNearestPortal} that ignores the destination dimension. */
    private static Portal findNearestPortalAnyDestination(ClientLevel level, BlockPos doorPos) {
        Portal best = null;
        double bestDist = SEARCH_RADIUS * SEARCH_RADIUS;
        for (Portal p : level.getEntitiesOfClass(Portal.class,
                new net.minecraft.world.phys.AABB(doorPos).inflate(2.0D))) {
            double d = p.distanceToSqr(Vec3.atCenterOf(doorPos));
            if (d <= bestDist) {
                best = p;
                bestDist = d;
            }
        }
        return best;
    }

    /** Captures the geometry of a portal pair plus the destination dimension key. */
    private static final class PortalViewState {
        final Portal portal;
        final net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> destination;

        PortalViewState(Portal portal,
                        net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> destination) {
            this.portal = portal;
            this.destination = destination;
        }

        PortalState toPortalState() {
            return new PortalState(
                    portal.position(),
                    portal.destinationPos,
                    portal.rotation == null ? com.mojang.math.Quaternion.ONE.copy() : portal.rotation,
                    portal.axisW,
                    portal.axisH,
                    portal.portalWidth,
                    portal.portalHeight
            );
        }
    }
}
