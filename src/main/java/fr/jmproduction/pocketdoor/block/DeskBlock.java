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
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Vanilla-model master block for the centered, three-block-wide desk.
 * Clicking on the master opens the full 54-slot "double chest" view of the
 * desk storage. The two side anchors ({@link DeskPartBlock}) open the left
 * or right 27-slot sub-chest view instead.
 *
 * <p>All menu opening goes through {@link ServerPlayer#openMenu} with a
 * vanilla {@link SimpleMenuProvider}: there is no custom
 * {@link net.minecraft.world.inventory.MenuType} and no Fabric screen-handler
 * dependency.</p>
 */
public final class DeskBlock extends HorizontalDirectionalBlock implements EntityBlock {

    /** View constants shared with {@link DeskPartBlock}. */
    public static final int VIEW_LEFT = 0;
    public static final int VIEW_RIGHT = 1;
    public static final int VIEW_CENTER = 2;

    private static final VoxelShape SHAPE = Shapes.or(Block.box(-16, 0, 0, 32, 16, 16));

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

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos,
                                 Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        openView((ServerPlayer) player, level, pos, VIEW_CENTER);
        return InteractionResult.CONSUME;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moved) {
        if (!state.is(next.getBlock())) {
            // Drop the contents of both halves so the player never loses their
            // stored items when the master is broken.
            if (level.getBlockEntity(pos) instanceof FurnitureBlockEntity furniture) {
                Containers.dropContents(level, pos, furniture.getLeft());
                Containers.dropContents(level, pos, furniture.getRight());
            }
            // Then clean up the two side anchors that may still be in the world.
            Direction axis = state.getValue(FACING).getAxis() == Direction.Axis.X ? Direction.EAST : Direction.SOUTH;
            for (boolean positive : new boolean[]{false, true}) {
                BlockPos side = pos.relative(positive ? axis : axis.getOpposite());
                if (level.getBlockState(side).is(ModBlocks.DESK_PART)) {
                    level.setBlock(side, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
        super.onRemove(state, level, pos, next, moved);
    }

    /**
     * Open the requested sub-chest view through vanilla's
     * {@link ServerPlayer#openMenu} – no Fabric screen-handler hooks are
     * involved. The vanilla {@link ChestMenu#threeRows} factory is used for
     * the side halves; the full 54-slot view is exposed via
     * {@link ChestMenu#sixRows} backed by a {@link CompoundContainer}.
     */
    public static void openView(ServerPlayer player, Level level, BlockPos pos, int viewMode) {
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof FurnitureBlockEntity furniture)) {
            return;
        }
        final FurnitureBlockEntity target = furniture;
        final int mode = viewMode;
        Component title = Component.translatable(switch (mode) {
            case VIEW_LEFT -> "container.pocketdoor.desk_left";
            case VIEW_RIGHT -> "container.pocketdoor.desk_right";
            default -> "container.pocketdoor.desk_center";
        });
        SimpleMenuProvider provider = new SimpleMenuProvider(
                (containerId, playerInv, p) -> buildMenu(containerId, playerInv, target, mode),
                title);
        player.openMenu(provider);
    }

    private static AbstractContainerMenu buildMenu(int containerId,
                                                   net.minecraft.world.entity.player.Inventory playerInv,
                                                   FurnitureBlockEntity furniture,
                                                   int viewMode) {
        if (viewMode == VIEW_CENTER) {
            return ChestMenu.sixRows(containerId, playerInv,
                    new CompoundContainer(furniture.getLeft(), furniture.getRight()));
        } else if (viewMode == VIEW_LEFT) {
            return ChestMenu.threeRows(containerId, playerInv, furniture.getLeft());
        } else {
            return ChestMenu.threeRows(containerId, playerInv, furniture.getRight());
        }
    }
}
