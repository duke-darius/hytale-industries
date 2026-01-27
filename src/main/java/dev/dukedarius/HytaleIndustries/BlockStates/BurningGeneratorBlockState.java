package dev.dukedarius.HytaleIndustries.BlockStates;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.protocol.ItemResourceType;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import com.hypixel.hytale.server.core.universe.world.chunk.state.TickableBlockState;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.meta.BlockState;
import com.hypixel.hytale.server.core.universe.world.meta.BlockStateModule;
import com.hypixel.hytale.server.core.universe.world.meta.state.ItemContainerBlockState;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.logger.HytaleLogger;
import dev.dukedarius.HytaleIndustries.Energy.ReceivesHE;
import dev.dukedarius.HytaleIndustries.Energy.StoresHE;
import dev.dukedarius.HytaleIndustries.Energy.TransfersHE;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * A simple 1-slot fuel-burning generator.
 *
 * Fuel is consumed smoothly using the server tick dt (seconds).
 * - Fuel value comes from Item.getFuelQuality() (only for items with resource type "Fuel").
 * - 1 fuel value => 10 HE
 * - Burn rate: 5 fuel value / second
 */
public class BurningGeneratorBlockState extends BlockState implements TickableBlockState, ItemContainerBlockState, StoresHE, TransfersHE {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    public static final String STATE_ID = "burningGenerator";

    // Conversion constants.
    private static final double HE_PER_FUEL = 10.0;
    private static final double FUEL_BURN_RATE_PER_SECOND = 5.0;

    public static final double MAX_HE = 10_000.0;

    // Storage.
    private double heStored = 0.0;

    // Output behavior.
    private static final double TRANSFER_HE_PER_SECOND = 250.0; // simple default; tune later

    // Fuel burn state.
    // Stored as a non-null string for codec simplicity ("" means none).
    private String burningItemId = "";

    // Remaining fuel value in the *current* item being burned.
    private double remainingFuelInCurrentItem = 0.0;

    // 1-slot fuel container.
    private SimpleItemContainer fuelContainer = new SimpleItemContainer((short) 1);

    public static final Codec<BurningGeneratorBlockState> CODEC = BuilderCodec.builder(
                    BurningGeneratorBlockState.class,
                    BurningGeneratorBlockState::new,
                    BlockState.BASE_CODEC
            )
            .append(new KeyedCodec<>("HeStored", Codec.DOUBLE), (s, v) -> s.heStored = v, s -> s.heStored)
            .add()
            .append(new KeyedCodec<>("BurningItemId", Codec.STRING), (s, v) -> s.burningItemId = v, s -> s.burningItemId)
            .add()
            .append(new KeyedCodec<>("RemainingFuelInCurrentItem", Codec.DOUBLE), (s, v) -> s.remainingFuelInCurrentItem = v, s -> s.remainingFuelInCurrentItem)
            .add()
            .append(new KeyedCodec<>("FuelContainer", SimpleItemContainer.CODEC), (s, v) -> s.fuelContainer = v, s -> s.fuelContainer)
            .add()
            .build();

    @Nonnull
    @Override
    public ItemContainer getItemContainer() {
        if (fuelContainer == null) {
            fuelContainer = new SimpleItemContainer((short) 1);
        }
        return fuelContainer;
    }

    @Override
    public double getHeStored() {
        return heStored;
    }

    @Override
    public void setHeStored(double he) {
        if (he < 0.0) {
            heStored = 0.0;
            return;
        }
        heStored = Math.min(MAX_HE, he);
    }

    @Override
    public double getHeCapacity() {
        return MAX_HE;
    }

    @Override
    public void tick(
            float dt,
            int index,
            @Nonnull com.hypixel.hytale.component.ArchetypeChunk<ChunkStore> archetypeChunk,
            @Nonnull com.hypixel.hytale.component.Store<ChunkStore> store,
            @Nonnull com.hypixel.hytale.component.CommandBuffer<ChunkStore> commandBuffer
    ) {
        if (dt <= 0) {
            return;
        }

        boolean dirty = false;

        // First: try to push stored HE to neighbors.
        dirty |= tryTransferToNeighbors(dt);

        ItemContainer container = getItemContainer();
        ItemStack stack = container.getItemStack((short) 0);
        if (stack == null || ItemStack.isEmpty(stack)) {
            if (!burningItemId.isEmpty() || remainingFuelInCurrentItem != 0.0) {
                dirty = true;
            }
            burningItemId = "";
            remainingFuelInCurrentItem = 0.0;

            if (dirty) {
                persistSelf();
            }
            updateState("default");
            return;
        }

        var item = stack.getItem();
        if (item == null) {
            if (dirty) {
                persistSelf();
            }
            updateState("default");
            return;
        }

        if (!isFuelItem(item.getResourceTypes())) {
            if (dirty) {
                persistSelf();
            }
            updateState("default");
            return;
        }

        double fuelQuality = item.getFuelQuality();
        if (fuelQuality <= 0.0) {
            if (dirty) {
                persistSelf();
            }
            updateState("default");
            return;
        }

        // If the item type changed (or we just started), reset the per-item fuel remainder.
        String itemId = stack.getItemId();
        if (burningItemId.isEmpty() || !burningItemId.equals(itemId) || remainingFuelInCurrentItem <= 0.0 || remainingFuelInCurrentItem > fuelQuality) {
            dirty = true;
            burningItemId = itemId;
            remainingFuelInCurrentItem = fuelQuality;
        }

        double burnBudget = FUEL_BURN_RATE_PER_SECOND * dt;
        if (burnBudget <= 0.0) {
            if (dirty) {
                persistSelf();
            }
            updateState("default");
            return;
        }

        // If we're full, don't consume fuel.
        if (heStored >= MAX_HE) {
            if (heStored != MAX_HE) {
                dirty = true;
            }
            heStored = MAX_HE;
            if (dirty) {
                persistSelf();
            }
            updateState("default");
            return;
        }

        updateState("on");
        double burned = Math.min(remainingFuelInCurrentItem, burnBudget);
        if (burned != 0.0) {
            dirty = true;
        }
        remainingFuelInCurrentItem -= burned;

        double addedHe = burned * HE_PER_FUEL;
        if (addedHe != 0.0) {
            dirty = true;
        }
        setHeStored(heStored + addedHe);

        // When this item is fully consumed, decrement the stack by 1 and reset remainder for the next.
        if (remainingFuelInCurrentItem <= 1.0e-9) {
            int qty = stack.getQuantity();
            int newQty = qty - 1;

            if (newQty <= 0) {
                dirty = true;
                container.setItemStackForSlot((short) 0, ItemStack.EMPTY);
                burningItemId = "";
                remainingFuelInCurrentItem = 0.0;
            } else {
                ItemStack dec = stack.withQuantity(newQty);
                if (dec == null || ItemStack.isEmpty(dec)) {
                    dirty = true;
                    container.setItemStackForSlot((short) 0, ItemStack.EMPTY);
                    burningItemId = "";
                    remainingFuelInCurrentItem = 0.0;
                } else {
                    dirty = true;
                    container.setItemStackForSlot((short) 0, dec);
                    burningItemId = itemId;
                    remainingFuelInCurrentItem = fuelQuality;
                }
            }
        }

        if (dirty) {
            persistSelf();
        }
    }

    private boolean tryTransferToNeighbors(float dt) {
        if (dt <= 0) {
            return false;
        }
        if (heStored <= 0.0) {
            return false;
        }

        // TickableBlockState provides chunk + position.
        WorldChunk selfChunk = this.getChunk();
        if (selfChunk == null) {
            return false;
        }
        var world = selfChunk.getWorld();
        if (world == null) {
            return false;
        }
        var pos = this.getBlockPosition();
        if (pos == null) {
            return false;
        }

        double budget = Math.min(heStored, TRANSFER_HE_PER_SECOND * dt);
        if (budget <= 0.0) {
            return false;
        }

        double before = heStored;

        // 6-neighborhood.
        int[][] dirs = new int[][]{
                {1, 0, 0}, {-1, 0, 0},
                {0, 1, 0}, {0, -1, 0},
                {0, 0, 1}, {0, 0, -1}
        };

        for (int[] d : dirs) {
            if (budget <= 0.0) {
                break;
            }

            int nx = pos.x + d[0];
            int ny = pos.y + d[1];
            int nz = pos.z + d[2];

            if (ny < 0 || ny >= 320) {
                continue;
            }

            // Quick filter: only consider neighbors whose state implements ReceivesHE.
            BlockState neighborState = world.getState(nx, ny, nz, true);
            if (!(neighborState instanceof ReceivesHE)) {
                continue;
            }

            // Persist the change via component replacement.
            WorldChunk chunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(nx, nz));
            if (chunk == null) {
                continue;
            }
            int lx = nx & 31;
            int lz = nz & 31;

            var stateRef = chunk.getBlockComponentEntity(lx, ny, lz);
            if (stateRef == null) {
                continue;
            }

            @SuppressWarnings({"rawtypes", "unchecked"})
            ComponentType<ChunkStore, Component<ChunkStore>> type = (ComponentType) BlockStateModule.get().getComponentType((Class) neighborState.getClass());
            if (type == null) {
                continue;
            }

            Component<ChunkStore> stateObj = stateRef.getStore().getComponent(stateRef, type);
            if (!(stateObj instanceof ReceivesHE receiver)) {
                continue;
            }

            double accepted = receiver.receiveHe(budget);
            if (accepted <= 0.0) {
                continue;
            }

            // Save neighbor state.
            stateRef.getStore().replaceComponent(stateRef, type, stateObj);
            chunk.markNeedsSaving();

            // Spend generator energy.
            heStored -= accepted;
            budget -= accepted;
        }

        if (heStored != before) {
            // Clamp and treat as dirty.
            setHeStored(heStored);
            return true;
        }
        return false;
    }

    private void persistSelf() {
        WorldChunk chunk = this.getChunk();
        var pos = this.getBlockPosition();
        if (chunk == null || pos == null) {
            return;
        }

        int lx = pos.x & 31;
        int lz = pos.z & 31;

        var stateRef = chunk.getBlockComponentEntity(lx, pos.y, lz);
        if (stateRef == null) {
            return;
        }

        ComponentType<ChunkStore, BurningGeneratorBlockState> type = BlockStateModule.get().getComponentType(BurningGeneratorBlockState.class);
        if (type == null) {
            return;
        }

        stateRef.getStore().replaceComponent(stateRef, type, this);
        chunk.markNeedsSaving();
    }

    private static boolean isFuelItem(@Nullable ItemResourceType[] resourceTypes) {
        if (resourceTypes == null) {
            return false;
        }
        for (ItemResourceType rt : resourceTypes) {
            if (rt != null && "Fuel".equals(rt.id)) {
                return true;
            }
        }
        return false;
    }

    private void updateState(String targetState) {
        WorldChunk chunk = this.getChunk();
        var pos = this.getBlockPosition();
        if (chunk == null || pos == null) {
            return;
        }

        int lx = pos.x & 31;
        int lz = pos.z & 31;
        BlockType blockType = chunk.getBlockType(lx, pos.y, lz);
        if (blockType == null || blockType.getState() == null) {
            return;
        }

        // Check current ID to avoid redundant updates
        String currentId = blockType.getId();
        if (currentId == null) return;

        boolean isOn = currentId.contains("_State_Definitions_on");
        boolean wantOn = "on".equals(targetState);

        if (isOn == wantOn) {
            return;
        }

        chunk.setBlockInteractionState(pos.x, pos.y, pos.z, blockType, targetState, true);
        LOGGER.atFine().log("Generator at " + pos + " switched state from " + currentId + " to " + targetState);
        
        // setBlockInteractionState may recreate the block component, losing our state.
        // We must re-persist ourselves to ensure fuel/energy data is preserved.
        this.persistSelf();
        
        chunk.markNeedsSaving();
    }
}
