package net.adminrunet.h9cluster.trip;

/** Detects confirmed engine start and stop transitions from fresh RPM samples. */
public final class EngineRunDetector {
    private static final int START_RPM = 400;
    private static final int STOP_RPM = 50;
    private static final long RPM_FRESH_MS = 1_000L;
    private static final long START_HOLD_MS = 1_000L;
    private static final long STOP_HOLD_MS = 1_500L;
    private static final long STOP_SPEED_EVIDENCE_MS = 2_000L;

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

    public EngineRunDetector(boolean initiallyRunning) {
        state = initiallyRunning ? State.RUNNING : State.OFF;
    }

    public Event update(
            int rpm,
            int speedKph,
            long rpmUpdatedAtMs,
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
        boolean stopCondition = observedStationary && lastObservedRpmStopped;

        switch (state) {
            case OFF:
                if (startCondition) {
                    beginCandidate(State.STARTING, nowMs);
                    return Event.STARTING;
                }
                return Event.NONE;
            case STARTING:
                if (!startCondition || nowMs < candidateSinceMs) {
                    cancelCandidate(State.OFF);
                    return Event.NONE;
                }
                if (nowMs - candidateSinceMs >= START_HOLD_MS) {
                    cancelCandidate(State.RUNNING);
                    return Event.STARTED;
                }
                return Event.NONE;
            case RUNNING:
                if (stopCondition) {
                    beginCandidate(State.STOPPING, nowMs);
                }
                return Event.NONE;
            case STOPPING:
                if (!stopCondition || nowMs < candidateSinceMs) {
                    cancelCandidate(State.RUNNING);
                    return Event.NONE;
                }
                if (nowMs - candidateSinceMs >= STOP_HOLD_MS) {
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
}
