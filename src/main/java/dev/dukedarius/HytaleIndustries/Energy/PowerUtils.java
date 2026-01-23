package dev.dukedarius.HytaleIndustries.Energy;

import java.util.Locale;

public final class PowerUtils {

    private PowerUtils() {}

    /**
     * Formats a power/energy amount (e.g. HE) into a compact human-readable string.
     * Examples: 999 -> "999", 1000 -> "1K", 10000 -> "10K", 10000000 -> "10M".
     */
    public static String formatCompact(long value) {
        long abs = Math.abs(value);
        String sign = value < 0 ? "-" : "";

        if (abs < 1_000L) {
            return sign + abs;
        }

        Unit unit = Unit.K;
        for (Unit u : Unit.values()) {
            if (abs >= u.scale) {
                unit = u;
            }
        }

        double scaled = abs / (double) unit.scale;

        String num;
        // Keep it simple/clean:
        // - Show no decimals when it's effectively whole, or it's already large (>= 100).
        // - Otherwise show 1 decimal.
        if (scaled >= 100.0 || isEffectivelyWhole(scaled)) {
            num = Long.toString(Math.round(scaled));
        } else {
            num = String.format(Locale.ROOT, "%.1f", scaled);
            if (num.endsWith(".0")) {
                num = num.substring(0, num.length() - 2);
            }
        }

        return sign + num + unit.suffix;
    }

    public static String formatHe(double he) {
        long v = (long) Math.floor(he);
        return formatCompact(v);
    }

    private static boolean isEffectivelyWhole(double v) {
        return Math.abs(v - Math.rint(v)) < 1e-9;
    }

    private enum Unit {
        K(1_000L, "K"),
        M(1_000_000L, "M"),
        B(1_000_000_000L, "G"),
        T(1_000_000_000_000L, "T"),
        Q(1_000_000_000_000_000L, "P"),
        Qi(1_000_000_000_000_000_000L, "E");

        private final long scale;
        private final String suffix;

        Unit(long scale, String suffix) {
            this.scale = scale;
            this.suffix = suffix;
        }
    }
}
