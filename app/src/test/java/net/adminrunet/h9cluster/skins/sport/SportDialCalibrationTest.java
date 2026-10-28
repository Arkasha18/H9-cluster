package net.adminrunet.h9cluster.skins.sport;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class SportDialCalibrationTest {
    private static final float TOLERANCE = 0.01f;

    @Test
    public void speedKeepsExistingMeasuredControlPoints() {
        float[] values = {
                0.0f, 20.0f, 40.0f, 60.0f, 80.0f,
                100.0f, 120.0f, 140.0f, 180.0f, 220.0f
        };
        float[] xCoordinates = {
                444.0f, 322.0f, 245.0f, 184.0f, 130.0f,
                96.0f, 118.0f, 202.0f, 360.0f, 526.0f
        };
        float[] yCoordinates = {
                660.0f, 660.0f, 631.0f, 577.0f, 498.0f,
                405.0f, 313.0f, 231.0f, 156.0f, 154.0f
        };

        assertControlPoints(values, xCoordinates, yCoordinates, true);
    }

    @Test
    public void rpmMatchesItsOwnPrintedScale() {
        float[] values = {
                0.0f, 1000.0f, 2000.0f, 3000.0f, 4000.0f,
                5000.0f, 6000.0f, 7000.0f, 8000.0f
        };
        float[] xCoordinates = {
                1598.0f, 1697.0f, 1790.0f, 1824.0f, 1802.0f,
                1718.0f, 1560.0f, 1394.0f, 1266.0f
        };
        float[] yCoordinates = {
                660.0f, 610.0f, 498.0f, 405.0f, 313.0f,
                231.0f, 156.0f, 154.0f, 154.0f
        };

        assertControlPoints(values, xCoordinates, yCoordinates, false);
    }

    @Test
    public void clampsValuesAtBothScaleEnds() {
        assertSamePosition(
                SportDialCalibration.speed(0.0f),
                SportDialCalibration.speed(-10.0f));
        assertSamePosition(
                SportDialCalibration.speed(220.0f),
                SportDialCalibration.speed(250.0f));
        assertSamePosition(
                SportDialCalibration.rpm(0.0f),
                SportDialCalibration.rpm(Float.NaN));
        assertSamePosition(
                SportDialCalibration.rpm(8000.0f),
                SportDialCalibration.rpm(9000.0f));
    }

    @Test
    public void lowSpeedStartsAtPrintedZeroAndReachesTwenty() {
        SportDialCalibration.Sample zero = SportDialCalibration.speed(0.0f);
        SportDialCalibration.Sample ten = SportDialCalibration.speed(10.0f);
        SportDialCalibration.Sample twenty = SportDialCalibration.speed(20.0f);

        assertEquals(444.0f, zero.x, TOLERANCE);
        assertEquals(660.0f, zero.y, TOLERANCE);
        assertTrue(ten.x < zero.x && ten.x > twenty.x);
        assertTrue(ten.y >= 659.0f && ten.y <= 661.0f);
    }

    @Test
    public void lowRpmAdvancesThroughTheFirstPrintedInterval() {
        SportDialCalibration.Sample idle = SportDialCalibration.rpm(700.0f);
        SportDialCalibration.Sample oneThousand = SportDialCalibration.rpm(1000.0f);
        SportDialCalibration.Sample eighteenHundred =
                SportDialCalibration.rpm(1800.0f);

        assertTrue(idle.x > 1655.0f && idle.x < oneThousand.x);
        assertTrue(idle.y < 640.0f && idle.y > oneThousand.y);
        assertEquals(1697.0f, oneThousand.x, TOLERANCE);
        assertEquals(610.0f, oneThousand.y, TOLERANCE);
        assertTrue(eighteenHundred.x > oneThousand.x);
        assertTrue(eighteenHundred.y < oneThousand.y);
        assertNonZeroTangent(idle);
        assertNonZeroTangent(eighteenHundred);
    }

    @Test
    public void intermediateRpmSamplesRemainOnSmoothPath() {
        SportDialCalibration.Sample sample1700 = SportDialCalibration.rpm(1700.0f);
        SportDialCalibration.Sample sample2600 = SportDialCalibration.rpm(2600.0f);

        assertTrue(sample1700.x > 1697.0f && sample1700.x < 1790.0f);
        assertTrue(sample1700.y > 498.0f && sample1700.y < 631.0f);
        assertTrue(sample2600.x > 1790.0f && sample2600.x < 1825.0f);
        assertTrue(sample2600.y > 405.0f && sample2600.y < 498.0f);
        assertNonZeroTangent(sample1700);
        assertNonZeroTangent(sample2600);
    }

    private static void assertControlPoints(
            float[] values,
            float[] xCoordinates,
            float[] yCoordinates,
            boolean speedScale) {
        for (int index = 0; index < values.length; index++) {
            SportDialCalibration.Sample sample = speedScale
                    ? SportDialCalibration.speed(values[index])
                    : SportDialCalibration.rpm(values[index]);
            assertEquals("x at " + values[index], xCoordinates[index], sample.x, TOLERANCE);
            assertEquals("y at " + values[index], yCoordinates[index], sample.y, TOLERANCE);
            assertNonZeroTangent(sample);
        }
    }

    private static void assertSamePosition(
            SportDialCalibration.Sample expected,
            SportDialCalibration.Sample actual) {
        assertEquals(expected.x, actual.x, TOLERANCE);
        assertEquals(expected.y, actual.y, TOLERANCE);
    }

    private static void assertNonZeroTangent(SportDialCalibration.Sample sample) {
        assertTrue(Math.hypot(sample.tangentX, sample.tangentY) > 0.001);
    }
}
