package dev.dukedarius.HytaleIndustries.Systems;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.component.RemoveReason;
import dev.dukedarius.HytaleIndustries.Components.Quarry.QuarryProjectileComponent;
import dev.dukedarius.HytaleIndustries.HytaleIndustriesPlugin;

import javax.annotation.Nonnull;

public class QuarryProjectileSystem extends EntityTickingSystem<EntityStore> {

    private final ComponentType<EntityStore, QuarryProjectileComponent> quarryProjectileType;
    private final Query<EntityStore> query;

    public QuarryProjectileSystem(ComponentType<EntityStore, QuarryProjectileComponent> quarryProjectileType) {
        this.quarryProjectileType = quarryProjectileType;
        this.query = quarryProjectileType;
    }

    @Override
    public Query<EntityStore> getQuery() {
        return query;
    }

    @Override
    public void tick(float dt,
                     int index,
                     ArchetypeChunk<EntityStore> chunk,
                     Store<EntityStore> store,
                     CommandBuffer<EntityStore> buffer) {
        QuarryProjectileComponent tag = chunk.getComponent(index, quarryProjectileType);
        if (tag == null) return;

        tag.age += dt;
        if (tag.age >= tag.maxLifetime) {
            Ref<EntityStore> ref = chunk.getReferenceTo(index);
            buffer.removeEntity(ref, RemoveReason.REMOVE);
        } else {
            // persist updated age
            buffer.replaceComponent(chunk.getReferenceTo(index), quarryProjectileType, tag);
        }
    }
}
