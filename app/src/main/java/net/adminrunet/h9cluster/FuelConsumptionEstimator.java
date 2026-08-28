package net.adminrunet.h9cluster;

/**
 * Derives fuel flow from the read-only ECM2 cumulative fuel counter.
 *
 * The counter is unsigned 16-bit and wraps naturally. The corresponding GWM
 * Vehicle HAL property exposes 0..131070 for the same signal, confirming the
 * CAN scale of two microlitres per counter step.
 */
final class FuelConsumptionEstimator {
    private static final int MAX_COUNTER = 0xffff;
    private static final int COUNTER_MODULUS = 0x10000;
    private static final float MICROLITERS_PER_COUNT = 2.0f;
    private static final float MICROLITERS_PER_LITER = 1_000_000.0f;
    private static final float MILLISECONDS_PER_HOUR = 3_600_000.0f;
    private static final long MIN_WINDOW_MS = 500L;
    private static final long MAX_SAMPLE_GAP_MS = 2000L;
    private static final float MAX_FLOW_LITERS_PER_HOUR = 100.0f;

    private boolean initialized;
    private int previousCounter;
    private long previousAtMs;
    private int accumulatedDelta;
    private long accumulatedElapsedMs;

    float update(int counter, long receivedAtMs) {
        if (counter < 0 || counter > MAX_COUNTER || receivedAtMs <= 0L) {
            reset();
            return Float.NaN;
        }
        if (!initialized) {
            initialized = true;
            previousCounter = counter;
            previousAtMs = receivedAtMs;
            return Float.NaN;
        }

        long elapsedMs = receivedAtMs - previousAtMs;
        if (elapsedMs <= 0L) {
            return Float.NaN;
        }
        int delta = counter - previousCounter;
        if (delta < 0) {
            delta += COUNTER_MODULUS;
        }
        previousCounter = counter;
        previousAtMs = receivedAtMs;

        float intervalFlow = litersPerHour(delta, elapsedMs);
        if (elapsedMs > MAX_SAMPLE_GAP_MS
                || !Float.isFinite(intervalFlow)
                || intervalFlow > MAX_FLOW_LITERS_PER_HOUR) {
            accumulatedDelta = 0;
            accumulatedElapsedMs = 0L;
            return Float.NaN;
        }

        accumulatedDelta += delta;
        accumulatedElapsedMs += elapsedMs;
        if (accumulatedElapsedMs < MIN_WINDOW_MS) {
            return Float.NaN;
        }

        float flow = litersPerHour(
                accumulatedDelta,
                accumulatedElapsedMs);
        accumulatedDelta = 0;
        accumulatedElapsedMs = 0L;
        return flow <= MAX_FLOW_LITERS_PER_HOUR ? flow : Float.NaN;
    }

    void reset() {
        initialized = false;
        previousCounter = 0;
        previousAtMs = 0L;
        accumulatedDelta = 0;
        accumulatedElapsedMs = 0L;
    }

    static float litersPerHour(int counterDelta, long elapsedMs) {
        if (counterDelta < 0 || elapsedMs <= 0L) {
            return Float.NaN;
        }
        return counterDelta
                * MICROLITERS_PER_COUNT
                * MILLISECONDS_PER_HOUR
                / MICROLITERS_PER_LITER
                / elapsedMs;
    }

    static float forClusterDisplay(float litersPerHour, int speedKph) {
        if (!Float.isFinite(litersPerHour) || litersPerHour < 0.0f) {
            return Float.NaN;
        }
        if (speedKph <= 1) {
            return litersPerHour;
        }
        return litersPerHour * 100.0f / speedKph;
    }
}
