package fr.jmproduction.pocketdoor.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Invisible side anchors: the master desk owns rendering and redirects interaction. */
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
    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext c) { return SHAPE; }
    @Override public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext c) { return SHAPE; }
    @Override public VoxelShape getInteractionShape(BlockState state, BlockGetter level, BlockPos pos) { return SHAPE; }

    /** Les ancres sont placées de part et d'autre du bloc principal, sur l'axe perpendiculaire à FACING. */
    public static BlockPos masterPos(BlockPos pos, BlockState state) {
        Direction right = state.getValue(FACING).getClockWise();
        return pos.relative(state.getValue(POSITIVE) ? right.getOpposite() : right);
    }

    /** Clic droit sur une ancre = clic sur le bureau : même inventaire que le bloc principal. */
    @Override public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        BlockPos master = masterPos(pos, state);
        if (!(level.getBlockEntity(master) instanceof FurnitureBlockEntity)) return InteractionResult.PASS;
        DeskBlock.openInventory((net.minecraft.server.level.ServerPlayer) player, level, master);
        return InteractionResult.CONSUME;
    }

    @Override public void playerDestroy(Level level, Player player, BlockPos pos, BlockState state, net.minecraft.world.level.block.entity.BlockEntity be, net.minecraft.world.item.ItemStack tool) {
        BlockPos master = masterPos(pos, state);
        if (level.getBlockState(master).is(ModBlocks.DESK)) level.destroyBlock(master, true, player);
        else super.playerDestroy(level, player, pos, state, be, tool);
    }
}
