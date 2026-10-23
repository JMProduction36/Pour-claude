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
import net.minecraft.world.phys.Vec3;
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
 *
 * <p>The desk is laid out perpendicular to its {@code FACING}: for
 * {@code NORTH}/{@code SOUTH} the three blocks run along the X axis, for
 * {@code EAST}/{@code WEST} they run along the Z axis. The collision /
 * outline shape, the side anchors and the in-master click-to-view mapping
 * all follow that same rule, so the desk never appears "twisted" when
 * placed to the left or right of the player.</p>
 */
public final class DeskBlock extends HorizontalDirectionalBlock implements EntityBlock {

    /** View constants shared with {@link DeskPartBlock}. */
    public static final int VIEW_LEFT = 0;
    public static final int VIEW_RIGHT = 1;
    public static final int VIEW_CENTER = 2;

    /**
     * Authored hitbox: the desk extends 3 blocks along the X axis (one block
     * to the left of the master at X=-16, the master at X=[0,16], and one
     * block to the right at X=[16,32]). Used when {@code FACING} is on the
     * Z axis (NORTH/SOUTH) – the parts sit along X.
     */
    private static final VoxelShape X_SHAPE = Shapes.or(Block.box(-16, 0, 0, 32, 16, 16));

    /**
     * Rotated hitbox: same 3-block-wide footprint but along the Z axis. Used
     * when {@code FACING} is on the X axis (EAST/WEST) – the parts sit along
     * Z. Without this, the previous code always returned {@link #X_SHAPE},
     * which left the outline / collision box stuck on the X axis even
     * though the visual model had been rotated 90°.
     */
    private static final VoxelShape Z_SHAPE = Shapes.or(Block.box(0, 0, -16, 16, 16, 32));

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

    /**
     * Hitbox must follow {@code FACING}. The block-model {@code y} rotation
     * (see {@code assets/pocketdoor/blockstates/desk.json}) rotates the
     * 3-block-wide model so it visually runs perpendicular to {@code FACING};
     * the outline / collision box has to follow the same axis or the
     * selection box stays "facing" the wrong way for EAST/WEST placements.
     */
    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext c) {
        return state.getValue(FACING).getAxis() == Direction.Axis.Z ? X_SHAPE : Z_SHAPE;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext c) {
        return state.getValue(FACING).getAxis() == Direction.Axis.Z ? X_SHAPE : Z_SHAPE;
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
     * The master's 3-block-wide hitbox lets the player click anywhere in the
     * desk footprint. We split that footprint into thirds along the axis
     * perpendicular to {@code FACING}:
     * <ul>
     *   <li>left third  → {@link #VIEW_LEFT}  (27-slot left half)</li>
     *   <li>middle third → {@link #VIEW_CENTER} (54-slot double chest)</li>
     *   <li>right third → {@link #VIEW_RIGHT} (27-slot right half)</li>
     * </ul>
     * The same mapping works for all four horizontal {@code FACING}s because
     * we compute the click position in the desk-local perpendicular axis
     * rather than relying on a hard-coded world axis.
     */
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos,
                                 Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        int view = resolveViewFromHit(state, pos, hit.getLocation());
        openView((ServerPlayer) player, level, pos, view);
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
            // The anchors always sit perpendicular to FACING, with the POSITIVE=true
            // anchor on the player's right side. For a desk FACING NORTH that means
            // EAST, for SOUTH that means WEST, for EAST that means SOUTH and for
            // WEST that means NORTH – which is exactly what FACING.getClockWise() gives.
            // The previous version used FACING.getAxis() to hard-pick EAST/SOUTH,
            // which broke every FACING where the player wasn't looking along +Z
            // (i.e. the EAST/WEST placement case reported in the bug).
            Direction positiveSide = state.getValue(FACING).getClockWise();
            for (boolean positive : new boolean[]{false, true}) {
                BlockPos side = pos.relative(positive ? positiveSide : positiveSide.getOpposite());
                if (level.getBlockState(side).is(ModBlocks.DESK_PART)) {
                    level.setBlock(side, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
        super.onRemove(state, level, pos, next, moved);
    }

    /**
     * Map a click on the master's 3-block-wide outline to one of the three
     * sub-chest views. {@code hit} is in world coordinates; we project it on
     * the axis perpendicular to {@code FACING} so the result is independent
     * of which way the desk is facing.
     */
    private static int resolveViewFromHit(BlockState state, BlockPos masterPos, Vec3 hit) {
        Direction facing = state.getValue(FACING);
        // side < -0.5 → left third (block at master - 1 along perpendicular)
        // side >  0.5 → right third (block at master + 1 along perpendicular)
        // otherwise   → centre third (the master block itself)
        double side = facing.getAxis() == Direction.Axis.Z
                ? hit.x - (masterPos.getX() + 0.5)
                : hit.z - (masterPos.getZ() + 0.5);
        if (side < -0.5) return VIEW_LEFT;
        if (side > 0.5) return VIEW_RIGHT;
        return VIEW_CENTER;
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
