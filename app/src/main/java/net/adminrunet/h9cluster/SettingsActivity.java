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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setStatusBarColor(0xFF071014);
        getWindow().setNavigationBarColor(0xFF071014);
        getWindow().getDecorView().setBackgroundColor(Color.BLACK);
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
                                ClusterLauncher.startOnClusterDisplay(
                                        SettingsActivity.this);
                        settingsView.showSaveResult(launched);
                    }
                });
        setContentView(settingsView);
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

    private void restorePersistedPreview() {
        if (unsavedPreviewActive) {
            unsavedPreviewActive = false;
            ClusterLauncher.startOnClusterDisplay(this);
        }
    }
}
