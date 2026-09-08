package net.adminrunet.h9cluster.skins;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/** Cached device-local clock text for dashboard views, used on the drawing thread. */
public final class DashboardClock {
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.US);
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy", Locale.US);
    private final Date date = new Date();

    private boolean initialized;
    private long cachedSecond;
    private TimeZone cachedTimeZone;
    private String timeText = "";
    private String dateText = "";

    /** Refreshes at most once per wall-clock second, including device time-zone changes. */
    public void update(long wallTimeMillis) {
        long second = Math.floorDiv(wallTimeMillis, 1000L);
        if (initialized && second == cachedSecond) {
            return;
        }
        update(wallTimeMillis, TimeZone.getDefault());
    }

    /** Allows deterministic clock and time-zone inputs without changing device settings. */
    void update(long wallTimeMillis, TimeZone timeZone) {
        long second = Math.floorDiv(wallTimeMillis, 1000L);
        boolean sameTimeZone = cachedTimeZone != null
                && cachedTimeZone.getID().equals(timeZone.getID())
                && cachedTimeZone.hasSameRules(timeZone);
        if (initialized && second == cachedSecond && sameTimeZone) {
            return;
        }

        if (!sameTimeZone) {
            // Keep a snapshot so a mutable caller-owned zone cannot invalidate the cache.
            cachedTimeZone = (TimeZone) timeZone.clone();
            timeFormat.setTimeZone(cachedTimeZone);
            dateFormat.setTimeZone(cachedTimeZone);
        }
        date.setTime(wallTimeMillis);
        timeText = timeFormat.format(date);
        dateText = dateFormat.format(date);
        cachedSecond = second;
        initialized = true;
    }

    public String getTimeText() {
        return timeText;
    }

    public String getDateText() {
        return dateText;
    }
}
