package fr.jmproduction.pocketdoor.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Chaise de bureau (32 px de haut, soit 2 blocs). Le modèle Blockbench est dessiné par
 * {@code ChairBlockEntityRenderer} ; le siège est un ArmorStand invisible léger.
 * Le dossier se trouve du côté {@code FACING}.
 */
public class ChairBlock extends Block implements EntityBlock {
    public static final DirectionProperty FACING = DirectionProperty.create("facing", Direction.Plane.HORIZONTAL);
    private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 16, 15);
    public ChairBlock(Properties properties) { super(properties); registerDefaultState(defaultBlockState().setValue(FACING, Direction.SOUTH)); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(FACING); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }
    // Le rendu passe par le BlockEntityRenderer : sans ENTITYBLOCK_ANIMATED, le modèle JSON de
    // secours (3 boîtes) s'afficherait à la place du vrai modèle.
    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.ENTITYBLOCK_ANIMATED; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new ChairBlockEntity(pos, state); }
    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext c) { return SHAPE; }
    @Override public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(level instanceof ServerLevel server) || player.isPassenger()) return InteractionResult.CONSUME;
        if (!server.getEntitiesOfClass(ArmorStand.class, new net.minecraft.world.phys.AABB(pos).inflate(0.4), e -> e.getTags().contains("pocketdoor_seat")).isEmpty()) return InteractionResult.CONSUME;
        ArmorStand seat = new ArmorStand(server, pos.getX()+.5, pos.getY()+.25, pos.getZ()+.5);
        seat.setInvisible(true); seat.setNoGravity(true); seat.setInvulnerable(true); seat.setSilent(true); seat.addTag("pocketdoor_seat");
        CompoundTag tag = new CompoundTag();
        seat.addAdditionalSaveData(tag);
        tag.putBoolean("Marker", true);
        tag.putBoolean("Invisible", true);
        seat.readAdditionalSaveData(tag);
        server.addFreshEntity(seat); player.startRiding(seat, true); return InteractionResult.CONSUME;
    }
    @Override public void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moved) {
        if (!state.is(next.getBlock()) && level instanceof ServerLevel server) {
            for (ArmorStand e : server.getEntitiesOfClass(ArmorStand.class, new net.minecraft.world.phys.AABB(pos).inflate(.6), x -> x.getTags().contains("pocketdoor_seat"))) e.discard();
        }
        super.onRemove(state, level, pos, next, moved);
    }
}
