package dev.dukedarius.HytaleIndustries.Utils;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.entity.Frozen;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.component.EntityScaleComponent;
import com.hypixel.hytale.server.core.modules.entity.component.Intangible;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.modules.entity.item.PreventItemMerging;
import com.hypixel.hytale.server.core.modules.entity.item.PreventPickup;
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
                                       ESDiskHousingComponent housing, int yawIndex) {
        world.execute(() -> {
            try {
                Store<EntityStore> entityStore = world.getEntityStore().getStore();
                for (int slot = 0; slot < ESDiskHousingComponent.DISK_SLOT_COUNT; slot++) {
                    long key = packKey(pos, slot);
                    boolean active = housing.isDiskActive(slot);

                    Ref<EntityStore> existing = DISPLAY_ENTITIES.get(key);

                    if (!active) {
                        // Remove display if disk removed
                        if (existing != null) {
                            DISPLAY_ENTITIES.remove(key);
                            try { entityStore.removeEntity(existing, RemoveReason.REMOVE); }
                            catch (Throwable ignored) {}
                        }
                        continue;
                    }

                    // Disk is active — spawn if not already displayed
                    if (existing == null || !existing.isValid()) {
                        spawnDiskDisplay(entityStore, pos, slot, yawIndex, key);
                    }
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
                                          int slot, int yawIndex, long key) {
        try {
            int yi = yawIndex & 3;
            int row = slot / 2;   // 0,0,1,1,2,2,3,3
            int col = slot % 2;   // 0,1,0,1,0,1,0,1

            // Compute position on front face
            double localX = COL_X[col];
            double localY = ROW_Y[row];
            double localZ = FORWARD_OFFSET;

            // Rotate local offset by yaw
            double worldX, worldZ;
            switch (yi) {
                case 0 -> { worldX = localX;  worldZ = localZ; }  // South
                case 1 -> { worldX = localZ;  worldZ = -localX; } // East
                case 2 -> { worldX = -localX; worldZ = -localZ; } // North
                case 3 -> { worldX = -localZ; worldZ = localX; }  // West
                default -> { worldX = localX; worldZ = localZ; }
            }

            Vector3d displayPos = new Vector3d(
                    pos.x + 0.5 + worldX,
                    pos.y + localY,
                    pos.z + 0.5 + worldZ);
            Rotation3f rot = new Rotation3f(0f, YAW_RADIANS[yi] + (float)Math.PI, 0f);

            ItemStack displayStack = new ItemStack(ESDiskHousingComponent.DISK_ITEM_ID, 1);
            displayStack.setOverrideDroppedItemAnimation(true);

            Holder<EntityStore> holder = EntityStore.REGISTRY.newHolder();

            ItemComponent itemComp = new ItemComponent(displayStack);
            itemComp.setPickupDelay(Float.MAX_VALUE);
            itemComp.setRemovedByPlayerPickup(false);
            holder.addComponent(ItemComponent.getComponentType(), itemComp);

            holder.addComponent(TransformComponent.getComponentType(),
                    new TransformComponent(displayPos, rot));
            holder.addComponent(EntityScaleComponent.getComponentType(),
                    new EntityScaleComponent(SCALE));

            holder.ensureComponent(Frozen.getComponentType());
            holder.ensureComponent(Intangible.getComponentType());
            holder.ensureComponent(PreventPickup.getComponentType());
            holder.ensureComponent(PreventItemMerging.getComponentType());
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
