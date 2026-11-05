package net.adminrunet.h9cluster;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class SettingsLogButtonTest {
    @Test
    public void hitTargetOccupiesLeftBottomSlotOnly() {
        assertTrue(SettingsLogButton.contains(190.0f, 488.0f));
        assertFalse(SettingsLogButton.contains(480.0f, 488.0f));
        assertFalse(SettingsLogButton.contains(770.0f, 488.0f));
    }
}
