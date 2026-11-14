package local.qingtap.adskip;

public final class HomeLaunchTest {
    public static void main(String[] args) {
        check("sample://home".equals(HomeLaunch.publicHomeUri("sample", "home", null)));
        check("sample://sample.local/home".equals(HomeLaunch.publicHomeUri("sample", "sample.local", "/home")));
        check(HomeLaunch.publicHomeUri("https", "sample.local", "/home") == null);
        check(HomeLaunch.publicHomeUri("sample", "home", "/login") == null);
        check(HomeLaunch.publicHomeUri("sample", "*", "/home") == null);
        check(HomeLaunch.publicHomeUri("sample", "sample.local", null) == null);
        check(HomeLaunch.publicHomeUri("sample", null, "/home") == null);
        HomeLaunch.Discovery discovery = new HomeLaunch.Discovery("sample", 1000);
        check(discovery.waitRemaining(1050) > 0 && discovery.waitRemaining(1180) == 0);
        discovery.observe("android.widget.FrameLayout", 7);
        discovery.resolved(java.util.Collections.singleton("sample.SplashActivity"), "sample://home",
                "sample.HomeActivity");
        check(discovery.route(7, 1070) == null && discovery.waitRemaining(1070) > 0);
        discovery.observe("sample.SplashActivity", 7);
        check(discovery.route(7, 1070).destination.equals("sample.HomeActivity"));
        check(discovery.route(8, 1070) == null && discovery.route(7, 1451) == null);
        discovery.observe("sample.ContentActivity", 8);
        check(discovery.route(8, 1100) == null);
        HomeLaunch.Discovery unavailable = new HomeLaunch.Discovery("sample", 1000);
        unavailable.resolved(java.util.Collections.emptySet(), "", "");
        check(unavailable.resolved() && !unavailable.hasEntry() && unavailable.waitRemaining(1001) == 0);
        HomeLaunch launch = new HomeLaunch();
        HomeLaunch.Route migu = HomeLaunch.forPackage("com.cmcc.cmvideo");
        check(migu != null && migu.launcherActivity.equals(migu.destination));
        check(!migu.isHomeWindow(migu.pkg, migu.destination));
        check(migu.isHomeWindow(migu.pkg, migu.homeActivity));
        check(!migu.isHomeWindow("local.qingtap.adskip", migu.homeActivity));
        HomeLaunch.Route bili = HomeLaunch.forPackage("tv.danmaku.bili");
        check(!bili.isHomeWindow(bili.pkg, bili.launcherActivity));
        check(!bili.isHomeWindow(bili.pkg, bili.destination));
        HomeLaunch.Route damai = HomeLaunch.forPackage("cn.damai");
        check(damai.isHomeWindow(damai.pkg, damai.destination));
        check(!damai.isHomeWindow(damai.pkg, damai.launcherActivity));

        launch.windowChanged(migu.pkg, migu.launcherActivity, 10, 1000);
        check(launch.take(migu.pkg, 10, 1000) == null);

        launch.fromDesktop(migu.pkg, 2000);
        launch.windowChanged(migu.pkg, "android.widget.FrameLayout", 10, 2050);
        check(launch.startupWait(migu.pkg, 2050) > 0);
        check(launch.startupWait("local.qingtap.adskip", 2050) == 0);
        launch.windowChanged(migu.pkg, migu.launcherActivity, 11, 2100);
        check(launch.startupWait(migu.pkg, 2101) == 0);
        check(launch.take(migu.pkg, 12, 2101) == null);
        check(launch.take(migu.pkg, 11, 2101) == migu);
        launch.requested(migu, 11, 2110);
        check(launch.waitRemaining(migu.pkg, 11, 2210) > 0);
        check(launch.waitRemaining(migu.pkg, 11, 2410) == 0);
        launch.windowChanged(migu.pkg, migu.destination, 12, 2250);
        check(launch.waitRemaining(migu.pkg, 12, 2250) == 0);
        check(launch.take(migu.pkg, 12, 2250) == null);

        launch.fromDesktop("local.qingtap.adskip", 2500);
        launch.windowChanged(migu.pkg, migu.launcherActivity, 12, 2550);
        check(launch.take(migu.pkg, 12, 2550) == null);

        launch.fromDesktop(migu.pkg, 3000);
        launch.windowChanged(migu.pkg, "com.cmcc.cmvideo.DeepLinkActivity", 13, 3050);
        launch.windowChanged(migu.pkg, migu.launcherActivity, 14, 3080);
        check(launch.take(migu.pkg, 14, 3081) == null);

        launch.fromDesktop(migu.pkg, 4000);
        check(launch.startupWait(migu.pkg, 6000) == 0);
        launch.windowChanged(migu.pkg, migu.launcherActivity, 15, 6000);
        check(launch.take(migu.pkg, 15, 6000) == null);
        System.out.println("HomeLaunchTest passed");
    }

    private static void check(boolean condition) {
        if (!condition) throw new AssertionError();
    }
}
