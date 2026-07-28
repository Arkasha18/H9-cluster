package net.adminrunet.h9cluster;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class PreviewAppearanceTest {
    @Test
    public void demoUsesOpaqueBlackBackground() {
        assertEquals(0xFF000000, PreviewAppearance.backgroundColor(true));
        assertTrue(PreviewAppearance.usesOpaqueWindow(true));
    }

    @Test
    public void productionUsesTransparentBackground() {
        assertEquals(0x00000000, PreviewAppearance.backgroundColor(false));
        assertFalse(PreviewAppearance.usesOpaqueWindow(false));
    }
}
