package dev.dukedarius.HytaleIndustries.Inventory.containers;

import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.container.filter.FilterActionType;
import com.hypixel.hytale.server.core.inventory.container.filter.FilterType;
import com.hypixel.hytale.server.core.inventory.container.filter.SlotFilter;
import com.hypixel.hytale.server.core.inventory.transaction.ClearTransaction;
import dev.dukedarius.HytaleIndustries.Components.EnergizedStorage.ESDiskHousingComponent;
import org.checkerframework.checker.nullness.compatqual.NullableDecl;

import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Virtual container that presents ES network items as slots.
 * Slot changes are routed to/from the connected Disk Housings.
 */
public class ESNetworkContainer extends ItemContainer {

    public static final short GRID_SLOTS = 54; // 9x6
    private final ItemStack[] slots;
    private final List<ESDiskHousingComponent> housings;

    public ESNetworkContainer(List<ItemStack> itemIndex, List<ESDiskHousingComponent> housings) {
        super();
        this.housings = housings;
        this.slots = new ItemStack[GRID_SLOTS];
        for (int i = 0; i < GRID_SLOTS; i++) slots[i] = ItemStack.EMPTY;
        populateFromIndex(itemIndex);
    }

    private void populateFromIndex(List<ItemStack> itemIndex) {
        for (int i = 0; i < Math.min(itemIndex.size(), GRID_SLOTS); i++) {
            slots[i] = itemIndex.get(i);
        }
    }

    private static int resolveMaxStack(String itemId) {
        try {
            var item = com.hypixel.hytale.server.core.asset.type.item.config.Item.getAssetMap().getAsset(itemId);
            if (item != null) return Math.max(1, item.getMaxStack());
        } catch (Throwable ignored) {}
        return 64;
    }

    @Override public short getCapacity() { return GRID_SLOTS; }

    @Override public void setGlobalFilter(FilterType f) {}
    @Override public void setSlotFilter(FilterActionType a, short i, SlotFilter s) {}

    @Override protected <V> V readAction(Supplier<V> s) { return s.get(); }
    @Override protected <X, V> V readAction(Function<X, V> f, X x) { return f.apply(x); }
    @Override protected <V> V writeAction(Supplier<V> s) { return s.get(); }
    @Override protected <X, V> V writeAction(Function<X, V> f, X x) { return f.apply(x); }

    @Override
    protected ClearTransaction internal_clear() {
        for (short i = 0; i < GRID_SLOTS; i++) slots[i] = ItemStack.EMPTY;
        return null;
    }

    @NullableDecl
    @Override
    protected ItemStack internal_getSlot(short i) {
        return i >= 0 && i < GRID_SLOTS ? slots[i] : ItemStack.EMPTY;
    }

    @NullableDecl
    @Override
    protected ItemStack internal_setSlot(short i, ItemStack itemStack) {
        if (i < 0 || i >= GRID_SLOTS) return ItemStack.EMPTY;
        ItemStack prev = slots[i];
        ItemStack newStack = itemStack == null ? ItemStack.EMPTY : itemStack;

        String prevId = prev != null && !ItemStack.isEmpty(prev) ? prev.getItemId() : null;
        int prevQty = prev != null && !ItemStack.isEmpty(prev) ? prev.getQuantity() : 0;
        String newId = !ItemStack.isEmpty(newStack) ? newStack.getItemId() : null;
        int newQty = !ItemStack.isEmpty(newStack) ? newStack.getQuantity() : 0;

        // Player removed items from this slot (extraction)
        if (prevId != null && (newId == null || newQty < prevQty)) {
            int toExtract = newId != null && newId.equals(prevId) ? prevQty - newQty : prevQty;
            for (ESDiskHousingComponent h : housings) {
                java.util.List<ItemStack> got = h.extractItem(prevId, toExtract);
                for (ItemStack s : got) toExtract -= s.getQuantity();
                if (toExtract <= 0) break;
            }
        }

        // Player added items to this slot (insertion)
        if (newId != null && (prevId == null || newQty > prevQty)) {
            int toInsert = prevId != null && prevId.equals(newId) ? newQty - prevQty : newQty;
            ItemStack insertStack = newStack.withQuantity(toInsert);
            for (ESDiskHousingComponent h : housings) {
                int took = h.insertItem(insertStack);
                toInsert -= took;
                if (toInsert <= 0) break;
                insertStack = insertStack.withQuantity(toInsert);
            }
        }

        slots[i] = newStack;
        return prev;
    }

    @NullableDecl
    @Override
    protected ItemStack internal_removeSlot(short i) {
        if (i < 0 || i >= GRID_SLOTS) return ItemStack.EMPTY;
        ItemStack prev = slots[i];
        // Route extraction to disk housings
        if (prev != null && !ItemStack.isEmpty(prev)) {
            String itemId = prev.getItemId();
            int qty = prev.getQuantity();
            for (ESDiskHousingComponent h : housings) {
                java.util.List<ItemStack> got = h.extractItem(itemId, qty);
                for (ItemStack s : got) qty -= s.getQuantity();
                if (qty <= 0) break;
            }
        }
        slots[i] = ItemStack.EMPTY;
        return prev;
    }

    @Override
    protected boolean cantAddToSlot(short slot, ItemStack existing, ItemStack toAdd) {
        if (toAdd == null || ItemStack.isEmpty(toAdd)) return false;
        // Check if network has space
        long totalCapacity = 0;
        long totalStored = 0;
        for (ESDiskHousingComponent h : housings) {
            totalCapacity += h.getTotalCapacity();
            totalStored += h.getTotalStored();
        }
        return totalStored + toAdd.getQuantity() > totalCapacity;
    }

    @Override protected boolean cantRemoveFromSlot(short i) { return false; }
    @Override protected boolean cantDropFromSlot(short i) { return false; }
    @Override protected boolean cantMoveToSlot(ItemContainer c, short i) { return false; }

    @Override protected void lockForRead() {}
    @Override protected void unlockForRead() {}
    @Override protected void lockForWrite() {}
    @Override protected void unlockForWrite() {}

    @Override
    public ItemContainer clone() {
        // Snapshot — not a deep clone of network state
        return this;
    }
}
