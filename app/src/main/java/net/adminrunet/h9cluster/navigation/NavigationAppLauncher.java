package net.adminrunet.h9cluster.navigation;

import android.app.ActivityOptions;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.util.Log;

/** Opens the selected launchable activity on the instrument display. */
public final class NavigationAppLauncher {
    private static final String TAG = "H9NavigationLauncher";

    private NavigationAppLauncher() {
    }

    public static boolean launch(
            Context context,
            String selectedComponent,
            int displayId) {
        String normalized = NavigationSettings.normalizeComponent(
                selectedComponent);
        if (normalized.length() == 0) {
            return true;
        }
        ComponentName component = ComponentName.unflattenFromString(normalized);
        if (component == null || context.getPackageName().equals(
                component.getPackageName())) {
            Log.w(TAG, "Ignoring invalid or recursive component " + normalized);
            return false;
        }

        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        intent.setComponent(component);
        intent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);

        ResolveInfo resolved = context.getPackageManager().resolveActivity(
                intent,
                0);
        ActivityInfo activity = resolved == null ? null : resolved.activityInfo;
        if (activity == null || !activity.enabled || !activity.exported) {
            Log.w(TAG, "Selected activity is not launchable: " + normalized);
            return false;
        }

        ActivityOptions options = ActivityOptions.makeBasic();
        options.setLaunchDisplayId(displayId);
        Bundle launchOptions = options.toBundle();
        try {
            context.startActivity(intent, launchOptions);
            Log.i(TAG, "Started " + normalized + " on Display " + displayId);
            return true;
        } catch (RuntimeException error) {
            Log.e(TAG, "Cannot start " + normalized
                    + " on Display " + displayId, error);
            return false;
        }
    }
}
