package dev.dukedarius.HytaleIndustries.Inventory;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class InventoryAdapters {

    private static final CopyOnWriteArrayList<InventoryAdapter> ADAPTERS = new CopyOnWriteArrayList<>();

    private InventoryAdapters() {}

    public static void register(InventoryAdapter adapter) {
        ADAPTERS.add(adapter);
    }

    public static List<MachineInventory> find(World world, Store<ChunkStore> store, int x, int y, int z) {
        if (ADAPTERS.isEmpty()) return Collections.emptyList();
        for (InventoryAdapter adapter : ADAPTERS) {
            List<MachineInventory> result = adapter.adapt(world, store, x, y, z);
            if (result != null && !result.isEmpty()) {
                return result;
            }
        }
        return Collections.emptyList();
    }

    public static List<InventoryAdapter> getAdaptersSnapshot() {
        return new ArrayList<>(ADAPTERS);
    }
}
