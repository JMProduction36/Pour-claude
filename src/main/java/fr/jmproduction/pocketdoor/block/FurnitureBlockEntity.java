package fr.jmproduction.pocketdoor.block;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Backing storage for the {@link DeskBlock}. Holds two independent 27-slot
 * {@link SimpleContainer} sub-inventories: a left half and a right half.
 * The two halves together form the 54-slot "double chest" view opened on the
 * master block; the two side anchors ({@link DeskPartBlock}) only expose
 * their respective half.
 *
 * <p>State persists across chunk saves through standard NBT – each half is
 * serialised under its own {@link ListTag} (key {@code "left"} / {@code
 * "right"} on the block entity root) so reloads do not lose items.</p>
 *
 * <p>The persistence layer writes and reads per-slot {@link CompoundTag}s
 * directly, rather than going through {@code ContainerHelper} or relying on
 * {@code SimpleContainer#getItems()}. That keeps the code compatible across
 * Minecraft versions where those APIs have shifted (for example,
 * {@code SimpleContainer#getItems()} is not part of the stable Fabric
 * contract in some mapped versions).</p>
 */
public class FurnitureBlockEntity extends BlockEntity {

    /** Number of slots in a single half (matches a vanilla single chest). */
    public static final int HALF_SIZE = 27;

    private final SimpleContainer left = new SimpleContainer(HALF_SIZE);
    private final SimpleContainer right = new SimpleContainer(HALF_SIZE);

    public FurnitureBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.FURNITURE, pos, state);
    }

    public SimpleContainer getLeft() {
        return left;
    }

    public SimpleContainer getRight() {
        return right;
    }

    // -------- NBT --------

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("left", saveHalf(left));
        tag.put("right", saveHalf(right));
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        // Clear in case the level is being reloaded so leftover items from the
        // previous load do not mix with the freshly-read ones.
        for (int i = 0; i < HALF_SIZE; i++) {
            left.setItem(i, ItemStack.EMPTY);
            right.setItem(i, ItemStack.EMPTY);
        }
        if (tag.contains("left", 9 /* ListTag */)) {
            loadHalf(tag.getList("left", 10 /* CompoundTag */), left);
        }
        if (tag.contains("right", 9 /* ListTag */)) {
            loadHalf(tag.getList("right", 10 /* CompoundTag */), right);
        }
    }

    /**
     * Build a {@link ListTag} where each entry is a per-slot
     * {@link CompoundTag} carrying the slot index under {@code "Slot"} and
     * the serialised {@link ItemStack} written through
     * {@link ItemStack#save(CompoundTag)}. Empty slots are skipped, matching
     * the vanilla chest format produced by
     * {@code ContainerHelper.saveAllItems}.
     */
    private static ListTag saveHalf(SimpleContainer container) {
        ListTag list = new ListTag();
        for (int i = 0; i < HALF_SIZE; i++) {
            ItemStack stack = container.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            CompoundTag entry = new CompoundTag();
            entry.putByte("Slot", (byte) i);
            // ItemStack.save writes "id", "Count" and the optional "tag"
            // sub-compound directly into the supplied tag.
            stack.save(entry);
            list.add(entry);
        }
        return list;
    }

    /**
     * Re-hydrate a {@link SimpleContainer} from a {@link ListTag} produced by
     * {@link #saveHalf(SimpleContainer)}. Entries whose slot index is out of
     * range are silently ignored so corrupted saves never throw.
     */
    private static void loadHalf(ListTag list, SimpleContainer container) {
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            int slot = entry.getByte("Slot") & 0xFF;
            if (slot < 0 || slot >= HALF_SIZE) {
                continue;
            }
            // ItemStack.of accepts the same shape ItemStack.save emits, with
            // "id", "Count" and optional "tag".
            container.setItem(slot, ItemStack.of(entry));
        }
    }
}
