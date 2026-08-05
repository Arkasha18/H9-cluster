package net.adminrunet.h9cluster;

/**
 * Placement of the exit button inside the logical 960x540 settings canvas. The
 * button keeps a fixed spot at the bottom so it never moves when a skin brings
 * its own settings button.
 */
public final class SettingsExitButton {
    public static final float LEFT = 360.0f;
    public static final float TOP = 466.0f;
    public static final float RIGHT = 600.0f;
    public static final float BOTTOM = 510.0f;

    private SettingsExitButton() {
    }

    public static boolean contains(float x, float y) {
        return x >= LEFT && x <= RIGHT && y >= TOP && y <= BOTTOM;
    }
}
