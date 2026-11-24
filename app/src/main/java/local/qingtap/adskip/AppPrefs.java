package local.qingtap.adskip;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

final class AppPrefs {
    static final String PACKAGES = "selected_apps";
    static final String INLINE_PACKAGES = "inline_apps";
    private static final String LEGACY_BLACKLIST = "app_blacklist";
    private static final String SPLASH_BLACKLIST = "splash_blacklist";
    private static final String INLINE_BLACKLIST = "inline_blacklist";
    private static final String PENDING_SPLASH = "pending_splash_apps";
    private static final String PENDING_INLINE = "pending_inline_apps";
    static final String SPLASH_ACTIVE = "splash_active";
    static final String ACTIVE = "active";
    static final String INLINE = "inline";
    static final String MINI_PROGRAM = "mini_program_ads";
    static final String CAMPUS_ADS = "campus_ad_optimization";
    static final String ACTIONS = "clicks_accepted";
    static final String LEARNED = "learned_targets";
    static final String HINTS = "splash_hints";
    static final String FEEDBACK = "window_feedback";
    static final String CONFIRM = "ask_confirm";
    static final String ACTION_LOG = "action_log";
    static final String TAUGHT = "taught_targets";
    static final String KEEP_ALIVE = "keep_alive";
    static final String RECENT_AD = "recent_ad_package";
    static final String REPEAT_COUNT = "repeat_count";
    static final String REPEAT_SECONDS = "repeat_seconds";
    static final String REPEAT_DENIED = "repeat_denied_pages";

    private AppPrefs() {}

    static SharedPreferences open(Context context) {
        return context.getSharedPreferences("settings", Context.MODE_PRIVATE);
    }

    static Set<String> selected(SharedPreferences prefs) {
        return appSelection(prefs, Collections.emptySet()).selected();
    }

    static boolean needsBlacklistMigration(SharedPreferences prefs) {
        return !prefs.contains(LEGACY_BLACKLIST) && !prefs.contains(SPLASH_BLACKLIST)
                && !prefs.contains(INLINE_BLACKLIST) && prefs.contains(PACKAGES);
    }

    static AppSelection appSelection(SharedPreferences prefs, Set<String> previousUnchecked) {
        Set<String> splash = prefs.getStringSet(PACKAGES, Collections.emptySet());
        Set<String> inline = prefs.getStringSet(INLINE_PACKAGES, splash);
        Set<String> previousBlocked = prefs.getStringSet(LEGACY_BLACKLIST, previousUnchecked);
        Set<String> blockedSplash = prefs.getStringSet(SPLASH_BLACKLIST, previousBlocked);
        Set<String> blockedInline = prefs.getStringSet(INLINE_BLACKLIST, previousBlocked);
        Set<String> pendingSplash = prefs.getStringSet(PENDING_SPLASH, Collections.emptySet());
        Set<String> pendingInline = prefs.getStringSet(PENDING_INLINE, Collections.emptySet());
        return new AppSelection(splash, inline, blockedSplash, blockedInline, pendingSplash, pendingInline);
    }

    static void initializeBlacklist(SharedPreferences prefs, AppSelection selection) {
        if (!prefs.contains(SPLASH_BLACKLIST) || !prefs.contains(INLINE_BLACKLIST)) {
            writeBlacklist(prefs.edit(), selection).apply();
        }
    }

    static void saveAppSelection(SharedPreferences prefs, AppSelection selection) {
        SharedPreferences.Editor editor = prefs.edit().putStringSet(PACKAGES, new HashSet<>(selection.splash))
                .putStringSet(INLINE_PACKAGES, new HashSet<>(selection.inline))
                .putStringSet(PENDING_SPLASH, new HashSet<>(selection.pendingSplash))
                .putStringSet(PENDING_INLINE, new HashSet<>(selection.pendingInline));
        writeBlacklist(editor, selection).apply();
    }

    private static SharedPreferences.Editor writeBlacklist(SharedPreferences.Editor editor, AppSelection selection) {
        return editor.putStringSet(SPLASH_BLACKLIST, new HashSet<>(selection.blockedSplash))
                .putStringSet(INLINE_BLACKLIST, new HashSet<>(selection.blockedInline));
    }

    static TaughtTargets taught(SharedPreferences prefs) {
        return new TaughtTargets(new TaughtTargets.Store() {
            @Override public String read() {
                return prefs.getString(TAUGHT, "");
            }

            @Override public void write(String value) {
                prefs.edit().putString(TAUGHT, value).apply();
            }
        });
    }

    static AdActionLog actionLog(SharedPreferences prefs) {
        return new AdActionLog(new AdActionLog.Store() {
            @Override public String read() {
                return prefs.getString(ACTION_LOG, "");
            }

            @Override public void write(String value) {
                prefs.edit().putString(ACTION_LOG, value).apply();
            }
        });
    }

    static int repeatCount(SharedPreferences prefs) {
        return Math.max(2, Math.min(RepeatGuard.MAX_COUNT, prefs.getInt(REPEAT_COUNT, RepeatGuard.DEFAULT_COUNT)));
    }

    static int repeatSeconds(SharedPreferences prefs) {
        return Math.max(2, Math.min(RepeatGuard.MAX_SECONDS,
                prefs.getInt(REPEAT_SECONDS, RepeatGuard.DEFAULT_SECONDS)));
    }

    static Set<String> deniedPages(SharedPreferences prefs) {
        return prefs.getStringSet(REPEAT_DENIED, Collections.emptySet());
    }

    static void setPageDenied(SharedPreferences prefs, String page, boolean denied) {
        Set<String> pages = new HashSet<>(deniedPages(prefs));
        if (denied) pages.add(page);
        else pages.remove(page);
        prefs.edit().putStringSet(REPEAT_DENIED, pages).apply();
    }
}
