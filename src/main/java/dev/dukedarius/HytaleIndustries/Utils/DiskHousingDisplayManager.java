package dev.dukedarius.HytaleIndustries.Utils;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset;
import com.hypixel.hytale.protocol.AnimationSlot;
import com.hypixel.hytale.server.core.entity.Frozen;
import com.hypixel.hytale.server.core.modules.entity.component.ActiveAnimationComponent;
import com.hypixel.hytale.server.core.modules.entity.component.BoundingBox;
import com.hypixel.hytale.server.core.modules.entity.component.Intangible;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.component.PersistentModel;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.dukedarius.HytaleIndustries.Components.EnergizedStorage.ESDiskHousingComponent;
import dev.dukedarius.HytaleIndustries.HytaleIndustriesPlugin;
import org.joml.Vector3d;
import org.joml.Vector3i;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Renders disk items on the front face of ES Disk Housing blocks.
 * 8 slots arranged as 2 columns x 4 rows: left0, right0, left1, right1, ...
 */
public final class DiskHousingDisplayManager {

    // Key: packed(blockPos, slotIndex) → entity ref
    private static final ConcurrentHashMap<Long, Ref<EntityStore>> DISPLAY_ENTITIES = new ConcurrentHashMap<>();
    private static final String DISK_MODEL_ASSET_ID = "HytaleIndustries_ESDisk_1kAsset";

    private static final float[] YAW_RADIANS = {
            0f, (float)(Math.PI * 0.5), (float)Math.PI, (float)(Math.PI * 1.5)
    };

    private static final double FORWARD_OFFSET = 0;
    private static final float SCALE = 2;

    // 4 rows, 2 columns — computed from 32x32 block, 8x4 cells, top-left at (6,5), gaps 4h/2v
    private static final double[] ROW_Y = {0.71875, 0.53125, 0.34375, 0.15625};
    private static final double[] COL_X = {-0.3125, 0.0625};

    private DiskHousingDisplayManager() {}

    /**
     * Update all 8 disk slot displays for a housing at the given position.
     * Called from world.execute() context.
     */
    public static void updateAllSlots(World world, Vector3i pos,
                                       ESDiskHousingComponent housing, int yawIndex,
                                       boolean networkOnline) {
        world.execute(() -> {
            try {
                Store<EntityStore> entityStore = world.getEntityStore().getStore();
                for (int slot = 0; slot < ESDiskHousingComponent.DISK_SLOT_COUNT; slot++) {
                    long key = packKey(pos, slot);
                    boolean active = housing.isDiskActive(slot);

                    // Remove existing display — we respawn with correct animation
                    Ref<EntityStore> existing = DISPLAY_ENTITIES.remove(key);
                    if (existing != null) {
                        try { entityStore.removeEntity(existing, RemoveReason.REMOVE); }
                        catch (Throwable ignored) {}
                    }

                    if (!active) continue;

                    // Determine animation based on power + fill level
                    String animation = null; // null = default (unpowered)
                    if (networkOnline) {
                        long used = housing.getDiskUsed(slot);
                        long capacity = housing.getDiskCapacity(slot);
                        if (capacity > 0 && used >= capacity) {
                            animation = "Full";
                        } else if (used > 0) {
                            animation = "PartFull";
                        } else {
                            animation = "On";
                        }
                    }

                    String modelAssetId = housing.getDiskModelAssetId(slot);
                    if (modelAssetId == null) modelAssetId = DISK_MODEL_ASSET_ID;

                    spawnDiskDisplay(entityStore, pos, slot, yawIndex, key, animation, modelAssetId);
                }
            } catch (Throwable t) {
                HytaleIndustriesPlugin.LOGGER.atWarning().withCause(t)
                        .log("[DiskDisplay] update failed at (%d,%d,%d)", pos.x, pos.y, pos.z);
            }
        });
    }

    /** Remove all displays for a housing (e.g., on block break) */
    public static void removeAll(World world, Vector3i pos) {
        world.execute(() -> {
            try {
                Store<EntityStore> entityStore = world.getEntityStore().getStore();
                for (int slot = 0; slot < ESDiskHousingComponent.DISK_SLOT_COUNT; slot++) {
                    long key = packKey(pos, slot);
                    Ref<EntityStore> ref = DISPLAY_ENTITIES.remove(key);
                    if (ref != null) {
                        try { entityStore.removeEntity(ref, RemoveReason.REMOVE); }
                        catch (Throwable ignored) {}
                    }
                }
            } catch (Throwable ignored) {}
        });
    }

    private static void spawnDiskDisplay(Store<EntityStore> entityStore, Vector3i pos,
                                          int slot, int yawIndex, long key,
                                          String animation, String modelAssetId) {
        try {
            int yi = yawIndex & 3;
            int row = slot / 2;
            int col = slot % 2;

            double localX = COL_X[col];
            double localY = ROW_Y[row];
            double localZ = FORWARD_OFFSET;

            double worldX, worldZ;
            switch (yi) {
                case 0 -> { worldX = localX;  worldZ = localZ; }
                case 1 -> { worldX = localZ;  worldZ = -localX; }
                case 2 -> { worldX = -localX; worldZ = -localZ; }
                case 3 -> { worldX = -localZ; worldZ = localX; }
                default -> { worldX = localX; worldZ = localZ; }
            }

            Vector3d displayPos = new Vector3d(
                    pos.x + 0.5 + worldX,
                    pos.y + localY,
                    pos.z + 0.5 + worldZ);
            Rotation3f rot = new Rotation3f(0f, YAW_RADIANS[yi] + (float)Math.PI, 0f);

            ModelAsset modelAsset = ModelAsset.getAssetMap().getAsset(modelAssetId);
            if (modelAsset == null) return;

            Model model = Model.createScaledModel(modelAsset, SCALE);
            if (model == null) return;

            Holder<EntityStore> holder = EntityStore.REGISTRY.newHolder();
            holder.addComponent(TransformComponent.getComponentType(),
                    new TransformComponent(displayPos, rot));
            holder.addComponent(PersistentModel.getComponentType(),
                    new PersistentModel(model.toReference()));
            holder.addComponent(ModelComponent.getComponentType(),
                    new ModelComponent(model));
            if (model.getBoundingBox() != null) {
                holder.addComponent(BoundingBox.getComponentType(),
                        new BoundingBox(model.getBoundingBox()));
            }
            holder.addComponent(NetworkId.getComponentType(),
                    new NetworkId(entityStore.getExternalData().takeNextNetworkId()));

            if (animation != null) {
                ActiveAnimationComponent anim = new ActiveAnimationComponent();
                anim.setPlayingAnimation(AnimationSlot.Status, animation);
                holder.addComponent(ActiveAnimationComponent.getComponentType(), anim);
            }

            holder.ensureComponent(Frozen.getComponentType());
            holder.ensureComponent(Intangible.getComponentType());
            holder.ensureComponent(EntityStore.REGISTRY.getNonSerializedComponentType());

            Ref<EntityStore> ref = entityStore.addEntity(holder, AddReason.SPAWN);
            if (ref != null) DISPLAY_ENTITIES.put(key, ref);
        } catch (Throwable t) {
            HytaleIndustriesPlugin.LOGGER.atWarning().withCause(t)
                    .log("[DiskDisplay] spawn failed slot %d at (%d,%d,%d)", slot, pos.x, pos.y, pos.z);
        }
    }

    // Pack block position + slot index into a single long key
    private static long packKey(Vector3i pos, int slot) {
        return (((long) pos.x & 0x3FFFFFFL) << 38)
                | (((long) pos.y & 0xFFFL) << 26)
                | (((long) pos.z & 0x3FFFFFFL) << 4)
                | ((long) slot & 0xFL);
    }

}
