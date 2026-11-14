package local.qingtap.adskip;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

// Shared lookup for names and icons so both the picker and the learning screen show the same thing.
final class AppCatalog {
    static final class Entry {
        final String pkg;
        final String label;
        final Drawable icon;

        Entry(String pkg, String label, Drawable icon) {
            this.pkg = pkg;
            this.label = label;
            this.icon = icon;
        }
    }

    private AppCatalog() {}

    // Launcher apps plus any package already selected, so an app without a launcher icon still shows up.
    static List<Entry> load(Context context, Collection<String> extra) {
        PackageManager manager = context.getPackageManager();
        Set<String> packages = launchablePackages(manager, Intent.CATEGORY_LAUNCHER);
        if (extra != null) packages.addAll(extra);
        packages.add(context.getPackageName());
        List<Entry> entries = new ArrayList<>();
        for (String pkg : packages) {
            entries.add(new Entry(pkg, label(context, pkg), icon(context, pkg)));
        }
        Collator collator = Collator.getInstance(Locale.SIMPLIFIED_CHINESE);
        entries.sort((left, right) -> {
            int compared = collator.compare(left.label, right.label);
            return compared == 0 ? left.pkg.compareTo(right.pkg) : compared;
        });
        return entries;
    }

    private static Set<String> launchablePackages(PackageManager manager, String category) {
        Intent intent = new Intent(Intent.ACTION_MAIN).addCategory(category);
        Set<String> packages = new HashSet<>();
        for (ResolveInfo info : manager.queryIntentActivities(intent, 0)) {
            packages.add(info.activityInfo.packageName);
        }
        return packages;
    }

    static Set<String> eligibleForBulkAdd(Context context) {
        PackageManager manager = context.getPackageManager();
        Set<String> eligible = launchablePackages(manager, Intent.CATEGORY_LAUNCHER);
        eligible.removeAll(launchablePackages(manager, Intent.CATEGORY_HOME));
        eligible.removeIf(pkg -> pkg.equals("android") || pkg.equals("com.android.settings")
                || pkg.equals("com.android.systemui") || pkg.endsWith(".permissioncontroller")
                || pkg.endsWith(".packageinstaller"));
        eligible.add(context.getPackageName());
        return eligible;
    }

    static Set<String> previousUnchecked(Context context, SharedPreferences prefs) {
        if (!AppPrefs.needsBlacklistMigration(prefs)) return Collections.emptySet();
        return previousUnchecked(context, prefs, eligibleForBulkAdd(context));
    }

    static Set<String> previousUnchecked(Context context, SharedPreferences prefs, Set<String> eligible) {
        if (!AppPrefs.needsBlacklistMigration(prefs)) return Collections.emptySet();
        Set<String> previous = new HashSet<>(eligible);
        previous.removeAll(AppPrefs.selected(prefs));
        PackageManager manager = context.getPackageManager();
        try {
            long upgradedAt = manager.getPackageInfo(context.getPackageName(), 0).lastUpdateTime;
            previous.removeIf(pkg -> installedAfter(manager, pkg, upgradedAt));
        } catch (PackageManager.NameNotFoundException ignored) {
            // Keep existing unchecked apps unchecked if the upgrade timestamp is unavailable.
        }
        return previous;
    }

    private static boolean installedAfter(PackageManager manager, String pkg, long upgradedAt) {
        try {
            return manager.getPackageInfo(pkg, 0).firstInstallTime > upgradedAt;
        } catch (PackageManager.NameNotFoundException missing) {
            return false;
        }
    }

    static String label(Context context, String pkg) {
        try {
            ApplicationInfo info = context.getPackageManager().getApplicationInfo(pkg, 0);
            return context.getPackageManager().getApplicationLabel(info).toString();
        } catch (Exception missing) {
            return pkg;
        }
    }

    static Drawable icon(Context context, String pkg) {
        try {
            return context.getPackageManager().getApplicationIcon(pkg);
        } catch (Exception missing) {
            return null;
        }
    }
}
