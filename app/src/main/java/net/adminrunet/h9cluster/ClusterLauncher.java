package net.adminrunet.h9cluster;

import android.app.ActivityOptions;
import android.content.Context;
import android.content.Intent;
import android.hardware.display.DisplayManager;
import android.util.Log;
import android.view.Display;

/** Starts the cluster activity only on the confirmed instrument display. */
public final class ClusterLauncher {
    public static final int CLUSTER_DISPLAY_ID = 2;
    private static final String TAG = "GWMClusterLauncher";

    private ClusterLauncher() {
    }

    public static boolean startOnClusterDisplay(Context context) {
        return launchOnClusterDisplay(context, null);
    }

    static boolean previewOnClusterDisplay(
            Context context,
            SkinSettingsSession.Snapshot draft) {
        return launchOnClusterDisplay(context, draft);
    }

    private static boolean launchOnClusterDisplay(
            Context context,
            SkinSettingsSession.Snapshot draft) {
        DisplayManager displayManager =
                (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
        if (displayManager == null || !hasClusterDisplay(displayManager)) {
            Log.w(TAG, "Display 2 is not ready");
            return false;
        }

        Intent intent = new Intent(context, PreviewActivity.class);
        if (draft == null) {
            intent.putExtra(PreviewActivity.EXTRA_RELOAD_SKIN, true);
        } else {
            intent.putExtra(PreviewActivity.EXTRA_HAS_DRAFT, true);
            intent.putExtra(PreviewActivity.EXTRA_DRAFT_SKIN, draft.skinId);
            intent.putExtra(
                    PreviewActivity.EXTRA_DRAFT_SETTINGS,
                    SkinSettingsTransport.toBundle(draft.settings));
        }
        intent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        ActivityOptions options = ActivityOptions.makeBasic();
        options.setLaunchDisplayId(CLUSTER_DISPLAY_ID);
        try {
            context.startActivity(intent, options.toBundle());
            return true;
        } catch (RuntimeException error) {
            Log.e(TAG, "Cannot start cluster on Display 2", error);
            return false;
        }
    }

    private static boolean hasClusterDisplay(DisplayManager displayManager) {
        for (Display display : displayManager.getDisplays()) {
            if (display.getDisplayId() == CLUSTER_DISPLAY_ID) {
                return true;
            }
        }
        return false;
    }
}
