package net.adminrunet.h9cluster;

/** Common state input implemented by every selectable instrument skin. */
public interface ClusterRenderer {
    void setClusterState(ClusterState state);

    /** Controls the skin's added ratio numeral without changing vehicle telemetry. */
    void setGearNumberVisible(boolean visible);
}
