package net.adminrunet.h9cluster.skins.simplered;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.junit.Test;

public final class SimpleRedLayoutTest {
    @Test
    public void mainScalesAreMatchingTiltedArcs() {
        assertEquals(
                45.680f,
                SimpleRedLayout.scaleX(0.0f, false),
                0.01f);
        assertEquals(
                346.075f,
                SimpleRedLayout.scaleY(0.0f),
                0.01f);
        assertEquals(
                190.680f,
                SimpleRedLayout.scaleY(0.5f),
                0.01f);
        // The far end sits on the stretched side, so it carries the
        // offset the stretch adds at that angle.
        assertEquals(
                534.320f + SimpleRedLayout.SCALE_STRETCH_X * 0.883022f,
                SimpleRedLayout.scaleX(1.0f, false),
                0.01f);

        // The tilt carries the full-scale end below the zero end, and
        // the apex stays above the centre on both gauges.
        org.junit.Assert.assertTrue(
                SimpleRedLayout.scaleY(1.0f)
                        > SimpleRedLayout.scaleY(0.0f));
        org.junit.Assert.assertTrue(
                SimpleRedLayout.scaleY(0.5f)
                        < SimpleRedLayout.GAUGE_CENTER_Y);
    }

    private static final float VERTICAL = (float) (Math.PI * 0.5);
    private static final float HORIZONTAL = (float) Math.PI;

    @Test
    public void speedometerKeepsItsLeftHalfExactlyWhereItWas() {
        // Everything from the start of the scale up to the screen
        // vertical must be untouched, which is the whole point of the
        // shape: only the right half is pulled out.
        for (int index = 0; index <= 16; index++) {
            float angle = SimpleRedLayout.SCALE_START_ANGLE_RADIANS
                    + (VERTICAL - SimpleRedLayout.SCALE_START_ANGLE_RADIANS)
                    * index / 16.0f;
            assertEquals(
                    "no stretch left of the vertical",
                    0.0f,
                    SimpleRedLayout.stretchOffsetAt(angle, false),
                    0.0001f);
        }
    }

    @Test
    public void stretchPeaksAtTheHorizontalAndNeverExceedsTheConstant() {
        assertEquals(
                SimpleRedLayout.SCALE_STRETCH_X,
                SimpleRedLayout.stretchOffsetAt(HORIZONTAL, false),
                0.001f);
        for (int index = 0; index <= 180; index++) {
            float angle = SimpleRedLayout.SCALE_START_ANGLE_RADIANS
                    + SimpleRedLayout.SCALE_SWEEP_ANGLE_RADIANS
                    * index / 180.0f;
            float offset = SimpleRedLayout.stretchOffsetAt(angle, false);
            org.junit.Assert.assertTrue(
                    "the speedometer only ever moves outwards",
                    offset >= 0.0f);
            org.junit.Assert.assertTrue(
                    "no point moves further than the constant",
                    offset <= SimpleRedLayout.SCALE_STRETCH_X + 0.001f);
        }
    }

    @Test
    public void stretchIsPurelyAnOffsetFromTheCircle() {
        // Subtracting the offset must give back the plain circle, so the
        // shape collapses to a circle the moment the constant is zeroed.
        for (int index = 0; index <= 16; index++) {
            float angle = SimpleRedLayout.SCALE_START_ANGLE_RADIANS
                    + SimpleRedLayout.SCALE_SWEEP_ANGLE_RADIANS
                    * index / 16.0f;
            for (boolean rightGauge : new boolean[] {false, true}) {
                float circleX = SimpleRedLayout.gaugeCenterX(rightGauge)
                        - SimpleRedLayout.GAUGE_RADIUS
                        * (float) Math.cos(angle);
                assertEquals(
                        circleX,
                        SimpleRedLayout.pointXAt(
                                angle,
                                SimpleRedLayout.GAUGE_RADIUS,
                                rightGauge)
                                - SimpleRedLayout.stretchOffsetAt(
                                        angle,
                                        rightGauge),
                        0.001f);
            }
        }
    }

    @Test
    public void theTwoGaugesAreMirrorImages() {
        // Reflecting a speedometer point about the vertical lands on the
        // tachometer point at the mirrored angle. This is what keeps the
        // pair symmetric while the tachometer stretches the other way.
        for (int index = 0; index <= 16; index++) {
            float angle = SimpleRedLayout.SCALE_START_ANGLE_RADIANS
                    + SimpleRedLayout.SCALE_SWEEP_ANGLE_RADIANS
                    * index / 16.0f;
            float mirrored = HORIZONTAL - angle;
            assertEquals(
                    -(SimpleRedLayout.pointXAt(
                            angle,
                            SimpleRedLayout.GAUGE_RADIUS,
                            false)
                            - SimpleRedLayout.LEFT_GAUGE_CENTER_X),
                    SimpleRedLayout.pointXAt(
                            mirrored,
                            SimpleRedLayout.GAUGE_RADIUS,
                            true)
                            - SimpleRedLayout.RIGHT_GAUGE_CENTER_X,
                    0.001f);
            assertEquals(
                    SimpleRedLayout.pointYAt(
                            angle,
                            SimpleRedLayout.GAUGE_RADIUS),
                    SimpleRedLayout.pointYAt(
                            mirrored,
                            SimpleRedLayout.GAUGE_RADIUS),
                    0.001f);
        }
    }

    @Test
    public void tachometerStretchesTowardsTheScreenCentre() {
        // Its low end faces the middle of the screen, so that is the
        // half that moves, and it moves left.
        org.junit.Assert.assertTrue(
                SimpleRedLayout.stretchOffsetAt(
                        SimpleRedLayout.SCALE_START_ANGLE_RADIANS,
                        true) < 0.0f);
        assertEquals(
                0.0f,
                SimpleRedLayout.stretchOffsetAt(HORIZONTAL, true),
                0.0001f);
    }

    @Test
    public void tangentsFollowTheStretchedCurve() {
        // drawScaleText builds the inward normal from these, so a
        // tangent that ignores the stretch would push labels off course.
        // Comparing against a central difference also proves the curve
        // has no kink: a linear ramp would break at the vertical.
        float step = 0.001f;
        for (int index = 1; index < 32; index++) {
            float fraction = index / 32.0f;
            for (boolean rightGauge : new boolean[] {false, true}) {
                float numeric = (SimpleRedLayout.scaleX(
                        fraction + step,
                        rightGauge)
                        - SimpleRedLayout.scaleX(fraction - step, rightGauge))
                        / (2.0f * step);
                assertEquals(
                        "tangent at fraction " + fraction,
                        numeric,
                        SimpleRedLayout.scaleTangentX(fraction, rightGauge),
                        0.5f);
            }
        }
    }

    @Test
    public void gaugesFrameFactoryReadoutsAndBottomValues() {
        assertEquals(
                290.0f,
                SimpleRedLayout.LEFT_GAUGE_CENTER_X,
                0.001f);
        assertEquals(
                1620.0f,
                SimpleRedLayout.RIGHT_GAUGE_CENTER_X,
                0.001f);
        assertEquals(
                435.0f,
                SimpleRedLayout.GAUGE_CENTER_Y,
                0.001f);
        assertEquals(
                260.0f,
                SimpleRedLayout.GAUGE_RADIUS,
                0.001f);
        assertEquals(
                668.0f,
                SimpleRedLayout.FUEL_LITERS_BASELINE,
                0.001f);
        assertEquals(
                665.0f,
                SimpleRedLayout.COOLANT_BASELINE,
                0.001f);
        assertEquals(
                665.0f,
                SimpleRedLayout.TRANSMISSION_BASELINE,
                0.001f);
        assertEquals(
                1645.0f,
                SimpleRedLayout.COOLANT_X,
                0.001f);
        assertEquals(
                SimpleRedLayout.GAUGE_RADIUS - 27.0f,
                SimpleRedLayout.PROGRESS_BAND_RADIUS,
                0.001f);
        assertEquals(
                SimpleRedLayout.TRANSMISSION_BASELINE,
                SimpleRedLayout.TRANSMISSION_LABEL_Y,
                0.001f);
        try {
            float labelX = SimpleRedLayout.class
                    .getDeclaredField("TRANSMISSION_LABEL_X")
                    .getFloat(null);
            org.junit.Assert.assertTrue(
                    labelX < SimpleRedLayout.TRANSMISSION_X);
        } catch (ReflectiveOperationException error) {
            org.junit.Assert.fail(
                    "TRANSMISSION_LABEL_X must place АКПП left of value");
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
        assertEquals(
                SimpleRedLayout.TICK_MAJOR_INNER_RADIUS,
                SimpleRedLayout.PROGRESS_BAND_RADIUS,
                0.001f);
        org.junit.Assert.assertTrue(
                SimpleRedLayout.PROGRESS_BAND_RADIUS
                        + SimpleRedLayout.PROGRESS_HALO_WIDTH * 0.5f
                        < SimpleRedLayout.GAUGE_RADIUS);
        org.junit.Assert.assertTrue(
                SimpleRedLayout.PROGRESS_BAND_RADIUS
                        - SimpleRedLayout.PROGRESS_HALO_WIDTH * 0.5f
                        > SimpleRedLayout.GAUGE_RADIUS
                        - SimpleRedLayout.MAIN_SCALE_LABEL_OFFSET);
        // An empty band sits at the start of the scale and a full one
        // reaches its end, so the band and the ticks agree everywhere.
        assertEquals(
                SimpleRedLayout.SCALE_START_ANGLE_RADIANS,
                SimpleRedLayout.progressEndAngle(0.0f),
                0.001f);
        assertEquals(
                SimpleRedLayout.scaleAngle(0.5f),
                SimpleRedLayout.progressEndAngle(0.5f),
                0.001f);
        assertEquals(
                SimpleRedLayout.SCALE_END_ANGLE_RADIANS,
                SimpleRedLayout.progressEndAngle(1.0f),
                0.001f);
        org.junit.Assert.assertTrue(
                "a sliver of band still needs a segment to draw",
                SimpleRedLayout.progressSegments(0.001f) >= 1);
        assertEquals(
                SimpleRedLayout.SCALE_PATH_SEGMENTS,
                SimpleRedLayout.progressSegments(1.0f));
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
                141.0f,
                SimpleRedLayout.steeringRotation(-141.0f),
                0.001f);
        assertEquals(
                0.0f,
                SimpleRedLayout.steeringRotation(Float.NaN),
                0.001f);
        assertEquals(
                -1080.0f,
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
    public void backgroundRingsAreOrderedFromLabelsOutwards() {
        float labelRadius = SimpleRedLayout.GAUGE_RADIUS
                - SimpleRedLayout.MAIN_SCALE_LABEL_OFFSET;
        float redlineInnerEdge = SimpleRedLayout.REDLINE_ARC_RADIUS
                - SimpleRedLayout.REDLINE_GLOW_WIDTH * 0.5f;
        org.junit.Assert.assertTrue(
                "labels must clear the red arc and its glow",
                labelRadius < redlineInnerEdge);
        org.junit.Assert.assertTrue(
                SimpleRedLayout.REDLINE_ARC_RADIUS
                        + SimpleRedLayout.REDLINE_ARC_WIDTH * 0.5f
                        < SimpleRedLayout.TICK_MAJOR_INNER_RADIUS);
        org.junit.Assert.assertTrue(
                SimpleRedLayout.TICK_MAJOR_INNER_RADIUS
                        < SimpleRedLayout.TICK_MINOR_INNER_RADIUS);
        org.junit.Assert.assertTrue(
                SimpleRedLayout.TICK_MINOR_INNER_RADIUS
                        < SimpleRedLayout.TICK_OUTER_RADIUS);
        org.junit.Assert.assertTrue(
                SimpleRedLayout.TICK_OUTER_RADIUS
                        <= SimpleRedLayout.SCALE_ARC_RADIUS);
        assertEquals(
                SimpleRedLayout.GAUGE_RADIUS,
                SimpleRedLayout.SCALE_ARC_RADIUS,
                0.001f);
    }

    @Test
    public void minorTicksDivideEveryLabelledInterval() {
        assertEquals(10, SimpleRedLayout.majorTickIntervals(false));
        assertEquals(6, SimpleRedLayout.majorTickIntervals(true));
        assertEquals(
                40,
                SimpleRedLayout.majorTickIntervals(false)
                        * SimpleRedLayout.minorTicksPerMajor(false));
        assertEquals(
                60,
                SimpleRedLayout.majorTickIntervals(true)
                        * SimpleRedLayout.minorTicksPerMajor(true));
    }

    @Test
    public void tachBackdropClearsTheLeftGauge() {
        // Both gauges stretch towards the middle of the screen, so the
        // clearance has to account for them closing in on each other.
        org.junit.Assert.assertTrue(
                SimpleRedLayout.RIGHT_GAUGE_CENTER_X
                        - SimpleRedLayout.TACH_BACKDROP_RADIUS
                        - SimpleRedLayout.SCALE_STRETCH_X
                        > SimpleRedLayout.LEFT_GAUGE_CENTER_X
                        + SimpleRedLayout.GAUGE_RADIUS
                        + SimpleRedLayout.SCALE_STRETCH_X);
    }

    @Test
    public void softBandLayersNarrowFromHaloDownToCore() {
        assertEquals(
                SimpleRedLayout.PROGRESS_HALO_WIDTH,
                SimpleRedLayout.progressSoftLayerWidth(0),
                0.001f);
        assertEquals(
                SimpleRedLayout.PROGRESS_CORE_WIDTH,
                SimpleRedLayout.progressSoftLayerWidth(
                        SimpleRedLayout.PROGRESS_SOFT_LAYER_COUNT - 1),
                0.001f);
        float previous = Float.MAX_VALUE;
        for (int layer = 0;
                layer < SimpleRedLayout.PROGRESS_SOFT_LAYER_COUNT;
                layer++) {
            float width = SimpleRedLayout.progressSoftLayerWidth(layer);
            org.junit.Assert.assertTrue(
                    "soft layers must narrow monotonically",
                    width < previous);
            previous = width;
        }
        int previousAlpha = -1;
        for (int layer = 0;
                layer < SimpleRedLayout.PROGRESS_SOFT_LAYER_COUNT;
                layer++) {
            int alpha = SimpleRedLayout.progressSoftLayerAlpha(layer);
            org.junit.Assert.assertTrue(
                    "stacked layers must stay translucent to accumulate",
                    alpha < 255);
            org.junit.Assert.assertTrue(
                    "alpha must rise as the layers narrow",
                    alpha > previousAlpha);
            previousAlpha = alpha;
        }
        assertEquals(
                SimpleRedLayout.PROGRESS_SOFT_LAYER_MIN_ALPHA,
                SimpleRedLayout.progressSoftLayerAlpha(0));
        assertEquals(
                SimpleRedLayout.PROGRESS_SOFT_LAYER_MAX_ALPHA,
                SimpleRedLayout.progressSoftLayerAlpha(
                        SimpleRedLayout.PROGRESS_SOFT_LAYER_COUNT - 1));
        org.junit.Assert.assertTrue(
                "the widest layer must still clear the scale arc",
                SimpleRedLayout.PROGRESS_BAND_RADIUS
                        + SimpleRedLayout.progressSoftLayerWidth(0) * 0.5f
                        < SimpleRedLayout.GAUGE_RADIUS);
    }

    @Test
    public void tipBloomIsClampedToTheBandAndStaysTranslucent() {
        org.junit.Assert.assertTrue(
                SimpleRedLayout.PROGRESS_TIP_BLOOM_RADIUS > 0.0f);
        org.junit.Assert.assertTrue(
                "a fully opaque bloom would hide the leading edge",
                SimpleRedLayout.PROGRESS_TIP_BLOOM_ALPHA < 255);
        org.junit.Assert.assertTrue(
                SimpleRedLayout.PROGRESS_TIP_BLOOM_ALPHA > 0);
    }

    @Test
    public void tachBackdropIsOpaqueBlackOutsideDemoBuilds() {
        assertEquals(
                0xFF000000,
                SimpleRedLayout.tachBackdropColor(false));
        org.junit.Assert.assertNotEquals(
                "demo builds must tint the backdrop to make it visible",
                SimpleRedLayout.tachBackdropColor(false),
                SimpleRedLayout.tachBackdropColor(true));
        assertEquals(
                "the demo tint must stay fully opaque",
                0xFF,
                SimpleRedLayout.tachBackdropColor(true) >>> 24);
    }

    @Test
    public void tachBackdropCoversTheScaleBandButNotTheCentre() {
        org.junit.Assert.assertTrue(
                "the backdrop must reach past the scale arc",
                SimpleRedLayout.TACH_BACKDROP_RADIUS
                        > SimpleRedLayout.SCALE_ARC_RADIUS);
        float labelRadius = SimpleRedLayout.GAUGE_RADIUS
                - SimpleRedLayout.MAIN_SCALE_LABEL_OFFSET;
        org.junit.Assert.assertTrue(
                "the backdrop must reach under the scale labels",
                SimpleRedLayout.TACH_BACKDROP_INNER_RADIUS < labelRadius);
        org.junit.Assert.assertTrue(
                "the gauge centre must stay uncovered",
                SimpleRedLayout.TACH_BACKDROP_INNER_RADIUS > 0.0f);
        org.junit.Assert.assertTrue(
                SimpleRedLayout.TACH_BACKDROP_INNER_RADIUS
                        < SimpleRedLayout.TACH_BACKDROP_RADIUS);
    }

    @Test
    public void tachBackdropOverhangsBothScaleEnds() {
        org.junit.Assert.assertTrue(
                SimpleRedLayout.TACH_BACKDROP_PADDING_RADIANS > 0.0f);
        assertEquals(
                SimpleRedLayout.SCALE_START_ANGLE_RADIANS
                        - SimpleRedLayout.TACH_BACKDROP_PADDING_RADIANS,
                SimpleRedLayout.tachBackdropStartAngle(),
                0.001f);
        assertEquals(
                SimpleRedLayout.SCALE_END_ANGLE_RADIANS
                        + SimpleRedLayout.TACH_BACKDROP_PADDING_RADIANS,
                SimpleRedLayout.tachBackdropEndAngle(),
                0.001f);
        org.junit.Assert.assertTrue(
                "the sector must not wrap onto itself",
                SimpleRedLayout.tachBackdropEndAngle()
                        - SimpleRedLayout.tachBackdropStartAngle()
                        < (float) (Math.PI * 2.0));
        org.junit.Assert.assertTrue(
                "the padded ends still get sampled",
                SimpleRedLayout.pathSegments(
                        SimpleRedLayout.tachBackdropStartAngle(),
                        SimpleRedLayout.tachBackdropEndAngle())
                        > SimpleRedLayout.SCALE_PATH_SEGMENTS);
    }

    @Test
    public void radialHelpersMatchTheScaleHelpersAtGaugeRadius() {
        for (int index = 0; index <= 8; index++) {
            float fraction = index / 8.0f;
            assertEquals(
                    SimpleRedLayout.scaleX(fraction, false),
                    SimpleRedLayout.radialX(
                            fraction,
                            SimpleRedLayout.GAUGE_RADIUS,
                            false),
                    0.001f);
            assertEquals(
                    SimpleRedLayout.scaleY(fraction),
                    SimpleRedLayout.radialY(
                            fraction,
                            SimpleRedLayout.GAUGE_RADIUS),
                    0.001f);
        }
        assertEquals(
                SimpleRedLayout.GAUGE_CENTER_Y,
                SimpleRedLayout.radialY(0.0f, 0.0f),
                0.001f);
        // The stretch is deliberately independent of the radius, so it
        // survives all the way down to a zero radius. Off the stretched
        // half that leaves the centre itself; on it, the centre shifts
        // by exactly the offset and nothing more.
        assertEquals(
                SimpleRedLayout.RIGHT_GAUGE_CENTER_X,
                SimpleRedLayout.radialX(0.5f, 0.0f, true),
                0.001f);
        assertEquals(
                SimpleRedLayout.RIGHT_GAUGE_CENTER_X
                        + SimpleRedLayout.stretchOffsetAt(
                                SimpleRedLayout.scaleAngle(0.35f),
                                true),
                SimpleRedLayout.radialX(0.35f, 0.0f, true),
                0.001f);
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
