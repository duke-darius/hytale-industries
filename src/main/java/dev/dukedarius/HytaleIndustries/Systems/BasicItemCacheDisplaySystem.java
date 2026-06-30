package dev.dukedarius.HytaleIndustries.Systems;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.dukedarius.HytaleIndustries.Utils.CacheDisplayManager;

import javax.annotation.Nonnull;

public class BasicItemCacheDisplaySystem extends EntityTickingSystem<EntityStore> {

    private final Query<EntityStore> query = Player.getComponentType();

    @Override
    public Query<EntityStore> getQuery() {
        return query;
    }

    @Override
    public void tick(float dt,
                     int index,
                     @Nonnull ArchetypeChunk<EntityStore> chunk,
                     @Nonnull Store<EntityStore> store,
                     @Nonnull CommandBuffer<EntityStore> buffer) {
        CacheDisplayManager.processDirtyQueue(buffer, store);
    }
}
