package com.PulsarLabs.ARMenu;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
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

    private String normalizeRestaurantUrl(String url) {
        try {
            if (url.endsWith("/menu.json")) {
                url = url.substring(0, url.length() - "/menu.json".length());
            }
            // Convert GitHub tree URLs to raw content URLs
            if (url.startsWith("https://github.com/")) {
                String[] parts = url.split("/");
                if (parts.length > 7 && "tree".equals(parts[5])) {
                    String owner = parts[3];
                    String repo = parts[4];
                    String branch = parts[6];
                    StringBuilder rest = new StringBuilder();
                    for (int i = 7; i < parts.length; i++) {
                        rest.append("/").append(parts[i]);
                    }
                    return "https://raw.githubusercontent.com/" + owner + "/" + repo + "/" + branch + rest.toString();
                }
            }
        } catch (Exception ignored) { }
        return url;
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
                        try {
                            if (contents.contains("/restaurants/") || contents.endsWith("/menu.json") || contents.contains("/menu.json")) {
                                // Save the scanned restaurant base URL so we can skip scanning on next app start
                                String normalized = normalizeRestaurantUrl(contents);
                                try {
                                    SharedPreferences prefs = getSharedPreferences("ARMenuPrefs", Context.MODE_PRIVATE);
                                    prefs.edit().putString("restaurant_base_url", normalized).apply();
                                } catch (Exception ignored) { }

                                // Try to fetch menu.json immediately and save it to internal storage so
                                // the app can reliably launch offline on subsequent runs.
                                String menuJsonUrl = normalized + (normalized.endsWith("/") ? "" : "/") + "menu.json";
                                try {
                                    java.net.URL urlObj = new java.net.URL(menuJsonUrl);
                                    java.net.URLConnection conn = urlObj.openConnection();
                                    conn.setConnectTimeout(4000);
                                    conn.setReadTimeout(4000);
                                    java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(conn.getInputStream(), "UTF-8"));
                                    StringBuilder json = new StringBuilder();
                                    String line;
                                    while ((line = reader.readLine()) != null) json.append(line);
                                    reader.close();

                                    // write to internal files dir
                                    try {
                                        String fileName = "menu_" + Integer.toHexString(normalized.hashCode()) + ".json";
                                        java.io.File outF = new java.io.File(getFilesDir(), fileName);
                                        try (java.io.FileOutputStream fos = new java.io.FileOutputStream(outF)) {
                                            fos.write(json.toString().getBytes("UTF-8"));
                                            fos.flush();
                                        }
                                        try {
                                            SharedPreferences prefs = getSharedPreferences("ARMenuPrefs", Context.MODE_PRIVATE);
                                            prefs.edit().putString("cached_menu_path", outF.getAbsolutePath()).putBoolean("setup_complete", true).apply();
                                        } catch (Exception ignored) { }
                                    } catch (Exception e) {
                                        // ignore write failures
                                    }
                                } catch (Exception e) {
                                    // If fetching fails, continue - RestaurantMenuActivity will attempt to fetch itself
                                }

                                Intent i = new Intent(this, RestaurantMenuActivity.class);
                                i.setData(Uri.parse(normalized));
                                startActivity(i);
                            } else {
                                Intent i = new Intent(this, ModelViewerActivity.class);
                                i.setData(Uri.parse(contents));
                                startActivity(i);
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                            Toast.makeText(this, "Error: " + e.getMessage() + "\nURL: " + contents, Toast.LENGTH_LONG).show();
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
