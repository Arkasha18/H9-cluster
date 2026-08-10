package net.adminrunet.h9cluster;

import net.adminrunet.h9cluster.skins.SkinRegistry;
import net.adminrunet.h9cluster.skins.SkinSettings;
import net.adminrunet.h9cluster.navigation.NavigationAppLauncher;
import net.adminrunet.h9cluster.navigation.NavigationSettings;

import android.app.Activity;
import android.app.ActivityOptions;
import android.content.Context;
import android.content.Intent;
import android.hardware.display.DisplayManager;
import android.util.Log;
import android.view.Display;

/** Starts the cluster activity only on the confirmed instrument display. */
public final class ClusterLauncher {
    private static final String TAG = "GWMClusterLauncher";

    private ClusterLauncher() {
    }

    /** Automatic starts keep whatever visibility the engine stop left behind. */
    public static boolean startOnClusterDisplay(Context context) {
        return launchOnClusterDisplay(context, null, false);
    }

    /** The settings screen asks for the chosen skin even with the engine off. */
    static boolean applyOnClusterDisplay(Context context) {
        return launchOnClusterDisplay(context, null, true);
    }

    static boolean previewOnClusterDisplay(
            Context context,
            SkinSettingsSession.Snapshot draft) {
        return launchOnClusterDisplay(context, draft, true);
    }

    private static boolean launchOnClusterDisplay(
            Context context,
            SkinSettingsSession.Snapshot draft,
            boolean userRequested) {
        String skinId = draft == null
                ? SkinPreferences.getSelectedSkin(context)
                : draft.skinId;
        if (!SkinRegistry.hasRenderer(skinId)) {
            ClusterWindowRegistry.closeAll();
            return true;
        }

        DisplayManager displayManager =
                (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
        boolean clusterDisplayAvailable =
                displayManager != null && hasClusterDisplay(displayManager);
        int currentDisplayId = getCurrentDisplayId(context);
        int targetDisplayId = ClusterDisplayPolicy.resolveTargetDisplay(
                BuildConfig.DEMO_MODE,
                clusterDisplayAvailable,
                currentDisplayId);
        if (targetDisplayId == ClusterDisplayPolicy.NO_DISPLAY) {
            Log.w(TAG, "Display 2 is not ready");
            return false;
        }

        SkinSettings selectedSettings = draft == null
                ? SkinSettingsStore.load(context, skinId)
                : draft.settings;
        if (!BuildConfig.DEMO_MODE
                && SkinRegistry.hidesFactoryCluster(skinId)) {
            String navigationComponent = NavigationSettings.selectedComponent(
                    selectedSettings);
            if (navigationComponent.length() > 0
                    && !NavigationAppLauncher.launch(
                            context,
                            navigationComponent,
                            targetDisplayId)) {
                Log.w(TAG, "Selected background app was not launched");
            }
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
        intent.putExtra(PreviewActivity.EXTRA_USER_REQUESTED, userRequested);
        intent.putExtra(
                PreviewActivity.EXTRA_SINGLE_DISPLAY_FALLBACK,
                ClusterDisplayPolicy.isSingleDisplayFallback(
                        BuildConfig.DEMO_MODE,
                        targetDisplayId));
        intent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        ActivityOptions options = ActivityOptions.makeBasic();
        options.setLaunchDisplayId(targetDisplayId);
        try {
            context.startActivity(intent, options.toBundle());
            return true;
        } catch (RuntimeException error) {
            Log.e(TAG, "Cannot start cluster on Display " + targetDisplayId, error);
            return false;
        }
    }

    private static boolean hasClusterDisplay(DisplayManager displayManager) {
        for (Display display : displayManager.getDisplays()) {
            if (display.getDisplayId() == ClusterDisplayPolicy.CLUSTER_DISPLAY_ID) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("deprecation")
    private static int getCurrentDisplayId(Context context) {
        if (context instanceof Activity) {
            return ((Activity) context)
                    .getWindowManager()
                    .getDefaultDisplay()
                    .getDisplayId();
        }
        return Display.DEFAULT_DISPLAY;
    }
}
