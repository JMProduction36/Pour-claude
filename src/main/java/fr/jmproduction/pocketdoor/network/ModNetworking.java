package fr.jmproduction.pocketdoor.network;

import fr.jmproduction.pocketdoor.PocketDoorMod;
import fr.jmproduction.pocketdoor.block.ModBlocks;
import fr.jmproduction.pocketdoor.block.TypewriterBlockEntity;
import fr.jmproduction.pocketdoor.data.PocketDoorSavedData;
import fr.jmproduction.pocketdoor.dimension.PocketDimensions;
import fr.jmproduction.pocketdoor.dimension.ImmersivePortalBridge;
import fr.jmproduction.pocketdoor.dimension.PocketOfficeGenerator;
import fr.jmproduction.pocketdoor.dimension.PocketDoorPortal;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.tags.BlockTags;
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

    public static final ResourceLocation FINISH_TYPEWRITER =
            new ResourceLocation(PocketDoorMod.MOD_ID, "finish_typewriter");

    public static final ResourceLocation OPEN_TYPEWRITER =
            new ResourceLocation(PocketDoorMod.MOD_ID, "open_typewriter");

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

        ServerPlayNetworking.registerGlobalReceiver(FINISH_TYPEWRITER,
                (server, player, handler, buf, responseSender) -> {
                    BlockPos pos = buf.readBlockPos();
                    InteractionHand hand = buf.readEnum(InteractionHand.class);
                    server.execute(() -> finishTypewriter(player, pos, hand));
                });

        ServerPlayNetworking.registerGlobalReceiver(OPEN_TYPEWRITER,
                (server, player, handler, buf, responseSender) -> {
                    BlockPos pos = buf.readBlockPos();
                    InteractionHand hand = buf.readEnum(InteractionHand.class);
                    server.execute(() -> openTypewriterBook(player, pos, hand));
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

    public static FriendlyByteBuf createFinishTypewriterPacket(BlockPos pos, InteractionHand hand) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeBlockPos(pos);
        buf.writeEnum(hand);
        return buf;
    }

    public static void openTypewriterBook(ServerPlayer player, BlockPos pos, InteractionHand hand) {
        if (player.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) > 64.0D) {
            return;
        }

        BlockEntity blockEntity = player.getLevel().getBlockEntity(pos);
        if (!(blockEntity instanceof TypewriterBlockEntity typewriter) || !typewriter.hasBook()) {
            return;
        }

        ItemStack book = typewriter.takeBook();
        player.setItemInHand(hand, book);
        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeBlockPos(pos);
        buf.writeEnum(hand);
        buf.writeItem(book.copy());
        ServerPlayNetworking.send(player, OPEN_TYPEWRITER, buf);
    }

    private static void finishTypewriter(ServerPlayer player, BlockPos pos, InteractionHand hand) {
        if (player.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) > 64.0D) {
            return;
        }

        BlockEntity blockEntity = player.getLevel().getBlockEntity(pos);
        if (!(blockEntity instanceof TypewriterBlockEntity typewriter)) {
            return;
        }

        ItemStack book = player.getItemInHand(hand);
        if (typewriter.acceptsBook(book)) {
            typewriter.setBook(book.copy());
            player.setItemInHand(hand, ItemStack.EMPTY);
        }
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
        // Force-load the target chunk before reading its heightmap or changing blocks.
        overworld.getChunk(x >> 4, z >> 4);
        if (!overworld.getWorldBorder().isWithinBounds(new BlockPos(x, overworld.getMinBuildHeight(), z))) {
            player.displayClientMessage(Component.literal("Destination impossible ici."), true);
            return;
        }

        BlockPos destination = findSafeDestination(overworld, x, z);
        if (destination == null) {
            player.displayClientMessage(Component.literal(
                    "Impossible de déplacer la porte ici : aucun sol sûr n'a été trouvé."), true);
            return;
        }

        ResourceLocation currentId = ResourceLocation.tryParse(existing.getDimensionId());
        ServerLevel oldDoorLevel = currentId == null
                ? null
                : player.getServer().getLevel(ResourceKey.create(net.minecraft.core.Registry.DIMENSION_REGISTRY, currentId));
        if (oldDoorLevel == null) {
            player.displayClientMessage(Component.literal(
                    "La Porte de Poche actuelle n'existe plus."), true);
            return;
        }

        BlockPos oldPos = existing.getDoorPos();
        // Load the old chunk as well: the old exterior door may be far away or in another dimension.
        oldDoorLevel.getChunk(oldPos.getX() >> 4, oldPos.getZ() >> 4);
        if (!oldDoorLevel.getBlockState(oldPos).is(ModBlocks.POCKET_DOOR)
                || !oldDoorLevel.getBlockState(oldPos.above()).is(ModBlocks.POCKET_DOOR)) {
            player.displayClientMessage(Component.literal(
                    "La Porte de Poche actuelle n'existe plus."), true);
            return;
        }

        // Reject the old two-block footprint before removing anything. A destination that is
        // identical to, or overlaps, the old footprint must never be replaced in-place.
        if (sameDoorFootprint(oldDoorLevel, oldPos, overworld, destination)) {
            player.displayClientMessage(Component.literal(
                    "La nouvelle destination doit être différente de la porte actuelle."), true);
            return;
        }

        BlockState oldLower = oldDoorLevel.getBlockState(oldPos);
        Direction facing = oldLower.getValue(DoorBlock.FACING);
        DoorHingeSide hinge = oldLower.getValue(DoorBlock.HINGE);

        // Remove the old portal and both door halves before placing anything at the new site.
        ImmersivePortalBridge.cleanupAll(oldDoorLevel, oldPos);
        oldDoorLevel.removeBlock(oldPos, false);
        oldDoorLevel.removeBlock(oldPos.above(), false);

        BlockState newLower = ModBlocks.POCKET_DOOR.defaultBlockState()
                .setValue(DoorBlock.FACING, facing)
                .setValue(DoorBlock.HINGE, hinge)
                .setValue(DoorBlock.OPEN, true)
                .setValue(DoorBlock.POWERED, false)
                .setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER);
        BlockState newUpper = newLower.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER);

        overworld.setBlock(destination, newLower, 3);
        overworld.setBlock(destination.above(), newUpper, 3);

        UUID owner = existing.hasOwner() ? existing.getOwner() : player.getUUID();
        existing.setTeleportTarget(destination);
        existing.setDoor(overworld, destination, owner);

        ServerLevel pocket = player.getServer().getLevel(PocketDimensions.POCKET_OFFICE);
        if (pocket != null) {
            PocketOfficeGenerator.ensureGenerated(pocket);
            PocketDoorPortal.setOpen(pocket, PocketOfficeGenerator.POCKET_DOOR_LOWER, true);
            ImmersivePortalBridge.open(overworld, destination, facing, pocket);
        }

        player.displayClientMessage(Component.literal(
                "Porte déplacée et destination chargée : X " + x + " Y " + destination.getY() + " Z " + z), true);
        PocketDoorMod.LOGGER.info(
                "Pocket Door moved to {} {} {} in the Overworld for {}",
                x, destination.getY(), z, player.getUUID());
    }

    /** Finds the first safe two-block doorway above the heightmap surface. */
    private static BlockPos findSafeDestination(ServerLevel level, int x, int z) {
        int height = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        int start = Math.max(level.getMinBuildHeight() + 1, height - 2);
        int end = Math.min(level.getMaxBuildHeight() - 2, height + 8);
        for (int y = start; y <= end; y++) {
            BlockPos lower = new BlockPos(x, y, z);
            BlockState support = level.getBlockState(lower.below());
            if (!isSolidSupport(level, lower.below(), support)) {
                continue;
            }
            if (isReplaceable(level.getBlockState(lower))
                    && isReplaceable(level.getBlockState(lower.above()))
                    && isReplaceable(level.getBlockState(lower.above(2)))) {
                return lower;
            }
        }
        return null;
    }

    private static boolean sameDoorFootprint(ServerLevel oldLevel, BlockPos oldPos,
                                               ServerLevel newLevel, BlockPos newPos) {
        if (!oldLevel.dimension().equals(newLevel.dimension())) {
            return false;
        }
        return oldPos.equals(newPos) || oldPos.equals(newPos.above())
                || oldPos.above().equals(newPos) || oldPos.above().equals(newPos.above());
    }

    private static boolean isSolidSupport(ServerLevel level, BlockPos pos, BlockState state) {
        return !state.isAir() && state.getFluidState().isEmpty()
                && !state.is(BlockTags.LEAVES) && !state.getCollisionShape(level, pos).isEmpty();
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
        return state.getFluidState().isEmpty()
                && (state.isAir() || state.getMaterial().isReplaceable());
    }
}
