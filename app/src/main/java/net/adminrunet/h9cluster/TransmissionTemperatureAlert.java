package net.adminrunet.h9cluster;

/**
 * Applies stable warning bands to the displayed transmission-oil temperature.
 *
 * <p>The thresholds are based on the rounded value shown to the driver. Each
 * band has a lower exit point so minor sensor changes around a boundary do not
 * make the card flicker between colors.</p>
 */
public final class TransmissionTemperatureAlert {
    public enum Level {
        NORMAL,
        ELEVATED,
        HOT,
        CRITICAL
    }

    static final int ELEVATED_ENTER_C = 100;
    static final int ELEVATED_EXIT_C = 97;
    static final int HOT_ENTER_C = 110;
    static final int HOT_EXIT_C = 107;
    static final int CRITICAL_ENTER_C = 120;
    static final int CRITICAL_EXIT_C = 115;

    private Level level = Level.NORMAL;

    public Level update(float temperatureC, boolean hasFreshValue) {
        if (!hasFreshValue || Float.isNaN(temperatureC) || Float.isInfinite(temperatureC)) {
            level = Level.NORMAL;
            return level;
        }

        int displayedTemperatureC = Math.round(temperatureC);
        switch (level) {
            case ELEVATED:
                if (displayedTemperatureC >= CRITICAL_ENTER_C) {
                    level = Level.CRITICAL;
                } else if (displayedTemperatureC >= HOT_ENTER_C) {
                    level = Level.HOT;
                } else if (displayedTemperatureC < ELEVATED_EXIT_C) {
                    level = Level.NORMAL;
                }
                break;
            case HOT:
                if (displayedTemperatureC >= CRITICAL_ENTER_C) {
                    level = Level.CRITICAL;
                } else if (displayedTemperatureC < HOT_EXIT_C) {
                    level = displayedTemperatureC >= ELEVATED_ENTER_C
                            ? Level.ELEVATED
                            : Level.NORMAL;
                }
                break;
            case CRITICAL:
                if (displayedTemperatureC < CRITICAL_EXIT_C) {
                    if (displayedTemperatureC >= HOT_ENTER_C) {
                        level = Level.HOT;
                    } else if (displayedTemperatureC >= ELEVATED_ENTER_C) {
                        level = Level.ELEVATED;
                    } else {
                        level = Level.NORMAL;
                    }
                }
                break;
            case NORMAL:
            default:
                level = levelForRisingTemperature(displayedTemperatureC);
                break;
        }
        return level;
    }

    private static Level levelForRisingTemperature(int temperatureC) {
        if (temperatureC >= CRITICAL_ENTER_C) {
            return Level.CRITICAL;
        }
        if (temperatureC >= HOT_ENTER_C) {
            return Level.HOT;
        }
        if (temperatureC >= ELEVATED_ENTER_C) {
            return Level.ELEVATED;
        }
        return Level.NORMAL;
    }
}
