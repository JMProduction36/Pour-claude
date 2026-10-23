package fr.jmproduction.pocketdoor.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.CompoundContainer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Master block of the three-block-wide desk. Each of the three blocks (one master
 * + two side anchors) opens the SAME single 54-slot ("double chest") inventory
 * stored on the master's {@link FurnitureBlockEntity}. There is no per-third
 * sub-view any more: clicking anywhere on the desk footprint (master or parts)
 * resolves to the master and opens the unified inventory.
 *
 * <p>The desk is laid out perpendicular to its {@code FACING}: for
 * {@code NORTH}/{@code SOUTH} the three blocks run along the X axis, for
 * {@code EAST}/{@code WEST} they run along the Z axis. Each individual block
 * owns a simple 1x1x1 outline/collision shape — the rendered model is the only
 * thing that is 3-blocks wide. This guarantees a valid
 * {@code UseItemOnPacket} hit position for every block in the footprint, no
 * matter which way the player is facing.</p>
 */
public final class DeskBlock extends HorizontalDirectionalBlock implements EntityBlock {

    /** Single, full footprint hitbox for the master block (a 1x1x1 cube). */
    private static final VoxelShape SHAPE = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 16.0D, 16.0D);

    public DeskBlock(Properties properties) {
        super(properties.noOcclusion());
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.SOUTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext c) {
        return SHAPE;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext c) {
        return SHAPE;
    }

    @Override
    public VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return SHAPE;
    }

    @Override
    public VoxelShape getInteractionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return SHAPE;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext c) {
        if (!c.getLevel().getBlockState(c.getClickedPos()).canBeReplaced(Fluids.EMPTY)) return null;
        return defaultBlockState().setValue(FACING, c.getHorizontalDirection().getOpposite());
    }

    @Override
    public BlockState rotate(BlockState s, Rotation r) {
        return s.setValue(FACING, r.rotate(s.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState s, Mirror m) {
        return s.rotate(m.getRotation(s.getValue(FACING)));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FurnitureBlockEntity(pos, state);
    }

    /**
     * Single unified inventory. Opening on the master always opens the
     * 54-slot "double chest" backed by {@link FurnitureBlockEntity#getCompound()}.
     */
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos,
                                 Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        openInventory((ServerPlayer) player, level, pos);
        return InteractionResult.CONSUME;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moved) {
        if (!state.is(next.getBlock())) {
            // Drop the contents of the unified 54-slot inventory so the player
            // never loses their stored items when the master is broken.
            if (level.getBlockEntity(pos) instanceof FurnitureBlockEntity furniture) {
                Containers.dropContents(level, pos, furniture.getCompound());
            }
            // Clean up the two side anchors that may still be in the world. They
            // always sit perpendicular to FACING (use FACING.getClockWise() for
            // the player's right side and its opposite for the left side).
            Direction rightSide = state.getValue(FACING).getClockWise();
            Direction leftSide = rightSide.getOpposite();
            for (Direction side : new Direction[]{leftSide, rightSide}) {
                BlockPos anchor = pos.relative(side);
                if (level.getBlockState(anchor).is(ModBlocks.DESK_PART)) {
                    level.setBlock(anchor, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
        super.onRemove(state, level, pos, next, moved);
    }

    /**
     * Opens the unified 54-slot desk inventory on the client. Used by both
     * {@link DeskBlock#use} and {@link DeskPartBlock#use}; no view mode is
     * passed because there is now a single shared view.
     */
    public static void openInventory(ServerPlayer player, Level level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof FurnitureBlockEntity furniture)) {
            return;
        }
        final FurnitureBlockEntity target = furniture;
        Component title = Component.translatable("container.pocketdoor.desk_center");
        SimpleMenuProvider provider = new SimpleMenuProvider(
                (containerId, playerInv, p) -> ChestMenu.sixRows(containerId, playerInv, target.getCompound()),
                title);
        player.openMenu(provider);
    }

    /** Build the 54-slot chest menu directly (kept for compatibility). */
    public static AbstractContainerMenu buildMenu(int containerId,
                                                   net.minecraft.world.entity.player.Inventory playerInv,
                                                   FurnitureBlockEntity furniture) {
        return ChestMenu.sixRows(containerId, playerInv, furniture.getCompound());
    }
}
