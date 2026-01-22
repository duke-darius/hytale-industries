package dev.dukedarius.HytaleIndustries.ChunkLoading;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

/**
 * A single ChunkLoader block instance.
 *
 * We persist the exact block position for validation/cleanup, and also cache the
 * containing chunk coordinates for efficient loading.
 */
public class ChunkLoaderEntry {

    public String worldName;
    public int blockX;
    public int blockY;
    public int blockZ;

    public int chunkX;
    public int chunkZ;

    public ChunkLoaderEntry() {
        // codec
    }

    public ChunkLoaderEntry(String worldName, int blockX, int blockY, int blockZ, int chunkX, int chunkZ) {
        this.worldName = worldName;
        this.blockX = blockX;
        this.blockY = blockY;
        this.blockZ = blockZ;
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
    }

    public static final BuilderCodec<ChunkLoaderEntry> CODEC = BuilderCodec.builder(
            ChunkLoaderEntry.class,
            ChunkLoaderEntry::new
    )
            .append(new KeyedCodec<>("WorldName", Codec.STRING), (s, v) -> s.worldName = v, s -> s.worldName)
            .add()
            .append(new KeyedCodec<>("BlockX", Codec.INTEGER), (s, v) -> s.blockX = v, s -> s.blockX)
            .add()
            .append(new KeyedCodec<>("BlockY", Codec.INTEGER), (s, v) -> s.blockY = v, s -> s.blockY)
            .add()
            .append(new KeyedCodec<>("BlockZ", Codec.INTEGER), (s, v) -> s.blockZ = v, s -> s.blockZ)
            .add()
            .append(new KeyedCodec<>("ChunkX", Codec.INTEGER), (s, v) -> s.chunkX = v, s -> s.chunkX)
            .add()
            .append(new KeyedCodec<>("ChunkZ", Codec.INTEGER), (s, v) -> s.chunkZ = v, s -> s.chunkZ)
            .add()
            .build();

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ChunkLoaderEntry other)) return false;
        return blockX == other.blockX
                && blockY == other.blockY
                && blockZ == other.blockZ
                && chunkX == other.chunkX
                && chunkZ == other.chunkZ
                && (worldName != null ? worldName.equals(other.worldName) : other.worldName == null);
    }

    @Override
    public int hashCode() {
        int result = worldName != null ? worldName.hashCode() : 0;
        result = 31 * result + blockX;
        result = 31 * result + blockY;
        result = 31 * result + blockZ;
        result = 31 * result + chunkX;
        result = 31 * result + chunkZ;
        return result;
    }
}
