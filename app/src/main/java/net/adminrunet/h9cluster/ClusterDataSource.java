package net.adminrunet.h9cluster;

/** Push source for complete cluster snapshots. */
public interface ClusterDataSource {
    interface Listener {
        void onClusterState(ClusterState state);
    }

    void start(Listener listener);

    void stop();
}
