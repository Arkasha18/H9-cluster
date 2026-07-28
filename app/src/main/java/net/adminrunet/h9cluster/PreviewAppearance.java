package net.adminrunet.h9cluster;

/** Defines preview colors that differ between vehicle and demo builds. */
public final class PreviewAppearance {
    private static final int COLOR_BLACK = 0xFF000000;
    private static final int COLOR_TRANSPARENT = 0x00000000;

    private PreviewAppearance() {
    }

    public static int backgroundColor(boolean demoMode) {
        return demoMode ? COLOR_BLACK : COLOR_TRANSPARENT;
    }

    public static boolean usesOpaqueWindow(boolean demoMode) {
        return demoMode;
    }
}
