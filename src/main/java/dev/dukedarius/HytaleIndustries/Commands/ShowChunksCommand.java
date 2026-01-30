package dev.dukedarius.HytaleIndustries.Commands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.matrix.Matrix4d;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.protocol.DebugShape;
import com.hypixel.hytale.protocol.Vector3f;
import com.hypixel.hytale.protocol.packets.player.DisplayDebug;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.HashSet;
import java.util.Set;

/**
 * /hi_showchunks
 * Shows vertical debug lines at the corners of chunks in a 10-chunk radius
 * around the invoking player. Visible only to that player.
 */
public class ShowChunksCommand extends AbstractPlayerCommand {

    private static final int RADIUS_CHUNKS = 10;
    private static final float DURATION_SECONDS = 10.0f;

    public ShowChunksCommand() {
        super("hi_showchunks", "Show chunk borders around you using debug shapes.");
    }

    @Override
    protected void execute(@Nonnull CommandContext context,
                           @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref,
                           @Nonnull PlayerRef playerRef,
                           @Nonnull World world) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }

        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) {
            return;
        }

        Vector3d pos = transform.getPosition();

        int centerChunkX = ChunkUtil.chunkCoordinate(pos.x);
        int centerChunkZ = ChunkUtil.chunkCoordinate(pos.z);

        // Collect unique world-space block corners so we don't draw duplicates.
        Set<Long> corners = new HashSet<>();

        for (int dx = -RADIUS_CHUNKS; dx <= RADIUS_CHUNKS; dx++) {
            for (int dz = -RADIUS_CHUNKS; dz <= RADIUS_CHUNKS; dz++) {
                int cx = centerChunkX + dx;
                int cz = centerChunkZ + dz;

                int minX = cx << 5;      // 32 blocks per chunk
                int minZ = cz << 5;
                int maxX = minX + 32;
                int maxZ = minZ + 32;

                addCorner(corners, minX, minZ);
                addCorner(corners, minX, maxZ);
                addCorner(corners, maxX, minZ);
                addCorner(corners, maxX, maxZ);
            }
        }

        // Visual parameters for the vertical lines.
        Vector3f color = new Vector3f(0.0f, 1.0f, 1.0f); // cyan
        double halfWidth = 0.2;      // thickness of the line
        double halfHeight = 64.0;    // vertical half-extent (total ~128 blocks)
        boolean fade = true;

        for (long packed : corners) {
            int cornerX = (int) (packed >> 32);
            int cornerZ = (int) packed;

            double centerX = cornerX + 0.5; // center of block
            double centerZ = cornerZ + 0.5;
            double centerY = pos.y;         // around player height

            Matrix4d matrix = new Matrix4d();
            matrix.identity();
            matrix.translate(new Vector3d(centerX, centerY, centerZ));
            // Non-uniform scale: tall, thin column.
            matrix.scale(halfWidth, halfHeight, halfWidth);

            DisplayDebug packet = new DisplayDebug(
                    DebugShape.Cube,
                    matrix.asFloatData(),
                    new Vector3f(color.x, color.y, color.z),
                    DURATION_SECONDS,
                    fade,
                    null
            );

            playerRef.getPacketHandler().write(packet);
        }
    }

    private static void addCorner(Set<Long> corners, int x, int z) {
        long packed = (((long) x) << 32) | (z & 0xffffffffL);
        corners.add(packed);
    }
}
