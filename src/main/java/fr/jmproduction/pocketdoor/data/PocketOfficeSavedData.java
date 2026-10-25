package fr.jmproduction.pocketdoor.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.saveddata.SavedData;

/** Remembers that the one procedural office has already been generated. */
public class PocketOfficeSavedData extends SavedData {
    private static final String DATA_ID = "pocketoffice_data";
    private static final String GENERATED = "Generated";
    private static final String LAYOUT = "Layout";

    /**
     * Version de l'agencement du mobilier.
     * 1 = chaise au sud du bureau (anciens mondes) ; 2 = chaise au nord, de l'autre côté du bureau ;
     * 3 = bureau retourné de 180° (FACING = NORTH).
     */
    public static final int CURRENT_LAYOUT = 3;

    private boolean generated;
    private int layout;

    public PocketOfficeSavedData() {
    }

    public static PocketOfficeSavedData load(CompoundTag tag) {
        PocketOfficeSavedData data = new PocketOfficeSavedData();
        data.generated = tag.getBoolean(GENERATED);
        // Les mondes sauvegardés avant l'introduction de ce champ ont l'agencement 1.
        data.layout = tag.contains(LAYOUT) ? tag.getInt(LAYOUT) : (data.generated ? 1 : 0);
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putBoolean(GENERATED, generated);
        tag.putInt(LAYOUT, layout);
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
        layout = CURRENT_LAYOUT;
        setDirty();
    }

    public int getLayout() {
        return layout;
    }

    public void setLayout(int layout) {
        this.layout = layout;
        setDirty();
    }
}
