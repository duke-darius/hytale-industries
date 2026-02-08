package dev.dukedarius.HytaleIndustries.Systems;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import dev.dukedarius.HytaleIndustries.Components.Storage.BasicItemCacheComponent;

import javax.annotation.Nonnull;

/**
 * Placeholder system for Basic Item Cache blocks.
 *
 * For now this does nothing (logic is driven from the UI page).
 * Once we want always-on displays, we can implement it here
 * using patterns from BasicItemPipeUpdateSystem.
 */
public class BasicItemCacheDisplaySystem extends EntityTickingSystem<ChunkStore> {

    private final ComponentType<ChunkStore, BasicItemCacheComponent> cacheType;
    private final Query<ChunkStore> query;

    public BasicItemCacheDisplaySystem(ComponentType<ChunkStore, BasicItemCacheComponent> cacheType) {
        this.cacheType = cacheType;
        this.query = cacheType;
    }

    @Override
    public Query<ChunkStore> getQuery() {
        return query;
    }

    @Override
    public void tick(float dt,
                     int index,
                     @Nonnull ArchetypeChunk<ChunkStore> chunk,
                     @Nonnull Store<ChunkStore> store,
                     @Nonnull CommandBuffer<ChunkStore> buffer) {
        // no-op for now
    }
}
