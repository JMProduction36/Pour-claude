package fr.jmproduction.pocketdoor.block;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Stores the single book that is sitting in the pocket-office typewriter.
 * The book survives world/chunk saves just like a normal block entity inventory.
 */
public class TypewriterBlockEntity extends BlockEntity {
    private static final String BOOK_TAG = "Book";

    private ItemStack book = ItemStack.EMPTY;

    public TypewriterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TYPEWRITER, pos, state);
    }

    public ItemStack getBook() {
        return book;
    }

    public boolean hasBook() {
        return !book.isEmpty();
    }

    public void setBook(ItemStack stack) {
        book = stack == null ? ItemStack.EMPTY : stack;
        setChanged();
    }

    public ItemStack takeBook() {
        ItemStack result = book;
        book = ItemStack.EMPTY;
        setChanged();
        return result;
    }

    public boolean acceptsBook(ItemStack stack) {
        return stack.is(Items.WRITABLE_BOOK) || stack.is(Items.WRITTEN_BOOK);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (!book.isEmpty()) {
            CompoundTag bookTag = new CompoundTag();
            book.save(bookTag);
            tag.put(BOOK_TAG, bookTag);
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains(BOOK_TAG, 10)) {
            book = ItemStack.of(tag.getCompound(BOOK_TAG));
        } else {
            book = ItemStack.EMPTY;
        }
    }
}
