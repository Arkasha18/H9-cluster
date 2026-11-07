package net.adminrunet.h9cluster;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class ClusterStateGearSelectorObservationTest {
    @Test
    public void derivedSnapshotsPreserveSelectorObservation() {
        ClusterState source = ClusterState.empty()
                .withGearSelectorObservation(12_345L);

        ClusterState withTbox = source.withTboxVehicleState(
                ClusterState.ENGINE_STATE_RUNNING,
                13_000L,
                true,
                false,
                13_000L);
        ClusterState withSelectedTank = withTbox.withFuelTankCapacity(60);

        assertEquals(12_345L, withTbox.gearSelectorObservedAtMs);
        assertEquals(12_345L, withSelectedTank.gearSelectorObservedAtMs);
    }
}
