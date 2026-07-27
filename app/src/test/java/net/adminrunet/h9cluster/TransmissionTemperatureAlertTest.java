package net.adminrunet.h9cluster;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class TransmissionTemperatureAlertTest {
    @Test
    public void risingTemperatureUsesDisplayedThresholds() {
        TransmissionTemperatureAlert alert = new TransmissionTemperatureAlert();

        assertLevel(alert, 81.0f, TransmissionTemperatureAlert.Level.NORMAL);
        assertLevel(alert, 99.4f, TransmissionTemperatureAlert.Level.NORMAL);
        assertLevel(alert, 99.5f, TransmissionTemperatureAlert.Level.ELEVATED);
        assertLevel(alert, 109.5f, TransmissionTemperatureAlert.Level.HOT);
        assertLevel(alert, 119.5f, TransmissionTemperatureAlert.Level.CRITICAL);
    }

    @Test
    public void elevatedBandExitsBelowNinetySeven() {
        TransmissionTemperatureAlert alert = new TransmissionTemperatureAlert();

        assertLevel(alert, 100.0f, TransmissionTemperatureAlert.Level.ELEVATED);
        assertLevel(alert, 97.0f, TransmissionTemperatureAlert.Level.ELEVATED);
        assertLevel(alert, 96.0f, TransmissionTemperatureAlert.Level.NORMAL);
    }

    @Test
    public void hotBandExitsBelowOneHundredSeven() {
        TransmissionTemperatureAlert alert = new TransmissionTemperatureAlert();

        assertLevel(alert, 110.0f, TransmissionTemperatureAlert.Level.HOT);
        assertLevel(alert, 107.0f, TransmissionTemperatureAlert.Level.HOT);
        assertLevel(alert, 106.0f, TransmissionTemperatureAlert.Level.ELEVATED);
    }

    @Test
    public void criticalBandExitsBelowOneHundredFifteen() {
        TransmissionTemperatureAlert alert = new TransmissionTemperatureAlert();

        assertLevel(alert, 120.0f, TransmissionTemperatureAlert.Level.CRITICAL);
        assertLevel(alert, 115.0f, TransmissionTemperatureAlert.Level.CRITICAL);
        assertLevel(alert, 114.0f, TransmissionTemperatureAlert.Level.HOT);
    }

    @Test
    public void missingOrInvalidValueResetsAlert() {
        TransmissionTemperatureAlert alert = new TransmissionTemperatureAlert();

        assertLevel(alert, 120.0f, TransmissionTemperatureAlert.Level.CRITICAL);
        assertEquals(
                TransmissionTemperatureAlert.Level.NORMAL,
                alert.update(120.0f, false));

        assertLevel(alert, 110.0f, TransmissionTemperatureAlert.Level.HOT);
        assertEquals(
                TransmissionTemperatureAlert.Level.NORMAL,
                alert.update(Float.NaN, true));
    }

    private static void assertLevel(
            TransmissionTemperatureAlert alert,
            float temperatureC,
            TransmissionTemperatureAlert.Level expected) {
        assertEquals(expected, alert.update(temperatureC, true));
    }
}
