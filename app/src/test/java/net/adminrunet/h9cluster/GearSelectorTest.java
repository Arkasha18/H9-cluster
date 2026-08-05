package net.adminrunet.h9cluster;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class GearSelectorTest {
    @Test
    public void vehicleCodesMatchThePositionsRecordedOnTheCar() {
        assertEquals(GearSelector.NEUTRAL, GearSelector.fromVehicleCode(0));
        assertEquals(GearSelector.DRIVE, GearSelector.fromVehicleCode(2));
        assertEquals(GearSelector.PARK, GearSelector.fromVehicleCode(3));
        assertEquals(GearSelector.REVERSE, GearSelector.fromVehicleCode(4));
        assertEquals(GearSelector.MANUAL, GearSelector.fromVehicleCode(5));
    }

    @Test
    public void unrecordedCodesLeaveThePositionUnknown() {
        assertEquals(GearSelector.UNKNOWN, GearSelector.fromVehicleCode(-1));
        assertEquals(GearSelector.UNKNOWN, GearSelector.fromVehicleCode(1));
        assertEquals(GearSelector.UNKNOWN, GearSelector.fromVehicleCode(6));
    }

    @Test
    public void drivePositionsShowTheLetterTogetherWithTheRatio() {
        assertEquals("D1", GearSelector.label(GearSelector.DRIVE, 1));
        assertEquals("D8", GearSelector.label(GearSelector.DRIVE, 8));
        assertEquals("M2", GearSelector.label(GearSelector.MANUAL, 2));
    }

    @Test
    public void drivePositionsWithoutARatioShowTheLetterAlone() {
        assertEquals("D", GearSelector.label(GearSelector.DRIVE, 0));
        assertEquals("M", GearSelector.label(GearSelector.MANUAL, 9));
    }

    @Test
    public void parkReverseAndNeutralNeverShowARatio() {
        assertEquals("P", GearSelector.label(GearSelector.PARK, 0));
        assertEquals("R", GearSelector.label(GearSelector.REVERSE, 0));
        assertEquals("R", GearSelector.label(GearSelector.REVERSE, 8));
        assertEquals("N", GearSelector.label(GearSelector.NEUTRAL, 0));
    }

    @Test
    public void bareRatioSkipsCodesThatAreNotForwardRatios() {
        assertEquals("", GearSelector.ratio(0));
        assertEquals("1", GearSelector.ratio(1));
        assertEquals("8", GearSelector.ratio(8));
        assertEquals("", GearSelector.ratio(9));
    }

    @Test
    public void unknownPositionKeepsTheBareRatio() {
        assertEquals("3", GearSelector.label(GearSelector.UNKNOWN, 3));
        assertEquals("", GearSelector.label(GearSelector.UNKNOWN, 0));
        assertEquals("", GearSelector.label(null, 9));
    }
}
