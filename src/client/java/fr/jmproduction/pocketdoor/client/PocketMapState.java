package fr.jmproduction.pocketdoor.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;

/** Client-side explored terrain cache for the Pocket Door teleport map. */
public final class PocketMapState {
    private static final Map<Long, int[]> CHUNKS = new HashMap<>();
    private static final int SAMPLE_RADIUS_CHUNKS = 4;
    private static long lastCaptureTick = Long.MIN_VALUE;
    private static long lastSaveTick = Long.MIN_VALUE;
    private static boolean loaded;
    private static String loadedWorldKey;
    private static double lastOverworldX;
    private static double lastOverworldZ;

    private PocketMapState() {}

    public static void tick(Minecraft client) {
        ClientLevel level = client.level;
        if (level == null || client.player == null) return;

        String worldKey = worldKey(client);
        if (!worldKey.equals(loadedWorldKey)) {
            load(client);
        }

        // JourneyMap-like discovery: as the player explores the Overworld, cache the
        // terrain of nearby loaded chunks. Unexplored chunks remain black on the map.
        if (level.dimension().equals(net.minecraft.world.level.Level.OVERWORLD)) {
            lastOverworldX = client.player.getX();
            lastOverworldZ = client.player.getZ();

            long tick = level.getGameTime();
            if (lastCaptureTick == Long.MIN_VALUE || tick - lastCaptureTick >= 10) {
                captureAround(level, client.player.blockPosition());
                lastCaptureTick = tick;
            }
            if ((lastSaveTick == Long.MIN_VALUE || tick - lastSaveTick >= 200) && !CHUNKS.isEmpty()) {
                save(client);
                lastSaveTick = tick;
            }
        }
    }

    public static double getLastOverworldX() { return lastOverworldX; }
    public static double getLastOverworldZ() { return lastOverworldZ; }

    public static boolean hasDiscovered(int x, int z) {
        long key = ChunkPos.asLong(x >> 4, z >> 4);
        return CHUNKS.containsKey(key);
    }

    public static int getColor(int x, int z) {
        int[] pixels = CHUNKS.get(ChunkPos.asLong(x >> 4, z >> 4));
        if (pixels == null) return 0xFF000000;
        int localX = x & 15;
        int localZ = z & 15;
        return pixels[localZ * 16 + localX];
    }

    private static void captureAround(ClientLevel level, BlockPos center) {
        int baseCx = center.getX() >> 4;
        int baseCz = center.getZ() >> 4;
        for (int cx = baseCx - SAMPLE_RADIUS_CHUNKS; cx <= baseCx + SAMPLE_RADIUS_CHUNKS; cx++) {
            for (int cz = baseCz - SAMPLE_RADIUS_CHUNKS; cz <= baseCz + SAMPLE_RADIUS_CHUNKS; cz++) {
                LevelChunk chunk;
                try {
                    chunk = level.getChunkSource().getChunk(cx, cz, false);
                } catch (Throwable ignored) {
                    continue;
                }
                if (chunk == null) continue;
                // A chunk is captured once when first discovered. This makes the map
                // progressively grow as the player explores without constantly
                // rebuilding every visible chunk every few ticks.
                if (!CHUNKS.containsKey(chunk.getPos().toLong())) {
                    captureChunk(level, chunk);
                }
            }
        }
    }

    private static void captureChunk(ClientLevel level, LevelChunk chunk) {
        int[] pixels = new int[256];
        int startX = chunk.getPos().getMinBlockX();
        int startZ = chunk.getPos().getMinBlockZ();
        for (int dz = 0; dz < 16; dz++) {
            for (int dx = 0; dx < 16; dx++) {
                int x = startX + dx;
                int z = startZ + dz;
                int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
                BlockPos pos = new BlockPos(x, Math.max(level.getMinBuildHeight(), y - 1), z);
                BlockState state = level.getBlockState(pos);
                int color;
                try {
                    color = Minecraft.getInstance().getBlockColors().getColor(state, level, pos);
                } catch (Throwable ignored) {
                    color = -1;
                }
                if (color < 0) {
                    color = fallbackColor(state);
                }
                // Slightly darken the top-down sample so the map reads as a terrain map.
                int r = ((color >> 16) & 0xFF) * 3 / 4;
                int g = ((color >> 8) & 0xFF) * 3 / 4;
                int b = (color & 0xFF) * 3 / 4;
                pixels[dz * 16 + dx] = 0xFF000000 | (r << 16) | (g << 8) | b;
            }
        }
        CHUNKS.put(chunk.getPos().toLong(), pixels);
    }

    private static int fallbackColor(BlockState state) {
        if (state.is(Blocks.WATER) || state.getFluidState().is(net.minecraft.world.level.material.Fluids.WATER)) return 0xFF3F76E4;
        if (state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.GRASS) || state.is(Blocks.FERN) || state.is(Blocks.TALL_GRASS)) return 0xFF5B8F3A;
        if (state.is(Blocks.SAND) || state.is(Blocks.SANDSTONE)) return 0xFFE4D09B;
        if (state.is(Blocks.SNOW) || state.is(Blocks.SNOW_BLOCK)) return 0xFFF0F0F0;
        if (state.is(Blocks.STONE) || state.is(Blocks.ANDESITE) || state.is(Blocks.DIORITE) || state.is(Blocks.GRANITE)) return 0xFF777777;
        if (state.is(Blocks.DIRT) || state.is(Blocks.COARSE_DIRT)) return 0xFF8A5A38;
        if (state.is(Blocks.ICE) || state.is(Blocks.PACKED_ICE)) return 0xFF9DD8F0;
        return 0xFF6A6A6A;
    }

    public static void load(Minecraft client) {
        String worldKey = worldKey(client);
        if (loaded && worldKey.equals(loadedWorldKey)) return;
        CHUNKS.clear();
        lastCaptureTick = Long.MIN_VALUE;
        lastSaveTick = Long.MIN_VALUE;
        lastOverworldX = 0.0D;
        lastOverworldZ = 0.0D;
        loadedWorldKey = worldKey;
        loaded = true;
        File file = file(client);
        if (!file.isFile()) return;
        try {
            CompoundTag root = NbtIo.readCompressed(file);
            lastOverworldX = root.getDouble("LastX");
            lastOverworldZ = root.getDouble("LastZ");
            ListTag list = root.getList("Chunks", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag c = list.getCompound(i);
                int[] colors = c.getIntArray("Colors");
                if (colors.length == 256) CHUNKS.put(c.getLong("Key"), colors);
            }
        } catch (IOException ignored) {
            // A corrupt cache simply behaves like a fresh unexplored map.
            CHUNKS.clear();
        }
    }

    public static void save(Minecraft client) {
        CompoundTag root = new CompoundTag();
        root.putDouble("LastX", lastOverworldX);
        root.putDouble("LastZ", lastOverworldZ);
        ListTag list = new ListTag();
        for (Map.Entry<Long, int[]> entry : CHUNKS.entrySet()) {
            CompoundTag c = new CompoundTag();
            c.putLong("Key", entry.getKey());
            c.putIntArray("Colors", entry.getValue());
            list.add(c);
        }
        root.put("Chunks", list);
        try {
            File file = file(client);
            File parent = file.getParentFile();
            if (!parent.exists()) parent.mkdirs();
            NbtIo.writeCompressed(root, file);
        } catch (IOException ignored) {
        }
    }

    private static String worldKey(Minecraft client) {
        if (client.getSingleplayerServer() != null) {
            Path worldPath = client.getSingleplayerServer().getWorldPath(
                    net.minecraft.world.level.storage.LevelResource.ROOT);
            return "world:" + client.gameDirectory.toPath().toAbsolutePath().relativize(worldPath.toAbsolutePath());
        }
        if (client.getCurrentServer() != null) {
            return "server:" + client.getCurrentServer().ip;
        }
        return "unknown";
    }

    private static File file(Minecraft client) {
        String key = worldKey(client);
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8));
            StringBuilder name = new StringBuilder(64);
            for (byte value : hash) {
                name.append(Character.forDigit((value >> 4) & 0xF, 16));
                name.append(Character.forDigit(value & 0xF, 16));
            }
            return new File(client.gameDirectory, "config/pocketdoor_maps/" + name + ".dat");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
