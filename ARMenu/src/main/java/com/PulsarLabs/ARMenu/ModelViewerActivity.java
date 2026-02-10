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

public class ModelViewerActivity extends Activity implements TextureView.SurfaceTextureListener {

    private WebView mWebView;
    private TextureView mTextureView;
    private CameraService cameraService;
    private boolean bound = false;
    private Surface pendingSurface = null;
    private ModelCacheManager cacheManager;

    private ServiceConnection svcConn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            try {
                CameraService.LocalBinder lb = (CameraService.LocalBinder) service;
                cameraService = lb.getService();
                bound = true;
                if (pendingSurface != null) {
                    cameraService.setPreviewSurface(pendingSurface);
                } else if (mTextureView != null && mTextureView.isAvailable()) {
                    SurfaceTexture st = mTextureView.getSurfaceTexture();
                    if (st != null) {
                        pendingSurface = new Surface(st);
                        cameraService.setPreviewSurface(pendingSurface);
                    }
                }
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

        // Camera preview background
        mTextureView = new TextureView(this);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, 
            FrameLayout.LayoutParams.MATCH_PARENT
        );
        mTextureView.setLayoutParams(lp);
        mTextureView.setSurfaceTextureListener(this);

        // WebView for 3D model (transparent so camera shows behind)
        mWebView = new WebView(this);
        mWebView.setLayoutParams(lp);
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

    @Override
    protected void onStart() {
        super.onStart();
        try {
            if (bound) {
                unbindService(svcConn);
                bound = false;
            }
        } catch (Exception e) { }
        try {
            startService(new Intent(this, CameraService.class));
            bindService(new Intent(this, CameraService.class), svcConn, Context.BIND_AUTO_CREATE);
        } catch (Exception e) { }
    }

    @Override
    protected void onResume() {
        super.onResume();
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
                try {
                    if (cameraService != null) {
                        cameraService.setPreviewSurface(null);
                    }
                } catch (Exception e) { }
                unbindService(svcConn);
                bound = false;
            }
        } catch (Exception e) { }
        super.onDestroy();
    }

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
