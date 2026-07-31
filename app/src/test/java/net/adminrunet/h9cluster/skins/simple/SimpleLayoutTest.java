package net.adminrunet.h9cluster.skins.simple;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.junit.Test;

public final class SimpleLayoutTest {
    /** Logical panel the skin draws into, fixed by the head unit. */
    private static final float PANEL_WIDTH = 1920.0f;
    private static final float PANEL_HEIGHT = 720.0f;

    private static float outlineExtreme(
            boolean rightGauge,
            float radius,
            boolean horizontal,
            boolean maximum) {
        float best = horizontal
                ? SimpleLayout.pointXAt(0.0f, radius, rightGauge)
                : SimpleLayout.pointYAt(0.0f, radius, rightGauge);
        for (int index = 0; index <= 128; index++) {
            float along = SimpleLayout.SCALE_TOTAL_LENGTH
                    * index / 128.0f;
            float value = horizontal
                    ? SimpleLayout.pointXAt(along, radius, rightGauge)
                    : SimpleLayout.pointYAt(along, radius, rightGauge);
            best = maximum
                    ? Math.max(best, value)
                    : Math.min(best, value);
        }
        return best;
    }

    @Test
    public void mainScalesAreMatchingTiltedArcs() {
        // The speedometer tilt lifts its zero end above the centre and
        // drops its full-scale end below, so the ends straddle the
        // centre line rather than both hanging under it.
        org.junit.Assert.assertTrue(
                SimpleLayout.scaleY(0.0f, false)
                        < SimpleLayout.GAUGE_CENTER_Y);
        org.junit.Assert.assertTrue(
                SimpleLayout.scaleY(1.0f, false)
                        > SimpleLayout.GAUGE_CENTER_Y);
        org.junit.Assert.assertTrue(
                SimpleLayout.scaleY(0.5f, false)
                        < SimpleLayout.GAUGE_CENTER_Y);
        // The tachometer is the mirror, so its tilt runs the other way:
        // zero sits low on the near side and the far end climbs.
        org.junit.Assert.assertTrue(
                SimpleLayout.scaleY(0.0f, true)
                        > SimpleLayout.GAUGE_CENTER_Y);
        org.junit.Assert.assertTrue(
                SimpleLayout.scaleY(1.0f, true)
                        < SimpleLayout.GAUGE_CENTER_Y);
        // Both scales still read left to right.
        for (boolean rightGauge : new boolean[] {false, true}) {
            org.junit.Assert.assertTrue(
                    "zero must sit left of full scale",
                    SimpleLayout.scaleX(0.0f, rightGauge)
                            < SimpleLayout.scaleX(1.0f, rightGauge));
        }
        // The apex is the top of the circle and nothing rises past it.
        for (boolean rightGauge : new boolean[] {false, true}) {
            assertEquals(
                    SimpleLayout.GAUGE_CENTER_Y
                            - SimpleLayout.GAUGE_RADIUS,
                    outlineExtreme(
                            rightGauge,
                            SimpleLayout.GAUGE_RADIUS,
                            false,
                            false),
                    0.001f);
        }
    }

    @Test
    public void bothGaugesFitThePanelWithoutTouching() {
        // These used to be a row of coordinate literals, which broke on
        // every retune while protecting nothing. What actually has to
        // hold is that the pair fits the panel and stays apart, insert
        // and backdrop included.
        float speedoLeft = outlineExtreme(
                false, SimpleLayout.GAUGE_RADIUS, true, false);
        float speedoRight = outlineExtreme(
                false, SimpleLayout.GAUGE_RADIUS, true, true);
        float tachLeft = outlineExtreme(
                true, SimpleLayout.TACH_BACKDROP_RADIUS, true, false);
        float tachRight = outlineExtreme(
                true, SimpleLayout.TACH_BACKDROP_RADIUS, true, true);
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
                true, SimpleLayout.TACH_BACKDROP_RADIUS, false, false);
        float bottom = outlineExtreme(
                true, SimpleLayout.TACH_BACKDROP_RADIUS, false, true);
        org.junit.Assert.assertTrue("the gauges overflow the top", top >= 0.0f);
        org.junit.Assert.assertTrue(
                "the gauges overflow the bottom",
                bottom <= PANEL_HEIGHT);
        org.junit.Assert.assertTrue(
                "the gauges must clear the bottom readouts",
                bottom < SimpleLayout.FUEL_LITERS_BASELINE);
    }

    private static final float CUT = (float) (Math.PI * 0.5);

    @Test
    public void theFirstArcStaysExactlyWhereItWas() {
        // Everything up to the cut is the circle it always was, which is
        // the point of the shape: the scale only opens up after it.
        for (int index = 0; index <= 16; index++) {
            float along = SimpleLayout.SCALE_LEADING_LENGTH
                    * index / 16.0f;
            assertEquals(
                    "nothing moves before the cut",
                    0.0f,
                    SimpleLayout.stretchOffsetAtLength(along),
                    0.0001f);
            float angle = SimpleLayout.SCALE_START_ANGLE_RADIANS
                    + along / SimpleLayout.GAUGE_RADIUS;
            assertEquals(
                    SimpleLayout.LEFT_GAUGE_CENTER_X
                            - SimpleLayout.GAUGE_RADIUS
                            * (float) Math.cos(angle),
                    SimpleLayout.pointXAt(
                            along,
                            SimpleLayout.GAUGE_RADIUS,
                            false),
                    0.001f);
        }
    }

    @Test
    public void theInsertIsHorizontalAndAsLongAsTheConstant() {
        float canonicalBefore = SimpleLayout.SCALE_LEADING_LENGTH;
        float canonicalAfter =
                canonicalBefore + SimpleLayout.SCALE_STRETCH_X;
        for (boolean rightGauge : new boolean[] {false, true}) {
            // The tachometer walks the outline backwards, so its insert
            // is entered from the other end.
            float before = rightGauge
                    ? SimpleLayout.SCALE_TOTAL_LENGTH - canonicalAfter
                    : canonicalBefore;
            float after = rightGauge
                    ? SimpleLayout.SCALE_TOTAL_LENGTH - canonicalBefore
                    : canonicalAfter;
            assertEquals(
                    "the insert must not rise or fall",
                    SimpleLayout.pointYAt(
                            before,
                            SimpleLayout.GAUGE_RADIUS,
                            rightGauge),
                    SimpleLayout.pointYAt(
                            after,
                            SimpleLayout.GAUGE_RADIUS,
                            rightGauge),
                    0.001f);
            assertEquals(
                    "it opens the scale by exactly the constant",
                    SimpleLayout.SCALE_STRETCH_X,
                    SimpleLayout.pointXAt(
                            after,
                            SimpleLayout.GAUGE_RADIUS,
                            rightGauge)
                            - SimpleLayout.pointXAt(
                                    before,
                                    SimpleLayout.GAUGE_RADIUS,
                                    rightGauge),
                    0.001f);
            // The insert sits at the top of the circle, the one place
            // the tangent is already horizontal, so no joint can show.
            assertEquals(
                    SimpleLayout.GAUGE_CENTER_Y
                            - SimpleLayout.GAUGE_RADIUS,
                    SimpleLayout.pointYAt(
                            before,
                            SimpleLayout.GAUGE_RADIUS,
                            rightGauge),
                    0.001f);
        }
        assertEquals(
                CUT,
                SimpleLayout.angleAtLength(canonicalBefore),
                0.001f);
        assertEquals(
                CUT,
                SimpleLayout.angleAtLength(canonicalAfter),
                0.001f);
    }

    @Test
    public void theOutlineIsNothingButCircleAndOffset() {
        // Subtracting the offset must give back the plain circle, so the
        // shape collapses to one the moment the constant is zeroed.
        for (int index = 0; index <= 32; index++) {
            float along = SimpleLayout.SCALE_TOTAL_LENGTH
                    * index / 32.0f;
            for (boolean rightGauge : new boolean[] {false, true}) {
                float canonical = SimpleLayout.canonicalLength(
                        along,
                        rightGauge);
                float angle = SimpleLayout.angleAtLength(canonical);
                float fromCentre = SimpleLayout.pointXAt(
                        along,
                        SimpleLayout.GAUGE_RADIUS,
                        rightGauge)
                        - SimpleLayout.gaugeCenterX(rightGauge);
                if (rightGauge) {
                    fromCentre = -fromCentre;
                }
                assertEquals(
                        -SimpleLayout.GAUGE_RADIUS
                                * (float) Math.cos(angle),
                        fromCentre
                                - SimpleLayout.stretchOffsetAtLength(
                                        canonical),
                        0.001f);
            }
        }
    }

    @Test
    public void theTwoGaugesAreMirrorImages() {
        // The pair is symmetric about the line halfway between the two
        // centres: a tachometer point and the speedometer point at the
        // mirrored distance are equally far from it and level with one
        // another. This is what makes the tachometer read as the
        // speedometer reflected rather than as a second copy of it.
        float centresSum = SimpleLayout.LEFT_GAUGE_CENTER_X
                + SimpleLayout.RIGHT_GAUGE_CENTER_X;
        for (int index = 0; index <= 32; index++) {
            float along = SimpleLayout.SCALE_TOTAL_LENGTH
                    * index / 32.0f;
            float mirrored = SimpleLayout.SCALE_TOTAL_LENGTH - along;
            assertEquals(
                    centresSum,
                    SimpleLayout.pointXAt(
                            along,
                            SimpleLayout.GAUGE_RADIUS,
                            true)
                            + SimpleLayout.pointXAt(
                                    mirrored,
                                    SimpleLayout.GAUGE_RADIUS,
                                    false),
                    0.01f);
            assertEquals(
                    SimpleLayout.pointYAt(
                            mirrored,
                            SimpleLayout.GAUGE_RADIUS,
                            false),
                    SimpleLayout.pointYAt(
                            along,
                            SimpleLayout.GAUGE_RADIUS,
                            true),
                    0.01f);
        }
    }

    @Test
    public void tachometerOpensTowardsTheScreenCentre() {
        // Mirroring puts the tachometer zero on the side facing the
        // middle of the screen, and the insert opens that end outwards
        // from the far edge, so the pair spreads towards each other.
        org.junit.Assert.assertTrue(
                "zero must face the middle of the screen",
                SimpleLayout.scaleX(0.0f, true)
                        < SimpleLayout.RIGHT_GAUGE_CENTER_X);
        assertEquals(
                "the far end is the untouched edge of the circle",
                0.0f,
                SimpleLayout.stretchOffsetAtLength(
                        SimpleLayout.canonicalLength(
                                SimpleLayout.SCALE_TOTAL_LENGTH,
                                true)),
                0.001f);
        assertEquals(
                "zero carries the whole insert",
                SimpleLayout.SCALE_STRETCH_X,
                SimpleLayout.stretchOffsetAtLength(
                        SimpleLayout.canonicalLength(0.0f, true)),
                0.001f);
    }

    @Test
    public void bandAnglesClimbWithTheFractionOnBothGauges() {
        // The band is painted by a sweep gradient placed from the
        // difference between two of these. Wrapping the angle into a
        // turn would make that difference negative on the mirrored
        // tachometer and leave the start of its band uncoloured.
        for (boolean rightGauge : new boolean[] {false, true}) {
            float previous = SimpleLayout.bandAngleDegrees(
                    0.0f,
                    rightGauge);
            for (int index = 1; index <= 32; index++) {
                float angle = SimpleLayout.bandAngleDegrees(
                        index / 32.0f,
                        rightGauge);
                org.junit.Assert.assertTrue(
                        "band angle must climb at " + index,
                        angle > previous);
                previous = angle;
            }
            assertEquals(
                    "a full band spans the whole sweep",
                    SimpleLayout.SCALE_SWEEP_DEGREES,
                    SimpleLayout.bandAngleDegrees(1.0f, rightGauge)
                            - SimpleLayout.bandAngleDegrees(
                                    0.0f,
                                    rightGauge),
                    0.01f);
        }
        // The lead-in has to be real, or the band starts fully
        // transparent and appears to begin above zero.
        org.junit.Assert.assertTrue(
                SimpleLayout.PROGRESS_GRADIENT_LEAD_IN_DEGREES > 0.0f);
        org.junit.Assert.assertTrue(
                "a full band plus its lead-in must stay inside a turn",
                SimpleLayout.SCALE_SWEEP_DEGREES
                        + SimpleLayout.PROGRESS_GRADIENT_LEAD_IN_DEGREES
                        <= 360.0f);
    }

    @Test
    public void ticksStayEvenlySpacedAcrossTheInsert() {
        // Reading the scale by length rather than by angle is what earns
        // this: spaced by angle, the pair of ticks straddling the cut
        // would sit SCALE_STRETCH_X further apart than every other pair.
        for (boolean rightGauge : new boolean[] {false, true}) {
            int steps = SimpleLayout.majorTickIntervals(rightGauge)
                    * SimpleLayout.minorTicksPerMajor(rightGauge);
            float expected = SimpleLayout.SCALE_TOTAL_LENGTH / steps;
            for (int index = 0; index < steps; index++) {
                float from = (float) index / steps;
                float to = (float) (index + 1) / steps;
                float gap = (float) Math.hypot(
                        SimpleLayout.scaleX(to, rightGauge)
                                - SimpleLayout.scaleX(from, rightGauge),
                        SimpleLayout.scaleY(to, rightGauge)
                                - SimpleLayout.scaleY(from, rightGauge));
                assertEquals(
                        "tick gap at " + from,
                        expected,
                        gap,
                        0.5f);
            }
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
        for (boolean rightGauge : new boolean[] {false, true}) {
            for (int index = 1; index < 64; index++) {
                float fraction = index / 64.0f;
                float numericX = (SimpleLayout.scaleX(
                        fraction + step,
                        rightGauge)
                        - SimpleLayout.scaleX(fraction - step, rightGauge))
                        / (2.0f * step);
                float numericY = (SimpleLayout.scaleY(
                        fraction + step,
                        rightGauge)
                        - SimpleLayout.scaleY(fraction - step, rightGauge))
                        / (2.0f * step);
                assertEquals(
                        "tangent x at " + fraction,
                        numericX,
                        SimpleLayout.scaleTangentX(fraction, rightGauge),
                        1.0f);
                assertEquals(
                        "tangent y at " + fraction,
                        numericY,
                        SimpleLayout.scaleTangentY(fraction, rightGauge),
                        1.0f);
                assertEquals(
                        "the outline is walked at a steady rate",
                        SimpleLayout.SCALE_TOTAL_LENGTH,
                        (float) Math.hypot(
                                SimpleLayout.scaleTangentX(
                                        fraction,
                                        rightGauge),
                                SimpleLayout.scaleTangentY(
                                        fraction,
                                        rightGauge)),
                        0.01f);
            }
            float insertMiddle = (SimpleLayout.SCALE_LEADING_LENGTH
                    + SimpleLayout.SCALE_STRETCH_X * 0.5f)
                    / SimpleLayout.SCALE_TOTAL_LENGTH;
            assertEquals(
                    "the tangent runs flat along the insert",
                    0.0f,
                    SimpleLayout.scaleTangentY(
                            rightGauge ? 1.0f - insertMiddle : insertMiddle,
                            rightGauge),
                    0.001f);
        }
    }

    @Test
    public void gaugesFrameFactoryReadoutsAndBottomValues() {
        // The gauges sit one on each side of the panel, level with one
        // another. Where exactly is a matter of taste and gets retuned;
        // bothGaugesFitThePanelWithoutTouching covers what must hold.
        org.junit.Assert.assertTrue(
                SimpleLayout.LEFT_GAUGE_CENTER_X
                        < PANEL_WIDTH * 0.5f);
        org.junit.Assert.assertTrue(
                SimpleLayout.RIGHT_GAUGE_CENTER_X
                        > PANEL_WIDTH * 0.5f);
        org.junit.Assert.assertTrue(
                SimpleLayout.GAUGE_RADIUS > 0.0f);
        assertEquals(
                668.0f,
                SimpleLayout.FUEL_LITERS_BASELINE,
                0.001f);
        assertEquals(
                665.0f,
                SimpleLayout.COOLANT_BASELINE,
                0.001f);
        assertEquals(
                665.0f,
                SimpleLayout.TRANSMISSION_BASELINE,
                0.001f);
        assertEquals(
                1645.0f,
                SimpleLayout.COOLANT_X,
                0.001f);
        assertEquals(
                SimpleLayout.GAUGE_RADIUS - 27.0f,
                SimpleLayout.PROGRESS_BAND_RADIUS,
                0.001f);
        assertEquals(
                SimpleLayout.TRANSMISSION_BASELINE,
                SimpleLayout.TRANSMISSION_LABEL_Y,
                0.001f);
        try {
            float labelX = SimpleLayout.class
                    .getDeclaredField("TRANSMISSION_LABEL_X")
                    .getFloat(null);
            org.junit.Assert.assertTrue(
                    labelX < SimpleLayout.TRANSMISSION_X);
        } catch (ReflectiveOperationException error) {
            org.junit.Assert.fail(
                    "TRANSMISSION_LABEL_X must place АКПП left of value");
        }
    }

    @Test
    public void scaleRangesAreTwoHundredAndSixThousand() {
        assertEquals(200.0f, SimpleLayout.MAX_SPEED_KPH, 0.001f);
        assertEquals(6000.0f, SimpleLayout.MAX_RPM, 0.001f);
        assertEquals(
                1.0f,
                SimpleLayout.speedFraction(200.0f),
                0.001f);
        assertEquals(
                1.0f,
                SimpleLayout.speedFraction(220.0f),
                0.001f);
        assertEquals(
                0.5f,
                SimpleLayout.rpmFraction(3000.0f),
                0.001f);
        assertFalse(SimpleLayout.DRAW_SCALE_UNITS);
    }

    @Test
    public void progressBandStaysInsideTicksAndOutsideLabels() {
        assertEquals(
                SimpleLayout.TICK_MAJOR_INNER_RADIUS,
                SimpleLayout.PROGRESS_BAND_RADIUS,
                0.001f);
        org.junit.Assert.assertTrue(
                SimpleLayout.PROGRESS_BAND_RADIUS
                        + SimpleLayout.PROGRESS_HALO_WIDTH * 0.5f
                        < SimpleLayout.GAUGE_RADIUS);
        org.junit.Assert.assertTrue(
                SimpleLayout.PROGRESS_BAND_RADIUS
                        - SimpleLayout.PROGRESS_HALO_WIDTH * 0.5f
                        > SimpleLayout.GAUGE_RADIUS
                        - SimpleLayout.MAIN_SCALE_LABEL_OFFSET);
        // An empty band sits at the start of the scale and a full one
        // reaches its end, so the band and the ticks agree everywhere.
        assertEquals(
                0.0f,
                SimpleLayout.scaleLength(0.0f),
                0.001f);
        assertEquals(
                SimpleLayout.SCALE_TOTAL_LENGTH,
                SimpleLayout.scaleLength(1.0f),
                0.001f);
        org.junit.Assert.assertTrue(
                "a sliver of band still needs a segment to draw",
                SimpleLayout.pathSegments(
                        0.0f,
                        SimpleLayout.scaleLength(0.001f)) >= 1);
        assertEquals(
                SimpleLayout.SCALE_PATH_SEGMENTS,
                SimpleLayout.pathSegments(
                        0.0f,
                        SimpleLayout.SCALE_TOTAL_LENGTH));
    }

    @Test
    public void numericGearIsLimitedToDriveRatios() {
        assertEquals("", SimpleLayout.formatGear(0));
        assertEquals("1", SimpleLayout.formatGear(1));
        assertEquals("8", SimpleLayout.formatGear(8));
        assertEquals("", SimpleLayout.formatGear(9));
    }

    @Test
    public void steeringAngleRotatesOnlyTheWheelIcon() {
        assertEquals(
                141.0f,
                SimpleLayout.steeringRotation(-141.0f),
                0.001f);
        assertEquals(
                0.0f,
                SimpleLayout.steeringRotation(Float.NaN),
                0.001f);
        assertEquals(
                -1080.0f,
                SimpleLayout.steeringRotation(1200.0f),
                0.001f);
    }

    @Test
    public void allValuesUseUprightText() {
        assertEquals(0.0f, SimpleLayout.TEXT_SKEW_X, 0.001f);
        org.junit.Assert.assertTrue(
                SimpleLayout.SCALE_LABEL_SKEW_X < 0.0f);
        assertEquals(
                "fonts/Rajdhani-Medium.ttf",
                SimpleLayout.SCALE_LABEL_FONT_ASSET);
    }

    @Test
    public void backgroundRingsAreOrderedFromLabelsOutwards() {
        float labelRadius = SimpleLayout.GAUGE_RADIUS
                - SimpleLayout.MAIN_SCALE_LABEL_OFFSET;
        float accentInnerEdge = SimpleLayout.ACCENT_ARC_RADIUS
                - SimpleLayout.ACCENT_GLOW_WIDTH * 0.5f;
        org.junit.Assert.assertTrue(
                "labels must clear the accent arc and its glow",
                labelRadius < accentInnerEdge);
        org.junit.Assert.assertTrue(
                SimpleLayout.ACCENT_ARC_RADIUS
                        + SimpleLayout.ACCENT_ARC_WIDTH * 0.5f
                        < SimpleLayout.TICK_MAJOR_INNER_RADIUS);
        org.junit.Assert.assertTrue(
                SimpleLayout.TICK_MAJOR_INNER_RADIUS
                        < SimpleLayout.TICK_MINOR_INNER_RADIUS);
        org.junit.Assert.assertTrue(
                SimpleLayout.TICK_MINOR_INNER_RADIUS
                        < SimpleLayout.TICK_OUTER_RADIUS);
        org.junit.Assert.assertTrue(
                SimpleLayout.TICK_OUTER_RADIUS
                        <= SimpleLayout.SCALE_ARC_RADIUS);
        assertEquals(
                SimpleLayout.GAUGE_RADIUS,
                SimpleLayout.SCALE_ARC_RADIUS,
                0.001f);
    }

    @Test
    public void minorTicksDivideEveryLabelledInterval() {
        assertEquals(10, SimpleLayout.majorTickIntervals(false));
        assertEquals(6, SimpleLayout.majorTickIntervals(true));
        assertEquals(
                40,
                SimpleLayout.majorTickIntervals(false)
                        * SimpleLayout.minorTicksPerMajor(false));
        assertEquals(
                60,
                SimpleLayout.majorTickIntervals(true)
                        * SimpleLayout.minorTicksPerMajor(true));
    }

    @Test
    public void tachBackdropClearsTheLeftGauge() {
        // Both gauges stretch towards the middle of the screen, so the
        // clearance has to account for them closing in on each other.
        org.junit.Assert.assertTrue(
                SimpleLayout.RIGHT_GAUGE_CENTER_X
                        - SimpleLayout.TACH_BACKDROP_RADIUS
                        - SimpleLayout.SCALE_STRETCH_X
                        > SimpleLayout.LEFT_GAUGE_CENTER_X
                        + SimpleLayout.GAUGE_RADIUS
                        + SimpleLayout.SCALE_STRETCH_X);
    }

    @Test
    public void softBandLayersNarrowFromHaloDownToCore() {
        assertEquals(
                SimpleLayout.PROGRESS_HALO_WIDTH,
                SimpleLayout.progressSoftLayerWidth(0),
                0.001f);
        assertEquals(
                SimpleLayout.PROGRESS_CORE_WIDTH,
                SimpleLayout.progressSoftLayerWidth(
                        SimpleLayout.PROGRESS_SOFT_LAYER_COUNT - 1),
                0.001f);
        float previous = Float.MAX_VALUE;
        for (int layer = 0;
                layer < SimpleLayout.PROGRESS_SOFT_LAYER_COUNT;
                layer++) {
            float width = SimpleLayout.progressSoftLayerWidth(layer);
            org.junit.Assert.assertTrue(
                    "soft layers must narrow monotonically",
                    width < previous);
            previous = width;
        }
        int previousAlpha = -1;
        for (int layer = 0;
                layer < SimpleLayout.PROGRESS_SOFT_LAYER_COUNT;
                layer++) {
            int alpha = SimpleLayout.progressSoftLayerAlpha(layer);
            org.junit.Assert.assertTrue(
                    "stacked layers must stay translucent to accumulate",
                    alpha < 255);
            org.junit.Assert.assertTrue(
                    "alpha must rise as the layers narrow",
                    alpha > previousAlpha);
            previousAlpha = alpha;
        }
        assertEquals(
                SimpleLayout.PROGRESS_SOFT_LAYER_MIN_ALPHA,
                SimpleLayout.progressSoftLayerAlpha(0));
        assertEquals(
                SimpleLayout.PROGRESS_SOFT_LAYER_MAX_ALPHA,
                SimpleLayout.progressSoftLayerAlpha(
                        SimpleLayout.PROGRESS_SOFT_LAYER_COUNT - 1));
        org.junit.Assert.assertTrue(
                "the widest layer must still clear the scale arc",
                SimpleLayout.PROGRESS_BAND_RADIUS
                        + SimpleLayout.progressSoftLayerWidth(0) * 0.5f
                        < SimpleLayout.GAUGE_RADIUS);
    }

    @Test
    public void tipBloomIsClampedToTheBandAndStaysTranslucent() {
        org.junit.Assert.assertTrue(
                SimpleLayout.PROGRESS_TIP_BLOOM_RADIUS > 0.0f);
        org.junit.Assert.assertTrue(
                "a fully opaque bloom would hide the leading edge",
                SimpleLayout.PROGRESS_TIP_BLOOM_ALPHA < 255);
        org.junit.Assert.assertTrue(
                SimpleLayout.PROGRESS_TIP_BLOOM_ALPHA > 0);
    }

    @Test
    public void tachBackdropIsOpaqueBlackOutsideDemoBuilds() {
        assertEquals(
                0xFF000000,
                SimpleLayout.tachBackdropColor(false));
        org.junit.Assert.assertNotEquals(
                "demo builds must tint the backdrop to make it visible",
                SimpleLayout.tachBackdropColor(false),
                SimpleLayout.tachBackdropColor(true));
        assertEquals(
                "the demo tint must stay fully opaque",
                0xFF,
                SimpleLayout.tachBackdropColor(true) >>> 24);
    }

    @Test
    public void tachBackdropCoversTheScaleBandButNotTheCentre() {
        org.junit.Assert.assertTrue(
                "the backdrop must reach past the scale arc",
                SimpleLayout.TACH_BACKDROP_RADIUS
                        > SimpleLayout.SCALE_ARC_RADIUS);
        float labelRadius = SimpleLayout.GAUGE_RADIUS
                - SimpleLayout.MAIN_SCALE_LABEL_OFFSET;
        org.junit.Assert.assertTrue(
                "the backdrop must reach under the scale labels",
                SimpleLayout.TACH_BACKDROP_INNER_RADIUS < labelRadius);
        org.junit.Assert.assertTrue(
                "the gauge centre must stay uncovered",
                SimpleLayout.TACH_BACKDROP_INNER_RADIUS > 0.0f);
        org.junit.Assert.assertTrue(
                SimpleLayout.TACH_BACKDROP_INNER_RADIUS
                        < SimpleLayout.TACH_BACKDROP_RADIUS);
    }

    @Test
    public void tachBackdropOverhangsBothScaleEnds() {
        org.junit.Assert.assertTrue(
                SimpleLayout.TACH_BACKDROP_PADDING_RADIANS > 0.0f);
        org.junit.Assert.assertTrue(
                "the sector must start before the scale does",
                SimpleLayout.tachBackdropStartLength() < 0.0f);
        org.junit.Assert.assertTrue(
                "and run past where it ends",
                SimpleLayout.tachBackdropEndLength()
                        > SimpleLayout.SCALE_TOTAL_LENGTH);
        // The padding is stated as an angle, so it has to arrive as the
        // matching length of circle at the gauge radius.
        assertEquals(
                SimpleLayout.TACH_BACKDROP_PADDING_RADIANS
                        * SimpleLayout.GAUGE_RADIUS,
                -SimpleLayout.tachBackdropStartLength(),
                0.001f);
        assertEquals(
                SimpleLayout.angleAtLength(
                        SimpleLayout.tachBackdropStartLength()),
                SimpleLayout.SCALE_START_ANGLE_RADIANS
                        - SimpleLayout.TACH_BACKDROP_PADDING_RADIANS,
                0.001f);
        org.junit.Assert.assertTrue(
                "the sector must not wrap onto itself",
                SimpleLayout.angleAtLength(
                        SimpleLayout.tachBackdropEndLength())
                        - SimpleLayout.angleAtLength(
                                SimpleLayout.tachBackdropStartLength())
                        < (float) (Math.PI * 2.0));
        org.junit.Assert.assertTrue(
                "the padded ends still get sampled",
                SimpleLayout.pathSegments(
                        SimpleLayout.tachBackdropStartLength(),
                        SimpleLayout.tachBackdropEndLength())
                        > SimpleLayout.SCALE_PATH_SEGMENTS);
    }

    @Test
    public void radialHelpersMatchTheScaleHelpersAtGaugeRadius() {
        for (int index = 0; index <= 8; index++) {
            float fraction = index / 8.0f;
            assertEquals(
                    SimpleLayout.scaleX(fraction, false),
                    SimpleLayout.radialX(
                            fraction,
                            SimpleLayout.GAUGE_RADIUS,
                            false),
                    0.001f);
            assertEquals(
                    SimpleLayout.scaleY(fraction, false),
                    SimpleLayout.radialY(
                            fraction,
                            SimpleLayout.GAUGE_RADIUS,
                            false),
                    0.001f);
        }
        assertEquals(
                SimpleLayout.GAUGE_CENTER_Y,
                SimpleLayout.radialY(0.0f, 0.0f, false),
                0.001f);
        // The offset is deliberately independent of the radius, so it
        // survives all the way down to a zero radius. Off the insert that
        // leaves the centre itself; past it, the centre shifts by exactly
        // the offset and nothing more. The tachometer mirrors, so its
        // shift lands on the other side of its centre.
        assertEquals(
                SimpleLayout.RIGHT_GAUGE_CENTER_X,
                SimpleLayout.radialX(1.0f, 0.0f, true),
                0.001f);
        assertEquals(
                SimpleLayout.RIGHT_GAUGE_CENTER_X
                        - SimpleLayout.stretchOffsetAtLength(
                                SimpleLayout.canonicalLength(
                                        SimpleLayout.scaleLength(0.35f),
                                        true)),
                SimpleLayout.radialX(0.35f, 0.0f, true),
                0.001f);
    }

    @Test
    public void steeringWheelUsesReadableTShapedSpokes() {
        org.junit.Assert.assertTrue(
                SimpleLayout.STEERING_ICON_RADIUS >= 18.0f);
        org.junit.Assert.assertTrue(
                SimpleLayout.STEERING_RIM_WIDTH >= 4.0f);
        org.junit.Assert.assertTrue(
                SimpleLayout.STEERING_SPOKE_WIDTH >= 5.0f);
        org.junit.Assert.assertTrue(
                SimpleLayout.STEERING_HUB_RADIUS >= 6.0f);
        assertEquals(0xFFF9F9F7, SimpleLayout.STEERING_COLOR);
    }

    @Test
    public void topTelemetryIsACompactRightAlignedGroup() {
        assertEquals(
                104.0f,
                SimpleLayout.TYRE_RIGHT_X
                        - SimpleLayout.TYRE_LEFT_X,
                0.001f);
        // The wheel stands between the four pressures rather than beside
        // them, so it has to sit on their midline.
        assertEquals(
                (SimpleLayout.TYRE_LEFT_X
                        + SimpleLayout.TYRE_RIGHT_X) * 0.5f,
                SimpleLayout.STEERING_ICON_X,
                0.001f);
        assertEquals(
                (SimpleLayout.TYRE_TOP_Y
                        + SimpleLayout.TYRE_BOTTOM_Y) * 0.5f
                        + SimpleLayout.STEERING_ICON_VERTICAL_OFFSET,
                SimpleLayout.STEERING_ICON_Y,
                0.001f);
        org.junit.Assert.assertTrue(
                SimpleLayout.STEERING_ICON_VERTICAL_OFFSET < 0.0f);
        assertEquals(
                22.0f,
                SimpleLayout.TYRE_TEXT_SIZE,
                0.001f);
        assertEquals(
                25.0f,
                SimpleLayout.TYRE_BOTTOM_Y
                        - SimpleLayout.TYRE_TOP_Y,
                0.001f);
    }

    @Test
    public void progressBandLayersNarrowAndBrightenTowardsTheCore() {
        // The blur is faked by stacking strokes, so it only reads as one
        // if each layer is both narrower and less transparent than the
        // last, from the halo down to the core.
        assertEquals(
                SimpleLayout.PROGRESS_HALO_WIDTH,
                SimpleLayout.progressSoftLayerWidth(0),
                0.001f);
        assertEquals(
                SimpleLayout.PROGRESS_CORE_WIDTH,
                SimpleLayout.progressSoftLayerWidth(
                        SimpleLayout.PROGRESS_SOFT_LAYER_COUNT - 1),
                0.001f);
        for (int layer = 1;
                layer < SimpleLayout.PROGRESS_SOFT_LAYER_COUNT;
                layer++) {
            org.junit.Assert.assertTrue(
                    "layer " + layer + " must narrow",
                    SimpleLayout.progressSoftLayerWidth(layer)
                            < SimpleLayout.progressSoftLayerWidth(
                                    layer - 1));
            org.junit.Assert.assertTrue(
                    "layer " + layer + " must gain alpha",
                    SimpleLayout.progressSoftLayerAlpha(layer)
                            > SimpleLayout.progressSoftLayerAlpha(
                                    layer - 1));
        }
    }

    @Test
    public void bottomValuesUseApprovedFormatsAndOneBaseline() {
        assertEquals(
                "15.9 L",
                SimpleLayout.formatConsumption(15.9f));
        assertEquals(
                "58 L",
                SimpleLayout.formatFuel(58.2f));
        assertEquals(
                "84 °C",
                SimpleLayout.formatCoolant(84));
        assertEquals(
                "13.4 V",
                SimpleLayout.formatVoltage(13.4f));
        assertEquals(
                "",
                SimpleLayout.formatConsumption(Float.NaN));
        assertEquals(
                "",
                SimpleLayout.formatFuel(-1.0f));
        assertEquals(
                "",
                SimpleLayout.formatVoltage(0.0f));
        assertEquals(
                SimpleLayout.FACTORY_SCALE_BASELINE,
                SimpleLayout.CONSUMPTION_BASELINE,
                0.001f);
        assertEquals(
                SimpleLayout.FACTORY_SCALE_BASELINE,
                SimpleLayout.VOLTAGE_BASELINE,
                0.001f);
    }

    @Test
    public void optionalTelemetryIsHiddenWhenUnavailable() {
        assertEquals(
                "2.35",
                SimpleLayout.formatPressure(2.35f));
        assertEquals(
                "",
                SimpleLayout.formatPressure(0.0f));
        assertEquals(
                "",
                SimpleLayout.formatPressure(Float.NaN));
        assertEquals(
                "79 °C",
                SimpleLayout.formatTransmissionTemperature(
                        79.2f,
                        10_000L,
                        20_000L));
        assertEquals(
                "",
                SimpleLayout.formatTransmissionTemperature(
                        79.2f,
                        10_000L,
                        26_000L));
        assertEquals(
                "",
                SimpleLayout.formatTransmissionTemperature(
                        Float.NaN,
                        10_000L,
                        20_000L));
    }

    @Test
    public void telemetryColorsChangeAtApprovedThresholds() {
        assertEquals(
                SimpleLayout.COLOR_NORMAL,
                SimpleLayout.consumptionColor(20.0f));
        assertEquals(
                SimpleLayout.COLOR_WARNING,
                SimpleLayout.consumptionColor(20.1f));

        assertEquals(
                SimpleLayout.COLOR_NORMAL,
                SimpleLayout.temperatureColor(110.0f));
        assertEquals(
                SimpleLayout.COLOR_WARNING,
                SimpleLayout.temperatureColor(110.1f));
        assertEquals(
                SimpleLayout.COLOR_WARNING,
                SimpleLayout.temperatureColor(120.0f));
        assertEquals(
                SimpleLayout.COLOR_CRITICAL,
                SimpleLayout.temperatureColor(120.1f));

        assertEquals(
                SimpleLayout.COLOR_NORMAL,
                SimpleLayout.voltageColor(12.0f));
        assertEquals(
                SimpleLayout.COLOR_WARNING,
                SimpleLayout.voltageColor(11.9f));

        assertEquals(
                SimpleLayout.COLOR_NORMAL,
                SimpleLayout.pressureColor(2.0f));
        assertEquals(
                SimpleLayout.COLOR_WARNING,
                SimpleLayout.pressureColor(1.99f));

        assertEquals(
                SimpleLayout.COLOR_NORMAL,
                SimpleLayout.fuelColor(8.0f));
        assertEquals(
                SimpleLayout.COLOR_WARNING,
                SimpleLayout.fuelColor(7.9f));
        assertEquals(
                SimpleLayout.COLOR_WARNING,
                SimpleLayout.fuelColor(2.0f));
        assertEquals(
                SimpleLayout.COLOR_CRITICAL,
                SimpleLayout.fuelColor(1.9f));
    }
}
