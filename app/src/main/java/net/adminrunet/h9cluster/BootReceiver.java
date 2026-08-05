package net.adminrunet.h9cluster;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

/** Retries briefly because the secondary display can appear after BOOT_COMPLETED. */
public final class BootReceiver extends BroadcastReceiver {
    private static final int MAX_ATTEMPTS = 6;
    private static final long RETRY_DELAY_MS = 1500L;

    @Override
    public void onReceive(final Context context, final Intent intent) {
        final String action = intent == null ? null : intent.getAction();
        final Context applicationContext = context.getApplicationContext();
        if (!BootStartPolicy.shouldStart(
                action,
                AutostartPreferences.isAutostartSuspended(applicationContext))) {
            return;
        }

        final PendingResult pendingResult = goAsync();
        final Handler handler = new Handler(Looper.getMainLooper());

        handler.post(new Runnable() {
            private int attempt;

            @Override
            public void run() {
                attempt++;
                if (ClusterLauncher.startOnClusterDisplay(applicationContext)
                        || attempt >= MAX_ATTEMPTS) {
                    pendingResult.finish();
                    return;
                }
                handler.postDelayed(this, RETRY_DELAY_MS);
            }
        });
    }
}
