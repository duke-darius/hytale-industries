package dev.dukedarius.HytaleIndustries.Systems;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import dev.dukedarius.HytaleIndustries.Inventory.containers.CacheItemContainer;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.modules.block.BlockModule.BlockStateInfo;
import dev.dukedarius.HytaleIndustries.Components.Storage.BasicItemCacheComponent;
import dev.dukedarius.HytaleIndustries.HytaleIndustriesPlugin;

import javax.annotation.Nonnull;

/**
 * ECS system that drives the logical behaviour of the Basic Item Cache.
 *
 * Responsibilities per tick:
 * - Consume items from the INPUT slot into a logical counter (cachedCount).
 * - Detect withdrawals from the OUTPUT slot and subtract them from cachedCount.
 * - Rebuild the OUTPUT slot so it exposes up to a normal stack of the
 *   cached item while any items remain cached.
 */
public class BasicItemCacheSystem extends EntityTickingSystem<ChunkStore> {
    private final ComponentType<ChunkStore, BasicItemCacheComponent> cacheType;
    private final Query<ChunkStore> query;
    private static final int DEFAULT_MAX_STACK = 1024;

    public BasicItemCacheSystem(ComponentType<ChunkStore, BasicItemCacheComponent> cacheType) {
        this.cacheType = cacheType;
        this.query = Query.and(cacheType,
                BlockStateInfo.getComponentType(),
                BlockChunk.getComponentType());
    }

    @Override
    public Query<ChunkStore> getQuery() {
        return query;
    }

    @Override
    public void tick(float dt,
                     int index,
                     @Nonnull ArchetypeChunk<ChunkStore> chunk,
                     @Nonnull Store<ChunkStore> store,
                     @Nonnull CommandBuffer<ChunkStore> buffer) {
        BasicItemCacheComponent cache = chunk.getComponent(index, cacheType);
        if (cache == null) return;

        ensureContainer(cache);

        CacheItemContainer slot = cache.slot;

        // 1) read slot
        ItemStack stack = slot.getItemStack((short) 0);
        String stackId = (stack != null && !ItemStack.isEmpty(stack)) ? stack.getItemId() : null;
        int stackQty = (stack != null && !ItemStack.isEmpty(stack)) ? stack.getQuantity() : 0;

        if (stackId == null || stackQty <= 0) {
            // Empty slot => reset cache
            cache.cachedItemId = null;
            cache.cachedCount = 0;
            cache.maxCount = 0;
            cache.lastExposedCount = 0;
            cache.slot.setMaxStack(DEFAULT_MAX_STACK);
            cache.slot.setLockedItemId(null);
        } else {
            if (cache.cachedItemId == null || cache.cachedCount <= 0) {
                // Adopt this item
                cache.cachedItemId = stackId;
                cache.maxCount = computeMaxCount(stack);
                cache.slot.setMaxStack((int) Math.min(cache.maxCount, Integer.MAX_VALUE));
                cache.cachedCount = Math.min(cache.maxCount, stackQty);
                cache.slot.setLockedItemId(stackId);
            } else if (cache.cachedItemId.equals(stackId)) {
                // Same item: detect delta vs last exposed
                int prev = cache.lastExposedCount;
                if (stackQty > prev) {
                    long added = stackQty - prev;
                    cache.cachedCount = Math.min(cache.maxCount, cache.cachedCount + added);
                } else if (stackQty < prev) {
                    long taken = prev - stackQty;
                    cache.cachedCount = Math.max(0, cache.cachedCount - taken);
                } else {
                    // unchanged
                }
            } else {
                // Mismatched item while cache active: ignore the foreign stack by restoring cached item
            }
        }

        // 2) rebuild slot from logical cache (overstack up to maxCount)
        if (cache.cachedItemId == null || cache.cachedCount <= 0) {
            cache.cachedItemId = null;
            cache.cachedCount = 0;
            cache.maxCount = 0;
            cache.lastExposedCount = 0;
            cache.slot.setMaxStack(DEFAULT_MAX_STACK);
            cache.slot.setLockedItemId(null);
            slot.setItemStackForSlot((short) 0, ItemStack.EMPTY);
        } else {
            long desired = Math.min(cache.cachedCount, cache.maxCount);
            cache.slot.setMaxStack((int) Math.min(cache.maxCount, Integer.MAX_VALUE));
            cache.slot.setLockedItemId(cache.cachedItemId);
            if (desired <= 0) {
                slot.setItemStackForSlot((short) 0, ItemStack.EMPTY);
                cache.lastExposedCount = 0;
            } else {
                ItemStack newStack = new ItemStack(cache.cachedItemId, (int) desired);
                slot.setItemStackForSlot((short) 0, newStack);
                cache.lastExposedCount = (int) desired;
            }
        }

        Ref<ChunkStore> ref = chunk.getReferenceTo(index);
        buffer.replaceComponent(ref, cacheType, cache);
    }

    private static void ensureContainer(BasicItemCacheComponent cache) {
        if ((cache.slot == null || cache.slot.getCapacity() <= 0)) {
            cache.slot = new dev.dukedarius.HytaleIndustries.Inventory.containers.CacheItemContainer((short) 1, 1024);
        }
    }

    private static long computeMaxCount(@Nonnull ItemStack stack) {
        try {
            var item = stack.getItem();
            if (item != null) {
                int base = item.getMaxStack();
                if (base <= 0) base = 64;
                return 16L * (long) base;
            }
        } catch (Throwable t) {
            HytaleIndustriesPlugin.LOGGER.atWarning().withCause(t)
                    .log("[BasicItemCache] Failed to compute maxCount; defaulting.");
        }
        return 16L * 64L;
    }

    private static int resolveBaseMaxStack(@Nonnull BasicItemCacheComponent cache) {
        if (cache.cachedItemId == null) return 0;
        try {
            Item item = Item.getAssetMap().getAsset(cache.cachedItemId);
            if (item != null) return item.getMaxStack();
        } catch (Throwable t) {
            HytaleIndustriesPlugin.LOGGER.atWarning().withCause(t)
                    .log("[BasicItemCache] Failed to resolve item '%s'", cache.cachedItemId);
        }
        if (cache.maxCount > 0) {
            long approx = cache.maxCount / 16L;
            if (approx > 0 && approx <= Integer.MAX_VALUE) return (int) approx;
        }
        return 64;
    }
   
}