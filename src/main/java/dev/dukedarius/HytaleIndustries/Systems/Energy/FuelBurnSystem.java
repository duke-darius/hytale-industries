package dev.dukedarius.HytaleIndustries.Systems.Energy;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.protocol.ItemResourceType;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import dev.dukedarius.HytaleIndustries.Components.Energy.FuelInventory;
import dev.dukedarius.HytaleIndustries.Components.Energy.ProducesHE;
import dev.dukedarius.HytaleIndustries.Components.Energy.StoresHE;
import dev.dukedarius.HytaleIndustries.HytaleIndustriesPlugin;

/**
 * Burns fuel items into HE production per tick.
 */
public class FuelBurnSystem extends EntityTickingSystem<ChunkStore> {
    private static final double FUEL_BURN_RATE_PER_SECOND = 1.0; // 1 fuel value per second

    private final ComponentType<ChunkStore, FuelInventory> fuelInvType;
    private final ComponentType<ChunkStore, ProducesHE> produceType;
    private final ComponentType<ChunkStore, StoresHE> storeType;
    private final Query<ChunkStore> query;

    public FuelBurnSystem(ComponentType<ChunkStore, FuelInventory> fuelInvType,
                          ComponentType<ChunkStore, ProducesHE> produceType,
                          ComponentType<ChunkStore, StoresHE> storeType) {
        this.fuelInvType = fuelInvType;
        this.produceType = produceType;
        this.storeType = storeType;
        this.query = Query.and(fuelInvType, produceType, storeType);
    }

    @Override
    public Query<ChunkStore> getQuery() {
        return query;
    }

    @Override
    public void tick(float dt, int index, ArchetypeChunk<ChunkStore> chunk, Store<ChunkStore> store, CommandBuffer<ChunkStore> buffer) {
        FuelInventory fuel = chunk.getComponent(index, fuelInvType);
        ProducesHE prod = chunk.getComponent(index, produceType);
        StoresHE energy = chunk.getComponent(index, storeType);
        Ref<ChunkStore> ref = chunk.getReferenceTo(index);
        if (fuel == null || prod == null || energy == null || ref == null) return;

        ItemContainer container = fuel.fuelContainer;
        if (container == null) {
            fuel.fuelContainer = new com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer((short) 1);
            container = fuel.fuelContainer;
            buffer.replaceComponent(ref, fuelInvType, fuel);
        }
        ItemStack stack;
        try {
            stack = container.getItemStack((short) 0);
        } catch (IllegalArgumentException ex) {
            // container has zero slots; recreate with one slot
            fuel.fuelContainer = new com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer((short) 1);
            buffer.replaceComponent(ref, fuelInvType, fuel);
            prod.enabled = false;
            buffer.replaceComponent(ref, produceType, prod);
            return;
        }
        if (stack == null || ItemStack.isEmpty(stack)) {
            prod.enabled = false;
            buffer.replaceComponent(ref, produceType, prod);
            return;
        }

        var item = stack.getItem();
        if (item == null) {
            prod.enabled = false;
            buffer.replaceComponent(ref, produceType, prod);
            return;
        }

        double fuelQuality = item.getFuelQuality();
        if (fuelQuality <= 0.0) {
            prod.enabled = false;
            buffer.replaceComponent(ref, produceType, prod);
            return;
        }
        // Determine how much fuel value we can burn this tick (1 fuel value per second)
        double burnBudget = FUEL_BURN_RATE_PER_SECOND / HytaleIndustriesPlugin.TPS;
        if (burnBudget <= 0.0) return;

        // If storage is full, pause burning
        if (energy.current >= energy.max) {
            prod.enabled = false;
            buffer.replaceComponent(ref, produceType, prod);
            return;
        }

        // Initialize remaining burn on current item if needed
        if (fuel.fuelValueRemaining <= 0.0) {
            fuel.fuelValueRemaining = fuelQuality;
        }

        double burned = Math.min(fuel.fuelValueRemaining, burnBudget);
        fuel.fuelValueRemaining -= burned;
        // mark production enabled while fuel is burning
        if (!prod.enabled) {
            prod.enabled = true;
            buffer.replaceComponent(ref, produceType, prod);
        }

        // If current item fully consumed, decrement stack and reset remaining for next item
        if (fuel.fuelValueRemaining <= 1e-6) {
            int qty = stack.getQuantity();
            int newQty = qty - 1;
            if (newQty <= 0) {
                container.setItemStackForSlot((short) 0, ItemStack.EMPTY);
                fuel.fuelValueRemaining = 0.0;
            } else {
                container.setItemStackForSlot((short) 0, stack.withQuantity(newQty));
                fuel.fuelValueRemaining = fuelQuality; // next item
            }
        }
        buffer.replaceComponent(ref, fuelInvType, fuel);
    }
}
