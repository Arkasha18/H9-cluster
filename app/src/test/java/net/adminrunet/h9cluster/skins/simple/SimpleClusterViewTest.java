package net.adminrunet.h9cluster.skins.simple;

import static org.junit.Assert.assertEquals;

import net.adminrunet.h9cluster.GearSelector;

import org.junit.Test;

public final class SimpleClusterViewTest {
    @Test
    public void ratioIsShownOnlyInDrive() {
        assertEquals(
                "3",
                SimpleClusterView.currentGearLabel(GearSelector.DRIVE, 3));
        assertEquals(
                "",
                SimpleClusterView.currentGearLabel(GearSelector.PARK, 3));
        assertEquals(
                "",
                SimpleClusterView.currentGearLabel(GearSelector.REVERSE, 3));
        assertEquals(
                "",
                SimpleClusterView.currentGearLabel(GearSelector.NEUTRAL, 3));
        assertEquals(
                "",
                SimpleClusterView.currentGearLabel(GearSelector.MANUAL, 3));
        assertEquals(
                "",
                SimpleClusterView.currentGearLabel(GearSelector.UNKNOWN, 3));
    }
}
