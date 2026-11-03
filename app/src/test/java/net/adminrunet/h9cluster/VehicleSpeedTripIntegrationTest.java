package net.adminrunet.h9cluster;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;
import net.adminrunet.h9cluster.trip.TripSession;
import net.adminrunet.h9cluster.trip.TripSessionPersistence;
import net.adminrunet.h9cluster.trip.TripSummary;
import net.adminrunet.h9cluster.trip.TripSummaryCoordinator;
import org.junit.Test;

/** Exercises speed freshness together with the complete trip lifecycle. */
public final class VehicleSpeedTripIntegrationTest {
    @Test public void controlLiveMcuStreamAllowsSummary() {
        Fixture f = new Fixture();
        f.stopAndWait(true);
        assertEquals(1, f.summaries.size());
        assertFalse(f.coordinator.isTripActive());
    }

    @Test public void observedZeroSpeedAndStoppedRpmMustSurviveShutdownSilence() {
        Fixture f = new Fixture();
        f.stopAndWait(false);
        assertEquals("The speed indication may expire, but an already observed stop must complete",
                1, f.summaries.size());
        assertFalse(f.coordinator.isTripActive());
    }

    @Test public void shutdownSilenceMustNotMergeNextEngineRunIntoPreviousTrip() {
        Fixture f = new Fixture();
        f.stopAndWait(false);
        int startsBeforeRestart = f.started;
        f.clock.now = 3_600_000;
        f.coordinator.onClusterState(state(800, 20, f.clock.now));
        f.clock.now += 1_000;
        f.coordinator.onClusterState(state(800, 20, f.clock.now));
        assertEquals("A new engine run must create a new trip after the previous observed stop",
                startsBeforeRestart + 1, f.started);
    }

    static ClusterState state(int rpm, int speed, long rpmAt) {
        return new ClusterState(speed, rpm, 0, "P", 80, Float.NaN,
                50f, 500, 20_000d, 10f, 100f,
                2.3f, 2.3f, 2.3f, 2.3f, 9f, 9f, 9f, 14f, 20f, 0f,
                0f, 0f, 0f, 0f, 0f, rpmAt, rpmAt, rpmAt, rpmAt, rpmAt, "NORMAL");
    }

    private static final class Clock implements TripSummaryCoordinator.Clock {
        long now = 1_000;
        @Override public long nowMs() { return now; }
    }

    private static final class Memory implements TripSessionPersistence {
        TripSession stored;
        @Override public TripSession load(long now) { return stored; }
        @Override public void saveAsync(TripSession value) { stored = value; }
        @Override public boolean saveSync(TripSession value) { stored = value; return true; }
        @Override public void clearAsync() { stored = null; }
        @Override public boolean clearSync() { stored = null; return true; }
    }

    private static final class Fixture {
        final Clock clock = new Clock();
        final List<TripSummary> summaries = new ArrayList<>();
        int started;
        final TripSummaryCoordinator coordinator = new TripSummaryCoordinator(
                new Memory(), new TripSummaryCoordinator.Listener() {
                    @Override public void onEngineStarted() { started++; }
                    @Override public void onTripSummary(TripSummary value) { summaries.add(value); }
                }, clock);
        final VehicleSpeedSelector selector = new VehicleSpeedSelector();

        Fixture() {
            coordinator.onClusterState(state(800, 20, clock.now));
            clock.now = 2_000;
            coordinator.onClusterState(state(800, 20, clock.now));
            assertTrue(coordinator.isTripActive());
            assertEquals(1, started);
            selector.onGwm(0, 5_000, true);
            selector.onMcu(0, 10_000);
        }

        void stopAndWait(boolean streamContinues) {
            // The engine sends an explicit zero. No new speed is generated after parking.
            // GWM has already expired; MCU supplied a real zero before the shutdown.
            for (long now = 10_000; now <= 20_000; now += 250) {
                clock.now = now;
                int speed = selector.select(now, streamContinues ? now : 10_000);
                if (now == 10_000) assertEquals(0, speed);
                if (!streamContinues && now == 11_250) assertEquals(-1, speed);
                coordinator.onClusterState(state(0, speed, 10_000));
                coordinator.onClockTick();
            }
        }
    }
}
