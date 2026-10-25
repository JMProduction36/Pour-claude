package fr.jmproduction.pocketdoor.block;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.CompoundContainer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Block entity that owns the unified 54-slot "double chest" inventory of a
 * desk. The user-visible inventory is one big 54-slot container, exposed via
 * {@link #getCompound()}.
 *
 * <p>For NBT backwards compatibility with older saves that stored items under
 * the {@code left}/{@code right} sub-keys (the previous two-half design),
 * the two 27-slot halves are still kept in memory and serialised under those
 * exact tags. {@link #getCompound()} returns a {@link CompoundContainer}
 * wrapping both halves, so any pre-existing items are still reachable through
 * the unified 54-slot view and any new items land in the right half (then
 * overflow into the left half, like vanilla double chests).</p>
 */
public class FurnitureBlockEntity extends BlockEntity {

    public static final int HALF_SIZE = 27;

    private final SimpleContainer left = new SimpleContainer(HALF_SIZE);
    private final SimpleContainer right = new SimpleContainer(HALF_SIZE);
    private final CompoundContainer compound = new CompoundContainer(right, left);

    public FurnitureBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.FURNITURE, pos, state);
        // Sans ces écouteurs, un objet déposé dans le bureau ne marquait jamais le chunk comme modifié :
        // l'inventaire pouvait être perdu à la fermeture du monde.
        left.addListener(container -> setChanged());
        right.addListener(container -> setChanged());
    }

    public SimpleContainer getLeft() {
        return left;
    }

    public SimpleContainer getRight() {
        return right;
    }

    /**
     * Returns the unified 54-slot view used by the single desk menu. Vanilla
     * double-chest semantics apply (right half fills first, then left).
     */
    public CompoundContainer getCompound() {
        return compound;
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
        // Wipe before loading so a malformed save can't leave stale items.
        for (int i = 0; i < HALF_SIZE; i++) {
            left.setItem(i, ItemStack.EMPTY);
            right.setItem(i, ItemStack.EMPTY);
        }
        if (tag.contains("left", 9 /* ListTag */)) {
            loadHalf(left, tag.getList("left", 10 /* CompoundTag */));
        }
        if (tag.contains("right", 9 /* ListTag */)) {
            loadHalf(right, tag.getList("right", 10 /* CompoundTag */));
        }
    }

    /**
     * Mirror of vanilla chest NBT: only non-empty stacks are written, with
     * their slot index under the {@code Slot} byte. Uses the public
     * {@link ItemStack#save} / {@link ItemStack#of} API so the contract is
     * stable across Mojang mappings.
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
            stack.save(entry);
            list.add(entry);
        }
        return list;
    }

    private static void loadHalf(SimpleContainer container, ListTag list) {
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            int slot = entry.getByte("Slot") & 0xFF;
            if (slot < 0 || slot >= HALF_SIZE) {
                continue;
            }
            container.setItem(slot, ItemStack.of(entry));
        }
    }
}
