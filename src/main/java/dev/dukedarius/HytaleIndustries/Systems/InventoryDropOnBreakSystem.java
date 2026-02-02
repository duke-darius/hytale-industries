package dev.dukedarius.HytaleIndustries.Systems;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.meta.BlockState;
import com.hypixel.hytale.server.core.universe.world.meta.state.ItemContainerBlockState;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.component.AddReason;
import dev.dukedarius.HytaleIndustries.Components.Processing.PoweredFurnaceInventory;
import dev.dukedarius.HytaleIndustries.Components.Processing.PoweredCrusherInventory;
import dev.dukedarius.HytaleIndustries.Components.Processing.AlloySmelterInventory;
import dev.dukedarius.HytaleIndustries.HytaleIndustriesPlugin;
import javax.annotation.Nonnull;

/**
 * Drops stored items from our machine containers when their block is broken.
 * Prevents inventory loss and mirrors vanilla container behavior.
 */
public class InventoryDropOnBreakSystem extends EntityEventSystem<EntityStore, BreakBlockEvent> {

    public InventoryDropOnBreakSystem() {
        super(BreakBlockEvent.class);
    }

    @Override
    public void handle(int index,
                       @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
                       @Nonnull Store<EntityStore> store,
                       @Nonnull CommandBuffer<EntityStore> commandBuffer,
                       @Nonnull BreakBlockEvent event) {

        // Resolve the block state being broken.
        var world = store.getExternalData().getWorld();
        var pos = event.getTargetBlock();
        WorldChunk chunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(pos.x, pos.z));
        if (chunk == null) {
            chunk = world.getChunkIfLoaded(ChunkUtil.indexChunkFromBlock(pos.x, pos.z));
        }
        if (chunk == null) {
            return;
        }

        var stateRef = chunk.getBlockComponentEntity(pos.x & 31, pos.y, pos.z & 31);
        if (stateRef == null) {
            return;
        }

        // Prefer ECS inventory if present
        PoweredFurnaceInventory pfInv = stateRef.getStore().getComponent(stateRef, HytaleIndustriesPlugin.INSTANCE.getPoweredFurnaceInventoryType());
        PoweredCrusherInventory pcInv = stateRef.getStore().getComponent(stateRef, HytaleIndustriesPlugin.INSTANCE.getPoweredCrusherInventoryType());
        AlloySmelterInventory asInv = stateRef.getStore().getComponent(stateRef, HytaleIndustriesPlugin.INSTANCE.getAlloySmelterInventoryType());
        ItemContainer container = null;
        if (pfInv != null) {
            container = new com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer(pfInv.input, pfInv.output);
        } else if (pcInv != null) {
            container = new com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer(pcInv.input, pcInv.output);
        } else if (asInv != null) {
            var inputs = new com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer(asInv.inputA, asInv.inputB);
            container = new com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer(inputs, asInv.output);
        } else {
            // todo: handle InventoryBlockState
        }
        if(container == null) return;

        Ref<EntityStore> ref = archetypeChunk.getReferenceTo(index);
        ComponentAccessor<EntityStore> accessor = store;
        // Drop position: center of the broken block.
        Vector3d dropPos = new Vector3d(pos.x + 0.5, pos.y + 0.5, pos.z + 0.5);

        short capacity = container.getCapacity();
        for (short slot = 0; slot < capacity; slot++) {
            ItemStack stack = container.getItemStack(slot);
            if (stack != null && !ItemStack.isEmpty(stack)) {
                var holder = ItemComponent.generateItemDrop(
                        accessor,
                        stack,
                        dropPos,
                        Vector3f.ZERO,
                        0f, 0f, 0f
                );
                if (holder != null) {
                    commandBuffer.addEntity(holder, AddReason.SPAWN);
                }
                container.setItemStackForSlot(slot, ItemStack.EMPTY);
            }
        }
    }

    @Override
    public Query<EntityStore> getQuery() {
        return Archetype.empty();
    }
}
