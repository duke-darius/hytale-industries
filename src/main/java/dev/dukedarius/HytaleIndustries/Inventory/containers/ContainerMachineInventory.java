package dev.dukedarius.HytaleIndustries.Inventory.containers;

import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import dev.dukedarius.HytaleIndustries.Inventory.MachineInventory;
import dev.dukedarius.HytaleIndustries.Inventory.SlotIO;

import java.util.function.IntFunction;

public class ContainerMachineInventory implements MachineInventory {
    private final ItemContainer container;
    private final IntFunction<SlotIO> slotMapper;

    public ContainerMachineInventory(ItemContainer container, IntFunction<SlotIO> slotMapper) {
        this.container = container;
        this.slotMapper = slotMapper;
    }

    @Override
    public ItemContainer getContainer() {
        return container;
    }

    @Override
    public SlotIO getSlotIO(int slotIndex) {
        return slotMapper.apply(slotIndex);
    }
}
