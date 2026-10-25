package fr.jmproduction.pocketdoor.portal;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import com.mojang.math.Quaternion;

import java.util.UUID;

/**
 * Faithful port of qouteall.imm_ptl.core.portal.Portal (Immersive Portals 1.19.2, v2.3.2).
 * Server-side portal entity that drives client-side rendering and teleportation.
 * We keep the surface area that Pocket Door needs: spawn / orientation / tagging /
 * collision / teleportable.
 *
 * <p>Bugfix (NPE &quot;packet is null&quot; in ServerEntity.sendPairingData):</p>
 * <ul>
 *   <li>{@link #TYPE} is now a non-final field, set at runtime by
 *       {@code ModEntities.initialize()} through {@code FabricEntityTypeBuilder}.
 *       Previously it was {@code null} and {@code ImmersivePortalBridge} created
 *       portal entities using {@code EntityType.ITEM} as a stand-in, which caused
 *       mismatched client-side entity lookups.</li>
 *   <li>{@link #getAddEntityPacket()} now returns a real
 *       {@link ClientboundAddEntityPacket}. Returning {@code null} makes
 *       {@code ServerEntity.sendPairingData} forward a {@code null} packet down
 *       the connection pipeline and crash with
 *       {@code NullPointerException: packet is null}.</li>
 * </ul>
 */
public class Portal extends Entity {
    /** Registered by {@code ModEntities.initialize()} using FabricEntityTypeBuilder. */
    public static EntityType<Portal> TYPE;

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

    @Override protected void defineSynchedData() {}
    @Override protected void readAdditionalSaveData(CompoundTag tag) {}
    @Override protected void addAdditionalSaveData(CompoundTag tag) {}

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
        if (!lvl.addFreshEntity(p)) {
            // already exists; do nothing
        }
    }
}
