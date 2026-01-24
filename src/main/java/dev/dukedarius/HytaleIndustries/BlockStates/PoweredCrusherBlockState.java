package dev.dukedarius.HytaleIndustries.BlockStates;

import com.hypixel.hytale.builtin.crafting.CraftingPlugin;
import com.hypixel.hytale.protocol.BenchType;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import com.hypixel.hytale.server.core.inventory.container.filter.FilterType;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackTransaction;
import com.hypixel.hytale.server.core.inventory.transaction.ListTransaction;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.state.TickableBlockState;
import com.hypixel.hytale.server.core.universe.world.meta.BlockState;
import com.hypixel.hytale.server.core.universe.world.meta.BlockStateModule;
import com.hypixel.hytale.server.core.universe.world.meta.state.ItemContainerBlockState;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import dev.dukedarius.HytaleIndustries.Energy.ReceivesHE;
import dev.dukedarius.HytaleIndustries.Energy.StoresHE;
import dev.dukedarius.HytaleIndustries.HytaleIndustriesPlugin;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * HE-powered crusher: identical power stats to the Powered Furnace but runs crusher recipes.
 * Recipes are pulled from the CraftingPlugin bench type Processing with bench id \"Crusher\"
 * to make it easy to extend via asset recipes.
 */
public class PoweredCrusherBlockState extends BlockState implements TickableBlockState, ItemContainerBlockState, StoresHE, ReceivesHE {

    public static final String STATE_ID = "poweredCrusher";

    private static final double HE_CAPACITY = 10_000.0;
    private static final double HE_CONSUMPTION_PER_SECOND = 10.0;
    private static final float SPEED_MULTIPLIER = 2.0f; // same as powered furnace

    private SimpleItemContainer inputContainer = new SimpleItemContainer((short) 1);
    private SimpleItemContainer outputContainer = new SimpleItemContainer((short) 1);

    private double heStored = 0.0;
    private float progress = 0.0f;
    private float lastEffectiveTime = 1.0f;

    @Nullable
    private CrusherRecipe recipe;

    @Nullable
    private String recipeId;

    public static final Codec<PoweredCrusherBlockState> CODEC = BuilderCodec.builder(
                    PoweredCrusherBlockState.class,
                    PoweredCrusherBlockState::new,
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
        HytaleIndustriesPlugin.LOGGER.atFine().log("[PoweredCrusher] tick dt=" + dt + " pos=(" + getBlockX() + "," + getBlockY() + "," + getBlockZ() + ")");
        if (dt <= 0) return;

        World world = store.getExternalData().getWorld();

        if (recipe == null && recipeId != null) {
            recipe = CrusherRecipeRegistry.getById(recipeId);
        }
        if (recipe == null) {
            findRecipe();
            progress = 0.0f;
        }

        if (recipe == null) {
            return;
        }

        List<ItemStack> outputs = Collections.singletonList(recipe.createOutputStack());
        if (!outputContainer.canAddItemStacks(outputs, false, false)) {
            return; // wait for space
        }

        ItemStack inputStack = inputContainer.getItemStack((short) 0);
        if (!recipe.matchesInput(inputStack)) {
            recipe = null;
            recipeId = null;
            progress = 0.0f;
            return;
        }

        double heNeeded = HE_CONSUMPTION_PER_SECOND * dt;
        if (heStored < heNeeded) {
            return; // pause until powered
        }

        setHeStored(heStored - heNeeded);

        float effectiveTime = Math.max(0.0001f, recipe.timeSeconds / SPEED_MULTIPLIER);
        lastEffectiveTime = effectiveTime;
        progress += dt;

        if (progress >= effectiveTime) {
            // consume input
            ItemStack remaining = recipe.consumeInput(inputStack);
            inputContainer.setItemStackForSlot((short) 0, remaining);

            ListTransaction<ItemStackTransaction> addTx = outputContainer.addItemStacks(outputs, false, false, false);
            if (addTx == null || !addTx.succeeded()) {
                progress = 0.0f;
                return;
            }

            progress = 0.0f;
            persistSelf();
        } else {
            persistSelf();
        }
    }

    private void findRecipe() {
        ItemStack stack = inputContainer.getItemStack((short) 0);
        if (stack == null || ItemStack.isEmpty(stack)) {
            recipe = null;
            recipeId = null;
            return;
        }

        List<CrusherRecipe> available = CrusherRecipeRegistry.getRecipes();
        for (CrusherRecipe r : available) {
            if (r.matchesInput(stack)) {
                recipe = r;
                recipeId = r.id;
                return;
            }
        }

        recipe = null;
        recipeId = null;
    }

    public static final class CrusherRecipe {
        private final String id;
        private final String inputItemId;
        private final int inputQty;
        private final String outputItemId;
        private final int outputQty;
        private final float timeSeconds;

        private CrusherRecipe(String id, String inputItemId, int inputQty, String outputItemId, int outputQty, float timeSeconds) {
            this.id = id;
            this.inputItemId = inputItemId;
            this.inputQty = inputQty;
            this.outputItemId = outputItemId;
            this.outputQty = outputQty;
            this.timeSeconds = timeSeconds;
        }

        public static CrusherRecipe of(String id, String inputItemId, int inputQty, String outputItemId, int outputQty, float timeSeconds) {
            return new CrusherRecipe(id, inputItemId, inputQty, outputItemId, outputQty, timeSeconds);
        }

        boolean matchesInput(@Nullable ItemStack stack) {
            if (stack == null || ItemStack.isEmpty(stack)) return false;
            return inputItemId.equals(stack.getItemId()) && stack.getQuantity() >= inputQty;
        }

        ItemStack consumeInput(@Nullable ItemStack stack) {
            if (stack == null || ItemStack.isEmpty(stack)) {
                return ItemStack.EMPTY;
            }
            int newQty = stack.getQuantity() - inputQty;
            if (newQty <= 0) {
                return ItemStack.EMPTY;
            }
            ItemStack dec = stack.withQuantity(newQty);
            return dec == null ? ItemStack.EMPTY : dec;
        }

        ItemStack createOutputStack() {
            return new ItemStack(outputItemId, outputQty);
        }
    }

    /**
        * Registry backed by CraftingPlugin so recipes can also be supplied via assets.
        */
    public static final class CrusherRecipeRegistry {
        private static final List<CrusherRecipe> STATIC_RECIPES = new ArrayList<>();

        static {
            // Default recipe requested: 1 Ore_Iron -> 2 Crushed Iron, 8s base time (halved by speed multiplier).
            STATIC_RECIPES.add(CrusherRecipe.of("HytaleIndustries_Crush_Iron", "Ore_Iron", 1, "HytaleIndustries_CrushedIron", 2, 8.0f));
        }

        private CrusherRecipeRegistry() {
        }

        public static List<CrusherRecipe> getRecipes() {
            List<CrusherRecipe> out = new ArrayList<>(STATIC_RECIPES);
            // Allow bench-driven recipes for easy extension by data packs.
            try {
                var recipes = CraftingPlugin.getBenchRecipes(BenchType.Processing, "Crusher");
                recipes.forEach(r -> {
                    var inputs = com.hypixel.hytale.builtin.crafting.component.CraftingManager.getInputMaterials(r);
                    var outputs = com.hypixel.hytale.builtin.crafting.component.CraftingManager.getOutputItemStacks(r);
                    if (inputs.size() != 1 || outputs.size() != 1) {
                        return;
                    }
                    var in = inputs.get(0);
                    var outStack = outputs.get(0);
                    out.add(CrusherRecipe.of(
                            r.getId(),
                            in.getItemId(),
                            in.getQuantity(),
                            outStack.getItemId(),
                            outStack.getQuantity(),
                            r.getTimeSeconds()
                    ));
                });
            } catch (Throwable ignored) {
                // CraftingPlugin may not have any crusher bench recipes yet.
            }
            return out;
        }

        @Nullable
        public static CrusherRecipe getById(@Nullable String id) {
            if (id == null) return null;
            for (CrusherRecipe r : getRecipes()) {
                if (id.equals(r.id)) {
                    return r;
                }
            }
            return null;
        }
    }
}
