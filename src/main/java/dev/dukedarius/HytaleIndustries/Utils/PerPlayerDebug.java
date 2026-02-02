package dev.dukedarius.HytaleIndustries.Utils;

import com.hypixel.hytale.math.matrix.Matrix4d;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.protocol.DebugShape;
import com.hypixel.hytale.protocol.Vector3f;
import com.hypixel.hytale.protocol.packets.player.DisplayDebug;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;

public final class PerPlayerDebug {

    private PerPlayerDebug() {}

    public static void addBoxFor(PlayerRef playerRef,
                                 Vector3d center,
                                 Vector3d halfExtents,
                                 Vector3f color,
                                 float timeSeconds,
                                 boolean fade) {
        Matrix4d matrix = new Matrix4d();
        matrix.identity();
        matrix.translate(center);
        matrix.scale(halfExtents.x, halfExtents.y, halfExtents.z);

        DisplayDebug packet = new DisplayDebug(
                DebugShape.Cube,
                matrix.asFloatData(),
                new com.hypixel.hytale.protocol.Vector3f(color.x, color.y, color.z),
                timeSeconds,
                fade,
                null
        );

        playerRef.getPacketHandler().write(packet);
    }

    public static void addCubeFor(PlayerRef playerRef,
                                  World world,
                                  Vector3d pos,
                                  Vector3f color,
                                  double scale,
                                  float timeSeconds,
                                  boolean fade) {
        Matrix4d matrix = new Matrix4d();
        matrix.identity();
        matrix.translate(pos);
        matrix.scale(scale, scale, scale);

        DisplayDebug packet = new DisplayDebug(
                DebugShape.Cube,
                matrix.asFloatData(),
                new com.hypixel.hytale.protocol.Vector3f(color.x, color.y, color.z),
                timeSeconds,
                fade,
                null // extra matrix for e.g. frustum; not needed for cubes
        );

        playerRef.getPacketHandler().write(packet);
    }
}