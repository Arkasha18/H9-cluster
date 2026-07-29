package net.adminrunet.h9cluster.trip;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import net.adminrunet.h9cluster.ClusterState;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public final class TripSummaryCoordinatorTest {
    @Test
    public void ignoresTelemetryUntilStartIsConfirmed() {
        MutableClock clock = new MutableClock(1_000L);
        MemoryPersistence persistence = new MemoryPersistence();
        RecordingListener listener = new RecordingListener(persistence);
        TripSummaryCoordinator coordinator =
                new TripSummaryCoordinator(persistence, listener, clock);

        coordinator.onClusterState(state(400, 0, 10.0f, 1.0f, clock.nowMs()));

        assertEquals(1, listener.startSignals);
        assertEquals(0, listener.confirmedStarts);
        assertFalse(coordinator.isTripActive());
        assertNull(persistence.stored);
        assertTrue(listener.summaries.isEmpty());
    }

    @Test
    public void confirmedStartCreatesAndSynchronouslyPersistsSession() {
        MutableClock clock = new MutableClock(1_000L);
        MemoryPersistence persistence = new MemoryPersistence();
        RecordingListener listener = new RecordingListener(persistence);
        TripSummaryCoordinator coordinator =
                new TripSummaryCoordinator(persistence, listener, clock);

        coordinator.onClusterState(state(400, 0, 10.0f, 1.0f, clock.nowMs()));
        clock.now = 2_000L;
        coordinator.onClusterState(state(400, 0, 10.0f, 1.0f, clock.nowMs()));

        assertTrue(coordinator.isTripActive());
        assertNotNull(persistence.stored);
        assertEquals(2_000L, persistence.stored.startedAtMs);
        assertEquals(1, persistence.syncSaves);
        assertEquals(1, listener.confirmedStarts);
    }

    @Test
    public void runningTripUpdatesAndThrottlesAsyncPersistence() {
        MutableClock clock = new MutableClock(1_000L);
        MemoryPersistence persistence = new MemoryPersistence();
        TripSummaryCoordinator coordinator = new TripSummaryCoordinator(
                persistence,
                new RecordingListener(persistence),
                clock);
        confirmStart(coordinator, clock, 10.0f);

        clock.now = 2_500L;
        coordinator.onClusterState(state(800, 60, 10.1f, 10.0f, clock.nowMs()));
        assertEquals(0, persistence.asyncSaves);

        clock.now = 3_000L;
        coordinator.onClusterState(state(800, 60, 10.2f, 10.0f, clock.nowMs()));
        assertEquals(1, persistence.asyncSaves);
        assertEquals(0.2, persistence.stored.distanceKm, 0.0001);
    }

    @Test
    public void recreationRestoresRunningTripAndContinuesDistance() {
        MutableClock clock = new MutableClock(1_000L);
        MemoryPersistence persistence = new MemoryPersistence();
        TripSummaryCoordinator first = new TripSummaryCoordinator(
                persistence,
                new RecordingListener(persistence),
                clock);
        confirmStart(first, clock, 10.0f);
        clock.now = 3_000L;
        first.onClusterState(state(800, 60, 10.2f, 10.0f, clock.nowMs()));
        first.flush();

        RecordingListener listener = new RecordingListener(persistence);
        TripSummaryCoordinator restored =
                new TripSummaryCoordinator(persistence, listener, clock);
        assertTrue(restored.isTripActive());

        clock.now = 4_000L;
        restored.onClusterState(state(0, 0, 10.3f, 1.0f, clock.nowMs()));
        clock.now = 6_500L;
        restored.onClusterState(state(0, 0, 10.3f, 1.0f, clock.nowMs()));

        assertEquals(1, listener.summaries.size());
        assertEquals(0.3, listener.summaries.get(0).distanceKm, 0.0001);
    }

    @Test
    public void confirmedStopClearsBeforeEmittingExactlyOnce() {
        MutableClock clock = new MutableClock(1_000L);
        MemoryPersistence persistence = new MemoryPersistence();
        RecordingListener listener = new RecordingListener(persistence);
        TripSummaryCoordinator coordinator =
                new TripSummaryCoordinator(persistence, listener, clock);
        confirmStart(coordinator, clock, 10.0f);

        clock.now = 3_000L;
        coordinator.onClusterState(state(0, 0, 10.1f, 1.0f, clock.nowMs()));
        clock.now = 5_500L;
        coordinator.onClusterState(state(0, 0, 10.1f, 1.0f, clock.nowMs()));
        clock.now = 6_000L;
        coordinator.onClusterState(state(0, 0, 10.1f, 1.0f, clock.nowMs()));

        assertFalse(coordinator.isTripActive());
        assertNull(persistence.stored);
        assertEquals(1, persistence.syncClears);
        assertEquals(1, listener.summaries.size());
        assertTrue(listener.storageWasClearAtCallback);
    }

    @Test
    public void invalidFuelDoesNotInvalidateDistanceOrDuration() {
        MutableClock clock = new MutableClock(1_000L);
        MemoryPersistence persistence = new MemoryPersistence();
        RecordingListener listener = new RecordingListener(persistence);
        TripSummaryCoordinator coordinator =
                new TripSummaryCoordinator(persistence, listener, clock);
        confirmStart(coordinator, clock, 10.0f, Float.NaN);

        clock.now = 3_000L;
        coordinator.onClusterState(state(
                0, 0, 10.1f, Float.NaN, Float.NaN, clock.nowMs()));
        clock.now = 5_500L;
        coordinator.onClusterState(state(
                0, 0, 10.1f, Float.NaN, Float.NaN, clock.nowMs()));

        TripSummary summary = listener.summaries.get(0);
        assertTrue(summary.distanceValid);
        assertFalse(summary.consumptionValid);
        assertTrue(summary.durationValid);
    }

    @Test
    public void clockTickCompletesStaleRpmStopWithoutAnotherDataEvent() {
        MutableClock clock = new MutableClock(1_000L);
        MemoryPersistence persistence = new MemoryPersistence();
        RecordingListener listener = new RecordingListener(persistence);
        TripSummaryCoordinator coordinator =
                new TripSummaryCoordinator(persistence, listener, clock);
        confirmStart(coordinator, clock, 10.0f);

        clock.now = 4_000L;
        coordinator.onClusterState(state(
                800,
                0,
                10.1f,
                Float.NaN,
                9.0f,
                2_000L,
                clock.nowMs()));
        clock.now = 5_500L;
        coordinator.onClockTick();

        assertEquals(1, listener.summaries.size());
        assertFalse(coordinator.isTripActive());
    }

    @Test
    public void retriesFailedSessionClearBeforeEmittingSummary() {
        MutableClock clock = new MutableClock(1_000L);
        MemoryPersistence persistence = new MemoryPersistence();
        RecordingListener listener = new RecordingListener(persistence);
        TripSummaryCoordinator coordinator =
                new TripSummaryCoordinator(persistence, listener, clock);
        confirmStart(coordinator, clock, 10.0f);

        persistence.clearSucceeds = false;
        clock.now = 3_000L;
        coordinator.onClusterState(state(0, 0, 10.1f, 1.0f, clock.nowMs()));
        clock.now = 5_500L;
        coordinator.onClusterState(state(0, 0, 10.1f, 1.0f, clock.nowMs()));

        assertTrue(coordinator.isTripActive());
        assertTrue(listener.summaries.isEmpty());
        assertNotNull(persistence.stored);

        persistence.clearSucceeds = true;
        clock.now = 6_000L;
        coordinator.onClockTick();

        assertFalse(coordinator.isTripActive());
        assertEquals(1, listener.summaries.size());
        assertTrue(listener.storageWasClearAtCallback);
    }

    @Test
    public void flushDoesNotOverwritePendingStopAfterFailedClear() {
        MutableClock clock = new MutableClock(1_000L);
        MemoryPersistence persistence = new MemoryPersistence();
        RecordingListener listener = new RecordingListener(persistence);
        TripSummaryCoordinator coordinator =
                new TripSummaryCoordinator(persistence, listener, clock);
        confirmStart(coordinator, clock, 10.0f);

        persistence.clearSucceeds = false;
        clock.now = 3_000L;
        coordinator.onClusterState(state(0, 0, 10.1f, 1.0f, clock.nowMs()));
        clock.now = 5_500L;
        coordinator.onClusterState(state(0, 0, 10.1f, 1.0f, clock.nowMs()));
        int savesBeforeFlush = persistence.syncSaves;

        coordinator.flush();

        assertEquals(savesBeforeFlush, persistence.syncSaves);
        assertTrue(listener.summaries.isEmpty());
    }

    @Test
    public void flushSynchronouslyStoresLatestActiveSession() {
        MutableClock clock = new MutableClock(1_000L);
        MemoryPersistence persistence = new MemoryPersistence();
        TripSummaryCoordinator coordinator = new TripSummaryCoordinator(
                persistence,
                new RecordingListener(persistence),
                clock);
        confirmStart(coordinator, clock, 10.0f);
        clock.now = 2_500L;
        coordinator.onClusterState(state(800, 60, 10.1f, 10.0f, clock.nowMs()));

        coordinator.flush();

        assertEquals(2, persistence.syncSaves);
        assertEquals(0.1, persistence.stored.distanceKm, 0.0001);
    }

    private static void confirmStart(
            TripSummaryCoordinator coordinator,
            MutableClock clock,
            float journeyKm) {
        confirmStart(coordinator, clock, journeyKm, 9.0f);
    }

    private static void confirmStart(
            TripSummaryCoordinator coordinator,
            MutableClock clock,
            float journeyKm,
            float averageFuel) {
        clock.now = 1_000L;
        coordinator.onClusterState(
                state(400, 0, journeyKm, 1.0f, averageFuel, clock.nowMs()));
        clock.now = 2_000L;
        coordinator.onClusterState(
                state(400, 0, journeyKm, 1.0f, averageFuel, clock.nowMs()));
    }

    private static ClusterState state(
            int rpm,
            int speedKph,
            float journeyKm,
            float instantFuel,
            long nowMs) {
        return state(
                rpm,
                speedKph,
                journeyKm,
                instantFuel,
                9.0f,
                nowMs);
    }

    private static ClusterState state(
            int rpm,
            int speedKph,
            float journeyKm,
            float instantFuel,
            float averageFuel,
            long nowMs) {
        return state(
                rpm,
                speedKph,
                journeyKm,
                instantFuel,
                averageFuel,
                nowMs,
                nowMs);
    }

    private static ClusterState state(
            int rpm,
            int speedKph,
            float journeyKm,
            float instantFuel,
            float averageFuel,
            long rpmUpdatedAtMs,
            long nowMs) {
        return new ClusterState(
                speedKph,
                rpm,
                speedKph == 0 ? 0 : 3,
                80,
                Float.NaN,
                50.0f,
                500,
                20_000.0,
                journeyKm,
                100.0f,
                2.3f,
                2.3f,
                2.3f,
                2.3f,
                averageFuel,
                instantFuel,
                14.0f,
                20.0f,
                0.0f,
                speedKph,
                speedKph,
                speedKph,
                speedKph,
                100.0f,
                rpmUpdatedAtMs,
                nowMs,
                nowMs,
                nowMs,
                nowMs,
                "NORMAL");
    }

    private static final class MutableClock
            implements TripSummaryCoordinator.Clock {
        long now;

        MutableClock(long now) {
            this.now = now;
        }

        @Override
        public long nowMs() {
            return now;
        }
    }

    private static final class MemoryPersistence
            implements TripSessionPersistence {
        TripSession stored;
        int syncSaves;
        int asyncSaves;
        int syncClears;
        boolean clearSucceeds = true;

        @Override
        public TripSession load(long nowMs) {
            return TripSessionNormalizer.normalize(stored, nowMs);
        }

        @Override
        public void saveAsync(TripSession session) {
            stored = session;
            asyncSaves++;
        }

        @Override
        public boolean saveSync(TripSession session) {
            stored = session;
            syncSaves++;
            return true;
        }

        @Override
        public void clearAsync() {
            stored = null;
        }

        @Override
        public boolean clearSync() {
            syncClears++;
            if (clearSucceeds) {
                stored = null;
            }
            return clearSucceeds;
        }
    }

    private static final class RecordingListener
            implements TripSummaryCoordinator.Listener {
        final MemoryPersistence persistence;
        final List<TripSummary> summaries = new ArrayList<>();
        int startSignals;
        int confirmedStarts;
        boolean storageWasClearAtCallback;

        RecordingListener(MemoryPersistence persistence) {
            this.persistence = persistence;
        }

        @Override
        public void onEngineStartSignal() {
            startSignals++;
        }

        @Override
        public void onEngineStarted() {
            confirmedStarts++;
        }

        @Override
        public void onTripSummary(TripSummary summary) {
            storageWasClearAtCallback = persistence.stored == null;
            summaries.add(summary);
        }
    }
}
