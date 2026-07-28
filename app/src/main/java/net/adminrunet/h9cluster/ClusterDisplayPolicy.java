package net.adminrunet.h9cluster;

/** Selects the display used for the cluster without depending on Android services. */
public final class ClusterDisplayPolicy {
    public static final int CLUSTER_DISPLAY_ID = 2;
    public static final int NO_DISPLAY = -1;

    private ClusterDisplayPolicy() {
    }

    public static int resolveTargetDisplay(
            boolean demoMode,
            boolean clusterDisplayAvailable,
            int currentDisplayId) {
        if (clusterDisplayAvailable) {
            return CLUSTER_DISPLAY_ID;
        }
        return demoMode ? currentDisplayId : NO_DISPLAY;
    }

    public static boolean isSingleDisplayFallback(boolean demoMode, int targetDisplayId) {
        return demoMode
                && targetDisplayId != NO_DISPLAY
                && targetDisplayId != CLUSTER_DISPLAY_ID;
    }

    public static boolean shouldReturnToSettingsOnInteraction(
            boolean demoMode,
            boolean singleDisplayFallback) {
        return demoMode && singleDisplayFallback;
    }
}
