package net.adminrunet.h9cluster.trip;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Locale;

public final class TripTelemetryDiagnosticsTest {
    @Test
    public void formatsOnlySafeNumericValidationFields() {
        TripTelemetry telemetry = new TripTelemetry(
                12_345L,
                60,
                42.3f,
                42.3,
                true,
                8.7f,
                true);

        String line = TripTelemetryDiagnostics.format(
                telemetry,
                telemetry.journeyOdometerKm,
                800,
                12_000L);
        String normalized = line.toLowerCase(Locale.US);

        assertTrue(line.contains("journeyRaw=42.300"));
        assertTrue(line.contains("journeyKm=42.300"));
        assertTrue(line.contains("journeyValid=true"));
        assertFalse(line.contains("instantFuelRaw"));
        assertTrue(line.contains("averageFuelRaw=8.700"));
        assertTrue(line.contains("averageFuelValid=true"));
        assertTrue(line.contains("rpmRaw=800"));
        assertTrue(line.contains("rpmAgeMs=345"));
        assertTrue(line.contains("speedKph=60"));
        assertTrue(line.contains("elapsedMs=12345"));
        assertFalse(normalized.contains("vin"));
        assertFalse(normalized.contains("latitude"));
        assertFalse(normalized.contains("longitude"));
        assertFalse(normalized.contains("password"));
        assertFalse(normalized.contains("tbox"));
        assertFalse(normalized.contains("binder"));
    }

    @Test
    public void logsAtMostOncePerSecondAndRecoversAfterClockReset() {
        assertTrue(TripTelemetryDiagnostics.shouldLog(true, -1L, 500L));
        assertFalse(TripTelemetryDiagnostics.shouldLog(true, 1_000L, 1_999L));
        assertTrue(TripTelemetryDiagnostics.shouldLog(true, 1_000L, 2_000L));
        assertTrue(TripTelemetryDiagnostics.shouldLog(true, 2_000L, 1_000L));
    }

    @Test
    public void neverLogsPeriodicallyWhenDiagnosticsAreNotOptedIn() {
        assertFalse(TripTelemetryDiagnostics.shouldLog(false, -1L, 500L));
        assertFalse(TripTelemetryDiagnostics.shouldLog(false, 1_000L, 2_000L));
        assertFalse(TripTelemetryDiagnostics.shouldLog(false, 2_000L, 1_000L));
        assertFalse(
                TripTelemetryDiagnostics.shouldLog(false, 0L, 3_600_000L));
    }
}
