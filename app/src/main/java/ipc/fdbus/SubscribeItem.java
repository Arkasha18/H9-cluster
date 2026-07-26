package ipc.fdbus;

/** Event or event-group subscription descriptor consumed directly by JNI. */
public final class SubscribeItem {
    private int mCode;
    private String mTopic;

    public SubscribeItem(int code, String topic) {
        mCode = code;
        mTopic = topic;
    }

    public static SubscribeItem newEvent(int code, String topic) {
        return new SubscribeItem(code, topic);
    }

    public static SubscribeItem newEvent(int code) {
        return new SubscribeItem(code, null);
    }

    public static SubscribeItem newGroup(int group, String topic) {
        return new SubscribeItem(((group & 0xff) << 24) | 0x00ffffff, topic);
    }

    public static SubscribeItem newGroup(int group) {
        return newGroup(group, null);
    }

    public int code() {
        return mCode;
    }

    public String topic() {
        return mTopic;
    }
}
