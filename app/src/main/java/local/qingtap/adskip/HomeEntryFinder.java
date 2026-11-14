package local.qingtap.adskip;

import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.XmlResourceParser;
import android.net.Uri;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class HomeEntryFinder {
    private static final String ANDROID = "http://schemas.android.com/apk/res/android";

    static final class Result {
        final Set<String> launcherActivities;
        final String uri;
        final String destination;

        Result(Set<String> launcherActivities, String uri, String destination) {
            this.launcherActivities = launcherActivities;
            this.uri = uri;
            this.destination = destination;
        }
    }

    private HomeEntryFinder() {}

    static Result find(PackageManager packages, String pkg) {
        Set<String> launchers = new HashSet<>();
        Intent launcher = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(pkg);
        for (ResolveInfo info : packages.queryIntentActivities(launcher, 0)) {
            ActivityInfo activity = info.activityInfo;
            if (activity == null || !pkg.equals(activity.packageName)) continue;
            launchers.add(activity.name);
            if (activity.targetActivity != null) launchers.add(activity.targetActivity);
        }
        if (!launchers.isEmpty()) {
            try (XmlResourceParser parser = packages.getResourcesForApplication(pkg).getAssets()
                    .openXmlResourceParser("AndroidManifest.xml")) {
                Result result = readManifest(packages, parser, pkg, launchers);
                if (result != null) return result;
            } catch (PackageManager.NameNotFoundException | IOException | XmlPullParserException
                     | RuntimeException unreadable) {
                // The ad rules can still run when the installed manifest is unavailable.
            }
        }
        return new Result(launchers, "", "");
    }

    private static Result readManifest(PackageManager packages, XmlResourceParser parser, String pkg,
                                       Set<String> launchers) throws IOException, XmlPullParserException {
        Result result = null;
        String activity = null;
        int activityDepth = -1;
        for (int event = parser.next(); event != XmlPullParser.END_DOCUMENT; event = parser.next()) {
            String tag = parser.getName();
            int depth = parser.getDepth();
            if (event == XmlPullParser.END_TAG && depth == activityDepth) {
                activity = null;
                activityDepth = -1;
            }
            if (event != XmlPullParser.START_TAG) continue;
            if (depth == 1 && (!"manifest".equals(tag) || !pkg.equals(parser.getAttributeValue(null, "package")))) {
                return null;
            }
            if (depth == 3 && ("activity".equals(tag) || "activity-alias".equals(tag))) {
                activity = componentName(pkg, parser.getAttributeValue(ANDROID, "name"));
                activityDepth = depth;
            } else if (activity != null && depth == activityDepth + 1 && "intent-filter".equals(tag)) {
                Set<String> uris = readFilter(parser);
                for (String uri : uris) {
                    if (!resolvesToHome(packages, pkg, uri, activity, launchers)) continue;
                    if (result != null && (!result.destination.equals(activity) || !result.uri.equals(uri))) {
                        return null;
                    }
                    result = new Result(launchers, uri, activity);
                }
            }
        }
        return result;
    }

    private static Set<String> readFilter(XmlResourceParser parser) throws IOException, XmlPullParserException {
        int depth = parser.getDepth();
        boolean view = false;
        boolean defaultCategory = false;
        Set<String> candidates = new HashSet<>();
        for (int event = parser.next(); event != XmlPullParser.END_DOCUMENT; event = parser.next()) {
            if (event == XmlPullParser.END_TAG && parser.getDepth() == depth) break;
            if (event != XmlPullParser.START_TAG || parser.getDepth() != depth + 1) continue;
            String tag = parser.getName();
            String name = parser.getAttributeValue(ANDROID, "name");
            if ("action".equals(tag) && Intent.ACTION_VIEW.equals(name)) view = true;
            if ("category".equals(tag) && Intent.CATEGORY_DEFAULT.equals(name)) defaultCategory = true;
            if ("data".equals(tag) && parser.getAttributeValue(ANDROID, "port") == null
                    && parser.getAttributeValue(ANDROID, "mimeType") == null) {
                String uri = HomeLaunch.publicHomeUri(parser.getAttributeValue(ANDROID, "scheme"),
                        parser.getAttributeValue(ANDROID, "host"), parser.getAttributeValue(ANDROID, "path"));
                if (uri != null) candidates.add(uri);
            }
        }
        if (!view || !defaultCategory) candidates.clear();
        return candidates;
    }

    private static boolean resolvesToHome(PackageManager packages, String pkg, String uri, String declared,
                                          Set<String> launchers) {
        Intent view = new Intent(Intent.ACTION_VIEW, Uri.parse(uri)).setPackage(pkg);
        List<ResolveInfo> matches = packages.queryIntentActivities(view, PackageManager.MATCH_DEFAULT_ONLY);
        if (matches.size() != 1) return false;
        ActivityInfo activity = matches.get(0).activityInfo;
        return activity != null && activity.exported && activity.permission == null && pkg.equals(activity.packageName)
                && declared.equals(activity.name) && !launchers.contains(activity.name)
                && !launchers.contains(activity.targetActivity);
    }

    private static String componentName(String pkg, String name) {
        if (name == null || name.isEmpty()) return null;
        if (name.charAt(0) == '.') return pkg + name;
        return name.indexOf('.') < 0 ? pkg + "." + name : name;
    }
}
