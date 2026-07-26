package ipc.fdbus;

public interface FdbusClientListener {
    void onBroadcast(FdbusMessage message);

    void onGetEvent(FdbusMessage message);

    void onOffline(int sessionId);

    void onOnline(int sessionId);

    void onReply(FdbusMessage message);
}
