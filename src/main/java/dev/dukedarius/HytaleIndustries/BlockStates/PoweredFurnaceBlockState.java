package dev.dukedarius.HytaleIndustries.BlockStates;

import com.hypixel.hytale.builtin.crafting.CraftingPlugin;
import com.hypixel.hytale.protocol.BenchType;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.inventory.container.filter.FilterType;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackTransaction;
import com.hypixel.hytale.server.core.inventory.transaction.ListTransaction;
import com.hypixel.hytale.server.core.inventory.transaction.MaterialTransaction;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.state.TickableBlockState;
import com.hypixel.hytale.server.core.universe.world.meta.BlockState;
import com.hypixel.hytale.server.core.universe.world.meta.BlockStateModule;
import com.hypixel.hytale.server.core.universe.world.meta.state.ItemContainerBlockState;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import dev.dukedarius.HytaleIndustries.Energy.ReceivesHE;
import dev.dukedarius.HytaleIndustries.Energy.StoresHE;
import dev.dukedarius.HytaleIndustries.HytaleIndustriesPlugin;
import io.netty.handler.logging.LogLevel;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import javax.annotation.Nullable;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * Furnace-style processing bench powered by HE instead of fuel.
 * - Uses furnace recipes (BenchId "Bench_Furnace", BenchType.Processing).
 * - Consumes 10 HE per second while processing.
 * - Processes twice as fast as vanilla: effective recipe time = recipeTime / 2.
 */
public class PoweredFurnaceBlockState extends BlockState implements TickableBlockState, ItemContainerBlockState, StoresHE, ReceivesHE {

    public static final String STATE_ID = "poweredFurnace";

    private static final double HE_CAPACITY = 10_000.0;
    private static final double HE_CONSUMPTION_PER_SECOND = 10.0;
    private static final float SPEED_MULTIPLIER = 2.0f; // twice as fast

    private SimpleItemContainer inputContainer = new SimpleItemContainer((short) 1);
    private SimpleItemContainer outputContainer = new SimpleItemContainer((short) 1);

    private double heStored = 0.0;
    private float progress = 0.0f;
    private float lastEffectiveTime = 1.0f;

    @Nullable
    private CraftingRecipe recipe;
    @Nullable
    private String recipeId;

    public static final Codec<PoweredFurnaceBlockState> CODEC = BuilderCodec.builder(
                    PoweredFurnaceBlockState.class,
                    PoweredFurnaceBlockState::new,
                    BlockState.BASE_CODEC
            )
            .append(new KeyedCodec<>("Input", ItemContainer.CODEC), (s, v) -> s.inputContainer = (SimpleItemContainer) v, s -> s.inputContainer)
            .add()
            .append(new KeyedCodec<>("Output", ItemContainer.CODEC), (s, v) -> s.outputContainer = (SimpleItemContainer) v, s -> s.outputContainer)
            .add()
            .append(new KeyedCodec<>("HeStored", Codec.DOUBLE), (s, v) -> s.heStored = v, s -> s.heStored)
            .add()
            .append(new KeyedCodec<>("Progress", Codec.FLOAT), (s, v) -> s.progress = v, s -> s.progress)
            .add()
            .append(new KeyedCodec<>("RecipeId", Codec.STRING), (s, v) -> s.recipeId = v, s -> s.recipeId)
            .add()
            .build();

    @Nonnull
    @Override
    public ItemContainer getItemContainer() {
        ensureFilters();
        return new CombinedItemContainer(inputContainer, outputContainer);
    }

    @Override
    public double getHeStored() {
        return heStored;
    }

    @Override
    public void setHeStored(double he) {
        heStored = Math.min(Math.max(0.0, he), HE_CAPACITY);
        persistSelf();
    }

    @Override
    public double getHeCapacity() {
        return HE_CAPACITY;
    }

    public ItemStack getInputStack() {
        return inputContainer.getItemStack((short) 0);
    }

    public ItemStack getOutputStack() {
        return outputContainer.getItemStack((short) 0);
    }

    public ItemContainer getInputContainer() {
        return inputContainer;
    }

    public ItemContainer getOutputContainer() {
        ensureFilters();
        return outputContainer;
    }

    public double getProgressPercent() {
        if (recipe == null || lastEffectiveTime <= 0.0f) {
            return 0.0;
        }
        return Math.min(1.0, Math.max(0.0, progress / lastEffectiveTime));
    }

    private void ensureFilters() {
        if (outputContainer != null) {
            outputContainer.setGlobalFilter(FilterType.ALLOW_OUTPUT_ONLY);
        }
    }

    private void persistSelf() {
        var chunk = this.getChunk();
        if (chunk == null) return;
        var pos = this.getBlockPosition();
        if (pos == null) return;
        var stateRef = chunk.getBlockComponentEntity(pos.x & 31, pos.y, pos.z & 31);
        if (stateRef == null) return;
        @SuppressWarnings({"rawtypes", "unchecked"})
        ComponentType<ChunkStore, Component<ChunkStore>> type =
                (ComponentType) BlockStateModule.get().getComponentType((Class) this.getClass());
        if (type == null) return;
        stateRef.getStore().replaceComponent(stateRef, type, this);
        chunk.markNeedsSaving();
    }

    @Override
    public void tick(float dt, int index, @Nonnull ArchetypeChunk<ChunkStore> archetypeChunk, @Nonnull Store<ChunkStore> store, @Nonnull CommandBuffer<ChunkStore> commandBuffer) {
        // Processing now handled by ECS (PoweredFurnaceProcessingSystem). Keep tick as no-op.
        return;

        // Legacy logic removed
    }

    private boolean hasAllInputs(List<MaterialQuantity> inputs) {
        IntArrayList slots = new IntArrayList();
        for (int i = 0; i < inputContainer.getCapacity(); i++) slots.add(i);
        int matched = 0;
        for (MaterialQuantity mq : inputs) {
            for (int i = 0; i < slots.size(); i++) {
                int slot = slots.getInt(i);
                int out = com.hypixel.hytale.server.core.inventory.container.InternalContainerUtilMaterial.testRemoveMaterialFromSlot(
                        inputContainer, (short) slot, mq, mq.getQuantity(), true);
                if (out == 0) {
                    matched++;
                    slots.removeInt(i);
                    break;
                }
            }
        }
        return matched == inputs.size();
    }

    private void findRecipe() {
        List<CraftingRecipe> recipes = CraftingPlugin.getBenchRecipes(BenchType.Processing, "Furnace");
        if (recipes.isEmpty()) {
            recipe = null;
            recipeId = null;
            return;
        }

        // pick first matching recipe with available inputs
        for (CraftingRecipe candidate : recipes) {
            if (hasAllInputs(com.hypixel.hytale.builtin.crafting.component.CraftingManager.getInputMaterials(candidate))) {
                recipe = candidate;
                recipeId = candidate.getId();
                return;
            }
        }
        recipe = null;
        recipeId = null;
    }
}
