package fr.jmproduction.pocketdoor.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Invisible side anchor of the three-block-wide desk. Has no rendering and no
 * inventory of its own: every interaction is forwarded to the master
 * {@link DeskBlock}, which opens the single, unified 54-slot inventory.
 *
 * <p>The anchor always sits on the axis perpendicular to the master's
 * {@code FACING}: {@code POSITIVE=true} on the player's right side
 * ({@code FACING.getClockWise()}) and {@code POSITIVE=false} on the left
 * ({@code FACING.getCounterClockWise()}).</p>
 */
public final class DeskPartBlock extends Block {
    public static final DirectionProperty FACING = DirectionProperty.create("facing", Direction.Plane.HORIZONTAL);
    public static final BooleanProperty POSITIVE = BooleanProperty.create("positive");

    /** Plain 1x1x1 outline/collision shape — guarantees a valid raycast hit. */
    private static final VoxelShape SHAPE = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 16.0D, 16.0D);

    public DeskPartBlock(Properties properties) {
        super(properties.noOcclusion());
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.SOUTH).setValue(POSITIVE, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING, POSITIVE);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
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

    /**
     * Resolve the master position from this part's state. The master sits on
     * the axis perpendicular to {@code FACING}: POSITIVE=true is on the
     * player's right side (FACING.getClockWise()), POSITIVE=false on the
     * left (its opposite).
     */
    public static BlockPos masterPos(BlockPos pos, BlockState state) {
        Direction rightSide = state.getValue(FACING).getClockWise();
        Direction axis = state.getValue(POSITIVE) ? rightSide : rightSide.getOpposite();
        return pos.relative(axis);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection().getOpposite())
                .setValue(POSITIVE, false);
    }

    /**
     * Always open the unified desk inventory held by the master, regardless of
     * which side of the desk was clicked.
     */
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos,
                                 Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        BlockPos master = masterPos(pos, state);
        if (!(level.getBlockEntity(master) instanceof FurnitureBlockEntity)) {
            return InteractionResult.PASS;
        }
        DeskBlock.openInventory((ServerPlayer) player, level, master);
        return InteractionResult.CONSUME;
    }

    @Override
    public void playerDestroy(Level level, Player player, BlockPos pos, BlockState state,
                              BlockEntity be, ItemStack tool) {
        BlockPos master = masterPos(pos, state);
        if (level.getBlockState(master).is(ModBlocks.DESK)) {
            level.destroyBlock(master, true, player);
        } else {
            super.playerDestroy(level, player, pos, state, be, tool);
        }
    }
}
