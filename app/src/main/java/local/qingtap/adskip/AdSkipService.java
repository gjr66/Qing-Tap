package local.qingtap.adskip;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.accessibilityservice.GestureDescription;
import android.app.KeyguardManager;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.hardware.HardwareBuffer;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.view.Display;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.widget.ImageView;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Consumer;

@SuppressWarnings("deprecation")
public final class AdSkipService extends AccessibilityService {
    private static final String TENCENT_VIDEO = "com.tencent.qqlive";
    private static final String HOME_LOG = "QingTapHome";
    private static final String HOME_RULE = "直接进入首页";
    private static final long HOME_CHECK_MS = 2400;
    private static final String TENCENT_SPLASH_ACTIVITY = "com.tencent.qqlive.ona.activity.SplashHomeActivity";
    private static final long TENCENT_STARTUP_MS = 4500;
    private static final int MAX_NODES = 320;
    private static final long SCAN_BUDGET_MS = 120;
    private static final long SCAN_GAP_MS = 44;
    private static final long SCROLL_GAP_MS = 125;
    private static final long[] STARTUP_PROBES_MS = {55, 145, 320, 650};
    private static final long TAUGHT_PROBE_GAP_MS = 480;
    private static final int TAUGHT_PROBE_LIMIT = 4;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable scan = this::scanPendingWindow;
    private final Runnable probe = () -> scheduleScan(SCAN_GAP_MS);
    private final Runnable retryProbe = () -> scheduleScan(0);
    private final Runnable repeatRescan = () -> scheduleScan(0);
    private final ClickGate<AccessibilityNodeInfo> clicks = new ClickGate<>();
    private final RepeatGuard repeatGuard = new RepeatGuard();
    private RepeatGuard.Turn repeatPrompt;
    private final SharedPreferences.OnSharedPreferenceChangeListener preferencesChanged =
            (preferences, key) -> {
                if (AppPrefs.PACKAGES.equals(key) || AppPrefs.INLINE_PACKAGES.equals(key)
                        || AppPrefs.ACTIVE.equals(key) || AppPrefs.SPLASH_ACTIVE.equals(key)
                        || AppPrefs.INLINE.equals(key)) {
                    updatePackageFilter();
                }
            };

    private SharedPreferences prefs;
    private AppSelection selection;
    private boolean splashActive;
    private boolean inlineActive;
    private KeyguardManager keyguard;
    private PowerManager power;
    private String requestedPackage;
    private int requestedEventWindow;
    private long lastScan;
    private long lastProbeStart;
    private long tencentStartupUntil;
    private int tencentStartupWindow = -1;
    private long scheduledAt = Long.MAX_VALUE;
    private AdMemory memory;
    private String windowActivity = "";
    private long windowOpenedAt;
    private boolean learnedUsed;
    private boolean ticking;
    private int lastCount = -1;
    private String learnedSignature;
    private int learnedWindow = -1;
    private double learnedX;
    private double learnedY;
    private final Runnable learnedCheck = this::verifyLearnedTap;
    private final Runnable visionCheck = this::verifyVisionTap;
    private AdHints hints;
    private AdFeedback feedback;
    private AdActionLog actionLog;
    private WindowCloseTrace closeTrace;
    private final HomeLaunch homeLaunch = new HomeLaunch();
    private HomeLaunch.Discovery homeDiscovery;
    private HomeEntryCheck homeEntryCheck;
    private int lastHomeLogWindow = -1;
    private String lastHomeLogPackage = "";
    private final Runnable homeCheckTimeout = () -> finishHomeCheck(AdActionLog.State.UNKNOWN,
            "已发送首页请求，等待后仍未观察到可确认的首页");
    private String launcherPackage = "";
    private String[] protectedPackages;
    private boolean desktopWatching;
    private TaughtTargets taught;
    private int taughtGeneration;
    private int taughtAttempts;
    private long nextTaughtProbeAt;
    private static AdSkipService connected;

    // The teaching screen writes prefs and asks the service to pick the change up immediately.
    static void reloadTaught() {
        AdSkipService service = connected;
        if (service != null && service.prefs != null) service.loadTaught();
    }

    private void loadTaught() {
        taught = AppPrefs.taught(prefs);
    }
    private int confirmWindow = -1;
    private boolean splashSeen;
    private boolean sawCountdown;
    private boolean userClicked;
    private static final class Pending {
        final String signature;
        final double x;
        final double y;
        final String label;
        final boolean strong;
        final long at;

        Pending(String signature, double x, double y, String label, boolean strong, long at) {
            this.signature = signature;
            this.x = x;
            this.y = y;
            this.label = label;
            this.strong = strong;
            this.at = at;
        }
    }

    private Pending pending;
    private boolean acted;
    private String seenSignature;
    private boolean hintCaptured;
    private double hintX;
    private double hintY;
    private String hintLabel = "";
    private int visionWindow = -1;
    private String visionSignature;
    private int visionWindowId = -1;
    private double visionX;
    private double visionY;
    private static Runnable memoryClear;
    private static Runnable hintClear;
    private static Runnable feedbackClear;
    private static java.util.function.Consumer<String> forgetHook;

    // Forgetting one row must also stop the click: remove every record of that package and activity
    // across all four stores.
    static void forgetAppActivity(String pkg, String activity) {
        AdSkipService service = connected;
        if (service == null) return;
        String page = (activity == null || activity.isEmpty()) ? "*" : activity;
        String prefix = pkg + "/" + page + "/";
        if (service.memory != null) service.memory.removePrefix(prefix);
        if (service.hints != null) service.hints.removePrefix(prefix);
        if (service.feedback != null) service.feedback.forgetPrefix(prefix);
    }

    static void forget(String signature) {
        java.util.function.Consumer<String> hook = forgetHook;
        if (hook != null) hook.accept(signature);
    }

    static void clearTaught() {
        AdSkipService service = connected;
        if (service != null && service.taught != null) service.taught.clear();
    }

    static void clearFeedback() {
        Runnable hook = feedbackClear;
        if (hook != null) hook.run();
    }

    static void clearLearned() {
        Runnable memory = memoryClear;
        if (memory != null) memory.run();
        Runnable hints = hintClear;
        if (hints != null) hints.run();
        Runnable feedbackHook = feedbackClear;
        if (feedbackHook != null) feedbackHook.run();
    }

    @Override protected void onServiceConnected() {
        super.onServiceConnected();
        connected = this;
        if (prefs != null) prefs.unregisterOnSharedPreferenceChangeListener(preferencesChanged);
        prefs = AppPrefs.open(this);
        memory = new AdMemory(new AdMemory.Store() {
            @Override public String read() {
                return prefs.getString(AppPrefs.LEARNED, "");
            }

            @Override public void write(String value) {
                prefs.edit().putString(AppPrefs.LEARNED, value).apply();
            }
        });
        memoryClear = memory::clear;
        hints = new AdHints(new AdHints.Store() {
            @Override public String read() {
                return prefs.getString(AppPrefs.HINTS, "");
            }

            @Override public void write(String value) {
                prefs.edit().putString(AppPrefs.HINTS, value).apply();
            }
        });
        hintClear = hints::clear;
        feedback = new AdFeedback(new AdFeedback.Store() {
            @Override public String read() {
                return prefs.getString(AppPrefs.FEEDBACK, "");
            }

            @Override public void write(String value) {
                prefs.edit().putString(AppPrefs.FEEDBACK, value).apply();
            }
        });
        feedbackClear = feedback::clear;
        actionLog = AppPrefs.actionLog(prefs);
        forgetHook = signature -> {
            if (memory != null) memory.remove(signature);
            if (hints != null) hints.remove(signature);
            if (feedback != null) feedback.forget(signature);
        };
        reloadTaught();
        keyguard = getSystemService(KeyguardManager.class);
        power = getSystemService(PowerManager.class);
        prefs.registerOnSharedPreferenceChangeListener(preferencesChanged);
        updatePackageFilter();
    }

    private void updatePackageFilter() {
        clearPending();
        selection = AppPrefs.appSelection(prefs, java.util.Collections.emptySet());
        splashActive = prefs.getBoolean(AppPrefs.SPLASH_ACTIVE, true);
        inlineActive = prefs.getBoolean(AppPrefs.INLINE, false);
        AccessibilityServiceInfo info = getServiceInfo();
        if (info == null) return;
        List<String> packages = new ArrayList<>();
        packages.add(getPackageName());
        if (prefs.getBoolean(AppPrefs.ACTIVE, false)) {
            for (String name : selection.selected()) {
                if (!getPackageName().equals(name) && (splashAllowed(name) || inlineAllowed(name))) {
                    packages.add(name);
                }
            }
        }
        launcherPackage = "";
        if (packages.stream().anyMatch(name -> !getPackageName().equals(name) && splashAllowed(name))) {
            Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
            ResolveInfo resolved = getPackageManager().resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY);
            if (resolved != null && resolved.activityInfo != null) {
                launcherPackage = resolved.activityInfo.packageName;
                if (!packages.contains(launcherPackage)) packages.add(launcherPackage);
                info.eventTypes |= AccessibilityEvent.TYPE_VIEW_CLICKED;
            }
        } else {
            info.eventTypes &= ~AccessibilityEvent.TYPE_VIEW_CLICKED;
        }
        protectedPackages = packages.toArray(new String[0]);
        info.packageNames = protectedPackages;
        info.eventTypes &= ~AccessibilityEvent.TYPE_WINDOWS_CHANGED;
        setServiceInfo(info);
        if (!launcherPackage.isEmpty() && isForegroundPackage(launcherPackage)) setDesktopWatching(true);
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (prefs == null) return;
        String name = event.getPackageName() == null ? "" : event.getPackageName().toString();
        int type = event.getEventType();
        trackLauncher(event, name, type);
        if (name.isEmpty() || type == AccessibilityEvent.TYPE_WINDOWS_CHANGED) return;
        if (launcherPackage.equals(name) || getPackageName().equals(name) && DemoActivity.isTesting()) return;
        if (!isAllowed(name)) {
            if (name.equals(requestedPackage) || (name.equals(getPackageName())
                    && event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                    && isForegroundPackage(name))) clearPending();
            return;
        }
        boolean packageChanged = !name.equals(requestedPackage);
        boolean windowChanged = requestedEventWindow != event.getWindowId();
        requestedPackage = name;
        requestedEventWindow = event.getWindowId();
        boolean windowState = type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED;
        if (type == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            // A click in the splash window that we did not issue: the user closed it themselves, so the
            // window must not teach us anything.
            userClicked = true;
        }
        if (packageChanged) tencentStartupUntil = 0;
        if (windowState && TENCENT_VIDEO.equals(name)) {
            if (TENCENT_SPLASH_ACTIVITY.contentEquals(
                    event.getClassName() == null ? "" : event.getClassName())) {
                tencentStartupWindow = event.getWindowId();
                tencentStartupUntil = SystemClock.uptimeMillis() + TENCENT_STARTUP_MS;
            } else if (windowChanged) {
                tencentStartupUntil = 0;
            }
        }
        if (packageChanged || (windowChanged && windowState)) {
            clearLastTarget();
        }
        if (packageChanged || windowChanged) {
            // The previous window is gone: if it was a splash we never acted on, it just taught us where
            // its skip control was.
            finishCloseTrace();
            closeSplash(SystemClock.uptimeMillis());
            windowActivity = !windowState || event.getClassName() == null ? "" : event.getClassName().toString();
            windowOpenedAt = SystemClock.uptimeMillis();
            learnedUsed = false;
            visionWindow = -1;
            confirmWindow = -1;
            ticking = false;
            lastCount = -1;
            sawCountdown = false;
            userClicked = false;
            resetTaughtProbes();
        } else if (windowState && event.getClassName() != null) {
            HomeLaunch.Route route = HomeLaunch.forPackage(name);
            if (windowActivity.isEmpty() || route != null && route.launcherActivity.contentEquals(event.getClassName())) {
                windowActivity = event.getClassName().toString();
            }
        }
        if (packageChanged || (windowState
                && (windowChanged || SystemClock.uptimeMillis() - lastProbeStart >= 240))) {
            beginProbes();
        } else {
            scheduleScan(type == AccessibilityEvent.TYPE_VIEW_SCROLLED ? SCROLL_GAP_MS : SCAN_GAP_MS);
        }
    }

    private void trackLauncher(AccessibilityEvent event, String name, int type) {
        if (launcherPackage.isEmpty()) return;
        boolean windowState = type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED;
        boolean focusChanged = type == AccessibilityEvent.TYPE_WINDOWS_CHANGED && desktopWatching;
        if (!windowState && !focusChanged) return;
        if (focusChanged && Build.VERSION.SDK_INT >= 28
                && (event.getWindowChanges() & (AccessibilityEvent.WINDOWS_CHANGE_FOCUSED
                        | AccessibilityEvent.WINDOWS_CHANGE_ACTIVE)) == 0) return;

        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;
        try {
            CharSequence packageName = root.getPackageName();
            if (packageName == null || !defaultDisplayWindow(root)) return;
            String foreground = packageName.toString();
            long now = SystemClock.uptimeMillis();
            if (launcherPackage.equals(foreground)) {
                if (windowState && launcherPackage.equals(name) && !desktopWatching) {
                    homeLaunch.reset();
                    homeDiscovery = null;
                    setDesktopWatching(true);
                }
                return;
            }
            if (desktopWatching) {
                setDesktopWatching(false);
                if (!getPackageName().equals(foreground) && splashAllowed(foreground) && isAllowed(foreground)) {
                    homeLaunch.fromDesktop(foreground, now);
                    if (HomeLaunch.forPackage(foreground) == null) discoverHomeEntry(foreground, now);
                }
            }
            if (homeDiscovery != null && !homeDiscovery.pkg.equals(foreground)) {
                homeDiscovery = null;
            }
            if (windowState && foreground.equals(name) && root.getWindowId() == event.getWindowId()) {
                String activity = event.getClassName() == null ? "" : event.getClassName().toString();
                homeLaunch.windowChanged(name, activity, event.getWindowId(), now);
                if (homeDiscovery != null) homeDiscovery.observe(activity, event.getWindowId());
                if (homeEntryCheck != null && homeEntryCheck.route.isHomeWindow(name, activity)) {
                    finishHomeCheck(AdActionLog.State.CHANGED, "发出请求后观察到应用首页");
                }
            }
        } finally {
            root.recycle();
        }
    }

    private void setDesktopWatching(boolean watching) {
        if (desktopWatching == watching) return;
        desktopWatching = watching;
        AccessibilityServiceInfo info = getServiceInfo();
        if (info == null) return;
        info.packageNames = watching ? null : protectedPackages;
        if (watching) info.eventTypes |= AccessibilityEvent.TYPE_WINDOWS_CHANGED;
        else info.eventTypes &= ~AccessibilityEvent.TYPE_WINDOWS_CHANGED;
        setServiceInfo(info);
    }

    private boolean splashAllowed(String name) {
        return splashActive && selection.splash.contains(name);
    }

    private boolean inlineAllowed(String name) {
        return inlineActive && selection.inline.contains(name);
    }

    private boolean isAllowed(String name) {
        if (!prefs.getBoolean(AppPrefs.ACTIVE, false) || !(splashAllowed(name) || inlineAllowed(name))) return false;
        if (getPackageName().equals(name)) {
            return UnknownSplashActivity.showing;
        }
        return true;
    }

    // The built in sample page is learned too, so the second launch of the sample shows the speed up
    // that a real app gets. Other own-package windows are never recorded.
    private boolean learnable(String name) {
        return !getPackageName().equals(name) || UnknownSplashActivity.showing;
    }

    private void beginProbes() {
        lastProbeStart = SystemClock.uptimeMillis();
        handler.removeCallbacks(probe);
        for (long delay : STARTUP_PROBES_MS) handler.postDelayed(probe, delay);
        scheduleScan(0);
    }

    private void scheduleScan(long gap) {
        if (requestedPackage == null) return;
        long at = Math.max(SystemClock.uptimeMillis(), lastScan + gap);
        if (at >= scheduledAt) return;
        handler.removeCallbacks(scan);
        scheduledAt = at;
        handler.postAtTime(scan, at);
    }

    private void scanPendingWindow() {
        scheduledAt = Long.MAX_VALUE;
        lastScan = SystemClock.uptimeMillis();
        String name = requestedPackage;
        if (name == null || !isAllowed(name) || !safeDeviceState()) return;

        AccessibilityNodeInfo root = activeRoot();
        if (root == null) return;
        List<AccessibilityNodeInfo> handles = new ArrayList<>();
        handles.add(root);
        try {
            int windowId = root.getWindowId();
            RepeatGuard.Turn previousPage = repeatGuard.active();
            if (previousPage != null && root.getPackageName() != null
                    && (previousPage.window != windowId || !previousPage.page.pkg.contentEquals(root.getPackageName()))) {
                repeatGuard.leave();
                dismissRepeatPrompt();
            }
            if (!matches(root, name, windowId)) return;
            AdRules.Box viewport = applicationBounds(root);
            if (viewport == null) return;
            boolean processSplash = splashAllowed(name);
            String signature = AdMemory.signature(name, windowActivity, viewport.width(), viewport.height());
            if (processSplash && waitForHomeEntry(root, name, windowId, signature)) return;

            List<AdRules.Node> nodes = new ArrayList<>();
            nodes.add(snapshot(root, -1, viewport, false));
            boolean complete = collectChildren(handles, nodes, name, windowId, viewport,
                    SystemClock.uptimeMillis() + SCAN_BUDGET_MS);
            if (processSplash) detectCountdown(nodes);
            long sinceOpen = SystemClock.uptimeMillis() - windowOpenedAt;
            boolean damaiStartup = processSplash && DamaiSplash.startup(name, windowActivity, sinceOpen);
            boolean splashWindow = processSplash && AdRules.splashShape(nodes, viewport);
            resolvePending(AdRules.pageLike(nodes), splashWindow, SystemClock.uptimeMillis());
            // A list, a comment panel or anything scrollable is a page, not a splash. Pages only get the
            // scored rule path and the explicit corner question; never structural guessing or blind taps.
            boolean page = AdRules.pageLike(nodes);
            if (complete && splashWindow && AdRules.splashAdEvidence(nodes)) {
                splashSeen = true;
                seenSignature = signature;
                if (!hintCaptured) captureHint(nodes, viewport);
                // Remembered so the teaching screen can suggest the right app without a long search.
                if (!name.equals(prefs.getString(AppPrefs.RECENT_AD, ""))) {
                    prefs.edit().putString(AppPrefs.RECENT_AD, name).apply();
                }
            }
            List<TaughtTargets.Target> lessons = !processSplash || taught == null ? java.util.Collections.emptyList()
                    : taught.forWindow(name, windowActivity);
            boolean hasLessons = lessons.stream().anyMatch(TaughtTargets.Target::hasTemplate);
            boolean suspect = damaiStartup || sinceOpen <= 6000 && splashWindow
                    && (hasLessons || AdRules.splashAdEvidence(nodes));
            String scanRule = damaiStartup ? "大麦开屏规则" : hasLessons ? "截图教学" : "开屏控件规则";
            if (suspect) {
                noteClose(signature, windowId, 0, scanRule, "识别",
                        !complete ? "控件树未读全" : !splashWindow ? "窗口结构未通过安全检查"
                                : "本次未找到可安全点击的关闭按钮", AdActionLog.State.FAILED);
            }
            // "Not an ad" is a hard stop for this window: nothing may act on it any more.
            if (feedback != null && feedback.denied(signature)) {
                if (suspect) noteClose(signature, windowId, 4, scanRule, "防误触",
                        "此页面已被标记为不是广告", AdActionLog.State.BLOCKED);
                if (complete) clearLastTarget();
                return;
            }

            boolean tencent = processSplash && TENCENT_VIDEO.equals(name);
            boolean startup = tencentStartupWindow == windowId && lastScan < tencentStartupUntil;
            boolean tencentContext = tencent && AdRules.tencentSplashContext(nodes, startup);
            int index = tencentContext ? AdRules.chooseTencentSkip(nodes, viewport) : -1;
            if (tencentContext && index == -1) {
                appendSkipTextMatches(root, handles, nodes, name, windowId, viewport, true);
                index = AdRules.chooseTencentSkip(nodes, viewport);
            }
            boolean tencentTap = index >= 0;
            if (damaiStartup && splashWindow && !userClicked
                    && defaultDisplayWindow(root) && !focusedInput(root)) {
                appendDamaiResourceMatch(root, handles, nodes, windowId, viewport);
                index = AdRules.chooseDamaiSkip(nodes, viewport);
                if (!complete && index >= 0 && !AdRules.DAMAI_SKIP_ID.equals(nodes.get(index).id)) index = -1;
                if (index < 0 && complete) {
                    appendSkipTextMatches(root, handles, nodes, name, windowId, viewport, false);
                    index = AdRules.chooseDamaiSkip(nodes, viewport);
                }
            }
            boolean damaiTap = damaiStartup && index >= 0;
            AdRules.TaughtControl taughtControl = null;
            boolean learnedTap = false;

            boolean hintTap = false;
            boolean cornerTap = false;
            boolean structuralTap = false;

            // Reuse of learned points waits half a second after the window appears: acting on the very
            // first probe felt like the app tapping before the user could even see the ad.
            if (processSplash && index < 0 && complete && splashWindow && !page
                    && AdRules.splashAdEvidence(nodes) && sinceOpen >= 500 && sinceOpen <= 6000 && !damaiStartup) {
                // Remembered and suspected points are only used when a real control is visible at that
                // spot right now. Blind coordinate taps are gone: they are what clicked a comment row.
                AdMemory.Target remembered = memory == null ? null : memory.target(signature);
                if (remembered != null && memory.reliable(signature)) {
                    int matched = AdRules.matchHint(nodes, viewport, remembered.xRatio(),
                            remembered.yRatio(), remembered.label);
                    if (matched >= 0) {
                        index = matched;
                        learnedTap = true;
                    }
                }
                if (index < 0) {
                    AdHints.Hint hint = hints == null ? null : hints.find(signature);
                    if (hint != null) {
                        int hinted = AdRules.matchHint(nodes, viewport, hint.xRatio(), hint.yRatio(), hint.label);
                        if (hinted >= 0) {
                            index = hinted;
                            hintTap = true;
                        }
                    }
                }
            }

            if (!damaiStartup && index < 0) {
                index = AdRules.choose(nodes, viewport, processSplash, inlineAllowed(name), complete);
            }
            if (index < 0 && !lessons.isEmpty() && complete && splashWindow && !userClicked
                    && sinceOpen <= 6000 && defaultDisplayWindow(root) && !focusedInput(root)) {
                AdRules.Box display = defaultDisplayBounds();
                boolean confirmed = feedback != null && feedback.allowed(signature);
                taughtControl = AdRules.chooseTaughtControl(nodes, viewport, display, lessons, confirmed);
                if (taughtControl == null && !tencentContext && !damaiStartup) {
                    appendSkipTextMatches(root, handles, nodes, name, windowId, viewport, false);
                    taughtControl = AdRules.chooseTaughtControl(nodes, viewport, display, lessons, confirmed);
                }
                if (taughtControl != null) index = taughtControl.index;
            }
            if (processSplash && !damaiStartup && index < 0) {
                if (complete && !page) {
                    int corner = AdRules.chooseCornerSkip(nodes, viewport, ticking, splashWindow);
                    if (corner < 0 && feedback != null && feedback.allowed(signature)) {
                        // The user confirmed this window is an ad, so the corner control alone is enough.
                        corner = AdRules.cornerSkipCandidate(nodes, viewport);
                    }
                    if (corner >= 0) {
                        index = corner;
                        cornerTap = true;
                    }
                }
                // Structure alone is only trusted during the splash phase of a freshly opened window.
                if (index < 0 && complete && !page && splashWindow
                        && SystemClock.uptimeMillis() - windowOpenedAt <= 6000) {
                    int structural = AdRules.structureTarget(nodes, viewport);
                    if (structural >= 0) {
                        index = structural;
                        cornerTap = true;
                        structuralTap = true;
                    }
                }
            }

            RepeatGuard.Turn currentPage = repeatGuard.active();
            boolean trackingVisual = currentPage != null && currentPage.page.scene != null;
            if (index < 0 && complete && (!trackingVisual || !splashWindow)) notePageMissing(windowId);
            if (index < 0 && repeatPrompt != null) return;
            boolean alreadyQuestioned = currentPage != null && (currentPage.asked
                    || currentPage.page.denied(AppPrefs.deniedPages(prefs)));

            if (index < 0 && complete && splashWindow && (!acted || trackingVisual) && !userClicked
                    && confirmWindow != windowId && (damaiStartup || hasLessons)
                    && sinceOpen <= 6000 && defaultDisplayWindow(root) && !focusedInput(root)
                    && (Build.VERSION.SDK_INT >= 30 || ScreenVision.available())
                    && probeTaught(name, windowId, signature, lessons, damaiStartup)) return;
            if (index < 0 && suspect && userClicked) {
                noteClose(signature, windowId, 4, scanRule, "点击前复核",
                        "检测到用户操作，本次未执行自动点击", AdActionLog.State.BLOCKED);
            }
            if (index < 0 && suspect && (damaiStartup || hasLessons) && Build.VERSION.SDK_INT < 30
                    && !ScreenVision.available()) {
                noteClose(signature, windowId, 2, scanRule, "获取画面", "当前没有可用的屏幕捕获",
                        AdActionLog.State.UNKNOWN);
            }

            // A page with a skip-shaped corner control but no ad evidence: ask the user once, then obey.
            if (processSplash && index < 0 && complete && page && feedback != null) {
                int candidate = AdRules.cornerSkipCandidate(nodes, viewport);
                if (candidate >= 0) {
                    if (feedback.allowed(signature)) {
                        index = candidate;
                        cornerTap = true;
                    } else if (!alreadyQuestioned && !feedback.denied(signature) && confirmWindow != windowId
                            && prefs.getBoolean(AppPrefs.CONFIRM, true)) {
                        confirmWindow = windowId;
                        noteClose(signature, windowId, 4, "角落规则", "防误触", "待用户确认是否是广告",
                                AdActionLog.State.BLOCKED);
                        askUser(name, nodes.get(candidate), viewport, signature);
                        return;
                    }
                }
            }

            // A splash we could not resolve is exactly the case worth asking about: show the card over
            // the ad, and let the user settle it once.
            if (index < 0 && complete && !page && splashWindow && feedback != null && !alreadyQuestioned
                    && confirmWindow != windowId && prefs.getBoolean(AppPrefs.CONFIRM, true)
                    && !feedback.denied(signature) && !feedback.allowed(signature)) {
                int candidate = AdRules.cornerSkipCandidate(nodes, viewport);
                if (candidate >= 0) {
                    confirmWindow = windowId;
                    noteClose(signature, windowId, 4, scanRule, "防误触", "待用户确认是否是广告",
                            AdActionLog.State.BLOCKED);
                    askUser(name, nodes.get(candidate), viewport, signature);
                    return;
                }
            }

            boolean gestureTap = tencentTap || damaiTap || cornerTap || hintTap || learnedTap || taughtControl != null;
            if (index < 0 && !damaiStartup && complete && !page && splashWindow && visionWindow != windowId
                    && AdRules.splashAdEvidence(nodes)
                    && SystemClock.uptimeMillis() - windowOpenedAt <= 6000 && ScreenVision.available()) {
                visionWindow = windowId;
                requestVision(name, windowId, signature);
                return;
            }
            if (index < 0) {
                if (complete) clearLastTarget();
                return;
            }

            String reason = damaiTap ? "大麦开屏规则" : taughtControl != null ? "教学规则 · 现场控件"
                    : tencentTap ? "腾讯专项" : learnedTap ? "记住的位置" : hintTap ? "漏关学到的位置"
                    : structuralTap ? "开屏结构" : cornerTap ? "角落规则" : "通用规则";
            AccessibilityNodeInfo target = handles.get(index);
            AdRules.Node choice = nodes.get(index);
            if (!safeDeviceState() || !isAllowed(name) || !stillActive(name, windowId)
                    || ((!complete || gestureTap) && focusedInput(root))
                    || !target.refresh() || !matches(target, name, windowId)) {
                noteClose(signature, windowId, 3, reason, "点击前复核", "原窗口或关闭控件已变化，未点击",
                        AdActionLog.State.BLOCKED);
                return;
            }
            AdRules.Node refreshed = snapshot(target, choice.parent, viewport, true);
            boolean targetHolds = damaiTap ? AdRules.damaiSkipAt(refreshed, viewport)
                    : (hintTap || learnedTap || taughtControl != null)
                    ? refreshed.close > 0 && refreshed.box.isSmallControl(viewport)
                    : cornerTap
                    ? (structuralTap ? refreshed.splashEdgeIn(viewport) : refreshed.cornerSkipIn(viewport))
                    : refreshed.tencentSkipIn(viewport);
            boolean damaiResource = damaiTap && AdRules.DAMAI_SKIP_ID.equals(choice.id);
            boolean sameTarget = damaiResource ? choice.id.equals(refreshed.id) && choice.sameBoxAs(refreshed)
                    : hintTap || learnedTap ? choice.sameTarget(refreshed) : choice.sameSplashTarget(refreshed);
            if (gestureTap ? !sameTarget || !targetHolds
                    : !choice.sameControl(refreshed)) {
                noteClose(signature, windowId, 3, reason, "点击前复核", "关闭控件的位置或标签已变化",
                        AdActionLog.State.BLOCKED);
                return;
            }

            RepeatGuard.Page identity = RepeatGuard.pageOf(name, String.valueOf(root.getClassName()),
                    nodes, viewport, null);
            if (!beforeAutomaticClick(identity, windowId)) {
                noteGuard(signature, windowId, reason);
                return;
            }
            if (tencentTap && target.equals(clicks.target())) return;
            long retry = clicks.waitTime(target, SystemClock.uptimeMillis());
            if (retry != 0) {
                if (retry > 0) scheduleRetry(retry);
                else if (hintTap && hints != null) hints.missed(signature);
                return;
            }
            if (!stillActive(name, windowId) || gestureTap && (!target.isEnabled() || !defaultDisplayWindow(target))) {
                noteClose(signature, windowId, 3, reason, "点击前复核", "窗口已切换或控件不可用",
                        AdActionLog.State.BLOCKED);
                return;
            }
            TaughtTap tap = new TaughtTap(name, windowId, taughtControl == null ? null : taughtControl.lesson,
                    null, damaiTap);
            tap.control = refreshed;
            ClickMode mode = !gestureTap ? ClickMode.ACTION_ONLY
                    : damaiTap || cornerTap || taughtControl != null ? ClickMode.ACTION_FIRST : ClickMode.TOUCH_FIRST;
            boolean accepted = damaiResource ? clickDamaiResource(target, refreshed, viewport, tap)
                    : clickControl(target, refreshed, mode, tap);
            finishGuardedClick(accepted);
            String label = damaiResource ? "homepage_advert_pb" : refreshed.text.isEmpty()
                    ? refreshed.description : refreshed.text;
            recordTaughtTap(signature, reason, label, tap, accepted);
            AccessibilityNodeInfo previous = clicks.target();
            AccessibilityNodeInfo remembered = AccessibilityNodeInfo.obtain(target);
            clicks.record(remembered, SystemClock.uptimeMillis(), accepted);
            if (previous != null) previous.recycle();
            if (accepted && !getPackageName().equals(name)) {
                long count = prefs.getLong(AppPrefs.ACTIONS, 0);
                prefs.edit().putLong(AppPrefs.ACTIONS, count + 1).apply();
            }
            boolean trusted = feedback != null && feedback.allowed(signature);
            if (accepted && processSplash && (splashWindow || trusted) && memory != null && learnable(name)) {
                double x = ((refreshed.box.left + refreshed.box.right) / 2.0 - viewport.left) / viewport.width();
                double y = ((refreshed.box.top + refreshed.box.bottom) / 2.0 - viewport.top) / viewport.height();
                memory.succeeded(signature, x, y,
                        refreshed.text.isEmpty() ? refreshed.description : refreshed.text,
                        SystemClock.uptimeMillis());
            }
            if (accepted) {
                acted = true;
                if (hintTap && hints != null) hints.confirmed(signature);

            }
            retry = clicks.waitTime(target, SystemClock.uptimeMillis());
            if ((!gestureTap || cornerTap) && retry > 0) scheduleRetry(retry);
        } finally {
            for (AccessibilityNodeInfo node : handles) node.recycle();
        }
    }

    private WindowCloseTrace trace(String signature, int windowId) {
        if (closeTrace != null && closeTrace.window == windowId && closeTrace.signature.equals(signature)) {
            return closeTrace;
        }
        finishCloseTrace();
        WindowCloseTrace current = new WindowCloseTrace(signature, windowId, System.currentTimeMillis());
        closeTrace = current;
        handler.postDelayed(() -> {
            if (closeTrace == current) current.flush(actionLog);
        }, 1750);
        return current;
    }

    private void finishCloseTrace() {
        if (closeTrace != null) closeTrace.flush(actionLog);
        closeTrace = null;
    }

    private void noteClose(String signature, int windowId, int priority, String rule,
                           String stage, String message, AdActionLog.State state) {
        trace(signature, windowId).note(priority, rule, stage, message, state);
    }

    private void noteGuard(String signature, int windowId, String rule) {
        RepeatGuard.Turn turn = repeatGuard.active();
        if (turn != null && turn.clicked) return;
        String message = turn != null && turn.page.denied(AppPrefs.deniedPages(prefs))
                ? "此页面已被标记为不是广告" : "同一页面短时间内反复出现，已暂停点击";
        noteClose(signature, windowId, 5, rule, "防误触", message, AdActionLog.State.BLOCKED);
    }

    private boolean beforeAutomaticClick(RepeatGuard.Page page, int windowId) {
        return beforeAutomaticClick(page, windowId, () -> {
            visionWindow = -1;
            resetTaughtProbes();
            scheduleScan(0);
        });
    }

    private boolean beforeAutomaticClick(RepeatGuard.Page page, int windowId, Runnable onApproved) {
        long now = SystemClock.uptimeMillis();
        RepeatGuard.Turn previous = repeatGuard.active();
        repeatGuard.observe(page, windowId, now);
        RepeatGuard.Turn turn = repeatGuard.active();
        handler.removeCallbacks(repeatRescan);
        if (previous != turn) {
            if (previous != null) clearLastTarget();
            dismissRepeatPrompt();
        }
        RepeatGuard.Decision decision = repeatGuard.decision(now, AppPrefs.repeatCount(prefs),
                AppPrefs.repeatSeconds(prefs), AppPrefs.deniedPages(prefs));
        if (decision == RepeatGuard.Decision.ASK) {
            clearLastTarget();
            repeatPrompt = turn;
            boolean shown = AdPrompt.show(this, "同一页面反复出现，是广告吗？", appLabel(page.pkg)
                    + " · 已暂停自动点击。选「不是」后会记住这个页面。", ad -> {
                repeatPrompt = null;
                if (!ad) {
                    AppPrefs.setPageDenied(prefs, turn.page.encode(), true);
                } else if (repeatGuard.approve(turn)) {
                    onApproved.run();
                }
            }, () -> repeatPrompt = null);
            if (!shown) repeatPrompt = null;
        }
        return decision == RepeatGuard.Decision.ALLOW;
    }

    enum TestClick { COMPLETED, DISABLED, PAUSED, CANCELLED }

    static void resetImportedTest() {
        AdSkipService service = connected;
        if (service != null) service.clearPending();
    }

    static void clickImportedTest(ImageView image, String sourceApp, float xRatio, float yRatio,
                                  SceneFingerprint scene, Consumer<TestClick> callback) {
        AdSkipService service = connected;
        if (service == null || service.prefs == null || service.selection == null
                || !service.prefs.getBoolean(AppPrefs.ACTIVE, false)
                || !service.selection.selected().contains(service.getPackageName())) {
            callback.accept(TestClick.DISABLED);
            return;
        }
        service.tryImportedTestClick(image, sourceApp, xRatio, yRatio, scene, callback);
    }

    private void tryImportedTestClick(ImageView image, String sourceApp, float xRatio, float yRatio,
                                      SceneFingerprint scene, Consumer<TestClick> callback) {
        if (!DemoActivity.ownsTest(image) || !image.isShown() || !image.hasWindowFocus() || !safeDeviceState()) {
            callback.accept(TestClick.CANCELLED);
            return;
        }
        Drawable drawable = image.getDrawable();
        Rect localVisible = new Rect();
        if (drawable == null || drawable.getBounds().isEmpty() || !image.getLocalVisibleRect(localVisible)) {
            callback.accept(TestClick.CANCELLED);
            return;
        }
        Rect bounds = drawable.getBounds();
        float[] point = {bounds.left + bounds.width() * xRatio, bounds.top + bounds.height() * yRatio};
        image.getImageMatrix().mapPoints(point);
        point[0] += image.getPaddingLeft();
        point[1] += image.getPaddingTop();
        if (!localVisible.contains((int) point[0], (int) point[1])) {
            callback.accept(TestClick.CANCELLED);
            return;
        }
        int[] location = new int[2];
        image.getLocationOnScreen(location);
        float x = location[0] + point[0], y = location[1] + point[1];

        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) {
            callback.accept(TestClick.CANCELLED);
            return;
        }
        int windowId;
        try {
            windowId = root.getWindowId();
            AdRules.Box viewport = applicationBounds(root);
            if (!matches(root, getPackageName(), windowId) || !defaultDisplayWindow(root) || viewport == null
                    || x < viewport.left || x >= viewport.right || y < viewport.top || y >= viewport.bottom) {
                callback.accept(TestClick.CANCELLED);
                return;
            }
        } finally {
            root.recycle();
        }

        RepeatGuard.Page page = new RepeatGuard.Page(getPackageName(), "test:" + sourceApp, scene);
        if (!beforeAutomaticClick(page, windowId, () -> image.post(() ->
                clickImportedTest(image, sourceApp, xRatio, yRatio, scene, callback)))) {
            callback.accept(TestClick.PAUSED);
            return;
        }
        if (!DemoActivity.ownsTest(image) || !image.hasWindowFocus() || !stillActive(getPackageName(), windowId)) {
            callback.accept(TestClick.CANCELLED);
            return;
        }
        boolean accepted = tapAt(x, y, new GestureResultCallback() {
            @Override public void onCompleted(GestureDescription gesture) {
                callback.accept(TestClick.COMPLETED);
            }

            @Override public void onCancelled(GestureDescription gesture) {
                callback.accept(TestClick.CANCELLED);
            }
        });
        repeatGuard.finishAttempt(accepted);
        if (!accepted) callback.accept(TestClick.CANCELLED);
    }

    private void notePageMissing(int windowId) {
        RepeatGuard.Turn turn = repeatGuard.active();
        if (turn == null || turn.window != windowId) return;
        boolean firstMissing = turn.missingSince < 0;
        if (repeatGuard.missing(windowId, SystemClock.uptimeMillis())) {
            dismissRepeatPrompt();
            clearLastTarget();
            acted = false;
            visionWindow = -1;
            confirmWindow = -1;
            resetTaughtProbes();
        } else if (firstMissing) {
            handler.postDelayed(repeatRescan, RepeatGuard.ABSENCE_MS);
        }
    }

    private void finishGuardedClick(boolean accepted) {
        repeatGuard.finishAttempt(accepted);
        if (accepted) handler.postDelayed(repeatRescan, RepeatGuard.ABSENCE_MS);
    }

    private void dismissRepeatPrompt() {
        if (repeatPrompt == null) return;
        repeatPrompt = null;
        AdPrompt.dismiss();
    }

    private void askUser(String name, AdRules.Node candidate, AdRules.Box viewport, String signature) {
        String label = candidate.text.isEmpty() ? candidate.description : candidate.text;
        String detail = label.isEmpty() ? "角落有一个关闭控件" : "角落出现「" + label + "」";
        ConfirmActivity.Answer answer = ad -> handler.post(() -> {
            if (feedback == null) return;
            if (ad) {
                feedback.allow(signature);
                scheduleScan(0);
            } else {
                feedback.deny(signature);
            }
        });
        // The overlay is drawn by the service itself and shows up over the ad; the translucent activity
        // is only a fallback for the rare case the overlay cannot be added.
        if (!AdPrompt.show(this, "这个页面是广告吗？", appLabel(name) + " · " + detail,
                ad -> answer.onAnswer(ad))) {
            ConfirmActivity.ask(this, appLabel(name), detail, answer);
        }
    }

    private String appLabel(String packageName) {
        try {
            return getPackageManager().getApplicationLabel(
                    getPackageManager().getApplicationInfo(packageName, 0)).toString();
        } catch (Exception missing) {
            return packageName;
        }
    }

    private enum ClickMode { ACTION_ONLY, ACTION_FIRST, TOUCH_FIRST }

    private boolean clickControl(AccessibilityNodeInfo target, AdRules.Node node, ClickMode mode, TaughtTap tap) {
        float x = (node.box.left + node.box.right) / 2f;
        float y = (node.box.top + node.box.bottom) / 2f;
        if (mode == ClickMode.TOUCH_FIRST
                && (tap == null ? tapAt(x, y) : dispatchTaughtGesture(x, y, tap, "控件中心"))) return true;
        if (node.clickable && target.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            if (tap != null) tap.action = "已向关闭控件提交点击";
            return true;
        }
        if (mode != ClickMode.ACTION_FIRST) {
            if (tap != null) tap.action = "系统未接受关闭控件点击";
            return false;
        }
        return tap == null ? tapAt(x, y) : dispatchTaughtGesture(x, y, tap, "控件中心");
    }

    private boolean tapAt(float x, float y) {
        return tapAt(x, y, null);
    }

    private boolean tapAt(float x, float y, GestureResultCallback result) {
        Path path = new Path();
        path.moveTo(x, y);
        GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(path, 0, 40))
                .build();
        return dispatchGesture(gesture, result, result == null ? null : handler);
    }

    // A learned point is only kept while it keeps working: if the same window is still on screen
    // 2.6 s later the entry loses confidence and stops being used until it is earned again.
    private void verifyLearnedTap() {
        String signature = learnedSignature;
        int windowId = learnedWindow;
        learnedSignature = null;
        learnedWindow = -1;
        String name = requestedPackage;
        if (signature == null || name == null || memory == null) return;
        if (stillActive(name, windowId)) {
            memory.missed(signature);
        } else if (name.equals(requestedPackage) && learnable(name)) {
            memory.succeeded(signature, learnedX, learnedY, "", SystemClock.uptimeMillis());
        }
    }

    // Remembers the most plausible control of a splash window we did not act on. If that window then
    // ends by itself, closeSplash() saves it as a suspect for the next launch.
    private void captureHint(List<AdRules.Node> nodes, AdRules.Box viewport) {
        int best = -1;
        int bestScore = -1;
        for (int i = 1; i < nodes.size(); i++) {
            AdRules.Node node = nodes.get(i);
            if (!viewport.contains(node.box) || !node.box.isSmallControl(viewport)) continue;
            if (!node.splashEdgeIn(viewport)) continue;
            if (node.close <= 0 || node.isInlineOrComment()) continue;
            int score = node.close;
            if (score > bestScore) {
                bestScore = score;
                best = i;
            }
        }
        if (best < 0) return;
        AdRules.Node node = nodes.get(best);
        hintX = ((node.box.left + node.box.right) / 2.0 - viewport.left) / viewport.width();
        hintY = ((node.box.top + node.box.bottom) / 2.0 - viewport.top) / viewport.height();
        hintLabel = node.text.isEmpty() ? node.description : node.text;
        hintCaptured = true;
    }

    // A splash we did not handle is not remembered at once. It is held as a pending observation and
    // only committed when the app lands on real content within three seconds: that transition is what
    // separates "ad played through" from a crash, an error shell or a screen the user dismissed.
    private void closeSplash(long now) {
        if (splashSeen && !acted && !userClicked && hintCaptured && seenSignature != null) {
            long shown = now - windowOpenedAt;
            if (shown >= 1200 && shown <= 15000) {
                pending = new Pending(seenSignature, hintX, hintY, hintLabel, sawCountdown, now);
            }
        }
        splashSeen = false;
        acted = false;
        hintCaptured = false;
        hintLabel = "";
        sawCountdown = false;
        userClicked = false;
    }

    private void resolvePending(boolean page, boolean splashWindow, long now) {
        Pending current = pending;
        if (current == null) return;
        if (now - current.at > 3000) {
            pending = null;
            return;
        }
        if (splashWindow) return;
        if (!page) return;
        if (hints != null) {
            hints.remember(current.signature, current.x, current.y, current.label, current.strong);
        }
        pending = null;
    }

    private void detectCountdown(List<AdRules.Node> nodes) {
        for (int i = 1; i < nodes.size(); i++) {
            AdRules.Node node = nodes.get(i);
            if (node.close == 0 && !node.bareCount) continue;
            int value = AdRules.firstNumber(node.text.isEmpty() ? node.description : node.text);
            if (value < 0 || value > 60) continue;
            if (lastCount >= 0 && value < lastCount) {
                ticking = true;
                sawCountdown = true;
            }
            lastCount = value;
            return;
        }
    }

    // The accessibility tree offered nothing usable, so ask for one still frame and look for a small
    // high contrast control in a corner. Only cornerwards candidates are accepted, and the tap is
    // verified afterwards: a hit teaches the memory so later launches skip the analysis entirely.
    private void resetTaughtProbes() {
        taughtGeneration++;
        taughtAttempts = 0;
        nextTaughtProbeAt = 0;
    }

    // The user has already identified the ad in the teaching screenshot. Only its local image
    // match is trusted here. No package-wide verdict or coordinate-only tap is created.
    private boolean probeTaught(String name, int windowId, String signature, List<TaughtTargets.Target> lessons,
                               boolean damai) {
        long started = SystemClock.uptimeMillis();
        if (started < nextTaughtProbeAt) return true;
        if (taughtAttempts >= TAUGHT_PROBE_LIMIT) return false;
        String rule = damai ? "大麦开屏规则 / 截图教学" : "截图教学";
        taughtAttempts++;
        nextTaughtProbeAt = started + TAUGHT_PROBE_GAP_MS;
        handler.postDelayed(probe, TAUGHT_PROBE_GAP_MS);
        int generation = taughtGeneration;
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                takeScreenshot(Display.DEFAULT_DISPLAY, ForkJoinPool.commonPool(), new TakeScreenshotCallback() {
                    @Override public void onSuccess(ScreenshotResult screenshot) {
                        LiveScreenshotMatch frame = matchScreenshot(screenshot, lessons, damai);
                        handler.post(() -> {
                            if (frame != null) useTaughtFrame(name, windowId, signature, generation, started, frame);
                            else noteCaptureIssue(signature, windowId, generation, rule, "未取得可读取的屏幕画面");
                        });
                    }

                    @Override public void onFailure(int errorCode) {
                        handler.post(() -> noteCaptureIssue(signature, windowId, generation, rule,
                                "系统截图失败，错误码 " + errorCode));
                    }
                });
                return true;
            } catch (SecurityException unavailable) {
                noteCaptureIssue(signature, windowId, generation, rule, "系统拒绝无障碍截图");
            }
        }
        WindowManager manager = getSystemService(WindowManager.class);
        if (manager != null && ScreenVision.available()) {
            Point display = new Point();
            manager.getDefaultDisplay().getRealSize(display);
            boolean[] received = {false};
            ScreenVision.request((width, height, argb) -> {
                received[0] = true;
                if (Math.abs(width * (double) display.y - height * (double) display.x)
                        > height * display.x * .025) {
                    noteCaptureIssue(signature, windowId, generation, rule, "投屏尺寸与当前屏幕不一致");
                    return;
                }
                Bitmap bitmap = Bitmap.createBitmap(argb, width, height, Bitmap.Config.ARGB_8888);
                try {
                    LiveScreenshotMatch frame = LiveScreenshotMatch.find(bitmap, display.x, display.y, lessons, damai);
                    handler.post(() -> useTaughtFrame(name, windowId, signature, generation, started, frame));
                } finally {
                    bitmap.recycle();
                }
            });
            if (!received[0]) noteCaptureIssue(signature, windowId, generation, rule, "视觉兜底未返回画面");
        }
        return true;
    }

    private void noteCaptureIssue(String signature, int windowId, int generation, String rule, String message) {
        if (generation != taughtGeneration || closeTrace == null || closeTrace.window != windowId) return;
        noteClose(signature, windowId, 2, rule, "获取画面", message, AdActionLog.State.UNKNOWN);
    }

    private static final class TaughtTap {
        final String pkg;
        final int window;
        final TaughtTargets.Target lesson;
        final SceneFingerprint scene;
        final boolean damai;
        AdRules.Node control;
        String logId;
        String action = "";
        String verification = "";
        String stopReason = "";
        boolean attempted;
        boolean cancelled;
        AdActionLog.State state = AdActionLog.State.CHECKING;

        TaughtTap(String pkg, int window, TaughtTargets.Target lesson, SceneFingerprint scene, boolean damai) {
            this.pkg = pkg;
            this.window = window;
            this.lesson = lesson;
            this.scene = scene;
            this.damai = damai;
        }
    }

    private static LiveScreenshotMatch matchScreenshot(ScreenshotResult screenshot, List<TaughtTargets.Target> lessons,
                                                boolean damai) {
        try (HardwareBuffer buffer = screenshot.getHardwareBuffer()) {
            Bitmap hardware = Bitmap.wrapHardwareBuffer(buffer, screenshot.getColorSpace());
            if (hardware == null) return null;
            Bitmap software = null;
            try {
                software = hardware.copy(Bitmap.Config.ARGB_8888, false);
                if (software == null) return null;
                return LiveScreenshotMatch.find(software, software.getWidth(), software.getHeight(), lessons, damai);
            } finally {
                if (software != null) software.recycle();
                hardware.recycle();
            }
        } catch (RuntimeException unavailable) {
            return null;
        }
    }

    private void useTaughtFrame(String name, int windowId, String signature, int generation, long started,
                                LiveScreenshotMatch frame) {
        if (generation != taughtGeneration || !name.equals(requestedPackage)) return;
        String rule = frame.damai ? frame.lesson == null ? "大麦开屏规则" : "大麦开屏规则 + 截图教学"
                : frame.hasLessons ? "截图教学" : "大麦开屏规则";
        if (SystemClock.uptimeMillis() - started >= TAUGHT_PROBE_GAP_MS) {
            noteClose(signature, windowId, 2, rule, "获取画面", "截图返回太晚，已放弃过期画面",
                    AdActionLog.State.UNKNOWN);
            return;
        }
        if (!safeDeviceState() || !isAllowed(name) || !splashAllowed(name) || userClicked || confirmWindow == windowId
                || repeatPrompt != null || feedback != null && feedback.denied(signature)) {
            noteClose(signature, windowId, 4, rule, "点击前复核", userClicked ? "检测到用户操作，停止自动点击"
                    : "安全条件已变化或正在等待确认", AdActionLog.State.BLOCKED);
            return;
        }
        if (frame.point == null) {
            noteClose(signature, windowId, 2, rule, "识别",
                    frame.hasLessons ? "取得屏幕画面，未找到可确认的教学匹配或关闭按钮"
                            : "取得屏幕画面，未同时识别到品牌区和跳过按钮", AdActionLog.State.FAILED);
            RepeatGuard.Turn previous = repeatGuard.active();
            if (previous != null && previous.page.scene != null && frame.scene != null
                    && !frame.scene.matches(previous.page.scene) && stillActive(name, windowId)) {
                notePageMissing(windowId);
            }
            return;
        }
        if (frame.damai && !DamaiSplash.startup(name, windowActivity, SystemClock.uptimeMillis() - windowOpenedAt)) {
            noteClose(signature, windowId, 3, rule, "点击前复核", "大麦已离开开屏阶段", AdActionLog.State.BLOCKED);
            return;
        }
        TaughtTap tap = new TaughtTap(name, windowId, frame.lesson, frame.scene, frame.damai);
        String label = frame.damai ? "识别到跳过按钮" : "学习画面匹配";
        if (!clickVisualTarget(signature, rule, label, started, frame.point.x, frame.point.y, tap)) return;
        acted = true;
        visionWindow = windowId;
        if (!getPackageName().equals(name)) {
            long count = prefs.getLong(AppPrefs.ACTIONS, 0);
            prefs.edit().putLong(AppPrefs.ACTIONS, count + 1).apply();
        }
    }

    private void recordTaughtTap(String signature, String reason, String label, TaughtTap tap, boolean accepted) {
        tap.logId = trace(signature, tap.window).action(actionLog, reason, label, tap.action, accepted);
        if (tap.damai) {
            android.util.Log.i("QingTapDamai", "action " + reason + " " + label + " " + tap.action);
        }
        if (accepted) handler.postDelayed(() -> verifyTaughtTap(tap), 700);
    }

    private static final class HomeEntryCheck {
        final HomeLaunch.Route route;
        final String logId;

        HomeEntryCheck(HomeLaunch.Route route, String logId) {
            this.route = route;
            this.logId = logId;
        }
    }

    private void discoverHomeEntry(String name, long now) {
        HomeLaunch.Discovery discovery = new HomeLaunch.Discovery(name, now);
        homeDiscovery = discovery;
        ForkJoinPool.commonPool().execute(() -> {
            HomeEntryFinder.Result result = HomeEntryFinder.find(getPackageManager(), name);
            handler.post(() -> {
                if (homeDiscovery != discovery) return;
                discovery.resolved(result.launcherActivities, result.uri, result.destination);
                if (name.equals(requestedPackage)) scheduleScan(0);
            });
        });
    }

    private boolean waitForHomeEntry(AccessibilityNodeInfo root, String name, int windowId, String signature) {
        long now = SystemClock.uptimeMillis();
        long wait = homeLaunch.waitRemaining(name, windowId, now);
        if (wait == 0) wait = homeLaunch.startupWait(name, now);
        HomeLaunch.Discovery discovery = homeDiscovery;
        boolean discovering = discovery != null && discovery.pkg.equals(name);
        if (wait == 0 && discovering) wait = discovery.waitRemaining(now);
        if (wait > 0) {
            scheduleScan(wait);
            return true;
        }

        HomeLaunch.Route route;
        if (discovering) {
            homeDiscovery = null;
            route = discovery.route(windowId, now);
            if (route == null) {
                String detail = !discovery.resolved() ? "首页入口检查超时"
                        : discovery.hasEntry() ? "没有确认对应的桌面启动窗口" : "没有找到可安全调用的公开首页入口";
                writeHomeLog(signature, "", AdActionLog.State.SKIPPED, "检查入口",
                        detail + "，继续识别广告");
                return false;
            }
        } else {
            route = homeLaunch.take(name, windowId, now);
            if (route == null) {
                noteMissingDesktopLaunch(name, windowId, signature);
                return false;
            }
        }

        if (userClicked || repeatPrompt != null || !defaultDisplayWindow(root)) {
            recordHome(route, windowId, signature, AdActionLog.State.BLOCKED, "启动来源",
                    "当前窗口或用户操作不适合跳转，继续按广告规则处理");
            return false;
        }
        if (!requestHomeEntry(route, windowId, signature)) return false;
        homeLaunch.requested(route, windowId, SystemClock.uptimeMillis());
        scheduleScan(homeLaunch.waitRemaining(name, windowId, SystemClock.uptimeMillis()));
        return true;
    }

    private void noteMissingDesktopLaunch(String name, int windowId, String signature) {
        if (windowId == lastHomeLogWindow && name.equals(lastHomeLogPackage)) return;
        HomeLaunch.Route route = HomeLaunch.forPackage(name);
        if (route != null && route.hasDistinctHome() && route.launcherActivity.equals(windowActivity)) {
            recordHome(route, windowId, signature, AdActionLog.State.UNKNOWN, "启动来源",
                    "未同时确认桌面启动与启动窗口，继续按广告规则处理");
        }
    }

    private String recordHome(HomeLaunch.Route route, int windowId, String signature, AdActionLog.State state,
                              String stage, String detail) {
        lastHomeLogWindow = windowId;
        lastHomeLogPackage = route.pkg;
        return writeHomeLog(signature, route.uri, state, stage, detail);
    }

    private String writeHomeLog(String signature, String uri, AdActionLog.State state, String stage, String detail) {
        return actionLog == null ? "" : actionLog.add(signature, HOME_RULE, uri,
                System.currentTimeMillis(), detail, state, stage);
    }

    private void finishHomeCheck(AdActionLog.State state, String detail) {
        HomeEntryCheck check = homeEntryCheck;
        if (check == null) return;
        homeEntryCheck = null;
        handler.removeCallbacks(homeCheckTimeout);
        if (actionLog != null) {
            actionLog.update(check.logId, null, null, detail, state, "首页核对");
        }
    }

    private boolean requestHomeEntry(HomeLaunch.Route route, int windowId, String signature) {
        Intent home = new Intent(Intent.ACTION_VIEW, Uri.parse(route.uri))
                .setPackage(route.pkg)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        ResolveInfo resolved = getPackageManager().resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY);
        if (resolved == null || resolved.activityInfo == null || !resolved.activityInfo.exported
                || !route.pkg.equals(resolved.activityInfo.packageName)
                || !route.destination.equals(resolved.activityInfo.name)) {
            recordHome(route, windowId, signature, AdActionLog.State.FAILED, "解析入口",
                    "当前版本没有匹配的可用首页入口，继续按广告规则处理");
            android.util.Log.i(HOME_LOG, route.pkg + " homepage unavailable; scanning current screen");
            return false;
        }
        if (!stillActive(route.pkg, windowId)) {
            recordHome(route, windowId, signature, AdActionLog.State.BLOCKED, "发起请求",
                    "发起首页请求前，原启动窗口已切换");
            return false;
        }
        try {
            startActivity(home.setClassName(route.pkg, route.destination));
            android.util.Log.i(HOME_LOG, route.pkg + " requested " + route.destination);
            finishHomeCheck(AdActionLog.State.UNKNOWN, "新启动已开始，前一次首页未核对");
            if (route.hasDistinctHome()) {
                String logId = recordHome(route, windowId, signature, AdActionLog.State.CHECKING, "发起请求",
                        "已发送首页请求，等待核对");
                homeEntryCheck = new HomeEntryCheck(route, logId);
                handler.postDelayed(homeCheckTimeout, HOME_CHECK_MS);
            } else {
                recordHome(route, windowId, signature, AdActionLog.State.UNKNOWN, "发起请求",
                        route.homeActivity.isEmpty() ? "已发送公开首页请求，继续检查当前画面"
                                : "已发送首页请求，开屏与首页共用窗口，继续按广告规则检查画面");
            }
            return true;
        } catch (ActivityNotFoundException | SecurityException unavailable) {
            recordHome(route, windowId, signature, AdActionLog.State.FAILED, "发起请求",
                    "系统未接受首页请求，继续按广告规则处理");
            android.util.Log.i(HOME_LOG, route.pkg + " homepage request rejected; scanning current screen");
            return false;
        }
    }

    private boolean clickVisualTarget(String signature, String rule, String label, long started,
                                      float x, float y, TaughtTap tap) {
        boolean accepted = tryVisualClick(signature, rule, started, x, y, tap);
        if (tap.attempted) {
            recordTaughtTap(signature, rule, label + " · " + Math.round(x) + "," + Math.round(y), tap, accepted);
        } else if (!tap.stopReason.isEmpty()) {
            noteClose(signature, tap.window, 3, rule, "点击前复核", tap.stopReason, AdActionLog.State.BLOCKED);
        }
        return accepted;
    }

    private boolean tryVisualClick(String signature, String rule, long started, float x, float y, TaughtTap tap) {
        String name = tap.pkg;
        int windowId = tap.window;
        AccessibilityNodeInfo root = activeRoot();
        if (root == null) return stopTap(tap, "未取得前台窗口");
        List<AccessibilityNodeInfo> handles = new ArrayList<>();
        handles.add(root);
        try {
            if (!matches(root, name, windowId) || !defaultDisplayWindow(root)) {
                return stopTap(tap, "原窗口已切换或不在主屏幕");
            }
            if (root.isScrollable() || focusedInput(root)) return stopTap(tap, "页面可滚动或正在输入");
            AdRules.Box bounds = applicationBounds(root);
            if (bounds == null || x < bounds.left || x >= bounds.right || y < bounds.top || y >= bounds.bottom) {
                return stopTap(tap, "识别坐标不在当前应用窗口内");
            }
            List<AdRules.Node> nodes = new ArrayList<>();
            nodes.add(snapshot(root, -1, bounds, false));
            boolean complete = collectChildren(handles, nodes, name, windowId, bounds,
                    Math.min(started + TAUGHT_PROBE_GAP_MS, SystemClock.uptimeMillis() + 35));
            if (!complete) return stopTap(tap, "控件树未读全，未执行点击");
            if (!AdRules.splashShape(nodes, bounds)) return stopTap(tap, "窗口结构未通过开屏安全检查");
            if (!tap.damai && tap.lesson == null && !AdRules.visualFallbackAllowed(nodes, bounds, x, y)) {
                return stopTap(tap, "角落图形缺少现场广告关闭控件，未执行点击");
            }
            if (tap.damai) appendDamaiResourceMatch(root, handles, nodes, windowId, bounds);
            if ((tap.damai || tap.lesson != null) && AdRules.taughtDismissNear(nodes, bounds, x, y) < 0) {
                appendSkipTextMatches(root, handles, nodes, name, windowId, bounds, false);
            }
            if (SystemClock.uptimeMillis() - started >= TAUGHT_PROBE_GAP_MS || !stillActive(name, windowId)) {
                return stopTap(tap, "画面已过期或原窗口已切换");
            }
            if (!beforeAutomaticClick(RepeatGuard.pageOf(name, String.valueOf(root.getClassName()),
                    nodes, bounds, tap.scene), windowId)) {
                noteGuard(signature, windowId, rule);
                return false;
            }
            if (!stillActive(name, windowId)) return stopTap(tap, "点击前原窗口已切换");
            boolean accepted = dispatchTaughtTap(tap, handles, nodes, bounds, x, y);
            finishGuardedClick(accepted);
            return accepted;
        } finally {
            for (AccessibilityNodeInfo node : handles) node.recycle();
        }
    }

    private static boolean stopTap(TaughtTap tap, String reason) {
        tap.stopReason = reason;
        return false;
    }

    private boolean dispatchTaughtTap(TaughtTap tap, List<AccessibilityNodeInfo> handles,
                                      List<AdRules.Node> nodes, AdRules.Box viewport, float x, float y) {
        int damaiIndex = tap.damai ? AdRules.chooseDamaiSkip(nodes, viewport) : -1;
        if (damaiIndex >= 0 && AdRules.DAMAI_SKIP_ID.equals(nodes.get(damaiIndex).id)) {
            AdRules.Node original = nodes.get(damaiIndex);
            AccessibilityNodeInfo control = handles.get(damaiIndex);
            if (control.refresh() && matches(control, tap.pkg, tap.window) && control.isEnabled()) {
                AdRules.Node current = snapshot(control, original.parent, viewport, true);
                if (AdRules.DAMAI_SKIP_ID.equals(current.id) && AdRules.damaiSkipAt(current, viewport)
                        && Math.abs(x - (current.box.left + current.box.right) / 2f) <= viewport.width() * .035f
                        && Math.abs(y - (current.box.top + current.box.bottom) / 2f) <= viewport.height() * .025f) {
                    if (!stillActive(tap.pkg, tap.window)) return stopTap(tap, "点击前原窗口已切换");
                    tap.control = current;
                    tap.attempted = true;
                    return clickDamaiResource(control, current, viewport, tap);
                }
            }
        }
        int index = AdRules.taughtDismissNear(nodes, viewport, x, y);
        if (index >= 0) {
            AccessibilityNodeInfo control = handles.get(index);
            AdRules.Node original = nodes.get(index);
            if (control.refresh() && matches(control, tap.pkg, tap.window) && control.isEnabled()) {
                AdRules.Node current = snapshot(control, original.parent, viewport, true);
                if (current.sameBoxAs(original) && AdRules.taughtDismissAt(current, viewport, x, y)) {
                    if (!stillActive(tap.pkg, tap.window)) return stopTap(tap, "点击前原窗口已切换");
                    tap.control = current;
                    tap.attempted = true;
                    return clickControl(control, current, ClickMode.ACTION_FIRST, tap);
                }
            }
            return stopTap(tap, "现场关闭控件已变化，未执行坐标点击");
        }
        if (!tap.damai && tap.lesson == null) return stopTap(tap, "未找到可复核的现场关闭控件");
        if (!stillActive(tap.pkg, tap.window)) return stopTap(tap, "点击前原窗口已切换");
        tap.attempted = true;
        return dispatchTaughtGesture(x, y, tap, "截图坐标");
    }

    private boolean clickDamaiResource(AccessibilityNodeInfo control, AdRules.Node node, AdRules.Box viewport,
                                       TaughtTap tap) {
        if (node.clickable && control.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            tap.action = "跳过资源控件 · 系统已接收点击";
            return true;
        }
        AccessibilityNodeInfo parent = control.getParent();
        if (parent != null) {
            try {
                if (matches(parent, tap.pkg, tap.window) && parent.isEnabled() && parent.isVisibleToUser()
                        && AdRules.damaiSkipParent(node, snapshot(parent, -1, viewport, true), viewport)
                        && stillActive(tap.pkg, tap.window)
                        && parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    tap.action = "跳过资源控件的父控件 · 系统已接收点击";
                    return true;
                }
            } finally {
                parent.recycle();
            }
        }
        if (!stillActive(tap.pkg, tap.window)) {
            tap.action = "跳过资源控件已离开前台";
            return false;
        }
        return dispatchTaughtGesture((node.box.left + node.box.right) / 2f,
                (node.box.top + node.box.bottom) / 2f, tap, "跳过资源控件中心");
    }

    private boolean dispatchTaughtGesture(float x, float y, TaughtTap tap, String target) {
        tap.action = target + " · 手势请求已提交";
        boolean submitted = tapAt(x, y, new GestureResultCallback() {
            @Override public void onCompleted(GestureDescription gesture) {
                tap.action = target + " · 手势已执行";
                writeTaughtTap(tap);
            }

            @Override public void onCancelled(GestureDescription gesture) {
                tap.cancelled = true;
                tap.action = target + " · 系统取消了点击手势";
                tap.state = AdActionLog.State.FAILED;
                writeTaughtTap(tap);
            }
        });
        if (!submitted) tap.action = target + " · 系统拒绝提交点击手势";
        return submitted;
    }

    private void updateTaughtTap(TaughtTap tap, AdActionLog.State state, String detail) {
        if (tap.cancelled) return;
        tap.state = state;
        tap.verification = detail;
        if (tap.damai) android.util.Log.i("QingTapDamai", "verify " + state + " " + detail);
        writeTaughtTap(tap);
    }

    private void writeTaughtTap(TaughtTap tap) {
        if (connected == this && actionLog != null) {
            String outcome = tap.action + (tap.verification.isEmpty() || tap.cancelled ? "" : " · " + tap.verification);
            actionLog.update(tap.logId, null, null, outcome, tap.state,
                    tap.cancelled || tap.verification.isEmpty() ? "点击" : "点击后核对");
        }
    }

    private enum WindowStatus { SAME, CHANGED, UNAVAILABLE }

    private WindowStatus currentWindow(TaughtTap tap) {
        AccessibilityNodeInfo root = activeRoot();
        if (root == null) return WindowStatus.UNAVAILABLE;
        try {
            return matches(root, tap.pkg, tap.window) ? WindowStatus.SAME : WindowStatus.CHANGED;
        } finally {
            root.recycle();
        }
    }

    private boolean verifySameWindow(TaughtTap tap) {
        if (connected != this || tap.cancelled) return false;
        WindowStatus current = currentWindow(tap);
        if (current == WindowStatus.SAME) return true;
        if (current == WindowStatus.CHANGED) {
            updateTaughtTap(tap, AdActionLog.State.CHANGED, "已观察到前台窗口切换，关闭原因无法确认");
        } else {
            updateTaughtTap(tap, AdActionLog.State.UNKNOWN, "无法读取点击后的前台窗口");
        }
        return false;
    }

    private void verifyTaughtTap(TaughtTap tap) {
        if (!verifySameWindow(tap)) return;
        if (tap.scene == null && !tap.damai) {
            verifyNativeControl(tap);
            return;
        }
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                takeScreenshot(Display.DEFAULT_DISPLAY, ForkJoinPool.commonPool(), new TakeScreenshotCallback() {
                    @Override public void onSuccess(ScreenshotResult screenshot) {
                        LiveScreenshotMatch frame = matchScreenshot(screenshot, java.util.Collections.emptyList(), tap.damai);
                        handler.post(() -> {
                            if (frame == null) reportUnverified(tap, "未取得可读取的核对截图");
                            else verifyVisualState(tap, frame.scene, frame.point != null);
                        });
                    }

                    @Override public void onFailure(int errorCode) {
                        handler.post(() -> reportUnverified(tap, "系统未返回核对截图，错误码 " + errorCode));
                    }
                });
                return;
            } catch (SecurityException unavailable) {
                if (!ScreenVision.available()) {
                    reportUnverified(tap, "系统拒绝核对截图");
                    return;
                }
            }
        }
        if (ScreenVision.available()) {
            boolean[] received = {false};
            ScreenVision.request((width, height, pixels) -> {
                received[0] = true;
                verifyVisualState(tap, SceneFingerprint.of(pixels, width, height),
                        tap.damai && DamaiSplash.find(pixels, width, height) != null);
            });
            if (received[0]) return;
        }
        reportUnverified(tap, "点击后没有可用画面");
    }

    private void reportUnverified(TaughtTap tap, String detail) {
        if (verifySameWindow(tap)) updateTaughtTap(tap, AdActionLog.State.UNKNOWN, detail);
    }

    private void verifyVisualState(TaughtTap tap, SceneFingerprint current, boolean damaiVisible) {
        if (!verifySameWindow(tap)) return;
        if (current == null) {
            updateTaughtTap(tap, AdActionLog.State.UNKNOWN, "核对画面无效");
            return;
        }
        boolean stillVisible = tap.damai ? damaiVisible : tap.scene.matches(current);
        if (stillVisible) {
            updateTaughtTap(tap, AdActionLog.State.FAILED,
                    tap.damai ? "点击后仍看到大麦开屏和跳过按钮" : "点击后仍匹配原广告画面");
            if (taught != null) taught.missed(tap.lesson);
        } else {
            updateTaughtTap(tap, AdActionLog.State.CHANGED,
                    tap.damai ? "核对画面已无大麦开屏特征" : "核对画面已不匹配原广告");
            if (taught != null) taught.confirmed(tap.lesson);
        }
    }

    private void verifyNativeControl(TaughtTap tap) {
        AccessibilityNodeInfo root = activeRoot();
        if (root == null) {
            reportUnverified(tap, "未取得核对控件树");
            return;
        }
        List<AccessibilityNodeInfo> handles = new ArrayList<>();
        handles.add(root);
        try {
            if (!matches(root, tap.pkg, tap.window)) {
                verifySameWindow(tap);
                return;
            }
            AdRules.Box bounds = applicationBounds(root);
            if (bounds == null || tap.control == null) {
                reportUnverified(tap, "原控件无法复核");
                return;
            }
            List<AdRules.Node> nodes = new ArrayList<>();
            nodes.add(snapshot(root, -1, bounds, false));
            boolean complete = collectChildren(handles, nodes, tap.pkg, tap.window, bounds,
                    SystemClock.uptimeMillis() + 45);
            if (!verifySameWindow(tap)) return;
            for (AdRules.Node node : nodes) {
                if (!tap.control.sameSplashTarget(node)) continue;
                updateTaughtTap(tap, AdActionLog.State.FAILED, "点击后同一关闭控件仍在");
                if (taught != null) taught.missed(tap.lesson);
                return;
            }
            if (!complete) {
                updateTaughtTap(tap, AdActionLog.State.UNKNOWN, "核对控件树未读全");
                return;
            }
            updateTaughtTap(tap, AdActionLog.State.CHANGED, "核对时未再找到原关闭控件");
            if (taught != null) taught.confirmed(tap.lesson);
        } finally {
            for (AccessibilityNodeInfo handle : handles) handle.recycle();
        }
    }

    private void requestVision(String name, int windowId, String signature) {
        WindowManager manager = getSystemService(WindowManager.class);
        if (manager == null) return;
        Point display = new Point();
        manager.getDefaultDisplay().getRealSize(display);
        long started = SystemClock.uptimeMillis();
        int generation = taughtGeneration;
        ScreenVision.request((width, height, argb) -> {
            if (Math.abs(width * (double) display.y - height * (double) display.x)
                    > height * display.x * .025) return;
            VisionFinder.Point point = VisionFinder.find(argb, width, height);
            if (point == null) return;
            double xRatio = point.x / (double) width;
            double yRatio = point.y / (double) height;
            SceneFingerprint scene = SceneFingerprint.of(argb, width, height);
            handler.post(() -> tapVision(name, windowId, signature, xRatio, yRatio,
                    (float) (display.x * xRatio), (float) (display.y * yRatio), generation, started, scene));
        });
    }

    private void tapVision(String name, int windowId, String signature, double xRatio, double yRatio,
                           float x, float y, int generation, long started, SceneFingerprint scene) {
        if (generation != taughtGeneration || !name.equals(requestedPackage) || !safeDeviceState()
                || !isAllowed(name) || !splashAllowed(name) || userClicked || repeatPrompt != null
                || (feedback != null && feedback.denied(signature))) return;
        TaughtTap tap = new TaughtTap(name, windowId, null, scene, false);
        if (!clickVisualTarget(signature, "视觉兜底", "角落画面候选", started, x, y, tap)) return;
        acted = true;
        visionSignature = signature;
        visionWindowId = windowId;
        visionX = xRatio;
        visionY = yRatio;
        handler.removeCallbacks(visionCheck);
        handler.postDelayed(visionCheck, 900);
    }

    private void verifyVisionTap() {
        String signature = visionSignature;
        int windowId = visionWindowId;
        visionSignature = null;
        visionWindowId = -1;
        String name = requestedPackage;
        if (signature == null || name == null) return;
        boolean gone = !stillActive(name, windowId);
        if (!gone || memory == null) return;
        if (name.equals(requestedPackage) && learnable(name)) {
            memory.succeeded(signature, visionX, visionY, "vision", SystemClock.uptimeMillis());
            long count = prefs.getLong(AppPrefs.ACTIONS, 0);
            prefs.edit().putLong(AppPrefs.ACTIONS, count + 1).apply();
        }
    }

    private void appendDamaiResourceMatch(AccessibilityNodeInfo root, List<AccessibilityNodeInfo> handles,
                                          List<AdRules.Node> nodes, int windowId, AdRules.Box viewport) {
        List<AccessibilityNodeInfo> matches = root.findAccessibilityNodeInfosByViewId(AdRules.DAMAI_SKIP_ID);
        try {
            if (matches.size() != 1) return;
            AccessibilityNodeInfo match = matches.get(0);
            if (!matches(match, DamaiSplash.PACKAGE, windowId) || !match.isEnabled() || !match.isVisibleToUser()
                    || handles.contains(match) || !insideRootOutsideInline(match, root, viewport)) return;
            AdRules.Node node = snapshot(match, 0, viewport, true);
            if (AdRules.damaiSkipAt(node, viewport)) {
                handles.add(AccessibilityNodeInfo.obtain(match));
                nodes.add(node);
            }
        } finally {
            for (AccessibilityNodeInfo match : matches) match.recycle();
        }
    }

    private void appendSkipTextMatches(AccessibilityNodeInfo root, List<AccessibilityNodeInfo> handles,
                                       List<AdRules.Node> nodes, String name, int windowId,
                                       AdRules.Box viewport, boolean tencent) {
        List<AccessibilityNodeInfo> results = root.findAccessibilityNodeInfosByText("跳过");
        try {
            if (results.size() > 8) return;
            for (AccessibilityNodeInfo match : results) {
                if (!matches(match, name, windowId) || handles.contains(match) || !match.isEnabled()
                        || !match.isVisibleToUser()) continue;
                AdRules.Node node = snapshot(match, 0, viewport, false);
                if ((tencent ? node.tencentSkipIn(viewport) : node.cornerSkipIn(viewport))
                        && insideRootOutsideInline(match, root, viewport)) {
                    handles.add(AccessibilityNodeInfo.obtain(match));
                    nodes.add(node);
                }
            }
        } finally {
            for (AccessibilityNodeInfo match : results) match.recycle();
        }
    }

    private AdRules.Box defaultDisplayBounds() {
        WindowManager manager = getSystemService(WindowManager.class);
        if (manager == null) return null;
        Point size = new Point();
        manager.getDefaultDisplay().getRealSize(size);
        return size.x > 0 && size.y > 0 ? new AdRules.Box(0, 0, size.x, size.y) : null;
    }

    private boolean insideRootOutsideInline(AccessibilityNodeInfo match, AccessibilityNodeInfo root,
                                             AdRules.Box viewport) {
        AccessibilityNodeInfo parent = match.getParent();
        for (int depth = 0; parent != null && depth < 18; depth++) {
            AccessibilityNodeInfo next;
            try {
                if (snapshot(parent, -1, viewport, false).isInlineOrComment()) return false;
                if (parent.equals(root)) return true;
                next = parent.getParent();
            } finally {
                parent.recycle();
            }
            parent = next;
        }
        if (parent != null) parent.recycle();
        return false;
    }

    private static boolean defaultDisplayWindow(AccessibilityNodeInfo target) {
        if (Build.VERSION.SDK_INT < 30) return true;
        AccessibilityWindowInfo window = target.getWindow();
        if (window == null) return false;
        try {
            return window.getDisplayId() == Display.DEFAULT_DISPLAY;
        } finally {
            window.recycle();
        }
    }

    private AccessibilityNodeInfo activeRoot() {
        if (Build.VERSION.SDK_INT >= 33) {
            return getRootInActiveWindow(AccessibilityNodeInfo.FLAG_PREFETCH_DESCENDANTS_BREADTH_FIRST);
        }
        return getRootInActiveWindow();
    }

    private boolean collectChildren(List<AccessibilityNodeInfo> handles, List<AdRules.Node> nodes,
                                    String name, int windowId, AdRules.Box viewport, long deadline) {
        for (int index = 0; index < handles.size(); index++) {
            AccessibilityNodeInfo parent = handles.get(index);
            if (parent.isEditable() || parent.isPassword()) continue;
            // Overlays are often drawn after the page behind them. Visit later siblings first.
            for (int childIndex = parent.getChildCount() - 1; childIndex >= 0; childIndex--) {
                if (SystemClock.uptimeMillis() >= deadline || handles.size() >= MAX_NODES) return false;
                AccessibilityNodeInfo child = parent.getChild(childIndex);
                if (child == null) continue;
                if (!matches(child, name, windowId) || !child.isVisibleToUser()) {
                    child.recycle();
                    continue;
                }
                handles.add(child);
                nodes.add(snapshot(child, index, viewport, true));
            }
        }
        return true;
    }

    private AdRules.Node snapshot(AccessibilityNodeInfo node, int parent, AdRules.Box viewport, boolean readLabel) {
        Rect bounds = new Rect();
        node.getBoundsInScreen(bounds);
        AdRules.Box box = new AdRules.Box(bounds.left, bounds.top, bounds.right, bounds.bottom);
        boolean isInput = node.isEditable() || node.isPassword();
        boolean screenElement = node.isVisibleToUser() && bounds.width() > 0 && bounds.height() > 0
                && bounds.right > viewport.left && bounds.left < viewport.right
                && bounds.bottom > viewport.top && bounds.top < viewport.bottom;
        if (isInput || !screenElement) {
            return new AdRules.Node(parent, box, false, node.isScrollable(),
                    screenElement && isInput && (node.isPassword() || node.isFocused()), null, null, null);
        }
        boolean clickable = node.isEnabled() && (node.getActions() & AccessibilityNodeInfo.ACTION_CLICK) != 0;
        CharSequence text = node.getText();
        CharSequence description = node.getContentDescription();
        if (readLabel && clickable && empty(text) && empty(description) && box.isSmallControl(viewport)) {
            text = childCloseLabel(node, bounds);
        }
        return new AdRules.Node(parent, box, clickable, node.isScrollable(), false,
                node.getViewIdResourceName(), text, description);
    }

    private CharSequence childCloseLabel(AccessibilityNodeInfo parent, Rect parentBounds) {
        int count = parent.getChildCount();
        if (count < 1 || count > 2) return null;
        CharSequence label = null;
        CharSequence countdown = null;
        String name = parent.getPackageName().toString();
        int windowId = parent.getWindowId();
        Rect childBounds = new Rect();
        for (int i = 0; i < count; i++) {
            AccessibilityNodeInfo child = parent.getChild(i);
            if (child == null) return null;
            try {
                child.getBoundsInScreen(childBounds);
                if (!matches(child, name, windowId) || !child.isVisibleToUser() || child.isEditable()
                        || child.isPassword() || (child.getActions() & AccessibilityNodeInfo.ACTION_CLICK) != 0
                        || child.getChildCount() != 0 || childBounds.isEmpty()
                        || !parentBounds.contains(childBounds)) return null;
                CharSequence text = child.getText();
                CharSequence description = child.getContentDescription();
                if (!empty(text) && !empty(description)
                        && !AdRules.clean(text).equals(AdRules.clean(description))) return null;
                CharSequence value = empty(text) ? description : text;
                if (label == null && AdRules.isCloseLabel(value)) label = value;
                else if (countdown == null && AdRules.isCountdownNumber(value)) countdown = value;
                else return null;
            } finally {
                child.recycle();
            }
        }
        if (label == null || countdown == null) return label;
        String combined = countdown + " " + label;
        return AdRules.isCloseLabel(combined) ? combined : null;
    }

    private static boolean empty(CharSequence value) {
        return value == null || value.length() == 0;
    }

    private static boolean focusedInput(AccessibilityNodeInfo root) {
        AccessibilityNodeInfo focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        if (focused == null) return false;
        try {
            return focused.isEditable() || focused.isPassword();
        } finally {
            focused.recycle();
        }
    }

    private AdRules.Box applicationBounds(AccessibilityNodeInfo root) {
        AccessibilityWindowInfo window = root.getWindow();
        if (window == null) return null;
        try {
            if (window.getType() != AccessibilityWindowInfo.TYPE_APPLICATION
                    || (!window.isActive() && !window.isFocused())) return null;
            Rect bounds = new Rect();
            window.getBoundsInScreen(bounds);
            if (bounds.isEmpty()) return null;
            return new AdRules.Box(bounds.left, bounds.top, bounds.right, bounds.bottom);
        } finally {
            window.recycle();
        }
    }

    private boolean stillActive(String name, int windowId) {
        AccessibilityNodeInfo current = Build.VERSION.SDK_INT >= 33 ? getRootInActiveWindow(0)
                : getRootInActiveWindow();
        if (current == null) return false;
        try {
            return matches(current, name, windowId);
        } finally {
            current.recycle();
        }
    }

    private boolean isForegroundPackage(String name) {
        AccessibilityNodeInfo current = getRootInActiveWindow();
        if (current == null) return false;
        try {
            return current.getPackageName() != null && name.contentEquals(current.getPackageName());
        } finally {
            current.recycle();
        }
    }

    private static boolean matches(AccessibilityNodeInfo node, String name, int windowId) {
        return node.getWindowId() == windowId && node.getPackageName() != null
                && name.contentEquals(node.getPackageName());
    }

    private boolean safeDeviceState() {
        return keyguard != null && !keyguard.isKeyguardLocked() && power != null && power.isInteractive();
    }

    private void clearLastTarget() {
        handler.removeCallbacks(retryProbe);
        AccessibilityNodeInfo previous = clicks.target();
        clicks.clear();
        if (previous != null) previous.recycle();
    }

    private void scheduleRetry(long delay) {
        handler.removeCallbacks(retryProbe);
        handler.postDelayed(retryProbe, delay);
    }

    private void clearPending() {
        finishCloseTrace();
        closeSplash(SystemClock.uptimeMillis());
        handler.removeCallbacks(scan);
        handler.removeCallbacks(probe);
        handler.removeCallbacks(learnedCheck);
        handler.removeCallbacks(visionCheck);
        handler.removeCallbacks(repeatRescan);
        repeatGuard.leave();
        dismissRepeatPrompt();
        learnedSignature = null;
        learnedWindow = -1;
        visionWindow = -1;
        confirmWindow = -1;
        visionSignature = null;
        pending = null;
        userClicked = false;
        sawCountdown = false;
        requestedPackage = null;
        requestedEventWindow = -1;
        homeLaunch.reset();
        homeDiscovery = null;
        finishHomeCheck(AdActionLog.State.UNKNOWN, "核对首页前，前台窗口或服务状态已切换");
        lastHomeLogWindow = -1;
        lastHomeLogPackage = "";
        setDesktopWatching(false);
        tencentStartupUntil = 0;
        windowActivity = "";
        ticking = false;
        lastCount = -1;
        scheduledAt = Long.MAX_VALUE;
        resetTaughtProbes();
        clearLastTarget();
    }

    @Override public void onInterrupt() {
        clearPending();
    }

    @Override public void onDestroy() {
        AdPrompt.dismiss();
        if (connected == this) connected = null;
        clearPending();
        if (prefs != null) prefs.unregisterOnSharedPreferenceChangeListener(preferencesChanged);
        super.onDestroy();
    }
}
