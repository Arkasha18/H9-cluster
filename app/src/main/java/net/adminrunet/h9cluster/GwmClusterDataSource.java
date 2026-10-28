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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Read-only source backed by the factory GWM adapter service.
 *
 * On the Binder side only GET_DATA, listener registration and listener
 * removal are used. A separate read-only FDBus reader supplies high-rate RPM
 * and the cumulative ECM2 fuel counter; transmission temperature is read from
 * the snapshot already maintained by the stock TBOX. None of these paths sends
 * vehicle commands.
 */
public final class GwmClusterDataSource
        implements ClusterDataSource,
                ServiceConnection,
                ReadOnlyDataListener.Callback,
                FdbusRpmReader.Listener,
                TransmissionTemperatureReader.Listener {
    private static final String TAG = "GwmClusterDataSource";
    private static final String SERVICE_PACKAGE = "com.gwm.android.adapter.server";
    private static final String SERVICE_CLASS =
            "com.gwm.android.adapter.server.GwmAdapterService";
    private static final String SERVICE_DESCRIPTOR =
            "com.gwm.android.adapter.IGwmAdapterService";

    private static final int TRANSACTION_GET_DATA = 1;
    private static final int TRANSACTION_REGISTER_LISTENER = 3;
    private static final int TRANSACTION_UNREGISTER_LISTENER = 4;
    private static final int MAX_ENGINE_RPM = 8000;
    private static final float TANK_CAPACITY_LITERS = 80.0f;
    private static final long REBIND_DELAY_MS = 1500L;
    private static final long FDBUS_RPM_STALE_MS = 500L;
    private static final long FDBUS_FUEL_STALE_MS = 2000L;
    private static final long LIVE_REFRESH_MS = 500L;
    private static final long SPEED_CALLBACK_STALE_MS = 1000L;

    private static final int INDEX_SPEED = 0;
    private static final int INDEX_RPM = 1;
    private static final int INDEX_ODOMETER = 2;
    private static final int INDEX_DAY = 3;
    private static final int INDEX_TRIP = 4;
    private static final int INDEX_FUEL_PERCENT = 5;
    private static final int INDEX_RANGE = 6;
    private static final int INDEX_COOLANT = 7;
    private static final int INDEX_OUTSIDE_TEMP = 8;
    private static final int INDEX_GEAR_STATUS = 9;
    private static final int INDEX_CURRENT_GEAR = 10;
    private static final int INDEX_TPMS = 11;
    private static final int INDEX_TPMS_UNITS = 12;
    private static final int INDEX_AVG_CONSUMPTION_B = 13;
    private static final int INDEX_AVG_CONSUMPTION_A = 14;
    private static final int INDEX_VOLTAGE = 15;
    private static final int INDEX_STEERING_ANGLE = 17;
    private static final int INDEX_WHEEL_FRONT_LEFT = 18;
    private static final int INDEX_WHEEL_FRONT_RIGHT = 19;
    private static final int INDEX_WHEEL_REAR_LEFT = 20;
    private static final int INDEX_WHEEL_REAR_RIGHT = 21;
    private static final int INDEX_ENGINE_FLYWHEEL_TORQUE = 22;
    private static final int INDEX_DOOR_STATUS = 23;
    private static final int INDEX_IPK_WARNING = 24;
    private static final int INDEX_INSTANT_FUEL_CONSUMPTION = 25;

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
            "car.basic.battery_voltage",
            "car.basic.tire_temp_unit",
            "car.basic.steering_wheel_angle",
            "car.basic.fl_wheel_speed",
            "car.basic.fr_wheel_speed",
            "car.basic.rl_wheel_speed",
            "car.basic.rr_wheel_speed",
            "car.off_road_info.engine_flywheel_torque",
            "car.basic.door_status",
            "car.ipk_info.tts_contents",
            "car.basic.instant_fuel_consumption"
    };

    // Refresh only fast adapter state. Cached RPM/trip/TTS packets must not
    // receive fabricated freshness timestamps or be replayed as notifications.
    private static final int[] LIVE_REFRESH_INDICES = {
            INDEX_SPEED, INDEX_GEAR_STATUS, INDEX_CURRENT_GEAR,
            INDEX_WHEEL_FRONT_LEFT, INDEX_WHEEL_FRONT_RIGHT,
            INDEX_WHEEL_REAR_LEFT, INDEX_WHEEL_REAR_RIGHT, INDEX_DOOR_STATUS
    };

    private static final Pattern NUMBER_PATTERN =
            Pattern.compile("[-+]?\\d+(?:\\.\\d+)?");

    private final Context context;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final String[] values = new String[DATA_IDS.length];
    private final long[] valueRevisions = new long[DATA_IDS.length];
    private final ReadOnlyDataListener dataListener;
    private final FdbusRpmReader fdbusRpmReader;
    private final FuelConsumptionEstimator fuelConsumptionEstimator =
            new FuelConsumptionEstimator();
    private final TransmissionTemperatureReader transmissionTemperatureReader;
    private final FactoryNotificationMonitor factoryNotificationMonitor =
            new FactoryNotificationMonitor();

    private Listener listener;
    private IBinder service;
    private boolean started;
    private boolean bound;
    private boolean listenerRegistered;
    private ClusterState lastState = ClusterState.empty();
    private long binderRpmUpdatedAtMs;
    private long fdbusRpmUpdatedAtMs;
    private int fdbusRpm;
    private float fdbusFuelFlowLitersPerHour = Float.NaN;
    private long fdbusFuelFlowUpdatedAtMs;
    private long journeyAverageFuelConsumptionUpdatedAtMs;
    private long journeyOdometerUpdatedAtMs;
    private long steeringUpdatedAtMs;
    private float transmissionTemperatureC = Float.NaN;
    private long transmissionTemperatureUpdatedAtMs;
    private boolean factoryNotificationVisible;
    private long speedCallbackAtMs;
    private int adapterGeneration;
    private boolean refreshPending;
    private ExecutorService adapterRefreshExecutor;
    private final Runnable liveRefresh = new Runnable() {
        @Override
        public void run() {
            if (!started || service == null) return;
            long now = SystemClock.elapsedRealtime();
            if (!listenerRegistered || speedCallbackAtMs <= 0
                    || now - speedCallbackAtMs >= SPEED_CALLBACK_STALE_MS) {
                requestLiveRefresh();
            }
            mainHandler.postDelayed(this, LIVE_REFRESH_MS);
        }
    };

    private final Runnable factoryNotificationTimeout = new Runnable() {
        @Override
        public void run() {
            if (!started) {
                return;
            }
            updateFactoryNotificationVisibility(SystemClock.elapsedRealtime());
        }
    };

    static final class RpmSample {
        final int rpm;
        final long updatedAtMs;

        RpmSample(int rpm, long updatedAtMs) {
            this.rpm = rpm;
            this.updatedAtMs = updatedAtMs;
        }
    }

    static final class JourneyOdometerSample {
        final float displayValue;
        final long updatedAtMs;

        JourneyOdometerSample(float displayValue, long updatedAtMs) {
            this.displayValue = displayValue;
            this.updatedAtMs = updatedAtMs;
        }
    }

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
        this.transmissionTemperatureReader =
                new TransmissionTemperatureReader(this);
        Arrays.fill(values, null);
    }

    @Override
    public void start(Listener listener) {
        this.listener = listener;
        started = true;
        fuelConsumptionEstimator.reset();
        fdbusFuelFlowLitersPerHour = Float.NaN;
        fdbusFuelFlowUpdatedAtMs = 0L;
        publishState();
        fdbusRpmReader.start();
        transmissionTemperatureReader.start();
        bindAdapterService();
    }

    @Override
    public void stop() {
        started = false;
        mainHandler.removeCallbacks(rebindTask);
        mainHandler.removeCallbacks(factoryNotificationTimeout);
        stopLiveRefresh();
        fdbusRpmReader.stop();
        fuelConsumptionEstimator.reset();
        fdbusFuelFlowLitersPerHour = Float.NaN;
        fdbusFuelFlowUpdatedAtMs = 0L;
        transmissionTemperatureReader.stop();
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
        if (!started) return;
        stopLiveRefresh();
        bound = true;
        service = binder;
        listenerRegistered = registerListener();
        requestCurrentData();
        mainHandler.postDelayed(liveRefresh, LIVE_REFRESH_MS);
        Log.i(TAG, listenerRegistered
                ? "GWM adapter connected; live listener active"
                : "GWM adapter connected; read-only snapshot refresh active");
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        stopLiveRefresh();
        service = null;
        listenerRegistered = false;
        Log.w(TAG, "GWM adapter service disconnected; waiting for Android reconnect");
    }

    @Override
    public void onBindingDied(ComponentName name) {
        stopLiveRefresh();
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
        stopLiveRefresh();
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
                long now = SystemClock.elapsedRealtime();
                if (index == INDEX_SPEED) {
                    if (!validSpeed(normalized)) {
                        speedCallbackAtMs = 0;
                        return;
                    }
                    speedCallbackAtMs = now;
                }
                storeValue(index, normalized, now);
                updateFactoryNotificationSignal(
                        index,
                        normalized,
                        now);
                publishState();
                return;
            }
        }
    }

    private void storeValue(int index, String value, long nowMs) {
        values[index] = value;
        valueRevisions[index]++;
        if (index == INDEX_RPM) {
            binderRpmUpdatedAtMs = engineRpm(value) >= 0 ? nowMs : 0L;
        } else if (index == INDEX_DAY) {
            journeyOdometerUpdatedAtMs =
                    Float.isFinite(journeyOdometer(value)) ? nowMs : 0L;
        } else if (index == INDEX_AVG_CONSUMPTION_A) {
            float journeyAverage = journeyAverageConsumption(value);
            journeyAverageFuelConsumptionUpdatedAtMs =
                    Float.isFinite(journeyAverage) && journeyAverage > 0.0f
                            ? nowMs
                            : 0L;
        } else if (index == INDEX_STEERING_ANGLE && value != null) {
            steeringUpdatedAtMs = nowMs;
        }
    }

    private void stopLiveRefresh() {
        adapterGeneration++;
        speedCallbackAtMs = 0;
        refreshPending = false;
        mainHandler.removeCallbacks(liveRefresh);
        if (adapterRefreshExecutor != null) {
            adapterRefreshExecutor.shutdownNow();
            adapterRefreshExecutor = null;
        }
    }

    /** A missing listener must not freeze the initial 0 km/h for the entire trip. */
    private void requestLiveRefresh() {
        if (refreshPending || service == null || !started) return;
        if (adapterRefreshExecutor == null) {
            adapterRefreshExecutor = Executors.newSingleThreadExecutor(task -> {
                Thread thread = new Thread(task, "h9-adapter-refresh");
                thread.setDaemon(true);
                return thread;
            });
        }
        refreshPending = true;
        final IBinder requestedService = service;
        final int generation = adapterGeneration;
        final long[] revisions = valueRevisions.clone();
        final String[] ids = new String[LIVE_REFRESH_INDICES.length];
        for (int i = 0; i < ids.length; i++) ids[i] = DATA_IDS[LIVE_REFRESH_INDICES[i]];
        adapterRefreshExecutor.execute(() -> {
            String[] result = readAdapterSnapshot(requestedService, ids);
            mainHandler.post(() -> {
                if (!started || generation != adapterGeneration || service != requestedService) return;
                refreshPending = false;
                if (result == null) return;
                long now = SystemClock.elapsedRealtime();
                for (int i = 0; i < Math.min(result.length, ids.length); i++) {
                    int index = LIVE_REFRESH_INDICES[i];
                    // A newer callback always wins over an older in-flight GET.
                    if (valueRevisions[index] != revisions[index]) continue;
                    String value = normalizeValue(result[i]);
                    if (value == null || (index == INDEX_SPEED && !validSpeed(value))) continue;
                    storeValue(index, value, now);
                    if (index == INDEX_DOOR_STATUS) updateFactoryNotificationSignal(index, value, now);
                }
                publishState();
            });
        });
    }

    private String[] readAdapterSnapshot(IBinder binder, String[] ids) {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(SERVICE_DESCRIPTOR);
            data.writeInt(1);
            data.writeString(context.getPackageName());
            data.writeStringArray(ids);
            data.writeStringArray(null);
            data.writeInt(1);
            if (!binder.transact(TRANSACTION_GET_DATA, data, reply, 0)) return null;
            reply.readException();
            return reply.createStringArray();
        } catch (Exception error) {
            Log.w(TAG, "Adapter live snapshot unavailable", error);
            return null;
        } finally {
            data.recycle();
            reply.recycle();
        }
    }

    private static boolean validSpeed(String rawValue) {
        double value = parseDouble(rawValue, Double.NaN);
        return Double.isFinite(value) && value >= 0 && value <= 220;
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
                    if (index == INDEX_SPEED && !validSpeed(value)) continue;
                    if (value != null
                            || index == INDEX_RPM
                            || index == INDEX_DAY
                            || index == INDEX_AVG_CONSUMPTION_A) {
                        storeValue(index, value, now);
                    }
                }
                updateFactoryNotificationSignal(
                        INDEX_DOOR_STATUS,
                        values[INDEX_DOOR_STATUS],
                        now);
                // A cached TTS packet may describe an already dismissed card.
                // Only a new listener event may open the generic-warning hole.
                factoryNotificationMonitor.seedWarningSnapshot(values[INDEX_IPK_WARNING]);
                updateFactoryNotificationVisibility(now);
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

    private void updateFactoryNotificationSignal(
            int index,
            String value,
            long nowMs) {
        if (index == INDEX_DOOR_STATUS) {
            factoryNotificationMonitor.updateDoorStatus(value, nowMs);
        } else if (index == INDEX_IPK_WARNING) {
            factoryNotificationMonitor.updateWarning(value, nowMs);
        } else {
            return;
        }
        updateFactoryNotificationVisibility(nowMs);
    }

    private void updateFactoryNotificationVisibility(long nowMs) {
        mainHandler.removeCallbacks(factoryNotificationTimeout);
        long remainingWarningMs =
                factoryNotificationMonitor.remainingWarningMs(nowMs);
        if (started && remainingWarningMs > 0L) {
            mainHandler.postDelayed(
                    factoryNotificationTimeout,
                    remainingWarningMs);
        }

        boolean visible = factoryNotificationMonitor.isVisibleAt(nowMs);
        if (factoryNotificationVisible == visible) {
            return;
        }
        factoryNotificationVisible = visible;
        Listener currentListener = listener;
        if (currentListener != null) {
            currentListener.onFactoryNotificationVisibilityChanged(visible);
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

        float journeyAverageConsumption = journeyAverageConsumption(
                values[INDEX_AVG_CONSUMPTION_A]);
        float consumption = clusterConsumption(
                values[INDEX_AVG_CONSUMPTION_A],
                values[INDEX_AVG_CONSUMPTION_B],
                lastState.consumptionLitersPer100Km);
        long now = SystemClock.elapsedRealtime();
        int speedKph = clamp(
                parseInt(values[INDEX_SPEED], lastState.speedKph),
                0,
                220);
        float instantConsumption = selectInstantFuelConsumption(
                now,
                speedKph,
                fdbusFuelFlowLitersPerHour,
                fdbusFuelFlowUpdatedAtMs,
                values[INDEX_INSTANT_FUEL_CONSUMPTION]);
        RpmSample rpmSample = selectRpmSample(
                now,
                fdbusRpm,
                fdbusRpmUpdatedAtMs,
                values[INDEX_RPM],
                binderRpmUpdatedAtMs,
                lastState.rpm,
                lastState.rpmUpdatedAtMs);
        JourneyOdometerSample journeyOdometerSample =
                selectJourneyOdometerSample(
                        values[INDEX_DAY],
                        journeyOdometerUpdatedAtMs,
                        lastState.dayKm);
        int currentGear = normalizeCurrentGear(
                parseInt(values[INDEX_CURRENT_GEAR], -1));
        String gearSelector = values[INDEX_GEAR_STATUS] == null
                ? lastState.gearSelector
                : GearSelector.fromVehicleCode(
                        parseInt(values[INDEX_GEAR_STATUS], -1));

        ClusterState state = new ClusterState(
                speedKph,
                rpmSample.rpm,
                currentGear,
                gearSelector,
                clamp(parseInt(values[INDEX_COOLANT], lastState.coolantC), 40, 130),
                transmissionTemperatureC,
                clamp(fuelLiters, 0.0f, TANK_CAPACITY_LITERS),
                Math.max(0, parseInt(values[INDEX_RANGE], lastState.rangeKm)),
                Math.max(0.0d, parseDouble(values[INDEX_ODOMETER], lastState.odometerKm)),
                journeyOdometerSample.displayValue,
                Math.max(0.0f, parseFloat(values[INDEX_TRIP], lastState.tripKm)),
                pressures[0],
                pressures[1],
                pressures[2],
                pressures[3],
                instantConsumption,
                Math.max(0.0f, consumption),
                journeyAverageConsumption,
                Math.max(0.0f, parseFloat(values[INDEX_VOLTAGE], lastState.voltage)),
                outsideTemperatureC(values[INDEX_OUTSIDE_TEMP]),
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
                rpmSample.updatedAtMs,
                journeyAverageFuelConsumptionUpdatedAtMs,
                journeyOdometerSample.updatedAtMs,
                steeringUpdatedAtMs,
                transmissionTemperatureUpdatedAtMs,
                lastState.driveMode);
        lastState = state;
        currentListener.onClusterState(state);
    }

    @Override
    public void onFdbusRpm(int rpm, long receivedAtMs) {
        if (!started
                || rpm < 0
                || rpm > MAX_ENGINE_RPM
                || receivedAtMs <= 0L) {
            return;
        }
        fdbusRpm = rpm;
        fdbusRpmUpdatedAtMs = receivedAtMs;
        publishState();
    }

    @Override
    public void onFdbusFuelConsumptionCounter(int counter, long receivedAtMs) {
        if (!started) {
            return;
        }
        float flowLitersPerHour = fuelConsumptionEstimator.update(
                counter,
                receivedAtMs);
        if (!Float.isFinite(flowLitersPerHour)) {
            return;
        }
        fdbusFuelFlowLitersPerHour = flowLitersPerHour;
        fdbusFuelFlowUpdatedAtMs = receivedAtMs;
        publishState();
    }

    @Override
    public void onTransmissionTemperature(float temperatureC, long receivedAtMs) {
        if (!started) {
            return;
        }
        transmissionTemperatureC = temperatureC;
        transmissionTemperatureUpdatedAtMs = receivedAtMs;
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

    static RpmSample selectRpmSample(
            long nowMs,
            int fdbusValue,
            long fdbusUpdatedAtMs,
            String binderValue,
            long binderUpdatedAtMs,
            int previousValue,
            long previousUpdatedAtMs) {
        boolean useFdbus = fdbusValue >= 0
                && fdbusValue <= MAX_ENGINE_RPM
                && fdbusUpdatedAtMs > 0L
                && nowMs >= fdbusUpdatedAtMs
                && nowMs - fdbusUpdatedAtMs <= FDBUS_RPM_STALE_MS;
        if (useFdbus) {
            return new RpmSample(fdbusValue, fdbusUpdatedAtMs);
        }

        int binderRpm = engineRpm(binderValue);
        boolean useBinder = binderRpm >= 0
                && binderUpdatedAtMs > 0L
                && nowMs >= binderUpdatedAtMs
                && binderUpdatedAtMs > previousUpdatedAtMs;
        if (useBinder) {
            return new RpmSample(binderRpm, binderUpdatedAtMs);
        }
        return new RpmSample(previousValue, previousUpdatedAtMs);
    }

    static JourneyOdometerSample selectJourneyOdometerSample(
            String rawValue,
            long updatedAtMs,
            float previousDisplayValue) {
        float value = journeyOdometer(rawValue);
        if (Float.isFinite(value) && updatedAtMs > 0L) {
            return new JourneyOdometerSample(value, updatedAtMs);
        }
        return new JourneyOdometerSample(previousDisplayValue, 0L);
    }

    static int engineRpm(String rawValue) {
        double value = parseDouble(rawValue, Double.NaN);
        // The Binder adapter reports -1 while the engine is running. Treat it
        // as unavailable instead of manufacturing a shutdown sample at 0 RPM.
        if (!Double.isFinite(value)
                || value < 0.0d
                || value > MAX_ENGINE_RPM) {
            return -1;
        }
        return (int) Math.round(value);
    }

    static float journeyOdometer(String rawValue) {
        float value = parseFloat(rawValue, Float.NaN);
        return Float.isFinite(value) && value >= 0.0f
                ? value
                : Float.NaN;
    }

    /**
     * The trip summary is contractually tied to
     * {@code cur_journey_avg_fuel_consumption_a}: when that indicator is
     * unavailable the trip has to show "—" rather than a plausible stand-in,
     * so this never falls back to indicator B or to the previous reading.
     */
    static float journeyAverageConsumption(String rawIndicatorA) {
        return parseFloat(rawIndicatorA, Float.NaN);
    }

    /** The cluster gauge, unlike the trip, may substitute B or hold its value. */
    static float clusterConsumption(
            String rawIndicatorA,
            String rawIndicatorB,
            float previousConsumption) {
        float consumption = parseFloat(rawIndicatorA, Float.NaN);
        if (Float.isNaN(consumption)) {
            consumption = parseFloat(rawIndicatorB, previousConsumption);
        }
        return consumption;
    }

    static float instantFuelConsumption(String rawValue) {
        float consumption = parseFloat(rawValue, Float.NaN);
        return Float.isFinite(consumption) && consumption >= 0.0f
                ? consumption
                : Float.NaN;
    }

    static float selectInstantFuelConsumption(
            long nowMs,
            int speedKph,
            float fdbusFlowLitersPerHour,
            long fdbusUpdatedAtMs,
            String binderValue) {
        boolean fdbusFresh = Float.isFinite(fdbusFlowLitersPerHour)
                && fdbusFlowLitersPerHour >= 0.0f
                && fdbusUpdatedAtMs > 0L
                && nowMs >= fdbusUpdatedAtMs
                && nowMs - fdbusUpdatedAtMs <= FDBUS_FUEL_STALE_MS;
        if (fdbusFresh) {
            return FuelConsumptionEstimator.forClusterDisplay(
                    fdbusFlowLitersPerHour,
                    speedKph);
        }
        return instantFuelConsumption(binderValue);
    }

    static int normalizeCurrentGear(int rawGear) {
        if (rawGear >= 1 && rawGear <= 7) {
            return rawGear;
        }
        // Codes are not contiguous: 8 is reverse and the eighth forward ratio
        // arrives as code 9. Reverse carries no ratio to show because the card
        // already spells the selector position out as "R".
        return rawGear == 9 ? 8 : 0;
    }

    /** The adapter returns -1001 on error/timeout; never turn it into -50 °C. */
    static float outsideTemperatureC(String rawValue) {
        if (rawValue == null) return Float.NaN;
        final float value;
        try {
            value = Float.parseFloat(rawValue.trim());
        } catch (NumberFormatException error) {
            return Float.NaN;
        }
        return Float.isFinite(value) && value >= -50.0f && value <= 60.0f
                ? value : Float.NaN;
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
