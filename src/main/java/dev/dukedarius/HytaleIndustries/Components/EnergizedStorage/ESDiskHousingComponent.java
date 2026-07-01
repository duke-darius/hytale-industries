package dev.dukedarius.HytaleIndustries.Components.EnergizedStorage;

import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

import java.util.ArrayList;
import java.util.List;

public class ESDiskHousingComponent implements Component<ChunkStore> {
    public static final int DISK_SLOT_COUNT = 8;
    public static final int ITEMS_PER_DISK = 1024;
    public static final String DISK_ITEM_ID = "HytaleIndustries_ES_1kDisk";

    /** 8 slots for inserting/removing disk items */
    public SimpleItemContainer diskSlots = new SimpleItemContainer((short) DISK_SLOT_COUNT);

    /** Transient bitmask for display change detection */
    public transient int lastDisplayMask = -1;

    /** Per-disk item storage — unbounded list, each entry is a unique item type with total count as quantity */
    @SuppressWarnings("unchecked")
    public List<ItemStack>[] diskStorage = new ArrayList[DISK_SLOT_COUNT];

    public ESDiskHousingComponent() {
        for (int i = 0; i < DISK_SLOT_COUNT; i++) diskStorage[i] = new ArrayList<>();
    }

    public boolean isDiskActive(int slot) {
        ItemStack stack = diskSlots.getItemStack((short) slot);
        return stack != null && !ItemStack.isEmpty(stack) && DISK_ITEM_ID.equals(stack.getItemId());
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
        return (long) getActiveDiskCount() * ITEMS_PER_DISK;
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
            long space = ITEMS_PER_DISK - used;
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

    // --- serialization: convert List<ItemStack> to/from SimpleItemContainer for CODEC ---

    private SimpleItemContainer listToContainer(List<ItemStack> items) {
        if (items.isEmpty()) return new SimpleItemContainer((short) 1);
        SimpleItemContainer c = new SimpleItemContainer((short) items.size());
        for (int i = 0; i < items.size(); i++) {
            c.setItemStackForSlot((short) i, items.get(i));
        }
        return c;
    }

    private List<ItemStack> containerToList(SimpleItemContainer c) {
        List<ItemStack> list = new ArrayList<>();
        if (c == null) return list;
        for (short i = 0; i < c.getCapacity(); i++) {
            ItemStack s = c.getItemStack(i);
            if (s != null && !ItemStack.isEmpty(s)) list.add(s);
        }
        return list;
    }

    public static final BuilderCodec<ESDiskHousingComponent> CODEC = BuilderCodec.builder(
                    ESDiskHousingComponent.class, ESDiskHousingComponent::new)
            .append(new KeyedCodec<>("DiskSlots", SimpleItemContainer.CODEC),
                    (o, v) -> o.diskSlots = v != null ? v : new SimpleItemContainer((short) DISK_SLOT_COUNT),
                    o -> o.diskSlots)
            .add()
            .append(new KeyedCodec<>("Disk0", SimpleItemContainer.CODEC),
                    (o, v) -> o.diskStorage[0] = o.containerToList(v), o -> o.listToContainer(o.diskStorage[0]))
            .add()
            .append(new KeyedCodec<>("Disk1", SimpleItemContainer.CODEC),
                    (o, v) -> o.diskStorage[1] = o.containerToList(v), o -> o.listToContainer(o.diskStorage[1]))
            .add()
            .append(new KeyedCodec<>("Disk2", SimpleItemContainer.CODEC),
                    (o, v) -> o.diskStorage[2] = o.containerToList(v), o -> o.listToContainer(o.diskStorage[2]))
            .add()
            .append(new KeyedCodec<>("Disk3", SimpleItemContainer.CODEC),
                    (o, v) -> o.diskStorage[3] = o.containerToList(v), o -> o.listToContainer(o.diskStorage[3]))
            .add()
            .append(new KeyedCodec<>("Disk4", SimpleItemContainer.CODEC),
                    (o, v) -> o.diskStorage[4] = o.containerToList(v), o -> o.listToContainer(o.diskStorage[4]))
            .add()
            .append(new KeyedCodec<>("Disk5", SimpleItemContainer.CODEC),
                    (o, v) -> o.diskStorage[5] = o.containerToList(v), o -> o.listToContainer(o.diskStorage[5]))
            .add()
            .append(new KeyedCodec<>("Disk6", SimpleItemContainer.CODEC),
                    (o, v) -> o.diskStorage[6] = o.containerToList(v), o -> o.listToContainer(o.diskStorage[6]))
            .add()
            .append(new KeyedCodec<>("Disk7", SimpleItemContainer.CODEC),
                    (o, v) -> o.diskStorage[7] = o.containerToList(v), o -> o.listToContainer(o.diskStorage[7]))
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
