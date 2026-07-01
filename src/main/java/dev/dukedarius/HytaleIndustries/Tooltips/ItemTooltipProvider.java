package dev.dukedarius.HytaleIndustries.Tooltips;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Our mod's tooltip provider interface.
 * Implement this instead of the library's TooltipProvider directly.
 */
public interface ItemTooltipProvider {
    @Nonnull String getId();
    int getPriority();
    @Nullable TooltipLine getTooltip(@Nonnull String itemId, @Nullable String metadata);
}
