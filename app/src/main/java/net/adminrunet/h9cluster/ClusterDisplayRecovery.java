package net.adminrunet.h9cluster;

import android.content.Context;
import android.hardware.display.DisplayManager;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.Display;
import android.widget.Toast;

/**
 * Waits briefly for Display 2 and relaunches a production cluster task that
 * was restored or moved onto the main display by the head unit.
 */
final class ClusterDisplayRecovery {
    private static final String TAG = "GWMClusterRecovery";
    private static final long INITIAL_RETRY_DELAY_MS = 450L;
    private static final long RETRY_DELAY_MS = 1_000L;
    private static final long LAUNCH_CONFIRM_DELAY_MS = 1_500L;
    private static final long MAX_WAIT_MS = 30_000L;

    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());
    private static Context applicationContext;
    private static DisplayManager displayManager;
    private static boolean active;
    private static boolean listenerRegistered;
    private static boolean pendingUserRequested;
    private static long deadlineAtMs;

    private static final Runnable ATTEMPT = new Runnable() {
        @Override
        public void run() {
            attemptLaunch();
        }
    };

    private static final DisplayManager.DisplayListener DISPLAY_LISTENER =
            new DisplayManager.DisplayListener() {
                @Override
                public void onDisplayAdded(int displayId) {
                    if (displayId == ClusterDisplayPolicy.CLUSTER_DISPLAY_ID) {
                        scheduleAttempt(0L);
                    }
                }

                @Override
                public void onDisplayRemoved(int displayId) {
                    // PreviewActivity requests recovery if Android moves it.
                }

                @Override
                public void onDisplayChanged(int displayId) {
                    if (displayId == ClusterDisplayPolicy.CLUSTER_DISPLAY_ID) {
                        scheduleAttempt(0L);
                    }
                }
            };

    private ClusterDisplayRecovery() {
    }

    static void request(Context context, final boolean userRequested) {
        final Context appContext = context.getApplicationContext();
        MAIN_HANDLER.post(new Runnable() {
            @Override
            public void run() {
                begin(appContext, userRequested);
            }
        });
    }

    static void confirmOnClusterDisplay() {
        MAIN_HANDLER.post(new Runnable() {
            @Override
            public void run() {
                if (active) {
                    Log.i(TAG, "Cluster confirmed on Display 2");
                    stopWaiting();
                }
            }
        });
    }

    static void cancel() {
        MAIN_HANDLER.post(new Runnable() {
            @Override
            public void run() {
                stopWaiting();
            }
        });
    }

    private static void begin(Context context, boolean userRequested) {
        pendingUserRequested |= userRequested;
        if (!active) {
            active = true;
            applicationContext = context;
            displayManager = (DisplayManager) context.getSystemService(
                    Context.DISPLAY_SERVICE);
            deadlineAtMs = SystemClock.elapsedRealtime() + MAX_WAIT_MS;
            registerDisplayListener();
            Log.w(TAG, "Waiting for cluster window on Display 2");
        }
        scheduleAttempt(INITIAL_RETRY_DELAY_MS);
    }

    private static void attemptLaunch() {
        if (!active || applicationContext == null) {
            return;
        }
        if (AutostartPreferences.isAutostartSuspended(applicationContext)) {
            stopWaiting();
            return;
        }
        if (SystemClock.elapsedRealtime() >= deadlineAtMs) {
            Context context = applicationContext;
            Log.e(TAG, "Display 2 recovery timed out");
            stopWaiting();
            Toast.makeText(
                    context,
                    "Приборный дисплей недоступен. Откройте H9 Cluster и повторите запуск",
                    Toast.LENGTH_LONG).show();
            return;
        }
        if (!ClusterLauncher.isClusterDisplayAvailable(applicationContext)) {
            scheduleAttempt(RETRY_DELAY_MS);
            return;
        }
        boolean accepted = ClusterLauncher.retryOnClusterDisplay(
                applicationContext,
                pendingUserRequested);
        Log.i(TAG, "Display 2 recovery launch accepted=" + accepted);
        scheduleAttempt(accepted ? LAUNCH_CONFIRM_DELAY_MS : RETRY_DELAY_MS);
    }

    private static void registerDisplayListener() {
        if (displayManager == null || listenerRegistered) {
            return;
        }
        displayManager.registerDisplayListener(DISPLAY_LISTENER, MAIN_HANDLER);
        listenerRegistered = true;
    }

    private static void scheduleAttempt(long delayMs) {
        if (!active) {
            return;
        }
        MAIN_HANDLER.removeCallbacks(ATTEMPT);
        MAIN_HANDLER.postDelayed(ATTEMPT, delayMs);
    }

    private static void stopWaiting() {
        MAIN_HANDLER.removeCallbacks(ATTEMPT);
        if (displayManager != null && listenerRegistered) {
            try {
                displayManager.unregisterDisplayListener(DISPLAY_LISTENER);
            } catch (RuntimeException error) {
                Log.w(TAG, "Cannot unregister display listener", error);
            }
        }
        active = false;
        listenerRegistered = false;
        pendingUserRequested = false;
        deadlineAtMs = 0L;
        displayManager = null;
        applicationContext = null;
    }
}
