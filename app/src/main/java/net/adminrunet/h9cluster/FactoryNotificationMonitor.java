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

    boolean updateDoorStatus(String rawValue, long nowMs) {
        doorOpen = containsPositiveValue(rawValue);
        return isVisibleAt(nowMs);
    }

    boolean updateWarning(String rawValue, long nowMs) {
        Integer warningId = firstInteger(rawValue);
        if (warningId == null) {
            return isVisibleAt(nowMs);
        }
        warningVisibleUntilMs = warningId.intValue() > 0
                ? saturatedAdd(nowMs, WARNING_TIMEOUT_MS)
                : 0L;
        return isVisibleAt(nowMs);
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
