package net.adminrunet.h9cluster;

/** Resolves a display-only zero while both independent TBOX signals are fresh. */
final class StationarySpeedDisplayFallback {
    static final long VEHICLE_STATE_FRESH_MS = 15_000L;

    private StationarySpeedDisplayFallback() {
    }

    static boolean isActive(ClusterState state, long nowMs) {
        if (state == null || state.speedKph >= 0) {
            return false;
        }
        return state.engineState == ClusterState.ENGINE_STATE_RUNNING
                && isFresh(state.engineStateUpdatedAtMs, nowMs)
                && state.parkStateValid
                && state.inPark
                && isFresh(state.parkStateUpdatedAtMs, nowMs);
    }

    static int resolveSpeedKph(ClusterState state, long nowMs) {
        if (state == null) {
            return -1;
        }
        return isActive(state, nowMs) ? 0 : state.speedKph;
    }

    private static boolean isFresh(long updatedAtMs, long nowMs) {
        return updatedAtMs > 0L
                && nowMs >= updatedAtMs
                && nowMs - updatedAtMs <= VEHICLE_STATE_FRESH_MS;
    }
}
