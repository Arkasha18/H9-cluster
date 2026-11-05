package net.adminrunet.h9cluster.trip;

import net.adminrunet.h9cluster.ClusterState;
import net.adminrunet.h9cluster.DiagnosticLog;

/** Detects confirmed engine start and stop transitions from fresh RPM samples. */
public final class EngineRunDetector {
    private static final int START_RPM = 400;
    private static final int STOP_RPM = 50;
    private static final long RPM_FRESH_MS = 1_000L;
    private static final long START_HOLD_MS = 1_000L;
    private static final long STOP_HOLD_MS = 1_500L;
    private static final long STOP_SPEED_EVIDENCE_MS = 60_000L;
    private static final long VEHICLE_STATE_FRESH_MS = 15_000L;
    private static final long TRANSIENT_LOG_INTERVAL_MS = 30_000L;

    public enum Event {
        NONE,
        STARTING,
        STARTED,
        STOPPED
    }

    private enum State {
        OFF,
        STARTING,
        RUNNING,
        STOPPING
    }

    private State state;
    private long candidateSinceMs = -1L;
    private long lastStationaryAtMs = -1L;
    private long lastStartCandidateLogAtMs = -1L;
    private long lastStartCancelledLogAtMs = -1L;
    private long lastStopCandidateLogAtMs = -1L;
    private long lastStopCancelledLogAtMs = -1L;

    public EngineRunDetector(boolean initiallyRunning) {
        state = initiallyRunning ? State.RUNNING : State.OFF;
    }

    public Event update(
            int rpm,
            int speedKph,
            long rpmUpdatedAtMs,
            long nowMs) {
        return update(
                rpm,
                speedKph,
                rpmUpdatedAtMs,
                ClusterState.ENGINE_STATE_UNKNOWN,
                0L,
                false,
                false,
                0L,
                nowMs);
    }

    public Event update(
            int rpm,
            int speedKph,
            long rpmUpdatedAtMs,
            int engineState,
            long engineStateUpdatedAtMs,
            boolean parkStateValid,
            boolean inPark,
            long parkStateUpdatedAtMs,
            long nowMs) {
        boolean observedRpm =
                rpmUpdatedAtMs > 0L && nowMs >= rpmUpdatedAtMs;
        boolean freshRpm = observedRpm
                && nowMs - rpmUpdatedAtMs <= RPM_FRESH_MS;
        boolean startCondition = freshRpm && rpm >= START_RPM;
        // Do not carry stationary evidence into another engine run. Expiry of
        // the speed indication is not a new movement measurement, though: the
        // speed and final RPM packets can stop arriving in either order.
        if (state == State.OFF || speedKph > 0) {
            lastStationaryAtMs = -1L;
        }
        if (speedKph == 0) {
            lastStationaryAtMs = nowMs;
        }
        boolean observedStationary = speedKph == 0
                || (speedKph < 0 && lastStationaryAtMs >= 0L
                        && nowMs >= lastStationaryAtMs
                        && (state == State.STOPPING
                                || nowMs - lastStationaryAtMs <= STOP_SPEED_EVIDENCE_MS));
        // Only an RPM value the bus actually delivered can prove shutdown.
        // Silence is not evidence: while the engine idles, updates may pause
        // for an arbitrarily long time, and treating that as a stop would end
        // the trip under a running engine.
        boolean lastObservedRpmStopped = observedRpm && rpm <= STOP_RPM;
        // Once both stop signals were observed, transport silence must not
        // cancel the hold. New movement or nonzero RPM still cancels it.
        boolean freshEngineState = engineStateUpdatedAtMs > 0L
                && nowMs >= engineStateUpdatedAtMs
                && nowMs - engineStateUpdatedAtMs <= VEHICLE_STATE_FRESH_MS;
        boolean freshParkState = parkStateValid
                && parkStateUpdatedAtMs > 0L
                && nowMs >= parkStateUpdatedAtMs
                && nowMs - parkStateUpdatedAtMs <= VEHICLE_STATE_FRESH_MS;
        // A fresh non-Park state identifies an automatic start-stop pause and
        // vetoes even the legacy RPM + zero-speed path.
        boolean freshNonPark = freshParkState && !inPark;
        boolean rpmAndSpeedStop = observedStationary
                && lastObservedRpmStopped
                && !freshNonPark;
        boolean explicitEngineStop = freshEngineState
                && engineState == ClusterState.ENGINE_STATE_STOPPED;
        // The independent TBOX path is deliberately stricter than RPM: engine
        // stopped and Park must both be fresh, while movement remains a veto.
        boolean tboxStop = explicitEngineStop
                && speedKph <= 0
                && freshParkState
                && inPark;
        boolean stopCondition = rpmAndSpeedStop || tboxStop;
        String stopEvidence = tboxStop
                ? "tbox_engine_park"
                : rpmAndSpeedStop ? "rpm_speed" : "none";

        switch (state) {
            case OFF:
                if (startCondition) {
                    beginCandidate(State.STARTING, nowMs);
                    logTransition(
                            "start_candidate",
                            rpm,
                            speedKph,
                            rpmUpdatedAtMs,
                            nowMs,
                            observedStationary,
                            stopEvidence);
                    return Event.STARTING;
                }
                return Event.NONE;
            case STARTING:
                if (!startCondition || nowMs < candidateSinceMs) {
                    logTransition(
                            "start_cancelled",
                            rpm,
                            speedKph,
                            rpmUpdatedAtMs,
                            nowMs,
                            observedStationary,
                            stopEvidence);
                    cancelCandidate(State.OFF);
                    return Event.NONE;
                }
                if (nowMs - candidateSinceMs >= START_HOLD_MS) {
                    logTransition(
                            "engine_started",
                            rpm,
                            speedKph,
                            rpmUpdatedAtMs,
                            nowMs,
                            observedStationary,
                            stopEvidence);
                    cancelCandidate(State.RUNNING);
                    return Event.STARTED;
                }
                return Event.NONE;
            case RUNNING:
                if (stopCondition) {
                    beginCandidate(State.STOPPING, nowMs);
                    logTransition(
                            "stop_candidate",
                            rpm,
                            speedKph,
                            rpmUpdatedAtMs,
                            nowMs,
                            observedStationary,
                            stopEvidence);
                }
                return Event.NONE;
            case STOPPING:
                if (!stopCondition || nowMs < candidateSinceMs) {
                    logTransition(
                            "stop_cancelled",
                            rpm,
                            speedKph,
                            rpmUpdatedAtMs,
                            nowMs,
                            observedStationary,
                            stopEvidence);
                    cancelCandidate(State.RUNNING);
                    return Event.NONE;
                }
                if (nowMs - candidateSinceMs >= STOP_HOLD_MS) {
                    logTransition(
                            "engine_stopped",
                            rpm,
                            speedKph,
                            rpmUpdatedAtMs,
                            nowMs,
                            observedStationary,
                            stopEvidence);
                    cancelCandidate(State.OFF);
                    return Event.STOPPED;
                }
                return Event.NONE;
            default:
                throw new IllegalStateException("Unknown detector state");
        }
    }

    private void beginCandidate(State candidateState, long nowMs) {
        state = candidateState;
        candidateSinceMs = nowMs;
    }

    private void cancelCandidate(State stableState) {
        state = stableState;
        candidateSinceMs = -1L;
    }

    private void logTransition(
            String event,
            int rpm,
            int speedKph,
            long rpmUpdatedAtMs,
            long nowMs,
            boolean observedStationary,
            String stopEvidence) {
        if (!shouldLogTransition(event, nowMs)) {
            return;
        }
        DiagnosticLog.info(
                "engine_detector",
                event,
                "state", state.name(),
                "rpm", String.valueOf(rpm),
                "speed_kph", String.valueOf(speedKph),
                "rpm_age_ms", rpmUpdatedAtMs > 0L && nowMs >= rpmUpdatedAtMs
                        ? String.valueOf(nowMs - rpmUpdatedAtMs)
                        : "unavailable",
                "stationary_evidence", String.valueOf(observedStationary),
                "stop_evidence", stopEvidence,
                "candidate_ms", candidateSinceMs >= 0L && nowMs >= candidateSinceMs
                        ? String.valueOf(nowMs - candidateSinceMs)
                        : "none");
    }

    private boolean shouldLogTransition(String event, long nowMs) {
        long previous;
        switch (event) {
            case "start_candidate":
                previous = lastStartCandidateLogAtMs;
                break;
            case "start_cancelled":
                previous = lastStartCancelledLogAtMs;
                break;
            case "stop_candidate":
                previous = lastStopCandidateLogAtMs;
                break;
            case "stop_cancelled":
                previous = lastStopCancelledLogAtMs;
                break;
            default:
                return true;
        }
        boolean shouldLog = previous < 0L
                || nowMs < previous
                || nowMs - previous >= TRANSIENT_LOG_INTERVAL_MS;
        if (!shouldLog) {
            return false;
        }
        switch (event) {
            case "start_candidate":
                lastStartCandidateLogAtMs = nowMs;
                break;
            case "start_cancelled":
                lastStartCancelledLogAtMs = nowMs;
                break;
            case "stop_candidate":
                lastStopCandidateLogAtMs = nowMs;
                break;
            case "stop_cancelled":
                lastStopCancelledLogAtMs = nowMs;
                break;
            default:
                break;
        }
        return true;
    }
}
