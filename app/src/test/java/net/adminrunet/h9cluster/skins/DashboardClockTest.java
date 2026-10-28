package net.adminrunet.h9cluster.skins;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import org.junit.Test;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.SimpleTimeZone;
import java.util.TimeZone;

public final class DashboardClockTest {
    private static final TimeZone UTC = TimeZone.getTimeZone("UTC");

    @Test
    public void formatsZeroPaddedTwentyFourHourTimeAndFullDate() {
        DashboardClock clock = new DashboardClock();

        clock.update(utcMillis(2026, 9, 7, 3, 4, 5), UTC);
        assertText(clock, "03:04", "07.09.2026");

        clock.update(utcMillis(2026, 9, 7, 23, 8, 9), UTC);
        assertText(clock, "23:08", "07.09.2026");
    }

    @Test
    public void midnightAdvancesTheDate() {
        assertRollover(2026, 9, 7, "07.09.2026", "08.09.2026");
    }

    @Test
    public void dateRollsOverAtLocalMidnightRatherThanUtcMidnight() {
        DashboardClock clock = new DashboardClock();
        TimeZone zone = new SimpleTimeZone(3 * 60 * 60 * 1000, "Test/East");
        long instant = utcMillis(2026, 9, 7, 20, 59, 59);
        clock.update(instant, zone);
        assertText(clock, "23:59", "07.09.2026");

        clock.update(instant + 1000L, zone);

        assertText(clock, "00:00", "08.09.2026");
    }

    @Test
    public void midnightAdvancesTheMonth() {
        assertRollover(2026, 4, 30, "30.04.2026", "01.05.2026");
    }

    @Test
    public void midnightAdvancesTheYear() {
        assertRollover(2026, 12, 31, "31.12.2026", "01.01.2027");
    }

    @Test
    public void leapYearIncludesFebruaryTwentyNinth() {
        assertRollover(2028, 2, 28, "28.02.2028", "29.02.2028");
        assertRollover(2028, 2, 29, "29.02.2028", "01.03.2028");
    }

    @Test
    public void nonLeapYearAdvancesDirectlyToMarch() {
        assertRollover(2026, 2, 28, "28.02.2026", "01.03.2026");
    }

    @Test
    public void sameSecondReusesTheFormattedStrings() {
        DashboardClock clock = new DashboardClock();
        long instant = utcMillis(2026, 9, 7, 3, 4, 5);
        clock.update(instant, UTC);
        String timeText = clock.getTimeText();
        String dateText = clock.getDateText();

        clock.update(instant + 999L, (TimeZone) UTC.clone());

        assertSame(timeText, clock.getTimeText());
        assertSame(dateText, clock.getDateText());
    }

    @Test
    public void injectedTimeZoneChangeRefreshesWithinTheSameSecond() {
        DashboardClock clock = new DashboardClock();
        long instant = utcMillis(2026, 9, 7, 21, 30, 10);
        clock.update(instant, UTC);
        assertText(clock, "21:30", "07.09.2026");

        clock.update(instant, new SimpleTimeZone(3 * 60 * 60 * 1000, "Test/East"));

        assertText(clock, "00:30", "08.09.2026");
    }

    @Test
    public void mutatedTimeZoneRulesRefreshWithinTheSameSecond() {
        DashboardClock clock = new DashboardClock();
        long instant = utcMillis(2026, 9, 7, 21, 30, 10);
        SimpleTimeZone zone = new SimpleTimeZone(0, "Test/Mutable");
        clock.update(instant, zone);

        zone.setRawOffset(3 * 60 * 60 * 1000);
        clock.update(instant, zone);

        assertText(clock, "00:30", "08.09.2026");
    }

    @Test
    public void changedDaylightSavingRulesRefreshWithTheSameIdAndRawOffset() {
        DashboardClock clock = new DashboardClock();
        long instant = utcMillis(2026, 7, 7, 12, 30, 10);
        clock.update(instant, new SimpleTimeZone(0, "Test/Seasonal"));
        assertText(clock, "12:30", "07.07.2026");
        SimpleTimeZone summerZone = new SimpleTimeZone(
                0,
                "Test/Seasonal",
                Calendar.MARCH, -1, Calendar.SUNDAY, 2 * 60 * 60 * 1000,
                Calendar.OCTOBER, -1, Calendar.SUNDAY, 3 * 60 * 60 * 1000);

        clock.update(instant, summerZone);

        assertText(clock, "13:30", "07.07.2026");
    }

    @Test
    public void publicUpdateUsesDeviceTimeZoneAndRefreshesOnTheNextSecond() {
        TimeZone originalZone = TimeZone.getDefault();
        try {
            TimeZone.setDefault(new SimpleTimeZone(3 * 60 * 60 * 1000, "Test/East"));
            DashboardClock clock = new DashboardClock();
            long instant = utcMillis(2026, 9, 7, 21, 30, 10);
            clock.update(instant);
            assertText(clock, "00:30", "08.09.2026");

            TimeZone.setDefault(UTC);
            clock.update(instant + 500L);
            assertText(clock, "00:30", "08.09.2026");
            clock.update(instant + 1000L);

            assertText(clock, "21:30", "07.09.2026");
        } finally {
            TimeZone.setDefault(originalZone);
        }
    }

    @Test
    public void wallClockCanMoveBackwardsAcrossMidnight() {
        DashboardClock clock = new DashboardClock();
        long midnight = utcMillis(2027, 1, 1, 0, 0, 0);
        clock.update(midnight, UTC);
        assertText(clock, "00:00", "01.01.2027");

        clock.update(midnight - 1L, UTC);
        assertText(clock, "23:59", "31.12.2026");

        clock.update(midnight, UTC);
        assertText(clock, "00:00", "01.01.2027");
    }

    @Test
    public void millisecondsBeforeTheEpochDoNotShareTheEpochSecond() {
        DashboardClock clock = new DashboardClock();
        clock.update(0L, UTC);
        assertText(clock, "00:00", "01.01.1970");

        clock.update(-1L, UTC);

        assertText(clock, "23:59", "31.12.1969");
    }

    @Test
    public void localizedDeviceSettingsDoNotChangeDigitsOrCalendar() {
        Locale originalLocale = Locale.getDefault();
        try {
            for (Locale locale : new Locale[] {
                    new Locale("ar", "EG"), new Locale("th", "TH")
            }) {
                Locale.setDefault(locale);
                DashboardClock clock = new DashboardClock();

                clock.update(utcMillis(2026, 9, 7, 3, 4, 5), UTC);

                assertText(clock, "03:04", "07.09.2026");
            }
        } finally {
            Locale.setDefault(originalLocale);
        }
    }

    private static void assertRollover(
            int year, int month, int day, String previousDate, String nextDate) {
        DashboardClock clock = new DashboardClock();
        long instant = utcMillis(year, month, day, 23, 59, 59);
        clock.update(instant, UTC);
        assertText(clock, "23:59", previousDate);

        clock.update(instant + 1000L, UTC);

        assertText(clock, "00:00", nextDate);
    }

    private static void assertText(DashboardClock clock, String time, String date) {
        assertEquals(time, clock.getTimeText());
        assertEquals(date, clock.getDateText());
    }

    private static long utcMillis(int year, int month, int day, int hour, int minute, int second) {
        GregorianCalendar calendar = new GregorianCalendar(UTC, Locale.US);
        calendar.clear();
        calendar.set(year, month - 1, day, hour, minute, second);
        return calendar.getTimeInMillis();
    }
}
