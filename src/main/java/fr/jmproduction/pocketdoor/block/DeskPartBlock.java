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
 * Invisible side anchors: the master desk owns rendering and redirects
 * interaction. Each part opens the left or right 27-slot sub-chest of the
 * shared two-half inventory held by the master.
 */
public final class DeskPartBlock extends Block {
    public static final DirectionProperty FACING = DirectionProperty.create("facing", Direction.Plane.HORIZONTAL);
    public static final BooleanProperty POSITIVE = BooleanProperty.create("positive");
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 16, 16);

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

    public static BlockPos masterPos(BlockPos pos, BlockState state) {
        Direction axis = state.getValue(FACING).getAxis() == Direction.Axis.X ? Direction.WEST : Direction.NORTH;
        if (state.getValue(POSITIVE)) axis = axis.getOpposite();
        return pos.relative(axis);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // POSITIVE means this part is placed on the "right" side from the player's POV.
        return defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection().getOpposite())
                .setValue(POSITIVE, false);
    }

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
        int viewMode = state.getValue(POSITIVE) ? DeskBlock.VIEW_RIGHT : DeskBlock.VIEW_LEFT;
        DeskBlock.openView((ServerPlayer) player, level, master, viewMode);
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
