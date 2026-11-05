package net.adminrunet.h9cluster;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;

import org.junit.Test;

public final class StationarySpeedDisplayFallbackTest {
    private static final long NOW_MS = 100_000L;

    @Test
    public void showsZeroOnlyForUnknownSpeedWithFreshRunningEngineAndPark() {
        ClusterState raw = state(-1).withTboxVehicleState(
                ClusterState.ENGINE_STATE_RUNNING,
                NOW_MS - 15_000L,
                true,
                true,
                NOW_MS - 15_000L);

        assertTrue(StationarySpeedDisplayFallback.isActive(raw, NOW_MS));
        assertEquals(0, StationarySpeedDisplayFallback.resolveSpeedKph(raw, NOW_MS));
        assertEquals(-1, raw.speedKph);
    }

    @Test
    public void preservesEveryMeasuredSpeed() {
        for (int speedKph : new int[] {0, 1, 86}) {
            ClusterState raw = state(speedKph).withTboxVehicleState(
                    ClusterState.ENGINE_STATE_RUNNING,
                    NOW_MS,
                    true,
                    true,
                    NOW_MS);

            assertFalse(StationarySpeedDisplayFallback.isActive(raw, NOW_MS));
            assertEquals(speedKph,
                    StationarySpeedDisplayFallback.resolveSpeedKph(raw, NOW_MS));
            assertSame(raw, raw.withStationaryDisplaySpeed());
        }
    }

    @Test
    public void refusesStoppedEngineNonParkStaleAndFutureEvidence() {
        assertUnavailable(state(-1).withTboxVehicleState(
                ClusterState.ENGINE_STATE_STOPPED,
                NOW_MS,
                true,
                true,
                NOW_MS));
        assertUnavailable(state(-1).withTboxVehicleState(
                ClusterState.ENGINE_STATE_RUNNING,
                NOW_MS,
                true,
                false,
                NOW_MS));
        assertUnavailable(state(-1).withTboxVehicleState(
                ClusterState.ENGINE_STATE_RUNNING,
                NOW_MS - 15_001L,
                true,
                true,
                NOW_MS));
        assertUnavailable(state(-1).withTboxVehicleState(
                ClusterState.ENGINE_STATE_RUNNING,
                NOW_MS,
                true,
                true,
                NOW_MS - 15_001L));
        assertUnavailable(state(-1).withTboxVehicleState(
                ClusterState.ENGINE_STATE_RUNNING,
                NOW_MS + 1L,
                true,
                true,
                NOW_MS));
        assertUnavailable(state(-1).withTboxVehicleState(
                ClusterState.ENGINE_STATE_RUNNING,
                NOW_MS,
                true,
                true,
                NOW_MS + 1L));
    }

    @Test
    public void displayProjectionChangesOnlySpeedAndKeepsRawSnapshotUntouched()
            throws Exception {
        ClusterState raw = state(-1).withFuelTankCapacity(55).withTboxVehicleState(
                ClusterState.ENGINE_STATE_RUNNING,
                NOW_MS,
                true,
                true,
                NOW_MS);
        ClusterState display = raw.withStationaryDisplaySpeed();

        assertNotSame(raw, display);
        assertEquals(-1, raw.speedKph);
        assertEquals(0, display.speedKph);
        for (Field field : ClusterState.class.getFields()) {
            if (!field.getName().equals("speedKph")) {
                assertEquals(field.getName(), field.get(raw), field.get(display));
            }
        }
    }

    @Test
    public void nullStateNeverCreatesStationaryEvidence() {
        assertFalse(StationarySpeedDisplayFallback.isActive(null, NOW_MS));
        assertEquals(-1, StationarySpeedDisplayFallback.resolveSpeedKph(null, NOW_MS));
    }

    private static void assertUnavailable(ClusterState state) {
        assertFalse(StationarySpeedDisplayFallback.isActive(state, NOW_MS));
        assertEquals(-1, StationarySpeedDisplayFallback.resolveSpeedKph(state, NOW_MS));
    }

    private static ClusterState state(int speedKph) {
        return new ClusterState(
                speedKph,
                1_200,
                1,
                GearSelector.PARK,
                72,
                48.0f,
                40.0f,
                500,
                10_000.0,
                12.0f,
                34.0f,
                2.3f,
                2.3f,
                2.4f,
                2.4f,
                1.0f,
                12.0f,
                12.0f,
                13.8f,
                5.0f,
                0.0f,
                0.0f,
                0.0f,
                0.0f,
                0.0f,
                0.0f,
                NOW_MS,
                NOW_MS,
                NOW_MS,
                NOW_MS,
                NOW_MS,
                "ECO");
    }
}
