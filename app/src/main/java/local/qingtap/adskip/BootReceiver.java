package local.qingtap.adskip;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

// After a reboot, an app update or being cleared away, the accessibility toggle can be gone. This
// receiver puts the guard back and, when the permission really is off, leaves a tap-to-reenable notice.
public final class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;
        boolean wanted = AppPrefs.open(context).getBoolean(AppPrefs.KEEP_ALIVE, true);
        if (!wanted) return;
        if (ServiceStatus.accessibilityEnabled(context, AdSkipService.class)) {
            KeepAliveService.start(context);
        } else {
            new AdNotifier(context).missingPermission();
        }
    }
}
