package net.adminrunet.h9cluster;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class SettingsSpeedControlTest {
    private static final float SKIN_CARD_BOTTOM = 252.0f;
    private static final float CONFIGURE_TOP = 318.0f;
    private static final float CANVAS_WIDTH = 960.0f;
    private static final float CANVAS_HEIGHT = 540.0f;

    @Test
    public void aTapInsideEachSegmentHitsItsIndex() {
        for (int index = 0;
                index < SettingsSpeedControl.SEGMENT_COUNT;
                index++) {
            assertEquals(
                    index,
                    SettingsSpeedControl.segmentAt(
                            SettingsSpeedControl.segmentCenterX(index),
                            SettingsSpeedControl.TEXT_BASELINE));
            assertEquals(
                    index,
                    SettingsSpeedControl.segmentAt(
                            SettingsSpeedControl.segmentLeft(index),
                            SettingsSpeedControl.TOP));
            assertEquals(
                    index,
                    SettingsSpeedControl.segmentAt(
                            SettingsSpeedControl.segmentRight(index),
                            SettingsSpeedControl.BOTTOM));
        }
    }

    @Test
    public void aTapOutsideTheRowIsIgnored() {
        assertEquals(
                -1,
                SettingsSpeedControl.segmentAt(
                        480.0f,
                        SettingsSpeedControl.BOTTOM + 1.0f));
        assertEquals(
                -1,
                SettingsSpeedControl.segmentAt(
                        480.0f,
                        SettingsSpeedControl.TOP - 1.0f));
        assertEquals(
                -1,
                SettingsSpeedControl.segmentAt(
                        299.0f,
                        SettingsSpeedControl.TEXT_BASELINE));
        assertEquals(
                -1,
                SettingsSpeedControl.segmentAt(
                        851.0f,
                        SettingsSpeedControl.TEXT_BASELINE));
        // The gap between two segments belongs to neither of them.
        float gapCenter = (SettingsSpeedControl.segmentRight(0)
                + SettingsSpeedControl.segmentLeft(1)) * 0.5f;
        assertEquals(
                -1,
                SettingsSpeedControl.segmentAt(
                        gapCenter,
                        SettingsSpeedControl.TEXT_BASELINE));
    }

    @Test
    public void segmentsDoNotOverlapNeighbouringZones() {
        assertTrue(SettingsSpeedControl.TOP > SKIN_CARD_BOTTOM);
        assertTrue(SettingsSpeedControl.BOTTOM < CONFIGURE_TOP);
        assertTrue(SettingsSpeedControl.LABEL_BASELINE
                > SettingsSpeedControl.TOP);
        assertTrue(SettingsSpeedControl.LABEL_BASELINE
                < SettingsSpeedControl.BOTTOM);
        assertTrue(SettingsSpeedControl.TEXT_BASELINE
                > SettingsSpeedControl.TOP);
        assertTrue(SettingsSpeedControl.TEXT_BASELINE
                < SettingsSpeedControl.BOTTOM);
    }

    @Test
    public void theRowStaysInsideTheCanvas() {
        assertTrue(SettingsSpeedControl.segmentLeft(0) > 0.0f);
        assertTrue(
                SettingsSpeedControl.segmentRight(
                        SettingsSpeedControl.SEGMENT_COUNT - 1)
                        < CANVAS_WIDTH);
        assertTrue(SettingsSpeedControl.BOTTOM < CANVAS_HEIGHT);
        for (int index = 0;
                index < SettingsSpeedControl.SEGMENT_COUNT;
                index++) {
            assertTrue(
                    SettingsSpeedControl.segmentRight(index)
                            > SettingsSpeedControl.segmentLeft(index));
        }
    }
}
