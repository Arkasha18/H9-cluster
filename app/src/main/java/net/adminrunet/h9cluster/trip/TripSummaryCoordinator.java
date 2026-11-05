package net.adminrunet.h9cluster.trip;

import net.adminrunet.h9cluster.ClusterState;
import net.adminrunet.h9cluster.DiagnosticLog;

/** Coordinates lifecycle detection, accumulation, persistence and one summary. */
public final class TripSummaryCoordinator {
    private static final long PERSIST_INTERVAL_MS = 1_000L;
    private static final long FAILURE_LOG_INTERVAL_MS = 60_000L;

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
    private long lastSummaryClearFailureLogAtMs = -1L;

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
            DiagnosticLog.info(
                    "trip",
                    "session_restored",
                    "last_updated_ms", String.valueOf(restored.lastUpdatedAtMs));
        } else {
            DiagnosticLog.info("trip", "no_persisted_session");
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
            DiagnosticLog.info("trip", "session_started");
            return;
        }

        if (accumulator == null) {
            return;
        }

        accumulator.update(telemetry);
        if (event == EngineRunDetector.Event.STOPPED) {
            pendingSummary = accumulator.finish(nowMs);
            DiagnosticLog.info("trip", "summary_pending");
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
            DiagnosticLog.info("trip", "session_flushed");
        }
    }

    public boolean isTripActive() {
        return accumulator != null;
    }

    private void completePendingSummary() {
        if (pendingSummary == null) {
            return;
        }
        if (!persistence.clearSync()) {
            long nowMs = clock.nowMs();
            if (lastSummaryClearFailureLogAtMs < 0L
                    || nowMs < lastSummaryClearFailureLogAtMs
                    || nowMs - lastSummaryClearFailureLogAtMs
                            >= FAILURE_LOG_INTERVAL_MS) {
                DiagnosticLog.warn("trip", "summary_blocked_by_persistence_clear");
                lastSummaryClearFailureLogAtMs = nowMs;
            }
            return;
        }
        TripSummary summary = pendingSummary;
        pendingSummary = null;
        accumulator = null;
        DiagnosticLog.info(
                "trip",
                "summary_ready",
                "duration_ms", String.valueOf(summary.durationMs),
                "distance_km", String.valueOf(summary.distanceKm));
        listener.onTripSummary(summary);
    }
}
