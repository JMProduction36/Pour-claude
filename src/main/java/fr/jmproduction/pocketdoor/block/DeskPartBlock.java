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

/** Invisible interactive side hitboxes for the three-block desk. */
public final class DeskPartBlock extends Block {
    public static final DirectionProperty FACING = DirectionProperty.create("facing", Direction.Plane.HORIZONTAL);
    public static final BooleanProperty POSITIVE = BooleanProperty.create("positive");
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 16, 16);

    public DeskPartBlock(Properties properties) {
        super(properties.noOcclusion());
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.SOUTH).setValue(POSITIVE, false));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(FACING, POSITIVE); }
    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }
    @Override public VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) { return SHAPE; }
    @Override public VoxelShape getCollisionShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) { return SHAPE; }
    @Override public VoxelShape getOcclusionShape(BlockState s, BlockGetter l, BlockPos p) { return SHAPE; }
    @Override public VoxelShape getInteractionShape(BlockState s, BlockGetter l, BlockPos p) { return SHAPE; }

    public static BlockPos masterPos(BlockPos pos, BlockState state) {
        Direction right = state.getValue(FACING).getClockWise();
        return pos.relative(state.getValue(POSITIVE) ? right.getOpposite() : right);
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext c) {
        return defaultBlockState().setValue(FACING, c.getHorizontalDirection().getOpposite()).setValue(POSITIVE, false);
    }
    @Override public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        BlockPos master = masterPos(pos, state);
        if (!(level.getBlockEntity(master) instanceof FurnitureBlockEntity)) return InteractionResult.PASS;
        DeskBlock.openInventory((ServerPlayer) player, level, master);
        return InteractionResult.CONSUME;
    }
    @Override public void playerDestroy(Level level, Player player, BlockPos pos, BlockState state, BlockEntity be, ItemStack tool) {
        BlockPos master = masterPos(pos, state);
        if (level.getBlockState(master).is(ModBlocks.DESK)) level.destroyBlock(master, true, player);
        else super.playerDestroy(level, player, pos, state, be, tool);
    }
}
