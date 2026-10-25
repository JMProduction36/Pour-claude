package fr.jmproduction.pocketdoor.block;

import fr.jmproduction.pocketdoor.dimension.PocketDoorPortal;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Custom Pocket Door. */
public class PocketDoorBlock extends DoorBlock {
    private static final VoxelShape CLOSED_NS_OUTLINE = Block.box(0, 0, 5, 16, 16, 11);
    private static final VoxelShape CLOSED_EW_OUTLINE = Block.box(5, 0, 0, 11, 16, 16);

    private static final VoxelShape CLOSED_NS_COLLISION = Block.box(1, 0, 6, 15, 16, 10);
    private static final VoxelShape CLOSED_EW_COLLISION = Block.box(6, 0, 1, 10, 16, 15);

    public PocketDoorBlock(Properties properties) {
        super(properties);
    }

    /**
     * La porte ne réagit jamais à la redstone : l'ouverture/fermeture est pilotée
     * uniquement par {@code PocketDoorPortal} (clic droit), afin que les deux portes
     * et les portails Immersive Portals restent synchronisés.
     */
    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block,
                                BlockPos fromPos, boolean isMoving) {
        // Volontairement vide (pas de POWERED, pas d'ouverture par redstone).
    }

    /**
     * Vanilla supprime une porte quand son support disparaît ou quand l'autre moitié
     * manque. Ici la porte ne doit JAMAIS disparaître toute seule (explosion qui détruit
     * le bloc support, etc.) : on garde toujours l'état tel quel.
     */
    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                  LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        return state;
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return true;
    }

    @Override
    public PushReaction getPistonPushReaction(BlockState state) {
        return PushReaction.BLOCK;
    }

    @Override
    public boolean dropFromExplosion(Explosion explosion) {
        return false;
    }

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
