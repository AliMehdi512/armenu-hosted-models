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
import java.nio.ByteBuffer;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Binder;
import android.view.Surface;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;

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
    // QR listener support
    public interface QrListener {
        void onQrFound(float nx, float ny, String text);
    }

    private QrListener qrListener;
    private long lastQrTimestamp = 0;

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
        if (cameraDevice != null) {
            recreateCaptureSession();
        }
    }

    public void registerQrListener(QrListener l) {
        qrListener = l;
    }

    public void unregisterQrListener(QrListener l) {
        if (qrListener == l) qrListener = null;
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
            imageReader = ImageReader.newInstance(320, 240, ImageFormat.YUV_420_888, 2);
            imageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
                @Override
                public void onImageAvailable(ImageReader reader) {
                    Image img = null;
                    try {
                        img = reader.acquireLatestImage();
                        if (img == null) return;
                        processImageForQr(img);
                    } catch (Exception e) {
                    } finally {
                        if (img != null) try { img.close(); } catch (Exception ex) { }
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

    private void processImageForQr(Image image) {
        long now = System.currentTimeMillis();
        if (now - lastQrTimestamp < 120) return;
        try {
            Image.Plane[] planes = image.getPlanes();
            ByteBuffer yBuf = planes[0].getBuffer();
            ByteBuffer uBuf = planes[1].getBuffer();
            ByteBuffer vBuf = planes[2].getBuffer();

            int ySize = yBuf.remaining();
            int uSize = uBuf.remaining();
            int vSize = vBuf.remaining();

            byte[] nv21 = new byte[ySize + uSize + vSize];
            yBuf.get(nv21, 0, ySize);

            byte[] u = new byte[uSize];
            byte[] v = new byte[vSize];
            uBuf.get(u);
            vBuf.get(v);
            for (int i = 0; i < vSize && ySize + 2 * i + 1 < nv21.length; i++) {
                nv21[ySize + 2 * i] = v[i];
                nv21[ySize + 2 * i + 1] = u[i];
            }

            int width = image.getWidth();
            int height = image.getHeight();

            try {
                com.google.zxing.PlanarYUVLuminanceSource py = new com.google.zxing.PlanarYUVLuminanceSource(nv21, width, height, 0, 0, width, height, false);
                com.google.zxing.BinaryBitmap bitmap = new com.google.zxing.BinaryBitmap(new com.google.zxing.common.HybridBinarizer(py));
                com.google.zxing.MultiFormatReader reader = new com.google.zxing.MultiFormatReader();
                com.google.zxing.Result result = reader.decode(bitmap);
                if (result != null && result.getResultPoints() != null && result.getResultPoints().length > 0) {
                    com.google.zxing.ResultPoint[] pts = result.getResultPoints();
                    float cx = 0, cy = 0;
                    for (com.google.zxing.ResultPoint p : pts) { cx += p.getX(); cy += p.getY(); }
                    cx /= pts.length; cy /= pts.length;
                    final float nx = cx / (float) width;
                    final float ny = cy / (float) height;
                    lastQrTimestamp = now;
                    if (qrListener != null) {
                        final String txt = result.getText();
                        backgroundHandler.post(new Runnable() {
                            @Override public void run() {
                                try { qrListener.onQrFound(nx, ny, txt); } catch (Exception e) { }
                            }
                        });
                    }
                }
            } catch (com.google.zxing.NotFoundException e) {
                // no QR
            }
        } catch (Exception e) {
            // ignore
        }
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
