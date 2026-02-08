package dev.dukedarius.HytaleIndustries.Inventory.adapters;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import dev.dukedarius.HytaleIndustries.Components.Storage.BasicItemCacheComponent;
import dev.dukedarius.HytaleIndustries.Inventory.InventoryAdapter;
import dev.dukedarius.HytaleIndustries.Inventory.MachineInventory;
import dev.dukedarius.HytaleIndustries.Inventory.SlotIO;
import dev.dukedarius.HytaleIndustries.Inventory.containers.BasicItemCacheMachineInventory;
import dev.dukedarius.HytaleIndustries.HytaleIndustriesPlugin;

import java.util.Collections;
import java.util.List;

/**
 * Exposes the Basic Item Cache as a 2-slot MachineInventory to pipes and
 * other mods:
 * - Slot 0: input (items inserted here are cached logically).
 * - Slot 1: output (always exposes up to a normal stack of the cached item).
 */
public class BasicItemCacheInventoryAdapter implements InventoryAdapter {
    @Override
    public List<MachineInventory> adapt(World world, Store<ChunkStore> store, int x, int y, int z) {
        WorldChunk chunk = world.getChunkIfInMemory(com.hypixel.hytale.math.util.ChunkUtil.indexChunkFromBlock(x, z));
        if (chunk == null) {
            chunk = world.getChunkIfLoaded(com.hypixel.hytale.math.util.ChunkUtil.indexChunkFromBlock(x, z));
        }
        if (chunk == null) return Collections.emptyList();

        var entity = chunk.getBlockComponentEntity(x & 31, y, z & 31);
        if (entity == null) return Collections.emptyList();

        BasicItemCacheComponent cache = store.getComponent(entity,
                HytaleIndustriesPlugin.INSTANCE.getBasicItemCacheComponentType());
        if (cache == null) return Collections.emptyList();

        ensureContainers(cache);

        short inputSlots = cache.input.getCapacity();
        short outputSlots = cache.output.getCapacity();

        var combined = new com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer(cache.input, cache.output);
        MachineInventory inv = new BasicItemCacheMachineInventory(
                combined,
                slot -> {
                    if (slot < inputSlots) return SlotIO.INPUT;
                    if (slot < inputSlots + outputSlots) return SlotIO.OUTPUT;
                    return SlotIO.NONE;
                }
        );
        return Collections.singletonList(inv);
    }

    private static void ensureContainers(BasicItemCacheComponent cache) {
        // If the new input container is missing but we still have the legacy
        // single "inventory" container, treat that as the input.
        if ((cache.input == null || cache.input.getCapacity() <= 0) &&
                cache.inventory != null && cache.inventory.getCapacity() > 0) {
            cache.input = cache.inventory;
        }
        if (cache.input == null || cache.input.getCapacity() <= 0) {
            cache.input = new com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer((short) 1);
        }
        if (cache.output == null || cache.output.getCapacity() <= 0) {
            cache.output = new com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer((short) 1);
        }
    }
}
