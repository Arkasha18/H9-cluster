package net.adminrunet.h9cluster.skins.simplered;

import java.util.Locale;

final class SimpleRedLayout {
    static final long TRANSMISSION_TEMPERATURE_STALE_AFTER_MS = 15_000L;
    static final int COLOR_NORMAL = 0xFFF5F5F3;
    static final int COLOR_WARNING = 0xFFFFD54F;
    static final int COLOR_CRITICAL = 0xFFFF4D4D;
    static final float MAX_SPEED_KPH = 200.0f;
    static final float MAX_RPM = 6000.0f;
    static final int SPEED_LABEL_STEP_KPH = 20;
    static final int RPM_LABEL_STEP = 1000;
    static final boolean DRAW_SCALE_UNITS = false;
    static final float LEFT_GAUGE_CENTER_X = 290.0f;
    static final float RIGHT_GAUGE_CENTER_X = 1620.0f;
    static final float GAUGE_CENTER_Y = 442.0f;
    static final float GAUGE_RADIUS = 256.0f;
    static final float SCALE_START_DEGREES = 21.0f;
    static final float SCALE_SWEEP_DEGREES = 190.0f;
    static final float SCALE_START_ANGLE_RADIANS =
            (float) Math.toRadians(SCALE_START_DEGREES);
    static final float SCALE_SWEEP_ANGLE_RADIANS =
            (float) Math.toRadians(SCALE_SWEEP_DEGREES);
    static final float SCALE_END_ANGLE_RADIANS =
            SCALE_START_ANGLE_RADIANS + SCALE_SWEEP_ANGLE_RADIANS;
    /**
     * The scale is a circle cut open at its top and pulled apart, so it
     * runs arc, horizontal insert, arc. The cut sits at the top of the
     * circle because that is the one place the tangent is already
     * horizontal, and so the only place a horizontal insert meets both
     * arcs without a corner. With the 20° tilt it lands at 78 km/h.
     */
    static final float SCALE_SPLIT_ANGLE_RADIANS =
            (float) (Math.PI * 0.5);
    /**
     * Length of the insert, in pixels. The speedometer pulls its far
     * half right and the tachometer pulls its near half left, so the
     * pair reads as one shape and its mirror.
     *
     * <p>The insert is the same length at every radius, so each layer of
     * a gauge is displaced equally and the ring keeps its thickness.</p>
     */
    static final float SCALE_STRETCH_X = 30.0f;
    /** Outline before the cut, and the circular remainder after it. */
    static final float SCALE_LEADING_LENGTH =
            (SCALE_SPLIT_ANGLE_RADIANS - SCALE_START_ANGLE_RADIANS)
                    * GAUGE_RADIUS;
    static final float SCALE_TRAILING_LENGTH =
            (SCALE_END_ANGLE_RADIANS - SCALE_SPLIT_ANGLE_RADIANS)
                    * GAUGE_RADIUS;
    static final float SCALE_TOTAL_LENGTH =
            SCALE_LEADING_LENGTH
                    + SCALE_STRETCH_X
                    + SCALE_TRAILING_LENGTH;
    /** Segments per full outline when sampling the scale into a Path. */
    static final int SCALE_PATH_SEGMENTS = 96;
    static final float MAIN_SCALE_LABEL_OFFSET = 60.0f;
    static final float SCALE_LABEL_TEXT_SIZE = 30.0f;
    static final int SCALE_LABEL_COLOR = 0xFFF4F5F5;
    static final float SCALE_UNIT_TEXT_SIZE = 15.0f;
    static final int SCALE_UNIT_COLOR = 0xFFCBD0D3;

    static final float SCALE_ARC_RADIUS = GAUGE_RADIUS;
    static final float SCALE_ARC_WIDTH = 3.0f;
    static final int SCALE_ARC_COLOR = 0xFFEFF1F1;
    static final float TICK_OUTER_RADIUS = GAUGE_RADIUS - 2.0f;
    static final float TICK_MAJOR_INNER_RADIUS = GAUGE_RADIUS - 27.0f;
    static final float TICK_MINOR_INNER_RADIUS = GAUGE_RADIUS - 16.0f;
    static final float TICK_MAJOR_WIDTH = 3.0f;
    static final float TICK_MINOR_WIDTH = 1.5f;
    static final int TICK_COLOR = 0xFFF1F2F2;
    static final int SPEED_MINOR_TICKS_PER_MAJOR = 4;
    static final int RPM_MINOR_TICKS_PER_MAJOR = 10;

    static final float REDLINE_ARC_RADIUS = GAUGE_RADIUS - 34.0f;
    static final float REDLINE_ARC_WIDTH = 4.0f;
    static final int REDLINE_ARC_COLOR = 0xFFFF1C1C;
    static final float REDLINE_GLOW_WIDTH = 17.0f;
    static final int REDLINE_GLOW_COLOR = 0xDCFF1212;
    /** Skia blur radius approximating the PIL sigma the asset used. */
    static final float REDLINE_GLOW_BLUR_RADIUS = 18.0f;

    /**
     * Opaque annular sector sitting under the tachometer scale only. It
     * hides the factory content behind the scale band while leaving the
     * middle of the gauge transparent. Bounded outside by the scale arc
     * plus a margin, inside by the labels plus a clearance, and extended
     * past both scale ends by the padding below.
     */
    static final float TACH_BACKDROP_RADIUS = GAUGE_RADIUS + 14.0f;
    static final float TACH_BACKDROP_INNER_CLEARANCE = 30.0f;
    static final float TACH_BACKDROP_INNER_RADIUS =
            GAUGE_RADIUS - MAIN_SCALE_LABEL_OFFSET
                    - TACH_BACKDROP_INNER_CLEARANCE;
    static final float TACH_BACKDROP_PADDING_DEGREES = 6.0f;
    static final float TACH_BACKDROP_PADDING_RADIANS =
            (float) Math.toRadians(TACH_BACKDROP_PADDING_DEGREES);
    static final float TACH_BACKDROP_EDGE_BLUR_RADIUS = 20.0f;
    static final int TACH_BACKDROP_COLOR_VEHICLE = 0xFF000000;
    /** Demo builds tint it so its bounds are visible while tuning. */
    static final int TACH_BACKDROP_COLOR_DEMO = 0xFF0044FF;
    /** The band sits at the inner end of the major ticks, so it tracks
     * GAUGE_RADIUS instead of drifting when the gauge is resized. */
    static final float PROGRESS_BAND_RADIUS = TICK_MAJOR_INNER_RADIUS;
    static final float PROGRESS_HALO_WIDTH = 46.0f;
    static final float PROGRESS_CORE_WIDTH = 18.0f;
    /**
     * The band is softened by stacking translucent strokes from
     * PROGRESS_HALO_WIDTH down to PROGRESS_CORE_WIDTH. Overlapping alpha
     * accumulates towards the middle, which fakes a blurred edge without
     * BlurMaskFilter — unreliable on the hardware canvas this layer uses.
     */
    static final int PROGRESS_SOFT_LAYER_COUNT = 6;
    /** Outermost layer: faint, so the halo fades out rather than edges. */
    static final int PROGRESS_SOFT_LAYER_MIN_ALPHA = 34;
    /** Innermost layer, keeping the band as bright as it was before. */
    static final int PROGRESS_SOFT_LAYER_MAX_ALPHA = 160;
    static final float PROGRESS_TIP_BLOOM_RADIUS = 46.0f;
    static final int PROGRESS_TIP_BLOOM_COLOR = 0xFFFFD54F;
    static final int PROGRESS_TIP_BLOOM_ALPHA = 200;
    static final int PROGRESS_START_COLOR = 0x08FF2020;
    static final int PROGRESS_LEADING_COLOR = 0xFFFFD54F;
    /**
     * How far before the band the colour ramp opens, in degrees. The
     * band is painted by a sweep gradient keyed to its own start, so
     * without a lead-in its first pixels would carry PROGRESS_START_COLOR
     * at full transparency and the band would appear to begin above zero.
     * Starting the ramp early lets it reach zero already tinted.
     */
    static final float PROGRESS_GRADIENT_LEAD_IN_DEGREES = 20.0f;
    static final float TEXT_SKEW_X = 0.0f;
    static final float SCALE_LABEL_SKEW_X = -0.12f;
    static final String SCALE_LABEL_FONT_ASSET =
            "fonts/Rajdhani-Medium.ttf";

    static final float GEAR_NUMBER_X = 1000.0f;
    static final float GEAR_NUMBER_BASELINE = 63.0f;

    static final float TYRE_LEFT_X = 1748.0f;
    static final float TYRE_RIGHT_X = 1852.0f;
    static final float TYRE_TEXT_SIZE = 22.0f;
    static final float TYRE_TOP_Y = 40.0f;
    static final float TYRE_BOTTOM_Y = 65.0f;
    /** The wheel sits between the four pressures, so it centres them. */
    static final float STEERING_ICON_X = 1800.0f;
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
    static final float FUEL_LITERS_X = 168.0f;
    static final float FUEL_LITERS_BASELINE = 668.0f;
    static final float COOLANT_X = 1645.0f;
    static final float COOLANT_BASELINE = 665.0f;
    static final float TRANSMISSION_X = 1735.0f;
    static final float TRANSMISSION_BASELINE = 665.0f;
    static final float TRANSMISSION_LABEL_X = 1730.0f;
    static final float TRANSMISSION_LABEL_Y = TRANSMISSION_BASELINE;
    static final float VOLTAGE_X = 1896.0f;
    static final float VOLTAGE_BASELINE = FACTORY_SCALE_BASELINE;

    private SimpleRedLayout() {
    }

    /**
     * The backdrop must be opaque black in the vehicle so it hides the
     * factory content. Demo builds tint it instead, because on a demo
     * background black-on-black gives no clue where it sits.
     */
    static int tachBackdropColor(boolean demoMode) {
        return demoMode
                ? TACH_BACKDROP_COLOR_DEMO
                : TACH_BACKDROP_COLOR_VEHICLE;
    }

    /** Start of the backdrop sector, padded past the start of the scale. */
    static float tachBackdropStartLength() {
        return -TACH_BACKDROP_PADDING_RADIANS * GAUGE_RADIUS;
    }

    static float tachBackdropEndLength() {
        return SCALE_TOTAL_LENGTH
                + TACH_BACKDROP_PADDING_RADIANS * GAUGE_RADIUS;
    }

    /** Labelled intervals on a gauge, which are also its major ticks. */
    static int majorTickIntervals(boolean rightGauge) {
        return rightGauge
                ? Math.round(MAX_RPM / RPM_LABEL_STEP)
                : Math.round(MAX_SPEED_KPH / SPEED_LABEL_STEP_KPH);
    }

    static int minorTicksPerMajor(boolean rightGauge) {
        return rightGauge
                ? RPM_MINOR_TICKS_PER_MAJOR
                : SPEED_MINOR_TICKS_PER_MAJOR;
    }

    static float gaugeCenterX(boolean rightGauge) {
        return rightGauge
                ? RIGHT_GAUGE_CENTER_X
                : LEFT_GAUGE_CENTER_X;
    }

    /**
     * Angle reached after travelling this far along the outline. The
     * insert holds it at the cut, which is what flattens the top: the
     * outline advances while the angle stands still.
     */
    static float angleAtLength(float lengthAlong) {
        if (lengthAlong <= SCALE_LEADING_LENGTH) {
            return SCALE_START_ANGLE_RADIANS
                    + lengthAlong / GAUGE_RADIUS;
        }
        float past = lengthAlong
                - SCALE_LEADING_LENGTH
                - SCALE_STRETCH_X;
        if (past <= 0.0f) {
            return SCALE_SPLIT_ANGLE_RADIANS;
        }
        return SCALE_SPLIT_ANGLE_RADIANS + past / GAUGE_RADIUS;
    }

    /**
     * How far the outline has been pulled sideways by this point. It
     * stands at zero along the first arc, walks across the insert, and
     * holds at SCALE_STRETCH_X along the second.
     */
    static float stretchOffsetAtLength(float lengthAlong) {
        return clamp(
                lengthAlong - SCALE_LEADING_LENGTH,
                0.0f,
                SCALE_STRETCH_X);
    }

    /**
     * Where a gauge stands on the canonical outline after travelling this
     * far along its own.
     *
     * <p>Only one outline is described, the speedometer's. The tachometer
     * is that shape reflected about the vertical, and it walks it
     * backwards so its own zero lands where the speedometer's full scale
     * does — below the centre on the near side, rising to the far top
     * corner. Both scales therefore still read left to right.</p>
     */
    static float canonicalLength(float lengthAlong, boolean rightGauge) {
        return rightGauge
                ? SCALE_TOTAL_LENGTH - lengthAlong
                : lengthAlong;
    }

    /**
     * A point on a gauge at a distance along its outline. Drawing code
     * walks by length rather than by scale fraction, because the
     * tachometer backdrop runs past both ends of the scale.
     */
    static float pointXAt(
            float lengthAlong,
            float radius,
            boolean rightGauge) {
        float along = canonicalLength(lengthAlong, rightGauge);
        float fromCentre =
                -radius * (float) Math.cos(angleAtLength(along))
                        + stretchOffsetAtLength(along);
        return gaugeCenterX(rightGauge)
                + (rightGauge ? -fromCentre : fromCentre);
    }

    static float pointYAt(
            float lengthAlong,
            float radius,
            boolean rightGauge) {
        float along = canonicalLength(lengthAlong, rightGauge);
        return GAUGE_CENTER_Y
                - radius * (float) Math.sin(angleAtLength(along));
    }

    /**
     * Distance along the outline a scale fraction sits at. Reading the
     * scale by length rather than by angle is what spaces the ticks
     * evenly across the insert instead of leaving a bare gap on it.
     */
    static float scaleLength(float fraction) {
        return clamp(fraction, 0.0f, 1.0f) * SCALE_TOTAL_LENGTH;
    }

    static float radialX(
            float fraction,
            float radius,
            boolean rightGauge) {
        return pointXAt(scaleLength(fraction), radius, rightGauge);
    }

    static float radialY(
            float fraction,
            float radius,
            boolean rightGauge) {
        return pointYAt(scaleLength(fraction), radius, rightGauge);
    }

    static float scaleX(float fraction, boolean rightGauge) {
        return radialX(fraction, GAUGE_RADIUS, rightGauge);
    }

    static float scaleY(float fraction, boolean rightGauge) {
        return radialY(fraction, GAUGE_RADIUS, rightGauge);
    }

    /**
     * Rate of change of the scale x against the fraction. Walking the
     * outline by length makes this {@code (sin angle, -cos angle)} times
     * the total length, on the arcs and on the insert alike: there the
     * angle holds at the cut, where the sine is one and the cosine zero,
     * which is exactly a horizontal step. The tangent therefore runs
     * continuously through both joints, and neither reads as a corner.
     *
     * <p>The tachometer walks the outline backwards and mirrored, which
     * flips the sign of the vertical component and leaves the horizontal
     * one alone. Both effects cancel in the inward normal drawScaleText
     * builds from this, so labels sit inside the band on either gauge.</p>
     */
    static float scaleTangentX(float fraction, boolean rightGauge) {
        return (float) (SCALE_TOTAL_LENGTH * Math.sin(tangentAngle(
                fraction,
                rightGauge)));
    }

    static float scaleTangentY(float fraction, boolean rightGauge) {
        double cosine = Math.cos(tangentAngle(fraction, rightGauge));
        return (float) (SCALE_TOTAL_LENGTH
                * (rightGauge ? cosine : -cosine));
    }

    /**
     * Canvas sweep angle of a scale fraction, in degrees from three
     * o'clock. Left unwrapped, so it climbs with the fraction on both
     * gauges even where that carries it past a full turn or below zero:
     * a sweep gradient is positioned by the difference between two of
     * these, which a wrapped angle would get backwards on the mirrored
     * tachometer.
     */
    static float bandAngleDegrees(float fraction, boolean rightGauge) {
        double angle = Math.toDegrees(angleAtLength(
                canonicalLength(scaleLength(fraction), rightGauge)));
        return (float) (rightGauge ? -angle : angle + 180.0);
    }

    private static float tangentAngle(float fraction, boolean rightGauge) {
        return angleAtLength(
                canonicalLength(scaleLength(fraction), rightGauge));
    }

    static float steeringRotation(float angleDeg) {
        if (!Float.isFinite(angleDeg)) {
            return 0.0f;
        }
        return -clamp(angleDeg, -1080.0f, 1080.0f);
    }

    static float speedFraction(float speedKph) {
        return clamp(speedKph / MAX_SPEED_KPH, 0.0f, 1.0f);
    }

    static float rpmFraction(float rpm) {
        return clamp(rpm / MAX_RPM, 0.0f, 1.0f);
    }

    /** Stroke width of a soft band layer, index 0 being the widest. */
    static float progressSoftLayerWidth(int index) {
        if (PROGRESS_SOFT_LAYER_COUNT <= 1) {
            return PROGRESS_CORE_WIDTH;
        }
        int checked = Math.max(
                0,
                Math.min(PROGRESS_SOFT_LAYER_COUNT - 1, index));
        float step = (float) checked / (PROGRESS_SOFT_LAYER_COUNT - 1);
        return PROGRESS_HALO_WIDTH
                + (PROGRESS_CORE_WIDTH - PROGRESS_HALO_WIDTH) * step;
    }

    /** Alpha of a soft band layer, rising as the layers narrow. */
    static int progressSoftLayerAlpha(int index) {
        if (PROGRESS_SOFT_LAYER_COUNT <= 1) {
            return PROGRESS_SOFT_LAYER_MAX_ALPHA;
        }
        int checked = Math.max(
                0,
                Math.min(PROGRESS_SOFT_LAYER_COUNT - 1, index));
        float step = (float) checked / (PROGRESS_SOFT_LAYER_COUNT - 1);
        return Math.round(PROGRESS_SOFT_LAYER_MIN_ALPHA
                + (PROGRESS_SOFT_LAYER_MAX_ALPHA
                - PROGRESS_SOFT_LAYER_MIN_ALPHA) * step);
    }

    /**
     * Segments to sample a stretch of outline with, kept at the density
     * SCALE_PATH_SEGMENTS sets for the whole of it.
     */
    static int pathSegments(float startLength, float endLength) {
        float span = Math.abs(endLength - startLength);
        return Math.max(
                1,
                Math.round(SCALE_PATH_SEGMENTS
                        * span / SCALE_TOTAL_LENGTH));
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
