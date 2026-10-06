package fr.jmproduction.pocketdoor;

import fr.jmproduction.pocketdoor.block.ModBlockEntities;
import fr.jmproduction.pocketdoor.block.ModBlocks;
import fr.jmproduction.pocketdoor.event.PocketDoorEvents;
import fr.jmproduction.pocketdoor.network.ModNetworking;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PocketDoorMod implements ModInitializer {
    public static final String MOD_ID = "pocketdoor";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        ModBlocks.initialize();
        ModBlockEntities.initialize();
        ModNetworking.initialize();
        PocketDoorEvents.initialize();
        LOGGER.info("Pocket Door initialized.");
    }
}
