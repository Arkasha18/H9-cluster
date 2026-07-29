package net.adminrunet.h9cluster.skins.simplered;

import java.util.Locale;

final class SimpleRedLayout {
    static final long TRANSMISSION_TEMPERATURE_STALE_AFTER_MS = 15_000L;
    static final int COLOR_NORMAL = 0xFFF5F5F3;
    static final int COLOR_WARNING = 0xFFFFD54F;
    static final int COLOR_CRITICAL = 0xFFFF4D4D;
    static final float MAX_SPEED_KPH = 200.0f;
    static final float MAX_RPM = 6000.0f;
    static final boolean DRAW_SCALE_UNITS = false;
    static final float LEFT_GAUGE_CENTER_X = 360.0f;
    static final float RIGHT_GAUGE_CENTER_X = 1560.0f;
    static final float GAUGE_CENTER_Y = 535.0f;
    static final float GAUGE_RADIUS = 280.0f;
    static final int MAIN_SCALE_POINT_COUNT = 10;
    static final float MAIN_SCALE_LABEL_OFFSET = 72.0f;
    static final float PROGRESS_BAND_RADIUS = 253.0f;
    static final float PROGRESS_HALO_WIDTH = 46.0f;
    static final float PROGRESS_GLOW_WIDTH = 32.0f;
    static final float PROGRESS_CORE_WIDTH = 18.0f;
    static final int PROGRESS_HALO_ALPHA = 140;
    static final int PROGRESS_START_COLOR = 0x08FF2020;
    static final int PROGRESS_LEADING_COLOR = 0xFFFFD54F;
    static final float TEXT_SKEW_X = 0.0f;
    static final float SCALE_LABEL_SKEW_X = -0.12f;
    static final String SCALE_LABEL_FONT_ASSET =
            "fonts/Rajdhani-Medium.ttf";

    static final float GEAR_NUMBER_X = 1035.0f;
    static final float GEAR_NUMBER_BASELINE = 69.0f;

    static final float TYRE_LEFT_X = 1748.0f;
    static final float TYRE_ICON_X = 1800.0f;
    static final float TYRE_ICON_WIDTH = 20.0f;
    static final float TYRE_ICON_HEIGHT = 28.0f;
    static final float TYRE_RIGHT_X = 1852.0f;
    static final float TYRE_TEXT_SIZE = 22.0f;
    static final float TYRE_TOP_Y = 35.0f;
    static final float TYRE_BOTTOM_Y = 60.0f;
    static final float STEERING_ICON_X = TYRE_ICON_X;
    static final float STEERING_ICON_VERTICAL_OFFSET = -3.0f;
    static final float STEERING_ICON_Y =
            (TYRE_TOP_Y + TYRE_BOTTOM_Y) * 0.5f
                    + STEERING_ICON_VERTICAL_OFFSET;
    static final float STEERING_ICON_RADIUS = 18.0f;
    static final float STEERING_RIM_WIDTH = 4.2f;
    static final float STEERING_SPOKE_WIDTH = 5.4f;
    static final float STEERING_HUB_RADIUS = 6.4f;
    static final float STEERING_HUB_HOLE_RADIUS = 2.2f;
    static final float STEERING_T_BAR_HALF_WIDTH = 14.0f;
    static final float STEERING_T_BAR_Y_OFFSET = -1.0f;
    static final float STEERING_T_STEM_LENGTH = 15.0f;
    static final int STEERING_COLOR = 0xFFF9F9F7;

    static final float FACTORY_SCALE_BASELINE = 690.0f;
    static final float CONSUMPTION_X = 24.0f;
    static final float CONSUMPTION_BASELINE = FACTORY_SCALE_BASELINE;
    static final float FUEL_LITERS_X = 170.0f;
    static final float FUEL_LITERS_BASELINE = FACTORY_SCALE_BASELINE - 43.0f;
    static final float COOLANT_X = 1570.0f;
    static final float COOLANT_BASELINE = FACTORY_SCALE_BASELINE - 43.0f;
    static final float TRANSMISSION_X = 1710.0f;
    static final float TRANSMISSION_BASELINE =
            FACTORY_SCALE_BASELINE - 43.0f;
    static final float TRANSMISSION_LABEL_Y =
            TRANSMISSION_BASELINE + 15.0f;
    static final float VOLTAGE_X = 1896.0f;
    static final float VOLTAGE_BASELINE = FACTORY_SCALE_BASELINE;

    private SimpleRedLayout() {
    }

    static float scaleX(float fraction, boolean rightGauge) {
        float checked = clamp(fraction, 0.0f, 1.0f);
        float centerX = rightGauge
                ? RIGHT_GAUGE_CENTER_X
                : LEFT_GAUGE_CENTER_X;
        return centerX
                - GAUGE_RADIUS
                * (float) Math.cos(Math.PI * checked);
    }

    static float scaleY(float fraction) {
        float checked = clamp(fraction, 0.0f, 1.0f);
        return GAUGE_CENTER_Y
                - GAUGE_RADIUS
                * (float) Math.sin(Math.PI * checked);
    }

    static float scaleTangentX(float fraction) {
        float checked = clamp(fraction, 0.0f, 1.0f);
        return (float) (Math.PI
                * GAUGE_RADIUS
                * Math.sin(Math.PI * checked));
    }

    static float scaleTangentY(float fraction) {
        float checked = clamp(fraction, 0.0f, 1.0f);
        return (float) (-Math.PI
                * GAUGE_RADIUS
                * Math.cos(Math.PI * checked));
    }

    static float steeringRotation(float angleDeg) {
        if (!Float.isFinite(angleDeg)) {
            return 0.0f;
        }
        return clamp(angleDeg, -1080.0f, 1080.0f);
    }

    static float speedFraction(float speedKph) {
        return clamp(speedKph / MAX_SPEED_KPH, 0.0f, 1.0f);
    }

    static float rpmFraction(float rpm) {
        return clamp(rpm / MAX_RPM, 0.0f, 1.0f);
    }

    static float progressSweepDegrees(float fraction) {
        return 180.0f * clamp(fraction, 0.0f, 1.0f);
    }

    static String formatGear(int gear) {
        return gear >= 1 && gear <= 8
                ? Integer.toString(gear)
                : "";
    }

    static String formatConsumption(float litersPer100Km) {
        return isPositiveFinite(litersPer100Km)
                ? String.format(Locale.US, "%.1f L", litersPer100Km)
                : "";
    }

    static String formatFuel(float liters) {
        return Float.isFinite(liters) && liters >= 0.0f
                ? Math.round(liters) + " L"
                : "";
    }

    static String formatCoolant(int temperatureC) {
        return temperatureC >= 40 && temperatureC <= 130
                ? temperatureC + " °C"
                : "";
    }

    static String formatVoltage(float voltage) {
        return isPositiveFinite(voltage)
                ? String.format(Locale.US, "%.1f V", voltage)
                : "";
    }

    static String formatPressure(float pressureBar) {
        return isPositiveFinite(pressureBar)
                ? String.format(Locale.US, "%.2f", pressureBar)
                : "";
    }

    static String formatTransmissionTemperature(
            float temperatureC,
            long updatedAtMs,
            long nowMs) {
        boolean fresh = Float.isFinite(temperatureC)
                && updatedAtMs > 0L
                && nowMs >= updatedAtMs
                && nowMs - updatedAtMs
                <= TRANSMISSION_TEMPERATURE_STALE_AFTER_MS;
        return fresh ? Math.round(temperatureC) + " °C" : "";
    }

    static int consumptionColor(float litersPer100Km) {
        return isPositiveFinite(litersPer100Km)
                && litersPer100Km > 20.0f
                ? COLOR_WARNING
                : COLOR_NORMAL;
    }

    static int temperatureColor(float temperatureC) {
        if (Float.isFinite(temperatureC) && temperatureC > 120.0f) {
            return COLOR_CRITICAL;
        }
        return Float.isFinite(temperatureC) && temperatureC > 110.0f
                ? COLOR_WARNING
                : COLOR_NORMAL;
    }

    static int voltageColor(float voltage) {
        return isPositiveFinite(voltage) && voltage < 12.0f
                ? COLOR_WARNING
                : COLOR_NORMAL;
    }

    static int pressureColor(float pressureBar) {
        return isPositiveFinite(pressureBar) && pressureBar < 2.0f
                ? COLOR_WARNING
                : COLOR_NORMAL;
    }

    static int fuelColor(float liters) {
        if (Float.isFinite(liters) && liters >= 0.0f && liters < 2.0f) {
            return COLOR_CRITICAL;
        }
        return Float.isFinite(liters) && liters >= 0.0f && liters < 8.0f
                ? COLOR_WARNING
                : COLOR_NORMAL;
    }

    private static boolean isPositiveFinite(float value) {
        return Float.isFinite(value) && value > 0.0f;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
