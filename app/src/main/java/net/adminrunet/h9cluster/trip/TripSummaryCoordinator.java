package net.adminrunet.h9cluster.trip;

import net.adminrunet.h9cluster.ClusterState;

/** Coordinates lifecycle detection, accumulation, persistence and one summary. */
public final class TripSummaryCoordinator {
    private static final long PERSIST_INTERVAL_MS = 1_000L;

    public interface Clock {
        long nowMs();
    }

    public interface Listener {
        default void onEngineStartSignal() {
        }

        default void onEngineStarted() {
        }

        void onTripSummary(TripSummary summary);
    }

    private final TripSessionPersistence persistence;
    private final Listener listener;
    private final Clock clock;
    private final EngineRunDetector detector;

    private TripAccumulator accumulator;
    private ClusterState lastState;
    private TripSummary pendingSummary;
    private long lastPersistedAtMs;

    public TripSummaryCoordinator(
            TripSessionPersistence persistence,
            Listener listener,
            Clock clock) {
        this.persistence = persistence;
        this.listener = listener;
        this.clock = clock;
        TripSession restored = persistence.load(clock.nowMs());
        detector = new EngineRunDetector(restored != null);
        if (restored != null) {
            accumulator = TripAccumulator.restore(restored);
            lastPersistedAtMs = restored.lastUpdatedAtMs;
        }
    }

    public void onClusterState(ClusterState state) {
        lastState = state;
        processState(state);
    }

    public void onClockTick() {
        if (lastState != null) {
            processState(lastState);
        }
    }

    private void processState(ClusterState state) {
        if (pendingSummary != null) {
            completePendingSummary();
            return;
        }

        long nowMs = clock.nowMs();
        EngineRunDetector.Event event = detector.update(
                state.rpm,
                state.speedKph,
                state.rpmUpdatedAtMs,
                nowMs);
        TripTelemetry telemetry = TripTelemetry.from(state, nowMs);

        if (event == EngineRunDetector.Event.STARTING) {
            listener.onEngineStartSignal();
            return;
        }

        if (event == EngineRunDetector.Event.STARTED) {
            listener.onEngineStarted();
            accumulator = TripAccumulator.start(nowMs);
            accumulator.update(telemetry);
            persistence.saveSync(accumulator.snapshot());
            lastPersistedAtMs = nowMs;
            return;
        }

        if (accumulator == null) {
            return;
        }

        accumulator.update(telemetry);
        if (event == EngineRunDetector.Event.STOPPED) {
            pendingSummary = accumulator.finish(nowMs);
            completePendingSummary();
            return;
        }

        if (nowMs >= lastPersistedAtMs
                && nowMs - lastPersistedAtMs >= PERSIST_INTERVAL_MS) {
            persistence.saveAsync(accumulator.snapshot());
            lastPersistedAtMs = nowMs;
        }
    }

    public void flush() {
        if (pendingSummary != null) {
            completePendingSummary();
            return;
        }
        if (accumulator != null) {
            persistence.saveSync(accumulator.snapshot());
            lastPersistedAtMs = clock.nowMs();
        }
    }

    public boolean isTripActive() {
        return accumulator != null;
    }

    private void completePendingSummary() {
        if (pendingSummary == null || !persistence.clearSync()) {
            return;
        }
        TripSummary summary = pendingSummary;
        pendingSummary = null;
        accumulator = null;
        listener.onTripSummary(summary);
    }
}
