package local.qingtap.adskip;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;

// Notification helper for the "the accessibility permission is gone" case, usable from a receiver
// where no service is running yet.
final class AdNotifier {
    private static final String CHANNEL = "qingtap-guard";
    private static final int WARNING_ID = 32;

    private final Context context;

    AdNotifier(Context context) {
        this.context = context.getApplicationContext();
    }

    void missingPermission() {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) return;
        if (manager.getNotificationChannel(CHANNEL) == null) {
            NotificationChannel channel = new NotificationChannel(CHANNEL, "常驻与恢复提醒",
                    NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("无障碍被关闭时提醒你重新开启");
            manager.createNotificationChannel(channel);
        }
        Intent settings = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        settings.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pending = PendingIntent.getActivity(context, 0, settings,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new Notification.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_adskip)
                .setContentTitle("清弹的无障碍被关掉了")
                .setContentText("点一下回到系统设置重新启用，之后会自动守护")
                .setAutoCancel(true)
                .setContentIntent(pending)
                .build();
        manager.notify(WARNING_ID, notification);
    }
}
