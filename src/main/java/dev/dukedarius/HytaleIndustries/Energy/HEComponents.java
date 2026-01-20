package dev.dukedarius.HytaleIndustries.Energy;

import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.meta.BlockState;
import org.checkerframework.checker.nullness.compatqual.NullableDecl;


public final class HEComponents {

    private HEComponents() {
    }

    @NullableDecl
    public static StoresHE stores(@NullableDecl BlockState state) {
        return (state instanceof StoresHE s) ? s : null;
    }

    @NullableDecl
    public static ReceivesHE receives(@NullableDecl BlockState state) {
        return (state instanceof ReceivesHE r) ? r : null;
    }

    @NullableDecl
    public static TransfersHE transfers(@NullableDecl BlockState state) {
        return (state instanceof TransfersHE t) ? t : null;
    }

    @NullableDecl
    public static StoresHE stores(World world, int x, int y, int z) {
        return stores(world.getState(x, y, z, true));
    }

    @NullableDecl
    public static ReceivesHE receives(World world, int x, int y, int z) {
        return receives(world.getState(x, y, z, true));
    }

    @NullableDecl
    public static TransfersHE transfers(World world, int x, int y, int z) {
        return transfers(world.getState(x, y, z, true));
    }
}
