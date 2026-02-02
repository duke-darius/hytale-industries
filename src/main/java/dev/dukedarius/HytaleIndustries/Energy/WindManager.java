package dev.dukedarius.HytaleIndustries.Energy;

import com.hypixel.hytale.server.core.universe.world.World;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Global per-world wind state.
 *
 * Speed is a scalar multiplier in [0.5, 3.0] that affects wind turbine production.
 * Direction is stored in radians [0, 2π) for future use.
 */
public class WindManager {

    private static final double MIN_SPEED = 0.5;
    private static final double MAX_SPEED = 3.0;

    // One full wind cycle period (in seconds) for the speed sinusoid.
    // Slower cycle so wind changes are more gradual.
    private static final double SPEED_PERIOD_SECONDS = 3600.0;
    private static final double SPEED_ANGULAR_VELOCITY = (Math.PI * 2.0) / SPEED_PERIOD_SECONDS;

    // Direction still does a slow random walk for future use.
    private static final double MAX_DIRECTION_DELTA_PER_SECOND = Math.toRadians(10.0); // 10 degrees per second

    private static class WindState {
        double phase = 0.0;          // for speed sinusoid
        double direction = 0.0;      // radians
    }

    private final Map<String, WindState> states = new ConcurrentHashMap<>();
    private final Random random = new Random();

    private WindState getOrCreate(@Nonnull String worldName) {
        return states.computeIfAbsent(worldName, k -> new WindState());
    }

    public double getSpeed(@Nonnull World world) {
        return getSpeed(world.getName());
    }

    public double getSpeed(@Nonnull String worldName) {
        WindState s = getOrCreate(worldName);
        // Map sin(phase) in [-1,1] to [MIN_SPEED, MAX_SPEED]
        double mid = (MIN_SPEED + MAX_SPEED) * 0.5;
        double amp = (MAX_SPEED - MIN_SPEED) * 0.5;
        double v = mid + amp * Math.sin(s.phase);
        return clampSpeed(v);
    }

    public double getDirection(@Nonnull World world) {
        return getDirection(world.getName());
    }

    public double getDirection(@Nonnull String worldName) {
        return normalizeAngle(getOrCreate(worldName).direction);
    }

    public void tickWorld(@Nonnull World world, double dtHintSeconds) {
        String name = world.getName();
        double dtSeconds = dtHintSeconds > 0.0 ? dtHintSeconds : 1.0;

        // Clamp dt so pauses don’t cause huge jumps.
        if (dtSeconds > 1.0) dtSeconds = 1.0;
        if (dtSeconds <= 0.0) return;

        WindState state = getOrCreate(name);

        // Advance phase for smooth sinusoidal speed.
        state.phase += SPEED_ANGULAR_VELOCITY * dtSeconds;

        // Random walk for direction (unused visually for now).
        double maxDirDelta = MAX_DIRECTION_DELTA_PER_SECOND * dtSeconds;
        double deltaDir = (random.nextDouble() * 2.0 - 1.0) * maxDirDelta;
        state.direction = normalizeAngle(state.direction + deltaDir);
    }

    private static double clampSpeed(double v) {
        if (v < MIN_SPEED) return MIN_SPEED;
        if (v > MAX_SPEED) return MAX_SPEED;
        return v;
    }

    private static double normalizeAngle(double a) {
        double twoPi = Math.PI * 2.0;
        a %= twoPi;
        if (a < 0) a += twoPi;
        return a;
    }
}
