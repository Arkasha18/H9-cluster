package net.adminrunet.h9cluster;

/** Push source for complete cluster snapshots. */
public interface ClusterDataSource {
    interface Listener {
        void onClusterState(ClusterState state);

        /** Reveals the factory QNX warning card through the translucent window. */
        default void onFactoryNotificationVisibilityChanged(boolean visible) {
            // Most data-source consumers only need telemetry snapshots.
        }
    }

    void start(Listener listener);

    void stop();
}
