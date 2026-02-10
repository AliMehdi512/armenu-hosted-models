package com.PulsarLabs.ARMenu;

import android.app.Notification;
// NotificationChannel (API 26) not available with compileSdkVersion 25
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Binder;
import android.view.Surface;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;

public class CameraService extends Service {

    private static final String CHANNEL_ID = "ARMenuCamera";
    private CameraDevice cameraDevice;
    private CameraCaptureSession captureSession;
    private ImageReader imageReader;
    private Handler backgroundHandler;
    private Surface previewSurface;
    private final IBinder binder = new LocalBinder();
    
    // QR Tracking
    private QrTracker qrTracker;
    private QrTracker.QrTrackingListener trackingListener;
    private boolean qrTrackingEnabled = false;
    private int frameSkipCounter = 0;
    private static final int FRAME_SKIP = 3; // Process every 3rd frame to reduce CPU/heat

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startForeground(1337, buildNotification("Camera running"));
        startBackgroundThread();
        openCamera();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    public class LocalBinder extends Binder {
        public CameraService getService() {
            return CameraService.this;
        }
    }

    public void setPreviewSurface(Surface s) {
        previewSurface = s;
        // Force restart the camera with the new surface to ensure capture session is properly set
        resetCamera();
    }

    /**
     * Enable QR tracking with a callback listener
     */
    public void enableQrTracking(QrTracker.QrTrackingListener listener) {
        this.trackingListener = listener;
        if (qrTracker == null) {
            qrTracker = new QrTracker();
        }
        qrTracker.setListener(listener);
        qrTracker.startTracking();
        qrTrackingEnabled = true;
    }

    /**
     * Disable QR tracking
     */
    public void disableQrTracking() {
        qrTrackingEnabled = false;
        if (qrTracker != null) {
            qrTracker.stopTracking();
        }
        trackingListener = null;
    }

    /**
     * Get the QR tracker instance
     */
    public QrTracker getQrTracker() {
        return qrTracker;
    }

    public void resetCamera() {
        // Close the current camera and reopen it with the current preview surface
        try {
            closeCamera();
            // Small delay to ensure camera is fully released before reopening
            Thread.sleep(100);
        } catch (Exception e) { }
        if (backgroundHandler != null) {
            backgroundHandler.post(new Runnable() {
                @Override
                public void run() {
                    openCamera();
                }
            });
        }
    }

    @Override
    public void onDestroy() {
        closeCamera();
        stopBackgroundThread();
        stopForeground(true);
        super.onDestroy();
    }

    private void createNotificationChannel() {
        // compileSdkVersion is 25 in this project; NotificationChannel is API 26+.
        // Skip channel creation to remain compatible with this project's SDK level.
    }

    private Notification buildNotification(String text) {
        return new NotificationCompat.Builder(this)
                .setContentTitle("ARMenu")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_menu_camera)
                .setOngoing(true)
                .build();
    }

    private void startBackgroundThread() {
        HandlerThread thread = new HandlerThread("CameraBg");
        thread.start();
        backgroundHandler = new Handler(thread.getLooper());
    }

    private void stopBackgroundThread() {
        if (backgroundHandler != null) {
            backgroundHandler.getLooper().quitSafely();
            backgroundHandler = null;
        }
    }

    private void openCamera() {
        try {
            CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
            String[] ids = manager.getCameraIdList();
            if (ids.length == 0) return;
            String cameraId = ids[0];
            // Increased resolution for better QR detection
            imageReader = ImageReader.newInstance(640, 480, ImageFormat.YUV_420_888, 2);
            imageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
                @Override
                public void onImageAvailable(ImageReader reader) {
                    Image image = null;
                    try {
                        image = reader.acquireNextImage();
                        if (image != null) {
                            // Process frame for QR tracking if enabled
                            if (qrTrackingEnabled && qrTracker != null) {
                                frameSkipCounter++;
                                if (frameSkipCounter >= FRAME_SKIP) {
                                    frameSkipCounter = 0;
                                    qrTracker.processImage(image);
                                }
                            }
                        }
                    } catch (Exception e) { 
                    } finally {
                        if (image != null) {
                            try { image.close(); } catch (Exception e) { }
                        }
                    }
                }
            }, backgroundHandler);
            manager.openCamera(cameraId, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(CameraDevice camera) {
                    cameraDevice = camera;
                    try {
                        final CaptureRequest.Builder builder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                        List<Surface> surfaces = new ArrayList<Surface>();
                        surfaces.add(imageReader.getSurface());
                        builder.addTarget(imageReader.getSurface());
                        if (previewSurface != null) {
                            surfaces.add(previewSurface);
                            builder.addTarget(previewSurface);
                        }
                        cameraDevice.createCaptureSession(surfaces, new CameraCaptureSession.StateCallback() {
                            @Override
                            public void onConfigured(CameraCaptureSession session) {
                                captureSession = session;
                                try {
                                    captureSession.setRepeatingRequest(builder.build(), null, backgroundHandler);
                                } catch (CameraAccessException e) { }
                            }

                            @Override
                            public void onConfigureFailed(CameraCaptureSession session) { }
                        }, backgroundHandler);
                    } catch (CameraAccessException e) { }
                }

                @Override
                public void onDisconnected(CameraDevice camera) {
                    camera.close();
                    cameraDevice = null;
                }

                @Override
                public void onError(CameraDevice camera, int error) {
                    camera.close();
                    cameraDevice = null;
                }
            }, backgroundHandler);
        } catch (CameraAccessException | SecurityException e) {
            // Missing permission or camera error
        }
    }

    private void closeCamera() {
        try {
            if (captureSession != null) {
                captureSession.close();
                captureSession = null;
            }
            if (cameraDevice != null) {
                cameraDevice.close();
                cameraDevice = null;
            }
            if (imageReader != null) {
                imageReader.close();
                imageReader = null;
            }
        } catch (Exception e) { }
    }

    private void recreateCaptureSession() {
        if (cameraDevice == null || imageReader == null) return;
        try {
            if (captureSession != null) {
                try { captureSession.close(); } catch (Exception e) { }
                captureSession = null;
            }
            final CaptureRequest.Builder builder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            List<Surface> surfaces = new ArrayList<Surface>();
            surfaces.add(imageReader.getSurface());
            builder.addTarget(imageReader.getSurface());
            if (previewSurface != null) {
                surfaces.add(previewSurface);
                builder.addTarget(previewSurface);
            }
            cameraDevice.createCaptureSession(surfaces, new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(CameraCaptureSession session) {
                    captureSession = session;
                    try {
                        captureSession.setRepeatingRequest(builder.build(), null, backgroundHandler);
                    } catch (CameraAccessException e) { }
                }

                @Override
                public void onConfigureFailed(CameraCaptureSession session) { }
            }, backgroundHandler);
        } catch (CameraAccessException e) { }
    }
}
