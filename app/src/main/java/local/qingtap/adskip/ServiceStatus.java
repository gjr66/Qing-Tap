package local.qingtap.adskip;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ResolveInfo;
import android.view.accessibility.AccessibilityManager;

// One place that answers "is our accessibility service currently enabled", used by the main screen,
// the keep-alive service and the boot receiver.
final class ServiceStatus {
    private ServiceStatus() {}

    static boolean accessibilityEnabled(Context context, Class<?> serviceClass) {
        AccessibilityManager manager = context.getSystemService(AccessibilityManager.class);
        if (manager == null) return false;
        ComponentName expected = new ComponentName(context, serviceClass);
        for (AccessibilityServiceInfo info
                : manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)) {
            ResolveInfo resolve = info.getResolveInfo();
            if (resolve == null || resolve.serviceInfo == null) continue;
            ComponentName actual = new ComponentName(resolve.serviceInfo.packageName, resolve.serviceInfo.name);
            if (expected.equals(actual)) return true;
        }
        return false;
    }
}
