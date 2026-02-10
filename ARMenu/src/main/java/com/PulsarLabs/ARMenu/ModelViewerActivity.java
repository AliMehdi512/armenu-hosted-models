package com.PulsarLabs.ARMenu;

import android.app.Activity;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

public class ModelViewerActivity extends Activity {

    private WebView mWebView;
    private ModelCacheManager cacheManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        cacheManager = new ModelCacheManager(this);

        FrameLayout root = new FrameLayout(this);

        mWebView = new WebView(this);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, 
            FrameLayout.LayoutParams.MATCH_PARENT
        );
        mWebView.setLayoutParams(lp);
        mWebView.setBackgroundColor(android.graphics.Color.BLACK);

        root.addView(mWebView);
        setContentView(root);

        WebSettings ws = mWebView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setAllowFileAccess(true);
        ws.setAllowContentAccess(true);
        ws.setDomStorageEnabled(true);

        mWebView.setWebViewClient(new WebViewClient());

        Uri data = getIntent().getData();
        String modelUrl;

        if (data != null) {
            String scheme = data.getScheme();
            if (scheme != null && (scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
                modelUrl = cacheManager.getCachedModelPath(data.toString());
            } else {
                String modelAsset = "models/fried_chicken.glb";
                String path = data.getPath();
                if (path != null && path.length() > 1) {
                    String item = path.substring(1);
                    if (!item.isEmpty()) {
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
}
