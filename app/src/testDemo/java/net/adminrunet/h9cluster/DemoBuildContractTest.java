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

    @Test
    public void demoVariantDoesNotContainTboxSecretMaterial() {
        assertTrue(
                "Demo TBOX secret mask must be empty",
                BuildConfig.TBOX_SECRET_MASK.isEmpty());
        assertTrue(
                "Demo TBOX secret data must be empty",
                BuildConfig.TBOX_SECRET_DATA.isEmpty());
    }
}
