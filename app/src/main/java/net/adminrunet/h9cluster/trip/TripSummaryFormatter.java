package net.adminrunet.h9cluster.trip;

import java.util.Locale;

/** Formats trip values without fabricating invalid metrics. */
public final class TripSummaryFormatter {
    private static final String INVALID = "—";
    private static final long MINUTE_MS = 60_000L;
    private static final long HOUR_MS = 60L * MINUTE_MS;

    private TripSummaryFormatter() {
    }

    public static String distance(TripSummary summary) {
        return summary.distanceValid && Double.isFinite(summary.distanceKm)
                ? String.format(Locale.US, "%.1f", summary.distanceKm)
                : INVALID;
    }

    public static String consumption(TripSummary summary) {
        return summary.consumptionValid
                        && Double.isFinite(
                                summary.averageConsumptionLitersPer100Km)
                ? String.format(
                        Locale.US,
                        "%.1f",
                        summary.averageConsumptionLitersPer100Km)
                : INVALID;
    }

    public static String duration(TripSummary summary) {
        if (!summary.durationValid || summary.durationMs < 0L) {
            return INVALID;
        }
        long hours = summary.durationMs / HOUR_MS;
        long minutes = summary.durationMs % HOUR_MS / MINUTE_MS;
        return hours > 0L
                ? hours + " ч " + minutes + " мин"
                : minutes + " мин";
    }
}
