package dev.dukedarius.HytaleIndustries.Tooltips;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * A single tooltip contribution from our mod.
 * Wraps the SimpleTooltipsLib API so our providers don't import org.herolias directly.
 */
public record TooltipLine(@Nonnull String line, @Nonnull String hashInput, @Nullable String nameOverride) {
    public TooltipLine(@Nonnull String line, @Nonnull String hashInput) {
        this(line, hashInput, null);
    }
}
