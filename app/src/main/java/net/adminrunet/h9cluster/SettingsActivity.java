package net.adminrunet.h9cluster;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Window;

/** Main-display settings window. Boot startup never opens this activity. */
public final class SettingsActivity extends Activity {
    private SettingsSession session;
    private SettingsView settingsView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setStatusBarColor(0xFF071014);
        getWindow().setNavigationBarColor(0xFF071014);
        getWindow().getDecorView().setBackgroundColor(Color.BLACK);
        session = new SettingsSession(ClusterPreferences.load(this));
        settingsView = new SettingsView(
                this,
                session,
                new SettingsView.Listener() {
                    @Override
                    public void onDraftChanged(
                            ClusterPreferences.Snapshot draft) {
                        ClusterLauncher.previewOnClusterDisplay(
                                SettingsActivity.this,
                                draft);
                    }

                    @Override
                    public void onSaveRequested(
                            ClusterPreferences.Snapshot draft) {
                        ClusterPreferences.save(
                                SettingsActivity.this,
                                draft.skin,
                                draft.visibility);
                        ClusterPreferences.Snapshot saved =
                                session.markSaved();
                        boolean launched =
                                ClusterLauncher.previewOnClusterDisplay(
                                        SettingsActivity.this,
                                        saved);
                        settingsView.showSaveResult(launched);
                    }
                });
        setContentView(settingsView);
    }

    @Override
    protected void onStop() {
        ClusterLauncher.restoreOnClusterDisplay(
                this,
                session.snapshotToRestoreOnClose());
        super.onStop();
    }
}
