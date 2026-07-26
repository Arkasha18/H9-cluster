package ipc.fdbus;

import java.util.ArrayList;

/**
 * Server half of the JNI ABI. The probe does not create a server, but JNI
 * registers this class while the FDBus runtime is initialised.
 */
public final class FdbusServer {
    private FdbusServerListener mFdbusListener;
    private long mNativeHandle;

    private native boolean fdb_bind(long handle, String url);

    private native boolean fdb_broadcast(
            long handle, int code, String topic, byte[] payload, String text);

    private native String fdb_bus_name(long handle);

    private native long fdb_create(String name);

    private native void fdb_destroy(long handle);

    private native void fdb_enable_event_cache(long handle, boolean enabled);

    private native String fdb_endpoint_name(long handle);

    private native void fdb_init_event_cache(
            long handle,
            int event,
            String topic,
            byte[] payload,
            boolean alwaysUpdate);

    private native boolean fdb_log_enabled(long handle, int messageType);

    private native boolean fdb_unbind(long handle);

    public FdbusServer() {
        mNativeHandle = fdb_create(null);
    }

    private void callbackOnline(int sessionId, boolean firstClient) {
        if (mFdbusListener != null) {
            mFdbusListener.onOnline(sessionId, firstClient);
        }
    }

    private void callbackOffline(int sessionId, boolean lastClient) {
        if (mFdbusListener != null) {
            mFdbusListener.onOffline(sessionId, lastClient);
        }
    }

    private void callbackInvoke(
            int sessionId, int code, byte[] payload, long messageHandle) {
        if (mFdbusListener != null) {
            mFdbusListener.onInvoke(
                    new FdbusMessage(messageHandle, sessionId, code, payload));
        }
    }

    private void callbackSubscribe(
            int sessionId,
            long messageHandle,
            ArrayList<SubscribeItem> subscriptions) {
        if (mFdbusListener != null) {
            mFdbusListener.onSubscribe(
                    new FdbusMessage(messageHandle, sessionId, 0, (byte[]) null),
                    subscriptions);
        }
    }
}
