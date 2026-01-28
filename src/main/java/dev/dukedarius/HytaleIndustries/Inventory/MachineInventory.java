package dev.dukedarius.HytaleIndustries.Inventory;

import com.hypixel.hytale.server.core.inventory.container.ItemContainer;

/**
 * Minimal inventory abstraction that pipes can interact with without knowing the concrete machine type.
 * Slots declare their IO role so pipes know where to insert / extract.
 */
public interface MachineInventory {
    ItemContainer getContainer();

    SlotIO getSlotIO(int slotIndex);

    default int getSlotCount() {
        return getContainer() == null ? 0 : getContainer().getCapacity();
    }

    default boolean hasInputSlots() {
        for (int i = 0; i < getSlotCount(); i++) {
            if (getSlotIO(i).allowsInput()) return true;
        }
        return false;
    }

    default boolean hasOutputSlots() {
        for (int i = 0; i < getSlotCount(); i++) {
            if (getSlotIO(i).allowsOutput()) return true;
        }
        return false;
    }
}
