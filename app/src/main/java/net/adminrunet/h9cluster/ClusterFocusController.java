package net.adminrunet.h9cluster;

import android.car.Car;
import android.car.CarAppFocusManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

/**
 * Owns automotive cluster focus while a custom skin is visible.
 *
 * <p>The vehicle keeps critical system indicators in its own composition
 * layer. Focus type 3 removes only the factory dashboard/navigation content,
 * as verified on the target Android 9 head unit. Binder death remains the
 * final fail-safe if this process terminates before {@link #destroy()}.</p>
 */
final class ClusterFocusController
        implements FactoryClusterController,
        CarAppFocusManager.OnAppFocusOwnershipCallback {
    private static final String TAG = "H9ClusterFocus";
    private static final int CLUSTER_FOCUS_TYPE = 3;
    private static final int MAX_REACQUIRE_ATTEMPTS = 3;
    private static final long REACQUIRE_DELAY_MS = 750L;

    private final Context context;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Runnable reacquireFocus = new Runnable() {
        @Override
        public void run() {
            if (focusManager == null) {
                connect();
            } else {
                requestFocusIfReady();
            }
        }
    };
    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            if (!enabled || destroyed || car == null) {
                releaseAndDisconnect();
                return;
            }
            try {
                focusManager = (CarAppFocusManager) car.getCarManager(
                        Car.APP_FOCUS_SERVICE);
                Log.i(TAG, "Connected to CarAppFocusManager");
                requestFocusIfReady();
            } catch (Exception error) {
                Log.e(TAG, "Cannot obtain CarAppFocusManager", error);
                focusManager = null;
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            focusManager = null;
            focusRequested = false;
            car = null;
            if (enabled && !destroyed) {
                mainHandler.postDelayed(
                        reacquireFocus,
                        REACQUIRE_DELAY_MS);
            }
        }
    };

    private Car car;
    private CarAppFocusManager focusManager;
    private boolean enabled;
    private boolean destroyed;
    private boolean focusRequested;
    private int reacquireAttempts;

    ClusterFocusController(Context context) {
        this.context = context;
    }

    @Override
    public void setEnabled(boolean shouldHideFactoryCluster) {
        if (destroyed) {
            return;
        }
        if (shouldHideFactoryCluster == enabled) {
            if (enabled) {
                requestFocusIfReady();
            }
            return;
        }
        enabled = shouldHideFactoryCluster;
        mainHandler.removeCallbacks(reacquireFocus);
        reacquireAttempts = 0;
        if (enabled) {
            connect();
        } else {
            releaseAndDisconnect();
        }
    }

    @Override
    public void destroy() {
        if (destroyed) {
            return;
        }
        destroyed = true;
        enabled = false;
        mainHandler.removeCallbacksAndMessages(null);
        releaseAndDisconnect();
    }

    private void connect() {
        if (car != null) {
            requestFocusIfReady();
            return;
        }
        try {
            car = Car.createCar(context, serviceConnection);
            if (car == null) {
                Log.w(TAG, "Car service is unavailable");
                return;
            }
            car.connect();
        } catch (Exception error) {
            Log.e(TAG, "Cannot connect to automotive Car service", error);
            car = null;
        }
    }

    private void requestFocusIfReady() {
        if (!enabled
                || destroyed
                || focusRequested
                || focusManager == null) {
            return;
        }
        try {
            int result = focusManager.requestAppFocus(
                    CLUSTER_FOCUS_TYPE,
                    this);
            focusRequested = result
                    == CarAppFocusManager.APP_FOCUS_REQUEST_SUCCEEDED;
            if (focusRequested) {
                Log.i(TAG, "Factory cluster focus hidden");
            } else {
                Log.w(TAG, "Cluster focus request was rejected");
            }
        } catch (Exception error) {
            Log.e(TAG, "Cluster focus request failed", error);
            focusRequested = false;
        }
    }

    private void releaseAndDisconnect() {
        if (focusRequested && focusManager != null) {
            try {
                focusManager.abandonAppFocus(this, CLUSTER_FOCUS_TYPE);
                Log.i(TAG, "Factory cluster focus restored");
            } catch (Exception error) {
                Log.e(TAG, "Cluster focus release failed", error);
            }
        }
        focusRequested = false;
        focusManager = null;
        if (car != null) {
            try {
                car.disconnect();
            } catch (Exception error) {
                Log.e(TAG, "Car service disconnect failed", error);
            }
            car = null;
        }
    }

    @Override
    public void onAppFocusOwnershipLost(int appType) {
        if (appType != CLUSTER_FOCUS_TYPE) {
            return;
        }
        focusRequested = false;
        Log.w(TAG, "Cluster focus ownership lost");
        if (!enabled
                || destroyed
                || reacquireAttempts >= MAX_REACQUIRE_ATTEMPTS) {
            return;
        }
        reacquireAttempts++;
        mainHandler.removeCallbacks(reacquireFocus);
        mainHandler.postDelayed(reacquireFocus, REACQUIRE_DELAY_MS);
    }

    @Override
    public void onAppFocusOwnershipGranted(int appType) {
        if (appType == CLUSTER_FOCUS_TYPE) {
            focusRequested = true;
            Log.i(TAG, "Cluster focus ownership granted");
        }
    }
}
