package dev.dukedarius.HytaleIndustries.Components.EnergizedStorage;

import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonInt64;
import org.bson.BsonString;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ESDiskHousingComponent implements Component<ChunkStore> {
    public static final int DISK_SLOT_COUNT = 8;

    private static final Map<String, Integer> DISK_CAPACITIES = new LinkedHashMap<>();
    static {
        DISK_CAPACITIES.put("HytaleIndustries_ES_1kDisk", 1024);
        DISK_CAPACITIES.put("HytaleIndustries_ES_4kDisk", 4096);
        DISK_CAPACITIES.put("HytaleIndustries_ES_16kDisk", 16384);
        DISK_CAPACITIES.put("HytaleIndustries_ES_64kDisk", 65536);
        DISK_CAPACITIES.put("HytaleIndustries_ES_256kDisk", 262144);
        DISK_CAPACITIES.put("HytaleIndustries_ES_1MDisk", 1048576);
    }

    private static final Map<String, String> DISK_MODEL_ASSETS = new LinkedHashMap<>();
    static {
        DISK_MODEL_ASSETS.put("HytaleIndustries_ES_1kDisk", "HytaleIndustries_ESDisk_1kAsset");
        DISK_MODEL_ASSETS.put("HytaleIndustries_ES_4kDisk", "HytaleIndustries_ESDisk_4kAsset");
        DISK_MODEL_ASSETS.put("HytaleIndustries_ES_16kDisk", "HytaleIndustries_ESDisk_16kAsset");
        DISK_MODEL_ASSETS.put("HytaleIndustries_ES_64kDisk", "HytaleIndustries_ESDisk_64kAsset");
        DISK_MODEL_ASSETS.put("HytaleIndustries_ES_256kDisk", "HytaleIndustries_ESDisk_256kAsset");
        DISK_MODEL_ASSETS.put("HytaleIndustries_ES_1MDisk", "HytaleIndustries_ESDisk_1MAsset");
    }

    /** @deprecated Use DISK_CAPACITIES instead */
    public static final int ITEMS_PER_DISK = 1024;

    /** 8 slots for inserting/removing disk items */
    public SimpleItemContainer diskSlots = new SimpleItemContainer((short) DISK_SLOT_COUNT);

    /** Transient bitmask for display change detection */
    public transient int lastDisplayMask = -1;
    /** Transient counter for throttled network state checks */
    public transient int networkCheckCounter = 0;
    /** Transient last known network online state: -1=unknown, 0=offline, 1=online */
    public transient int lastNetworkOnline = -1;

    /** Per-disk item storage — unbounded list, each entry is a unique item type with total count as quantity */
    @SuppressWarnings("unchecked")
    public List<ItemStack>[] diskStorage = new ArrayList[DISK_SLOT_COUNT];

    /** Tracks which slots had active disks last tick, to detect insert/remove transitions */
    public transient int lastSlotMask = 0;
    /** Set true after first syncDiskSlotTransitions — prevents CODEC getter from wiping unloaded data */
    public transient boolean dataLoaded = false;

    private static final String META_KEY = "ESStoredItems";

    public ESDiskHousingComponent() {
        for (int i = 0; i < DISK_SLOT_COUNT; i++) diskStorage[i] = new ArrayList<>();
    }

    /**
     * Call each tick to detect disk insertions/removals and sync metadata.
     * When a disk is inserted: load its stored items from ItemStack metadata.
     * When a disk is removed: save stored items into the ItemStack metadata before it leaves.
     */
    public void syncDiskSlotTransitions() {
        int currentMask = 0;
        for (int i = 0; i < DISK_SLOT_COUNT; i++) {
            if (isDiskActive(i)) currentMask |= (1 << i);
        }

        for (int i = 0; i < DISK_SLOT_COUNT; i++) {
            boolean wasActive = (lastSlotMask & (1 << i)) != 0;
            boolean isActive = (currentMask & (1 << i)) != 0;

            if (!wasActive && isActive) {
                // Disk was just inserted — load items from its metadata
                loadFromDiskMetadata(i);
            } else if (wasActive && !isActive) {
                // Disk was just removed — items already saved (see saveToDiskMetadata)
                diskStorage[i].clear();
            }
        }
        lastSlotMask = currentMask;
        dataLoaded = true;
    }

    /** Save items from diskStorage[slot] into the disk ItemStack's metadata */
    public void saveToDiskMetadata(int slot) {
        ItemStack disk = diskSlots.getItemStack((short) slot);
        if (disk == null || ItemStack.isEmpty(disk)) return;

        BsonArray arr = new BsonArray();
        for (ItemStack stored : diskStorage[slot]) {
            BsonDocument entry = new BsonDocument();
            entry.put("id", new BsonString(stored.getItemId()));
            entry.put("qty", new BsonInt64(stored.getQuantity()));
            // Preserve nested metadata if present
            if (stored.getMetadata() != null && !stored.getMetadata().isEmpty()) {
                entry.put("meta", stored.getMetadata());
            }
            arr.add(entry);
        }

        BsonDocument meta = disk.getMetadata();
        if (meta == null) meta = new BsonDocument();
        meta.put(META_KEY, arr);
        diskSlots.setItemStackForSlot((short) slot, disk.withMetadata(meta));
    }

    /** Load items from the disk ItemStack's metadata into diskStorage[slot] */
    public void loadFromDiskMetadata(int slot) {
        diskStorage[slot].clear();
        ItemStack disk = diskSlots.getItemStack((short) slot);
        if (disk == null || ItemStack.isEmpty(disk)) return;

        BsonDocument meta = disk.getMetadata();
        if (meta == null || !meta.containsKey(META_KEY)) return;

        try {
            BsonArray arr = meta.getArray(META_KEY);
            for (int i = 0; i < arr.size(); i++) {
                BsonDocument entry = arr.get(i).asDocument();
                String itemId = entry.getString("id").getValue();
                long qty = entry.getInt64("qty").getValue();
                ItemStack restored = new ItemStack(itemId, (int) qty);
                // Restore nested metadata if present
                if (entry.containsKey("meta")) {
                    restored = restored.withMetadata(entry.getDocument("meta"));
                }
                diskStorage[slot].add(restored);
            }
        } catch (Throwable ignored) {}
    }

    /** Save ALL active disk slots to their metadata (call before housing breaks) */
    public void saveAllDisksMetadata() {
        for (int i = 0; i < DISK_SLOT_COUNT; i++) {
            if (isDiskActive(i)) saveToDiskMetadata(i);
        }
    }

    public boolean isDiskActive(int slot) {
        ItemStack stack = diskSlots.getItemStack((short) slot);
        return stack != null && !ItemStack.isEmpty(stack) && DISK_CAPACITIES.containsKey(stack.getItemId());
    }

    public int getDiskCapacity(int slot) {
        ItemStack stack = diskSlots.getItemStack((short) slot);
        if (stack == null || ItemStack.isEmpty(stack)) return 0;
        return DISK_CAPACITIES.getOrDefault(stack.getItemId(), 0);
    }

    public String getDiskModelAssetId(int slot) {
        ItemStack stack = diskSlots.getItemStack((short) slot);
        if (stack == null || ItemStack.isEmpty(stack)) return null;
        return DISK_MODEL_ASSETS.get(stack.getItemId());
    }

    public static boolean isStorageDisk(String itemId) {
        return DISK_CAPACITIES.containsKey(itemId);
    }

    public int getActiveDiskCount() {
        int count = 0;
        for (int i = 0; i < DISK_SLOT_COUNT; i++) if (isDiskActive(i)) count++;
        return count;
    }

    public long getDiskUsed(int disk) {
        long total = 0;
        for (ItemStack s : diskStorage[disk]) total += s.getQuantity();
        return total;
    }

    public long getTotalCapacity() {
        long total = 0;
        for (int i = 0; i < DISK_SLOT_COUNT; i++) {
            if (isDiskActive(i)) total += getDiskCapacity(i);
        }
        return total;
    }

    public long getTotalStored() {
        long total = 0;
        for (int i = 0; i < DISK_SLOT_COUNT; i++) {
            if (isDiskActive(i)) total += getDiskUsed(i);
        }
        return total;
    }

    /** Aggregate all items across all active disks, merging stackable entries */
    public List<ItemStack> aggregateItems() {
        List<ItemStack> result = new ArrayList<>();
        for (int d = 0; d < DISK_SLOT_COUNT; d++) {
            if (!isDiskActive(d)) continue;
            for (ItemStack stack : diskStorage[d]) {
                mergeInto(result, stack);
            }
        }
        return result;
    }

    /** Insert an ItemStack preserving metadata. Returns amount actually inserted. */
    public int insertItem(ItemStack toInsert) {
        if (toInsert == null || ItemStack.isEmpty(toInsert)) return 0;
        int remaining = toInsert.getQuantity();
        for (int d = 0; d < DISK_SLOT_COUNT && remaining > 0; d++) {
            if (!isDiskActive(d)) continue;
            long used = getDiskUsed(d);
            long space = getDiskCapacity(d) - used;
            if (space <= 0) continue;

            int canInsert = (int) Math.min(remaining, space);

            // Try to merge with existing entry of same type
            boolean merged = false;
            for (int e = 0; e < diskStorage[d].size(); e++) {
                ItemStack existing = diskStorage[d].get(e);
                if (existing.isStackableWith(toInsert)) {
                    diskStorage[d].set(e, existing.withQuantity(existing.getQuantity() + canInsert));
                    merged = true;
                    break;
                }
            }
            if (!merged) {
                diskStorage[d].add(toInsert.withQuantity(canInsert));
            }
            remaining -= canInsert;
        }
        return toInsert.getQuantity() - remaining;
    }

    /** Extract items by ID. Returns actual ItemStacks preserving metadata. */
    public List<ItemStack> extractItem(String itemId, int amount) {
        List<ItemStack> extracted = new ArrayList<>();
        int remaining = amount;
        for (int d = 0; d < DISK_SLOT_COUNT && remaining > 0; d++) {
            if (!isDiskActive(d)) continue;
            var iter = diskStorage[d].iterator();
            while (iter.hasNext() && remaining > 0) {
                ItemStack stack = iter.next();
                if (!itemId.equals(stack.getItemId())) continue;
                int take = Math.min(remaining, stack.getQuantity());
                extracted.add(stack.withQuantity(take));
                int left = stack.getQuantity() - take;
                if (left <= 0) {
                    iter.remove();
                } else {
                    // Can't modify during iteration with iterator, use index
                    int idx = diskStorage[d].indexOf(stack);
                    if (idx >= 0) diskStorage[d].set(idx, stack.withQuantity(left));
                }
                remaining -= take;
            }
        }
        return extracted;
    }

    public static final BuilderCodec<ESDiskHousingComponent> CODEC = BuilderCodec.builder(
                    ESDiskHousingComponent.class, ESDiskHousingComponent::new)
            .append(new KeyedCodec<>("DiskSlots", SimpleItemContainer.CODEC),
                    (o, v) -> o.diskSlots = v != null ? v : new SimpleItemContainer((short) DISK_SLOT_COUNT),
                    o -> { if (o.dataLoaded) o.saveAllDisksMetadata(); return o.diskSlots; })
            .add()
            .build();

    @Override
    @SuppressWarnings("unchecked")
    public ESDiskHousingComponent clone() {
        ESDiskHousingComponent copy = new ESDiskHousingComponent();
        copy.diskSlots = (SimpleItemContainer) this.diskSlots.clone();
        for (int i = 0; i < DISK_SLOT_COUNT; i++) {
            copy.diskStorage[i] = new ArrayList<>(this.diskStorage[i]);
        }
        copy.dataLoaded = this.dataLoaded;
        copy.lastSlotMask = this.lastSlotMask;
        return copy;
    }

    private static void mergeInto(List<ItemStack> list, ItemStack stack) {
        for (int i = 0; i < list.size(); i++) {
            ItemStack existing = list.get(i);
            if (existing.isStackableWith(stack)) {
                list.set(i, existing.withQuantity(existing.getQuantity() + stack.getQuantity()));
                return;
            }
        }
        list.add(stack);
    }
}
