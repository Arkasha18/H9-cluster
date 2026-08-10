package net.adminrunet.h9cluster;

import android.content.Context;
import android.util.Log;

/** Loads the automotive controller only in production builds. */
final class FactoryClusterControllerFactory {
    private static final String TAG = "H9ClusterFocus";
    private static final String IMPLEMENTATION_CLASS =
            "net.adminrunet.h9cluster.ClusterFocusController";

    private FactoryClusterControllerFactory() {
    }

    static FactoryClusterController create(Context context) {
        try {
            Class<?> type = Class.forName(IMPLEMENTATION_CLASS);
            return (FactoryClusterController) type
                    .getDeclaredConstructor(Context.class)
                    .newInstance(context);
        } catch (ReflectiveOperationException | LinkageError error) {
            Log.e(TAG, "Automotive cluster focus is unavailable", error);
            return null;
        }
    }
}
