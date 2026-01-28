package dev.dukedarius.HytaleIndustries.Systems.Energy;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import dev.dukedarius.HytaleIndustries.Components.Energy.ConsumesHE;
import dev.dukedarius.HytaleIndustries.Components.Energy.StoresHE;

public class HEConsumptionSystem extends EntityTickingSystem<ChunkStore> {
    private final ComponentType<ChunkStore, ConsumesHE> consumeType;
    private final ComponentType<ChunkStore, StoresHE> storeType;
    private final Query<ChunkStore> query;

    public HEConsumptionSystem(ComponentType<ChunkStore, ConsumesHE> consumeType,
                               ComponentType<ChunkStore, StoresHE> storeType) {
        this.consumeType = consumeType;
        this.storeType = storeType;
        this.query = Query.and(consumeType, storeType);
    }

    @Override
    public Query<ChunkStore> getQuery() { return query; }

    @Override
    public void tick(float dt, int index, ArchetypeChunk<ChunkStore> chunk,
                     com.hypixel.hytale.component.Store<ChunkStore> store,
                     CommandBuffer<ChunkStore> buffer) {
        ConsumesHE cons = chunk.getComponent(index, consumeType);
        StoresHE energy = chunk.getComponent(index, storeType);
        if (cons == null || energy == null) return;
        boolean consumed = cons.consume(energy);
        if (consumed) {
            buffer.replaceComponent(chunk.getReferenceTo(index), storeType, energy);
        }
    }
}
