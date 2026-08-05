package net.adminrunet.h9cluster;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class SettingsExitButtonTest {
    private static final float SAVE_LEFT = 255.0f;
    private static final float SAVE_RIGHT = 705.0f;
    private static final float SAVE_BOTTOM_WITH_SETTINGS = 423.0f;
    private static final float CANVAS_HEIGHT = 540.0f;

    @Test
    public void aTapInsideTheButtonCounts() {
        assertTrue(SettingsExitButton.contains(480.0f, 488.0f));
        assertTrue(SettingsExitButton.contains(
                SettingsExitButton.LEFT,
                SettingsExitButton.TOP));
        assertTrue(SettingsExitButton.contains(
                SettingsExitButton.RIGHT,
                SettingsExitButton.BOTTOM));
    }

    @Test
    public void aTapOutsideTheButtonIsIgnored() {
        assertFalse(SettingsExitButton.contains(480.0f, 400.0f));
        assertFalse(SettingsExitButton.contains(200.0f, 488.0f));
        assertFalse(SettingsExitButton.contains(700.0f, 488.0f));
        assertFalse(SettingsExitButton.contains(480.0f, 530.0f));
    }

    @Test
    public void theButtonNeverOverlapsTheSaveButton() {
        assertTrue(
                "the exit button must stay below the save button",
                SettingsExitButton.TOP > SAVE_BOTTOM_WITH_SETTINGS);
        assertTrue(SettingsExitButton.LEFT > SAVE_LEFT);
        assertTrue(SettingsExitButton.RIGHT < SAVE_RIGHT);
    }

    @Test
    public void theButtonStaysInsideTheCanvas() {
        assertTrue(SettingsExitButton.BOTTOM < CANVAS_HEIGHT);
        assertTrue(SettingsExitButton.RIGHT > SettingsExitButton.LEFT);
        assertTrue(SettingsExitButton.BOTTOM > SettingsExitButton.TOP);
    }
}
