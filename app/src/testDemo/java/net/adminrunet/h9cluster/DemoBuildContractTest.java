package net.adminrunet.h9cluster;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class DemoBuildContractTest {
    @Test
    public void demoVariantIsDebuggableAndHasAnIsolatedIdentity() {
        assertTrue(BuildConfig.DEMO_MODE);
        assertTrue(BuildConfig.APPLICATION_ID.endsWith(".demo"));
        assertTrue(BuildConfig.DEBUG);
    }
}
