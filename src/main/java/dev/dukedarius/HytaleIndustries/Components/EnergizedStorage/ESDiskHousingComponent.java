package dev.dukedarius.HytaleIndustries.Components.EnergizedStorage;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

import java.util.HashMap;
import java.util.Map;

public class ESDiskHousingComponent implements Component<ChunkStore> {
    public static final int DISK_SLOT_COUNT = 8;
    public static final int ITEMS_PER_DISK = 1024;
    public static final String DISK_ITEM_ID = "HytaleIndustries_ES_1kDisk";

    public SimpleItemContainer diskSlots = new SimpleItemContainer((short) DISK_SLOT_COUNT);

    // Per-disk item storage: diskItems[diskIndex] = map of itemId -> count
    public transient Map<String, Long>[] diskItems;

    public ESDiskHousingComponent() {
        initDiskItems();
    }

    @SuppressWarnings("unchecked")
    private void initDiskItems() {
        diskItems = new HashMap[DISK_SLOT_COUNT];
        for (int i = 0; i < DISK_SLOT_COUNT; i++) {
            diskItems[i] = new HashMap<>();
        }
    }

    public int getActiveDiskCount() {
        int count = 0;
        for (short i = 0; i < DISK_SLOT_COUNT; i++) {
            ItemStack stack = diskSlots.getItemStack(i);
            if (stack != null && !ItemStack.isEmpty(stack) && DISK_ITEM_ID.equals(stack.getItemId())) {
                count++;
            }
        }
        return count;
    }

    public boolean isDiskActive(int slot) {
        ItemStack stack = diskSlots.getItemStack((short) slot);
        return stack != null && !ItemStack.isEmpty(stack) && DISK_ITEM_ID.equals(stack.getItemId());
    }

    public long getDiskUsed(int slot) {
        long total = 0;
        for (long v : diskItems[slot].values()) total += v;
        return total;
    }

    public long getTotalCapacity() {
        return (long) getActiveDiskCount() * ITEMS_PER_DISK;
    }

    public long getTotalStored() {
        long total = 0;
        for (int i = 0; i < DISK_SLOT_COUNT; i++) {
            if (isDiskActive(i)) total += getDiskUsed(i);
        }
        return total;
    }

    public Map<String, Long> aggregateItems() {
        Map<String, Long> result = new HashMap<>();
        for (int i = 0; i < DISK_SLOT_COUNT; i++) {
            if (!isDiskActive(i)) continue;
            for (var entry : diskItems[i].entrySet()) {
                result.merge(entry.getKey(), entry.getValue(), Long::sum);
            }
        }
        return result;
    }

    /** Insert items into the first disk with space. Returns amount actually inserted. */
    public int insertItem(String itemId, int amount) {
        int remaining = amount;
        for (int i = 0; i < DISK_SLOT_COUNT && remaining > 0; i++) {
            if (!isDiskActive(i)) continue;
            long used = getDiskUsed(i);
            long space = ITEMS_PER_DISK - used;
            if (space <= 0) continue;
            int toInsert = (int) Math.min(remaining, space);
            diskItems[i].merge(itemId, (long) toInsert, Long::sum);
            remaining -= toInsert;
        }
        return amount - remaining;
    }

    /** Extract items from the first disk that has them. Returns amount actually extracted. */
    public int extractItem(String itemId, int amount) {
        int remaining = amount;
        for (int i = 0; i < DISK_SLOT_COUNT && remaining > 0; i++) {
            if (!isDiskActive(i)) continue;
            Long stored = diskItems[i].get(itemId);
            if (stored == null || stored <= 0) continue;
            int toExtract = (int) Math.min(remaining, stored);
            long newCount = stored - toExtract;
            if (newCount <= 0) {
                diskItems[i].remove(itemId);
            } else {
                diskItems[i].put(itemId, newCount);
            }
            remaining -= toExtract;
        }
        return amount - remaining;
    }

    public static final BuilderCodec<ESDiskHousingComponent> CODEC = BuilderCodec.builder(
                    ESDiskHousingComponent.class, ESDiskHousingComponent::new)
            .append(new KeyedCodec<>("DiskSlots", SimpleItemContainer.CODEC),
                    (o, v) -> o.diskSlots = v != null ? v : new SimpleItemContainer((short) DISK_SLOT_COUNT),
                    o -> o.diskSlots)
            .add()
            .build();

    @Override
    @SuppressWarnings("unchecked")
    public ESDiskHousingComponent clone() {
        ESDiskHousingComponent copy = new ESDiskHousingComponent();
        copy.diskSlots = (SimpleItemContainer) this.diskSlots.clone();
        copy.diskItems = new HashMap[DISK_SLOT_COUNT];
        for (int i = 0; i < DISK_SLOT_COUNT; i++) {
            copy.diskItems[i] = new HashMap<>(this.diskItems[i]);
        }
        return copy;
    }
}
