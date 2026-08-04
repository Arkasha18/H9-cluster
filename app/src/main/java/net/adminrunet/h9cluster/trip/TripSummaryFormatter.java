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

    public static String fuelUsed(TripSummary summary) {
        return summary.fuelUsedValid
                        && Double.isFinite(summary.fuelUsedLiters)
                ? String.format(Locale.US, "%.2f", summary.fuelUsedLiters)
                : INVALID;
    }

    public static String duration(TripSummary summary) {
        DurationParts parts = durationParts(summary);
        if (!parts.valid) {
            return INVALID;
        }
        return parts.hours > 0L
                ? parts.hours + " ч " + parts.minutes + " мин"
                : parts.minutes + " мин";
    }

    public static DurationParts durationParts(TripSummary summary) {
        if (!summary.durationValid || summary.durationMs < 0L) {
            return new DurationParts(false, 0L, 0L);
        }
        return new DurationParts(
                true,
                summary.durationMs / HOUR_MS,
                summary.durationMs % HOUR_MS / MINUTE_MS);
    }

    public static final class DurationParts {
        public final boolean valid;
        public final long hours;
        public final long minutes;

        private DurationParts(boolean valid, long hours, long minutes) {
            this.valid = valid;
            this.hours = hours;
            this.minutes = minutes;
        }
    }
}
