package dev.dukedarius.HytaleIndustries.Systems;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import dev.dukedarius.HytaleIndustries.Components.PowerCables.UpdatePowerCableComponent;

public class UpdatePowerCableSystem extends EntityTickingSystem<ChunkStore> {

    private final ComponentType<ChunkStore, UpdatePowerCableComponent> updateComponentType;
    private final Query<ChunkStore> query;

    public UpdatePowerCableSystem(ComponentType<ChunkStore, UpdatePowerCableComponent> updateComponentType) {
        this.updateComponentType = updateComponentType;
        this.query = Query.and(updateComponentType);
    }

    @Override
    public Query<ChunkStore> getQuery() {
        return query;
    }

    @Override
    public void tick(float dt, int index, ArchetypeChunk<ChunkStore> archetypeChunk,
                     Store<ChunkStore> store, CommandBuffer<ChunkStore> commandBuffer) {
        var updateComp = archetypeChunk.getComponent(index, updateComponentType);
        var ref = archetypeChunk.getReferenceTo(index);

        if (updateComp == null || ref == null) {
            return;
        }

        // Remove the update marker if it has been processed
        if (updateComp.hasUpdated()) {
            commandBuffer.removeComponent(ref, updateComponentType);
        }
    }
}
