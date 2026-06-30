package dev.dukedarius.HytaleIndustries.Utils;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.entity.Frozen;
import com.hypixel.hytale.server.core.entity.nameplate.Nameplate;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.component.Intangible;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.modules.entity.item.PreventItemMerging;
import com.hypixel.hytale.server.core.modules.entity.item.PreventPickup;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.dukedarius.HytaleIndustries.HytaleIndustriesPlugin;
import org.joml.Vector3d;
import org.joml.Vector3i;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public final class CacheDisplayManager {

    private record DisplayPair(Ref<EntityStore> item) {}

    private static final ConcurrentHashMap<Long, DisplayPair> DISPLAY_ENTITIES = new ConcurrentHashMap<>();
    private static final ConcurrentLinkedQueue<DirtyEntry> DIRTY_QUEUE = new ConcurrentLinkedQueue<>();

    private static final float[] YAW_RADIANS = {
            0f,                             // 0 = South (+Z)
            (float)(Math.PI * 0.5),         // 1 = East  (+X)
            (float) Math.PI,                // 2 = North (-Z)
            (float)(Math.PI * 1.5)          // 3 = West  (-X)
    };
    private static final double FORWARD_OFFSET = 0.52;
    private static final double ITEM_Y = 0.2;
    private static final double[][] FACE_OFFSET = {
            { 0.0,  0.0,  FORWARD_OFFSET},
            { FORWARD_OFFSET, 0.0,  0.0 },
            { 0.0,  0.0, -FORWARD_OFFSET},
            {-FORWARD_OFFSET, 0.0,  0.0 }
    };

    private CacheDisplayManager() {}

    public record DirtyEntry(int x, int y, int z, String itemId, long count, int yawIndex) {}

    public static void markDirty(int x, int y, int z, String itemId, long count, int yawIndex) {
        DIRTY_QUEUE.add(new DirtyEntry(x, y, z, itemId, count, yawIndex));
    }

    public static void processDirtyQueue(CommandBuffer<EntityStore> buffer,
                                         Store<EntityStore> store) {
        DirtyEntry entry;
        while ((entry = DIRTY_QUEUE.poll()) != null) {
            applyUpdateBuffered(buffer, store, new Vector3i(entry.x, entry.y, entry.z),
                    entry.itemId, entry.count, entry.yawIndex);
        }
    }

    public static void updateFromInteraction(World world, Vector3i pos,
                                             String itemId, long count, int yawIndex) {
        long key = packPos(pos);
        world.execute(() -> {
            try {
                Store<EntityStore> store = world.getEntityStore().getStore();
                applyUpdateDirect(store, pos, key, itemId, count, yawIndex);
            } catch (Throwable t) {
                HytaleIndustriesPlugin.LOGGER.atWarning().withCause(t)
                        .log("[CacheDisplay] world.execute() failed at (%d,%d,%d)", pos.x, pos.y, pos.z);
            }
        });
    }

    // --- buffered path (from EntityStore system tick) ---

    private static void applyUpdateBuffered(CommandBuffer<EntityStore> buffer,
                                            Store<EntityStore> store,
                                            Vector3i pos, String itemId, long count, int yawIndex) {
        long key = packPos(pos);
        if (itemId == null || count <= 0) {
            removeBuffered(buffer, key);
            return;
        }
        String label = String.format("%s x %d", friendlyName(itemId), count);
        DisplayPair existing = DISPLAY_ENTITIES.get(key);
        if (existing != null && existing.item != null && existing.item.isValid()) {
            try {
                buffer.replaceComponent(existing.item, Nameplate.getComponentType(), new Nameplate(label));
                return;
            } catch (Throwable t) {
                removeBuffered(buffer, key);
            }
        }
        spawnBuffered(buffer, store, pos, key, itemId, count, yawIndex);
    }

    private static void removeBuffered(CommandBuffer<EntityStore> buffer, long key) {
        DisplayPair old = DISPLAY_ENTITIES.remove(key);
        if (old != null && old.item != null) {
            try { buffer.tryRemoveEntity(old.item, RemoveReason.REMOVE); } catch (Throwable ignored) {}
        }
    }

    private static void spawnBuffered(CommandBuffer<EntityStore> buffer,
                                      Store<EntityStore> store,
                                      Vector3i pos, long key, String itemId, long count, int yawIndex) {
        try {
            int yi = yawIndex & 3;
            Ref<EntityStore> itemRef = buffer.addEntity(
                    buildItemHolder(pos, itemId, count, yi), AddReason.SPAWN);
            if (itemRef != null) {
                DISPLAY_ENTITIES.put(key, new DisplayPair(itemRef));
            }
        } catch (Throwable t) {
            HytaleIndustriesPlugin.LOGGER.atWarning().withCause(t)
                    .log("[CacheDisplay] buffered spawn failed at (%d,%d,%d)", pos.x, pos.y, pos.z);
        }
    }

    // --- direct path (from world.execute()) ---

    private static void applyUpdateDirect(Store<EntityStore> store, Vector3i pos, long key,
                                          String itemId, long count, int yawIndex) {
        if (itemId == null || count <= 0) {
            removeDirect(store, key);
            return;
        }
        String label = String.format("%s x %d", friendlyName(itemId), count);
        DisplayPair existing = DISPLAY_ENTITIES.get(key);
        if (existing != null && existing.item != null && existing.item.isValid()) {
            try {
                store.replaceComponent(existing.item, Nameplate.getComponentType(), new Nameplate(label));
                return;
            } catch (Throwable t) {
                removeDirect(store, key);
            }
        }
        spawnDirect(store, pos, key, itemId, count, yawIndex);
    }

    private static void removeDirect(Store<EntityStore> store, long key) {
        DisplayPair old = DISPLAY_ENTITIES.remove(key);
        if (old != null && old.item != null) {
            try { store.removeEntity(old.item, RemoveReason.REMOVE); } catch (Throwable ignored) {}
        }
    }

    private static void spawnDirect(Store<EntityStore> store, Vector3i pos, long key,
                                    String itemId, long count, int yawIndex) {
        try {
            int yi = yawIndex & 3;
            Ref<EntityStore> itemRef = store.addEntity(
                    buildItemHolder(pos, itemId, count, yi), AddReason.SPAWN);
            if (itemRef != null) {
                DISPLAY_ENTITIES.put(key, new DisplayPair(itemRef));
            }
        } catch (Throwable t) {
            HytaleIndustriesPlugin.LOGGER.atWarning().withCause(t)
                    .log("[CacheDisplay] direct spawn failed at (%d,%d,%d)", pos.x, pos.y, pos.z);
        }
    }

    // --- entity builders ---

    private static Holder<EntityStore> buildItemHolder(Vector3i pos, String itemId,
                                                       long count, int yi) {
        double[] offset = FACE_OFFSET[yi];
        Vector3d displayPos = new Vector3d(
                pos.x + 0.5 + offset[0],
                pos.y + ITEM_Y + offset[1],
                pos.z + 0.5 + offset[2]);
        Rotation3f rot = new Rotation3f(0f, YAW_RADIANS[yi], 0f);

        ItemStack displayStack = new ItemStack(itemId, 1);
        displayStack.setOverrideDroppedItemAnimation(true);

        Holder<EntityStore> holder = EntityStore.REGISTRY.newHolder();

        ItemComponent itemComp = new ItemComponent(displayStack);
        itemComp.setPickupDelay(Float.MAX_VALUE);
        itemComp.setRemovedByPlayerPickup(false);
        holder.addComponent(ItemComponent.getComponentType(), itemComp);

        holder.addComponent(TransformComponent.getComponentType(),
                new TransformComponent(displayPos, rot));
        holder.addComponent(Nameplate.getComponentType(),
                new Nameplate(String.format("%s x %d", friendlyName(itemId), count)));

        holder.ensureComponent(Frozen.getComponentType());
        holder.ensureComponent(Intangible.getComponentType());
        holder.ensureComponent(PreventPickup.getComponentType());
        holder.ensureComponent(PreventItemMerging.getComponentType());
        holder.ensureComponent(EntityStore.REGISTRY.getNonSerializedComponentType());

        return holder;
    }

    // --- utils ---

    private static long packPos(Vector3i pos) {
        return ((long) pos.x & 0x3FFFFFFL) << 38
                | ((long) pos.y & 0xFFFL) << 26
                | ((long) pos.z & 0x3FFFFFFL);
    }

    private static String friendlyName(String itemId) {
        int underscore = itemId.indexOf('_');
        if (underscore >= 0 && underscore < itemId.length() - 1) {
            return itemId.substring(underscore + 1).replace('_', ' ');
        }
        return itemId;
    }
}
