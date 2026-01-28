package dev.dukedarius.HytaleIndustries.Systems.Energy;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import dev.dukedarius.HytaleIndustries.Components.Energy.ConsumesHE;
import dev.dukedarius.HytaleIndustries.Components.Processing.HEProcessing;
import dev.dukedarius.HytaleIndustries.HytaleIndustriesPlugin;

public class HEProcessingSystem extends EntityTickingSystem<ChunkStore> {
    private final ComponentType<ChunkStore, HEProcessing> procType;
    private final ComponentType<ChunkStore, ConsumesHE> consumeType;
    private final Query<ChunkStore> query;

    public HEProcessingSystem(ComponentType<ChunkStore, HEProcessing> procType,
                              ComponentType<ChunkStore, ConsumesHE> consumeType) {
        this.procType = procType;
        this.consumeType = consumeType;
        this.query = Query.and(procType, consumeType);
    }

    @Override
    public Query<ChunkStore> getQuery() { return query; }

    @Override
    public void tick(float dt, int index, ArchetypeChunk<ChunkStore> chunk,
                     com.hypixel.hytale.component.Store<ChunkStore> store,
                     CommandBuffer<ChunkStore> buffer) {
        HEProcessing proc = chunk.getComponent(index, procType);
        ConsumesHE cons = chunk.getComponent(index, consumeType);
        if (proc == null || cons == null || !proc.isEnabled()) return;

        if (!cons.enabled) return;
        // Work required expressed in seconds worth of work; convert via TPS
        float perTickWork = proc.getWorkRequired() / HytaleIndustriesPlugin.TPS;
        proc.setCurrentWork(proc.getCurrentWork() + perTickWork);
        buffer.replaceComponent(chunk.getReferenceTo(index), procType, proc);
    }
}
