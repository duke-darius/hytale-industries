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
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
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

    public BasicItemCacheSystem(ComponentType<ChunkStore, BasicItemCacheComponent> cacheType) {
        this.cacheType = cacheType;
        // Match block entities that have our cache component attached; we
        // also require BlockStateInfo + BlockChunk so we only tick actual
        // block-state entities, mirroring other block systems.
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
        if (cache == null) {
            return;
        }

        ensureContainers(cache);

        SimpleItemContainer input = cache.input;
        SimpleItemContainer output = cache.output;

        // 1) Detect withdrawals from the output slot since last tick.
        int actualOutputCount = 0;
        ItemStack outStack = output.getItemStack((short) 0);
        if (outStack != null && !ItemStack.isEmpty(outStack) &&
                cache.cachedItemId != null && cache.cachedItemId.equals(outStack.getItemId())) {
            actualOutputCount = outStack.getQuantity();
        }

        if (cache.cachedItemId != null && cache.cachedCount > 0 && cache.lastOutputCount > actualOutputCount) {
            int delta = cache.lastOutputCount - actualOutputCount;
            long newCount = cache.cachedCount - delta;
            if (newCount < 0) newCount = 0;
            cache.cachedCount = newCount;
        }

        if (cache.cachedCount <= 0) {
            cache.cachedCount = 0;
        }

        // 2) Consume items from the input slot into the logical counter.
        ItemStack inStack = input.getItemStack((short) 0);
        if (inStack != null && !ItemStack.isEmpty(inStack)) {
            String inId = inStack.getItemId();
            if (inId != null) {
                // If the cache is empty or uninitialized, adopt this item id.
                if (cache.cachedItemId == null || cache.cachedCount <= 0) {
                    cache.cachedItemId = inId;
                    cache.cachedCount = 0L;
                    cache.maxCount = computeMaxCount(inStack);
                }

                // Only accept items that match the cached item id.
                if (cache.cachedItemId != null && cache.cachedItemId.equals(inId)) {
                    if (cache.maxCount <= 0) {
                        cache.maxCount = computeMaxCount(inStack);
                    }
                    long space = cache.maxCount - cache.cachedCount;
                    if (space > 0) {
                        int qty = inStack.getQuantity();
                        long toAdd = Math.min(space, qty);
                        if (toAdd > 0) {
                            cache.cachedCount += toAdd;
                            int remaining = (int) (qty - toAdd);
                            if (remaining <= 0) {
                                input.setItemStackForSlot((short) 0, ItemStack.EMPTY);
                            } else {
                                input.setItemStackForSlot((short) 0, inStack.withQuantity(remaining));
                            }
                        }
                    }
                }
            }
        }

        // 3) Rebuild the output slot from the logical state.
        if (cache.cachedItemId == null || cache.cachedCount <= 0) {
            cache.cachedItemId = null;
            cache.cachedCount = 0L;
            cache.maxCount = 0L;
            cache.lastOutputCount = 0;
            output.setItemStackForSlot((short) 0, ItemStack.EMPTY);
        } else {
            int maxStack = resolveBaseMaxStack(cache);
            if (maxStack <= 0) {
                maxStack = 64; // Sensible default fallback.
            }
            int desired = (int) Math.min(cache.cachedCount, (long) maxStack);
            if (desired <= 0) {
                output.setItemStackForSlot((short) 0, ItemStack.EMPTY);
                cache.lastOutputCount = 0;
            } else {
                ItemStack newOut = new ItemStack(cache.cachedItemId, desired);
                output.setItemStackForSlot((short) 0, newOut);
                cache.lastOutputCount = desired;
            }
        }

        Ref<ChunkStore> ref = chunk.getReferenceTo(index);
        buffer.replaceComponent(ref, cacheType, cache);
    }

    private static void ensureContainers(BasicItemCacheComponent cache) {
        // Migrate legacy single-container data if necessary.
        if ((cache.input == null || cache.input.getCapacity() <= 0) &&
                cache.inventory != null && cache.inventory.getCapacity() > 0) {
            cache.input = cache.inventory;
        }
        if (cache.input == null || cache.input.getCapacity() <= 0) {
            cache.input = new SimpleItemContainer((short) 1);
        }
        if (cache.output == null || cache.output.getCapacity() <= 0) {
            cache.output = new SimpleItemContainer((short) 1);
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
                    .log("[BasicItemCache] Failed to compute maxCount from stack; using default.");
        }
        return 16L * 64L;
    }

    private static int resolveBaseMaxStack(@Nonnull BasicItemCacheComponent cache) {
        if (cache.cachedItemId == null) return 0;
        try {
            Item item = Item.getAssetMap().getAsset(cache.cachedItemId);
            if (item != null) {
                return item.getMaxStack();
            }
        } catch (Throwable t) {
            HytaleIndustriesPlugin.LOGGER.atWarning().withCause(t)
                    .log("[BasicItemCache] Failed to resolve Item for id '%s'", cache.cachedItemId);
        }
        // Fallback: derive from maxCount if possible (maxCount ~= 16 * baseMaxStack).
        if (cache.maxCount > 0) {
            long approx = cache.maxCount / 16L;
            if (approx > 0 && approx <= Integer.MAX_VALUE) {
                return (int) approx;
            }
        }
        return 64;
    }
}