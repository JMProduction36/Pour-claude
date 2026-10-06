package fr.jmproduction.pocketdoor.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.saveddata.SavedData;

/** Remembers that the one procedural office has already been generated. */
public class PocketOfficeSavedData extends SavedData {
    private static final String DATA_ID = "pocketoffice_data";
    private static final String GENERATED = "Generated";

    private boolean generated;

    public PocketOfficeSavedData() {
    }

    public static PocketOfficeSavedData load(CompoundTag tag) {
        PocketOfficeSavedData data = new PocketOfficeSavedData();
        data.generated = tag.getBoolean(GENERATED);
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putBoolean(GENERATED, generated);
        return tag;
    }

    public static PocketOfficeSavedData get(net.minecraft.server.level.ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                PocketOfficeSavedData::load,
                PocketOfficeSavedData::new,
                DATA_ID
        );
    }

    public boolean isGenerated() {
        return generated;
    }

    public void markGenerated() {
        generated = true;
        setDirty();
    }
}
