package fr.jmproduction.pocketdoor.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class ChairBlockEntity extends BlockEntity {
    public ChairBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.POCKET_CHAIR, pos, state);
    }
}
