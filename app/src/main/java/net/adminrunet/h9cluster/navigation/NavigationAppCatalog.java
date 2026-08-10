package net.adminrunet.h9cluster.navigation;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Enumerates user-launchable activities without requiring package visibility APIs. */
final class NavigationAppCatalog {
    private NavigationAppCatalog() {
    }

    static List<NavigationAppOption> load(
            Context context,
            String selectedComponent) {
        PackageManager packageManager = context.getPackageManager();
        Intent launcherIntent = new Intent(Intent.ACTION_MAIN);
        launcherIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> resolved = packageManager.queryIntentActivities(
                launcherIntent,
                0);

        List<NavigationAppOption> applications = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        String ownPackage = context.getPackageName();
        for (ResolveInfo info : resolved) {
            ActivityInfo activity = info.activityInfo;
            if (activity == null
                    || !activity.enabled
                    || !activity.exported
                    || ownPackage.equals(activity.packageName)) {
                continue;
            }
            ComponentName component = new ComponentName(
                    activity.packageName,
                    activity.name);
            String flattened = component.flattenToString();
            if (!seen.add(flattened)) {
                continue;
            }
            CharSequence label = info.loadLabel(packageManager);
            String readableLabel = label == null
                    ? activity.packageName
                    : label.toString().trim();
            applications.add(new NavigationAppOption(
                    flattened,
                    readableLabel + " — " + activity.packageName));
        }

        final Collator collator = Collator.getInstance(new Locale("ru", "RU"));
        Collections.sort(
                applications,
                new Comparator<NavigationAppOption>() {
                    @Override
                    public int compare(
                            NavigationAppOption left,
                            NavigationAppOption right) {
                        return collator.compare(left.title, right.title);
                    }
                });

        List<NavigationAppOption> options = new ArrayList<>();
        options.add(new NavigationAppOption("", "Не запускать приложение"));
        options.addAll(applications);

        String normalizedSelection = NavigationSettings.normalizeComponent(
                selectedComponent);
        if (normalizedSelection.length() > 0 && !seen.contains(normalizedSelection)) {
            options.add(new NavigationAppOption(
                    normalizedSelection,
                    "Недоступно — " + normalizedSelection));
        }
        return options;
    }
}
