package com.PulsarLabs.ARMenu;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.SurfaceTexture;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.view.Surface;
import android.view.TextureView;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

public class ModelViewerActivity extends Activity implements TextureView.SurfaceTextureListener, QrTracker.QrTrackingListener {

    private WebView mWebView;
    private TextureView mTextureView;
    private CameraService cameraService;
    private boolean bound = false;
    private Surface pendingSurface = null;
    private ModelCacheManager cacheManager;
    private boolean qrTrackingActive = false;

    private ServiceConnection svcConn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            try {
                CameraService.LocalBinder lb = (CameraService.LocalBinder) service;
                cameraService = lb.getService();
                bound = true;
                // Set pending surface if TextureView was already available
                if (pendingSurface != null) {
                    cameraService.setPreviewSurface(pendingSurface);
                } else if (mTextureView != null && mTextureView.isAvailable()) {
                    SurfaceTexture st = mTextureView.getSurfaceTexture();
                    if (st != null) {
                        pendingSurface = new Surface(st);
                        cameraService.setPreviewSurface(pendingSurface);
                    }
                }
                // Enable QR tracking
                cameraService.enableQrTracking(ModelViewerActivity.this);
                qrTrackingActive = true;
            } catch (Exception e) { }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            cameraService = null;
            bound = false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        cacheManager = new ModelCacheManager(this);

        FrameLayout root = new FrameLayout(this);

        mTextureView = new TextureView(this);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
        mTextureView.setLayoutParams(lp);
        mTextureView.setSurfaceTextureListener(this);

        mWebView = new WebView(this);
        mWebView.setLayoutParams(lp);
        // Make WebView background transparent so the TextureView camera preview shows through
        try {
            mWebView.setBackgroundColor(0);
            mWebView.setBackgroundResource(android.R.color.transparent);
            mWebView.setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null);
        } catch (Exception e) { }

        root.addView(mTextureView);
        root.addView(mWebView);

        setContentView(root);

        WebSettings ws = mWebView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setAllowFileAccess(true);
        ws.setAllowContentAccess(true);
        ws.setDomStorageEnabled(true);

        mWebView.setWebViewClient(new WebViewClient());

        // Expecting deep link like: armenu://show/fried_chicken
        Uri data = getIntent().getData();
        String modelUrl;

        if (data != null) {
            String scheme = data.getScheme();
            if (scheme != null && (scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
                // remote URL (e.g., raw.githubusercontent.com or github.io)
                // Check cache first, download and cache if needed
                modelUrl = cacheManager.getCachedModelPath(data.toString());
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
    protected void onStart() {
        super.onStart();
        // Unbind and clear any previous connection
        try {
            if (bound) {
                unbindService(svcConn);
                bound = false;
            }
        } catch (Exception e) { }
        // Ensure CameraService is running fresh and bind to it
        try {
            startService(new Intent(this, CameraService.class));
            bindService(new Intent(this, CameraService.class), svcConn, Context.BIND_AUTO_CREATE);
        } catch (Exception e) { }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Ensure preview surface is set when resuming
        if (bound && cameraService != null && mTextureView != null && mTextureView.isAvailable()) {
            try {
                SurfaceTexture st = mTextureView.getSurfaceTexture();
                if (st != null) {
                    if (pendingSurface == null) {
                        pendingSurface = new Surface(st);
                    }
                    cameraService.setPreviewSurface(pendingSurface);
                }
            } catch (Exception e) { }
        }
    }

    @Override
    protected void onDestroy() {
        try {
            if (bound) {
                // Disable QR tracking and clear preview surface
                try {
                    if (cameraService != null) {
                        cameraService.disableQrTracking();
                        cameraService.setPreviewSurface(null);
                    }
                } catch (Exception e) { }
                unbindService(svcConn);
                bound = false;
            }
        } catch (Exception e) { }
        qrTrackingActive = false;
        super.onDestroy();
    }

    // QrTracker.QrTrackingListener implementation
    @Override
    public void onQrPositionUpdated(final float centerX, final float centerY, final float size, final boolean detected) {
        if (mWebView == null) return;
        
        final float screenX = centerX;
        final float screenY = centerY;
        final float scale = Math.max(0.5f, Math.min(2.0f, size * 3.0f));
        
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    String js = String.format(
                        java.util.Locale.US,
                        "if(typeof updateModelPosition==='function'){updateModelPosition(%f,%f,%f,%s);}",
                        screenX, screenY, scale, detected ? "true" : "false"
                    );
                    if (android.os.Build.VERSION.SDK_INT >= 19) {
                        mWebView.evaluateJavascript(js, null);
                    } else {
                        mWebView.loadUrl("javascript:" + js);
                    }
                } catch (Exception e) { }
            }
        });
    }

    // TextureView.SurfaceTextureListener
    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        try {
            pendingSurface = new Surface(surface);
            if (cameraService != null && bound) {
                cameraService.setPreviewSurface(pendingSurface);
            }
        } catch (Exception e) { }
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) { }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) { }
}
