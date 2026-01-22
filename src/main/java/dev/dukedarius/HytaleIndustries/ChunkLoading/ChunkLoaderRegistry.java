package dev.dukedarius.HytaleIndustries.ChunkLoading;

import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.set.SetCodec;

import java.util.HashSet;
import java.util.Set;

public class ChunkLoaderRegistry {

    public Set<ChunkLoaderEntry> entries = new HashSet<>();

    public ChunkLoaderRegistry() {
        // codec
    }

    public static final BuilderCodec<ChunkLoaderRegistry> CODEC = BuilderCodec.builder(
            ChunkLoaderRegistry.class,
            ChunkLoaderRegistry::new
    )
            .append(new KeyedCodec<>(
                    "Entries",
                    // Important: must be mutable at runtime (we add/remove entries as blocks are placed/broken).
                    new SetCodec<>(ChunkLoaderEntry.CODEC, HashSet::new, false)
            ), (s, v) -> s.entries = v, s -> s.entries)
            .add()
            .build();
}
