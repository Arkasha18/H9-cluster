package net.adminrunet.h9cluster.skins.simplered;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.junit.Test;

public final class SimpleRedLayoutTest {
    /** Logical panel the skin draws into, fixed by the head unit. */
    private static final float PANEL_WIDTH = 1920.0f;
    private static final float PANEL_HEIGHT = 720.0f;

    private static float outlineExtreme(
            boolean rightGauge,
            float radius,
            boolean horizontal,
            boolean maximum) {
        float best = horizontal
                ? SimpleRedLayout.pointXAt(0.0f, radius, rightGauge)
                : SimpleRedLayout.pointYAt(0.0f, radius);
        for (int index = 0; index <= 128; index++) {
            float along = SimpleRedLayout.SCALE_TOTAL_LENGTH
                    * index / 128.0f;
            float value = horizontal
                    ? SimpleRedLayout.pointXAt(along, radius, rightGauge)
                    : SimpleRedLayout.pointYAt(along, radius);
            best = maximum
                    ? Math.max(best, value)
                    : Math.min(best, value);
        }
        return best;
    }

    @Test
    public void mainScalesAreMatchingTiltedArcs() {
        // The tilt carries the full-scale end below the zero end, and
        // the apex stays above the centre on both gauges.
        org.junit.Assert.assertTrue(
                SimpleRedLayout.scaleY(1.0f)
                        > SimpleRedLayout.scaleY(0.0f));
        org.junit.Assert.assertTrue(
                SimpleRedLayout.scaleY(0.5f)
                        < SimpleRedLayout.GAUGE_CENTER_Y);
        // The ends straddle the centre line rather than both hanging
        // below it: the tilt lifts the zero end and drops the far one.
        org.junit.Assert.assertTrue(
                SimpleRedLayout.scaleY(0.0f)
                        < SimpleRedLayout.GAUGE_CENTER_Y);
        org.junit.Assert.assertTrue(
                SimpleRedLayout.scaleY(1.0f)
                        > SimpleRedLayout.GAUGE_CENTER_Y);
        // The apex is the top of the circle and nothing rises past it.
        assertEquals(
                SimpleRedLayout.GAUGE_CENTER_Y
                        - SimpleRedLayout.GAUGE_RADIUS,
                outlineExtreme(
                        false,
                        SimpleRedLayout.GAUGE_RADIUS,
                        false,
                        false),
                0.001f);
    }

    @Test
    public void bothGaugesFitThePanelWithoutTouching() {
        // These used to be a row of coordinate literals, which broke on
        // every retune while protecting nothing. What actually has to
        // hold is that the pair fits the panel and stays apart, insert
        // and backdrop included.
        float speedoLeft = outlineExtreme(
                false, SimpleRedLayout.GAUGE_RADIUS, true, false);
        float speedoRight = outlineExtreme(
                false, SimpleRedLayout.GAUGE_RADIUS, true, true);
        float tachLeft = outlineExtreme(
                true, SimpleRedLayout.TACH_BACKDROP_RADIUS, true, false);
        float tachRight = outlineExtreme(
                true, SimpleRedLayout.TACH_BACKDROP_RADIUS, true, true);
        org.junit.Assert.assertTrue(
                "the speedometer runs off the left edge",
                speedoLeft >= 0.0f);
        org.junit.Assert.assertTrue(
                "the tachometer runs off the right edge",
                tachRight <= PANEL_WIDTH);
        org.junit.Assert.assertTrue(
                "the gauges must not close on each other",
                speedoRight < tachLeft);

        float top = outlineExtreme(
                true, SimpleRedLayout.TACH_BACKDROP_RADIUS, false, false);
        float bottom = outlineExtreme(
                true, SimpleRedLayout.TACH_BACKDROP_RADIUS, false, true);
        org.junit.Assert.assertTrue("the gauges overflow the top", top >= 0.0f);
        org.junit.Assert.assertTrue(
                "the gauges overflow the bottom",
                bottom <= PANEL_HEIGHT);
        org.junit.Assert.assertTrue(
                "the gauges must clear the bottom readouts",
                bottom < SimpleRedLayout.FUEL_LITERS_BASELINE);
    }

    private static final float CUT = (float) (Math.PI * 0.5);

    @Test
    public void theFirstArcStaysExactlyWhereItWas() {
        // Everything up to the cut is the circle it always was, which is
        // the point of the shape: the scale only opens up after it.
        for (int index = 0; index <= 16; index++) {
            float along = SimpleRedLayout.SCALE_LEADING_LENGTH
                    * index / 16.0f;
            assertEquals(
                    "nothing moves before the cut",
                    0.0f,
                    SimpleRedLayout.stretchOffsetAtLength(along, false),
                    0.0001f);
            float angle = SimpleRedLayout.SCALE_START_ANGLE_RADIANS
                    + along / SimpleRedLayout.GAUGE_RADIUS;
            assertEquals(
                    SimpleRedLayout.LEFT_GAUGE_CENTER_X
                            - SimpleRedLayout.GAUGE_RADIUS
                            * (float) Math.cos(angle),
                    SimpleRedLayout.pointXAt(
                            along,
                            SimpleRedLayout.GAUGE_RADIUS,
                            false),
                    0.001f);
        }
    }

    @Test
    public void theInsertIsHorizontalAndAsLongAsTheConstant() {
        float before = SimpleRedLayout.SCALE_LEADING_LENGTH;
        float after = before + SimpleRedLayout.SCALE_STRETCH_X;
        for (boolean rightGauge : new boolean[] {false, true}) {
            assertEquals(
                    "the insert must not rise or fall",
                    SimpleRedLayout.pointYAt(
                            before,
                            SimpleRedLayout.GAUGE_RADIUS),
                    SimpleRedLayout.pointYAt(
                            after,
                            SimpleRedLayout.GAUGE_RADIUS),
                    0.001f);
            assertEquals(
                    "it opens the scale by exactly the constant",
                    SimpleRedLayout.SCALE_STRETCH_X,
                    SimpleRedLayout.pointXAt(
                            after,
                            SimpleRedLayout.GAUGE_RADIUS,
                            rightGauge)
                            - SimpleRedLayout.pointXAt(
                                    before,
                                    SimpleRedLayout.GAUGE_RADIUS,
                                    rightGauge),
                    0.001f);
        }
        // Both ends sit at the top of the circle, the one place its
        // tangent is already horizontal, so the joints cannot show.
        assertEquals(CUT, SimpleRedLayout.angleAtLength(before), 0.001f);
        assertEquals(CUT, SimpleRedLayout.angleAtLength(after), 0.001f);
        assertEquals(
                SimpleRedLayout.GAUGE_CENTER_Y
                        - SimpleRedLayout.GAUGE_RADIUS,
                SimpleRedLayout.pointYAt(
                        before,
                        SimpleRedLayout.GAUGE_RADIUS),
                0.001f);
    }

    @Test
    public void theOutlineIsNothingButCircleAndOffset() {
        // Subtracting the offset must give back the plain circle, so the
        // shape collapses to one the moment the constant is zeroed.
        for (int index = 0; index <= 32; index++) {
            float along = SimpleRedLayout.SCALE_TOTAL_LENGTH
                    * index / 32.0f;
            float angle = SimpleRedLayout.angleAtLength(along);
            for (boolean rightGauge : new boolean[] {false, true}) {
                assertEquals(
                        SimpleRedLayout.gaugeCenterX(rightGauge)
                                - SimpleRedLayout.GAUGE_RADIUS
                                * (float) Math.cos(angle),
                        SimpleRedLayout.pointXAt(
                                along,
                                SimpleRedLayout.GAUGE_RADIUS,
                                rightGauge)
                                - SimpleRedLayout.stretchOffsetAtLength(
                                        along,
                                        rightGauge),
                        0.001f);
            }
        }
    }

    @Test
    public void theTwoGaugesAreMirrorImages() {
        // Reflecting a speedometer point about the vertical lands on a
        // tachometer point, which is what keeps the pair symmetric while
        // the tachometer opens the other way. The mirrored distance is
        // derived from the geometry: the reflection sends angle to
        // pi - angle, and the insert onto itself reversed.
        float mirror = SimpleRedLayout.SCALE_STRETCH_X
                + SimpleRedLayout.GAUGE_RADIUS
                * (float) (Math.PI
                        - 2.0 * SimpleRedLayout.SCALE_START_ANGLE_RADIANS);
        for (int index = 0; index <= 32; index++) {
            float along = SimpleRedLayout.SCALE_TOTAL_LENGTH
                    * index / 32.0f;
            float mirrored = mirror - along;
            assertEquals(
                    -(SimpleRedLayout.pointXAt(
                            along,
                            SimpleRedLayout.GAUGE_RADIUS,
                            false)
                            - SimpleRedLayout.LEFT_GAUGE_CENTER_X),
                    SimpleRedLayout.pointXAt(
                            mirrored,
                            SimpleRedLayout.GAUGE_RADIUS,
                            true)
                            - SimpleRedLayout.RIGHT_GAUGE_CENTER_X,
                    0.01f);
            assertEquals(
                    SimpleRedLayout.pointYAt(
                            along,
                            SimpleRedLayout.GAUGE_RADIUS),
                    SimpleRedLayout.pointYAt(
                            mirrored,
                            SimpleRedLayout.GAUGE_RADIUS),
                    0.01f);
        }
    }

    @Test
    public void tachometerOpensTowardsTheScreenCentre() {
        // Its low end faces the middle of the screen, so that is the
        // side that sits pulled out, and it is pulled left.
        assertEquals(
                -SimpleRedLayout.SCALE_STRETCH_X,
                SimpleRedLayout.stretchOffsetAtLength(0.0f, true),
                0.001f);
        assertEquals(
                0.0f,
                SimpleRedLayout.stretchOffsetAtLength(
                        SimpleRedLayout.SCALE_TOTAL_LENGTH,
                        true),
                0.001f);
    }

    @Test
    public void ticksStayEvenlySpacedAcrossTheInsert() {
        // Reading the scale by length rather than by angle is what earns
        // this: spaced by angle, the pair of ticks straddling the cut
        // would sit SCALE_STRETCH_X further apart than every other pair.
        int steps = SimpleRedLayout.majorTickIntervals(false)
                * SimpleRedLayout.minorTicksPerMajor(false);
        float expected = SimpleRedLayout.SCALE_TOTAL_LENGTH / steps;
        for (int index = 0; index < steps; index++) {
            float from = (float) index / steps;
            float to = (float) (index + 1) / steps;
            float gap = (float) Math.hypot(
                    SimpleRedLayout.scaleX(to, false)
                            - SimpleRedLayout.scaleX(from, false),
                    SimpleRedLayout.scaleY(to)
                            - SimpleRedLayout.scaleY(from));
            assertEquals(
                    "tick gap at " + from,
                    expected,
                    gap,
                    0.5f);
        }
    }

    @Test
    public void tangentsFollowTheOutline() {
        // drawScaleText builds the inward normal from these, so a
        // tangent that ignored the insert would push labels off course.
        // A constant magnitude is the outline being walked at a steady
        // rate, and matching a central difference through both joints is
        // the shape having no corner at either.
        float step = 0.001f;
        for (int index = 1; index < 64; index++) {
            float fraction = index / 64.0f;
            float numeric = (SimpleRedLayout.scaleX(fraction + step, false)
                    - SimpleRedLayout.scaleX(fraction - step, false))
                    / (2.0f * step);
            assertEquals(
                    "tangent at fraction " + fraction,
                    numeric,
                    SimpleRedLayout.scaleTangentX(fraction),
                    1.0f);
            assertEquals(
                    "the outline is walked at a steady rate",
                    SimpleRedLayout.SCALE_TOTAL_LENGTH,
                    (float) Math.hypot(
                            SimpleRedLayout.scaleTangentX(fraction),
                            SimpleRedLayout.scaleTangentY(fraction)),
                    0.01f);
        }
        assertEquals(
                "the tangent runs flat along the insert",
                0.0f,
                SimpleRedLayout.scaleTangentY(
                        (SimpleRedLayout.SCALE_LEADING_LENGTH
                                + SimpleRedLayout.SCALE_STRETCH_X * 0.5f)
                                / SimpleRedLayout.SCALE_TOTAL_LENGTH),
                0.001f);
    }

    @Test
    public void gaugesFrameFactoryReadoutsAndBottomValues() {
        // The gauges sit one on each side of the panel, level with one
        // another. Where exactly is a matter of taste and gets retuned;
        // bothGaugesFitThePanelWithoutTouching covers what must hold.
        org.junit.Assert.assertTrue(
                SimpleRedLayout.LEFT_GAUGE_CENTER_X
                        < PANEL_WIDTH * 0.5f);
        org.junit.Assert.assertTrue(
                SimpleRedLayout.RIGHT_GAUGE_CENTER_X
                        > PANEL_WIDTH * 0.5f);
        org.junit.Assert.assertTrue(
                SimpleRedLayout.GAUGE_RADIUS > 0.0f);
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
                0.0f,
                SimpleRedLayout.scaleLength(0.0f),
                0.001f);
        assertEquals(
                SimpleRedLayout.SCALE_TOTAL_LENGTH,
                SimpleRedLayout.scaleLength(1.0f),
                0.001f);
        org.junit.Assert.assertTrue(
                "a sliver of band still needs a segment to draw",
                SimpleRedLayout.pathSegments(
                        0.0f,
                        SimpleRedLayout.scaleLength(0.001f)) >= 1);
        assertEquals(
                SimpleRedLayout.SCALE_PATH_SEGMENTS,
                SimpleRedLayout.pathSegments(
                        0.0f,
                        SimpleRedLayout.SCALE_TOTAL_LENGTH));
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
        org.junit.Assert.assertTrue(
                "the sector must start before the scale does",
                SimpleRedLayout.tachBackdropStartLength() < 0.0f);
        org.junit.Assert.assertTrue(
                "and run past where it ends",
                SimpleRedLayout.tachBackdropEndLength()
                        > SimpleRedLayout.SCALE_TOTAL_LENGTH);
        // The padding is stated as an angle, so it has to arrive as the
        // matching length of circle at the gauge radius.
        assertEquals(
                SimpleRedLayout.TACH_BACKDROP_PADDING_RADIANS
                        * SimpleRedLayout.GAUGE_RADIUS,
                -SimpleRedLayout.tachBackdropStartLength(),
                0.001f);
        assertEquals(
                SimpleRedLayout.angleAtLength(
                        SimpleRedLayout.tachBackdropStartLength()),
                SimpleRedLayout.SCALE_START_ANGLE_RADIANS
                        - SimpleRedLayout.TACH_BACKDROP_PADDING_RADIANS,
                0.001f);
        org.junit.Assert.assertTrue(
                "the sector must not wrap onto itself",
                SimpleRedLayout.angleAtLength(
                        SimpleRedLayout.tachBackdropEndLength())
                        - SimpleRedLayout.angleAtLength(
                                SimpleRedLayout.tachBackdropStartLength())
                        < (float) (Math.PI * 2.0));
        org.junit.Assert.assertTrue(
                "the padded ends still get sampled",
                SimpleRedLayout.pathSegments(
                        SimpleRedLayout.tachBackdropStartLength(),
                        SimpleRedLayout.tachBackdropEndLength())
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
        // The offset is deliberately independent of the radius, so it
        // survives all the way down to a zero radius. Past the insert
        // that leaves the centre itself; before it, the centre shifts by
        // exactly the offset and nothing more.
        assertEquals(
                SimpleRedLayout.RIGHT_GAUGE_CENTER_X,
                SimpleRedLayout.radialX(1.0f, 0.0f, true),
                0.001f);
        assertEquals(
                SimpleRedLayout.RIGHT_GAUGE_CENTER_X
                        + SimpleRedLayout.stretchOffsetAtLength(
                                SimpleRedLayout.scaleLength(0.35f),
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
        // The wheel stands between the four pressures rather than beside
        // them, so it has to sit on their midline.
        assertEquals(
                (SimpleRedLayout.TYRE_LEFT_X
                        + SimpleRedLayout.TYRE_RIGHT_X) * 0.5f,
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
        assertEquals(
                0x08FF2020,
                SimpleRedLayout.PROGRESS_START_COLOR);
        assertEquals(
                0xFFFFD54F,
                SimpleRedLayout.PROGRESS_LEADING_COLOR);
        // The blur is faked by stacking strokes, so it only reads as one
        // if each layer is both narrower and less transparent than the
        // last, from the halo down to the core.
        assertEquals(
                SimpleRedLayout.PROGRESS_HALO_WIDTH,
                SimpleRedLayout.progressSoftLayerWidth(0),
                0.001f);
        assertEquals(
                SimpleRedLayout.PROGRESS_CORE_WIDTH,
                SimpleRedLayout.progressSoftLayerWidth(
                        SimpleRedLayout.PROGRESS_SOFT_LAYER_COUNT - 1),
                0.001f);
        for (int layer = 1;
                layer < SimpleRedLayout.PROGRESS_SOFT_LAYER_COUNT;
                layer++) {
            org.junit.Assert.assertTrue(
                    "layer " + layer + " must narrow",
                    SimpleRedLayout.progressSoftLayerWidth(layer)
                            < SimpleRedLayout.progressSoftLayerWidth(
                                    layer - 1));
            org.junit.Assert.assertTrue(
                    "layer " + layer + " must gain alpha",
                    SimpleRedLayout.progressSoftLayerAlpha(layer)
                            > SimpleRedLayout.progressSoftLayerAlpha(
                                    layer - 1));
        }
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
