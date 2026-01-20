package com.PulsarLabs.ARMenu;

import android.app.Activity;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class ModelViewerActivity extends Activity {

    private WebView mWebView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mWebView = new WebView(this);
        setContentView(mWebView);

        WebSettings ws = mWebView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setAllowFileAccess(true);
        ws.setAllowContentAccess(true);

        mWebView.setWebViewClient(new WebViewClient());

        // Expecting deep link like: armenu://show/fried_chicken
        Uri data = getIntent().getData();
        String modelUrl;

        if (data != null) {
            String scheme = data.getScheme();
            if (scheme != null && (scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
                // remote URL (e.g., raw.githubusercontent.com or github.io)
                modelUrl = data.toString();
            } else {
                // Expecting deep link like: armenu://show/fried_chicken
                String modelAsset = "models/fried_chicken.glb"; // default
                // path: /fried_chicken
                String path = data.getPath();
                if (path != null && path.length() > 1) {
                    String item = path.substring(1); // remove leading '/'
                    if (!item.isEmpty()) {
                        // map item to asset filename - basic sanitation
                        item = item.replaceAll("[^a-zA-Z0-9_\\-]", "_");
                        modelAsset = "models/" + item + ".glb";
                    }
                }
                modelUrl = "file:///android_asset/" + modelAsset;
            }
        } else {
            modelUrl = "file:///android_asset/models/fried_chicken.glb";
        }
        String pageUrl = "file:///android_asset/model_viewer.html?model=" + Uri.encode(modelUrl);

        mWebView.loadUrl(pageUrl);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Stop the CameraService when the model viewer is closed
        try {
            stopService(new android.content.Intent(this, CameraService.class));
        } catch (Exception e) { }
    }
}
