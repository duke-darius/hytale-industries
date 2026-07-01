package dev.dukedarius.HytaleIndustries.Tooltips;

import dev.dukedarius.HytaleIndustries.HytaleIndustriesPlugin;
import dev.dukedarius.HytaleIndustries.Tooltips.lib.api.SimpleTooltipsApi;
import dev.dukedarius.HytaleIndustries.Tooltips.lib.api.SimpleTooltipsApiProvider;
import dev.dukedarius.HytaleIndustries.Tooltips.lib.api.TooltipData;
import dev.dukedarius.HytaleIndustries.Tooltips.lib.api.TooltipProvider;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Bridge between our ItemTooltipProvider interface and SimpleTooltipsLib.
 * This is the ONLY file that imports org.herolias — everything else uses our own types.
 */
public final class TooltipRegistration {

    private static final List<ItemTooltipProvider> PROVIDERS = new ArrayList<>();

    public static void addProvider(ItemTooltipProvider provider) {
        PROVIDERS.add(provider);
    }

    public static void register() {
        SimpleTooltipsApi api = SimpleTooltipsApiProvider.get();
        if (api == null) {
            HytaleIndustriesPlugin.LOGGER.atWarning().log("[Tooltips] SimpleTooltipsLib API not available");
            return;
        }

        for (ItemTooltipProvider provider : PROVIDERS) {
            api.registerProvider(new LibraryBridge(provider));
            HytaleIndustriesPlugin.LOGGER.atInfo().log("[Tooltips] Registered provider: %s", provider.getId());
        }
    }

    /**
     * Adapts our ItemTooltipProvider to the library's TooltipProvider interface.
     */
    private static class LibraryBridge implements TooltipProvider {
        private final ItemTooltipProvider delegate;

        LibraryBridge(ItemTooltipProvider delegate) {
            this.delegate = delegate;
        }

        @Nonnull
        @Override
        public String getProviderId() {
            return delegate.getId();
        }

        @Override
        public int getPriority() {
            return delegate.getPriority();
        }

        @Nullable
        @Override
        public TooltipData getTooltipData(@Nonnull String itemId, @Nullable String metadata) {
            TooltipLine line = delegate.getTooltip(itemId, metadata);
            if (line == null) return null;

            var builder = TooltipData.builder()
                    .addLine(line.line())
                    .hashInput(line.hashInput());

            if (line.nameOverride() != null) {
                builder.nameOverride(line.nameOverride());
            }

            return builder.build();
        }
    }
}
