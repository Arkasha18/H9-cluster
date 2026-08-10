package net.adminrunet.h9cluster;

import net.adminrunet.h9cluster.skins.SkinSettings;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Window;

import java.util.Map;

/** Main-display settings window. Boot startup never opens this activity. */
public final class SettingsActivity extends Activity {
    private SkinSettingsSession session;
    private SettingsView settingsView;
    private boolean unsavedPreviewActive;
    private UpdateManager updateManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setStatusBarColor(0xFF071014);
        getWindow().setNavigationBarColor(0xFF071014);
        getWindow().getDecorView().setBackgroundColor(Color.BLACK);
        AutostartPreferences.setAutostartSuspended(this, false);
        session = new SkinSettingsSession(
                SkinPreferences.getSelectedSkin(this),
                new SkinSettingsSession.Loader() {
                    @Override
                    public SkinSettings load(String skinId) {
                        return SkinSettingsStore.load(
                                SettingsActivity.this,
                                skinId);
                    }
                });
        settingsView = new SettingsView(
                this,
                session,
                new SettingsView.Listener() {
                    @Override
                    public void onDraftChanged(
                            SkinSettingsSession.Snapshot draft) {
                        if (ClusterLauncher.previewOnClusterDisplay(
                                SettingsActivity.this,
                                draft)) {
                            unsavedPreviewActive = true;
                        }
                    }

                    @Override
                    public void onSaveRequested(
                            SkinSettingsSession.Snapshot draft) {
                        SkinPreferences.setSelectedSkin(
                                SettingsActivity.this,
                                draft.skinId);
                        for (Map.Entry<String, SkinSettings> entry
                                : session.drafts().entrySet()) {
                            SkinSettingsStore.save(
                                    SettingsActivity.this,
                                    entry.getKey(),
                                    entry.getValue());
                        }
                        unsavedPreviewActive = false;
                        boolean launched =
                                ClusterLauncher.applyOnClusterDisplay(
                                        SettingsActivity.this);
                        settingsView.showSaveResult(launched);
                    }

                    @Override
                    public void onExitRequested() {
                        exitApplication();
                    }
                });
        setContentView(settingsView);

        boolean updatesEnabled = !BuildConfig.DEBUG && !BuildConfig.DEMO_MODE;
        updateManager = new UpdateManager(
                this,
                BuildConfig.VERSION_NAME,
                updatesEnabled);
        updateManager.checkForUpdates();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (updateManager != null) {
            updateManager.onResume();
        }
    }

    @Override
    public void onBackPressed() {
        restorePersistedPreview();
        super.onBackPressed();
    }

    @Override
    protected void onStop() {
        if (isFinishing()) {
            restorePersistedPreview();
        }
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        if (updateManager != null) {
            updateManager.destroy();
        }
        super.onDestroy();
    }

    private void restorePersistedPreview() {
        if (unsavedPreviewActive) {
            unsavedPreviewActive = false;
            ClusterLauncher.applyOnClusterDisplay(this);
        }
    }

    /**
     * Closes the cluster window on Display 2 first, otherwise it would keep
     * reading vehicle data after the settings window is gone. The unsaved
     * preview is dropped so leaving the screen cannot restart the cluster.
     */
    private void exitApplication() {
        unsavedPreviewActive = false;
        AutostartPreferences.setAutostartSuspended(this, true);
        ClusterWindowRegistry.closeAll();
        finishAndRemoveTask();
    }
}
