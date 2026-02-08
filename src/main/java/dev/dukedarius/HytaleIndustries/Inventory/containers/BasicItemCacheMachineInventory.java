package dev.dukedarius.HytaleIndustries.Inventory.containers;

import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import dev.dukedarius.HytaleIndustries.Inventory.SlotIO;

import java.util.function.IntFunction;

/**
 * Marker MachineInventory wrapper for the Basic Item Cache.
 * Allows pipe logic to detect this inventory and apply special rules
 * (like 16x stack capacity for inserts).
 */
public class BasicItemCacheMachineInventory extends ContainerMachineInventory {
    public BasicItemCacheMachineInventory(ItemContainer container, IntFunction<SlotIO> slotMapper) {
        super(container, slotMapper);
    }
}
