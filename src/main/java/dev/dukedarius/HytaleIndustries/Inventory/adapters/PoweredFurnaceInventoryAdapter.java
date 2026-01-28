package dev.dukedarius.HytaleIndustries.Inventory.adapters;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import dev.dukedarius.HytaleIndustries.Components.Processing.PoweredFurnaceInventory;
import dev.dukedarius.HytaleIndustries.Inventory.InventoryAdapter;
import dev.dukedarius.HytaleIndustries.Inventory.MachineInventory;
import dev.dukedarius.HytaleIndustries.Inventory.SlotIO;
import dev.dukedarius.HytaleIndustries.Inventory.containers.ContainerMachineInventory;
import dev.dukedarius.HytaleIndustries.HytaleIndustriesPlugin;

import java.util.Collections;
import java.util.List;

public class PoweredFurnaceInventoryAdapter implements InventoryAdapter {
    @Override
    public List<MachineInventory> adapt(World world, Store<ChunkStore> store, int x, int y, int z) {
        WorldChunk chunk = world.getChunkIfInMemory(com.hypixel.hytale.math.util.ChunkUtil.indexChunkFromBlock(x, z));
        if (chunk == null) {
            chunk = world.getChunkIfLoaded(com.hypixel.hytale.math.util.ChunkUtil.indexChunkFromBlock(x, z));
        }
        if (chunk == null) return Collections.emptyList();

        var entity = chunk.getBlockComponentEntity(x & 31, y, z & 31);
        if (entity == null) return Collections.emptyList();

        PoweredFurnaceInventory inv = store.getComponent(entity, HytaleIndustriesPlugin.INSTANCE.getPoweredFurnaceInventoryType());
        if (inv == null) return Collections.emptyList();
        ensure(inv);

        var handler = new ContainerMachineInventory(
                new com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer(inv.input, inv.output),
                slot -> slot == 0 ? SlotIO.INPUT : (slot == 1 ? SlotIO.OUTPUT : SlotIO.NONE)
        );
        return Collections.singletonList(handler);
    }

    private static void ensure(PoweredFurnaceInventory inv) {
        if (inv.input == null || inv.input.getCapacity() <= 0) {
            inv.input = new com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer((short) 1);
        }
        if (inv.output == null || inv.output.getCapacity() <= 0) {
            inv.output = new com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer((short) 1);
        }
    }
}
