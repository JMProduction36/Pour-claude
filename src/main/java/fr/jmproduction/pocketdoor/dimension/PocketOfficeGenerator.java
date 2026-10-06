package fr.jmproduction.pocketdoor.dimension;

import fr.jmproduction.pocketdoor.PocketDoorMod;
import fr.jmproduction.pocketdoor.block.ModBlocks;
import fr.jmproduction.pocketdoor.block.TypewriterBlock;
import fr.jmproduction.pocketdoor.data.PocketDoorSavedData;
import fr.jmproduction.pocketdoor.data.PocketOfficeSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.decoration.Painting;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChainBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.block.DoorBlock;

import java.util.Optional;

/** One-time deterministic/procedural generation of the 17x17x10 pocket office. */
public final class PocketOfficeGenerator {
    public static final int MIN_X = 0;
    public static final int MAX_X = 16;
    public static final int MIN_Z = 0;
    public static final int MAX_Z = 16;
    public static final int FLOOR_Y = 64;
    public static final int CEILING_Y = 73;

    /** Pocket-side door in the center of the south wall. */
    public static final BlockPos POCKET_DOOR_LOWER = new BlockPos(8, FLOOR_Y + 1, 16);

    private PocketOfficeGenerator() {
    }

    public static boolean isInsideOffice(ServerLevel level, BlockPos pos) {
        return level.dimension().equals(PocketDimensions.POCKET_OFFICE)
                && pos.getX() >= MIN_X && pos.getX() <= MAX_X
                && pos.getZ() >= MIN_Z && pos.getZ() <= MAX_Z
                && pos.getY() >= FLOOR_Y && pos.getY() <= CEILING_Y;
    }

    public static boolean isStructural(ServerLevel level, BlockPos pos) {
        if (!isInsideOffice(level, pos)) {
            return false;
        }
        // Anything on the outer shell boundary is structural: walls, ceiling, floor,
        // and the glass panes embedded in those walls.
        return pos.getY() == FLOOR_Y || pos.getY() == CEILING_Y
                || pos.getX() == MIN_X || pos.getX() == MAX_X
                || pos.getZ() == MIN_Z || pos.getZ() == MAX_Z;
    }

    /**
     * Generates only once. All decorations are seeded from the world seed, so a given
     * world gets one stable arrangement rather than a new arrangement on every chunk load.
     */
    public static void ensureGenerated(ServerLevel level) {
        if (!level.dimension().equals(PocketDimensions.POCKET_OFFICE)) {
            return;
        }

        PocketOfficeSavedData data = PocketOfficeSavedData.get(level);
        if (data.isGenerated()) {
            ensurePocketDoor(level);
            return;
        }

        forceOfficeChunks(level);
        generateShell(level);
        generateWindows(level);
        generateFurniture(level);
        placeDeskLayout(level);
        generateWallDecorations(level);
        ensurePocketDoor(level);

        data.markGenerated();
        PocketDoorMod.LOGGER.info("Generated the 17x17x10 Pocket Office in {}.", level.dimension().location());
    }

    private static void forceOfficeChunks(ServerLevel level) {
        for (int cx = 0; cx <= 1; cx++) {
            for (int cz = 0; cz <= 1; cz++) {
                level.setChunkForced(cx, cz, true);
                level.getChunk(cx, cz);
            }
        }
    }

    private static void generateShell(ServerLevel level) {
        BlockState wall = ModBlocks.POCKET_OAK_PLANKS.defaultBlockState();

        for (int x = MIN_X; x <= MAX_X; x++) {
            for (int z = MIN_Z; z <= MAX_Z; z++) {
                place(level, new BlockPos(x, FLOOR_Y, z), wall);
                place(level, new BlockPos(x, CEILING_Y, z), wall);
            }
        }

        for (int y = FLOOR_Y + 1; y < CEILING_Y; y++) {
            for (int x = MIN_X; x <= MAX_X; x++) {
                place(level, new BlockPos(x, y, MIN_Z), wall);
                place(level, new BlockPos(x, y, MAX_Z), wall);
            }
            for (int z = MIN_Z + 1; z < MAX_Z; z++) {
                place(level, new BlockPos(MIN_X, y, z), wall);
                place(level, new BlockPos(MAX_X, y, z), wall);
            }
        }

        // South entrance: the mod door replaces this opening.
        for (int y = FLOOR_Y + 1; y <= FLOOR_Y + 2; y++) {
            place(level, new BlockPos(8, y, MAX_Z), Blocks.AIR.defaultBlockState());
        }
    }

    private static void generateWindows(ServerLevel level) {
        BlockState glass = Blocks.GLASS.defaultBlockState();

        // North wall: two 3x3 windows.
        for (int x = 3; x <= 5; x++) {
            for (int y = 68; y <= 70; y++) {
                place(level, new BlockPos(x, y, 0), glass);
            }
        }
        for (int x = 11; x <= 13; x++) {
            for (int y = 68; y <= 70; y++) {
                place(level, new BlockPos(x, y, 0), glass);
            }
        }

        // West and east walls: matching 3x3 windows.
        for (int z = 5; z <= 7; z++) {
            for (int y = 68; y <= 70; y++) {
                place(level, new BlockPos(0, y, z), glass);
                place(level, new BlockPos(16, y, z), glass);
            }
        }
    }

    private static void generateFurniture(ServerLevel level) {
        BlockState plank = Blocks.CHAIN.defaultBlockState();
        BlockState stair = Blocks.OAK_STAIRS.defaultBlockState()
                .setValue(StairBlock.HALF, Half.TOP);
        BlockState lantern = Blocks.LANTERN.defaultBlockState();

        for (int x = 5; x <= 11; x++) {
            place(level, new BlockPos(x, 65, 4), plank);
            place(level, new BlockPos(x, 65, 5), plank);
            place(level, new BlockPos(x, 65, 6), plank);
        }

        place(level, new BlockPos(5, 66, 4), stair);
        place(level, new BlockPos(6, 66, 4), stair);
        place(level, new BlockPos(7, 66, 4), stair);
        place(level, new BlockPos(8, 66, 4), stair);
        place(level, new BlockPos(9, 66, 4), stair);
        place(level, new BlockPos(10, 66, 4), stair);
        place(level, new BlockPos(11, 66, 4), stair);
        place(level, new BlockPos(5, 67, 4), lantern.setValue(LanternBlock.HANGING, true));

        place(level, new BlockPos(5, 66, 5), stair);
        place(level, new BlockPos(6, 66, 5), plank);
        place(level, new BlockPos(7, 66, 5), plank);
        place(level, new BlockPos(8, 66, 5), plank);
        place(level, new BlockPos(9, 66, 5), plank);
        place(level, new BlockPos(10, 66, 5), plank);
        place(level, new BlockPos(11, 66, 5), stair);

        place(level, new BlockPos(5, 66, 6), stair);
        place(level, new BlockPos(6, 66, 6), plank);
        place(level, new BlockPos(7, 66, 6), plank);
        place(level, new BlockPos(8, 66, 6), stair);
        place(level, new BlockPos(9, 66, 6), plank);
        place(level, new BlockPos(10, 66, 6), plank);
        place(level, new BlockPos(11, 66, 6), stair);

        BlockPos typewriterPos = new BlockPos(8, 67, 6);
        place(level, typewriterPos,
                ModBlocks.TYPEWRITER.defaultBlockState().setValue(TypewriterBlock.FACING, Direction.NORTH));

        for (int x = 6; x <= 10; x++) {
            place(level, new BlockPos(x, 65, 8), Blocks.OAK_SLAB.defaultBlockState()
                    .setValue(SlabBlock.TYPE, SlabType.TOP));
        }
        place(level, new BlockPos(8, 65, 8), ModBlocks.POCKET_CHAIR.defaultBlockState());
    }

    private static void placeDeskLayout(ServerLevel level) {
        BlockState plank = Blocks.CHAIN.defaultBlockState();
        BlockState stair = Blocks.OAK_STAIRS.defaultBlockState()
                .setValue(StairBlock.HALF, Half.TOP);
        BlockState lantern = Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true);
        for (int x = 5; x <= 11; x++) {
            for (int z = 4; z <= 6; z++) {
                place(level, new BlockPos(x, 65, z), plank);
            }
        }
        for (int x = 5; x <= 11; x++) place(level, new BlockPos(x, 66, 4), stair);
        place(level, new BlockPos(5, 67, 4), lantern);

        place(level, new BlockPos(5, 66, 5), stair);
        place(level, new BlockPos(6, 66, 5), plank);
        place(level, new BlockPos(7, 66, 5), plank);
        place(level, new BlockPos(8, 66, 5), plank);
        place(level, new BlockPos(9, 66, 5), plank);
        place(level, new BlockPos(10, 66, 5), plank);
        place(level, new BlockPos(11, 66, 5), stair);

        place(level, new BlockPos(5, 66, 6), stair);
        place(level, new BlockPos(6, 66, 6), plank);
        place(level, new BlockPos(7, 66, 6), plank);
        place(level, new BlockPos(8, 66, 6), stair);
        place(level, new BlockPos(9, 66, 6), plank);
        place(level, new BlockPos(10, 66, 6), plank);
        place(level, new BlockPos(11, 66, 6), stair);

        place(level, new BlockPos(8, 67, 6),
                ModBlocks.TYPEWRITER.defaultBlockState().setValue(TypewriterBlock.FACING, Direction.NORTH));
    }

    private static void generateWallDecorations(ServerLevel level) {
        RandomSource random = RandomSource.create(level.getSeed() ^ 0x504F434B45544F46L);

        generateChandelier(level);
        placeWallBookshelves(level, random);
        placeWallLadders(level, random);
        placeWallFlowerPots(level, random);
        placeWallPaintings(level, random);
        placeAmbientLanterns(level, random);
    }

    /** Wooden cross-beam chandelier with five hanging lanterns, based on the user's reference. */
    private static void generateChandelier(ServerLevel level) {
        BlockState fence = Blocks.OAK_FENCE.defaultBlockState();
        BlockState chain = Blocks.CHAIN.defaultBlockState();
        BlockState lantern = Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true);

        int y = 71;
        for (int x = 5; x <= 11; x++) {
            place(level, new BlockPos(x, y, 8), fence);
        }
        for (int z = 5; z <= 11; z++) {
            place(level, new BlockPos(8, y, z), fence);
        }

        // Four side arms.
        for (int x : new int[]{4, 12}) {
            place(level, new BlockPos(x, y, 8), fence);
            place(level, new BlockPos(x, y, 7), fence);
            place(level, new BlockPos(x, y, 9), fence);
        }
        for (int z : new int[]{4, 12}) {
            place(level, new BlockPos(8, y, z), fence);
            place(level, new BlockPos(7, y, z), fence);
            place(level, new BlockPos(9, y, z), fence);
        }

        // Central chain plus four lower lanterns.
        place(level, new BlockPos(8, 70, 8), chain);
        place(level, new BlockPos(8, 69, 8), chain);
        place(level, new BlockPos(8, 68, 8), lantern);

        int[][] arms = {
                {5, 68, 8}, {11, 68, 8},
                {8, 68, 5}, {8, 68, 11}
        };
        for (int[] p : arms) {
            place(level, new BlockPos(p[0], 70, p[2]), chain);
            place(level, new BlockPos(p[0], 69, p[2]), chain);
            place(level, new BlockPos(p[0], p[1], p[2]), lantern);
        }
    }

    private static void placeWallBookshelves(ServerLevel level, RandomSource random) {
        // West/east walls: variable-height groups. Windows occupy z=5..7.
        for (int z = 2; z <= 14; z += 2) {
            if (z >= 5 && z <= 7) {
                continue;
            }
            int heightWest = 1 + random.nextInt(3);
            int heightEast = 1 + random.nextInt(3);
            for (int y = FLOOR_Y + 1; y < FLOOR_Y + 1 + heightWest; y++) {
                place(level, new BlockPos(1, y, z), Blocks.BOOKSHELF.defaultBlockState());
            }
            for (int y = FLOOR_Y + 1; y < FLOOR_Y + 1 + heightEast; y++) {
                place(level, new BlockPos(15, y, z), Blocks.BOOKSHELF.defaultBlockState());
            }
        }

        // North wall below the windows.
        for (int x = 2; x <= 14; x++) {
            if ((x >= 3 && x <= 5) || (x >= 11 && x <= 13)) {
                continue;
            }
            int height = 1 + random.nextInt(2);
            for (int y = FLOOR_Y + 1; y < FLOOR_Y + 1 + height; y++) {
                place(level, new BlockPos(x, y, 1), Blocks.BOOKSHELF.defaultBlockState());
            }
        }
    }

    private static void placeWallLadders(ServerLevel level, RandomSource random) {
        // One stable wall ladder with a small chance of a second short section.
        int side = random.nextBoolean() ? 2 : 14;
        Direction facing = side == 2 ? Direction.EAST : Direction.WEST;
        for (int y = 65; y <= 68; y++) {
            place(level, new BlockPos(side, y, 14),
                    Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, facing));
        }
    }

    private static void placeWallFlowerPots(ServerLevel level, RandomSource random) {
        place(level, new BlockPos(3, 65, 12), Blocks.POTTED_DANDELION.defaultBlockState());
        place(level, new BlockPos(13, 65, 12), Blocks.POTTED_POPPY.defaultBlockState());
        if (random.nextBoolean()) {
            place(level, new BlockPos(2, 65, 10), Blocks.POTTED_OXEYE_DAISY.defaultBlockState());
        }
    }

    private static void placeWallPaintings(ServerLevel level, RandomSource random) {
        Optional<Painting> p1 = Painting.create(level, new BlockPos(4, 68, 15), Direction.NORTH);
        p1.ifPresent(entity -> entity.addTag("pocketoffice_decoration"));
        Optional<Painting> p2 = Painting.create(level, new BlockPos(12, 68, 15), Direction.NORTH);
        p2.ifPresent(entity -> entity.addTag("pocketoffice_decoration"));

        if (random.nextBoolean()) {
            Optional<Painting> p3 = Painting.create(level, new BlockPos(14, 68, 8), Direction.WEST);
            p3.ifPresent(entity -> entity.addTag("pocketoffice_decoration"));
        }
    }

    private static void placeAmbientLanterns(ServerLevel level, RandomSource random) {
        // Additional wall/floor lighting kept separate from the chandelier.
        place(level, new BlockPos(2, 65, 14), Blocks.LANTERN.defaultBlockState());
        place(level, new BlockPos(14, 65, 11), Blocks.LANTERN.defaultBlockState());

        if (random.nextBoolean()) {
            place(level, new BlockPos(2, 65, 8), Blocks.LANTERN.defaultBlockState());
        }
        if (random.nextBoolean()) {
            place(level, new BlockPos(14, 65, 8), Blocks.LANTERN.defaultBlockState());
        }
    }

    private static void ensurePocketDoor(ServerLevel level) {
        BlockPos lower = POCKET_DOOR_LOWER;
        BlockPos upper = lower.above();

        // Do not overwrite a door that already exists: its open/closed state is synchronized
        // with the exterior door. Only install the missing pocket-side model in worlds
        // generated by older PocketDoor versions.
        boolean lowerPresent = level.getBlockState(lower).is(ModBlocks.POCKET_DOOR);
        boolean upperPresent = level.getBlockState(upper).is(ModBlocks.POCKET_DOOR);
        if (lowerPresent && upperPresent) {
            return;
        }

        // The pocket-side face looks toward the exterior doorway in the south wall.
        BlockState lowerState = ModBlocks.POCKET_DOOR.defaultBlockState()
                .setValue(DoorBlock.FACING, Direction.SOUTH)
                .setValue(DoorBlock.HINGE, net.minecraft.world.level.block.state.properties.DoorHingeSide.LEFT)
                .setValue(DoorBlock.OPEN, false)
                .setValue(DoorBlock.POWERED, false)
                .setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER);
        BlockState upperState = lowerState.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER);

        place(level, lower, lowerState);
        place(level, upper, upperState);
        PocketDoorMod.LOGGER.info("Repaired the missing pocket-side door in the Pocket Office at {}.", lower);
    }



    private static void place(ServerLevel level, BlockPos pos, BlockState state) {
        level.setBlock(pos, state, 3);
    }
}
