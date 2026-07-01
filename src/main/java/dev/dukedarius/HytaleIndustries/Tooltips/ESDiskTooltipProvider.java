package dev.dukedarius.HytaleIndustries.Tooltips;

import dev.dukedarius.HytaleIndustries.Components.EnergizedStorage.ESDiskHousingComponent;
import org.bson.BsonArray;
import org.bson.BsonDocument;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Shows disk storage usage in the tooltip.
 * Reads the ESStoredItems metadata that lives on each disk ItemStack.
 */
public class ESDiskTooltipProvider implements ItemTooltipProvider {

    @Nonnull
    @Override
    public String getId() {
        return "hytale_industries:es_disk";
    }

    @Override
    public int getPriority() {
        return 100;
    }

    @Nullable
    @Override
    public TooltipLine getTooltip(@Nonnull String itemId, @Nullable String metadata) {
        if (!ESDiskHousingComponent.isStorageDisk(itemId)) return null;

        long used = 0;
        int types = 0;

        if (metadata != null && !metadata.isEmpty()) {
            try {
                BsonDocument doc = BsonDocument.parse(metadata);
                if (doc.containsKey("ESStoredItems")) {
                    BsonArray items = doc.getArray("ESStoredItems");
                    types = items.size();
                    for (int i = 0; i < items.size(); i++) {
                        BsonDocument entry = items.get(i).asDocument();
                        var qtyVal = entry.get("qty");
                        used += qtyVal.isInt64() ? qtyVal.asInt64().getValue() : qtyVal.asInt32().getValue();
                    }
                }
            } catch (Throwable ignored) {}
        }

        int capacity = getDiskCapacity(itemId);
        String line;
        if (used == 0 && types == 0) {
            line = String.format("<color is=\"#7f93a9\">Storage: Empty (0 / %s)</color>", formatNumber(capacity));
        } else {
            double pct = capacity > 0 ? (used * 100.0 / capacity) : 0;
            String color = pct >= 100 ? "#ff5555" : pct >= 75 ? "#e8a654" : "#48d185";
            line = String.format(
                    "<color is=\"%s\">Storage: %s / %s (%.0f%%)</color>\n<color is=\"#7f93a9\">%d item type%s</color>",
                    color, formatNumber(used), formatNumber(capacity), pct,
                    types, types == 1 ? "" : "s");
        }

        return new TooltipLine(line, "es_disk:" + used + ":" + types);
    }

    private static int getDiskCapacity(String itemId) {
        return switch (itemId) {
            case "HytaleIndustries_ES_1kDisk" -> 1024;
            case "HytaleIndustries_ES_4kDisk" -> 4096;
            case "HytaleIndustries_ES_16kDisk" -> 16384;
            case "HytaleIndustries_ES_64kDisk" -> 65536;
            case "HytaleIndustries_ES_256kDisk" -> 262144;
            case "HytaleIndustries_ES_1MDisk" -> 1048576;
            default -> 0;
        };
    }

    private static String formatNumber(long n) {
        if (n >= 1_000_000) return String.format("%.1fM", n / 1_000_000.0);
        if (n >= 1_000) return String.format("%.1fK", n / 1_000.0);
        return String.valueOf(n);
    }
}
