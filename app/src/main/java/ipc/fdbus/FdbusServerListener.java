package ipc.fdbus;

import java.util.ArrayList;

public interface FdbusServerListener {
    void onInvoke(FdbusMessage message);

    void onOffline(int sessionId, boolean lastClient);

    void onOnline(int sessionId, boolean firstClient);

    void onSubscribe(
            FdbusMessage message, ArrayList<SubscribeItem> subscriptions);
}
