package ipc.fdbus;

/** Raw FDBus message object populated by libfdbus-jni.so. */
public final class FdbusMessage {
    private int mMsgCode;
    private long mNativeHandle;
    private byte[] mPayload;
    private int mSid;
    private int mStatus;
    private String mTopic;
    private Object mUserData;

    private native boolean fdb_broadcast(
            long handle, int code, String topic, byte[] payload, String text);

    private native void fdb_destroy(long handle);

    private native boolean fdb_log_enabled(long handle);

    private native boolean fdb_reply(long handle, byte[] payload, String text);

    private void initialize(
            long handle,
            int sessionId,
            int code,
            String topic,
            byte[] payload,
            Object userData,
            int status) {
        mNativeHandle = handle;
        mSid = sessionId;
        mMsgCode = code;
        mTopic = topic;
        mPayload = payload;
        mUserData = userData;
        mStatus = status;
    }

    public FdbusMessage(
            int sessionId, int code, byte[] payload, Object userData, int status) {
        initialize(0L, sessionId, code, null, payload, userData, status);
    }

    public FdbusMessage(
            int sessionId,
            int code,
            String topic,
            byte[] payload,
            Object userData,
            int status) {
        initialize(0L, sessionId, code, topic, payload, userData, status);
    }

    public FdbusMessage(int sessionId, int code, byte[] payload, Object userData) {
        initialize(0L, sessionId, code, null, payload, userData, 0);
    }

    public FdbusMessage(int sessionId, int code, byte[] payload, int status) {
        initialize(0L, sessionId, code, null, payload, null, status);
    }

    public FdbusMessage(
            int sessionId, int code, String topic, byte[] payload, int status) {
        initialize(0L, sessionId, code, topic, payload, null, status);
    }

    public FdbusMessage(int sessionId, int code, byte[] payload) {
        initialize(0L, sessionId, code, null, payload, null, 0);
    }

    public FdbusMessage(long handle, int sessionId, int code, byte[] payload) {
        initialize(handle, sessionId, code, null, payload, null, 0);
    }

    public byte[] byteArray() {
        return mPayload;
    }

    public int code() {
        return mMsgCode;
    }

    public int sid() {
        return mSid;
    }

    public int returnValue() {
        return mStatus;
    }

    public Object userData() {
        return mUserData;
    }

    public String topic() {
        return mTopic;
    }

    public void topic(String topic) {
        mTopic = topic;
    }

    public boolean reply(Object message) {
        FdbusMsgBuilder builder = Fdbus.encodeMessage(message, logEnabled());
        if (builder == null) {
            return false;
        }
        boolean result =
                fdb_reply(mNativeHandle, builder.toBuffer(), builder.toString());
        destroy();
        return result;
    }

    public boolean broadcast(int code, String topic, Object message) {
        FdbusMsgBuilder builder = Fdbus.encodeMessage(message, logEnabled());
        if (builder == null) {
            return false;
        }
        boolean result = fdb_broadcast(
                mNativeHandle, code, topic, builder.toBuffer(), builder.toString());
        destroy();
        return result;
    }

    public boolean logEnabled() {
        return fdb_log_enabled(mNativeHandle);
    }

    private void destroy() {
        long handle = mNativeHandle;
        mNativeHandle = 0L;
        if (handle != 0L) {
            fdb_destroy(handle);
        }
    }
}
