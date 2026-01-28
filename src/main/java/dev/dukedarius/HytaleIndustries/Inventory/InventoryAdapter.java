package dev.dukedarius.HytaleIndustries.Inventory;

import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.component.Store;

import java.util.List;

public interface InventoryAdapter {
    /**
    * @return list of inventories at the given block position or empty if unsupported.
    */
    List<MachineInventory> adapt(World world, Store<ChunkStore> store, int x, int y, int z);
}
