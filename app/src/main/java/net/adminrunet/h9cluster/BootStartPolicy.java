package net.adminrunet.h9cluster;

/** Boot startup rules kept free of Android APIs so they stay testable. */
public final class BootStartPolicy {
    public static final String ACTION_BOOT_COMPLETED =
            "android.intent.action.BOOT_COMPLETED";
    public static final String ACTION_MY_PACKAGE_REPLACED =
            "android.intent.action.MY_PACKAGE_REPLACED";

    private BootStartPolicy() {
    }

    public static boolean shouldStart(String action, boolean autostartSuspended) {
        if (autostartSuspended) {
            return false;
        }
        return ACTION_BOOT_COMPLETED.equals(action)
                || ACTION_MY_PACKAGE_REPLACED.equals(action);
    }
}
