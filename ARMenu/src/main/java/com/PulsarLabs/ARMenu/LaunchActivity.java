package com.PulsarLabs.ARMenu;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;

/**
 * LaunchActivity - decide whether user needs to scan QR (first run) or go straight to menu.
 */
public class LaunchActivity extends Activity {

    private static final String PREFS = "ARMenuPrefs";
    private static final String KEY_SETUP_COMPLETE = "setup_complete";
    private static final String KEY_CACHED_MENU = "cached_menu_path";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        boolean setup = prefs.getBoolean(KEY_SETUP_COMPLETE, false);

        if (setup) {
            // If setup is complete and we have a cached menu, go straight to RestaurantMenuActivity
            String cached = prefs.getString(KEY_CACHED_MENU, null);
            Intent i = new Intent(this, RestaurantMenuActivity.class);
            if (cached != null) {
                i.putExtra(KEY_CACHED_MENU, cached);
            }
            // Ensure Relaunch clears any previous task and becomes the root
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(i);
        } else {
            // First run: launch QR scanner
            Intent i = new Intent(this, QrScannerActivity.class);
            // For scanner, keep normal flow
            startActivity(i);
        }
        finish();
    }

}
