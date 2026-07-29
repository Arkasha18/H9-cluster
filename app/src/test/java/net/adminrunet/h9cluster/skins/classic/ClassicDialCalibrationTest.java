package net.adminrunet.h9cluster.skins.classic;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class ClassicDialCalibrationTest {
    private static final float TOLERANCE_DEG = 0.01f;

    @Test
    public void speedMatchesMeasuredMajorTicks() {
        float[] values = {
                0.0f, 20.0f, 40.0f, 60.0f, 80.0f, 100.0f,
                120.0f, 140.0f, 160.0f, 180.0f, 200.0f, 220.0f
        };
        float[] angles = {
                90.0f, 108.8f, 127.7f, 145.5f, 163.4f, 181.4f,
                201.2f, 221.6f, 245.6f, 270.4f, 292.4f, 304.6f
        };

        for (int index = 0; index < values.length; index++) {
            assertEquals(
                    "speed " + values[index],
                    angles[index],
                    ClassicDialCalibration.speedAngleDeg(values[index]),
                    TOLERANCE_DEG);
        }
    }

    @Test
    public void rpmMatchesMeasuredMajorTicks() {
        float[] values = {
                0.0f, 1000.0f, 2000.0f, 3000.0f, 4000.0f,
                5000.0f, 6000.0f, 7000.0f, 8000.0f
        };
        float[] angles = {
                90.0f, 48.9f, 26.4f, 1.7f, -20.9f,
                -44.4f, -71.5f, -98.0f, -126.4f
        };

        for (int index = 0; index < values.length; index++) {
            assertEquals(
                    "rpm " + values[index],
                    angles[index],
                    ClassicDialCalibration.rpmAngleDeg(values[index]),
                    TOLERANCE_DEG);
        }
    }

    @Test
    public void interpolatesValuesSeenInVehicleVideo() {
        assertEquals(
                33.15f,
                ClassicDialCalibration.rpmAngleDeg(1700.0f),
                TOLERANCE_DEG);
        assertEquals(
                11.58f,
                ClassicDialCalibration.rpmAngleDeg(2600.0f),
                TOLERANCE_DEG);
    }

    @Test
    public void clampsValuesOutsidePrintedScales() {
        assertEquals(
                90.0f,
                ClassicDialCalibration.speedAngleDeg(-1.0f),
                TOLERANCE_DEG);
        assertEquals(
                304.6f,
                ClassicDialCalibration.speedAngleDeg(250.0f),
                TOLERANCE_DEG);
        assertEquals(
                90.0f,
                ClassicDialCalibration.rpmAngleDeg(Float.NaN),
                TOLERANCE_DEG);
        assertEquals(
                -126.4f,
                ClassicDialCalibration.rpmAngleDeg(9000.0f),
                TOLERANCE_DEG);
    }
}
