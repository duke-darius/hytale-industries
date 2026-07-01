package dev.dukedarius.HytaleIndustries.Tooltips.lib.api;

import javax.annotation.Nullable;

/**
 * Static accessor for the {@link SimpleTooltipsApi}.
 * <p>
 * The library registers the API instance during its {@code setup()} phase.
 * Mods should call {@link #get()} to obtain the API — it returns {@code null}
 * if the library is not installed or not yet initialized.
 *
 * <pre>{@code
 * SimpleTooltipsApi api = SimpleTooltipsApiProvider.get();
 * if (api != null) {
 *     api.registerProvider(new MyTooltipProvider());
 * }
 * }</pre>
 */
public final class SimpleTooltipsApiProvider {

    private static volatile SimpleTooltipsApi instance;

    private SimpleTooltipsApiProvider() {}

    /**
     * Gets the current API instance, or {@code null} if the library
     * is not loaded.
     */
    @Nullable
    public static SimpleTooltipsApi get() {
        return instance;
    }

    /**
     * Registers the API instance. Called internally by the library during setup.
     * <b>Not intended for external use.</b>
     */
    public static void register(SimpleTooltipsApi api) {
        instance = api;
    }
}
