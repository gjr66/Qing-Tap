package local.qingtap.adskip;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;

// Low priority foreground service whose only job is to keep the process alive and to notice when the
// system turned the accessibility permission off. Android does not let an app re-enable that permission
// itself, so the notification carries a one tap route back to the settings page.
public final class KeepAliveService extends Service {
    static final int NOTIFICATION_ID = 31;
    static final int WARNING_ID = 32;
    private static final String CHANNEL = "qingtap-guard";
    private static final long INTERVAL_MS = 20000;

    static volatile boolean running;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable tick = this::check;
    private boolean warned;

    static void start(Context context) {
        Intent intent = new Intent(context, KeepAliveService.class);
        if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent);
        else context.startService(intent);
    }

    static void stop(Context context) {
        context.stopService(new Intent(context, KeepAliveService.class));
    }

    @Override public IBinder onBind(Intent intent) {
        return null;
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(NOTIFICATION_ID, build(true));
        running = true;
        handler.removeCallbacks(tick);
        handler.postDelayed(tick, 5000);
        return START_STICKY;
    }

    private void check() {
        boolean enabled = ServiceStatus.accessibilityEnabled(this, AdSkipService.class);
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, build(enabled));
            if (!enabled && !warned) {
                warned = true;
                manager.notify(WARNING_ID, warning());
            } else if (enabled && warned) {
                warned = false;
                manager.cancel(WARNING_ID);
            }
        }
        handler.postDelayed(tick, INTERVAL_MS);
    }

    private Notification build(boolean enabled) {
        channel();
        Notification.Builder builder = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_adskip)
                .setContentTitle(enabled ? "清弹正在守护无障碍服务" : "无障碍服务已被关闭")
                .setContentText(enabled ? "常驻通知可以让系统更少清理它，可在清弹内关闭"
                        : "点这里回到系统设置重新启用")
                .setOngoing(true)
                .setContentIntent(settingsIntent());
        return builder.build();
    }

    private Notification warning() {
        channel();
        return new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_adskip)
                .setContentTitle("清弹的无障碍被系统关掉了")
                .setContentText("点一下重新启用；被清理后打开会自动提醒")
                .setAutoCancel(true)
                .setContentIntent(settingsIntent())
                .build();
    }

    private PendingIntent settingsIntent() {
        Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private void channel() {
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager == null) return;
        if (manager.getNotificationChannel(CHANNEL) != null) return;
        NotificationChannel channel = new NotificationChannel(CHANNEL, "常驻与恢复提醒",
                NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("让清弹更少被系统清理；无障碍被关闭时提醒你重新开启");
        manager.createNotificationChannel(channel);
    }

    @Override public void onDestroy() {
        running = false;
        handler.removeCallbacks(tick);
        super.onDestroy();
    }
}
