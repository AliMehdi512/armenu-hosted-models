package com.PulsarLabs.ARMenu;

import android.app.Notification;
import android.app.NotificationChannel;
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
import android.media.ImageReader;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.util.Arrays;

public class CameraService extends Service {

    private static final String CHANNEL_ID = "ARMenuCamera";
    private CameraDevice cameraDevice;
    private CameraCaptureSession captureSession;
    private ImageReader imageReader;
    private Handler backgroundHandler;

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
        return null;
    }

    @Override
    public void onDestroy() {
        closeCamera();
        stopBackgroundThread();
        stopForeground(true);
        super.onDestroy();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "ARMenu Camera", NotificationManager.IMPORTANCE_LOW);
            nm.createNotificationChannel(ch);
        }
    }

    private Notification buildNotification(String text) {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
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
            imageReader = ImageReader.newInstance(320, 240, ImageFormat.YUV_420_888, 2);
            imageReader.setOnImageAvailableListener(reader -> {
                // Drain frames to keep camera active; no processing for MVP
                try {
                    reader.acquireNextImage().close();
                } catch (Exception e) { }
            }, backgroundHandler);
            manager.openCamera(cameraId, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(CameraDevice camera) {
                    cameraDevice = camera;
                    try {
                        final CaptureRequest.Builder builder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                        builder.addTarget(imageReader.getSurface());
                        cameraDevice.createCaptureSession(Arrays.asList(imageReader.getSurface()), new CameraCaptureSession.StateCallback() {
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
}
