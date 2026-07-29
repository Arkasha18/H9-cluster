package net.adminrunet.h9cluster.skins.simplered;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.junit.Test;

public final class SimpleRedLayoutTest {
    @Test
    public void mainScalesAreMatchingUpperSemicircles() {
        assertEquals(
                SimpleRedLayout.LEFT_GAUGE_CENTER_X
                        - SimpleRedLayout.GAUGE_RADIUS,
                SimpleRedLayout.scaleX(0.0f, false),
                0.001f);
        assertEquals(
                SimpleRedLayout.GAUGE_CENTER_Y
                        - SimpleRedLayout.GAUGE_RADIUS,
                SimpleRedLayout.scaleY(0.5f),
                0.001f);
        assertEquals(
                SimpleRedLayout.LEFT_GAUGE_CENTER_X
                        + SimpleRedLayout.GAUGE_RADIUS,
                SimpleRedLayout.scaleX(1.0f, false),
                0.001f);

        for (int index = 0; index <= 8; index++) {
            float fraction = index / 8.0f;
            assertEquals(
                    1920.0f
                            - SimpleRedLayout.scaleX(
                                    1.0f - fraction,
                                    false),
                    SimpleRedLayout.scaleX(fraction, true),
                    0.001f);
            assertEquals(
                    SimpleRedLayout.scaleY(fraction),
                    SimpleRedLayout.scaleY(1.0f - fraction),
                    0.001f);
        }
    }

    @Test
    public void scaleRangesAreTwoHundredAndSixThousand() {
        assertEquals(200.0f, SimpleRedLayout.MAX_SPEED_KPH, 0.001f);
        assertEquals(6000.0f, SimpleRedLayout.MAX_RPM, 0.001f);
        assertEquals(
                1.0f,
                SimpleRedLayout.speedFraction(200.0f),
                0.001f);
        assertEquals(
                1.0f,
                SimpleRedLayout.speedFraction(220.0f),
                0.001f);
        assertEquals(
                0.5f,
                SimpleRedLayout.rpmFraction(3000.0f),
                0.001f);
        assertFalse(SimpleRedLayout.DRAW_SCALE_UNITS);
    }

    @Test
    public void progressBandStaysInsideTicksAndOutsideLabels() {
        org.junit.Assert.assertTrue(
                SimpleRedLayout.PROGRESS_BAND_RADIUS
                        + SimpleRedLayout.PROGRESS_HALO_WIDTH * 0.5f
                        < SimpleRedLayout.GAUGE_RADIUS);
        org.junit.Assert.assertTrue(
                SimpleRedLayout.PROGRESS_BAND_RADIUS
                        - SimpleRedLayout.PROGRESS_HALO_WIDTH * 0.5f
                        > SimpleRedLayout.GAUGE_RADIUS
                        - SimpleRedLayout.MAIN_SCALE_LABEL_OFFSET);
        assertEquals(
                90.0f,
                SimpleRedLayout.progressSweepDegrees(0.5f),
                0.001f);
        assertEquals(
                180.0f,
                SimpleRedLayout.progressSweepDegrees(1.0f),
                0.001f);
    }

    @Test
    public void numericGearIsLimitedToDriveRatios() {
        assertEquals("", SimpleRedLayout.formatGear(0));
        assertEquals("1", SimpleRedLayout.formatGear(1));
        assertEquals("8", SimpleRedLayout.formatGear(8));
        assertEquals("", SimpleRedLayout.formatGear(9));
    }

    @Test
    public void steeringAngleRotatesOnlyTheWheelIcon() {
        assertEquals(
                -141.0f,
                SimpleRedLayout.steeringRotation(-141.0f),
                0.001f);
        assertEquals(
                0.0f,
                SimpleRedLayout.steeringRotation(Float.NaN),
                0.001f);
        assertEquals(
                1080.0f,
                SimpleRedLayout.steeringRotation(1200.0f),
                0.001f);
    }

    @Test
    public void allValuesUseUprightText() {
        assertEquals(0.0f, SimpleRedLayout.TEXT_SKEW_X, 0.001f);
        org.junit.Assert.assertTrue(
                SimpleRedLayout.SCALE_LABEL_SKEW_X < 0.0f);
        assertEquals(
                "fonts/Rajdhani-Medium.ttf",
                SimpleRedLayout.SCALE_LABEL_FONT_ASSET);
    }

    @Test
    public void steeringWheelUsesReadableTShapedSpokes() {
        org.junit.Assert.assertTrue(
                SimpleRedLayout.STEERING_ICON_RADIUS >= 18.0f);
        org.junit.Assert.assertTrue(
                SimpleRedLayout.STEERING_RIM_WIDTH >= 4.0f);
        org.junit.Assert.assertTrue(
                SimpleRedLayout.STEERING_SPOKE_WIDTH >= 5.0f);
        org.junit.Assert.assertTrue(
                SimpleRedLayout.STEERING_HUB_RADIUS >= 6.0f);
        assertEquals(0xFFF9F9F7, SimpleRedLayout.STEERING_COLOR);
    }

    @Test
    public void topTelemetryIsACompactRightAlignedGroup() {
        assertEquals(
                104.0f,
                SimpleRedLayout.TYRE_RIGHT_X
                        - SimpleRedLayout.TYRE_LEFT_X,
                0.001f);
        assertEquals(
                SimpleRedLayout.TYRE_ICON_X,
                SimpleRedLayout.STEERING_ICON_X,
                0.001f);
        assertEquals(
                (SimpleRedLayout.TYRE_TOP_Y
                        + SimpleRedLayout.TYRE_BOTTOM_Y) * 0.5f
                        + SimpleRedLayout.STEERING_ICON_VERTICAL_OFFSET,
                SimpleRedLayout.STEERING_ICON_Y,
                0.001f);
        org.junit.Assert.assertTrue(
                SimpleRedLayout.STEERING_ICON_VERTICAL_OFFSET < 0.0f);
        assertEquals(
                22.0f,
                SimpleRedLayout.TYRE_TEXT_SIZE,
                0.001f);
        assertEquals(
                25.0f,
                SimpleRedLayout.TYRE_BOTTOM_Y
                        - SimpleRedLayout.TYRE_TOP_Y,
                0.001f);
    }

    @Test
    public void progressBandHasTransparentRedStartAndYellowGlow() {
        org.junit.Assert.assertTrue(
                SimpleRedLayout.PROGRESS_HALO_WIDTH
                        > SimpleRedLayout.PROGRESS_GLOW_WIDTH);
        org.junit.Assert.assertTrue(
                SimpleRedLayout.PROGRESS_GLOW_WIDTH
                        > SimpleRedLayout.PROGRESS_CORE_WIDTH);
        org.junit.Assert.assertTrue(
                SimpleRedLayout.PROGRESS_BAND_RADIUS > 250.0f);
        assertEquals(
                0x08FF2020,
                SimpleRedLayout.PROGRESS_START_COLOR);
        assertEquals(
                0xFFFFD54F,
                SimpleRedLayout.PROGRESS_LEADING_COLOR);
        org.junit.Assert.assertTrue(
                SimpleRedLayout.PROGRESS_HALO_ALPHA >= 128);
    }

    @Test
    public void bottomValuesUseApprovedFormatsAndOneBaseline() {
        assertEquals(
                "15.9 L",
                SimpleRedLayout.formatConsumption(15.9f));
        assertEquals(
                "58 L",
                SimpleRedLayout.formatFuel(58.2f));
        assertEquals(
                "84 °C",
                SimpleRedLayout.formatCoolant(84));
        assertEquals(
                "13.4 V",
                SimpleRedLayout.formatVoltage(13.4f));
        assertEquals(
                "",
                SimpleRedLayout.formatConsumption(Float.NaN));
        assertEquals(
                "",
                SimpleRedLayout.formatFuel(-1.0f));
        assertEquals(
                "",
                SimpleRedLayout.formatVoltage(0.0f));
        assertEquals(
                SimpleRedLayout.FACTORY_SCALE_BASELINE,
                SimpleRedLayout.CONSUMPTION_BASELINE,
                0.001f);
        assertEquals(
                SimpleRedLayout.FACTORY_SCALE_BASELINE,
                SimpleRedLayout.VOLTAGE_BASELINE,
                0.001f);
    }

    @Test
    public void optionalTelemetryIsHiddenWhenUnavailable() {
        assertEquals(
                "2.35",
                SimpleRedLayout.formatPressure(2.35f));
        assertEquals(
                "",
                SimpleRedLayout.formatPressure(0.0f));
        assertEquals(
                "",
                SimpleRedLayout.formatPressure(Float.NaN));
        assertEquals(
                "79 °C",
                SimpleRedLayout.formatTransmissionTemperature(
                        79.2f,
                        10_000L,
                        20_000L));
        assertEquals(
                "",
                SimpleRedLayout.formatTransmissionTemperature(
                        79.2f,
                        10_000L,
                        26_000L));
        assertEquals(
                "",
                SimpleRedLayout.formatTransmissionTemperature(
                        Float.NaN,
                        10_000L,
                        20_000L));
    }

    @Test
    public void telemetryColorsChangeAtApprovedThresholds() {
        assertEquals(
                SimpleRedLayout.COLOR_NORMAL,
                SimpleRedLayout.consumptionColor(20.0f));
        assertEquals(
                SimpleRedLayout.COLOR_WARNING,
                SimpleRedLayout.consumptionColor(20.1f));

        assertEquals(
                SimpleRedLayout.COLOR_NORMAL,
                SimpleRedLayout.temperatureColor(110.0f));
        assertEquals(
                SimpleRedLayout.COLOR_WARNING,
                SimpleRedLayout.temperatureColor(110.1f));
        assertEquals(
                SimpleRedLayout.COLOR_WARNING,
                SimpleRedLayout.temperatureColor(120.0f));
        assertEquals(
                SimpleRedLayout.COLOR_CRITICAL,
                SimpleRedLayout.temperatureColor(120.1f));

        assertEquals(
                SimpleRedLayout.COLOR_NORMAL,
                SimpleRedLayout.voltageColor(12.0f));
        assertEquals(
                SimpleRedLayout.COLOR_WARNING,
                SimpleRedLayout.voltageColor(11.9f));

        assertEquals(
                SimpleRedLayout.COLOR_NORMAL,
                SimpleRedLayout.pressureColor(2.0f));
        assertEquals(
                SimpleRedLayout.COLOR_WARNING,
                SimpleRedLayout.pressureColor(1.99f));

        assertEquals(
                SimpleRedLayout.COLOR_NORMAL,
                SimpleRedLayout.fuelColor(8.0f));
        assertEquals(
                SimpleRedLayout.COLOR_WARNING,
                SimpleRedLayout.fuelColor(7.9f));
        assertEquals(
                SimpleRedLayout.COLOR_WARNING,
                SimpleRedLayout.fuelColor(2.0f));
        assertEquals(
                SimpleRedLayout.COLOR_CRITICAL,
                SimpleRedLayout.fuelColor(1.9f));
    }
}
