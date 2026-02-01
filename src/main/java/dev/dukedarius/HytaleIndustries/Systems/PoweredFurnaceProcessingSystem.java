package dev.dukedarius.HytaleIndustries.Systems;

import com.hypixel.hytale.builtin.crafting.CraftingPlugin;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.protocol.BenchType;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;
import com.hypixel.hytale.server.core.inventory.container.InternalContainerUtilMaterial;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackTransaction;
import com.hypixel.hytale.server.core.inventory.transaction.ListTransaction;
import dev.dukedarius.HytaleIndustries.Components.Energy.ConsumesHE;
import dev.dukedarius.HytaleIndustries.Components.Energy.StoresHE;
import dev.dukedarius.HytaleIndustries.Components.Processing.PoweredFurnaceInventory;
import dev.dukedarius.HytaleIndustries.HytaleIndustriesPlugin;
import it.unimi.dsi.fastutil.ints.IntArrayList;

import java.util.List;

/**
 * Drives Powered Furnace processing via ECS components.
 * - Consumes 20 HE per tick while processing.
 * - Processes at 2x speed compared to vanilla furnace recipe time.
 */
public class PoweredFurnaceProcessingSystem extends EntityTickingSystem<com.hypixel.hytale.server.core.universe.world.storage.ChunkStore> {

    private static final float SPEED_MULTIPLIER = 2.0f;

    private final ComponentType<com.hypixel.hytale.server.core.universe.world.storage.ChunkStore, PoweredFurnaceInventory> invType;
    private final ComponentType<com.hypixel.hytale.server.core.universe.world.storage.ChunkStore, StoresHE> storeType;
    private final ComponentType<com.hypixel.hytale.server.core.universe.world.storage.ChunkStore, ConsumesHE> consumeType;
    private final Query<com.hypixel.hytale.server.core.universe.world.storage.ChunkStore> query;

    public PoweredFurnaceProcessingSystem(ComponentType<com.hypixel.hytale.server.core.universe.world.storage.ChunkStore, PoweredFurnaceInventory> invType,
                                          ComponentType<com.hypixel.hytale.server.core.universe.world.storage.ChunkStore, StoresHE> storeType,
                                          ComponentType<com.hypixel.hytale.server.core.universe.world.storage.ChunkStore, ConsumesHE> consumeType) {
        this.invType = invType;
        this.storeType = storeType;
        this.consumeType = consumeType;
        this.query = Query.and(invType, storeType, consumeType);
    }

    @Override
    public Query<com.hypixel.hytale.server.core.universe.world.storage.ChunkStore> getQuery() {
        return query;
    }

    @Override
    public void tick(float dt, int index, ArchetypeChunk<com.hypixel.hytale.server.core.universe.world.storage.ChunkStore> chunk,
                     Store<com.hypixel.hytale.server.core.universe.world.storage.ChunkStore> store,
                     CommandBuffer<com.hypixel.hytale.server.core.universe.world.storage.ChunkStore> buffer) {

        PoweredFurnaceInventory inv = chunk.getComponent(index, invType);
        StoresHE energy = chunk.getComponent(index, storeType);
        ConsumesHE consume = chunk.getComponent(index, consumeType);
        if (inv == null || energy == null || consume == null) {
            HytaleIndustriesPlugin.LOGGER.atFine().log("[PoweredFurnace] Missing components: inv=%s energy=%s consume=%s",
                    inv != null, energy != null, consume != null);
            return;
        }
        ensureContainers(inv);

        SimpleItemContainer input = inv.input;
        SimpleItemContainer output = inv.output;

        // Fast-path: if there are no items in the input at all, do nothing quietly.
        boolean hasAnyInput = false;
        if (input != null) {
            for (int i = 0; i < input.getCapacity(); i++) {
                ItemStack s = input.getItemStack((short) i);
                if (s != null && !ItemStack.isEmpty(s)) {
                    hasAnyInput = true;
                    break;
                }
            }
        }
        if (!hasAnyInput) {
            consume.enabled = false;
            inv.currentWork = 0f;
            buffer.replaceComponent(chunk.getReferenceTo(index), invType, inv);
            buffer.replaceComponent(chunk.getReferenceTo(index), consumeType, consume);
            return;
        }

        // We have input and a valid HE store; proceed with recipe lookup and processing.
        CraftingRecipe recipe = findRecipe(input);
        if (recipe == null) {
            HytaleIndustriesPlugin.LOGGER.atFine().log("[PoweredFurnace] No recipe found; halting.");
            consume.enabled = false;
            inv.currentWork = 0f;
            buffer.replaceComponent(chunk.getReferenceTo(index), invType, inv);
            buffer.replaceComponent(chunk.getReferenceTo(index), consumeType, consume);
            return;
        }

        List<MaterialQuantity> inputs = com.hypixel.hytale.builtin.crafting.component.CraftingManager.getInputMaterials(recipe);
        List<ItemStack> outputs = com.hypixel.hytale.builtin.crafting.component.CraftingManager.getOutputItemStacks(recipe);

        if (!canFitOutputs(output, outputs)) {
            HytaleIndustriesPlugin.LOGGER.atFine().log("[PoweredFurnace] Output full or cannot fit outputs; halting.");
            consume.enabled = false;
            buffer.replaceComponent(chunk.getReferenceTo(index), consumeType, consume);
            buffer.replaceComponent(chunk.getReferenceTo(index), invType, inv);
            return;
        }

        float effectiveTime = Math.max(0.0001f, recipe.getTimeSeconds() / SPEED_MULTIPLIER);
        inv.workRequired = effectiveTime;

        // For the powered furnace, we consume HE directly from StoresHE each tick,
        // and keep ConsumesHE disabled so HEConsumptionSystem stays inert.
        consume.heConsumption = 0;
        consume.enabled = false;

        final long heCost = 20L; // 20 HE per tick while processing

        if (energy.current < heCost) {
            HytaleIndustriesPlugin.LOGGER.atFine().log("[PoweredFurnace] Not enough HE: current=%d required=%d", energy.current, heCost);
            consume.enabled = false;
            buffer.replaceComponent(chunk.getReferenceTo(index), consumeType, consume);
            buffer.replaceComponent(chunk.getReferenceTo(index), invType, inv);
            buffer.replaceComponent(chunk.getReferenceTo(index), storeType, energy);
            return;
        }

        // Begin/continue processing: draw HE directly and advance work using dt.
        energy.current -= heCost;
        inv.currentWork += dt;

        if (inv.currentWork + 1e-6 >= inv.workRequired) {
            if (!hasAllInputs(input, inputs)) {
                HytaleIndustriesPlugin.LOGGER.atFine().log("[PoweredFurnace] Work complete but inputs missing when committing; resetting.");
                inv.currentWork = 0f;
                consume.enabled = false;
                buffer.replaceComponent(chunk.getReferenceTo(index), consumeType, consume);
                buffer.replaceComponent(chunk.getReferenceTo(index), invType, inv);
                buffer.replaceComponent(chunk.getReferenceTo(index), storeType, energy);
                return;
            }

            ListTransaction<com.hypixel.hytale.server.core.inventory.transaction.MaterialTransaction> removeTx =
                    input.removeMaterials(inputs, true, true, true);
            if (!removeTx.succeeded()) {
                HytaleIndustriesPlugin.LOGGER.atFine().log("[PoweredFurnace] Failed to remove input materials; resetting.");
                inv.currentWork = 0f;
                consume.enabled = false;
                buffer.replaceComponent(chunk.getReferenceTo(index), consumeType, consume);
                buffer.replaceComponent(chunk.getReferenceTo(index), invType, inv);
                buffer.replaceComponent(chunk.getReferenceTo(index), storeType, energy);
                return;
            }

            ListTransaction<ItemStackTransaction> addTx = output.addItemStacks(outputs, false, false, false);
            if (addTx == null || !addTx.succeeded()) {
                // rollback? inputs already removed; try to re-add outputs on next tick
                HytaleIndustriesPlugin.LOGGER.atFine().log("[PoweredFurnace] Failed to insert output items; will retry next tick.");
                inv.currentWork = 0f;
                buffer.replaceComponent(chunk.getReferenceTo(index), invType, inv);
                buffer.replaceComponent(chunk.getReferenceTo(index), storeType, energy);
                return;
            }

            HytaleIndustriesPlugin.LOGGER.atFine().log("[PoweredFurnace] cycle complete; resetting work to 0");
            inv.currentWork = 0f;
        }

        buffer.replaceComponent(chunk.getReferenceTo(index), invType, inv);
        buffer.replaceComponent(chunk.getReferenceTo(index), consumeType, consume);
        buffer.replaceComponent(chunk.getReferenceTo(index), storeType, energy);
    }

    private static void ensureContainers(PoweredFurnaceInventory inv) {
        if (inv.input == null) {
            inv.input = new SimpleItemContainer((short) 1);
        }
        if (inv.output == null) {
            inv.output = new SimpleItemContainer((short) 1);
        }
    }

    private static boolean canFitOutputs(SimpleItemContainer output, List<ItemStack> outputs) {
        if (output == null) return false;
        return output.canAddItemStacks(outputs, false, false);
    }

    private static boolean hasAllInputs(SimpleItemContainer input, List<MaterialQuantity> requirements) {
        if (input == null || requirements == null) return false;
        IntArrayList slots = new IntArrayList();
        for (int i = 0; i < input.getCapacity(); i++) slots.add(i);
        int matched = 0;
        for (MaterialQuantity mq : requirements) {
            for (int i = 0; i < slots.size(); i++) {
                int slot = slots.getInt(i);
                int out = InternalContainerUtilMaterial.testRemoveMaterialFromSlot(input, (short) slot, mq, mq.getQuantity(), true);
                if (out == 0) {
                    matched++;
                    slots.removeInt(i);
                    break;
                }
            }
        }
        return matched == requirements.size();
    }

    private static CraftingRecipe findRecipe(SimpleItemContainer input) {
        if (input == null) return null;

        // Try the vanilla furnace bench first.
        List<CraftingRecipe> recipes = CraftingPlugin.getBenchRecipes(BenchType.Processing, "Furnace");
        // Fallbacks in case the bench id differs in this environment (e.g. custom powered furnace bench).
        if (recipes == null || recipes.isEmpty()) {
            List<CraftingRecipe> alt = CraftingPlugin.getBenchRecipes(BenchType.Processing, "poweredFurnace");
            if (alt != null && !alt.isEmpty()) {
                recipes = alt;
            } else {
                alt = CraftingPlugin.getBenchRecipes(BenchType.Processing, "PoweredFurnace");
                if (alt != null && !alt.isEmpty()) {
                    recipes = alt;
                }
            }
        }
        if (recipes == null || recipes.isEmpty()) {
            return null;
        }

        for (CraftingRecipe recipe : recipes) {
            List<MaterialQuantity> mats = com.hypixel.hytale.builtin.crafting.component.CraftingManager.getInputMaterials(recipe);
            if (hasAllInputs(input, mats)) {
                return recipe;
            }
        }
        return null;
    }
}
