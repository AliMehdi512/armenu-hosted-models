package com.PulsarLabs.ARMenu;

import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.media.Image;
import android.util.Log;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.ChecksumException;
import com.google.zxing.FormatException;
import com.google.zxing.LuminanceSource;
import com.google.zxing.NotFoundException;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.ResultPoint;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

/**
 * QrTracker - Computer Vision based QR code detection and tracking.
 * Continuously processes camera frames to detect QR code position.
 * 
 * Coordinate mapping:
 * - Camera frame is typically 640x480 (landscape)
 * - Phone screen is portrait, so we need to rotate coordinates
 * - Camera X (0-640) maps to Screen Y (0-screenHeight) 
 * - Camera Y (0-480) maps to Screen X (screenWidth-0) [inverted]
 */
public class QrTracker {

    private static final String TAG = "QrTracker";
    private QRCodeReader qrReader;
    private QrTrackingListener listener;
    private int frameWidth;
    private int frameHeight;
    private boolean isTracking = false;
    
    // Last known QR position (normalized 0-1, in SCREEN coordinates)
    private float lastCenterX = 0.5f;
    private float lastCenterY = 0.5f;
    private float lastSize = 0.3f;
    private boolean qrDetected = false;
    
    // Smoothing factor for position updates (higher = more responsive)
    private static final float SMOOTHING = 0.6f;
    
    // Frames without detection before marking as lost
    private int framesWithoutDetection = 0;
    private static final int MAX_FRAMES_WITHOUT_DETECTION = 10;

    public interface QrTrackingListener {
        void onQrPositionUpdated(float centerX, float centerY, float size, boolean detected);
    }

    public QrTracker() {
        qrReader = new QRCodeReader();
    }

    public void setListener(QrTrackingListener listener) {
        this.listener = listener;
    }

    public void startTracking() {
        isTracking = true;
    }

    public void stopTracking() {
        isTracking = false;
    }

    public boolean isTracking() {
        return isTracking;
    }

    /**
     * Process a YUV_420_888 image from Camera2 API
     */
    public void processImage(Image image) {
        if (!isTracking || image == null) return;
        
        try {
            frameWidth = image.getWidth();
            frameHeight = image.getHeight();
            
            // Get Y plane (luminance) for QR detection
            Image.Plane yPlane = image.getPlanes()[0];
            ByteBuffer yBuffer = yPlane.getBuffer();
            byte[] yData = new byte[yBuffer.remaining()];
            yBuffer.get(yData);
            
            // Create luminance source for ZXing
            PlanarYUVLuminanceSource source = new PlanarYUVLuminanceSource(
                yData, frameWidth, frameHeight, 
                0, 0, frameWidth, frameHeight, 
                false
            );
            
            BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));
            
            try {
                Result result = qrReader.decode(bitmap);
                processQrResult(result);
            } catch (NotFoundException e) {
                // QR not found in this frame
                handleQrLost();
            } catch (ChecksumException | FormatException e) {
                // Invalid QR code
                handleQrLost();
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error processing image", e);
        }
    }

    /**
     * Process raw YUV byte array (alternative method)
     */
    public void processYuvData(byte[] yuvData, int width, int height) {
        if (!isTracking || yuvData == null) return;
        
        try {
            frameWidth = width;
            frameHeight = height;
            
            PlanarYUVLuminanceSource source = new PlanarYUVLuminanceSource(
                yuvData, width, height,
                0, 0, width, height,
                false
            );
            
            BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));
            
            try {
                Result result = qrReader.decode(bitmap);
                processQrResult(result);
            } catch (NotFoundException e) {
                handleQrLost();
            } catch (ChecksumException | FormatException e) {
                handleQrLost();
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error processing YUV data", e);
        }
    }

    private void processQrResult(Result result) {
        ResultPoint[] points = result.getResultPoints();
        if (points == null || points.length < 3) {
            handleQrLost();
            return;
        }
        
        // Calculate center from QR corner points
        float sumX = 0, sumY = 0;
        float minX = Float.MAX_VALUE, maxX = Float.MIN_VALUE;
        float minY = Float.MAX_VALUE, maxY = Float.MIN_VALUE;
        
        for (ResultPoint point : points) {
            sumX += point.getX();
            sumY += point.getY();
            minX = Math.min(minX, point.getX());
            maxX = Math.max(maxX, point.getX());
            minY = Math.min(minY, point.getY());
            maxY = Math.max(maxY, point.getY());
        }
        
        // Raw center in camera frame (0-width, 0-height)
        float camCenterX = sumX / points.length;
        float camCenterY = sumY / points.length;
        
        // Camera is typically rotated 90 degrees for portrait mode
        // Camera frame (landscape 640x480) -> Screen (portrait)
        // Camera X -> Screen Y (direct mapping)
        // Camera Y -> Screen X (inverted - camera Y=0 is screen right)
        
        // Map to screen coordinates (normalized 0-1)
        // screenX = 1 - (camY / frameHeight)  -> camera top = screen right
        // screenY = camX / frameWidth          -> camera left = screen top
        float screenCenterX = 1.0f - (camCenterY / frameHeight);
        float screenCenterY = camCenterX / frameWidth;
        
        // Calculate size (use the larger dimension for more stability)
        float qrWidth = maxX - minX;
        float qrHeight = maxY - minY;
        float qrSize = Math.max(qrWidth, qrHeight);
        float size = qrSize / Math.max(frameWidth, frameHeight);
        
        // Apply smoothing for stable tracking
        if (qrDetected) {
            lastCenterX = lastCenterX + SMOOTHING * (screenCenterX - lastCenterX);
            lastCenterY = lastCenterY + SMOOTHING * (screenCenterY - lastCenterY);
            lastSize = lastSize + SMOOTHING * (size - lastSize);
        } else {
            // First detection, no smoothing
            lastCenterX = screenCenterX;
            lastCenterY = screenCenterY;
            lastSize = size;
        }
        // Reset lost frame counter on successful detection
        framesWithoutDetection = 0;
        qrDetected = true;
        
        // Notify listener every frame for smooth tracking
        if (listener != null) {
            listener.onQrPositionUpdated(lastCenterX, lastCenterY, lastSize, true);
        }
        
        Log.d(TAG, "QR at screen: " + lastCenterX + ", " + lastCenterY + " size: " + lastSize);
    }

    private void handleQrLost() {
        framesWithoutDetection++;
        
        // Still send position updates with last known position for smooth transition
        if (listener != null) {
            // Only mark as lost after several frames without detection
            boolean stillTracking = framesWithoutDetection < MAX_FRAMES_WITHOUT_DETECTION;
            if (qrDetected || !stillTracking) {
                listener.onQrPositionUpdated(lastCenterX, lastCenterY, lastSize, stillTracking);
            }
            if (!stillTracking && qrDetected) {
                qrDetected = false;
            }
        }
    }

    /**
     * Get the last known QR center X (normalized 0-1)
     */
    public float getLastCenterX() {
        return lastCenterX;
    }

    /**
     * Get the last known QR center Y (normalized 0-1)
     */
    public float getLastCenterY() {
        return lastCenterY;
    }

    /**
     * Get the last known QR size (normalized 0-1)
     */
    public float getLastSize() {
        return lastSize;
    }

    /**
     * Check if QR is currently detected
     */
    public boolean isQrDetected() {
        return qrDetected;
    }

    public void reset() {
        qrDetected = false;
        lastCenterX = 0.5f;
        lastCenterY = 0.5f;
        lastSize = 0.3f;
    }
}
