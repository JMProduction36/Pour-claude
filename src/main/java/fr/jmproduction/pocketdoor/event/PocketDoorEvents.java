package fr.jmproduction.pocketdoor.event;

import fr.jmproduction.pocketdoor.block.ModBlocks;
import fr.jmproduction.pocketdoor.data.PocketDoorSavedData;
import fr.jmproduction.pocketdoor.dimension.PocketDimensions;
import fr.jmproduction.pocketdoor.dimension.PocketDoorPortal;
import fr.jmproduction.pocketdoor.dimension.ImmersivePortalBridge;
import fr.jmproduction.pocketdoor.dimension.PocketOfficeGenerator;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.Level;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

public final class PocketDoorEvents {
    private PocketDoorEvents() {
    }

    public static void initialize() {
        ServerWorldEvents.LOAD.register((server, world) -> {
            if (world.dimension().equals(PocketDimensions.POCKET_OFFICE)) {
                PocketOfficeGenerator.ensureGenerated(world);
                // When the pocket dimension is loaded, restore the physical door state
                // instead of leaving an old open/closed state from a previous session.
                PocketDoorPortal.syncPocketDoorState(world);
            }
        });

        ServerTickEvents.END_WORLD_TICK.register(PocketDoorEvents::tickWorld);

        // Exécuté AUSSI côté client (événement partagé) : le joueur ne peut même pas
        // commencer à casser la porte. Sans ça, en créatif, le client la supprime
        // localement avant que le serveur ne refuse -> état désynchronisé -> crash.
        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) ->
                isProtectedBlock(world, pos) ? InteractionResult.FAIL : InteractionResult.PASS);

        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
            if (!(world instanceof ServerLevel level)) {
                return true;
            }

            if (state.is(ModBlocks.POCKET_DOOR)) {
                return false; // O is the intentional removal key.
            }

            if (isProtectedBlock(level, pos)) {
                return false;
            }

            PocketDoorSavedData data = PocketDoorSavedData.get(level);

            // The exterior support block is protected so the door cannot collapse under it.
            if (data.hasDoor()
                    && level.dimension().location().toString().equals(data.getDimensionId())
                    && pos.equals(data.getDoorPos().below())) {
                return false;
            }

            if (level.dimension().equals(PocketDimensions.POCKET_OFFICE)) {
                // Protect only the permanent shell of the office: outer walls, floor,
                // ceiling and the glass panes embedded in those boundary blocks.
                // Everything that lives inside the room remains normally breakable,
                // including in Creative mode.
                if (PocketOfficeGenerator.isStructural(level, pos)) {
                    return false;
                }
            }

            return true;
        });

        // Block placement is allowed inside the pocket office.
        // The protection above only blocks destruction of the permanent shell;
        // interior space remains a normal buildable area.
    }

    /** Porte (les deux moitiés) et blocs invisibles derrière la porte du bureau. */
    private static boolean isProtectedBlock(Level world, BlockPos pos) {
        if (world.getBlockState(pos).is(ModBlocks.POCKET_DOOR)) {
            return true;
        }
        return world.dimension().equals(PocketDimensions.POCKET_OFFICE)
                && PocketOfficeGenerator.isDoorBackstop(pos);
    }

    /**
     * Filet de sécurité : si la porte extérieure a quand même disparu (commande, autre mod...),
     * on retire les portails Immersive Portals et on oublie la porte au lieu de laisser un
     * portail orphelin (ou un crash à la traversée).
     */
    private static void validateDoor(ServerLevel overworld, PocketDoorSavedData data) {
        ServerLevel doorLevel = resolveDoorLevel(overworld, data);
        if (doorLevel == null) {
            return;
        }
        BlockPos pos = data.getDoorPos();
        if (!doorLevel.hasChunkAt(pos)) {
            return; // chunk non chargé : on ne force rien
        }
        boolean intact = doorLevel.getBlockState(pos).is(ModBlocks.POCKET_DOOR)
                && doorLevel.getBlockState(pos.above()).is(ModBlocks.POCKET_DOOR);
        if (intact) {
            return;
        }
        ImmersivePortalBridge.cleanupAll(doorLevel, pos);
        data.clearDoor();
        ServerLevel pocket = overworld.getServer().getLevel(PocketDimensions.POCKET_OFFICE);
        if (pocket != null) {
            PocketDoorPortal.setOpen(pocket, PocketOfficeGenerator.POCKET_DOOR_LOWER, false);
        }
        fr.jmproduction.pocketdoor.PocketDoorMod.LOGGER.warn(
                "Pocket Door missing at {} in {} : portals removed and door unregistered.", pos, doorLevel.dimension().location());
    }

    private static void tickWorld(ServerLevel level) {
        if (level.dimension().equals(PocketDimensions.POCKET_OFFICE)) {
            // No hunger drain in the pocket office.
            for (var player : level.players()) {
                player.getFoodData().setFoodLevel(20);
                player.getFoodData().setSaturation(20.0F);
                player.getFoodData().setExhaustion(0.0F);
            }

            // Clean up invisible chair seats when they have no passenger left.
            level.getAllEntities().forEach(entity -> {
                if (entity instanceof net.minecraft.world.entity.decoration.ArmorStand stand
                        && stand.getTags().contains("pocketdoor_seat")
                        && stand.getPassengers().isEmpty()) {
                    stand.discard();
                }
            });
        }

        // Immersive Portals handles the actual seamless cross-dimension teleport.
        // PocketDoor only manages the linked door state and portal lifecycle.

        ServerLevel overworld = level.getServer().overworld();
        PocketDoorSavedData data = PocketDoorSavedData.get(overworld);
        if (!data.hasDoor()) {
            return;
        }

        ServerLevel doorLevel = resolveDoorLevel(overworld, data);
        if (doorLevel == null) {
            return;
        }

        if (level == overworld && level.getGameTime() % 20L == 0L) {
            validateDoor(overworld, data);
            if (!data.hasDoor()) {
                return;
            }
        }

        if (PocketDoorPortal.isDoorOpen(doorLevel, data.getDoorPos())) {
            ServerLevel pocket = level.getServer().getLevel(PocketDimensions.POCKET_OFFICE);
            if (pocket != null) {
                PocketOfficeGenerator.ensureGenerated(pocket);
                ImmersivePortalBridge.ensureOpen(doorLevel, data.getDoorPos(),
                        doorLevel.getBlockState(data.getDoorPos()).getValue(net.minecraft.world.level.block.DoorBlock.FACING), pocket);
            }
            PocketDoorPortal.syncPocketTime(overworld);
        }
    }

    private static ServerLevel resolveDoorLevel(ServerLevel fallback, PocketDoorSavedData data) {
        var id = net.minecraft.resources.ResourceLocation.tryParse(data.getDimensionId());
        if (id == null) return null;
        var key = net.minecraft.resources.ResourceKey.create(net.minecraft.core.Registry.DIMENSION_REGISTRY, id);
        return fallback.getServer().getLevel(key);
    }
}
