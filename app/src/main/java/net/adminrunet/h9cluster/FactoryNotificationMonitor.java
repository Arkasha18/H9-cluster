package net.adminrunet.h9cluster;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tracks the read-only factory signals that cause the QNX notification card.
 *
 * <p>Door state is level-triggered. Generic IPK warnings are event-like and
 * therefore have a timeout as a fail-safe in case their dismiss event is lost.
 */
final class FactoryNotificationMonitor {
    static final long WARNING_TIMEOUT_MS = 10_000L;

    private static final Pattern INTEGER_PATTERN = Pattern.compile("-?\\d+");

    private boolean doorOpen;
    private long warningVisibleUntilMs;
    private int lastWarningId;
    private int lastWarningAction;

    boolean updateDoorStatus(String rawValue, long nowMs) {
        doorOpen = containsPositiveValue(rawValue);
        return isVisibleAt(nowMs);
    }

    boolean updateWarning(String rawValue, long nowMs) {
        int[] event = warningEvent(rawValue);
        if (event == null) {
            if (Integer.valueOf(0).equals(firstInteger(rawValue))) {
                warningVisibleUntilMs = 0L;
                lastWarningId = 0;
                lastWarningAction = 2;
            }
            return isVisibleAt(nowMs);
        }
        int warningId = event[0];
        int action = event[2];
        if (warningId <= 0 || action == 2) {
            // The dismiss packet can retain its warnId; it is not a new alert.
            warningVisibleUntilMs = 0L;
        } else if (action == 1
                && (warningId != lastWarningId || action != lastWarningAction)) {
            warningVisibleUntilMs = saturatedAdd(nowMs, WARNING_TIMEOUT_MS);
        }
        lastWarningId = warningId;
        lastWarningAction = action;
        return isVisibleAt(nowMs);
    }

    /** GET_DATA is a cache, not evidence that QNX is still displaying this event. */
    void seedWarningSnapshot(String rawValue) {
        int[] event = warningEvent(rawValue);
        if (event != null) {
            lastWarningId = event[0];
            lastWarningAction = event[2];
        }
        warningVisibleUntilMs = 0L;
    }

    private static int[] warningEvent(String rawValue) {
        if (rawValue == null) return null;
        Matcher matcher = INTEGER_PATTERN.matcher(rawValue);
        int[] values = new int[3];
        for (int i = 0; i < values.length; i++) {
            if (!matcher.find()) return null;
            try {
                values[i] = Integer.parseInt(matcher.group());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return matcher.find() ? null : values;
    }

    boolean isVisibleAt(long nowMs) {
        return doorOpen || warningVisibleUntilMs > nowMs;
    }

    long remainingWarningMs(long nowMs) {
        return Math.max(0L, warningVisibleUntilMs - nowMs);
    }

    static boolean containsPositiveValue(String rawValue) {
        if (rawValue == null) {
            return false;
        }
        Matcher matcher = INTEGER_PATTERN.matcher(rawValue);
        while (matcher.find()) {
            try {
                if (Integer.parseInt(matcher.group()) > 0) {
                    return true;
                }
            } catch (NumberFormatException ignored) {
                // Keep looking; malformed values must never leave a stale hole.
            }
        }
        return false;
    }

    static Integer firstInteger(String rawValue) {
        if (rawValue == null) {
            return null;
        }
        Matcher matcher = INTEGER_PATTERN.matcher(rawValue);
        if (!matcher.find()) {
            return null;
        }
        try {
            return Integer.valueOf(matcher.group());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static long saturatedAdd(long value, long increment) {
        if (value > Long.MAX_VALUE - increment) {
            return Long.MAX_VALUE;
        }
        return value + increment;
    }
}
