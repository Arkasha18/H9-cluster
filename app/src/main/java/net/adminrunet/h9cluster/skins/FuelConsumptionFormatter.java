package net.adminrunet.h9cluster.skins;

import net.adminrunet.h9cluster.ClusterState;

import java.util.Locale;

/** Text policy shared by the three custom dashboard skins. */
public final class FuelConsumptionFormatter {
    private FuelConsumptionFormatter() {
    }

    public static String instant(ClusterState state) {
        float value = state.instantFuelConsumption;
        if (!Float.isFinite(value) || value < 0.0f) {
            return "\u2014";
        }
        String unit = state.speedKph <= 1 ? "L/h" : "L/100";
        return String.format(Locale.US, "%.1f %s", value, unit);
    }

    public static String average(float value) {
        return Float.isFinite(value) && value >= 0.0f
                ? String.format(Locale.US, "%.1f L/100", value)
                : "\u2014";
    }
}
