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
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackTransaction;
import com.hypixel.hytale.server.core.inventory.transaction.ListTransaction;
import dev.dukedarius.HytaleIndustries.Components.Energy.ConsumesHE;
import dev.dukedarius.HytaleIndustries.Components.Energy.StoresHE;
import dev.dukedarius.HytaleIndustries.Components.Processing.AlloySmelterInventory;
import dev.dukedarius.HytaleIndustries.HytaleIndustriesPlugin;
import it.unimi.dsi.fastutil.ints.IntArrayList;

import java.util.List;

/**
 * Drives Alloy Smelter processing via ECS components.
 * Uses a dedicated Processing bench "AlloySmelter" and consumes HE each tick.
 */
public class AlloySmelterProcessingSystem extends EntityTickingSystem<com.hypixel.hytale.server.core.universe.world.storage.ChunkStore> {

    private static final float SPEED_MULTIPLIER = 2.0f;

    private final ComponentType<com.hypixel.hytale.server.core.universe.world.storage.ChunkStore, AlloySmelterInventory> invType;
    private final ComponentType<com.hypixel.hytale.server.core.universe.world.storage.ChunkStore, StoresHE> storeType;
    private final ComponentType<com.hypixel.hytale.server.core.universe.world.storage.ChunkStore, ConsumesHE> consumeType;
    private final Query<com.hypixel.hytale.server.core.universe.world.storage.ChunkStore> query;

    public AlloySmelterProcessingSystem(ComponentType<com.hypixel.hytale.server.core.universe.world.storage.ChunkStore, AlloySmelterInventory> invType,
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

        AlloySmelterInventory inv = chunk.getComponent(index, invType);
        StoresHE energy = chunk.getComponent(index, storeType);
        ConsumesHE consume = chunk.getComponent(index, consumeType);
        if (inv == null || energy == null || consume == null) {
            HytaleIndustriesPlugin.LOGGER.atFine().log("[AlloySmelter] Missing components: inv=%s energy=%s consume=%s",
                    inv != null, energy != null, consume != null);
            return;
        }
        ensureContainers(inv);

        CombinedItemContainer input = new CombinedItemContainer(inv.inputA, inv.inputB);
        SimpleItemContainer output = inv.output;

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

        CraftingRecipe recipe = findRecipe(input);
        if (recipe == null) {
            HytaleIndustriesPlugin.LOGGER.atFine().log("[AlloySmelter] No recipe found; halting.");
            consume.enabled = false;
            inv.currentWork = 0f;
            buffer.replaceComponent(chunk.getReferenceTo(index), invType, inv);
            buffer.replaceComponent(chunk.getReferenceTo(index), consumeType, consume);
            return;
        }

        List<MaterialQuantity> inputs = com.hypixel.hytale.builtin.crafting.component.CraftingManager.getInputMaterials(recipe);
        List<ItemStack> outputs = com.hypixel.hytale.builtin.crafting.component.CraftingManager.getOutputItemStacks(recipe);

        if (!canFitOutputs(output, outputs)) {
            HytaleIndustriesPlugin.LOGGER.atFine().log("[AlloySmelter] Output full; halting.");
            consume.enabled = false;
            buffer.replaceComponent(chunk.getReferenceTo(index), consumeType, consume);
            buffer.replaceComponent(chunk.getReferenceTo(index), invType, inv);
            return;
        }

        float effectiveTime = Math.max(0.0001f, recipe.getTimeSeconds() / SPEED_MULTIPLIER);
        inv.workRequired = effectiveTime;

        consume.heConsumption = 0;
        consume.enabled = false;

        final long heCost = 20L; // 20 HE per tick while processing
        if (energy.current < heCost) {
            HytaleIndustriesPlugin.LOGGER.atFine().log("[AlloySmelter] Not enough HE: current=%d required=%d", energy.current, heCost);
            consume.enabled = false;
            buffer.replaceComponent(chunk.getReferenceTo(index), consumeType, consume);
            buffer.replaceComponent(chunk.getReferenceTo(index), invType, inv);
            buffer.replaceComponent(chunk.getReferenceTo(index), storeType, energy);
            return;
        }

        energy.current -= heCost;
        inv.currentWork += dt;

        if (inv.currentWork + 1e-6 >= inv.workRequired) {
            if (!hasAllInputs(input, inputs)) {
                HytaleIndustriesPlugin.LOGGER.atFine().log("[AlloySmelter] Work complete but inputs missing when committing; resetting.");
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
                HytaleIndustriesPlugin.LOGGER.atFine().log("[AlloySmelter] Failed to remove input materials; resetting.");
                inv.currentWork = 0f;
                consume.enabled = false;
                buffer.replaceComponent(chunk.getReferenceTo(index), consumeType, consume);
                buffer.replaceComponent(chunk.getReferenceTo(index), invType, inv);
                buffer.replaceComponent(chunk.getReferenceTo(index), storeType, energy);
                return;
            }

            ListTransaction<ItemStackTransaction> addTx = output.addItemStacks(outputs, false, false, false);
            if (addTx == null || !addTx.succeeded()) {
                HytaleIndustriesPlugin.LOGGER.atFine().log("[AlloySmelter] Failed to insert output items; will retry next tick.");
                inv.currentWork = 0f;
                buffer.replaceComponent(chunk.getReferenceTo(index), invType, inv);
                buffer.replaceComponent(chunk.getReferenceTo(index), storeType, energy);
                return;
            }

            HytaleIndustriesPlugin.LOGGER.atFine().log("[AlloySmelter] cycle complete; resetting work to 0");
            inv.currentWork = 0f;
        }

        buffer.replaceComponent(chunk.getReferenceTo(index), invType, inv);
        buffer.replaceComponent(chunk.getReferenceTo(index), consumeType, consume);
        buffer.replaceComponent(chunk.getReferenceTo(index), storeType, energy);
    }

    private static void ensureContainers(AlloySmelterInventory inv) {
        if (inv.inputA == null) {
            inv.inputA = new SimpleItemContainer((short) 1);
        }
        if (inv.inputB == null) {
            inv.inputB = new SimpleItemContainer((short) 1);
        }
        if (inv.output == null) {
            inv.output = new SimpleItemContainer((short) 1);
        }
    }

    private static boolean canFitOutputs(SimpleItemContainer output, List<ItemStack> outputs) {
        if (output == null) return false;
        return output.canAddItemStacks(outputs, false, false);
    }

    private static boolean hasAllInputs(CombinedItemContainer input, List<MaterialQuantity> requirements) {
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

    private static CraftingRecipe findRecipe(CombinedItemContainer input) {
        if (input == null) return null;

        List<CraftingRecipe> recipes = CraftingPlugin.getBenchRecipes(BenchType.Processing, "AlloySmelter");
        if (recipes == null || recipes.isEmpty()) {
            // Allow some flexibility in id casing
            List<CraftingRecipe> alt = CraftingPlugin.getBenchRecipes(BenchType.Processing, "alloySmelter");
            if (alt != null && !alt.isEmpty()) {
                recipes = alt;
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
