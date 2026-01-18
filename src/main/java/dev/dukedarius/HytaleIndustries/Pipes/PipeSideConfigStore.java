package dev.dukedarius.HytaleIndustries.Pipes;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Temporary persistence for pipe per-side config.
 *
 * The engine recreates BlockState components when connected-block variants change, which wipes custom fields.
 * We keep the authoritative sideConfig here, keyed by block position.
 */
public final class PipeSideConfigStore {
    private PipeSideConfigStore() {}

    private static final ConcurrentHashMap<Long, Integer> CONFIG = new ConcurrentHashMap<>();

    // Pack world coords into a single long (26-bit X/Z, 12-bit Y). Matches earlier pipe traversal packing.
    public static long key(int x, int y, int z) {
        long lx = (long) x & 0x3FFFFFFL;
        long lz = (long) z & 0x3FFFFFFL;
        long ly = (long) y & 0xFFFL;
        return (lx << 38) | (lz << 12) | ly;
    }

    public static int getOrDefault(int x, int y, int z, int fallback) {
        return CONFIG.getOrDefault(key(x, y, z), fallback);
    }

    public static int get(int x, int y, int z) {
        return CONFIG.getOrDefault(key(x, y, z), 0);
    }

    public static void set(int x, int y, int z, int sideConfig) {
        CONFIG.put(key(x, y, z), sideConfig);
    }

    public static void clear(int x, int y, int z) {
        CONFIG.remove(key(x, y, z));
    }
}
