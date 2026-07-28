package net.adminrunet.h9cluster;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class ClusterStateTripTelemetryTest {
    @Test
    public void emptyStateMarksTripTelemetryUnavailable() {
        ClusterState state = ClusterState.empty();

        assertTrue(Float.isNaN(state.instantFuelConsumption));
        assertEquals(0L, state.instantFuelConsumptionUpdatedAtMs);
        assertEquals(0L, state.journeyOdometerUpdatedAtMs);
    }
}
