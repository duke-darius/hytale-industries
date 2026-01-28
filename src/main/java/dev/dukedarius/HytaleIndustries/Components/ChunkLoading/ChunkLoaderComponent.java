package dev.dukedarius.HytaleIndustries.Components.ChunkLoading;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.server.core.universe.world.chunk.ChunkFlag;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;

import javax.annotation.Nonnull;

public class ChunkLoaderComponent implements Component<com.hypixel.hytale.server.core.universe.world.storage.ChunkStore> {

    public enum LoadingMode {
        Background,
        Active
    }

    @Nonnull
    public static final BuilderCodec<ChunkLoaderComponent> CODEC = BuilderCodec.builder(
                    ChunkLoaderComponent.class,
                    ChunkLoaderComponent::new
            )
            .append(new KeyedCodec<>("LoadingMode", Codec.STRING),
                    (c, v) -> c.loadingMode = parseMode(v),
                    c -> c.loadingMode.name())
            .add()
            .build();

    public LoadingMode loadingMode = LoadingMode.Background;
    public long lastRegisterNanos = 0L;

    public void ensureKeepLoaded(WorldChunk chunk) {
        if (chunk == null) return;
        chunk.addKeepLoaded();
        chunk.setFlag(ChunkFlag.TICKING, true);
        chunk.resetKeepAlive();
        if (loadingMode == LoadingMode.Active) {
            chunk.resetActiveTimer();
        }
    }


    private static LoadingMode parseMode(String v) {
        if (v == null) return LoadingMode.Background;
        try {
            return LoadingMode.valueOf(v);
        } catch (IllegalArgumentException ignored) {
            return LoadingMode.Background;
        }
    }

    @Override
    public ChunkLoaderComponent clone() {
        try {
            return (ChunkLoaderComponent) super.clone();
        } catch (CloneNotSupportedException e) {
            ChunkLoaderComponent copy = new ChunkLoaderComponent();
            copy.loadingMode = this.loadingMode;
            copy.lastRegisterNanos = this.lastRegisterNanos;
            return copy;
        }
    }
}
