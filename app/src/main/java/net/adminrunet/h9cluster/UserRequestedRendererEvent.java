package net.adminrunet.h9cluster;

/** Keeps a manual renderer request one-shot across activity recreation. */
final class UserRequestedRendererEvent {
    private boolean consumed;

    UserRequestedRendererEvent(boolean consumed) {
        this.consumed = consumed;
    }

    boolean consume(boolean userRequested) {
        if (!userRequested || consumed) {
            return false;
        }
        consumed = true;
        return true;
    }

    void onNewIntent() {
        consumed = false;
    }

    boolean isConsumed() {
        return consumed;
    }
}
