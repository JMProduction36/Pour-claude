package fr.jmproduction.pocketdoor.portal;

import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * Faithful port of qouteall.imm_ptl.core.ClientWorldLoader (Immersive Portals 1.19.2, v2.3.2).
 * Holds client-side secondary worlds (one per dimension key) so we can switch without
 * showing the Mojang loading screen.
 */
public final class ClientWorldLoader {
    private static final Object2ObjectMap<ResourceKey<Level>, ClientLevel> clientWorldMap = new Object2ObjectOpenHashMap<>();
    private static final Minecraft mc = Minecraft.getInstance();

    private ClientWorldLoader() {}

    public static void setWorld(ResourceKey<Level> key, ClientLevel world) {
        clientWorldMap.put(key, world);
    }

    public static ClientLevel getWorld(ResourceKey<Level> key) {
        return clientWorldMap.get(key);
    }

    public static boolean isInitialized(ResourceKey<Level> key) {
        return clientWorldMap.containsKey(key);
    }

    public static void removeWorld(ResourceKey<Level> key) {
        ClientLevel w = clientWorldMap.remove(key);
        // 1.19.2 (Mojang mappings): LevelRenderer#levelChanged() does not exist;
        // the canonical hook to retarget the renderer onto a different ClientLevel
        // is LevelRenderer#setLevel(ClientLevel). Re-bind the renderer to the
        // currently active world so any chunk cache references to the evicted
        // level are dropped.
        if (w != null && mc.level != null) mc.levelRenderer.setLevel(mc.level);
    }

    public static Iterable<ClientLevel> getClientWorlds() {
        return clientWorldMap.values();
    }

    public static void cleanUp() {
        clientWorldMap.clear();
    }
}
