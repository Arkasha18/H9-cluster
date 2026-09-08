package net.adminrunet.h9cluster;

/** Deterministic automatic driving loop used by the emulator-only demo build. */
final class DemoScenario {
    static final long CYCLE_MS = 18_000L;

    private static final float[] SPEED_KEYFRAME_SECONDS = new float[] {
            0.0f,
            1.5f,
            5.5f,
            8.5f,
            10.5f,
            12.5f,
            16.5f,
            18.0f
    };
    private static final float[] SPEED_KEYFRAME_KPH = new float[] {
            0.0f,
            0.0f,
            54.0f,
            86.0f,
            86.0f,
            112.0f,
            0.0f,
            0.0f
    };
    private static final float[] RPM_KEYFRAME_SECONDS = new float[] {
            0.0f,
            1.5f,
            3.1f,
            3.35f,
            5.5f,
            7.2f,
            7.45f,
            8.5f,
            10.5f,
            12.5f,
            16.5f,
            18.0f
    };
    private static final float[] RPM_KEYFRAME_VALUES = new float[] {
            800.0f,
            800.0f,
            3_200.0f,
            2_000.0f,
            2_800.0f,
            3_900.0f,
            2_400.0f,
            2_400.0f,
            2_400.0f,
            4_100.0f,
            800.0f,
            800.0f
    };

    ClusterState snapshot(long elapsedMs, long nowMs) {
        return snapshot(elapsedMs, nowMs, false);
    }

    ClusterState snapshot(
            long elapsedMs,
            long nowMs,
            boolean invalidConsumption) {
        long cycleTimeMs = Math.floorMod(elapsedMs, CYCLE_MS);
        float seconds = cycleTimeMs / 1_000.0f;
        float speed = speedAt(seconds);
        int speedKph = Math.round(speed);
        float steering = 150.0f * sin(
                (float) (seconds * Math.PI * 2.0 / 12.0));
        int gear = gearFor(speed, seconds);
        int rpm = clamp(Math.round(rpmAt(seconds)), 0, 8_000);
        float turnDelta = steering / 100.0f;
        double distanceKm = Math.max(0L, elapsedMs)
                * 65.0 / 3_600_000.0;
        int alertPhase = seconds < 11.0f || seconds >= 16.5f
                ? 0
                : seconds < 14.0f
                ? 1
                : 2;
        int coolantC = alertPhase == 0
                ? 92
                : alertPhase == 1
                ? 115
                : 125;
        float transmissionTemperatureC = alertPhase == 0
                ? 78.0f
                : alertPhase == 1
                ? 112.0f
                : 124.0f;
        float fuelLiters = alertPhase == 0
                ? 47.0f
                : alertPhase == 1
                ? 7.0f
                : 1.0f;
        float frontLeftPressure = alertPhase == 0 ? 2.35f : 1.85f;
        float averageConsumption = alertPhase == 0 ? 14.8f : 21.5f;
        float instantConsumption = speedKph <= 1
                ? 1.1f + 0.1f * sin(seconds * 1.7f)
                : 7.0f + speed * 0.12f
                        + 2.5f * Math.abs(sin(seconds * 0.9f));
        float voltage = alertPhase == 0 ? 13.8f : 11.8f;

        return new ClusterState(
                speedKph,
                rpm,
                gear,
                selectorFor(gear),
                coolantC,
                transmissionTemperatureC,
                fuelLiters,
                421 - Math.round(seconds * 0.3f),
                28_642.0 + distanceKm,
                42.3f + (float) distanceKm,
                167.8f + (float) distanceKm,
                frontLeftPressure,
                2.37f + 0.02f * sin(seconds / 3.0f + 0.5f),
                2.42f + 0.02f * sin(seconds / 3.0f + 1.0f),
                2.40f + 0.02f * sin(seconds / 3.0f + 1.5f),
                invalidConsumption
                        ? Float.NaN
                        : instantConsumption,
                invalidConsumption
                        ? Float.NaN
                        : averageConsumption,
                invalidConsumption
                        ? Float.NaN
                        : averageConsumption,
                voltage,
                18.5f + 0.5f * sin(seconds / 8.0f),
                steering,
                Math.max(0.0f, speed - turnDelta),
                Math.max(0.0f, speed + turnDelta),
                Math.max(0.0f, speed - turnDelta * 0.7f),
                Math.max(0.0f, speed + turnDelta * 0.7f),
                220.0f + 80.0f * sin(seconds / 1.7f),
                nowMs,
                nowMs,
                nowMs,
                nowMs,
                nowMs,
                driveModeAt(seconds));
    }

    ClusterState stoppedSnapshot(
            long frozenElapsedMs,
            long nowMs,
            boolean invalidConsumption) {
        ClusterState frozen =
                snapshot(frozenElapsedMs, nowMs, invalidConsumption);
        return new ClusterState(
                0,
                0,
                0,
                GearSelector.PARK,
                frozen.coolantC,
                frozen.transmissionTemperatureC,
                frozen.fuelLiters,
                frozen.rangeKm,
                frozen.odometerKm,
                frozen.dayKm,
                frozen.tripKm,
                frozen.tyreFrontLeftBar,
                frozen.tyreFrontRightBar,
                frozen.tyreRearLeftBar,
                frozen.tyreRearRightBar,
                0.0f,
                frozen.consumptionLitersPer100Km,
                frozen.journeyAverageFuelConsumption,
                frozen.voltage,
                frozen.outsideTemperatureC,
                frozen.steeringAngleDeg,
                0.0f,
                0.0f,
                0.0f,
                0.0f,
                frozen.engineFlywheelTorque,
                nowMs,
                nowMs,
                nowMs,
                nowMs,
                nowMs,
                frozen.driveMode);
    }

    private static float speedAt(float seconds) {
        return interpolateKeyframes(
                seconds,
                SPEED_KEYFRAME_SECONDS,
                SPEED_KEYFRAME_KPH,
                true);
    }

    private static float rpmAt(float seconds) {
        return interpolateKeyframes(
                seconds,
                RPM_KEYFRAME_SECONDS,
                RPM_KEYFRAME_VALUES,
                false);
    }

    private static float interpolateKeyframes(
            float seconds,
            float[] keyframeSeconds,
            float[] keyframeValues,
            boolean eased) {
        for (int index = 1; index < keyframeSeconds.length; index++) {
            float endSeconds = keyframeSeconds[index];
            if (seconds <= endSeconds) {
                float startSeconds = keyframeSeconds[index - 1];
                float progress = (seconds - startSeconds)
                        / (endSeconds - startSeconds);
                if (eased) {
                    progress = progress * progress * (3.0f - 2.0f * progress);
                }
                return lerp(
                        keyframeValues[index - 1],
                        keyframeValues[index],
                        progress);
            }
        }
        return keyframeValues[keyframeValues.length - 1];
    }

    private static int gearFor(float speed, float seconds) {
        if (seconds >= 16.5f) {
            return 0;
        }
        if (speed < 15.0f) {
            return 1;
        }
        if (speed < 35.0f) {
            return 2;
        }
        if (speed < 55.0f) {
            return 3;
        }
        if (speed <= 75.0f) {
            return 4;
        }
        if (speed < 95.0f) {
            return 5;
        }
        return 6;
    }

    private static String selectorFor(int gear) {
        return gear > 0 ? GearSelector.DRIVE : GearSelector.PARK;
    }

    private static String driveModeAt(float seconds) {
        if (seconds < 6.0f) {
            return "SPORT";
        }
        if (seconds < 13.0f) {
            return "NORMAL";
        }
        return "ECO";
    }

    private static float lerp(float start, float end, float progress) {
        return start + (end - start) * progress;
    }

    private static float sin(float value) {
        return (float) Math.sin(value);
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
