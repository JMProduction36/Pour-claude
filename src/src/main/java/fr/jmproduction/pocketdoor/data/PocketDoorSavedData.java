package fr.jmproduction.pocketdoor.data;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.UUID;

/** Persistent world data for the single Pocket Door and its owner. */
public class PocketDoorSavedData extends SavedData {
    private static final String DATA_ID = "pocketdoor_data";
    private static final String HAS_DOOR = "HasDoor";
    private static final String POS = "Pos";
    private static final String DIMENSION = "Dimension";
    private static final String OWNER = "Owner";
    private static final String HAS_TARGET = "HasTarget";
    private static final String TARGET = "Target";

    private boolean hasDoor;
    private BlockPos doorPos = BlockPos.ZERO;
    private String dimensionId = Level.OVERWORLD.location().toString();
    private UUID owner;
    private boolean hasTarget;
    private BlockPos teleportTarget = BlockPos.ZERO;

    public PocketDoorSavedData() {
    }

    public static PocketDoorSavedData load(CompoundTag tag) {
        PocketDoorSavedData data = new PocketDoorSavedData();
        data.hasDoor = tag.getBoolean(HAS_DOOR);
        if (data.hasDoor && tag.contains(POS)) {
            data.doorPos = BlockPos.of(tag.getLong(POS));
            data.dimensionId = tag.getString(DIMENSION);
            if (tag.hasUUID(OWNER)) {
                data.owner = tag.getUUID(OWNER);
            }
        } else {
            data.hasDoor = false;
        }
        data.hasTarget = tag.getBoolean(HAS_TARGET);
        if (data.hasTarget && tag.contains(TARGET)) {
            data.teleportTarget = BlockPos.of(tag.getLong(TARGET));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putBoolean(HAS_DOOR, hasDoor);
        if (hasDoor) {
            tag.putLong(POS, doorPos.asLong());
            tag.putString(DIMENSION, dimensionId);
            if (owner != null) {
                tag.putUUID(OWNER, owner);
            }
        }
        tag.putBoolean(HAS_TARGET, hasTarget);
        if (hasTarget) {
            tag.putLong(TARGET, teleportTarget.asLong());
        }
        return tag;
    }

    public static PocketDoorSavedData get(ServerLevel level) {
        ServerLevel overworld = level.getServer().overworld();
        return overworld.getDataStorage().computeIfAbsent(
                PocketDoorSavedData::load,
                PocketDoorSavedData::new,
                DATA_ID
        );
    }

    public boolean hasDoor() {
        return hasDoor;
    }

    public BlockPos getDoorPos() {
        return doorPos;
    }

    public String getDimensionId() {
        return dimensionId;
    }

    public boolean hasOwner() {
        return owner != null;
    }

    public UUID getOwner() {
        return owner;
    }

    public boolean hasTeleportTarget() {
        return hasTarget;
    }

    public BlockPos getTeleportTarget() {
        return teleportTarget;
    }

    public void setTeleportTarget(BlockPos target) {
        this.hasTarget = true;
        this.teleportTarget = target.immutable();
        setDirty();
    }

    public void clearTeleportTarget() {
        this.hasTarget = false;
        this.teleportTarget = BlockPos.ZERO;
        setDirty();
    }

    public boolean isOwner(UUID uuid) {
        return owner != null && owner.equals(uuid);
    }

    public void setDoor(ServerLevel level, BlockPos pos, UUID owner) {
        this.hasDoor = true;
        this.doorPos = pos.immutable();
        this.dimensionId = level.dimension().location().toString();
        this.owner = owner;
        setDirty();
    }

    public void clearDoor() {
        this.hasDoor = false;
        this.doorPos = BlockPos.ZERO;
        this.dimensionId = Level.OVERWORLD.location().toString();
        this.owner = null;
        this.hasTarget = false;
        this.teleportTarget = BlockPos.ZERO;
        setDirty();
    }

    /** Clears the saved entry if the stored door was broken manually. */
    public boolean refreshAgainstWorld(ServerPlayer player) {
        if (!hasDoor) {
            return false;
        }

        ResourceLocation id = ResourceLocation.tryParse(dimensionId);
        if (id == null) {
            clearDoor();
            return false;
        }

        ResourceKey<Level> key = ResourceKey.create(Registry.DIMENSION_REGISTRY, id);
        ServerLevel doorLevel = player.getServer().getLevel(key);
        if (doorLevel == null) {
            return true;
        }

        boolean lowerExists = doorLevel.getBlockState(doorPos).getBlock()
                instanceof fr.jmproduction.pocketdoor.block.PocketDoorBlock;
        boolean upperExists = doorLevel.getBlockState(doorPos.above()).getBlock()
                instanceof fr.jmproduction.pocketdoor.block.PocketDoorBlock;
        if (!lowerExists || !upperExists) {
            clearDoor();
            return false;
        }

        return true;
    }
}
