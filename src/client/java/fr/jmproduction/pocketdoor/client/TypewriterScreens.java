package fr.jmproduction.pocketdoor.client;

import fr.jmproduction.pocketdoor.network.ModNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.screens.inventory.BookEditScreen;
import net.minecraft.client.gui.screens.inventory.BookScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Vanilla-looking book screens with a tiny hook that returns the book to the typewriter on close. */
public final class TypewriterScreens {
    private TypewriterScreens() {
    }

    public static final class Edit extends BookEditScreen {
        private final BlockPos typewriterPos;
        private final InteractionHand hand;
        private boolean sent;

        public Edit(Player player, ItemStack stack, InteractionHand hand, BlockPos typewriterPos) {
            super(player, stack, hand);
            this.typewriterPos = typewriterPos.immutable();
            this.hand = hand;
        }

        @Override
        public void removed() {
            super.removed();
            sendFinishOnce();
        }

        private void sendFinishOnce() {
            if (sent) return;
            sent = true;
            ClientPlayNetworking.send(ModNetworking.FINISH_TYPEWRITER, 
                    ModNetworking.createFinishTypewriterPacket(typewriterPos, hand));
        }
    }

    public static final class Read extends BookScreen {
        private final BlockPos typewriterPos;
        private final InteractionHand hand;
        private boolean sent;

        public Read(ItemStack stack, InteractionHand hand, BlockPos typewriterPos) {
            super(BookScreen.Contents.create(stack));
            this.typewriterPos = typewriterPos.immutable();
            this.hand = hand;
        }

        @Override
        public void removed() {
            super.removed();
            if (sent) return;
            sent = true;
            ClientPlayNetworking.send(ModNetworking.FINISH_TYPEWRITER,
                    ModNetworking.createFinishTypewriterPacket(typewriterPos, hand));
        }
    }
}
