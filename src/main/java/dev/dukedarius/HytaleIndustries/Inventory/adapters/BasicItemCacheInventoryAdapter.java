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

        var blockType = chunk.getBlockType(x & 31, y, z & 31);
        String blockId = blockType != null ? blockType.getId() : "null";
        HytaleIndustriesPlugin.LOGGER.atInfo().log("[CacheAdapter] adapting (%d,%d,%d) block=%s", x, y, z, blockId);

        BasicItemCacheComponent cache = store.getComponent(entity,
                HytaleIndustriesPlugin.INSTANCE.getBasicItemCacheComponentType());
        if (cache == null) return Collections.emptyList();
        ensureContainer(cache);

        MachineInventory inv = new BasicItemCacheMachineInventory(
                cache.slot,
                slot -> SlotIO.BOTH
        );
        return Collections.singletonList(inv);
    }
    private static void ensureContainer(BasicItemCacheComponent cache) {
        if (cache.slot == null || cache.slot.getCapacity() <= 0) {
            cache.slot = new dev.dukedarius.HytaleIndustries.Inventory.containers.CacheItemContainer((short) 1, 1024);
        }
    }
}
