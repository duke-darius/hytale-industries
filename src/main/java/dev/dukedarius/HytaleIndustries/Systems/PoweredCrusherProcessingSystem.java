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
import dev.dukedarius.HytaleIndustries.Components.Processing.HEProcessing;
import dev.dukedarius.HytaleIndustries.Components.Processing.PoweredCrusherInventory;
import dev.dukedarius.HytaleIndustries.BlockStates.PoweredCrusherBlockState;
import dev.dukedarius.HytaleIndustries.HytaleIndustriesPlugin;
import it.unimi.dsi.fastutil.ints.IntArrayList;

import javax.annotation.Nonnull;
import java.util.List;

public class PoweredCrusherProcessingSystem extends EntityTickingSystem<com.hypixel.hytale.server.core.universe.world.storage.ChunkStore> {

    private static final float SPEED_MULTIPLIER = 2.0f; // match furnace speedup style

    private final ComponentType<com.hypixel.hytale.server.core.universe.world.storage.ChunkStore, PoweredCrusherInventory> invType;
    private final ComponentType<com.hypixel.hytale.server.core.universe.world.storage.ChunkStore, StoresHE> storeType;
    private final ComponentType<com.hypixel.hytale.server.core.universe.world.storage.ChunkStore, ConsumesHE> consumeType;
    private final ComponentType<com.hypixel.hytale.server.core.universe.world.storage.ChunkStore, HEProcessing> procType;
    private final Query<com.hypixel.hytale.server.core.universe.world.storage.ChunkStore> query;

    public PoweredCrusherProcessingSystem(ComponentType<com.hypixel.hytale.server.core.universe.world.storage.ChunkStore, PoweredCrusherInventory> invType,
                                          ComponentType<com.hypixel.hytale.server.core.universe.world.storage.ChunkStore, StoresHE> storeType,
                                          ComponentType<com.hypixel.hytale.server.core.universe.world.storage.ChunkStore, ConsumesHE> consumeType,
                                          ComponentType<com.hypixel.hytale.server.core.universe.world.storage.ChunkStore, HEProcessing> procType) {
        this.invType = invType;
        this.storeType = storeType;
        this.consumeType = consumeType;
        this.procType = procType;
        this.query = Query.and(invType, storeType, consumeType, procType);
    }

    @Override
    public Query<com.hypixel.hytale.server.core.universe.world.storage.ChunkStore> getQuery() {
        return query;
    }

    @Override
    public void tick(float dt, int index, ArchetypeChunk<com.hypixel.hytale.server.core.universe.world.storage.ChunkStore> chunk,
                     Store<com.hypixel.hytale.server.core.universe.world.storage.ChunkStore> store,
                     CommandBuffer<com.hypixel.hytale.server.core.universe.world.storage.ChunkStore> buffer) {

        PoweredCrusherInventory inv = chunk.getComponent(index, invType);
        StoresHE energy = chunk.getComponent(index, storeType);
        ConsumesHE consume = chunk.getComponent(index, consumeType);
        HEProcessing proc = chunk.getComponent(index, procType);
        if (inv == null || energy == null || consume == null || proc == null) return;

        ensureContainers(inv);

        SimpleItemContainer input = inv.input;
        SimpleItemContainer output = inv.output;

        CraftingRecipe recipe = findRecipe(input);
        if (recipe == null) {
            proc.setEnabled(false);
            consume.enabled = false;
            proc.setCurrentWork(0f);
            buffer.replaceComponent(chunk.getReferenceTo(index), procType, proc);
            buffer.replaceComponent(chunk.getReferenceTo(index), consumeType, consume);
            return;
        }
        List<MaterialQuantity> inputs = com.hypixel.hytale.builtin.crafting.component.CraftingManager.getInputMaterials(recipe);
        List<ItemStack> outputs = com.hypixel.hytale.builtin.crafting.component.CraftingManager.getOutputItemStacks(recipe);

        if (!canFitOutputs(output, outputs)) {
            proc.setEnabled(false);
            consume.enabled = false;
            buffer.replaceComponent(chunk.getReferenceTo(index), procType, proc);
            buffer.replaceComponent(chunk.getReferenceTo(index), consumeType, consume);
            return;
        }

        float effectiveTime = Math.max(0.0001f, recipe.getTimeSeconds() / SPEED_MULTIPLIER);
        proc.setWorkRequired(effectiveTime);
        consume.heConsumption = 20;

        long heCost = consume.heConsumption; // per tick
        if (energy.current < heCost) {
            consume.enabled = false;
            proc.setEnabled(false);
            buffer.replaceComponent(chunk.getReferenceTo(index), consumeType, consume);
            buffer.replaceComponent(chunk.getReferenceTo(index), procType, proc);
            return;
        }

        // Begin/continue processing
        consume.enabled = true;
        proc.setEnabled(false); // keep HEProcessingSystem from also advancing work

        proc.setCurrentWork(proc.getCurrentWork() + dt);

        if (proc.getCurrentWork() + 1e-6 >= proc.getWorkRequired()) {
            if (!hasAllInputs(input, inputs)) {
                proc.setCurrentWork(0f);
                proc.setEnabled(false);
                consume.enabled = false;
                buffer.replaceComponent(chunk.getReferenceTo(index), consumeType, consume);
                buffer.replaceComponent(chunk.getReferenceTo(index), procType, proc);
                buffer.replaceComponent(chunk.getReferenceTo(index), storeType, energy);
                return;
            }

            ListTransaction<com.hypixel.hytale.server.core.inventory.transaction.MaterialTransaction> removeTx =
                    input.removeMaterials(inputs, true, true, true);
            if (!removeTx.succeeded()) {
                proc.setCurrentWork(0f);
                proc.setEnabled(false);
                consume.enabled = false;
                buffer.replaceComponent(chunk.getReferenceTo(index), consumeType, consume);
                buffer.replaceComponent(chunk.getReferenceTo(index), procType, proc);
                buffer.replaceComponent(chunk.getReferenceTo(index), storeType, energy);
                return;
            }

            ListTransaction<ItemStackTransaction> addTx = output.addItemStacks(outputs, false, false, false);
            if (addTx == null || !addTx.succeeded()) {
                proc.setCurrentWork(0f);
                buffer.replaceComponent(chunk.getReferenceTo(index), procType, proc);
                buffer.replaceComponent(chunk.getReferenceTo(index), storeType, energy);
                return;
            }

            proc.setCurrentWork(0f);
        }

        buffer.replaceComponent(chunk.getReferenceTo(index), procType, proc);
        buffer.replaceComponent(chunk.getReferenceTo(index), consumeType, consume);
        buffer.replaceComponent(chunk.getReferenceTo(index), storeType, energy);
    }

    private static void ensureContainers(PoweredCrusherInventory inv) {
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
        List<CraftingRecipe> recipes = CraftingPlugin.getBenchRecipes(BenchType.Processing, "Crusher");
        if (recipes == null || recipes.isEmpty()) {
            // fallback: try powered crusher blockstate recipe id if present
            recipes = CraftingPlugin.getBenchRecipes(BenchType.Processing, "PoweredCrusher");
        }
        if (recipes != null) {
            for (CraftingRecipe recipe : recipes) {
                List<MaterialQuantity> mats = com.hypixel.hytale.builtin.crafting.component.CraftingManager.getInputMaterials(recipe);
                if (hasAllInputs(input, mats)) {
                    return recipe;
                }
            }
        }
        return null;
    }
}
