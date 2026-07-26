package ipc.fdbus;

import java.util.ArrayList;

/** Thin Java wrapper matching the native FDBus client ABI installed in the car. */
public final class FdbusClient {
    private FdbusClientListener mFdbusListener;
    private long mNativeHandle;

    private native String fdb_bus_name(long handle);

    private native boolean fdb_connect(long handle, String url);

    private native long fdb_create(String name);

    private native void fdb_destroy(long handle);

    private native boolean fdb_disconnect(long handle);

    private native String fdb_endpoint_name(long handle);

    private native boolean fdb_get_event_async(
            long handle, int event, String topic, Object userData, int timeout);

    private native FdbusMessage fdb_get_event_sync(
            long handle, int event, String topic, int timeout);

    private native boolean fdb_invoke_async(
            long handle,
            int code,
            byte[] payload,
            String text,
            Object userData,
            int timeout);

    private native FdbusMessage fdb_invoke_sync(
            long handle, int code, byte[] payload, String text, int timeout);

    private native boolean fdb_log_enabled(long handle, int messageType);

    private native boolean fdb_publish(
            long handle,
            int event,
            String topic,
            byte[] payload,
            String text,
            boolean alwaysUpdate);

    private native boolean fdb_send(
            long handle, int code, byte[] payload, String text);

    private native boolean fdb_subscribe(
            long handle, ArrayList<SubscribeItem> subscriptions);

    private native boolean fdb_unsubscribe(
            long handle, ArrayList<SubscribeItem> subscriptions);

    private void initialize(String name, FdbusClientListener listener) {
        mNativeHandle = fdb_create(name);
        mFdbusListener = listener;
    }

    public FdbusClient(String name, FdbusClientListener listener) {
        initialize(name, listener);
    }

    public FdbusClient(FdbusClientListener listener) {
        initialize(null, listener);
    }

    public FdbusClient(String name) {
        initialize(name, null);
    }

    public FdbusClient() {
        initialize(null, null);
    }

    public void setListener(FdbusClientListener listener) {
        mFdbusListener = listener;
    }

    public void destroy() {
        long handle = mNativeHandle;
        mNativeHandle = 0L;
        if (handle != 0L) {
            fdb_destroy(handle);
        }
    }

    public boolean connect(String url) {
        return fdb_connect(mNativeHandle, url);
    }

    public boolean disconnect() {
        return fdb_disconnect(mNativeHandle);
    }

    public boolean subscribe(ArrayList<SubscribeItem> subscriptions) {
        return fdb_subscribe(mNativeHandle, subscriptions);
    }

    public boolean unsubscribe(ArrayList<SubscribeItem> subscriptions) {
        return fdb_unsubscribe(mNativeHandle, subscriptions);
    }

    public String endpointName() {
        return fdb_endpoint_name(mNativeHandle);
    }

    public String busName() {
        return fdb_bus_name(mNativeHandle);
    }

    public boolean logEnabled(int messageType) {
        return fdb_log_enabled(mNativeHandle, messageType);
    }

    private void callbackOnline(int sessionId) {
        if (mFdbusListener != null) {
            mFdbusListener.onOnline(sessionId);
        }
    }

    private void callbackOffline(int sessionId) {
        if (mFdbusListener != null) {
            mFdbusListener.onOffline(sessionId);
        }
    }

    private void callbackReply(
            int sessionId,
            int code,
            byte[] payload,
            int status,
            Object userData) {
        if (mFdbusListener != null) {
            mFdbusListener.onReply(
                    new FdbusMessage(sessionId, code, payload, userData, status));
        }
    }

    private void callbackGetEvent(
            int sessionId,
            int code,
            String topic,
            byte[] payload,
            int status,
            Object userData) {
        if (mFdbusListener != null) {
            mFdbusListener.onGetEvent(
                    new FdbusMessage(
                            sessionId, code, topic, payload, userData, status));
        }
    }

    private void callbackBroadcast(
            int sessionId, int code, String topic, byte[] payload) {
        if (mFdbusListener != null) {
            FdbusMessage message = new FdbusMessage(sessionId, code, payload);
            message.topic(topic);
            mFdbusListener.onBroadcast(message);
        }
    }
}
