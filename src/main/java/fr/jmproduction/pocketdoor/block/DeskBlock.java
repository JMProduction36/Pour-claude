package fr.jmproduction.pocketdoor.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Vanilla-model master block for the centered, three-block-wide desk. */
public final class DeskBlock extends HorizontalDirectionalBlock {
    private static final VoxelShape SHAPE = Shapes.or(box(-16, 0, 0, 32, 16, 16));
    public DeskBlock(Properties properties) { super(properties.noOcclusion()); registerDefaultState(defaultBlockState().setValue(FACING, Direction.SOUTH)); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(FACING); }
    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext c) { return SHAPE; }
    @Override public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext c) { return SHAPE; }
    @Override public BlockState getStateForPlacement(BlockPlaceContext c) {
        if (!c.getLevel().getBlockState(c.getClickedPos()).canBeReplaced(Fluids.EMPTY)) return null;
        return defaultBlockState().setValue(FACING, c.getHorizontalDirection().getOpposite()); }
    @Override public BlockState rotate(BlockState s, Rotation r) { return s.setValue(FACING, r.rotate(s.getValue(FACING))); }
    @Override public BlockState mirror(BlockState s, Mirror m) { return s.rotate(m.getRotation(s.getValue(FACING))); }
    @Override public void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moved) {
        if (!state.is(next.getBlock())) {
            Direction axis = state.getValue(FACING).getAxis() == Direction.Axis.X ? Direction.EAST : Direction.SOUTH;
            for (boolean positive : new boolean[]{false, true}) {
                BlockPos side = pos.relative(positive ? axis : axis.getOpposite());
                if (level.getBlockState(side).is(ModBlocks.DESK_PART)) level.removeBlock(side, false);
            }
        }
        super.onRemove(state, level, pos, next, moved);
    }
}
