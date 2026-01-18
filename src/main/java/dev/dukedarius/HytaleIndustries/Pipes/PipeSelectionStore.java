package dev.dukedarius.HytaleIndustries.Pipes;

import com.hypixel.hytale.math.vector.Vector3i;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PipeSelectionStore {
    private PipeSelectionStore() {}

    private static final ConcurrentHashMap<UUID, Vector3i> LAST = new ConcurrentHashMap<>();

    public static void set(UUID playerId, Vector3i pos) {
        LAST.put(playerId, pos);
    }

    public static Vector3i get(UUID playerId) {
        return LAST.get(playerId);
    }
}
