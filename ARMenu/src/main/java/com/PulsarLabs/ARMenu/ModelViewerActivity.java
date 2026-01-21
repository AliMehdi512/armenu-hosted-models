package com.PulsarLabs.ARMenu;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.SurfaceTexture;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.view.Surface;
import android.view.TextureView;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class ModelViewerActivity extends Activity implements TextureView.SurfaceTextureListener {

    private WebView mWebView;
    private TextureView mTextureView;
    private CameraService cameraService;
    private boolean bound = false;
    private CameraService.QrListener qrListenerRef;

    private ServiceConnection svcConn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            try {
                CameraService.LocalBinder lb = (CameraService.LocalBinder) service;
                cameraService = lb.getService();
                bound = true;
                if (mTextureView != null && mTextureView.isAvailable()) {
                    SurfaceTexture st = mTextureView.getSurfaceTexture();
                    if (st != null) {
                        Surface s = new Surface(st);
                        cameraService.setPreviewSurface(s);
                    }
                }
                    try {
                        qrListenerRef = new CameraService.QrListener() {
                            @Override
                            public void onQrFound(final float nx, final float ny, final String text) {
                                runOnUiThread(new Runnable() {
                                    @Override public void run() {
                                        try {
                                            String js = String.format("window.onQrFound(%f,%f)", nx, ny);
                                            mWebView.evaluateJavascript(js, null);
                                        } catch (Exception e) { }
                                    }
                                });
                            }
                        };
                        cameraService.registerQrListener(qrListenerRef);
                    } catch (Exception e) { }
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
        final String modelUrl;

        if (data != null) {
            String scheme = data.getScheme();
            if (scheme != null && (scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
                // remote URL - cache it locally first
                final String remoteUrl = data.toString();
                cacheAndLoadModel(remoteUrl);
                return;
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
        // ensure CameraService is running and bind to it to receive preview surface
        try {
            startService(new Intent(this, CameraService.class));
            bindService(new Intent(this, CameraService.class), svcConn, Context.BIND_AUTO_CREATE);
        } catch (Exception e) { }
    }

    @Override
    protected void onDestroy() {
        try {
            if (bound) {
                // clear preview surface so service can continue running without our surface
                try {
                    if (cameraService != null) {
                        cameraService.setPreviewSurface(null);
                        if (qrListenerRef != null) cameraService.unregisterQrListener(qrListenerRef);
                    }
                } catch (Exception e) { }
                unbindService(svcConn);
                bound = false;
            }
        } catch (Exception e) { }
        super.onDestroy();
    }

    // TextureView.SurfaceTextureListener
    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        if (cameraService != null) {
            try {
                Surface s = new Surface(surface);
                cameraService.setPreviewSurface(s);
            } catch (Exception e) { }
        }
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) { }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) { }

    private void cacheAndLoadModel(final String remoteUrl) {
        // Generate cache filename from URL hash
        final String cacheFilename = "model_" + Math.abs(remoteUrl.hashCode()) + ".glb";
        final File cacheDir = new File(getCacheDir(), "models");
        if (!cacheDir.exists()) cacheDir.mkdirs();
        final File cachedFile = new File(cacheDir, cacheFilename);

        // If already cached, use it immediately
        if (cachedFile.exists() && cachedFile.length() > 0) {
            loadModelFromFile(cachedFile);
            return;
        }

        // Otherwise download and cache
        new AsyncTask<Void, Void, File>() {
            @Override
            protected File doInBackground(Void... voids) {
                try {
                    URL url = new URL(remoteUrl);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setConnectTimeout(10000);
                    conn.setReadTimeout(30000);
                    conn.connect();

                    if (conn.getResponseCode() != 200) return null;

                    InputStream in = conn.getInputStream();
                    FileOutputStream out = new FileOutputStream(cachedFile);
                    byte[] buf = new byte[8192];
                    int len;
                    while ((len = in.read(buf)) > 0) {
                        out.write(buf, 0, len);
                    }
                    out.close();
                    in.close();
                    conn.disconnect();

                    return cachedFile;
                } catch (Exception e) {
                    e.printStackTrace();
                    return null;
                }
            }

            @Override
            protected void onPostExecute(File file) {
                if (file != null && file.exists()) {
                    loadModelFromFile(file);
                } else {
                    // Fallback: load remote URL directly if caching failed
                    loadModelUrl(remoteUrl);
                }
            }
        }.execute();
    }

    private void loadModelFromFile(File file) {
        String fileUrl = "file://" + file.getAbsolutePath();
        loadModelUrl(fileUrl);
    }

    private void loadModelUrl(String modelUrl) {
        String pageUrl = "file:///android_asset/model_viewer.html?model=" + Uri.encode(modelUrl);
        mWebView.loadUrl(pageUrl);
    }
}
