package fr.jmproduction.pocketdoor.block;

import fr.jmproduction.pocketdoor.network.ModNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.VoxelShape;

/** A small typewriter sitting on the desk, with one persistent book slot. */
public class TypewriterBlock extends Block implements EntityBlock {
    private static final VoxelShape COLLISION = Block.box(1, 0, 1, 15, 10, 15);

    public TypewriterBlock(Properties properties) {
        super(properties.noOcclusion());
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                               net.minecraft.world.phys.shapes.CollisionContext context) {
        return COLLISION;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                        net.minecraft.world.phys.shapes.CollisionContext context) {
        return COLLISION;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TypewriterBlockEntity(pos, state);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos,
                                 Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        if (!(level.getBlockEntity(pos) instanceof TypewriterBlockEntity typewriter)) {
            return InteractionResult.PASS;
        }

        ItemStack held = player.getItemInHand(hand);

        // A sneak-right-click with an empty hand removes the currently installed book.
        if (held.isEmpty() && player.isShiftKeyDown()) {
            if (!typewriter.hasBook()) {
                player.displayClientMessage(Component.literal("La machine à écrire est vide."), true);
                return InteractionResult.CONSUME;
            }
            ItemStack removed = typewriter.takeBook();
            giveOrDrop(player, removed);
            player.displayClientMessage(Component.literal("Ouvrage retiré."), true);
            return InteractionResult.CONSUME;
        }

        // Right-clicking with a writable/written book installs it. If another book is
        // already installed, the old one is returned to the player, so old manuscripts
        // can be swapped in and out without losing their NBT/pages.
        if (typewriter.acceptsBook(held)) {
            ItemStack inserted = held.split(1);
            ItemStack old = typewriter.takeBook();
            typewriter.setBook(inserted);
            if (!old.isEmpty()) {
                giveOrDrop(player, old);
            }
            player.displayClientMessage(Component.literal(
                    old.isEmpty() ? "Ouvrage placé." : "Ouvrage remplacé."), true);
            return InteractionResult.CONSUME;
        }

        // Empty hand, normal click: use the book currently in the machine.
        if (held.isEmpty()) {
            if (!typewriter.hasBook()) {
                player.displayClientMessage(Component.literal("Aucun ouvrage dans la machine à écrire."), true);
                return InteractionResult.CONSUME;
            }
            if (player instanceof ServerPlayer serverPlayer) {
                ModNetworking.openTypewriterBook(serverPlayer, pos, hand);
            }
            return InteractionResult.CONSUME;
        }

        return InteractionResult.PASS;
    }

    private static void giveOrDrop(Player player, ItemStack stack) {
        if (stack.isEmpty()) return;
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }
}
