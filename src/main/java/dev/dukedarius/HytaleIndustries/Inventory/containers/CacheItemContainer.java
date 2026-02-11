package dev.dukedarius.HytaleIndustries.Inventory.containers;

import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import com.hypixel.hytale.server.core.inventory.container.filter.FilterActionType;
import com.hypixel.hytale.server.core.inventory.container.filter.FilterType;
import com.hypixel.hytale.server.core.inventory.container.filter.SlotFilter;
import com.hypixel.hytale.server.core.inventory.transaction.ClearTransaction;
import org.checkerframework.checker.nullness.compatqual.NullableDecl;

import java.util.function.Function;
import java.util.function.Supplier;
import dev.dukedarius.HytaleIndustries.HytaleIndustriesPlugin;

/**
 * Simple 1+ slot container that allows configuring the maximum stack size
 * per slot (uniform across slots). Useful for overstacking logical caches.
 */
public class CacheItemContainer extends ItemContainer {
    private final ItemStack[] slots;
    private final short capacity;
    public static final int DEFAULT_MAX_STACK = 1024;
    private int maxStack;
    private String lockedItemId;

    public CacheItemContainer(short slots, int maxStack) {
        super();
        short cap = (short) Math.max(1, slots);
        this.capacity = cap;
        this.slots = new ItemStack[cap];
        for (int i = 0; i < cap; i++) {
            this.slots[i] = ItemStack.EMPTY;
        }
        this.maxStack = Math.max(1, maxStack > 0 ? maxStack : DEFAULT_MAX_STACK);
    }

    public void setMaxStack(int maxStack) {
        this.maxStack = Math.max(1, maxStack);
    }

    public void setLockedItemId(String itemId) {
        this.lockedItemId = itemId;
    }

    public String getLockedItemId() {
        return lockedItemId;
    }

    public int getMaxStackForSlot(short slot) {
        return maxStack;
    }

    public static CacheItemContainer from(SimpleItemContainer base, int maxStack) {
        CacheItemContainer cc = new CacheItemContainer((short) Math.max(1, base.getCapacity()),
                maxStack > 0 ? maxStack : DEFAULT_MAX_STACK);
        for (short i = 0; i < base.getCapacity(); i++) {
            cc.setItemStackForSlot(i, base.getItemStack(i));
        }
        return cc;
    }

    public SimpleItemContainer toSimple() {
        short cap = (short) Math.max(1, getCapacity());
        SimpleItemContainer s = new SimpleItemContainer(cap);
        for (short i = 0; i < cap; i++) {
            s.setItemStackForSlot(i, getItemStack(i));
        }
        return s;
    }
    @Override
    public ItemContainer clone() {
        CacheItemContainer copy = new CacheItemContainer(getCapacity(), maxStack);
        copy.lockedItemId = this.lockedItemId;
        for (short i = 0; i < getCapacity(); i++) {
            copy.setItemStackForSlot(i, getItemStack(i));
        }
        return copy;
    }


    @Override
    public short getCapacity() {
        return capacity;
    }

    @Override
    public void setGlobalFilter(FilterType filterType) {
        // not used
    }

    @Override
    public void setSlotFilter(FilterActionType filterActionType, short i, SlotFilter slotFilter) {
        // not used
    }

    @Override
    protected <V> V readAction(Supplier<V> supplier) {
        return supplier.get();
    }

    @Override
    protected <X, V> V readAction(Function<X, V> function, X x) {
        return function.apply(x);
    }

    @Override
    protected <V> V writeAction(Supplier<V> supplier) {
        return supplier.get();
    }

    @Override
    protected <X, V> V writeAction(Function<X, V> function, X x) {
        return function.apply(x);
    }

    @Override
    protected ClearTransaction internal_clear() {
        for (short i = 0; i < capacity; i++) {
            slots[i] = ItemStack.EMPTY;
        }
        return null;
    }

    @NullableDecl
    @Override
    protected ItemStack internal_getSlot(short i) {
        return slots[i];
    }

    @NullableDecl
    @Override
    protected ItemStack internal_setSlot(short i, ItemStack itemStack) {
        ItemStack prev = slots[i];
        slots[i] = itemStack == null ? ItemStack.EMPTY : itemStack;
        HytaleIndustriesPlugin.LOGGER.atInfo().log(
                "[CacheItemContainer] setSlot idx=%d prev=%s(%d) new=%s(%d) locked=%s max=%d",
                i,
                prev != null ? prev.getItemId() : "null",
                prev != null && !ItemStack.isEmpty(prev) ? prev.getQuantity() : 0,
                slots[i] != null ? slots[i].getItemId() : "null",
                slots[i] != null && !ItemStack.isEmpty(slots[i]) ? slots[i].getQuantity() : 0,
                lockedItemId,
                maxStack
        );
        return prev;
    }

    @NullableDecl
    @Override
    protected ItemStack internal_removeSlot(short i) {
        ItemStack prev = slots[i];
        slots[i] = ItemStack.EMPTY;
        HytaleIndustriesPlugin.LOGGER.atInfo().log(
                "[CacheItemContainer] removeSlot idx=%d removed=%s(%d) locked=%s",
                i,
                prev != null ? prev.getItemId() : "null",
                prev != null && !ItemStack.isEmpty(prev) ? prev.getQuantity() : 0,
                lockedItemId
        );
        return prev;
    }

    @Override
    protected boolean cantAddToSlot(short slot, ItemStack existing, ItemStack toAdd) {
        if (toAdd != null && !ItemStack.isEmpty(toAdd)) {
            if (lockedItemId != null && !lockedItemId.equals(toAdd.getItemId())) {
                HytaleIndustriesPlugin.LOGGER.atInfo().log(
                        "[CacheItemContainer] deny add idx=%d reason=locked existing=%s(%d) toAdd=%s(%d) locked=%s max=%d",
                        slot,
                        existing != null ? existing.getItemId() : "null",
                        existing != null && !ItemStack.isEmpty(existing) ? existing.getQuantity() : 0,
                        toAdd.getItemId(),
                        toAdd.getQuantity(),
                        lockedItemId,
                        maxStack
                );
                return true;
            }
            int existingQty = existing != null && !ItemStack.isEmpty(existing) ? existing.getQuantity() : 0;
            if (existingQty + toAdd.getQuantity() > maxStack) {
                HytaleIndustriesPlugin.LOGGER.atInfo().log(
                        "[CacheItemContainer] deny add idx=%d reason=overflow existing=%s(%d) toAdd=%s(%d) max=%d locked=%s",
                        slot,
                        existing != null ? existing.getItemId() : "null",
                        existingQty,
                        toAdd.getItemId(),
                        toAdd.getQuantity(),
                        maxStack,
                        lockedItemId
                );
                return true;
            }
        }
        HytaleIndustriesPlugin.LOGGER.atInfo().log(
                "[CacheItemContainer] allow add idx=%d existing=%s(%d) toAdd=%s(%d) locked=%s max=%d",
                slot,
                existing != null ? existing.getItemId() : "null",
                existing != null && !ItemStack.isEmpty(existing) ? existing.getQuantity() : 0,
                toAdd != null ? toAdd.getItemId() : "null",
                toAdd != null && !ItemStack.isEmpty(toAdd) ? toAdd.getQuantity() : 0,
                lockedItemId,
                maxStack
        );
        return false;
    }

    @Override
    protected boolean cantRemoveFromSlot(short i) {
        return false;
    }

    @Override
    protected boolean cantDropFromSlot(short i) {
        return false;
    }

    @Override
    protected boolean cantMoveToSlot(ItemContainer itemContainer, short i) {
        return false;
    }
}
