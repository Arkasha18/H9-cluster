package net.adminrunet.h9cluster;

/**
 * Selector position published by the factory transmission.
 *
 * <p>The vehicle reports the position as a numeric code in
 * {@code car.basic.gear_status}. The codes below were recorded on the car by
 * walking the selector through every position twice.
 */
public final class GearSelector {
    public static final String UNKNOWN = "";
    public static final String PARK = "P";
    public static final String REVERSE = "R";
    public static final String NEUTRAL = "N";
    public static final String DRIVE = "D";
    public static final String MANUAL = "M";

    private static final int CODE_NEUTRAL = 0;
    private static final int CODE_DRIVE = 2;
    private static final int CODE_PARK = 3;
    private static final int CODE_REVERSE = 4;
    private static final int CODE_MANUAL = 5;

    private GearSelector() {
    }

    static String fromVehicleCode(int code) {
        switch (code) {
            case CODE_NEUTRAL:
                return NEUTRAL;
            case CODE_DRIVE:
                return DRIVE;
            case CODE_PARK:
                return PARK;
            case CODE_REVERSE:
                return REVERSE;
            case CODE_MANUAL:
                return MANUAL;
            default:
                return UNKNOWN;
        }
    }

    /**
     * Caption of the gear card.
     *
     * <p>A ratio number only means something while the selector lets the
     * gearbox engage a forward ratio, so park, reverse and neutral stay
     * letter-only. When the position is unknown the caption falls back to the
     * bare ratio, which is what the cluster showed before positions were read.
     */
    public static String label(String selector, int gear) {
        String ratio = gear >= 1 && gear <= 8 ? Integer.toString(gear) : "";
        if (DRIVE.equals(selector) || MANUAL.equals(selector)) {
            return selector + ratio;
        }
        if (PARK.equals(selector)
                || REVERSE.equals(selector)
                || NEUTRAL.equals(selector)) {
            return selector;
        }
        return ratio;
    }
}
