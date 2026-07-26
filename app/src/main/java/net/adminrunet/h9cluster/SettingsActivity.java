package net.adminrunet.h9cluster;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Window;

/** Main-display settings window. Boot startup never opens this activity. */
public final class SettingsActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setStatusBarColor(0xFF071014);
        getWindow().setNavigationBarColor(0xFF071014);
        getWindow().getDecorView().setBackgroundColor(Color.BLACK);
        setContentView(new SettingsView(this));
    }
}
