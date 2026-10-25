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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.AABB;

public final class PocketDoorEvents {

    private PocketDoorEvents() {
    }

    public static void initialize() {
        ServerWorldEvents.LOAD.register((server, world) -> {
            if (world.dimension().equals(
                    PocketDimensions.POCKET_OFFICE
            )) {
                PocketOfficeGenerator.ensureGenerated(world);

                // Restore the physical door state when the dimension loads.
                PocketDoorPortal.syncPocketDoorState(world);
            }
        });

        ServerTickEvents.END_WORLD_TICK.register(
                PocketDoorEvents::tickWorld
        );

        PlayerBlockBreakEvents.BEFORE.register(
                (world, player, pos, state, blockEntity) -> {
                    if (!(world instanceof ServerLevel level)) {
                        return true;
                    }

                    if (state.is(ModBlocks.POCKET_DOOR)) {
                        return false;
                    }

                    PocketDoorSavedData data =
                            PocketDoorSavedData.get(level);

                    // Protect the block supporting the exterior door.
                    if (data.hasDoor()
                            && level.dimension().location().toString()
                            .equals(data.getDimensionId())
                            && pos.equals(data.getDoorPos().below())) {
                        return false;
                    }

                    if (level.dimension().equals(
                            PocketDimensions.POCKET_OFFICE
                    )) {
                        // Protect the permanent office shell.
                        if (PocketOfficeGenerator.isStructural(
                                level,
                                pos
                        )) {
                            return false;
                        }
                    }

                    return true;
                }
        );

        // Block placement remains allowed inside the pocket office.
    }

    private static void tickWorld(ServerLevel level) {
        if (level.dimension().equals(
                PocketDimensions.POCKET_OFFICE
        )) {
            // No hunger drain in the pocket office.
            for (var player : level.players()) {
                player.getFoodData().setFoodLevel(20);
                player.getFoodData().setSaturation(20.0F);
                player.getFoodData().setExhaustion(0.0F);
            }

            /*
             * Clean up old invisible seats once per second.
             * Search only inside the office instead of checking every
             * entity in the whole dimension on every tick.
             */
            if (level.getGameTime() % 20L == 0L) {
                AABB officeBounds = new AABB(
                        PocketOfficeGenerator.MIN_X,
                        PocketOfficeGenerator.FLOOR_Y,
                        PocketOfficeGenerator.MIN_Z,
                        PocketOfficeGenerator.MAX_X + 1.0D,
                        PocketOfficeGenerator.CEILING_Y + 1.0D,
                        PocketOfficeGenerator.MAX_Z + 1.0D
                );

                level.getEntitiesOfClass(
                        ArmorStand.class,
                        officeBounds,
                        stand -> stand.getTags().contains(
                                "pocketdoor_seat"
                        ) && stand.getPassengers().isEmpty()
                ).forEach(ArmorStand::discard);
            }
        }

        // Immersive Portals handles seamless dimension transfer.
        // PocketDoor manages the linked door and portal lifecycle.

        ServerLevel overworld = level.getServer().overworld();
        PocketDoorSavedData data = PocketDoorSavedData.get(overworld);

        if (!data.hasDoor()) {
            return;
        }

        ServerLevel doorLevel = resolveDoorLevel(overworld, data);

        if (doorLevel == null) {
            return;
        }

        if (PocketDoorPortal.isDoorOpen(
                doorLevel,
                data.getDoorPos()
        )) {
            ServerLevel pocket = level.getServer().getLevel(
                    PocketDimensions.POCKET_OFFICE
            );

            if (pocket != null) {
                PocketOfficeGenerator.ensureGenerated(pocket);

                ImmersivePortalBridge.ensureOpen(
                        doorLevel,
                        data.getDoorPos(),
                        doorLevel.getBlockState(data.getDoorPos())
                                .getValue(
                                        net.minecraft.world.level.block.DoorBlock.FACING
                                ),
                        pocket
                );
            }

            PocketDoorPortal.syncPocketTime(overworld);
        }
    }

    private static ServerLevel resolveDoorLevel(
            ServerLevel fallback,
            PocketDoorSavedData data
    ) {
        var id = net.minecraft.resources.ResourceLocation.tryParse(
                data.getDimensionId()
        );

        if (id == null) {
            return null;
        }

        var key = net.minecraft.resources.ResourceKey.create(
                net.minecraft.core.Registry.DIMENSION_REGISTRY,
                id
        );

        return fallback.getServer().getLevel(key);
    }
}