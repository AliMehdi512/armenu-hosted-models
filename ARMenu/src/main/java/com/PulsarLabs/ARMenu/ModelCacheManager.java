package com.PulsarLabs.ARMenu;

import android.content.Context;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.FileOutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.security.MessageDigest;

public class ModelCacheManager {

    private static final String CACHE_DIR = "model_cache";
    private Context context;

    public ModelCacheManager(Context ctx) {
        context = ctx;
    }

    /**
     * Get a cached model if it exists, or download and cache it if it's a remote URL.
     * Backward-compatible method for GLB models.
     */
    public String getCachedModelPath(String modelUrl) {
        return getCachedPath(modelUrl);
    }

    /**
     * Generic cached path resolver for any remote asset (models, thumbnails, etc.).
     * Returns a local file:// path when cached, or the original URL if caching isn't applicable.
     */
    public String getCachedPath(String url) {
        // If it's an Android asset, return as-is
        if (url.startsWith("file:///android_asset/")) {
            return url;
        }

        // For remote URLs, check cache and download if needed
        if (url.startsWith("http://") || url.startsWith("https://")) {
            try {
                String hash = hashUrl(url);
                String ext = getExtension(url);
                File cacheDir = new File(context.getCacheDir(), CACHE_DIR);
                if (!cacheDir.exists()) {
                    cacheDir.mkdirs();
                }
                File cachedFile = new File(cacheDir, hash + "." + ext);

                if (cachedFile.exists() && cachedFile.length() > 0) {
                    android.util.Log.d("ModelCache", "Using cached: " + url);
                    InAppLogger.log("Cache hit: " + shortUrl(url));
                    return "file://" + cachedFile.getAbsolutePath();
                } else {
                    android.util.Log.d("ModelCache", "Downloading and caching: " + url);
                    InAppLogger.log("Downloading: " + shortUrl(url));
                    downloadAndCache(url, cachedFile);
                    if (cachedFile.exists() && cachedFile.length() > 0) {
                        android.util.Log.d("ModelCache", "Successfully cached to: " + cachedFile.getAbsolutePath());
                        InAppLogger.log("Cached OK: " + shortUrl(url));
                        return "file://" + cachedFile.getAbsolutePath();
                    } else {
                        android.util.Log.w("ModelCache", "Failed to cache: " + url);
                        InAppLogger.log("Cache failed: " + shortUrl(url));
                    }
                }
            } catch (Exception e) {
                android.util.Log.e("ModelCache", "Cache error for " + url, e);
                InAppLogger.log("Cache error: " + shortUrl(url));
                e.printStackTrace();
            }
        }

        // If caching failed or not needed, return original URL
        android.util.Log.d("ModelCache", "Returning original URL: " + url);
        InAppLogger.log("Using original URL: " + shortUrl(url));
        return url;
    }

    /**
     * Generate a hash of the URL to use as cache filename
     */
    private String hashUrl(String url) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] hash = md.digest(url.getBytes());
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /**
     * Derive a safe file extension from a URL (e.g., glb, png, jpg). Defaults to 'dat'.
     */
    private String getExtension(String url) {
        try {
            // Strip query and fragment
            int q = url.indexOf('?');
            if (q >= 0) url = url.substring(0, q);
            int h = url.indexOf('#');
            if (h >= 0) url = url.substring(0, h);
            int lastSlash = url.lastIndexOf('/');
            int lastDot = url.lastIndexOf('.');
            if (lastDot > lastSlash && lastDot >= 0) {
                String ext = url.substring(lastDot + 1).toLowerCase();
                if (ext.length() > 0 && ext.length() <= 5) {
                    return ext;
                }
            }
        } catch (Exception e) {
            // ignore
        }
        return "dat";
    }

    /**
     * Download a remote model and save to cache
     */
    private void downloadAndCache(String urlStr, File destFile) throws Exception {
        android.util.Log.d("ModelCache", "Downloading from: " + urlStr);
        URL url = new URL(urlStr);
        java.net.URLConnection conn = url.openConnection();
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);

        try (InputStream in = conn.getInputStream();
             OutputStream out = new FileOutputStream(destFile)) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            long totalBytes = 0;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
                totalBytes += bytesRead;
            }
            out.flush();
            android.util.Log.d("ModelCache", "Downloaded " + totalBytes + " bytes to " + destFile.getName());
            InAppLogger.log("Downloaded " + (totalBytes / 1024) + " KB");
        } catch (Exception e) {
            android.util.Log.e("ModelCache", "Download failed: " + urlStr, e);
            InAppLogger.log("Download failed: " + shortUrl(urlStr));
            if (destFile.exists()) {
                destFile.delete();
            }
            throw e;
        }
    }

    /**
     * Shorten noisy URLs for on-screen logging.
     */
    private String shortUrl(String url) {
        if (url == null) return "";
        int lastSlash = url.lastIndexOf('/') + 1;
        if (lastSlash > 0 && lastSlash < url.length()) {
            return url.substring(lastSlash);
        }
        return url;
    }

    /**
     * Clear all cached models
     */
    public void clearCache() {
        try {
            File cacheDir = new File(context.getCacheDir(), CACHE_DIR);
            if (cacheDir.exists()) {
                for (File f : cacheDir.listFiles()) {
                    f.delete();
                }
                cacheDir.delete();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
