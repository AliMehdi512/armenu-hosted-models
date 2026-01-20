package com.PulsarLabs.ARMenu;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.Manifest;
import android.content.pm.PackageManager;
import android.widget.Toast;

import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

/** QrScannerActivity - launches a QR scanner and opens scanned deep links */
public class QrScannerActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Request camera permission at runtime if needed (Android 6+)
        if (android.os.Build.VERSION.SDK_INT >= 23) {
            if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.CAMERA}, 1001);
                return;
            }
        }
        startScan();
    }

    private void startScan() {
        IntentIntegrator integrator = new IntentIntegrator(this);
        integrator.setDesiredBarcodeFormats(java.util.Arrays.asList("QR_CODE"));
        integrator.setPrompt("Scan a QR code to open item");
        integrator.setBeepEnabled(true);
        integrator.setOrientationLocked(false);
        integrator.initiateScan();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        IntentResult result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
        if (result != null) {
            String contents = result.getContents();
                if (contents != null) {
                    // If the QR contains our app deep link, open it
                    if (contents.startsWith("armenu://")) {
                        try {
                            Intent view = new Intent(Intent.ACTION_VIEW, Uri.parse(contents));
                            view.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(view);
                        } catch (Exception e) {
                            Toast.makeText(this, "Unable to open link", Toast.LENGTH_SHORT).show();
                        }
                    } else if (contents.startsWith("http://") || contents.startsWith("https://")) {
                        // If it's an http(s) URL (e.g., raw.githubusercontent or github.io), open it in-app via ModelViewerActivity
                        try {
                            Intent i = new Intent(this, ModelViewerActivity.class);
                            i.setData(Uri.parse(contents));
                            startActivity(i);
                        } catch (Exception e) {
                            Toast.makeText(this, "Unable to open remote model", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        // If it's not a supported scheme, just show the raw content
                        Toast.makeText(this, "Scanned: " + contents, Toast.LENGTH_LONG).show();
                    }
            } else {
                Toast.makeText(this, "Scan cancelled", Toast.LENGTH_SHORT).show();
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
        finish();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == 1001) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startScan();
            } else {
                Toast.makeText(this, "Camera permission required to scan QR codes", Toast.LENGTH_LONG).show();
                finish();
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

}
