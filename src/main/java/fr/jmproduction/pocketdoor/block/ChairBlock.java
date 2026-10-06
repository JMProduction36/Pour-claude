package fr.jmproduction.pocketdoor.block;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Invisible seat block: no custom model/texture is needed yet. */
public class ChairBlock extends Block {
    private static final VoxelShape SHAPE = Block.box(2, 0, 2, 14, 8, 14);

    public ChairBlock(Properties properties) {
        super(properties);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public InteractionResult use(BlockState state, net.minecraft.world.level.Level level, BlockPos pos,
                                 Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.PASS;
        }

        if (player.isPassenger()) {
            return InteractionResult.CONSUME;
        }

        ArmorStand seat = new ArmorStand(serverLevel, pos.getX() + 0.5D, pos.getY() + 0.35D, pos.getZ() + 0.5D);
        seat.setInvisible(true);
        seat.setNoGravity(true);
        seat.setInvulnerable(true);
        seat.setSilent(true);
        seat.addTag("pocketdoor_seat");
        serverLevel.addFreshEntity(seat);
        player.startRiding(seat, true);
        return InteractionResult.CONSUME;
    }
}
