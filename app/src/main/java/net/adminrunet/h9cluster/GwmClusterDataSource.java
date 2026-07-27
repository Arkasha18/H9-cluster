package net.adminrunet.h9cluster;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Parcel;
import android.os.SystemClock;
import android.util.Log;

import java.util.Arrays;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Read-only source backed by the factory GWM adapter service.
 *
 * On the Binder side only GET_DATA, listener registration and listener
 * removal are used. A separate read-only FDBus reader supplies high-rate RPM;
 * neither path sends vehicle commands.
 */
public final class GwmClusterDataSource
        implements ClusterDataSource,
                ServiceConnection,
                ReadOnlyDataListener.Callback,
                FdbusRpmReader.Listener {
    private static final String TAG = "GwmClusterDataSource";
    private static final String SERVICE_PACKAGE = "com.gwm.android.adapter.server";
    private static final String SERVICE_CLASS =
            "com.gwm.android.adapter.server.GwmAdapterService";
    private static final String SERVICE_DESCRIPTOR =
            "com.gwm.android.adapter.IGwmAdapterService";

    private static final int TRANSACTION_GET_DATA = 1;
    private static final int TRANSACTION_REGISTER_LISTENER = 3;
    private static final int TRANSACTION_UNREGISTER_LISTENER = 4;
    private static final float TANK_CAPACITY_LITERS = 80.0f;
    private static final long REBIND_DELAY_MS = 1500L;
    private static final long FDBUS_RPM_STALE_MS = 500L;

    private static final int INDEX_SPEED = 0;
    private static final int INDEX_RPM = 1;
    private static final int INDEX_ODOMETER = 2;
    private static final int INDEX_DAY = 3;
    private static final int INDEX_TRIP = 4;
    private static final int INDEX_FUEL_PERCENT = 5;
    private static final int INDEX_RANGE = 6;
    private static final int INDEX_COOLANT = 7;
    private static final int INDEX_OUTSIDE_TEMP = 8;
    private static final int INDEX_CURRENT_GEAR = 10;
    private static final int INDEX_TPMS = 11;
    private static final int INDEX_TPMS_UNITS = 12;
    private static final int INDEX_AVG_CONSUMPTION_B = 13;
    private static final int INDEX_AVG_CONSUMPTION_A = 14;
    private static final int INDEX_VOLTAGE = 16;
    private static final int INDEX_STEERING_ANGLE = 18;
    private static final int INDEX_WHEEL_FRONT_LEFT = 19;
    private static final int INDEX_WHEEL_FRONT_RIGHT = 20;
    private static final int INDEX_WHEEL_REAR_LEFT = 21;
    private static final int INDEX_WHEEL_REAR_RIGHT = 22;
    private static final int INDEX_ENGINE_FLYWHEEL_TORQUE = 23;

    private static final String[] DATA_IDS = new String[] {
            "car.basic.vehicle_speed",
            "car.basic.engine_speed",
            "car.basic.total_odometer",
            "car.basic.cur_journey_odometer",
            "car.basic.accumulated_odometer",
            "car.basic.remain_fuel_percentage",
            "car.basic.remain_odometer",
            "car.basic.coolant_temp",
            "car.basic.outside_temp",
            "car.basic.gear_status",
            "car.basic.current_gear",
            "car.basic.tpms_status",
            "car.basic.tpms_units",
            "car.basic.avg_fuel_consumption",
            "car.basic.cur_journey_avg_fuel_consumption_a",
            "car.basic.instant_fuel_consumption",
            "car.basic.battery_voltage",
            "car.basic.tire_temp_unit",
            "car.basic.steering_wheel_angle",
            "car.basic.fl_wheel_speed",
            "car.basic.fr_wheel_speed",
            "car.basic.rl_wheel_speed",
            "car.basic.rr_wheel_speed",
            "car.off_road_info.engine_flywheel_torque"
    };

    private static final Pattern NUMBER_PATTERN =
            Pattern.compile("[-+]?\\d+(?:\\.\\d+)?");

    private final Context context;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final String[] values = new String[DATA_IDS.length];
    private final ReadOnlyDataListener dataListener;
    private final FdbusRpmReader fdbusRpmReader;

    private Listener listener;
    private IBinder service;
    private boolean started;
    private boolean bound;
    private boolean listenerRegistered;
    private ClusterState lastState = ClusterState.empty();
    private long binderRpmUpdatedAtMs;
    private long fdbusRpmUpdatedAtMs;
    private int fdbusRpm;
    private long steeringUpdatedAtMs;

    private final Runnable rebindTask = new Runnable() {
        @Override
        public void run() {
            if (started && service == null && !bound) {
                bindAdapterService();
            }
        }
    };

    public GwmClusterDataSource(Context context) {
        this.context = context.getApplicationContext();
        this.dataListener = new ReadOnlyDataListener(mainHandler, this);
        this.fdbusRpmReader = new FdbusRpmReader(this);
        Arrays.fill(values, null);
    }

    @Override
    public void start(Listener listener) {
        this.listener = listener;
        started = true;
        publishState();
        fdbusRpmReader.start();
        bindAdapterService();
    }

    @Override
    public void stop() {
        started = false;
        mainHandler.removeCallbacks(rebindTask);
        fdbusRpmReader.stop();
        unregisterListener();
        if (bound) {
            try {
                context.unbindService(this);
            } catch (RuntimeException error) {
                Log.w(TAG, "Adapter service was already unbound", error);
            }
        }
        bound = false;
        service = null;
        listener = null;
    }

    private void bindAdapterService() {
        if (!started || bound) {
            return;
        }

        Intent intent = new Intent();
        intent.setClassName(SERVICE_PACKAGE, SERVICE_CLASS);
        try {
            bound = context.bindService(intent, this, Context.BIND_AUTO_CREATE);
            if (!bound) {
                Log.w(TAG, "bindService returned false");
                scheduleRebind();
            }
        } catch (RuntimeException error) {
            bound = false;
            Log.e(TAG, "Cannot bind GWM adapter service", error);
            scheduleRebind();
        }
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder binder) {
        bound = true;
        service = binder;
        listenerRegistered = registerListener();
        requestCurrentData();
        Log.i(TAG, listenerRegistered
                ? "GWM adapter connected; live listener active"
                : "GWM adapter connected; one-shot data only");
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        service = null;
        listenerRegistered = false;
        Log.w(TAG, "GWM adapter service disconnected; waiting for Android reconnect");
    }

    @Override
    public void onBindingDied(ComponentName name) {
        service = null;
        listenerRegistered = false;
        if (bound) {
            try {
                context.unbindService(this);
            } catch (RuntimeException ignored) {
                // Android may have already removed the dead binding.
            }
        }
        bound = false;
        scheduleRebind();
    }

    @Override
    public void onNullBinding(ComponentName name) {
        service = null;
        listenerRegistered = false;
        if (bound) {
            try {
                context.unbindService(this);
            } catch (RuntimeException ignored) {
                // Nothing else is required for a null binding.
            }
        }
        bound = false;
        scheduleRebind();
    }

    @Override
    public void onDataChanged(String id, String value) {
        for (int index = 0; index < DATA_IDS.length; index++) {
            if (DATA_IDS[index].equals(id)) {
                String normalized = normalizeValue(value);
                values[index] = normalized;
                if (normalized != null) {
                    long now = SystemClock.elapsedRealtime();
                    if (index == INDEX_RPM) {
                        binderRpmUpdatedAtMs = now;
                    } else if (index == INDEX_STEERING_ANGLE) {
                        steeringUpdatedAtMs = now;
                    }
                }
                publishState();
                return;
            }
        }
    }

    private void scheduleRebind() {
        if (!started) {
            return;
        }
        mainHandler.removeCallbacks(rebindTask);
        mainHandler.postDelayed(rebindTask, REBIND_DELAY_MS);
    }

    private void requestCurrentData() {
        IBinder currentService = service;
        if (currentService == null) {
            return;
        }

        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(SERVICE_DESCRIPTOR);
            data.writeInt(1);
            data.writeString(context.getPackageName());
            data.writeStringArray(DATA_IDS);
            data.writeStringArray(null);
            data.writeInt(1);

            if (!currentService.transact(TRANSACTION_GET_DATA, data, reply, 0)) {
                Log.w(TAG, "GET_DATA transaction rejected");
                return;
            }

            reply.readException();
            String[] response = reply.createStringArray();
            if (response != null) {
                int count = Math.min(values.length, response.length);
                long now = SystemClock.elapsedRealtime();
                for (int index = 0; index < count; index++) {
                    String value = normalizeValue(response[index]);
                    if (value != null) {
                        values[index] = value;
                        if (index == INDEX_RPM) {
                            binderRpmUpdatedAtMs = now;
                        } else if (index == INDEX_STEERING_ANGLE) {
                            steeringUpdatedAtMs = now;
                        }
                    }
                }
            }
            publishState();
        } catch (Throwable error) {
            Log.e(TAG, "Cannot read GWM data", error);
        } finally {
            data.recycle();
            reply.recycle();
        }
    }

    private boolean registerListener() {
        IBinder currentService = service;
        if (currentService == null) {
            return false;
        }

        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(SERVICE_DESCRIPTOR);
            data.writeString(context.getPackageName());
            data.writeStringArray(DATA_IDS);
            data.writeStrongBinder(dataListener.asBinder());
            if (!currentService.transact(TRANSACTION_REGISTER_LISTENER, data, reply, 0)) {
                return false;
            }
            reply.readException();
            return reply.readInt() != 0;
        } catch (Throwable error) {
            Log.e(TAG, "Cannot register GWM listener", error);
            return false;
        } finally {
            data.recycle();
            reply.recycle();
        }
    }

    private void unregisterListener() {
        IBinder currentService = service;
        if (!listenerRegistered || currentService == null) {
            listenerRegistered = false;
            return;
        }

        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(SERVICE_DESCRIPTOR);
            data.writeString(context.getPackageName());
            data.writeStrongBinder(dataListener.asBinder());
            if (currentService.transact(TRANSACTION_UNREGISTER_LISTENER, data, reply, 0)) {
                reply.readException();
                reply.readInt();
            }
        } catch (Throwable error) {
            Log.w(TAG, "Cannot unregister GWM listener", error);
        } finally {
            listenerRegistered = false;
            data.recycle();
            reply.recycle();
        }
    }

    private void publishState() {
        Listener currentListener = listener;
        if (currentListener == null) {
            return;
        }

        float[] pressures = parseTyrePressures(lastState);
        float fuelPercent = parseFloat(values[INDEX_FUEL_PERCENT], Float.NaN);
        float fuelLiters = Float.isNaN(fuelPercent)
                ? lastState.fuelLiters
                : clamp(fuelPercent, 0.0f, 100.0f) * TANK_CAPACITY_LITERS / 100.0f;

        float consumption = parseFloat(values[INDEX_AVG_CONSUMPTION_A], Float.NaN);
        if (Float.isNaN(consumption)) {
            consumption = parseFloat(
                    values[INDEX_AVG_CONSUMPTION_B],
                    lastState.consumptionLitersPer100Km);
        }

        long now = SystemClock.elapsedRealtime();
        boolean useFdbusRpm = fdbusRpmUpdatedAtMs > 0L
                && now - fdbusRpmUpdatedAtMs <= FDBUS_RPM_STALE_MS;
        int rpm = useFdbusRpm
                ? fdbusRpm
                : clamp(parseInt(values[INDEX_RPM], lastState.rpm), 0, 8000);
        long effectiveRpmUpdatedAtMs = useFdbusRpm
                ? fdbusRpmUpdatedAtMs
                : binderRpmUpdatedAtMs;
        int parsedGear = parseInt(values[INDEX_CURRENT_GEAR], -1);
        int currentGear = parsedGear >= 1 && parsedGear <= 15
                ? parsedGear
                : 0;

        ClusterState state = new ClusterState(
                clamp(parseInt(values[INDEX_SPEED], lastState.speedKph), 0, 220),
                rpm,
                currentGear,
                clamp(parseInt(values[INDEX_COOLANT], lastState.coolantC), 40, 130),
                clamp(fuelLiters, 0.0f, TANK_CAPACITY_LITERS),
                Math.max(0, parseInt(values[INDEX_RANGE], lastState.rangeKm)),
                Math.max(0.0d, parseDouble(values[INDEX_ODOMETER], lastState.odometerKm)),
                Math.max(0.0f, parseFloat(values[INDEX_DAY], lastState.dayKm)),
                Math.max(0.0f, parseFloat(values[INDEX_TRIP], lastState.tripKm)),
                pressures[0],
                pressures[1],
                pressures[2],
                pressures[3],
                Math.max(0.0f, consumption),
                Math.max(0.0f, parseFloat(values[INDEX_VOLTAGE], lastState.voltage)),
                clamp(parseFloat(
                        values[INDEX_OUTSIDE_TEMP],
                        lastState.outsideTemperatureC), -50.0f, 60.0f),
                clamp(parseFloat(
                        values[INDEX_STEERING_ANGLE],
                        lastState.steeringAngleDeg), -1080.0f, 1080.0f),
                clamp(parseFloat(
                        values[INDEX_WHEEL_FRONT_LEFT],
                        lastState.wheelFrontLeftKph), 0.0f, 350.0f),
                clamp(parseFloat(
                        values[INDEX_WHEEL_FRONT_RIGHT],
                        lastState.wheelFrontRightKph), 0.0f, 350.0f),
                clamp(parseFloat(
                        values[INDEX_WHEEL_REAR_LEFT],
                        lastState.wheelRearLeftKph), 0.0f, 350.0f),
                clamp(parseFloat(
                        values[INDEX_WHEEL_REAR_RIGHT],
                        lastState.wheelRearRightKph), 0.0f, 350.0f),
                clamp(parseFloat(
                        values[INDEX_ENGINE_FLYWHEEL_TORQUE],
                        lastState.engineFlywheelTorque), -2000.0f, 2000.0f),
                effectiveRpmUpdatedAtMs,
                steeringUpdatedAtMs,
                lastState.driveMode);
        lastState = state;
        currentListener.onClusterState(state);
    }

    @Override
    public void onFdbusRpm(int rpm, long receivedAtMs) {
        if (!started) {
            return;
        }
        fdbusRpm = clamp(rpm, 0, 8000);
        fdbusRpmUpdatedAtMs = receivedAtMs;
        publishState();
    }

    private float[] parseTyrePressures(ClusterState fallback) {
        float[] result = new float[] {
                fallback.tyreFrontLeftBar,
                fallback.tyreFrontRightBar,
                fallback.tyreRearLeftBar,
                fallback.tyreRearRightBar
        };
        String raw = values[INDEX_TPMS];
        if (raw == null) {
            return result;
        }

        StringTokenizer tokens =
                new StringTokenizer(raw.replace("[", "").replace("]", ""), ",");
        if (tokens.countTokens() < 8) {
            return result;
        }

        String unitCode = values[INDEX_TPMS_UNITS];
        for (int tyre = 0; tyre < 4; tyre++) {
            float pressure = parseFloat(tokens.nextToken(), Float.NaN);
            tokens.nextToken();
            if (Float.isNaN(pressure)) {
                continue;
            }
            float pressureBar = convertPressureToBar(pressure, unitCode);
            if (pressureBar >= 0.0f && pressureBar <= 5.0f) {
                result[tyre] = pressureBar;
            }
        }
        return result;
    }

    private static float convertPressureToBar(float value, String unitCode) {
        if ("1".equals(unitCode)) {
            return value * 0.0689476f;
        }
        if ("2".equals(unitCode)) {
            return value / 100.0f;
        }
        if ("3".equals(unitCode)) {
            return value;
        }
        return value > 20.0f ? value / 100.0f : value;
    }

    private static String normalizeValue(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() == 0
                || "null".equalsIgnoreCase(normalized)
                || "waiting".equalsIgnoreCase(normalized)) {
            return null;
        }
        return normalized;
    }

    private static int parseInt(String value, int fallback) {
        double parsed = parseDouble(value, Double.NaN);
        return Double.isNaN(parsed) ? fallback : (int) Math.round(parsed);
    }

    private static float parseFloat(String value, float fallback) {
        double parsed = parseDouble(value, Double.NaN);
        return Double.isNaN(parsed) ? fallback : (float) parsed;
    }

    private static double parseDouble(String value, double fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException ignored) {
            Matcher matcher = NUMBER_PATTERN.matcher(value);
            if (!matcher.find()) {
                return fallback;
            }
            try {
                return Double.parseDouble(matcher.group());
            } catch (NumberFormatException ignoredAgain) {
                return fallback;
            }
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
