package dev.dukedarius.HytaleIndustries.Systems.Energy;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import dev.dukedarius.HytaleIndustries.Components.Energy.ProducesHE;
import dev.dukedarius.HytaleIndustries.Components.Energy.StoresHE;

public class HEProductionSystem extends EntityTickingSystem<ChunkStore> {
    private final ComponentType<ChunkStore, ProducesHE> prodType;
    private final ComponentType<ChunkStore, StoresHE> storeType;
    private final Query<ChunkStore> query;

    public HEProductionSystem(ComponentType<ChunkStore, ProducesHE> prodType,
                              ComponentType<ChunkStore, StoresHE> storeType) {
        this.prodType = prodType;
        this.storeType = storeType;
        this.query = Query.and(prodType, storeType);
    }

    @Override
    public Query<ChunkStore> getQuery() { return query; }

    @Override
    public void tick(float dt, int index, ArchetypeChunk<ChunkStore> chunk,
                     com.hypixel.hytale.component.Store<ChunkStore> store,
                     CommandBuffer<ChunkStore> buffer) {
        ProducesHE prod = chunk.getComponent(index, prodType);
        StoresHE energy = chunk.getComponent(index, storeType);

        if(energy != null && energy.creative){
            energy.current = energy.max;
            buffer.replaceComponent(chunk.getReferenceTo(index), storeType, energy);
            return;
        }

        if (prod == null || energy == null || !prod.enabled) return;
        double eff = prod.efficiency > 0 ? prod.efficiency : 1.0;
        double mult = prod.productionMultiplier > 0 ? prod.productionMultiplier : 1.0;
        long base = Math.max(0, prod.producedPerTick);
        long perTick = (long) Math.floor(base * eff * mult);
        if (perTick <= 0) return;
        long remaining = energy.addEnergy(perTick);
        if (remaining != perTick) {
            buffer.replaceComponent(chunk.getReferenceTo(index), storeType, energy);
        }
    }
}
