package dev.dukedarius.HytaleIndustries.Inventory.adapters;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import dev.dukedarius.HytaleIndustries.Components.Processing.AlloySmelterInventory;
import dev.dukedarius.HytaleIndustries.Inventory.InventoryAdapter;
import dev.dukedarius.HytaleIndustries.Inventory.MachineInventory;
import dev.dukedarius.HytaleIndustries.Inventory.SlotIO;
import dev.dukedarius.HytaleIndustries.Inventory.containers.ContainerMachineInventory;
import dev.dukedarius.HytaleIndustries.HytaleIndustriesPlugin;

import java.util.Collections;
import java.util.List;

/**
 * Exposes the Alloy Smelter inventory to pipes.
 * The first two slots are treated as inputs, the final slot as output.
 */
public class AlloySmelterInventoryAdapter implements InventoryAdapter {
    @Override
    public List<MachineInventory> adapt(World world, Store<ChunkStore> store, int x, int y, int z) {
        WorldChunk chunk = world.getChunkIfInMemory(com.hypixel.hytale.math.util.ChunkUtil.indexChunkFromBlock(x, z));
        if (chunk == null) chunk = world.getChunkIfLoaded(com.hypixel.hytale.math.util.ChunkUtil.indexChunkFromBlock(x, z));
        if (chunk == null) return Collections.emptyList();

        var entity = chunk.getBlockComponentEntity(x & 31, y, z & 31);
        if (entity == null) return Collections.emptyList();

        AlloySmelterInventory inv = store.getComponent(entity, HytaleIndustriesPlugin.INSTANCE.getAlloySmelterInventoryType());
        if (inv == null) return Collections.emptyList();

        ensure(inv);

        short inputASlots = inv.inputA.getCapacity();
        short inputBSlots = inv.inputB.getCapacity();
        short totalInputSlots = (short) (inputASlots + inputBSlots);
        short outputSlots = inv.output.getCapacity();

        var combinedInputs = new com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer(inv.inputA, inv.inputB);
        var handler = new ContainerMachineInventory(
                new com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer(combinedInputs, inv.output),
                slot -> {
                    if (slot < totalInputSlots) return SlotIO.INPUT;
                    if (slot < totalInputSlots + outputSlots) return SlotIO.OUTPUT;
                    return SlotIO.NONE;
                }
        );
        return Collections.singletonList(handler);
    }

    private static void ensure(AlloySmelterInventory inv) {
        if (inv.inputA == null || inv.inputA.getCapacity() <= 0) {
            inv.inputA = new com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer((short) 1);
        }
        if (inv.inputB == null || inv.inputB.getCapacity() <= 0) {
            inv.inputB = new com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer((short) 1);
        }
        if (inv.output == null || inv.output.getCapacity() <= 0) {
            inv.output = new com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer((short) 1);
        }
    }
}
