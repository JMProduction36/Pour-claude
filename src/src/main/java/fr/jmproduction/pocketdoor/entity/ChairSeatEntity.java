package fr.jmproduction.pocketdoor.entity;

import fr.jmproduction.pocketdoor.block.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class ChairSeatEntity extends Entity {
    private BlockPos chairPos = BlockPos.ZERO;
    private int emptyTicks;

    public ChairSeatEntity(EntityType<? extends ChairSeatEntity> type, Level level) {
        super(type, level);
        setNoGravity(true);
        setInvulnerable(true);
        setSilent(true);
    }

    @Override
    public Packet<?> getAddEntityPacket() {
        return new ClientboundAddEntityPacket(this);
    }

    public void setChairPos(BlockPos pos) {
        chairPos = pos.immutable();
        setPos(pos.getX() + 0.5D, pos.getY() + 0.62D, pos.getZ() + 0.5D);
    }

    public boolean belongsTo(BlockPos pos) {
        return chairPos.equals(pos);
    }

    @Override
    public void tick() {
        super.tick();
        if (level.isClientSide) {
            return;
        }

        if (!level.getBlockState(chairPos).is(ModBlocks.POCKET_CHAIR)) {
            ejectPassengers();
            discard();
            return;
        }

        if (getPassengers().isEmpty()) {
            if (++emptyTicks > 1) {
                discard();
            }
            return;
        }

        emptyTicks = 0;
        setNoGravity(true);
        setDeltaMovement(Vec3.ZERO);
        setPos(chairPos.getX() + 0.5D, chairPos.getY() + 0.62D, chairPos.getZ() + 0.5D);
    }

    @Override
    protected void defineSynchedData() {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.contains("ChairPos")) {
            chairPos = BlockPos.of(tag.getLong("ChairPos"));
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putLong("ChairPos", chairPos.asLong());
    }

    @Override
    public double getPassengersRidingOffset() {
        return 0.0D;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return passenger instanceof Player && getPassengers().isEmpty();
    }
}
