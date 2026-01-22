package dev.dukedarius.HytaleIndustries.ChunkLoading;

import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.ChunkFlag;
import com.hypixel.hytale.server.core.util.Config;
import dev.dukedarius.HytaleIndustries.BlockStates.ChunkLoaderBlockState;
import dev.dukedarius.HytaleIndustries.HytaleIndustriesPlugin;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public final class ChunkLoaderManager {
    public static final String CHUNK_LOADER_BLOCKTYPE_ID = "HytaleIndustries_ChunkLoader";

    private final HytaleIndustriesPlugin plugin;
    private final Config<ChunkLoaderRegistry> config;

    private final Object lock = new Object();

    @Nullable
    private ScheduledExecutorService executor;
    @Nullable
    private ScheduledFuture<?> reconcileFuture;

    public ChunkLoaderManager(HytaleIndustriesPlugin plugin, Config<ChunkLoaderRegistry> config) {
        this.plugin = plugin;
        this.config = config;
    }

    public void start() {
        // Load persisted entries.
        try {
            config.load().join();
        } catch (Throwable t) {
            plugin.getLogger().atSevere().withCause(t).log("Failed to load chunk loader config");
        }

        // Delay reconcile until the Universe is fully ready.
        Universe.get().getUniverseReady().thenRun(() -> {
            safeReconcileAll();

            // Periodically re-apply keepLoaded/ticking and prune stale entries.
            executor = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, plugin.getName() + "-ChunkLoaderManager");
                t.setDaemon(true);
                return t;
            });

            reconcileFuture = executor.scheduleAtFixedRate(this::safeReconcileAll, 5, 5, TimeUnit.SECONDS);
        });
    }

    public void stop() {
        try {
            if (reconcileFuture != null) {
                reconcileFuture.cancel(false);
            }
        } catch (Throwable ignored) {
        }
        try {
            if (executor != null) {
                executor.shutdownNow();
            }
        } catch (Throwable ignored) {
        }
        try {
            config.save().join();
        } catch (Throwable t) {
            plugin.getLogger().atSevere().withCause(t).log("Failed to save chunk loader config");
        }
    }

    public void registerLoader(String worldName, int blockX, int blockY, int blockZ) {
        int chunkX = Math.floorDiv(blockX, 32);
        int chunkZ = Math.floorDiv(blockZ, 32);

        boolean changed = false;
        synchronized (lock) {
            ChunkLoaderRegistry reg = config.get();
            if (reg == null) {
                return;
            }
            if (reg.entries == null) {
                reg.entries = new HashSet<>();
            } else if (!(reg.entries instanceof HashSet)) {
                // fallback
                reg.entries = new HashSet<>(reg.entries);
            }
            changed = reg.entries.add(new ChunkLoaderEntry(worldName, blockX, blockY, blockZ, chunkX, chunkZ));
        }

        if (changed) {
            try {
                config.save();
            } catch (Throwable t) {
                plugin.getLogger().atSevere().withCause(t).log("Failed to save chunk loader config after register");
            }
        }

        // Best-effort immediate apply for this chunk.
        World world = Universe.get().getWorld(worldName);
        if (world != null) {
            var chunkStore = world.getChunkStore();
            if (chunkStore == null || chunkStore.getStore() == null) {
                return;
            }
            world.getChunkAsync(chunkX, chunkZ).thenAccept(chunk -> {
                if (chunk != null) {
                    chunk.setKeepLoaded(true);
                    chunk.setFlag(ChunkFlag.TICKING, true);
                    chunk.resetKeepAlive();
                }
            });
        }
    }

    private void safeReconcileAll() {
        try {
            reconcileAll();
        } catch (Throwable t) {
            plugin.getLogger().atSevere().withCause(t).log("Chunk loader reconcile failed");
        }
    }

    private void reconcileAll() {
        ChunkLoaderRegistry reg;
        Set<ChunkLoaderEntry> snapshot;
        synchronized (lock) {
            reg = config.get();
            if (reg == null || reg.entries == null || reg.entries.isEmpty()) {
                return;
            }

            // Defensive: ensure entries set is mutable for later prune.
            if (!(reg.entries instanceof HashSet)) {
                reg.entries = new HashSet<>(reg.entries);
            }

            snapshot = new HashSet<>(reg.entries);
        }

        Map<ChunkKey, List<ChunkLoaderEntry>> byChunk = new HashMap<>();
        for (ChunkLoaderEntry e : snapshot) {
            if (e == null) continue;
            if (e.worldName == null) continue;
            byChunk.computeIfAbsent(new ChunkKey(e.worldName, e.chunkX, e.chunkZ), k -> new ArrayList<>()).add(e);
        }

        for (Map.Entry<ChunkKey, List<ChunkLoaderEntry>> group : byChunk.entrySet()) {
            ChunkKey key = group.getKey();
            List<ChunkLoaderEntry> entries = group.getValue();

            World world = Universe.get().getWorld(key.worldName);
            if (world == null) {
                continue;
            }

            // During early startup/shutdown the ChunkStore may exist but not have an initialized backing Store.
            var chunkStore = world.getChunkStore();
            if (chunkStore == null || chunkStore.getStore() == null) {
                continue;
            }

            world.getChunkAsync(key.chunkX, key.chunkZ).thenAccept(chunk -> {
                if (chunk == null) {
                    return;
                }

                // Validate each entry: if the block is no longer a ChunkLoader, remove it.
                boolean changed = false;
                int valid = 0;
                boolean anyActive = false;
                List<ChunkLoaderEntry> stale = new ArrayList<>();

                for (ChunkLoaderEntry e : entries) {
                    if (isChunkLoaderAt(world, e.blockX, e.blockY, e.blockZ)) {
                        valid++;
                        var st = world.getState(e.blockX, e.blockY, e.blockZ, true);
                        if (st instanceof ChunkLoaderBlockState cls && cls.getLoadingMode() == ChunkLoaderBlockState.LoadingMode.Active) {
                            anyActive = true;
                        }
                    } else {
                        stale.add(e);
                    }
                }

                if (!stale.isEmpty()) {
                    synchronized (lock) {
                        ChunkLoaderRegistry reg2 = config.get();
                        if (reg2 != null && reg2.entries != null) {
                            for (ChunkLoaderEntry s : stale) {
                                if (reg2.entries.remove(s)) {
                                    changed = true;
                                }
                            }
                        }
                    }
                }

                if (changed) {
                    try {
                        config.save();
                    } catch (Throwable t) {
                        plugin.getLogger().atSevere().withCause(t).log("Failed to save chunk loader config after prune");
                    }
                }

                // Keep the chunk loaded *and ticking* if any valid loaders remain in it.
                if (valid > 0) {
                    chunk.setKeepLoaded(true);
                    chunk.setFlag(ChunkFlag.TICKING, true);
                    chunk.resetKeepAlive();
                    if (anyActive) {
                        chunk.resetActiveTimer();
                    }
                } else {
                    // No valid loaders left in this chunk; allow it to stop ticking/unload normally.
                    chunk.setKeepLoaded(false);
                    chunk.setFlag(ChunkFlag.TICKING, false);
                }
            });
        }
    }

    private static boolean isChunkLoaderAt(World world, int x, int y, int z) {
        if (world == null) return false;
        if (y < 0 || y >= 320) return false;

        BlockType bt = world.getBlockType(x, y, z);
        if (bt == null) {
            return false;
        }
        String id = bt.getId();
        return Objects.equals(id, CHUNK_LOADER_BLOCKTYPE_ID);
    }

    private static final class ChunkKey {
        private final String worldName;
        private final int chunkX;
        private final int chunkZ;

        private ChunkKey(String worldName, int chunkX, int chunkZ) {
            this.worldName = worldName;
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ChunkKey other)) return false;
            return chunkX == other.chunkX && chunkZ == other.chunkZ && Objects.equals(worldName, other.worldName);
        }

        @Override
        public int hashCode() {
            return Objects.hash(worldName, chunkX, chunkZ);
        }
    }
}
