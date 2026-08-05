package net.adminrunet.h9cluster;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class BootStartPolicyTest {
    @Test
    public void bootAndPackageReplacementStartTheCluster() {
        assertTrue(BootStartPolicy.shouldStart(
                BootStartPolicy.ACTION_BOOT_COMPLETED,
                false));
        assertTrue(BootStartPolicy.shouldStart(
                BootStartPolicy.ACTION_MY_PACKAGE_REPLACED,
                false));
    }

    @Test
    public void anExplicitExitKeepsTheClusterClosedAfterReboot() {
        assertFalse(BootStartPolicy.shouldStart(
                BootStartPolicy.ACTION_BOOT_COMPLETED,
                true));
        assertFalse(BootStartPolicy.shouldStart(
                BootStartPolicy.ACTION_MY_PACKAGE_REPLACED,
                true));
    }

    @Test
    public void unrelatedBroadcastsAreIgnored() {
        assertFalse(BootStartPolicy.shouldStart(null, false));
        assertFalse(BootStartPolicy.shouldStart("", false));
        assertFalse(BootStartPolicy.shouldStart(
                "android.intent.action.SCREEN_ON",
                false));
    }
}
