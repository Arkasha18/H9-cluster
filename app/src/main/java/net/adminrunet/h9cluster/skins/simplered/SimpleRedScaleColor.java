package net.adminrunet.h9cluster.skins.simplered;

/**
 * Colour the Simple Red scale is drawn in.
 *
 * <p>A choice states only two colours: the accent that runs along the scale
 * and the leading edge of the progress band. Everything else the band needs
 * is the accent at a different transparency, so deriving those keeps them
 * from drifting apart when the palette is retuned.</p>
 *
 * <p>The white arc, its ticks and the scale numbers are deliberately left
 * out. Readability rests on them, and it should not depend on the choice.</p>
 */
public enum SimpleRedScaleColor {
    /** Ships with the skin, and reproduces how it looked without settings. */
    RED("red", "Красный", 0xFFFF1C1C, 0xFFFFD54F),
    WHITE("white", "Белый", 0xFFEFF1F1, 0xFFFFFFFF),
    MOON("moon", "Лунный", 0xFFCFE0FF, 0xFFEAF2FF),
    YELLOW("yellow", "Жёлтый", 0xFFFFC400, 0xFFFFE68A),
    LIGHT_GREEN("light_green", "Светло-зелёный", 0xFF9CE23A, 0xFFD6F49B),
    GREEN("green", "Зелёный", 0xFF24C04A, 0xFF8FE6A6),
    CYAN("cyan", "Голубой", 0xFF2FC4E8, 0xFFA6ECF8),
    BLUE("blue", "Синий", 0xFF2F6BFF, 0xFF9FBBFF);

    private static final int GLOW_ALPHA = 0xDC;
    private static final int BAND_BODY_ALPHA = 0xAA;
    /** Barely there, so the band fades in rather than starting abruptly. */
    private static final int BAND_START_ALPHA = 0x08;

    /** Stored in the skin settings, so it outlives palette reordering. */
    public final String id;
    public final String title;
    public final int accent;
    public final int leading;

    SimpleRedScaleColor(String id, String title, int accent, int leading) {
        this.id = id;
        this.title = title;
        this.accent = accent;
        this.leading = leading;
    }

    public static SimpleRedScaleColor defaultColor() {
        return RED;
    }

    /**
     * Settings reach the skin from storage and from preview intents, so an
     * unrecognised value has to fall back rather than fail.
     */
    public static SimpleRedScaleColor byId(String id) {
        if (id != null) {
            for (SimpleRedScaleColor color : values()) {
                if (color.id.equals(id)) {
                    return color;
                }
            }
        }
        return defaultColor();
    }

    public int glow() {
        return withAlpha(GLOW_ALPHA);
    }

    public int bandBody() {
        return withAlpha(BAND_BODY_ALPHA);
    }

    public int bandStart() {
        return withAlpha(BAND_START_ALPHA);
    }

    /** The bloom is the leading edge spread out, so it shares its colour. */
    public int bloom() {
        return leading;
    }

    private int withAlpha(int alpha) {
        return alpha << 24 | accent & 0x00FFFFFF;
    }
}
