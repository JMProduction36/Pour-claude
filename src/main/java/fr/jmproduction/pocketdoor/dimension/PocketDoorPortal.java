package fr.jmproduction.pocketdoor.dimension;

import fr.jmproduction.pocketdoor.block.ModBlocks;
import fr.jmproduction.pocketdoor.data.PocketDoorSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/** Handles the linked door state; Immersive Portals performs the seamless cross-dimension teleport. */
public final class PocketDoorPortal {
    private static final Map<UUID, Vec3> LAST_POSITIONS = new HashMap<>();
    private static final Map<UUID, Long> TELEPORT_COOLDOWN_UNTIL = new HashMap<>();
    private static final Map<UUID, ResourceKey<Level>> LAST_LEVELS = new HashMap<>();
    private static final double DOOR_HALF_WIDTH = 0.55D;
    private static final double DOOR_MIN_Y_OFFSET = 0.0D;
    private static final double DOOR_MAX_Y_OFFSET = 2.05D;

    private PocketDoorPortal() {
    }

    /**
     * Right-clicking only toggles the door. Walking through an open doorway performs the teleport.
     */
    public static boolean interact(ServerPlayer player, BlockPos clickedPos) {
        ServerLevel level = player.getLevel();
        PocketDoorSavedData data = PocketDoorSavedData.get(level);

        if (!data.hasDoor()) {
            return false;
        }

        if (level.dimension().equals(PocketDimensions.POCKET_OFFICE)) {
            BlockPos lower = normalizeLower(level, clickedPos);
            if (!lower.equals(PocketOfficeGenerator.POCKET_DOOR_LOWER)) {
                return false;
            }

            boolean nextOpen = !isDoorOpen(level, lower);
            setOpen(level, lower, nextOpen);

            ServerLevel exterior = getDoorLevel(level, data);
            if (exterior != null) {
                setOpen(exterior, data.getDoorPos(), nextOpen);
                ServerLevel pocket = player.getServer().getLevel(PocketDimensions.POCKET_OFFICE);
                if (pocket != null) {
                    if (nextOpen) {
                        ImmersivePortalBridge.open(exterior, data.getDoorPos(),
                                exterior.getBlockState(data.getDoorPos()).getValue(DoorBlock.FACING), pocket);
                    } else {
                        ImmersivePortalBridge.close(exterior, data.getDoorPos());
                    }
                }
            }
            return true;
        }

        if (!level.dimension().location().toString().equals(data.getDimensionId())) {
            return false;
        }

        BlockPos lower = normalizeLower(level, clickedPos);
        if (!level.getBlockState(lower).is(ModBlocks.POCKET_DOOR)) {
            return false;
        }
        if (!lower.equals(data.getDoorPos())) {
            return false;
        }

        boolean nextOpen = !isDoorOpen(level, lower);
        setOpen(level, lower, nextOpen);

        ServerLevel pocket = player.getServer().getLevel(PocketDimensions.POCKET_OFFICE);
        if (pocket != null) {
            PocketOfficeGenerator.ensureGenerated(pocket);
            setOpen(pocket, PocketOfficeGenerator.POCKET_DOOR_LOWER, nextOpen);
            if (nextOpen) {
                ImmersivePortalBridge.open(level, lower, level.getBlockState(lower).getValue(DoorBlock.FACING), pocket);
            } else {
                ImmersivePortalBridge.close(level, lower);
            }
        }
        return true;
    }

    /** Checks for an actual crossing of the doorway plane and teleports the player. */
    public static void checkCrossing(ServerLevel level) {
        DoorEndpoint endpoint = getEndpoint(level);
        if (endpoint == null || !isDoorOpenForLevel(level)) {
            return;
        }

        // Never evaluate a crossing across two different dimensions. A dimension transfer
        // changes the player's coordinates discontinuously, so treating that as movement
        // through the destination door can immediately bounce the player back.
        ResourceKey<Level> currentLevelKey = level.dimension();

        // Take a snapshot: teleporting a player removes it from the current level's
        // player list. Iterating the live list while doing that causes a
        // ConcurrentModificationException.
        List<ServerPlayer> players = new ArrayList<>(level.players());
        List<ServerPlayer> pendingTeleports = new ArrayList<>();

        for (ServerPlayer player : players) {
            if (player.isRemoved()) {
                continue;
            }

            UUID uuid = player.getUUID();
            Vec3 current = player.position();
            ResourceKey<Level> previousLevel = LAST_LEVELS.get(uuid);
            Vec3 previous = LAST_POSITIONS.get(uuid);

            LAST_LEVELS.put(uuid, currentLevelKey);
            LAST_POSITIONS.put(uuid, current);

            long now = level.getGameTime();
            Long cooldownUntil = TELEPORT_COOLDOWN_UNTIL.get(uuid);
            if (cooldownUntil != null && now < cooldownUntil) {
                continue;
            }

            if (previous == null || previousLevel == null || !previousLevel.equals(currentLevelKey)) {
                continue;
            }

            if (!crossedDoor(previous, current, endpoint)) {
                continue;
            }

            // Arm a generous debounce before changing dimensions.
            TELEPORT_COOLDOWN_UNTIL.put(uuid, now + 30L);
            pendingTeleports.add(player);
        }

        // Perform dimension transfers only after the player-list iteration has finished.
        for (ServerPlayer player : pendingTeleports) {
            if (player.isRemoved()) {
                continue;
            }

            if (endpoint.isPocketSide) {
                teleportToOutside(player, endpoint.data);
            } else {
                teleportToPocket(player, endpoint.data);
            }

            // The next tick in the destination dimension starts with a fresh baseline.
            LAST_LEVELS.remove(player.getUUID());
            LAST_POSITIONS.remove(player.getUUID());
        }

        // Avoid stale entries from players who have left the server.
        LAST_POSITIONS.keySet().removeIf(uuid -> level.getServer().getPlayerList().getPlayer(uuid) == null);
        LAST_LEVELS.keySet().removeIf(uuid -> level.getServer().getPlayerList().getPlayer(uuid) == null);
        TELEPORT_COOLDOWN_UNTIL.keySet().removeIf(uuid -> level.getServer().getPlayerList().getPlayer(uuid) == null);
    }

    private static boolean crossedDoor(Vec3 previous, Vec3 current, DoorEndpoint endpoint) {
        Vec3 center = endpoint.center;
        Direction facing = endpoint.facing;
        double prevSigned = signedDistance(previous, center, facing);
        double currSigned = signedDistance(current, center, facing);

        // We need to cross the actual plane, not merely approach it.
        if ((prevSigned > 0 && currSigned >= 0) || (prevSigned < 0 && currSigned <= 0)) {
            return false;
        }

        double yOffset = current.y - endpoint.lower.getY();
        if (yOffset < DOOR_MIN_Y_OFFSET || yOffset > DOOR_MAX_Y_OFFSET) {
            return false;
        }

        double lateral;
        if (facing.getAxis() == Direction.Axis.Z) {
            lateral = Math.abs(current.x - center.x);
        } else {
            lateral = Math.abs(current.z - center.z);
        }

        return lateral <= DOOR_HALF_WIDTH;
    }

    private static double signedDistance(Vec3 position, Vec3 center, Direction normal) {
        return (position.x - center.x) * normal.getStepX()
                + (position.z - center.z) * normal.getStepZ();
    }

    private static boolean isDoorOpenForLevel(ServerLevel level) {
        if (level.dimension().equals(PocketDimensions.POCKET_OFFICE)) {
            return isDoorOpen(level, PocketOfficeGenerator.POCKET_DOOR_LOWER);
        }

        PocketDoorSavedData data = PocketDoorSavedData.get(level);
        if (!data.hasDoor()) return false;
        ServerLevel doorLevel = getDoorLevel(level, data);
        return doorLevel != null && doorLevel.dimension().equals(level.dimension()) && isDoorOpen(doorLevel, data.getDoorPos());
    }

    private static DoorEndpoint getEndpoint(ServerLevel level) {
        if (level.dimension().equals(PocketDimensions.POCKET_OFFICE)) {
            BlockPos lower = PocketOfficeGenerator.POCKET_DOOR_LOWER;
            BlockState state = level.getBlockState(lower);
            if (!state.is(ModBlocks.POCKET_DOOR)) return null;
            return new DoorEndpoint(
                    lower,
                    new Vec3(lower.getX() + 0.5D, lower.getY(), lower.getZ() + 0.5D),
                    state.getValue(DoorBlock.FACING).getOpposite(),
                    PocketDoorSavedData.get(level),
                    true
            );
        }

        PocketDoorSavedData data = PocketDoorSavedData.get(level);
        if (!data.hasDoor()) return null;
        if (!level.dimension().location().toString().equals(data.getDimensionId())) return null;
        BlockPos lower = data.getDoorPos();
        BlockState state = level.getBlockState(lower);
        if (!state.is(ModBlocks.POCKET_DOOR)) return null;
        return new DoorEndpoint(
                lower,
                new Vec3(lower.getX() + 0.5D, lower.getY(), lower.getZ() + 0.5D),
                state.getValue(DoorBlock.FACING),
                data,
                false
        );
    }

    private static final class DoorEndpoint {
        final BlockPos lower;
        final Vec3 center;
        final Direction facing;
        final PocketDoorSavedData data;
        final boolean isPocketSide;

        DoorEndpoint(BlockPos lower, Vec3 center, Direction facing, PocketDoorSavedData data, boolean isPocketSide) {
            this.lower = lower;
            this.center = center;
            this.facing = facing;
            this.data = data;
            this.isPocketSide = isPocketSide;
        }
    }

    /** Synchronizes the pocket-side door with the linked exterior door when the dimension loads. */
    public static void syncPocketDoorState(ServerLevel pocket) {
        if (!pocket.dimension().equals(PocketDimensions.POCKET_OFFICE)) {
            return;
        }

        ServerLevel exterior = pocket.getServer().overworld();
        PocketDoorSavedData data = PocketDoorSavedData.get(exterior);
        if (!data.hasDoor()) {
            setOpen(pocket, PocketOfficeGenerator.POCKET_DOOR_LOWER, false);
            return;
        }

        ServerLevel doorLevel = getDoorLevel(exterior, data);
        if (doorLevel == null || !doorLevel.getBlockState(data.getDoorPos()).is(ModBlocks.POCKET_DOOR)) {
            setOpen(pocket, PocketOfficeGenerator.POCKET_DOOR_LOWER, false);
            return;
        }

        boolean open = isDoorOpen(doorLevel, data.getDoorPos());
        setOpen(pocket, PocketOfficeGenerator.POCKET_DOOR_LOWER, open);
        if (open) {
            ImmersivePortalBridge.ensureOpen(doorLevel, data.getDoorPos(),
                    doorLevel.getBlockState(data.getDoorPos()).getValue(DoorBlock.FACING), pocket);
        } else {
            ImmersivePortalBridge.close(doorLevel, data.getDoorPos());
        }
    }

    public static void syncPocketTime(ServerLevel exterior) {
        PocketDoorSavedData data = PocketDoorSavedData.get(exterior);
        if (!data.hasDoor()) {
            return;
        }
        ServerLevel doorLevel = getDoorLevel(exterior, data);
        if (doorLevel == null || !isDoorOpen(doorLevel, data.getDoorPos())) {
            return;
        }
        ServerLevel pocket = exterior.getServer().getLevel(PocketDimensions.POCKET_OFFICE);
        if (pocket != null) {
            pocket.setDayTime(doorLevel.getDayTime());
        }
    }

    public static boolean isDoorOpen(ServerLevel level, BlockPos lower) {
        return level.getBlockState(lower).is(ModBlocks.POCKET_DOOR)
                && level.getBlockState(lower).getValue(DoorBlock.OPEN);
    }

    private static void teleportToPocket(ServerPlayer player, PocketDoorSavedData data) {
        ServerLevel exterior = getDoorLevel(player.getServer().overworld(), data);
        if (exterior == null) return;

        ServerLevel pocket = player.getServer().getLevel(PocketDimensions.POCKET_OFFICE);
        if (pocket == null) return;
        PocketOfficeGenerator.ensureGenerated(pocket);
        setOpen(pocket, PocketOfficeGenerator.POCKET_DOOR_LOWER, true);

        BlockState exteriorDoor = exterior.getBlockState(data.getDoorPos());
        Direction exteriorFacing = exteriorDoor.getValue(DoorBlock.FACING);
        Direction enterDirection = exteriorFacing.getOpposite();

        // The pocket door is on the south wall (Z=16); the office interior is north of it.
        // Spawn just inside the room so the player never arrives outside the generated shell.
        Direction pocketInterior = Direction.NORTH;
        double x = PocketOfficeGenerator.POCKET_DOOR_LOWER.getX() + 0.5D;
        double y = PocketOfficeGenerator.FLOOR_Y + 1.05D;
        double z = PocketOfficeGenerator.POCKET_DOOR_LOWER.getZ() + 0.5D + pocketInterior.getStepZ() * 1.15D;
        player.teleportTo(pocket, x, y, z, pocketInterior.toYRot(), player.getXRot());
    }

    private static void teleportToOutside(ServerPlayer player, PocketDoorSavedData data) {
        ServerLevel exterior = getDoorLevel(player.getServer().overworld(), data);
        if (exterior == null) return;

        BlockPos doorPos = data.getDoorPos();
        BlockState door = exterior.getBlockState(doorPos);
        Direction facing = door.is(ModBlocks.POCKET_DOOR) ? door.getValue(DoorBlock.FACING) : Direction.SOUTH;
        Direction exitDirection = facing;

        double x = doorPos.getX() + 0.5D + exitDirection.getStepX() * 1.15D;
        double y = doorPos.getY() + 0.05D;
        double z = doorPos.getZ() + 0.5D + exitDirection.getStepZ() * 1.15D;
        player.teleportTo(exterior, x, y, z, exitDirection.toYRot(), player.getXRot());
    }

    private static ServerLevel getDoorLevel(ServerLevel fallback, PocketDoorSavedData data) {
        net.minecraft.resources.ResourceLocation id = net.minecraft.resources.ResourceLocation.tryParse(data.getDimensionId());
        if (id == null) return null;
        net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> key =
                net.minecraft.resources.ResourceKey.create(net.minecraft.core.Registry.DIMENSION_REGISTRY, id);
        return fallback.getServer().getLevel(key);
    }

    private static BlockPos normalizeLower(ServerLevel level, BlockPos clickedPos) {
        BlockPos lower = clickedPos;
        if (level.getBlockState(clickedPos).getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER) {
            lower = clickedPos.below();
        }
        return lower;
    }

    public static void setOpen(ServerLevel level, BlockPos lower, boolean open) {
        BlockPos upper = lower.above();
        if (!level.getBlockState(lower).is(ModBlocks.POCKET_DOOR)) return;

        level.setBlock(lower, level.getBlockState(lower).setValue(DoorBlock.OPEN, open), 10);
        if (level.getBlockState(upper).is(ModBlocks.POCKET_DOOR)) {
            level.setBlock(upper, level.getBlockState(upper).setValue(DoorBlock.OPEN, open), 10);
        }
    }
}
