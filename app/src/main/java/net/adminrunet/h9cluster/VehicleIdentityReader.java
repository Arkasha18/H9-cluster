package net.adminrunet.h9cluster;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Parcel;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** One-shot read-only VIN lookup through the factory GWM adapter. */
final class VehicleIdentityReader implements ServiceConnection {
    interface Listener {
        void onIdentity(MachineIdentity.Identity identity);
    }

    private static final String SERVICE_PACKAGE = "com.gwm.android.adapter.server";
    private static final String SERVICE_CLASS =
            "com.gwm.android.adapter.server.GwmAdapterService";
    private static final String SERVICE_DESCRIPTOR =
            "com.gwm.android.adapter.IGwmAdapterService";
    private static final int TRANSACTION_GET_DATA = 1;
    private static final long TIMEOUT_MS = 5000L;

    /* Different firmware generations use different names for the same field. */
    static final String[] VIN_DATA_IDS = {
            "car.basic.vin",
            "car.basic.vin_code",
            "car.basic.vehicle_vin",
            "car.basic.vehicle_identification_number",
            "car.vehicle.vin"
    };

    private final Context context;
    private final Listener listener;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "h9-vehicle-identity");
        thread.setDaemon(true);
        return thread;
    });
    private boolean started;
    private boolean bound;
    private boolean completed;

    private final Runnable timeout = new Runnable() {
        @Override
        public void run() {
            if (!completed) {
                DiagnosticLog.warn("identity", "vin_query_timeout");
                complete(MachineIdentity.current(context));
            }
        }
    };

    VehicleIdentityReader(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
    }

    void start() {
        if (started || completed) {
            return;
        }
        started = true;
        MachineIdentity.Identity current = MachineIdentity.current(context);
        if (current.isVinBased()) {
            complete(current);
            return;
        }
        Intent intent = new Intent();
        intent.setClassName(SERVICE_PACKAGE, SERVICE_CLASS);
        try {
            bound = context.bindService(intent, this, Context.BIND_AUTO_CREATE);
            DiagnosticLog.info(
                    "identity",
                    "vin_query_bind",
                    "accepted", String.valueOf(bound));
            if (!bound) {
                complete(current);
                return;
            }
            mainHandler.postDelayed(timeout, TIMEOUT_MS);
        } catch (RuntimeException error) {
            DiagnosticLog.warn(
                    "identity",
                    "vin_query_bind_failed",
                    "error", error.getClass().getSimpleName());
            complete(current);
        }
    }

    void stop() {
        completed = true;
        started = false;
        mainHandler.removeCallbacks(timeout);
        if (bound) {
            try {
                context.unbindService(this);
            } catch (RuntimeException ignored) {
                // The factory service may already have removed the binding.
            }
        }
        bound = false;
        executor.shutdownNow();
    }

    @Override
    public void onServiceConnected(ComponentName name, final IBinder service) {
        if (completed) {
            return;
        }
        executor.execute(new Runnable() {
            @Override
            public void run() {
                final MachineIdentity.Identity result = queryIdentity(service);
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        complete(result);
                    }
                });
            }
        });
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        DiagnosticLog.warn("identity", "vin_query_service_disconnected");
        if (!completed) {
            complete(MachineIdentity.current(context));
        }
    }

    @Override
    public void onBindingDied(ComponentName name) {
        DiagnosticLog.warn("identity", "vin_query_binding_died");
        if (!completed) {
            complete(MachineIdentity.current(context));
        }
    }

    @Override
    public void onNullBinding(ComponentName name) {
        DiagnosticLog.warn("identity", "vin_query_null_binding");
        if (!completed) {
            complete(MachineIdentity.current(context));
        }
    }

    private MachineIdentity.Identity queryIdentity(IBinder service) {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(SERVICE_DESCRIPTOR);
            data.writeInt(1);
            data.writeString(context.getPackageName());
            data.writeStringArray(VIN_DATA_IDS);
            data.writeStringArray(null);
            data.writeInt(1);
            if (!service.transact(TRANSACTION_GET_DATA, data, reply, 0)) {
                DiagnosticLog.warn("identity", "vin_query_rejected");
                return MachineIdentity.current(context);
            }
            reply.readException();
            String[] values = reply.createStringArray();
            String vin = findVin(values);
            if (vin == null) {
                DiagnosticLog.warn(
                        "identity",
                        "vin_unavailable",
                        "candidate_count", String.valueOf(VIN_DATA_IDS.length),
                        "response_count", String.valueOf(values == null ? 0 : values.length));
                return MachineIdentity.current(context);
            }
            MachineIdentity.Identity identity = MachineIdentity.acceptVin(context, vin);
            DiagnosticLog.info(
                    "identity",
                    "vin_identity_resolved",
                    "machine_id", identity.code);
            return identity;
        } catch (Throwable error) {
            DiagnosticLog.warn(
                    "identity",
                    "vin_query_failed",
                    "error", error.getClass().getSimpleName());
            return MachineIdentity.current(context);
        } finally {
            data.recycle();
            reply.recycle();
        }
    }

    static String findVin(String[] values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            String vin = MachineIdentity.extractVin(value);
            if (vin != null) {
                return vin;
            }
        }
        return null;
    }

    private void complete(MachineIdentity.Identity identity) {
        if (completed) {
            return;
        }
        completed = true;
        mainHandler.removeCallbacks(timeout);
        if (bound) {
            try {
                context.unbindService(this);
            } catch (RuntimeException ignored) {
                // The binding can disappear between callback and cleanup.
            }
        }
        bound = false;
        executor.shutdownNow();
        if (listener != null) {
            listener.onIdentity(identity);
        }
    }
}
