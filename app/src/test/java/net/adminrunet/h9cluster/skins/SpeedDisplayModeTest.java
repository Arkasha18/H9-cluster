package net.adminrunet.h9cluster.skins;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class SpeedDisplayModeTest {
    private static final float TOLERANCE = 0.0001f;

    @Test
    public void realModeKeepsTheMeasuredSpeed() {
        assertEquals(
                76.3f,
                SpeedDisplayMode.REAL.indicatedKph(76.3f),
                TOLERANCE);
        assertEquals(
                0.0f,
                SpeedDisplayMode.REAL.indicatedKph(0.0f),
                TOLERANCE);
    }

    @Test
    public void percentModeAddsFourAndHalfPercent() {
        assertEquals(
                104.5f,
                SpeedDisplayMode.FACTORY_PLUS_4_5_PERCENT
                        .indicatedKph(100.0f),
                TOLERANCE);
        assertEquals(
                0.0f,
                SpeedDisplayMode.FACTORY_PLUS_4_5_PERCENT
                        .indicatedKph(0.0f),
                TOLERANCE);
    }

    @Test
    public void fixedOffsetModeRampsInOverTheFirstTwentyKph() {
        // The zero stays pinned at the printed scale origin and the full
        // +5 km/h is reached by 20 km/h: the behaviour the Sport pointer
        // was originally calibrated to.
        assertEquals(
                0.0f,
                SpeedDisplayMode.FACTORY_PLUS_5_KPH.indicatedKph(0.0f),
                TOLERANCE);
        assertEquals(
                12.5f,
                SpeedDisplayMode.FACTORY_PLUS_5_KPH.indicatedKph(10.0f),
                TOLERANCE);
        assertEquals(
                25.0f,
                SpeedDisplayMode.FACTORY_PLUS_5_KPH.indicatedKph(20.0f),
                TOLERANCE);
        assertEquals(
                81.0f,
                SpeedDisplayMode.FACTORY_PLUS_5_KPH.indicatedKph(76.0f),
                TOLERANCE);
    }

    @Test
    public void fixedOffsetModeNeverOverreadsAtLowerSpeed() {
        float previous = SpeedDisplayMode.FACTORY_PLUS_5_KPH
                .indicatedKph(0.0f);
        for (float speed = 1.0f; speed <= 220.0f; speed += 1.0f) {
            float indicated = SpeedDisplayMode.FACTORY_PLUS_5_KPH
                    .indicatedKph(speed);
            assertTrue(indicated >= speed);
            assertTrue(indicated >= previous);
            previous = indicated;
        }
    }

    @Test
    public void fixedOffsetModeToleratesBrokenInput() {
        assertEquals(
                0.0f,
                SpeedDisplayMode.FACTORY_PLUS_5_KPH.indicatedKph(-10.0f),
                TOLERANCE);
        assertEquals(
                0.0f,
                SpeedDisplayMode.FACTORY_PLUS_5_KPH
                        .indicatedKph(Float.NaN),
                TOLERANCE);
    }

    @Test
    public void keysRoundTripThroughFromKey() {
        for (SpeedDisplayMode mode : SpeedDisplayMode.values()) {
            assertEquals(mode, SpeedDisplayMode.fromKey(mode.key()));
        }
    }

    @Test
    public void unknownAndMissingKeysFallBackToReal() {
        assertEquals(
                SpeedDisplayMode.REAL,
                SpeedDisplayMode.fromKey(null));
        assertEquals(
                SpeedDisplayMode.REAL,
                SpeedDisplayMode.fromKey("miles_per_hour"));
    }
}
