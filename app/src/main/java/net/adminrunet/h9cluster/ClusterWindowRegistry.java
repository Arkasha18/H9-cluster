package net.adminrunet.h9cluster;

import java.util.ArrayList;
import java.util.List;

/**
 * Tracks the cluster windows opened by this process so the settings screen can
 * close the one living on Display 2, which it cannot reach with an intent.
 */
public final class ClusterWindowRegistry {
    public interface Window {
        void closeClusterWindow();
    }

    private static final List<Window> WINDOWS = new ArrayList<>();

    private ClusterWindowRegistry() {
    }

    public static void register(Window window) {
        if (window == null) {
            return;
        }
        synchronized (WINDOWS) {
            if (!WINDOWS.contains(window)) {
                WINDOWS.add(window);
            }
        }
    }

    public static void unregister(Window window) {
        synchronized (WINDOWS) {
            WINDOWS.remove(window);
        }
    }

    /** Returns true when at least one window was open and got the close call. */
    public static boolean closeAll() {
        List<Window> pending;
        synchronized (WINDOWS) {
            if (WINDOWS.isEmpty()) {
                return false;
            }
            pending = new ArrayList<>(WINDOWS);
            WINDOWS.clear();
        }
        for (Window window : pending) {
            window.closeClusterWindow();
        }
        return true;
    }

    public static boolean hasOpenWindow() {
        synchronized (WINDOWS) {
            return !WINDOWS.isEmpty();
        }
    }
}
