package ipc.fdbus;

/**
 * Initialises the Java bridge bundled with the vehicle's FDBus runtime.
 *
 * The four class arguments and their field/method names are part of the native
 * ABI used by libfdbus-jni.so.
 */
public final class Fdbus {
    private native void fdb_init(
            Class<?> serverClass,
            Class<?> clientClass,
            Class<?> subscribeItemClass,
            Class<?> messageClass);

    private static native void fdb_log_trace(String tag, int level, String message);

    public Fdbus() {
        System.loadLibrary("fdbus-jni");
        fdb_init(
                FdbusServer.class,
                FdbusClient.class,
                SubscribeItem.class,
                FdbusMessage.class);
    }

    static FdbusMsgBuilder encodeMessage(Object message, boolean enableLog) {
        if (message == null || message instanceof byte[]) {
            return new FdbusMsgBuilder((byte[]) message, null);
        }
        if (message instanceof FdbusMsgBuilder) {
            FdbusMsgBuilder builder = (FdbusMsgBuilder) message;
            return builder.build(enableLog) ? builder : null;
        }
        return null;
    }

    public static void logError(String tag, String message) {
        fdb_log_trace(tag, 4, message);
    }
}
