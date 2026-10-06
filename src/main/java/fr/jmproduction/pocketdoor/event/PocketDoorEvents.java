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
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
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

        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
            if (!(world instanceof ServerLevel level)) {
                return true;
            }

            if (state.is(ModBlocks.POCKET_DOOR)) {
                return false; // O is the intentional removal key.
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
