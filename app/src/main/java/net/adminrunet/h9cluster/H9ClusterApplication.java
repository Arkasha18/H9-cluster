package net.adminrunet.h9cluster;

import android.app.Activity;
import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;

/** Initializes persistent support diagnostics before either activity starts. */
public final class H9ClusterApplication extends Application {
    private static final long[] IDENTITY_RETRY_MS = {10_000L, 60_000L, 300_000L};

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private VehicleIdentityReader identityReader;
    private int identityAttempt;

    private final Runnable resolveIdentity = new Runnable() {
        @Override
        public void run() {
            final MachineIdentity.Identity current;
            try {
                current = MachineIdentity.current(H9ClusterApplication.this);
            } catch (RuntimeException error) {
                DiagnosticLog.warn(
                        "identity",
                        "identity_read_failed",
                        "error", error.getClass().getSimpleName());
                return;
            }
            if (current.isVinBased()) {
                return;
            }
            if (identityReader != null) {
                identityReader.stop();
            }
            identityAttempt++;
            identityReader = new VehicleIdentityReader(
                    H9ClusterApplication.this,
                    new VehicleIdentityReader.Listener() {
                        @Override
                        public void onIdentity(MachineIdentity.Identity identity) {
                            DiagnosticLog.info(
                                    "identity",
                                    "machine_identity_ready",
                                    "machine_id", identity.code,
                                    "source", identity.source,
                                    "attempt", String.valueOf(identityAttempt));
                            if (!identity.isVinBased()
                                    && identityAttempt <= IDENTITY_RETRY_MS.length) {
                                mainHandler.postDelayed(
                                        resolveIdentity,
                                        IDENTITY_RETRY_MS[identityAttempt - 1]);
                            }
                        }
                    });
            identityReader.start();
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        DiagnosticLog.initialize(this);
        MachineIdentity.Identity identity = null;
        try {
            identity = MachineIdentity.current(this);
        } catch (RuntimeException error) {
            DiagnosticLog.warn(
                    "identity",
                    "identity_initialization_failed",
                    "error", error.getClass().getSimpleName());
        }
        DiagnosticLog.info(
                "app",
                "process_start",
                "version", BuildConfig.VERSION_NAME,
                "version_code", String.valueOf(BuildConfig.VERSION_CODE),
                "android_sdk", String.valueOf(Build.VERSION.SDK_INT),
                "device_model", safeDeviceModel(),
                "machine_id", identity == null ? "unavailable" : identity.code,
                "identity_source", identity == null ? "unavailable" : identity.source,
                "interactive", String.valueOf(isInteractive()));
        installCrashLogger();
        registerLifecycleLogger();
        registerPowerLogger();
        mainHandler.post(resolveIdentity);
    }

    private void installCrashLogger() {
        final Thread.UncaughtExceptionHandler previous =
                Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(
                new Thread.UncaughtExceptionHandler() {
                    @Override
                    public void uncaughtException(Thread thread, Throwable error) {
                        try {
                            DiagnosticLog.error(
                                    "app",
                                    "uncaught_exception",
                                    "thread", thread == null ? "" : thread.getName(),
                                    "error", error == null
                                            ? "unknown"
                                            : error.getClass().getSimpleName());
                        } catch (Throwable ignored) {
                            // Diagnostics must never replace Android's crash handler.
                        } finally {
                            if (previous != null) {
                                previous.uncaughtException(thread, error);
                            }
                        }
                    }
                });
    }

    private void registerLifecycleLogger() {
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(Activity activity, Bundle state) {
                lifecycle(activity, "created");
            }

            @Override
            public void onActivityStarted(Activity activity) {
                lifecycle(activity, "started");
            }

            @Override
            public void onActivityResumed(Activity activity) {
                lifecycle(activity, "resumed");
            }

            @Override
            public void onActivityPaused(Activity activity) {
                lifecycle(activity, "paused");
            }

            @Override
            public void onActivityStopped(Activity activity) {
                lifecycle(activity, "stopped");
            }

            @Override
            public void onActivitySaveInstanceState(Activity activity, Bundle state) {
                lifecycle(activity, "save_state");
            }

            @Override
            public void onActivityDestroyed(Activity activity) {
                lifecycle(activity, "destroyed");
            }
        });
    }

    private void registerPowerLogger() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_USER_PRESENT);
        filter.addAction(Intent.ACTION_SHUTDOWN);
        filter.addAction(Intent.ACTION_POWER_CONNECTED);
        filter.addAction(Intent.ACTION_POWER_DISCONNECTED);
        filter.addAction("android.intent.action.DREAMING_STARTED");
        filter.addAction("android.intent.action.DREAMING_STOPPED");
        try {
            registerReceiver(new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    DiagnosticLog.info(
                            "android_power",
                            "broadcast",
                            "action", intent == null ? "" : intent.getAction(),
                            "interactive", String.valueOf(isInteractive()));
                }
            }, filter);
        } catch (RuntimeException error) {
            DiagnosticLog.warn(
                    "android_power",
                    "receiver_registration_failed",
                    "error", error.getClass().getSimpleName());
        }
    }

    private void lifecycle(Activity activity, String state) {
        DiagnosticLog.info(
                "activity",
                "lifecycle",
                "activity", activity.getClass().getSimpleName(),
                "state", state,
                "finishing", String.valueOf(activity.isFinishing()),
                "interactive", String.valueOf(isInteractive()));
    }

    private boolean isInteractive() {
        PowerManager manager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        return manager != null && manager.isInteractive();
    }

    private static String safeDeviceModel() {
        String manufacturer = Build.MANUFACTURER == null ? "" : Build.MANUFACTURER.trim();
        String model = Build.MODEL == null ? "" : Build.MODEL.trim();
        return (manufacturer + " " + model).trim();
    }
}
