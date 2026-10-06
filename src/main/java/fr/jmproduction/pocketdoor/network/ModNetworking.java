package fr.jmproduction.pocketdoor.network;

import fr.jmproduction.pocketdoor.PocketDoorMod;
import fr.jmproduction.pocketdoor.block.ModBlocks;
import fr.jmproduction.pocketdoor.data.PocketDoorSavedData;
import fr.jmproduction.pocketdoor.dimension.PocketDimensions;
import fr.jmproduction.pocketdoor.dimension.PocketDoorPortal;
import fr.jmproduction.pocketdoor.dimension.ImmersivePortalBridge;
import fr.jmproduction.pocketdoor.dimension.PocketOfficeGenerator;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

import java.util.UUID;

public final class ModNetworking {
    public static final ResourceLocation TOGGLE_DOOR =
            new ResourceLocation(PocketDoorMod.MOD_ID, "toggle_door");

    public static final ResourceLocation REMOVE_DOOR =
            new ResourceLocation(PocketDoorMod.MOD_ID, "remove_door");

    public static final ResourceLocation SET_TELEPORT_TARGET =
            new ResourceLocation(PocketDoorMod.MOD_ID, "set_teleport_target");

    private ModNetworking() {
    }

    public static void initialize() {
        ServerPlayNetworking.registerGlobalReceiver(TOGGLE_DOOR, (server, player, handler, buf, responseSender) -> {
            BlockPos target = buf.readBlockPos();
            Direction hitFace = buf.readEnum(Direction.class);
            server.execute(() -> createDoor(player, target, hitFace));
        });

        ServerPlayNetworking.registerGlobalReceiver(REMOVE_DOOR, (server, player, handler, buf, responseSender) ->
                server.execute(() -> removeStoredDoor(player)));

        ServerPlayNetworking.registerGlobalReceiver(SET_TELEPORT_TARGET, (server, player, handler, buf, responseSender) -> {
            int x = buf.readInt();
            int z = buf.readInt();
            server.execute(() -> setTeleportTarget(player, x, z));
        });
    }

    public static FriendlyByteBuf createTogglePacket(BlockPos target, Direction hitFace) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeBlockPos(target);
        buf.writeEnum(hitFace);
        return buf;
    }

    public static FriendlyByteBuf createRemovePacket() {
        return PacketByteBufs.create();
    }

    public static FriendlyByteBuf createTargetPacket(int x, int z) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeInt(x);
        buf.writeInt(z);
        return buf;
    }

    public static void removeStoredDoor(ServerPlayer player) {
        if (player.getLevel().dimension().equals(PocketDimensions.POCKET_OFFICE)) {
            player.displayClientMessage(Component.literal("La Porte de Poche ne peut pas être masquée depuis la dimension de poche."), true);
            return;
        }

        PocketDoorSavedData doorData = PocketDoorSavedData.get(player.getLevel());

        if (!doorData.hasDoor()) {
            player.displayClientMessage(Component.literal("Aucune Porte de Poche n'est présente."), true);
            return;
        }

        ResourceLocation id = ResourceLocation.tryParse(doorData.getDimensionId());
        if (id == null) {
            doorData.clearDoor();
            player.displayClientMessage(Component.literal("Aucune Porte de Poche n'est présente."), true);
            return;
        }

        ResourceKey<Level> key = ResourceKey.create(net.minecraft.core.Registry.DIMENSION_REGISTRY, id);
        ServerLevel exterior = player.getServer().getLevel(key);
        ServerLevel pocket = player.getServer().getLevel(PocketDimensions.POCKET_OFFICE);

        // Remove the real Immersive Portals render entities before removing the door.
        if (exterior != null) {
            ImmersivePortalBridge.cleanupAll(exterior, doorData.getDoorPos());
        } else if (pocket != null) {
            ImmersivePortalBridge.cleanupAll(pocket, PocketOfficeGenerator.POCKET_DOOR_LOWER);
        }

        boolean removed = false;
        if (exterior != null) {
            BlockPos lower = doorData.getDoorPos();
            if (exterior.getBlockState(lower).is(ModBlocks.POCKET_DOOR)) {
                exterior.removeBlock(lower, false);
                removed = true;
            }
            if (exterior.getBlockState(lower.above()).is(ModBlocks.POCKET_DOOR)) {
                exterior.removeBlock(lower.above(), false);
                removed = true;
            }
            // The protected support block remains untouched.
        }

        if (pocket != null) {
            BlockPos lower = PocketOfficeGenerator.POCKET_DOOR_LOWER;
            if (pocket.getBlockState(lower).is(ModBlocks.POCKET_DOOR)) {
                pocket.removeBlock(lower, false);
                removed = true;
            }
            if (pocket.getBlockState(lower.above()).is(ModBlocks.POCKET_DOOR)) {
                pocket.removeBlock(lower.above(), false);
                removed = true;
            }
        }

        doorData.clearDoor();
        player.displayClientMessage(Component.literal(removed
                ? "Porte masquée."
                : "Aucune Porte de Poche n'est présente."), true);
    }

    private static void setTeleportTarget(ServerPlayer player, int x, int z) {
        if (!player.getLevel().dimension().equals(PocketDimensions.POCKET_OFFICE)) {
            return;
        }

        PocketDoorSavedData existing = PocketDoorSavedData.get(player.getLevel());
        if (!existing.hasDoor()) {
            player.displayClientMessage(Component.literal("Aucune Porte de Poche n'est configurée."), true);
            return;
        }

        ServerLevel overworld = player.getServer().overworld();
        int y = overworld.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        if (y <= overworld.getMinBuildHeight()) {
            player.displayClientMessage(Component.literal("Destination impossible ici."), true);
            return;
        }

        BlockPos destination = new BlockPos(x, y, z);
        BlockState destinationLower = overworld.getBlockState(destination);
        BlockState destinationUpper = overworld.getBlockState(destination.above());

        // The selected map coordinate is the exact X/Z where the physical Pocket Door
        // is going to reappear. The surface block is kept untouched; both door blocks
        // must be free (or replaceable) before moving the real door there.
        if (!isReplaceable(destinationLower) || !isReplaceable(destinationUpper)) {
            player.displayClientMessage(Component.literal(
                    "Impossible de déplacer la porte ici : l'emplacement est bloqué."), true);
            return;
        }

        ResourceLocation currentId = ResourceLocation.tryParse(existing.getDimensionId());
        ServerLevel oldDoorLevel = currentId == null
                ? null
                : player.getServer().getLevel(ResourceKey.create(net.minecraft.core.Registry.DIMENSION_REGISTRY, currentId));
        if (oldDoorLevel == null || !oldDoorLevel.getBlockState(existing.getDoorPos()).is(ModBlocks.POCKET_DOOR)) {
            player.displayClientMessage(Component.literal(
                    "La Porte de Poche actuelle n'existe plus."), true);
            return;
        }

        BlockPos oldPos = existing.getDoorPos();
        BlockState oldLower = oldDoorLevel.getBlockState(oldPos);
        Direction facing = oldLower.getValue(DoorBlock.FACING);
        DoorHingeSide hinge = oldLower.getValue(DoorBlock.HINGE);

        // Make absolutely sure no old live portal/render remains at the previous location.
        ImmersivePortalBridge.cleanupAll(oldDoorLevel, oldPos);

        // The office door does not move: only the Overworld/exterior endpoint changes.
        // The player stays in the pocket office, which creates the intended illusion that
        // the exterior door itself has teleported to the selected map coordinate.
        if (oldDoorLevel.getBlockState(oldPos).is(ModBlocks.POCKET_DOOR)) {
            oldDoorLevel.removeBlock(oldPos, false);
        }
        if (oldDoorLevel.getBlockState(oldPos.above()).is(ModBlocks.POCKET_DOOR)) {
            oldDoorLevel.removeBlock(oldPos.above(), false);
        }

        BlockState newLower = ModBlocks.POCKET_DOOR.defaultBlockState()
                .setValue(DoorBlock.FACING, facing)
                .setValue(DoorBlock.HINGE, hinge)
                .setValue(DoorBlock.OPEN, false)
                .setValue(DoorBlock.POWERED, false)
                .setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER);
        BlockState newUpper = newLower.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER);

        overworld.setBlock(destination, newLower, 3);
        overworld.setBlock(destination.above(), newUpper, 3);

        // Keep the persistent door record in sync with the newly moved physical model.
        UUID owner = existing.hasOwner() ? existing.getOwner() : player.getUUID();
        existing.setTeleportTarget(destination);
        existing.setDoor(overworld, destination, owner);

        // Prepare the NEW exterior endpoint immediately. Previously the portal pair was
        // only recreated when the door was closed and opened again, which made the render
        // appear to load only after leaving the map interface. Keep the player in the pocket
        // office, but refresh the two portal endpoints as soon as the destination is confirmed.
        // The real door remains closed; the portal sits behind the physical door model and
        // becomes visible as soon as the player opens it.
        overworld.getChunk(destination.getX() >> 4, destination.getZ() >> 4);
        ServerLevel pocket = player.getServer().getLevel(PocketDimensions.POCKET_OFFICE);
        if (pocket != null) {
            PocketOfficeGenerator.ensureGenerated(pocket);
            ImmersivePortalBridge.open(overworld, destination, facing, pocket);
        }

        player.displayClientMessage(Component.literal(
                "Porte déplacée et destination chargée : X " + x + " Y " + y + " Z " + z), true);
        PocketDoorMod.LOGGER.info(
                "Pocket Door moved to {} {} {} in the Overworld for {}",
                x, y, z, player.getUUID());
    }

    private static void createDoor(ServerPlayer player, BlockPos target, Direction hitFace) {
        if (player.distanceToSqr(target.getX() + 0.5D, target.getY() + 0.5D, target.getZ() + 0.5D) > 64.0D) {
            player.displayClientMessage(Component.literal("La porte est trop loin."), true);
            return;
        }

        ServerLevel level = player.getLevel();
        if (level.dimension().equals(PocketDimensions.POCKET_OFFICE)) {
            player.displayClientMessage(Component.literal("P ne sert à créer la porte qu'à l'extérieur."), true);
            return;
        }

        BlockState targetState = level.getBlockState(target);
        PocketDoorSavedData doorData = PocketDoorSavedData.get(level);

        // P on the stored door removes the whole linked system.
        if (targetState.is(ModBlocks.POCKET_DOOR)) {
            removeStoredDoor(player);
            return;
        }

        if (doorData.refreshAgainstWorld(player)) {
            player.displayClientMessage(Component.literal("Il n'existe déjà qu'une seule Porte de Poche dans ce monde."), true);
            return;
        }

        // The support may be any non-air block. If the player is actually aiming at a
        // replaceable plant/grass/snow layer, it is crushed and the solid block below
        // becomes the support, which keeps the door sitting directly on the ground.
        if (hitFace != Direction.UP || targetState.isAir()) {
            player.displayClientMessage(Component.literal("Vise le dessus d'un bloc non vide."), true);
            return;
        }

        BlockPos supportPos = targetState.getMaterial().isReplaceable() ? target.below() : target;
        BlockState supportState = level.getBlockState(supportPos);
        if (supportState.isAir()) {
            player.displayClientMessage(Component.literal("La porte doit avoir un bloc sous elle."), true);
            return;
        }

        BlockPos lowerPos = supportPos.above();
        BlockPos upperPos = lowerPos.above();

        // Replace only air/replaceable foliage/fluid occupants; solid blocks remain untouched.
        if (!isReplaceable(level.getBlockState(lowerPos)) || !isReplaceable(level.getBlockState(upperPos))) {
            player.displayClientMessage(Component.literal("La porte a besoin de deux blocs libres au-dessus."), true);
            return;
        }

        BlockState lower = ModBlocks.POCKET_DOOR.defaultBlockState()
                .setValue(DoorBlock.FACING, player.getDirection().getOpposite())
                .setValue(DoorBlock.HINGE, DoorHingeSide.RIGHT)
                .setValue(DoorBlock.OPEN, false)
                .setValue(DoorBlock.POWERED, false)
                .setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER);

        BlockState upper = lower.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER);

        level.setBlock(lowerPos, lower, 3);
        level.setBlock(upperPos, upper, 3);
        doorData.setDoor(level, lowerPos, player.getUUID());

        player.displayClientMessage(Component.literal("Porte créée."), true);
        PocketDoorMod.LOGGER.info("Pocket Door created at {} in {} by {}", lowerPos, level.dimension().location(), player.getUUID());
    }

    private static boolean isReplaceable(BlockState state) {
        return state.isAir() || state.getMaterial().isReplaceable();
    }
}
