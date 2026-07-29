package net.adminrunet.h9cluster.trip;

import android.content.Context;
import android.content.SharedPreferences;

/** Persists an active trip independently from skin settings. */
public final class TripSessionStore implements TripSessionPersistence {
    private static final String PREFERENCES = "trip_session";
    private static final String KEY_ACTIVE = "active";
    private static final String KEY_STARTED_AT = "started_at";
    private static final String KEY_LAST_UPDATED_AT = "last_updated_at";
    private static final String KEY_DISTANCE = "distance";
    private static final String KEY_LAST_JOURNEY = "last_journey";
    private static final String KEY_LAST_JOURNEY_VALID = "last_journey_valid";
    private static final String KEY_DISTANCE_VALID = "distance_valid";
    private static final String KEY_FUEL = "fuel";
    private static final String KEY_LAST_FUEL = "last_fuel";
    private static final String KEY_LAST_FUEL_VALID = "last_fuel_valid";
    private static final String KEY_LAST_SPEED = "last_speed";
    private static final String KEY_FUEL_RELIABLE = "fuel_reliable";
    private static final String KEY_HAS_FUEL_INTERVAL = "has_fuel_interval";
    private static final String KEY_LAST_AVERAGE_FUEL =
            "last_average_fuel";
    private static final String KEY_LAST_AVERAGE_FUEL_VALID =
            "last_average_fuel_valid";

    private final SharedPreferences preferences;

    public TripSessionStore(Context context) {
        preferences = context
                .getApplicationContext()
                .getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
    }

    @Override
    public TripSession load(long nowMs) {
        if (!preferences.getBoolean(KEY_ACTIVE, false)) {
            return null;
        }
        TripSession decoded = new TripSession(
                true,
                preferences.getLong(KEY_STARTED_AT, -1L),
                preferences.getLong(KEY_LAST_UPDATED_AT, -1L),
                decodeDouble(KEY_DISTANCE),
                decodeDouble(KEY_LAST_JOURNEY),
                preferences.getBoolean(KEY_LAST_JOURNEY_VALID, false),
                preferences.getBoolean(KEY_DISTANCE_VALID, false),
                decodeDouble(KEY_FUEL),
                decodeFloat(KEY_LAST_FUEL),
                preferences.getBoolean(KEY_LAST_FUEL_VALID, false),
                preferences.getInt(KEY_LAST_SPEED, 0),
                preferences.getBoolean(KEY_FUEL_RELIABLE, false),
                preferences.getBoolean(KEY_HAS_FUEL_INTERVAL, false),
                decodeFloat(KEY_LAST_AVERAGE_FUEL),
                preferences.getBoolean(
                        KEY_LAST_AVERAGE_FUEL_VALID,
                        false));
        TripSession normalized =
                TripSessionNormalizer.normalize(decoded, nowMs);
        if (normalized == null) {
            clearSync();
        }
        return normalized;
    }

    @Override
    public void saveAsync(TripSession session) {
        editorFor(session).apply();
    }

    @Override
    public boolean saveSync(TripSession session) {
        return editorFor(session).commit();
    }

    @Override
    public void clearAsync() {
        preferences.edit().clear().apply();
    }

    @Override
    public boolean clearSync() {
        return preferences.edit().clear().commit();
    }

    private SharedPreferences.Editor editorFor(TripSession session) {
        return preferences.edit()
                .putBoolean(KEY_ACTIVE, session.active)
                .putLong(KEY_STARTED_AT, session.startedAtMs)
                .putLong(KEY_LAST_UPDATED_AT, session.lastUpdatedAtMs)
                .putLong(
                        KEY_DISTANCE,
                        Double.doubleToRawLongBits(session.distanceKm))
                .putLong(
                        KEY_LAST_JOURNEY,
                        Double.doubleToRawLongBits(
                                session.lastJourneyOdometerKm))
                .putBoolean(
                        KEY_LAST_JOURNEY_VALID,
                        session.lastJourneyOdometerValid)
                .putBoolean(KEY_DISTANCE_VALID, session.distanceValid)
                .putLong(
                        KEY_FUEL,
                        Double.doubleToRawLongBits(session.fuelLiters))
                .putInt(
                        KEY_LAST_FUEL,
                        Float.floatToRawIntBits(
                                session.lastFuelConsumption))
                .putBoolean(
                        KEY_LAST_FUEL_VALID,
                        session.lastFuelConsumptionValid)
                .putInt(KEY_LAST_SPEED, session.lastSpeedKph)
                .putBoolean(KEY_FUEL_RELIABLE, session.fuelReliable)
                .putBoolean(
                        KEY_HAS_FUEL_INTERVAL,
                        session.hasFuelInterval)
                .putInt(
                        KEY_LAST_AVERAGE_FUEL,
                        Float.floatToRawIntBits(
                                session.lastAverageFuelConsumption))
                .putBoolean(
                        KEY_LAST_AVERAGE_FUEL_VALID,
                        session.lastAverageFuelConsumptionValid);
    }

    private double decodeDouble(String key) {
        return Double.longBitsToDouble(preferences.getLong(
                key,
                Double.doubleToRawLongBits(Double.NaN)));
    }

    private float decodeFloat(String key) {
        return Float.intBitsToFloat(preferences.getInt(
                key,
                Float.floatToRawIntBits(Float.NaN)));
    }
}
