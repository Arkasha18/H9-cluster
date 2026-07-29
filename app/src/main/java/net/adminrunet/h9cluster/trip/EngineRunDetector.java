package net.adminrunet.h9cluster.trip;

/** Detects confirmed engine start and stop transitions from fresh RPM samples. */
public final class EngineRunDetector {
    private static final int START_RPM = 400;
    private static final int STOP_RPM = 50;
    private static final long RPM_FRESH_MS = 1_000L;
    private static final long START_HOLD_MS = 1_000L;
    private static final long STOP_HOLD_MS = 1_500L;

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
        boolean silentRpm = observedRpm
                && nowMs - rpmUpdatedAtMs > RPM_FRESH_MS;
        boolean startCondition = freshRpm && rpm >= START_RPM;
        boolean stopCondition = speedKph == 0
                && ((freshRpm && rpm <= STOP_RPM) || silentRpm);

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
