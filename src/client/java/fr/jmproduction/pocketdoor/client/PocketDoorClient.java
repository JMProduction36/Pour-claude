package fr.jmproduction.pocketdoor.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import fr.jmproduction.pocketdoor.block.ModBlocks;
import fr.jmproduction.pocketdoor.dimension.PocketDimensions;
import fr.jmproduction.pocketdoor.dimension.PocketOfficeGenerator;
import fr.jmproduction.pocketdoor.network.ModNetworking;
import qouteall.imm_ptl.core.ClientWorldLoader;
import qouteall.imm_ptl.core.portal.Portal;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.Blocks;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.object.builder.v1.client.model.FabricModelPredicateProviderRegistry;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.client.renderer.item.ItemPropertyFunction;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.lwjgl.glfw.GLFW;

/** Client-side controls plus the deliberately broken clock/compass while disconnected from the exterior time. */
public class PocketDoorClient implements ClientModInitializer {
    private static final String KEY_CATEGORY = "key.categories.pocketdoor";
    private static final ResourceLocation CLOCK_TIME = new ResourceLocation("minecraft", "time");
    private static final ResourceLocation COMPASS_ANGLE = new ResourceLocation("minecraft", "angle");

    public static final KeyMapping TOGGLE_PORTAL = KeyBindingHelper.registerKeyBinding(
            new KeyMapping(
                    "key.pocketdoor.toggle_portal",
                    InputConstants.Type.KEYSYM,
                    GLFW.GLFW_KEY_P,
                    KEY_CATEGORY
            )
    );

    public static final KeyMapping REMOVE_PORTAL = KeyBindingHelper.registerKeyBinding(
            new KeyMapping(
                    "key.pocketdoor.remove_portal",
                    InputConstants.Type.KEYSYM,
                    GLFW.GLFW_KEY_O,
                    KEY_CATEGORY
            )
    );

    private static boolean pocketDoorWasObservedOpen = false;
    private static boolean teleportMapUnlocked = false;

    @Override
    public void onInitializeClient() {
        PocketMapState.load(Minecraft.getInstance());
        registerMapInteraction();
        registerBrokenTimekeepers();
        // Portal rendering is now delegated to Immersive Portals Core.

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (TOGGLE_PORTAL.consumeClick()) {
                if (client.player == null || client.level == null
                        || client.level.dimension().equals(PocketDimensions.POCKET_OFFICE)) {
                    continue;
                }

                HitResult hit = client.hitResult;
                if (!(hit instanceof BlockHitResult blockHit)) {
                    continue;
                }

                ClientPlayNetworkingBridge.sendToggle(blockHit);
            }

            while (REMOVE_PORTAL.consumeClick()) {
                if (client.player == null || client.level == null
                        || client.level.dimension().equals(PocketDimensions.POCKET_OFFICE)) {
                    continue;
                }

                ClientPlayNetworkingBridge.sendRemove();
            }

            PocketMapState.tick(client);

            // The teleport map becomes available from the pocket-side door only after the
            // player has seen that door open and then closed it once. This replaces the
            // old dedicated cartography table.
            if (client.level != null && client.level.dimension().equals(PocketDimensions.POCKET_OFFICE)) {
                boolean open = client.level.getBlockState(PocketOfficeGenerator.POCKET_DOOR_LOWER)
                        .getValue(DoorBlock.OPEN);
                if (open) {
                    pocketDoorWasObservedOpen = true;
                } else if (pocketDoorWasObservedOpen) {
                    teleportMapUnlocked = true;
                }
            }

            // Pre-create Immersive Portals' secondary ClientLevel as soon as the
            // linked render portal arrives near the player. This avoids creating the
            // destination world renderer on the exact frame the player first looks through.
            if (client.player != null && client.level != null) {
                boolean nearPocketPortal = !client.level.dimension().equals(PocketDimensions.POCKET_OFFICE)
                        && !client.level.getEntitiesOfClass(Portal.class,
                        client.player.getBoundingBox().inflate(16.0D),
                        portal -> PocketDimensions.POCKET_OFFICE.equals(portal.getDestDim())).isEmpty();
                if (nearPocketPortal) {
                    ClientWorldLoader.getWorld(PocketDimensions.POCKET_OFFICE);
                }
            }
        });
    }

    private static void registerMapInteraction() {
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (!level.isClientSide || !level.dimension().equals(PocketDimensions.POCKET_OFFICE)) {
                return InteractionResult.PASS;
            }

            if (!hit.getBlockPos().equals(PocketOfficeGenerator.POCKET_DOOR_LOWER)
                    && !hit.getBlockPos().equals(PocketOfficeGenerator.POCKET_DOOR_LOWER.above())) {
                return InteractionResult.PASS;
            }

            if (!teleportMapUnlocked) {
                return InteractionResult.PASS;
            }

            if (!level.getBlockState(PocketOfficeGenerator.POCKET_DOOR_LOWER).getValue(DoorBlock.OPEN)) {
                Minecraft.getInstance().setScreen(new PocketTeleportMapScreen());
                return InteractionResult.SUCCESS;
            }

            return InteractionResult.PASS;
        });
    }

    private static void registerBrokenTimekeepers() {
        // With official Mojang mappings on Minecraft 1.19.2, the vanilla
        // predicate API lives in net.minecraft.client.renderer.item.
        // Fabric's registry accepts the same predicate function through its
        // access-widened registration path.
        ItemPropertyFunction originalClock = ItemProperties.getProperty(Items.CLOCK, CLOCK_TIME);
        ItemPropertyFunction originalCompass = ItemProperties.getProperty(Items.COMPASS, COMPASS_ANGLE);

        FabricModelPredicateProviderRegistry.register(Items.CLOCK, CLOCK_TIME,
                (stack, world, entity, seed) -> {
                    if (PocketTimekeeper.isDisconnected(world)) {
                        long t = world == null ? 0L : world.getGameTime();
                        return (float) ((Math.sin(t * 0.37D + seed * 0.013D) + 1.0D) * 0.5D);
                    }
                    return originalClock == null ? 0.0F : originalClock.call(stack, world, entity, seed);
                });

        FabricModelPredicateProviderRegistry.register(Items.COMPASS, COMPASS_ANGLE,
                (stack, world, entity, seed) -> {
                    if (PocketTimekeeper.isDisconnected(world)) {
                        long t = world == null ? 0L : world.getGameTime();
                        double value = (t * 0.071D + seed * 0.00031D) % 1.0D;
                        if (value < 0) value += 1.0D;
                        return (float) value;
                    }
                    return originalCompass == null ? 0.0F : originalCompass.call(stack, world, entity, seed);
                });
    }

    private static final class PocketTimekeeper {
        private static boolean isDisconnected(net.minecraft.client.multiplayer.ClientLevel world) {
            if (world == null || !world.dimension().equals(PocketDimensions.POCKET_OFFICE)) {
                return false;
            }

            if (!world.getBlockState(PocketOfficeGenerator.POCKET_DOOR_LOWER).is(ModBlocks.POCKET_DOOR)) {
                return true;
            }

            return !world.getBlockState(PocketOfficeGenerator.POCKET_DOOR_LOWER).getValue(DoorBlock.OPEN);
        }
    }

    /** Small indirection keeps the key handler readable and isolates the networking calls. */
    private static final class ClientPlayNetworkingBridge {
        private static void sendToggle(BlockHitResult hit) {
            net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                    ModNetworking.TOGGLE_DOOR,
                    ModNetworking.createTogglePacket(hit.getBlockPos(), hit.getDirection())
            );
        }

        private static void sendRemove() {
            net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                    ModNetworking.REMOVE_DOOR,
                    ModNetworking.createRemovePacket()
            );
        }
    }
}
