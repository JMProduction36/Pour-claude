package fr.jmproduction.pocketdoor.portal;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.synched.EntityDataAccessor;
import net.minecraft.network.synched.EntityDataSerializers;
import net.minecraft.network.synched.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import com.mojang.math.Quaternion;

import java.util.UUID;

/**
 * Faithful port of qouteall.imm_ptl.core.portal.Portal (Immersive Portals 1.19.2, v2.3.2).
 *
 * <p><b>Bugfix (portal invisible on client when door is open):</b> portal-specific fields
 * ({@code destinationWorld}, {@code destinationPos}, {@code axisW/H}, {@code rotation},
 * {@code portalWidth/Height}, {@code portalTag}) were plain server-side public fields
 * with no SynchedEntityData — so the client received only {@link ClientboundAddEntityPacket}
 * (position + type + uuid) and saw {@code destinationWorld == null},
 * {@code axisW = (1,0,0)}, {@code portalTag = ""}. The renderer filter
 * {@code portal.destinationWorld.equals(POCKET_OFFICE)} rejected every portal, hence
 * nothing was drawn through the doorway.</p>
 *
 * <p>The fix mirrors upstream Immersive Portals: a single {@link CompoundTag} data
 * parameter {@link #DATA} carries the full portal state. Server sets it after
 * {@link #spawnServerEntity(Portal)} via {@link #syncToClient()} and the client
 * populates the fields in {@link #onSyncedDataUpdated(EntityDataAccessor)}.</p>
 */
public class Portal extends Entity {
    /** Registered by {@code ModEntities.initialize()} using FabricEntityTypeBuilder. */
    public static EntityType<Portal> TYPE;

    /** Synced payload: destination, orientation, size, tag. See {@link #syncToClient()}. */
    private static final EntityDataAccessor<CompoundTag> DATA =
            SynchedEntityData.defineId(Portal.class, EntityDataSerializers.COMPOUND_TAG);

    public ResourceKey<Level> destinationWorld;
    public Vec3 destinationPos = Vec3.ZERO;
    public Quaternion rotation = Quaternion.ONE.copy();
    public Vec3 axisW = new Vec3(1,0,0);
    public Vec3 axisH = new Vec3(0,1,0);
    public double portalWidth = 0.98D;
    public double portalHeight = 1.99D;

    public String portalTag = "";
    public boolean teleportable = true;
    public boolean interactable = true;
    public boolean hasCrossPortalCollision = false;
    public boolean doRenderPlayer = false;
    public boolean renderingMergable = false;
    public boolean fuseView = false;

    private final UUID uuid = UUID.randomUUID();

    public Portal(EntityType<?> type, Level level) { super(type, level); }

    public void setOriginPos(Vec3 p) { this.setPos(p.x, p.y, p.z); }
    public Vec3 getOriginPos() { return position(); }
    public void setDestination(Vec3 p) { this.destinationPos = p; }
    public void setRotationTransformation(Quaternion q) { this.rotation = q; }
    public void setOrientationAndSize(Vec3 w, Vec3 h, double width, double height) {
        this.axisW = w; this.axisH = h; this.portalWidth = width; this.portalHeight = height;
    }

    public void setInteractable(boolean value) { this.interactable = value; }
    public boolean isInteractable() { return interactable; }

    @Override
    protected void defineSynchedData() {
        // Register the single compound tag that carries all portal state. Server writes
        // it via syncToClient() right after addFreshEntity(); client receives it in
        // ClientboundSetEntityDataPacket and triggers onSyncedDataUpdated().
        this.entityData.define(DATA, new CompoundTag());
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        readSyncData(tag);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        writeSyncData(tag);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        if (key == DATA) {
            CompoundTag tag = this.entityData.get(DATA);
            if (tag != null && !tag.isEmpty()) {
                readSyncData(tag);
            }
        }
        super.onSyncedDataUpdated(key);
    }

    /**
     * Serialize all portal state into a single compound tag. Used both for
     * persistent storage ({@link #addAdditionalSaveData}) and for the
     * {@code SynchedEntityData} payload sent to clients.
     */
    public CompoundTag writeSyncData(CompoundTag tag) {
        if (destinationWorld != null) {
            tag.putString("destinationWorld", destinationWorld.location().toString());
        } else {
            tag.putString("destinationWorld", "");
        }
        tag.putDouble("destX", destinationPos == null ? 0.0D : destinationPos.x);
        tag.putDouble("destY", destinationPos == null ? 0.0D : destinationPos.y);
        tag.putDouble("destZ", destinationPos == null ? 0.0D : destinationPos.z);
        // Quaternion is 4 floats (x, y, z, w).
        Quaternion q = rotation == null ? Quaternion.ONE.copy() : rotation;
        tag.putFloat("rotX", q.i());
        tag.putFloat("rotY", q.j());
        tag.putFloat("rotZ", q.k());
        tag.putFloat("rotW", q.r());
        tag.putDouble("axisWX", axisW == null ? 1.0D : axisW.x);
        tag.putDouble("axisWY", axisW == null ? 0.0D : axisW.y);
        tag.putDouble("axisWZ", axisW == null ? 0.0D : axisW.z);
        tag.putDouble("axisHX", axisH == null ? 0.0D : axisH.x);
        tag.putDouble("axisHY", axisH == null ? 1.0D : axisH.y);
        tag.putDouble("axisHZ", axisH == null ? 0.0D : axisH.z);
        tag.putDouble("width", portalWidth);
        tag.putDouble("height", portalHeight);
        tag.putString("tag", portalTag == null ? "" : portalTag);
        return tag;
    }

    /**
     * Inverse of {@link #writeSyncData}: apply the synced payload to this entity's
     * fields. Called both from {@link #readAdditionalSaveData} (chunk reload) and
     * from {@link #onSyncedDataUpdated} (live update from server).
     */
    public void readSyncData(CompoundTag tag) {
        if (tag == null) return;
        String dw = tag.getString("destinationWorld");
        if (!dw.isEmpty()) {
            try {
                destinationWorld = ResourceKey.create(
                        net.minecraft.core.Registry.DIMENSION_REGISTRY,
                        new ResourceLocation(dw));
            } catch (Exception ignored) {
                destinationWorld = null;
            }
        } else {
            destinationWorld = null;
        }
        destinationPos = new Vec3(
                tag.getDouble("destX"),
                tag.getDouble("destY"),
                tag.getDouble("destZ"));
        rotation = new Quaternion(
                tag.getFloat("rotX"),
                tag.getFloat("rotY"),
                tag.getFloat("rotZ"),
                tag.getFloat("rotW"));
        axisW = new Vec3(tag.getDouble("axisWX"), tag.getDouble("axisWY"), tag.getDouble("axisWZ"));
        axisH = new Vec3(tag.getDouble("axisHX"), tag.getDouble("axisHY"), tag.getDouble("axisHZ"));
        portalWidth = tag.getDouble("width");
        portalHeight = tag.getDouble("height");
        portalTag = tag.getString("tag");
    }

    /**
     * Server-only: push the current portal state into {@code SynchedEntityData} so the
     * client receives it via {@code ClientboundSetEntityDataPacket}. Call this right
     * after {@code addFreshEntity(p)} so the data is included in the first dirty tick.
     */
    public void syncToClient() {
        if (this.level == null || this.level.isClientSide) return;
        CompoundTag tag = writeSyncData(new CompoundTag());
        this.entityData.set(DATA, tag);
    }

    @Override
    public Packet<?> getAddEntityPacket() {
        // 1.19.2 (Mojang mappings): ClientboundAddEntityPacket(Entity). The vanilla
        // ServerEntity.sendPairingData() calls Connection.send(this.getAddEntityPacket());
        // a null packet here yields NPE "packet is null" in PacketUtils / the
        // protocol pipeline. Returning a real spawn packet is also the contract
        // expected by Fabric's NetworkHooks.getEntitySpawningPacket default.
        return new ClientboundAddEntityPacket(this);
    }

    public void spawnServer(ServerLevel level, Vec3 pos) {
        this.setPos(pos.x, pos.y, pos.z);
        level.addFreshEntity(this);
    }

    public static void spawnServerEntity(Portal p) {
        if (p == null || p.level == null || p.level.isClientSide) return;
        ServerLevel lvl = (ServerLevel) p.level;
        if (lvl.addFreshEntity(p)) {
            // Push the full portal state to the client. Without this the client
            // sees destinationWorld=null and PocketPortalRenderer rejects the portal.
            p.syncToClient();
        }
    }
}
