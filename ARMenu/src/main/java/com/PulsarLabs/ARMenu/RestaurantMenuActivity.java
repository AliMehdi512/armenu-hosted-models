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
import android.view.Gravity;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.widget.RatingBar;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import android.content.SharedPreferences;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class RestaurantMenuActivity extends Activity implements TextureView.SurfaceTextureListener {

    private WebView mWebView;
    private TextureView mTextureView;
    private CameraService cameraService;
    private boolean bound = false;
    private Surface pendingSurface = null;
    private ModelCacheManager cacheManager;
    private CartManager cartManager;
    
    private String restaurantBaseUrl;
    private String cachedMenuPath = null;
    private List<MenuItem> menuItems = new ArrayList<MenuItem>();
    private TextView cartBadge;
    private LinearLayout carouselLayout;
    private TextView restaurantNameView;
    private RatingBar infoRatingBar;
    private TextView priceTextView;
    private Random random = new Random();
    private MenuItem selectedItem = null;

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
        cartManager = new CartManager(this);

        // Get restaurant URL from intent and normalize it
        Uri data = getIntent().getData();
        if (data != null) {
            restaurantBaseUrl = normalizeRestaurantUrl(data.toString());
        }

        // If an already-cached menu path was provided (LaunchActivity on subsequent runs), remember it
        String cachedPath = null;
        try {
            cachedPath = getIntent().getStringExtra("cached_menu_path");
        } catch (Exception ignored) { }
        if ((cachedPath == null || cachedPath.isEmpty())) {
            // also check prefs for cached menu (in case QrScanner saved base URL earlier)
            try {
                SharedPreferences prefs = getSharedPreferences("ARMenuPrefs", MODE_PRIVATE);
                String prefCache = prefs.getString("cached_menu_path", null);
                if (prefCache != null && !prefCache.isEmpty()) {
                    cachedPath = prefCache;
                }
                if ((restaurantBaseUrl == null || restaurantBaseUrl.isEmpty())) {
                    String prefBase = prefs.getString("restaurant_base_url", null);
                    if (prefBase != null && !prefBase.isEmpty()) {
                        restaurantBaseUrl = prefBase;
                    }
                }
            } catch (Exception ignored) { }
        }
        // Save cached path to field for use after UI is created and for lifecycle events
        if (cachedPath != null && !cachedPath.isEmpty()) cachedMenuPath = cachedPath;

        FrameLayout root = new FrameLayout(this);

        // Camera preview background
        mTextureView = new TextureView(this);
        FrameLayout.LayoutParams tvLp = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, 
            FrameLayout.LayoutParams.MATCH_PARENT
        );
        mTextureView.setLayoutParams(tvLp);
        mTextureView.setSurfaceTextureListener(this);

        // WebView for 3D model (transparent so camera shows behind)
        mWebView = new WebView(this);
        mWebView.setLayoutParams(tvLp);
        try {
            mWebView.setBackgroundColor(0);
            mWebView.setBackgroundResource(android.R.color.transparent);
            mWebView.setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null);
        } catch (Exception e) { }

        WebSettings ws = mWebView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setAllowFileAccess(true);
        ws.setAllowContentAccess(true);
        ws.setAllowFileAccessFromFileURLs(true);
        ws.setAllowUniversalAccessFromFileURLs(true);
        ws.setDomStorageEnabled(true);
        ws.setCacheMode(android.webkit.WebSettings.LOAD_NO_CACHE);
        ws.setMixedContentMode(android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        mWebView.setWebViewClient(new InterceptingClient());
        mWebView.setWebChromeClient(new android.webkit.WebChromeClient() {
            @Override
            public boolean onConsoleMessage(android.webkit.ConsoleMessage cm) {
                InAppLogger.log("WV console: " + cm.message());
                return super.onConsoleMessage(cm);
            }
        });

        // Top: Restaurant name
        restaurantNameView = new TextView(this);
        restaurantNameView.setTextSize(24);
        restaurantNameView.setTextColor(Color.WHITE);
        restaurantNameView.setPadding(20, 40, 20, 20);
        restaurantNameView.setBackgroundColor(Color.argb(180, 0, 0, 0));
        FrameLayout.LayoutParams nameLp = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        );
        nameLp.gravity = Gravity.TOP;
        restaurantNameView.setLayoutParams(nameLp);

        // Cart icon
        final TextView cartIcon = new TextView(this);
        cartIcon.setText("🛒");
        cartIcon.setTextSize(22);
        cartIcon.setTextColor(Color.WHITE);
        cartIcon.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams cartLp = new FrameLayout.LayoutParams(dpToPx(44), dpToPx(44));
        cartLp.gravity = Gravity.TOP | Gravity.RIGHT;
        cartLp.setMargins(0, dpToPx(40), dpToPx(8), 0);
        cartIcon.setLayoutParams(cartLp);
        cartIcon.setAlpha(0.95f);

    cartBadge = new TextView(this);
    cartBadge.setTextSize(12);
    cartBadge.setTextColor(Color.WHITE);
    cartBadge.setBackgroundColor(Color.parseColor("#E94560"));
    cartBadge.setPadding(dpToPx(6), dpToPx(2), dpToPx(6), dpToPx(2));
    FrameLayout.LayoutParams badgeLp = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
    badgeLp.gravity = Gravity.TOP | Gravity.RIGHT;
    badgeLp.setMargins(0, dpToPx(32), dpToPx(4), 0);
    cartBadge.setLayoutParams(badgeLp);
    int qty = cartManager.getTotalQuantity();
    if (qty <= 0) cartBadge.setVisibility(View.GONE);
    else cartBadge.setText(String.valueOf(qty));

        cartIcon.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    startActivity(new android.content.Intent(RestaurantMenuActivity.this, CartActivity.class));
                } catch (Exception e) { }
            }
        });

        // Bottom container: Vertical layout holding carousel + info overlay
        LinearLayout bottomContainer = new LinearLayout(this);
        bottomContainer.setOrientation(LinearLayout.VERTICAL);
        bottomContainer.setBackgroundColor(Color.argb(200, 0, 0, 0));
        FrameLayout.LayoutParams bottomLp = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        );
        bottomLp.gravity = Gravity.BOTTOM;
        bottomContainer.setLayoutParams(bottomLp);

        // Horizontal carousel (inside bottom container)
        HorizontalScrollView scrollView = new HorizontalScrollView(this);
        scrollView.setBackgroundColor(Color.TRANSPARENT);
        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dpToPx(110)
        );
        scrollView.setLayoutParams(scrollLp);

        carouselLayout = new LinearLayout(this);
        carouselLayout.setOrientation(LinearLayout.HORIZONTAL);
        carouselLayout.setPadding(dpToPx(10), dpToPx(8), dpToPx(10), dpToPx(8));
        scrollView.addView(carouselLayout);

        // Info overlay with rating and price (inside bottom container)
        LinearLayout infoOverlay = new LinearLayout(this);
        infoOverlay.setOrientation(LinearLayout.VERTICAL);
        infoOverlay.setBackgroundColor(Color.TRANSPARENT);
        infoOverlay.setPadding(dpToPx(12), dpToPx(8), dpToPx(12), dpToPx(12));
        LinearLayout.LayoutParams infoLp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        infoOverlay.setLayoutParams(infoLp);

        // Rating bar row
        LinearLayout ratingRow = new LinearLayout(this);
        ratingRow.setOrientation(LinearLayout.HORIZONTAL);
        ratingRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams ratingRowLp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        ratingRow.setLayoutParams(ratingRowLp);

        infoRatingBar = new RatingBar(this, null, android.R.attr.ratingBarStyleSmall);
        infoRatingBar.setNumStars(5);
        infoRatingBar.setStepSize(0.5f);
        infoRatingBar.setIsIndicator(false);
        infoRatingBar.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        infoRatingBar.setOnRatingBarChangeListener(new RatingBar.OnRatingBarChangeListener() {
            @Override
            public void onRatingChanged(RatingBar ratingBar, float rating, boolean fromUser) {
                if (fromUser) {
                    Toast.makeText(RestaurantMenuActivity.this, "Thanks for rating: " + rating, Toast.LENGTH_SHORT).show();
                }
            }
        });
        ratingRow.addView(infoRatingBar);

        // Add to cart button next to rating
        final Button addToCartBtn = new Button(this);
        addToCartBtn.setText("Add to Cart");
        addToCartBtn.setTextSize(14);
        addToCartBtn.setPadding(dpToPx(8), dpToPx(6), dpToPx(8), dpToPx(6));
        LinearLayout.LayoutParams addLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        addLp.setMargins(dpToPx(12), 0, 0, 0);
        addToCartBtn.setLayoutParams(addLp);
        addToCartBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (selectedItem != null) {
                    cartManager.addItem(selectedItem, 1);
                    int newQty = cartManager.getTotalQuantity();
                    try {
                        cartBadge.setVisibility(View.VISIBLE);
                        cartBadge.setText(String.valueOf(newQty));
                    } catch (Exception e) { }
                    Toast.makeText(RestaurantMenuActivity.this, "Added to cart: " + selectedItem.name, Toast.LENGTH_SHORT).show();
                }
            }
        });

        ratingRow.addView(addToCartBtn);

        // Price text (multi-line support)
        priceTextView = new TextView(this);
        LinearLayout.LayoutParams priceLp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        priceLp.setMargins(0, dpToPx(4), 0, 0);
        priceTextView.setLayoutParams(priceLp);
        priceTextView.setTextColor(Color.WHITE);
        priceTextView.setTextSize(14);
        priceTextView.setLineSpacing(dpToPx(2), 1.0f);
        priceTextView.setPadding(0, dpToPx(4), 0, dpToPx(4));
        priceTextView.setTypeface(null, android.graphics.Typeface.BOLD);

        infoOverlay.addView(ratingRow);
        infoOverlay.addView(priceTextView);

        // Add carousel then info overlay to bottom container
        bottomContainer.addView(scrollView);
        bottomContainer.addView(infoOverlay);

        root.addView(mTextureView);
        root.addView(mWebView);
        root.addView(restaurantNameView);
        root.addView(bottomContainer);

    // Add cart UI to root so it appears above the WebView/texture
    try { root.addView(cartIcon); } catch (Exception ignored) { }
    try { root.addView(cartBadge); } catch (Exception ignored) { }


        setContentView(root);

        // After UI is initialized, if we have a cached menu path, load from file. Otherwise, proceed to remote load.
        if (cachedMenuPath != null && !cachedMenuPath.isEmpty()) {
            loadRestaurantMenuFromFile(cachedMenuPath);
            return;
        }

    // Note: removed attaching the on-screen logger per UI changes

        // Get restaurant URL from intent
        if (restaurantBaseUrl == null || restaurantBaseUrl.isEmpty()) {
            Toast.makeText(this, "No restaurant URL provided", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        loadRestaurantMenu(restaurantBaseUrl);
    }

    /**
     * Normalize restaurant URL: strip menu.json suffix if present
     */
    private String normalizeRestaurantUrl(String url) {
        if (url.endsWith("/menu.json")) {
            return url.substring(0, url.length() - "/menu.json".length());
        }
        return url;
    }

    private void loadRestaurantMenu(final String baseUrl) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String menuJsonUrl = baseUrl + (baseUrl.endsWith("/") ? "" : "/") + "menu.json";
                    android.util.Log.d("RestaurantMenu", "Loading menu from: " + menuJsonUrl);
                    InAppLogger.log("Fetching menu.json...");
                    
                    URL url = new URL(menuJsonUrl);
                    java.net.URLConnection conn = url.openConnection();
                    conn.setConnectTimeout(5000);
                    conn.setReadTimeout(5000);
                    
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder json = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        json.append(line);
                    }
                    reader.close();

                    // Save fetched menu.json to internal storage for offline/cached startup
                    try {
                        String menuFileName = "menu_" + Integer.toHexString(baseUrl.hashCode()) + ".json";
                        File menuFile = new File(getFilesDir(), menuFileName);
                        try (FileOutputStream fos = new FileOutputStream(menuFile)) {
                            fos.write(json.toString().getBytes("UTF-8"));
                            fos.flush();
                        }
                        try {
                            SharedPreferences prefs = getSharedPreferences("ARMenuPrefs", MODE_PRIVATE);
                            prefs.edit().putString("cached_menu_path", menuFile.getAbsolutePath()).putString("restaurant_base_url", baseUrl).putBoolean("setup_complete", true).apply();
                        } catch (Exception ignored) { }
                    } catch (Exception e) {
                        // ignore cache-write failures
                    }

                    android.util.Log.d("RestaurantMenu", "Menu JSON loaded: " + json.toString().substring(0, Math.min(100, json.length())));
                    InAppLogger.log("Menu loaded OK");
                    
                    JSONObject menuObj = new JSONObject(json.toString());
                    final String restName = menuObj.getString("restaurantName");
                    JSONArray products = menuObj.getJSONArray("products");

                    menuItems.clear();
                    for (int i = 0; i < products.length(); i++) {
                        JSONObject prod = products.getJSONObject(i);
                        MenuItem mi = MenuItem.fromJson(prod);
                        if (mi == null) {
                            String id = prod.optString("id", "");
                            String name = prod.optString("name", "");
                            String model = prod.optString("model", "");
                            String thumbnail = prod.optString("thumbnail", "");
                            mi = new MenuItem(id, name, model, thumbnail);
                        }
                        menuItems.add(mi);
                    }

                    android.util.Log.d("RestaurantMenu", "Parsed " + menuItems.size() + " menu items");
                    InAppLogger.log("Items: " + menuItems.size());

                    // Prefetch all restaurant assets into cache (models and thumbnails)
                    prefetchAllAssets();

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            restaurantNameView.setText(restName);
                            buildCarousel();
                            // Load first product by default and update price/rating overlay
                            if (menuItems.size() > 0) {
                                selectedItem = menuItems.get(0);
                                updatePriceAndRating(selectedItem);
                                loadProduct(selectedItem);
                            }
                        }
                    });
                } catch (final Exception e) {
                    android.util.Log.e("RestaurantMenu", "Error loading menu", e);
                    InAppLogger.log("Menu error: " + e.getMessage());
                    e.printStackTrace();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(RestaurantMenuActivity.this, "Failed to load menu: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    });
                }
            }
        }).start();
    }

    /**
     * Download all assets referenced by the restaurant menu into cache.
     * Runs in background; UI remains responsive.
     */
    private void prefetchAllAssets() {
        try {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    for (MenuItem item : menuItems) {
                        try {
                            String modelUrl = restaurantBaseUrl + "/" + item.model;
                            InAppLogger.log("Prefetching: " + item.model);
                            cacheManager.getCachedPath(modelUrl);
                            if (item.thumbnail != null && item.thumbnail.length() > 0) {
                                String thumbUrl = restaurantBaseUrl + "/" + item.thumbnail;
                                InAppLogger.log("Prefetching: " + item.thumbnail);
                                cacheManager.getCachedPath(thumbUrl);
                            }
                        } catch (Exception ignored) { }
                    }
                    InAppLogger.log("Prefetch complete");
                }
            }).start();
        } catch (Exception e) { }
    }

    /**
     * Load a previously cached menu.json saved to internal storage.
     */
    private void loadRestaurantMenuFromFile(final String filePath) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    File f = new File(filePath);
                    if (!f.exists()) {
                        // fallback to remote if file missing
                        if (restaurantBaseUrl != null && !restaurantBaseUrl.isEmpty()) {
                            loadRestaurantMenu(restaurantBaseUrl);
                        }
                        return;
                    }
                    StringBuilder json = new StringBuilder();
                    try (FileInputStream fis = new FileInputStream(f);
                         java.io.InputStreamReader isr = new java.io.InputStreamReader(fis, "UTF-8");
                         BufferedReader reader = new BufferedReader(isr)) {
                        String line;
                        while ((line = reader.readLine()) != null) json.append(line);
                    }

                    JSONObject menuObj = new JSONObject(json.toString());
                    final String restName = menuObj.optString("restaurantName", "Menu");
                    JSONArray products = menuObj.optJSONArray("products");

                    menuItems.clear();
                    if (products != null) {
                        for (int i = 0; i < products.length(); i++) {
                            JSONObject prod = products.getJSONObject(i);
                            MenuItem mi = MenuItem.fromJson(prod);
                            if (mi == null) {
                                String id = prod.optString("id", "");
                                String name = prod.optString("name", "");
                                String model = prod.optString("model", "");
                                String thumbnail = prod.optString("thumbnail", "");
                                mi = new MenuItem(id, name, model, thumbnail);
                            }
                            menuItems.add(mi);
                        }
                    }

                    // Ensure restaurantBaseUrl is set (may be in prefs)
                    try {
                        SharedPreferences prefs = getSharedPreferences("ARMenuPrefs", MODE_PRIVATE);
                        if (restaurantBaseUrl == null || restaurantBaseUrl.isEmpty()) {
                            String prefBase = prefs.getString("restaurant_base_url", null);
                            if (prefBase != null && !prefBase.isEmpty()) restaurantBaseUrl = prefBase;
                        }
                        prefs.edit().putBoolean("setup_complete", true).apply();
                    } catch (Exception ignored) { }

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            restaurantNameView.setText(restName);
                            buildCarousel();
                            if (menuItems.size() > 0) {
                                selectedItem = menuItems.get(0);
                                updatePriceAndRating(selectedItem);
                                loadProduct(selectedItem);
                            }
                        }
                    });

                    // Prefetch assets to cache (models/thumbnails)
                    prefetchAllAssets();

                } catch (Exception e) {
                    e.printStackTrace();
                    // fallback to remote if anything goes wrong
                    if (restaurantBaseUrl != null && !restaurantBaseUrl.isEmpty()) {
                        loadRestaurantMenu(restaurantBaseUrl);
                    }
                }
            }
        }).start();
    }

    private void buildCarousel() {
        carouselLayout.removeAllViews();
        for (final MenuItem item : menuItems) {
            LinearLayout itemView = new LinearLayout(this);
            itemView.setOrientation(LinearLayout.VERTICAL);
            itemView.setPadding(dpToPx(10), dpToPx(5), dpToPx(10), dpToPx(5));
            itemView.setBackgroundColor(Color.argb(100, 255, 255, 255));
            
            LinearLayout.LayoutParams itemLp = new LinearLayout.LayoutParams(
                dpToPx(80),
                LinearLayout.LayoutParams.MATCH_PARENT
            );
            itemLp.setMargins(dpToPx(5), 0, dpToPx(5), 0);
            itemView.setLayoutParams(itemLp);

            // Thumbnail icon (async load from cache or network)
            final ImageView icon = new ImageView(this);
            icon.setBackgroundColor(Color.argb(255, 100, 100, 100));
            icon.setScaleType(ImageView.ScaleType.CENTER_CROP);
            LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dpToPx(60), dpToPx(60));
            iconLp.gravity = Gravity.CENTER_HORIZONTAL;
            icon.setLayoutParams(iconLp);

            // If thumbnail exists, try to load it asynchronously (from cacheManager)
            if (item.thumbnail != null && item.thumbnail.length() > 0) {
                final String thumbUrl = restaurantBaseUrl + "/" + item.thumbnail;
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            String cached = cacheManager.getCachedPath(thumbUrl);
                            Bitmap bmp = null;
                            if (cached != null) {
                                if (cached.startsWith("file://")) {
                                    String path = cached.substring(7);
                                    bmp = BitmapFactory.decodeFile(path);
                                } else if (cached.startsWith("http://") || cached.startsWith("https://")) {
                                    java.io.InputStream in = new java.net.URL(cached).openStream();
                                    bmp = BitmapFactory.decodeStream(in);
                                    in.close();
                                }
                            }
                            final Bitmap finalBmp = bmp;
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    try {
                                        if (finalBmp != null) {
                                            icon.setImageBitmap(finalBmp);
                                        } else {
                                            // use bundled placeholder drawable when thumbnail missing
                                            try {
                                                icon.setImageResource(R.drawable.placeholder);
                                            } catch (Exception ignored) { }
                                        }
                                    } catch (Exception e) { }
                                }
                            });
                        } catch (Exception e) {
                            // ignore thumbnail load failure
                        }
                    }
                }).start();
            }

            TextView nameView = new TextView(this);
            nameView.setText(item.name);
            nameView.setTextSize(10);
            nameView.setTextColor(Color.WHITE);
            nameView.setGravity(Gravity.CENTER);
            nameView.setMaxLines(2);

            itemView.addView(icon);
            itemView.addView(nameView);

            // price label under name (always show price, formatted)
            TextView priceLabel = new TextView(this);
            priceLabel.setTextSize(12);
            priceLabel.setTextColor(Color.YELLOW);
            priceLabel.setGravity(Gravity.CENTER);
            String curr = item.currency != null && item.currency.length() > 0 ? item.currency + " " : "";
            String p;
            if (Math.abs(item.price - Math.round(item.price)) < 0.001) p = curr + String.valueOf((int)Math.round(item.price));
            else p = curr + String.format(java.util.Locale.US, "%.2f", item.price);
            priceLabel.setText(p);
            itemView.addView(priceLabel);

            itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    selectedItem = item;
                    updatePriceAndRating(item);
                    loadProduct(item);
                }
            });

            carouselLayout.addView(itemView);
        }
    }

    private void loadProduct(final MenuItem item) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final String modelUrl = restaurantBaseUrl + "/" + item.model;
                    InAppLogger.log("Loading model: " + item.name);
                    final String cachedPath = cacheManager.getCachedPath(modelUrl);
                    InAppLogger.log("Model path: " + cachedPath);
                    final String filename = extractFileName(cachedPath);
                    final String localServeUrl = "https://local.model/" + filename;
                    
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                String pageUrl = "file:///android_asset/model_viewer.html?model=" + localServeUrl;
                                InAppLogger.log("Loading URL: " + pageUrl);
                                mWebView.clearCache(true);
                                mWebView.loadUrl(pageUrl);
                                // update bottom overlay price and cart badge
                                updatePriceAndRating(item);
                                updateCartBadge();
                            } catch (Exception e) {
                                InAppLogger.log("WebView error: " + e.getMessage());
                            }
                        }
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                    InAppLogger.log("Model load error: " + e.getMessage());
                }
            }
        }).start();
    }

    /**
     * WebViewClient that serves cached GLB/PNG files over a pseudo HTTPS host to satisfy fetch restrictions.
     */
    private class InterceptingClient extends WebViewClient {
        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            try {
                Uri uri = request.getUrl();
                if ("local.model".equals(uri.getHost())) {
                    String name = uri.getLastPathSegment();
                    if (name != null) {
                        // Prefer persistent filesDir cache (migrated by ModelCacheManager)
                        File filesCache = new File(getFilesDir(), "model_cache/" + name);
                        if (filesCache.exists()) {
                            String mime = name.endsWith(".png") ? "image/png" : "model/gltf-binary";
                            return new WebResourceResponse(mime, "UTF-8", new java.io.FileInputStream(filesCache));
                        }
                        // Fallback to legacy cacheDir
                        File cacheFile = new File(getCacheDir(), "model_cache/" + name);
                        if (cacheFile.exists()) {
                            String mime = name.endsWith(".png") ? "image/png" : "model/gltf-binary";
                            return new WebResourceResponse(mime, "UTF-8", new java.io.FileInputStream(cacheFile));
                        }
                    }
                }
            } catch (Exception e) {
                InAppLogger.log("Intercept error: " + e.getMessage());
            }
            return super.shouldInterceptRequest(view, request);
        }

        // Fallback for older API levels
        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
            try {
                Uri uri = Uri.parse(url);
                if ("local.model".equals(uri.getHost())) {
                    String name = uri.getLastPathSegment();
                    if (name != null) {
                        File filesCache = new File(getFilesDir(), "model_cache/" + name);
                        if (filesCache.exists()) {
                            String mime = name.endsWith(".png") ? "image/png" : "model/gltf-binary";
                            return new WebResourceResponse(mime, "UTF-8", new java.io.FileInputStream(filesCache));
                        }
                        File cacheFile = new File(getCacheDir(), "model_cache/" + name);
                        if (cacheFile.exists()) {
                            String mime = name.endsWith(".png") ? "image/png" : "model/gltf-binary";
                            return new WebResourceResponse(mime, "UTF-8", new java.io.FileInputStream(cacheFile));
                        }
                    }
                }
            } catch (Exception e) {
                InAppLogger.log("Intercept error: " + e.getMessage());
            }
            return super.shouldInterceptRequest(view, url);
        }
    }

    private String extractFileName(String path) {
        try {
            Uri u = Uri.parse(path);
            String last = u.getLastPathSegment();
            if (last != null) return last;
        } catch (Exception ignored) { }
        int slash = path.lastIndexOf('/');
        if (slash >= 0 && slash < path.length() - 1) return path.substring(slash + 1);
        return "model.glb";
    }

    /**
     * Update the bottom overlay to show the selected product's price and a randomized rating.
     */
    private void updatePriceAndRating(final MenuItem item) {
        try {
            if (item == null) return;
            final String price = getPriceStringForItem(item);
            // Random initial rating between 3.5 and 5.0 (steps of 0.5)
            int steps = random.nextInt(4); // 0..3
            final float rating = 3.5f + steps * 0.5f;
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        if (priceTextView != null) priceTextView.setText(price);
                        if (infoRatingBar != null) infoRatingBar.setRating(rating);
                        updateCartBadge();
                    } catch (Exception e) { }
                }
            });
        } catch (Exception e) { }
    }

    private String getPriceStringForItem(MenuItem item) {
        if (item == null) return "";
        // Prefer explicit price field
        StringBuilder sb = new StringBuilder();
        if (item.name != null) sb.append(item.name);
        // Always show price (even if zero) and description
        sb.append("\n");
        String curr = (item.currency != null && item.currency.length() > 0) ? item.currency + " " : "";
        if (Math.abs(item.price - Math.round(item.price)) < 0.001) sb.append(curr).append((int)Math.round(item.price));
        else sb.append(curr).append(String.format(java.util.Locale.US, "%.2f", item.price));
        if (item.description != null && item.description.length() > 0) {
            sb.append("\n").append(item.description);
        }
        return sb.toString();
    }

    // (Add-to-cart button / badge are created in onCreate and via local UI wiring)

    private void updateCartBadge() {
        try {
            if (cartBadge != null) {
                int cnt = cartManager.getTotalQuantity();
                if (cnt <= 0) cartBadge.setVisibility(View.GONE);
                else {
                    cartBadge.setVisibility(View.VISIBLE);
                    cartBadge.setText(String.valueOf(cnt));
                }
            }
        } catch (Exception e) { }
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

        // If we don't have any menu items loaded (e.g., activity relaunched), try loading cached menu from prefs
        try {
            if (menuItems == null || menuItems.size() == 0) {
                SharedPreferences prefs = getSharedPreferences("ARMenuPrefs", MODE_PRIVATE);
                boolean setup = prefs.getBoolean("setup_complete", false);
                if (setup) {
                    String cached = prefs.getString("cached_menu_path", null);
                    if (cached != null && !cached.isEmpty()) {
                        cachedMenuPath = cached;
                        loadRestaurantMenuFromFile(cachedMenuPath);
                    }
                }
            }
        } catch (Exception ignored) { }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        // When receiving a new intent (launcher relaunch), ensure cached menu is loaded if setup was completed
        try {
            SharedPreferences prefs = getSharedPreferences("ARMenuPrefs", MODE_PRIVATE);
            boolean setup = prefs.getBoolean("setup_complete", false);
            if (setup && (menuItems == null || menuItems.size() == 0)) {
                String cached = prefs.getString("cached_menu_path", null);
                if (cached != null && !cached.isEmpty()) {
                    cachedMenuPath = cached;
                    loadRestaurantMenuFromFile(cachedMenuPath);
                }
            }
        } catch (Exception ignored) { }
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

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round((float) dp * density);
    }
}
