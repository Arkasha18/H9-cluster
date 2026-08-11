package net.adminrunet.h9cluster;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class SettingsUpdateButtonTest {
    private static final float SAVE_BOTTOM_WITH_SETTINGS = 423.0f;
    private static final float CANVAS_WIDTH = 960.0f;
    private static final float CANVAS_HEIGHT = 540.0f;

    @Test
    public void aTapInsideTheButtonCounts() {
        assertTrue(SettingsUpdateButton.contains(770.0f, 488.0f));
        assertTrue(SettingsUpdateButton.contains(
                SettingsUpdateButton.LEFT,
                SettingsUpdateButton.TOP));
        assertTrue(SettingsUpdateButton.contains(
                SettingsUpdateButton.RIGHT,
                SettingsUpdateButton.BOTTOM));
    }

    @Test
    public void aTapOutsideTheButtonIsIgnored() {
        assertFalse(SettingsUpdateButton.contains(639.0f, 488.0f));
        assertFalse(SettingsUpdateButton.contains(901.0f, 488.0f));
        assertFalse(SettingsUpdateButton.contains(770.0f, 465.0f));
        assertFalse(SettingsUpdateButton.contains(770.0f, 511.0f));
    }

    @Test
    public void theButtonDoesNotOverlapSaveOrExit() {
        assertTrue(SettingsUpdateButton.TOP > SAVE_BOTTOM_WITH_SETTINGS);
        assertTrue(SettingsUpdateButton.LEFT > SettingsExitButton.RIGHT);
    }

    @Test
    public void theButtonStaysInsideTheCanvas() {
        assertTrue(SettingsUpdateButton.RIGHT < CANVAS_WIDTH);
        assertTrue(SettingsUpdateButton.BOTTOM < CANVAS_HEIGHT);
        assertTrue(SettingsUpdateButton.RIGHT > SettingsUpdateButton.LEFT);
        assertTrue(SettingsUpdateButton.BOTTOM > SettingsUpdateButton.TOP);
    }
}
