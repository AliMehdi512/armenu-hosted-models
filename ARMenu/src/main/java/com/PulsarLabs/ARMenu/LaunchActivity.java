package com.PulsarLabs.ARMenu;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

public class LaunchActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Start the in-app QR scanner immediately on app start
        Intent i = new Intent(this, QrScannerActivity.class);
        startActivity(i);
        finish();
    }

}
