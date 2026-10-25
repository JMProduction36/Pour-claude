package fr.jmproduction.pocketdoor.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
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
 * Bloc principal du bureau de trois blocs de large. Le modèle s'étend sur trois blocs ; ce bloc et ses deux
 * ancres invisibles ({@link DeskPartBlock}) ont chacun une hitbox 1x1x1. Un clic droit sur N'IMPORTE quelle
 * partie du bureau (centre ou côtés) ouvre le même inventaire de 54 emplacements, celui de ce bloc.
 */
public final class DeskBlock extends HorizontalDirectionalBlock implements EntityBlock {

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
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (!level.getBlockState(pos).canBeReplaced(Fluids.EMPTY)) {
            return null;
        }

        Direction facing = context.getPlayer() == null
                ? Direction.SOUTH
                : context.getHorizontalDirection().getOpposite();

        // Le bureau occupe trois blocs perpendiculairement à FACING : les deux ancres doivent être libres.
        Direction right = facing.getClockWise();
        if (!level.getBlockState(pos.relative(right)).canBeReplaced(Fluids.EMPTY)
                || !level.getBlockState(pos.relative(right.getOpposite())).canBeReplaced(Fluids.EMPTY)) {
            return null;
        }

        return defaultBlockState().setValue(FACING, facing);
    }

    /** Crée les deux ancres invisibles d'un bureau posé par un joueur. */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide) return;
        Direction right = state.getValue(FACING).getClockWise();
        level.setBlock(pos.relative(right), ModBlocks.DESK_PART.defaultBlockState()
                .setValue(DeskPartBlock.FACING, state.getValue(FACING))
                .setValue(DeskPartBlock.POSITIVE, true), 3);
        level.setBlock(pos.relative(right.getOpposite()), ModBlocks.DESK_PART.defaultBlockState()
                .setValue(DeskPartBlock.FACING, state.getValue(FACING))
                .setValue(DeskPartBlock.POSITIVE, false), 3);
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
            if (level.getBlockEntity(pos) instanceof FurnitureBlockEntity furniture) {
                Containers.dropContents(level, pos, furniture.getCompound());
            }
            Direction right = state.getValue(FACING).getClockWise();
            for (Direction side : new Direction[]{right.getOpposite(), right}) {
                BlockPos anchor = pos.relative(side);
                if (level.getBlockState(anchor).is(ModBlocks.DESK_PART)) {
                    level.setBlock(anchor, Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
        super.onRemove(state, level, pos, next, moved);
    }

    /** Ouvre l'inventaire (coffre double de 6 rangées) du bureau dont le bloc principal est en {@code pos}. */
    public static void openInventory(ServerPlayer player, Level level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof FurnitureBlockEntity furniture)) {
            return;
        }
        player.openMenu(new SimpleMenuProvider(
                (containerId, playerInv, p) -> ChestMenu.sixRows(containerId, playerInv, furniture.getCompound()),
                Component.translatable("container.pocketdoor.desk_center")));
    }
}
