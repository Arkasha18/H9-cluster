package net.adminrunet.h9cluster;

/**
 * Smooths a low-rate vehicle signal and briefly extrapolates its recent motion.
 *
 * Prediction is deliberately bounded in value, velocity and time. It makes a
 * one-hertz signal feel continuous without allowing an old sample to drift.
 */
public final class PredictiveMotionFilter {
    private final float minimum;
    private final float maximum;
    private final float maximumVelocityPerSecond;
    private final long predictionHorizonMs;
    private final float fastTimeConstantMs;
    private final float normalTimeConstantMs;
    private final float fastErrorThreshold;
    private final float snapThreshold;
    private final float noiseThreshold;

    private boolean initialized;
    private float displayedValue;
    private float targetValue;
    private float velocityPerSecond;
    private long lastSampleAtMs;
    private long lastFrameAtMs;

    public PredictiveMotionFilter(
            float minimum,
            float maximum,
            float maximumVelocityPerSecond,
            long predictionHorizonMs,
            float fastTimeConstantMs,
            float normalTimeConstantMs,
            float fastErrorThreshold,
            float snapThreshold,
            float noiseThreshold) {
        this.minimum = minimum;
        this.maximum = maximum;
        this.maximumVelocityPerSecond = maximumVelocityPerSecond;
        this.predictionHorizonMs = predictionHorizonMs;
        this.fastTimeConstantMs = fastTimeConstantMs;
        this.normalTimeConstantMs = normalTimeConstantMs;
        this.fastErrorThreshold = fastErrorThreshold;
        this.snapThreshold = snapThreshold;
        this.noiseThreshold = noiseThreshold;
    }

    public void onSample(float value, long sampleAtMs) {
        float boundedValue = clamp(value, minimum, maximum);
        if (!initialized) {
            initialized = true;
            displayedValue = boundedValue;
            targetValue = boundedValue;
            lastSampleAtMs = sampleAtMs;
            lastFrameAtMs = sampleAtMs;
            return;
        }
        if (sampleAtMs <= lastSampleAtMs) {
            return;
        }

        long elapsedMs = sampleAtMs - lastSampleAtMs;
        float delta = boundedValue - targetValue;
        float rawVelocity = Math.abs(delta) <= noiseThreshold
                ? 0.0f
                : delta * 1000.0f / Math.max(1L, elapsedMs);
        rawVelocity = clamp(
                rawVelocity,
                -maximumVelocityPerSecond,
                maximumVelocityPerSecond);

        boolean directionChanged =
                velocityPerSecond != 0.0f
                        && rawVelocity != 0.0f
                        && Math.signum(velocityPerSecond) != Math.signum(rawVelocity);
        float velocityBlend = directionChanged ? 0.82f : 0.58f;
        velocityPerSecond += (rawVelocity - velocityPerSecond) * velocityBlend;
        if (rawVelocity == 0.0f) {
            velocityPerSecond *= 0.25f;
        }

        targetValue = boundedValue;
        lastSampleAtMs = sampleAtMs;
    }

    public float update(long nowMs) {
        if (!initialized) {
            return targetValue;
        }
        if (lastFrameAtMs <= 0L) {
            lastFrameAtMs = nowMs;
        }

        float elapsedFrameMs =
                Math.min(100.0f, Math.max(0.0f, nowMs - lastFrameAtMs));
        lastFrameAtMs = nowMs;

        long sampleAgeMs = Math.max(0L, nowMs - lastSampleAtMs);
        float predictedValue = targetValue
                + velocityPerSecond
                * Math.min(sampleAgeMs, predictionHorizonMs)
                / 1000.0f;
        predictedValue = clamp(predictedValue, minimum, maximum);

        float error = predictedValue - displayedValue;
        float timeConstant = Math.abs(error) >= fastErrorThreshold
                ? fastTimeConstantMs
                : normalTimeConstantMs;
        float blend = 1.0f - (float) Math.exp(-elapsedFrameMs / timeConstant);
        displayedValue += error * blend;
        if (Math.abs(error) <= snapThreshold && sampleAgeMs >= predictionHorizonMs) {
            displayedValue = predictedValue;
        }
        return displayedValue;
    }

    public boolean needsAnimationFrame(long nowMs) {
        if (!initialized) {
            return false;
        }
        long sampleAgeMs = Math.max(0L, nowMs - lastSampleAtMs);
        if (Math.abs(velocityPerSecond) > 0.01f
                && sampleAgeMs < predictionHorizonMs) {
            return true;
        }
        float predictedValue = targetValue
                + velocityPerSecond
                * Math.min(sampleAgeMs, predictionHorizonMs)
                / 1000.0f;
        predictedValue = clamp(predictedValue, minimum, maximum);
        return Math.abs(predictedValue - displayedValue) > snapThreshold;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
