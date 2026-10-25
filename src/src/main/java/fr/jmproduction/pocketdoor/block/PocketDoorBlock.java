package fr.jmproduction.pocketdoor.block;

import fr.jmproduction.pocketdoor.dimension.ImmersivePortalBridge;
import fr.jmproduction.pocketdoor.dimension.PocketDimensions;
import fr.jmproduction.pocketdoor.dimension.PocketDoorPortal;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Custom Pocket Door.
 *
 * <p>Behaviour summary:</p>
 * <ul>
 *   <li>{@link #neighborChanged} / {@link #tick} are no-ops so redstone cannot toggle the door.</li>
 *   <li>The pocket-side door is incassable from the inside ({@link #getDestroyProgress},
 *       {@link #playerWillDestroy}).</li>
 *   <li>The door is fully indestructible against any explosion. We override both the
 *       1.19.2 vanilla hook {@link Block#wasExploded(Level, BlockPos, Explosion)} (which
 *       is what {@code Explosion#finalizeExplosion} actually calls to remove blocks) and
 *       {@link Block#dropFromExplosion(Explosion)} (which controls the loot drop). Without
 *       overriding {@code wasExploded}, an explosion at the door's coordinates would still
 *       call {@code super.wasExploded(...)} → {@code level.removeBlock(pos, false)} even
 *       though the blast resistance is 3,600,000. The same override protects BOTH halves
 *       of the door (the one at {@code pos} and the matching {@code otherHalf}) so the
 *       upper or lower half can never be left orphaned in the world.</li>
 *   <li>On removal (creative / setblock / piston) we explicitly remove the matching
 *       half and tear down portals on both sides via {@link ImmersivePortalBridge}.</li>
 * </ul>
 */
public class PocketDoorBlock extends DoorBlock {
    private static final VoxelShape CLOSED_NS_OUTLINE = Block.box(0, 0, 5, 16, 16, 11);
    private static final VoxelShape CLOSED_EW_OUTLINE = Block.box(5, 0, 0, 11, 16, 16);

    private static final VoxelShape CLOSED_NS_COLLISION = Block.box(1, 0, 6, 15, 16, 10);
    private static final VoxelShape CLOSED_EW_COLLISION = Block.box(6, 0, 1, 10, 16, 15);

    public PocketDoorBlock(Properties properties) {
        super(properties);
    }

    // ---------------------------------------------------------------------
    // Bug 1 — never react to redstone (neither side of the door)
    // ---------------------------------------------------------------------

    /**
     * Vanilla DoorBlock updates POWERED here and schedules a tick when a signal
     * changes. We intentionally do nothing so the POWERED state never changes
     * and {@link #tick} never opens/closes the door on a redstone pulse.
     */
    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos,
                                Block neighborBlock, BlockPos neighborPos, boolean movedByPiston) {
        // Intentionally empty: redstone must not toggle the pocket door.
    }

    /**
     * Vanilla DoorBlock toggles OPEN whenever POWERED differs from OPEN. Since
     * POWERED never changes (see {@link #neighborChanged}), this override is
     * a defensive no-op to guarantee no redstone-driven toggle happens.
     */
    @Override
    public void tick(BlockState state, net.minecraft.server.level.ServerLevel level,
                     BlockPos pos, net.minecraft.util.RandomSource random) {
        // Intentionally empty.
    }

    // ---------------------------------------------------------------------
    // Bug 2 — pocket-side door is incassable from the inside
    // ---------------------------------------------------------------------

    /**
     * Returns 0.0F when the block lives in the pocket dimension. Combined with
     * the negative destroy speed set on {@code Properties.strength(-1, 3600000)}
     * in {@link ModBlocks#POCKET_DOOR}, this prevents a player from ever
     * progressing the break animation. This stops the crash that happens when
     * the interior door is destroyed while the portal is still alive.
     */
    @Override
    public float getDestroyProgress(BlockState state, Player player,
                                    BlockGetter level, BlockPos pos) {
        if (level instanceof Level lvl && lvl.dimension().equals(PocketDimensions.POCKET_OFFICE)) {
            return 0.0F;
        }
        return super.getDestroyProgress(state, player, level, pos);
    }

    /**
     * Same protection at the "wants to start breaking" stage: when the block is
     * inside the pocket dimension, the player never enters the breaking flow.
     * This guards against creative-mode / instant-break / setblock paths that
     * bypass {@link #getDestroyProgress}.
     */
    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (level.dimension().equals(PocketDimensions.POCKET_OFFICE)) {
            // Refuse to start the destruction in the pocket dimension: do nothing.
            // (DoorBlock#playerWillDestroy in 1.19.2 also handles breaking the other
            // half in creative mode, so we still call super outside the pocket.)
            return;
        }
        super.playerWillDestroy(level, pos, state, player);
    }

    // ---------------------------------------------------------------------
    // Bug 3 — exterior (and interior) door resists explosions
    // ---------------------------------------------------------------------
    // Properties are registered with strength(3.0F, 3600000.0F) in ModBlocks,
    // so vanilla blast damage cannot break the block. The two extra overrides
    // below close the remaining loopholes:

    /**
     * Real 1.19.2 vanilla hook for explosion-driven block removal. {@code Explosion#finalizeExplosion}
     * iterates affected blocks and calls {@link Block#wasExploded(Level, BlockPos, Explosion)} on
     * each one BEFORE testing blast resistance: the default implementation calls
     * {@code level.removeBlock(pos, false)} and {@code dropFromExplosion(...)}. Even with a
     * 3,600,000 blast resistance, an explosion at the exact position of the door would still
     * hit this hook and remove it. We override it to do nothing: both halves stay, open or closed,
     * and the matching portal pair on the destination side stays untouched.
     *
     * <p>Note: this is the only correct place to intercept explosion removal in 1.19.2 Mojang
     * mappings. {@link Block#getExplosionResistance} only gates which blocks the explosion
     * algorithm actually tries to break; {@link #dropFromExplosion} only gates the loot drop.
     * {@code wasExploded} is the final removal step.</p>
     */
    @Override
    public void wasExploded(Level level, BlockPos pos, Explosion explosion) {
        // Intentionally empty: Pocket Door blocks must never be removed by explosions.
        // We deliberately do NOT call super.wasExploded(...).
        //
        // We also do NOT need to manually remove the matching half: vanilla explodes both
        // halves independently, and our override is hit for each one (since both halves are
        // pocket-door blocks). So when the explosion's affected block set contains either
        // half, that half stays. The other half stays because it has its own override call.
        //
        // Belt-and-braces: if for some reason only one half is in the affected set (very
        // small explosion radius touching a single block), we still refuse to remove it.
    }

    /**
     * Belt-and-braces: refuse to drop the door's item even if some other code path
     * ends up calling this (e.g. indirect cleanup). {@code noLootTable()} on the
     * block properties already prevents the loot table from running; this override
     * catches the remaining direct calls.
     */
    @Override
    public boolean dropFromExplosion(Explosion explosion) {
        return false;
    }

    // ---------------------------------------------------------------------
    // Bug 4 — clean up portals / both halves when either half disappears
    // ---------------------------------------------------------------------

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos,
                         BlockState newState, boolean movedByPiston) {
        // Only react when the block actually changes (e.g. setblock / piston /
        // creative / broken). If the new state is still this same block (state
        // update only), do nothing.
        if (!state.is(newState.getBlock())) {
            // Drop nothing — the door is unbreakable in practice and has noLootTable.
            // Make sure both halves disappear together: vanilla DoorBlock doesn't
            // auto-remove the upper half, so do it explicitly here.
            if (!level.isClientSide) {
                BlockPos otherHalf = pos.above();
                if (state.getValue(HALF) == DoubleBlockHalf.UPPER) {
                    otherHalf = pos.below();
                }
                if (level.getBlockState(otherHalf).is(this)) {
                    level.setBlock(otherHalf, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 35);
                }
                // Tear down portals on both sides and force the interior door
                // closed so we never leave a stale portal alive.
                ImmersivePortalBridge.cleanupAll(
                        level.getServer() != null ? level.getServer().overworld() : null,
                        state.getValue(HALF) == DoubleBlockHalf.UPPER ? pos.below() : pos
                );
                // Close the matching door on the other side, if any.
                PocketDoorPortal.forceCloseOtherSide(level,
                        state.getValue(HALF) == DoubleBlockHalf.UPPER ? pos.below() : pos);
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    // ---------------------------------------------------------------------
    // Player interaction — unchanged behaviour
    // ---------------------------------------------------------------------

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos,
                                 Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        if (!(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)) {
            return InteractionResult.PASS;
        }

        BlockPos lower = state.getValue(HALF) == DoubleBlockHalf.UPPER ? pos.below() : pos;
        if (!level.getBlockState(lower).is(this)) {
            return InteractionResult.PASS;
        }

        boolean handled = PocketDoorPortal.interact(serverPlayer, lower);
        return handled ? InteractionResult.CONSUME : InteractionResult.PASS;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Direction facing = state.getValue(FACING);
        return isNorthSouth(facing) ? CLOSED_NS_OUTLINE : CLOSED_EW_OUTLINE;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Direction facing = state.getValue(FACING);
        return state.getValue(OPEN)
                ? net.minecraft.world.phys.shapes.Shapes.empty()
                : (isNorthSouth(facing) ? CLOSED_NS_COLLISION : CLOSED_EW_COLLISION);
    }

    @Override
    public boolean hasDynamicShape() {
        return true;
    }

    private static boolean isNorthSouth(Direction direction) {
        return direction == Direction.NORTH || direction == Direction.SOUTH;
    }
}
