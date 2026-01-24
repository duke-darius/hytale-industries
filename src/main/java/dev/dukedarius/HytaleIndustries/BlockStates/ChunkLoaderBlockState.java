package dev.dukedarius.HytaleIndustries.BlockStates;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.chunk.ChunkFlag;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.state.TickableBlockState;
import com.hypixel.hytale.server.core.universe.world.meta.BlockState;
import com.hypixel.hytale.server.core.universe.world.meta.BlockStateModule;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import dev.dukedarius.HytaleIndustries.HytaleIndustriesPlugin;

import javax.annotation.Nonnull;

public class ChunkLoaderBlockState extends BlockState implements TickableBlockState {

    public static final String STATE_ID = "chunkLoader";

    public enum LoadingMode {
        Background,
        Active
    }

    // Defaults to Background.
    private LoadingMode loadingMode = LoadingMode.Background;

    public static final Codec<ChunkLoaderBlockState> CODEC = BuilderCodec.builder(
            ChunkLoaderBlockState.class,
            ChunkLoaderBlockState::new,
            BlockState.BASE_CODEC
    )
            .append(new KeyedCodec<>("LoadingMode", Codec.STRING), (s, v) -> s.loadingMode = parseMode(v), s -> s.loadingMode.name())
            .add()
            .build();

    private transient long lastRegisterNanos = 0L;

    @Override
    public boolean initialize(@Nonnull BlockType blockType) {
        boolean ok = super.initialize(blockType);
        ensureKeepLoadedAndRegistered();
        return ok;
    }

    @Override
    public void tick(float dt, int index,
                     @Nonnull com.hypixel.hytale.component.ArchetypeChunk<com.hypixel.hytale.server.core.universe.world.storage.ChunkStore> archetypeChunk,
                     @Nonnull com.hypixel.hytale.component.Store<com.hypixel.hytale.server.core.universe.world.storage.ChunkStore> store,
                     @Nonnull com.hypixel.hytale.component.CommandBuffer<com.hypixel.hytale.server.core.universe.world.storage.ChunkStore> commandBuffer) {
        // Keep it very cheap; the manager will also reconcile periodically.
        ensureChunkLoadedAndTicking();

        // Re-register occasionally in case config was deleted/cleared.
        long now = System.nanoTime();
        if (now - lastRegisterNanos > 5_000_000_000L) { // 5s
            ensureRegistered();
            lastRegisterNanos = now;
        }
    }

    private void ensureChunkLoadedAndTicking() {
        WorldChunk chunk = this.getChunk();
        if (chunk == null) return;

        chunk.addKeepLoaded();
        chunk.setFlag(ChunkFlag.TICKING, true);
        chunk.resetKeepAlive();

        if (loadingMode == LoadingMode.Active) {
            chunk.resetActiveTimer();
        }
    }

    private void ensureRegistered() {
        WorldChunk chunk = this.getChunk();
        if (chunk == null) return;
        var world = chunk.getWorld();
        if (world == null) return;
        var pos = this.getBlockPosition();
        if (pos == null) return;

        HytaleIndustriesPlugin plugin = HytaleIndustriesPlugin.INSTANCE;
        if (plugin == null) return;
        if (plugin.getChunkLoaderManager() == null) return;

        plugin.getChunkLoaderManager().registerLoader(world.getName(), pos.x, pos.y, pos.z);
    }

    public LoadingMode getLoadingMode() {
        return loadingMode == null ? LoadingMode.Background : loadingMode;
    }

    public void setLoadingMode(LoadingMode mode) {
        if (mode == null) {
            mode = LoadingMode.Background;
        }
        if (this.loadingMode == mode) {
            return;
        }
        this.loadingMode = mode;
        persistSelf();
    }

    private void ensureKeepLoadedAndRegistered() {
        ensureChunkLoadedAndTicking();
        ensureRegistered();
    }

    private static LoadingMode parseMode(String v) {
        if (v == null) return LoadingMode.Background;
        try {
            return LoadingMode.valueOf(v);
        } catch (IllegalArgumentException ignored) {
            return LoadingMode.Background;
        }
    }

    private void persistSelf() {
        WorldChunk chunk = this.getChunk();
        var pos = this.getBlockPosition();
        if (chunk == null || pos == null) {
            return;
        }

        var stateRef = chunk.getBlockComponentEntity(pos.x & 31, pos.y, pos.z & 31);
        if (stateRef == null) {
            return;
        }

        ComponentType<ChunkStore, Component<ChunkStore>> type = (ComponentType) BlockStateModule.get().getComponentType((Class) this.getClass());
        if (type == null) {
            return;
        }

        stateRef.getStore().replaceComponent(stateRef, type, this);
        chunk.markNeedsSaving();
    }
}
