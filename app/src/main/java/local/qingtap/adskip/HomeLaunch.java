package local.qingtap.adskip;

import java.util.Set;
import java.util.regex.Pattern;

// Historical route helpers retained for compatibility tests. Since r51 the accessibility service
// does not invoke them: external home intents lose cloned-profile identity.
// Consume the first app transition from the desktop. A re-entry caused by our own request
// cannot create another candidate, even when its destination is the same startup activity.
final class HomeLaunch {
    private static final long STARTUP_MS = 1800;
    private static final long SETTLE_MS = 300;
    private static final Pattern CUSTOM_SCHEME = Pattern.compile("[a-z][a-z0-9+.-]*");
    private static final Pattern HOST = Pattern.compile("[a-zA-Z0-9-]+(?:\\.[a-zA-Z0-9-]+)*");

    static final class Route {
        final String pkg;
        final String launcherActivity;
        final String uri;
        final String destination;
        final String homeActivity;

        Route(String pkg, String launcherActivity, String uri, String destination, String homeActivity) {
            this.pkg = pkg;
            this.launcherActivity = launcherActivity;
            this.uri = uri;
            this.destination = destination;
            this.homeActivity = homeActivity;
        }

        boolean hasDistinctHome() {
            return !homeActivity.isEmpty() && !homeActivity.equals(launcherActivity);
        }

        boolean isHomeWindow(String foreground, String activity) {
            return hasDistinctHome() && pkg.equals(foreground) && homeActivity.equals(activity);
        }
    }

    private static final Route[] ROUTES = {
        new Route("cn.damai", "cn.damai.launcher.splash.SplashMainActivity",
                "damai://home", "cn.damai.homepage.MainActivity", "cn.damai.homepage.MainActivity"),
        new Route("tv.danmaku.bili", "tv.danmaku.bili.MainActivityV2",
                "bilibili://home", "tv.danmaku.bili.ui.intent.IntentHandlerActivity",
                "tv.danmaku.bili.MainActivityV2"),
        new Route("com.cmcc.cmvideo", "com.cmcc.cmvideo.splash.SplashActivity",
                "miguvideo://miguvideo", "com.cmcc.cmvideo.splash.SplashActivity",
                "com.cmcc.cmvideo.main.application.CompatibleMainActivity")
    };

    static String publicHomeUri(String scheme, String host, String path) {
        if (scheme == null || host == null || !CUSTOM_SCHEME.matcher(scheme).matches()
                || !HOST.matcher(host).matches()) return null;
        switch (scheme) {
            case "http": case "https": case "file": case "content": case "intent": case "android-app":
            case "android": case "market": case "tel": case "sms": case "mailto": case "javascript":
                return null;
            default:
                break;
        }
        String suffix = path == null ? "" : path;
        boolean homeHost = "home".equals(host) || "homepage".equals(host) || "main".equals(host);
        boolean rootPath = suffix.isEmpty() || "/".equals(suffix);
        boolean homePath = "/home".equals(suffix) || "/homepage".equals(suffix) || "/main".equals(suffix);
        return homeHost && rootPath || homePath ? scheme + "://" + host + suffix : null;
    }

    static Route forPackage(String pkg) {
        for (Route route : ROUTES) {
            if (route.pkg.equals(pkg)) return route;
        }
        return null;
    }

    static final class Discovery {
        private static final long SEARCH_MS = 180;
        private static final long WINDOW_MS = 450;
        final String pkg;
        private final long startedAt;
        private String activity = "";
        private int window = -1;
        private Set<String> launchers;
        private String uri = "";
        private String destination = "";

        Discovery(String pkg, long startedAt) {
            this.pkg = pkg;
            this.startedAt = startedAt;
        }

        void observe(String name, int windowId) {
            if (!name.isEmpty() && !name.startsWith("android.")) {
                activity = name;
                window = windowId;
            }
        }

        void resolved(Set<String> launcherActivities, String uri, String destination) {
            launchers = launcherActivities;
            this.uri = uri;
            this.destination = destination;
        }

        long waitRemaining(long now) {
            if (launchers == null) return Math.max(0, startedAt + SEARCH_MS - now);
            if (!uri.isEmpty() && activity.isEmpty()) return Math.max(0, startedAt + WINDOW_MS - now);
            return 0;
        }

        Route route(int windowId, long now) {
            if (uri.isEmpty() || window != windowId || now - startedAt > WINDOW_MS
                    || !launchers.contains(activity)) return null;
            return new Route(pkg, activity, uri, destination, "");
        }

        boolean resolved() {
            return launchers != null;
        }

        boolean hasEntry() {
            return !uri.isEmpty();
        }
    }

    private Route candidate;
    private long startedAt;
    private int candidateWindow = -1;
    private String requestedPackage;
    private int requestedWindow = -1;
    private long settleUntil;

    void fromDesktop(String pkg, long now) {
        reset();
        candidate = forPackage(pkg);
        startedAt = now;
    }

    void windowChanged(String pkg, String activity, int window, long now) {
        if (requestedPackage != null && (!requestedPackage.equals(pkg) || requestedWindow != window)) {
            requestedPackage = null;
        }
        if (candidate == null) return;
        if (!candidate.pkg.equals(pkg) || now - startedAt > STARTUP_MS) {
            candidate = null;
        } else if (candidate.launcherActivity.equals(activity)) {
            candidateWindow = window;
        } else if (candidateWindow != -1 || !activity.isEmpty() && !activity.startsWith("android.")) {
            candidate = null;
        }
    }

    long startupWait(String pkg, long now) {
        return candidate != null && candidateWindow == -1 && candidate.pkg.equals(pkg)
                ? Math.max(0, startedAt + STARTUP_MS - now) : 0;
    }

    Route take(String pkg, int window, long now) {
        if (candidate == null || candidateWindow != window || !candidate.pkg.equals(pkg)) return null;
        Route result = candidate;
        candidate = null;
        return now - startedAt <= STARTUP_MS ? result : null;
    }

    void requested(Route route, int window, long now) {
        requestedPackage = route.pkg;
        requestedWindow = window;
        settleUntil = now + SETTLE_MS;
    }

    long waitRemaining(String pkg, int window, long now) {
        return pkg.equals(requestedPackage) && window == requestedWindow ? Math.max(0, settleUntil - now) : 0;
    }

    void reset() {
        candidate = null;
        candidateWindow = -1;
        requestedPackage = null;
    }
}
