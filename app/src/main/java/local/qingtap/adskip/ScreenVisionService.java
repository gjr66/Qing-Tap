package local.qingtap.adskip;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.util.DisplayMetrics;

import java.nio.ByteBuffer;

// Optional experimental mode C: still frames from the screen, analysed on device, never uploaded and
// never written to disk. It only runs while the user keeps the switch on and Android holds a live
// screen capture grant, which shows in the status bar.
public final class ScreenVisionService extends Service {
    static final String EXTRA_CODE = "code";
    static final String EXTRA_DATA = "data";
    static volatile boolean running;

    private static final int NOTIFICATION_ID = 22;
    private static final String CHANNEL = "qingtap-vision";
    private static final int SCALE = 4;

    private MediaProjection projection;
    private ImageReader reader;
    private VirtualDisplay display;
    private HandlerThread thread;
    private Handler worker;
    // Capture and pixel conversion run on the service worker, never on the accessibility thread, so a
    // frame cannot stall event delivery.
    private final ScreenVision.Grabber grabber = sink -> {
        Handler target = worker;
        if (target == null) return;
        target.post(() -> grab(sink));
    };

    static void start(Context context, int code, Intent data) {
        Intent intent = new Intent(context, ScreenVisionService.class);
        intent.putExtra(EXTRA_CODE, code);
        intent.putExtra(EXTRA_DATA, data);
        if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent);
        else context.startService(intent);
    }

    static void stop(Context context) {
        context.stopService(new Intent(context, ScreenVisionService.class));
    }

    @Override public IBinder onBind(Intent intent) {
        return null;
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(NOTIFICATION_ID, notification());
        if (intent == null) {
            stopSelf();
            return START_NOT_STICKY;
        }
        int code = intent.getIntExtra(EXTRA_CODE, 0);
        Intent data = intent.getParcelableExtra(EXTRA_DATA);
        if (code == 0 || data == null) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (display != null) return START_STICKY;

        MediaProjectionManager manager = getSystemService(MediaProjectionManager.class);
        projection = manager == null ? null : manager.getMediaProjection(code, data);
        if (projection == null) {
            stopSelf();
            return START_NOT_STICKY;
        }
        thread = new HandlerThread("qingtap-vision");
        thread.start();
        worker = new Handler(thread.getLooper());
        projection.registerCallback(new MediaProjection.Callback() {
        }, worker);

        DisplayMetrics metrics = getResources().getDisplayMetrics();
        int width = Math.max(64, metrics.widthPixels / SCALE);
        int height = Math.max(64, metrics.heightPixels / SCALE);
        reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2);
        display = projection.createVirtualDisplay("qingtap", width, height,
                Math.max(60, metrics.densityDpi / SCALE),
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader.getSurface(), null, worker);
        running = true;
        ScreenVision.attach(grabber);
        return START_STICKY;
    }

    private Notification notification() {
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            NotificationChannel channel = new NotificationChannel(CHANNEL, "视觉兜底",
                    NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("屏幕画面在本机分析，用于定位开屏上的关闭按钮");
            manager.createNotificationChannel(channel);
        }
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL) : new Notification.Builder(this);
        return builder.setContentTitle("清弹 · 视觉兜底已开启")
                .setContentText("只在开屏窗口出现时抓一帧，在本机内存里判断，不上传")
                .setSmallIcon(R.drawable.ic_adskip)
                .setOngoing(true)
                .build();
    }

    private void grab(ScreenVision.Sink sink) {
        ImageReader target = reader;
        if (target == null || sink == null) return;
        Image image = null;
        try {
            image = target.acquireLatestImage();
        } catch (IllegalStateException busy) {
            return;
        }
        if (image == null) return;
        try {
            Image.Plane plane = image.getPlanes()[0];
            ByteBuffer buffer = plane.getBuffer();
            int pixelStride = plane.getPixelStride();
            int rowStride = plane.getRowStride();
            int width = image.getWidth();
            int height = image.getHeight();
            int[] argb = new int[width * height];
            for (int y = 0; y < height; y++) {
                int rowStart = y * rowStride;
                for (int x = 0; x < width; x++) {
                    int offset = rowStart + x * pixelStride;
                    argb[y * width + x] = 0xff000000
                            | ((buffer.get(offset) & 0xff) << 16)
                            | ((buffer.get(offset + 1) & 0xff) << 8)
                            | (buffer.get(offset + 2) & 0xff);
                }
            }
            sink.frame(width, height, argb);
        } finally {
            image.close();
        }
    }

    @Override public void onDestroy() {
        ScreenVision.detach(grabber);
        running = false;
        if (display != null) {
            display.release();
            display = null;
        }
        if (reader != null) {
            reader.close();
            reader = null;
        }
        if (projection != null) {
            projection.stop();
            projection = null;
        }
        if (thread != null) {
            thread.quitSafely();
            thread = null;
        }
        super.onDestroy();
    }
}
