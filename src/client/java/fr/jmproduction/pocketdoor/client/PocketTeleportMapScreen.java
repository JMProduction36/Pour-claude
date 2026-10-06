package fr.jmproduction.pocketdoor.client;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.jmproduction.pocketdoor.network.ModNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Functional top-down teleport map for the Pocket Door.
 *
 * The map stays centered on the screen, can be zoomed with the mouse wheel,
 * displays the exact world coordinates under the cursor, and lets the player
 * click a discovered position to set it as the teleport destination.
 */
public final class PocketTeleportMapScreen extends Screen {
    private static final int MAP_CELLS = 160;
    private static final int MAX_MAP_PIXELS = 480;
    private static final int SIDE_MARGIN = 48;
    private static final int VERTICAL_MARGIN = 170;

    private int mapLeft;
    private int mapTop;
    private int mapPixels;
    private int centerX;
    private int centerZ;
    private int blocksPerCell = 1;
    private int selectedX;
    private int selectedZ;
    private boolean hasSelection;

    public PocketTeleportMapScreen() {
        super(Component.literal("Carte de téléportation"));
        centerX = (int) Math.floor(PocketMapState.getLastOverworldX());
        centerZ = (int) Math.floor(PocketMapState.getLastOverworldZ());
        selectedX = centerX;
        selectedZ = centerZ;
        hasSelection = PocketMapState.hasDiscovered(centerX, centerZ);
    }

    @Override
    protected void init() {
        mapPixels = Math.min(MAX_MAP_PIXELS, Math.min(width - SIDE_MARGIN, height - VERTICAL_MARGIN));
        mapPixels = Math.max(220, mapPixels);
        mapPixels = Math.min(mapPixels, Math.min(width - 16, height - 110));

        // The map is always centered in the available GUI space instead of being
        // anchored to the bottom of the screen.
        mapLeft = (width - mapPixels) / 2;
        mapTop = (height - mapPixels) / 2;

        int buttonY = mapTop + mapPixels + 18;
        addRenderableWidget(new Button(width / 2 - 105, buttonY, 100, 20,
                Component.literal("Confirmer"), b -> confirm()));
        addRenderableWidget(new Button(width / 2 + 5, buttonY, 100, 20,
                Component.literal("Annuler"), b -> onClose()));
    }

    private void confirm() {
        if (!hasSelection || !PocketMapState.hasDiscovered(selectedX, selectedZ)) return;
        ClientPlayNetworking.send(ModNetworking.SET_TELEPORT_TARGET,
                ModNetworking.createTargetPacket(selectedX, selectedZ));
        onClose();
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(null);
    }

    @Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        renderBackground(poseStack);
        fill(poseStack, 0, 0, width, height, 0xFF111111);

        drawCenteredString(poseStack, font, title, width / 2, mapTop - 34, 0xFFFFFFFF);
        drawCenteredString(poseStack, font,
                Component.literal("Clique sur une zone découverte • molette : zoom"),
                width / 2, mapTop - 21, 0xFFBBBBBB);

        drawMap(poseStack);
        drawInformation(poseStack, mouseX, mouseY);
        super.render(poseStack, mouseX, mouseY, partialTick);
    }

    private void drawMap(PoseStack poseStack) {
        fill(poseStack, mapLeft - 3, mapTop - 3, mapLeft + mapPixels + 3, mapTop + mapPixels + 3, 0xFF777777);
        fill(poseStack, mapLeft - 1, mapTop - 1, mapLeft + mapPixels + 1, mapTop + mapPixels + 1, 0xFF000000);

        double cellPixels = (double) mapPixels / MAP_CELLS;
        for (int sy = 0; sy < MAP_CELLS; sy++) {
            for (int sx = 0; sx < MAP_CELLS; sx++) {
                int worldX = sampleWorldX(sx);
                int worldZ = sampleWorldZ(sy);
                int color = PocketMapState.getColor(worldX, worldZ);

                int left = mapLeft + (int) Math.floor(sx * cellPixels);
                int top = mapTop + (int) Math.floor(sy * cellPixels);
                int right = mapLeft + (int) Math.ceil((sx + 1) * cellPixels);
                int bottom = mapTop + (int) Math.ceil((sy + 1) * cellPixels);

                fill(poseStack, left, top, right, bottom, color);
            }
        }

        int currentX = (int) Math.floor(PocketMapState.getLastOverworldX());
        int currentZ = (int) Math.floor(PocketMapState.getLastOverworldZ());
        drawMarker(poseStack, currentX, currentZ, 0xFF4EA1FF);

        if (hasSelection) {
            drawMarker(poseStack, selectedX, selectedZ, 0xFFFF4D4D);
        }

        // Small center crosshair showing the map's current center.
        int cx = mapLeft + mapPixels / 2;
        int cz = mapTop + mapPixels / 2;
        fill(poseStack, cx - 5, cz, cx + 6, cz + 1, 0x66FFFFFF);
        fill(poseStack, cx, cz - 5, cx + 1, cz + 6, 0x66FFFFFF);

    }

    private void drawInformation(PoseStack poseStack, int mouseX, int mouseY) {
        // Zoom and cursor coordinates are deliberately stacked at the top-left.
        String zoom = "Zoom : 1:" + blocksPerCell;
        drawString(poseStack, font, zoom, 8, 8, 0xFFBBBBBB);

        int[] hovered = getWorldCoordinates(mouseX, mouseY);
        if (hovered != null) {
            int hx = hovered[0];
            int hz = hovered[1];
            boolean discovered = PocketMapState.hasDiscovered(hx, hz);
            int textColor = discovered ? 0xFFFFFFFF : 0xFF777777;
            drawString(poseStack, font, "Coordonnées : X " + hx + "  Z " + hz, 8, 22, textColor);
            drawString(poseStack, font,
                    discovered ? "Zone découverte" : "Zone inexplorée",
                    8, 36, discovered ? 0xFF79C879 : 0xFF777777);

            drawHoverCell(poseStack, hx, hz);
        } else {
            drawString(poseStack, font, "Coordonnées : —", 8, 22, 0xFF777777);
        }

        String coords = hasSelection
                ? "Destination : X " + selectedX + "  Z " + selectedZ
                : "Destination : aucune zone découverte sélectionnée";
        drawCenteredString(poseStack, font, coords, width / 2, mapTop + mapPixels + 3,
                hasSelection ? 0xFFFFFFFF : 0xFFFF7777);
    }

    private void drawHoverCell(PoseStack poseStack, int worldX, int worldZ) {
        double totalBlocks = MAP_CELLS * (double) blocksPerCell;
        double relX = (worldX - centerX + 0.5D) / totalBlocks;
        double relZ = (worldZ - centerZ + 0.5D) / totalBlocks;
        int px = mapLeft + (int) Math.floor((relX + 0.5D) * mapPixels);
        int pz = mapTop + (int) Math.floor((relZ + 0.5D) * mapPixels);

        if (px < mapLeft || px >= mapLeft + mapPixels || pz < mapTop || pz >= mapTop + mapPixels) return;
        fill(poseStack, px, pz, px + 2, pz + 2, 0xCCFFFFFF);
    }

    private void drawMarker(PoseStack poseStack, int x, int z, int color) {
        double totalBlocks = MAP_CELLS * (double) blocksPerCell;
        double relX = (x - centerX + 0.5D) / totalBlocks;
        double relZ = (z - centerZ + 0.5D) / totalBlocks;
        int px = mapLeft + (int) Math.floor((relX + 0.5D) * mapPixels);
        int pz = mapTop + (int) Math.floor((relZ + 0.5D) * mapPixels);
        if (px < mapLeft || px >= mapLeft + mapPixels || pz < mapTop || pz >= mapTop + mapPixels) return;
        fill(poseStack, px - 2, pz, px + 3, pz + 1, color);
        fill(poseStack, px, pz - 2, px + 1, pz + 3, color);
    }

    private int sampleWorldX(int screenX) {
        return centerX + (screenX - MAP_CELLS / 2) * blocksPerCell;
    }

    private int sampleWorldZ(int screenZ) {
        return centerZ + (screenZ - MAP_CELLS / 2) * blocksPerCell;
    }

    private boolean isMouseOverMap(double mouseX, double mouseY) {
        return mouseX >= mapLeft && mouseX < mapLeft + mapPixels
                && mouseY >= mapTop && mouseY < mapTop + mapPixels;
    }

    /** Converts the mouse position to the exact world block under the cursor. */
    private int[] getWorldCoordinates(double mouseX, double mouseY) {
        if (!isMouseOverMap(mouseX, mouseY)) return null;

        double normalizedX = (mouseX - mapLeft) / (double) mapPixels;
        double normalizedZ = (mouseY - mapTop) / (double) mapPixels;
        int worldWidth = MAP_CELLS * blocksPerCell;
        int worldX = centerX + (int) Math.floor((normalizedX - 0.5D) * worldWidth);
        int worldZ = centerZ + (int) Math.floor((normalizedZ - 0.5D) * worldWidth);
        return new int[] {worldX, worldZ};
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int[] hovered = getWorldCoordinates(mouseX, mouseY);
            if (hovered != null) {
                selectedX = hovered[0];
                selectedZ = hovered[1];
                hasSelection = PocketMapState.hasDiscovered(selectedX, selectedZ);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int[] levels = {1, 2, 4, 8, 16};
        int current = 0;
        for (int i = 0; i < levels.length; i++) {
            if (levels[i] == blocksPerCell) {
                current = i;
                break;
            }
        }
        current += delta > 0 ? -1 : 1;
        current = Math.max(0, Math.min(levels.length - 1, current));
        blocksPerCell = levels[current];
        return true;
    }
}
