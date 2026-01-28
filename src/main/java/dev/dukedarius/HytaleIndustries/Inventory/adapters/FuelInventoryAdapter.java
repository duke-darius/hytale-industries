package dev.dukedarius.HytaleIndustries.Inventory.adapters;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import dev.dukedarius.HytaleIndustries.Components.Energy.FuelInventory;
import dev.dukedarius.HytaleIndustries.Inventory.InventoryAdapter;
import dev.dukedarius.HytaleIndustries.Inventory.MachineInventory;
import dev.dukedarius.HytaleIndustries.Inventory.SlotIO;
import dev.dukedarius.HytaleIndustries.Inventory.containers.ContainerMachineInventory;

import java.util.Collections;
import java.util.List;

public class FuelInventoryAdapter implements InventoryAdapter {
    @Override
    public List<MachineInventory> adapt(World world, Store<ChunkStore> store, int x, int y, int z) {
        WorldChunk chunk = world.getChunkIfInMemory(com.hypixel.hytale.math.util.ChunkUtil.indexChunkFromBlock(x, z));
        if (chunk == null) {
            chunk = world.getChunkIfLoaded(com.hypixel.hytale.math.util.ChunkUtil.indexChunkFromBlock(x, z));
        }
        if (chunk == null) return Collections.emptyList();

        var entity = chunk.getBlockComponentEntity(x & 31, y, z & 31);
        if (entity == null) return Collections.emptyList();

        FuelInventory fuel = store.getComponent(entity, dev.dukedarius.HytaleIndustries.HytaleIndustriesPlugin.INSTANCE.getFuelInventoryType());
        if (fuel == null) return Collections.emptyList();

        ItemContainer container = fuel.fuelContainer;
        if (container == null) return Collections.emptyList();

        return Collections.singletonList(new ContainerMachineInventory(container, slot -> slot == 0 ? SlotIO.INPUT : SlotIO.NONE));
    }
}
