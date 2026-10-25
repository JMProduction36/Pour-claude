package fr.jmproduction.pocketdoor.teleport;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Faithful port of qouteall.imm_ptl.core.teleportation.ClientTeleportationManager
 * (Immersive Portals 1.19.2, v2.3.2). Used to swap the active client world WITHOUT
 * showing the Mojang loading screen. The destination ClientLevel must already live
 * in ClientWorldLoader.
 */
public final class ClientTeleportationManager {
    private ClientTeleportationManager() {}

    public static void changeClientWorld(ResourceKey<Level> toLevel, Vec3 newPos, float yaw, float pitch) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel target = fr.jmproduction.pocketdoor.portal.ClientWorldLoader.getWorld(toLevel);
        if (target == null) return;
        mc.level = target;
        LocalPlayer p = mc.player;
        if (p != null) {
            p.moveTo(newPos.x, newPos.y, newPos.z, yaw, pitch);
            // 1.19.2 (Mojang mappings): LocalPlayer.setLevel(ClientLevel) is not public/inherited,
            // but Entity#level is a public non-final field, so we write it directly.
            p.level = target;
        }
        // 1.19.2 (Mojang mappings): LevelRenderer#levelChanged() does not exist; the
        // canonical hook is LevelRenderer#setLevel(ClientLevel).
        mc.levelRenderer.setLevel(target);
    }

    public static void teleportPlayerInSameWorld(Vec3 pos, float yaw, float pitch) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p != null) p.moveTo(pos.x, pos.y, pos.z, yaw, pitch);
    }

    /**
     * Per-tick client-side hook called from {@link fr.jmproduction.pocketdoor.client.PocketDoorClient}.
     * Currently a no-op placeholder; reserved for future cross-dimension smoothing
     * (camera, sound, fog) that doesn't belong to a single renderer.
     */
    public static void tick(Minecraft client) {
        if (client == null || client.level == null || client.player == null) return;
        // Reserved: future interpolation / portal transition logic.
    }
}
