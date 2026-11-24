package local.qingtap.adskip;

import java.util.List;
import java.util.Locale;

// Hosts are not themselves proof of an ad. Only mini-app runtime windows and live ad dismissal
// evidence are eligible. Host navigation/capsule controls and ordinary chat pages never qualify.
final class MiniProgramRules {
    static final String WECHAT = "com.tencent.mm";
    static final String ALIPAY = "com.eg.android.AlipayGphone";

    static boolean host(String pkg) {
        return WECHAT.equals(pkg) || ALIPAY.equals(pkg);
    }

    static boolean context(String pkg, String activity) {
        if (activity == null || activity.isEmpty()) return false;
        if (WECHAT.equals(pkg)) {
            return activity.startsWith("com.tencent.mm.plugin.appbrand.")
                    && (activity.contains("AppBrandUI") || activity.endsWith("AdUI")
                    || activity.endsWith("AppBrandLaunchProxyUI"));
        }
        if (!ALIPAY.equals(pkg)) return false;
        // Tiny mini-programs run in the :lite process as NebulaActivity$Lite1. A plain Nebula
        // activity is an ordinary Alipay H5 page, not a mini program.
        if (activity.startsWith("com.alipay.mobile.nebulax.integration.mpaas.activity.")) {
            return activity.contains("$Lite") || activity.contains("Tiny");
        }
        if (activity.startsWith("com.alipay.mobile.nebulaappproxy.activity.")
                && (activity.contains("Tiny") || activity.contains("Nebula"))) return true;
        return activity.startsWith("com.alipay.mobile.nebulacore.ui.")
                && activity.contains("Tiny");
    }

    // A window whose root view or ids name a mini-app runtime. This is only a fallback for the case
    // where the activity state change was missed: OCR still requires live ad evidence of its own.
    static boolean windowEvidence(String pkg, String rootClass, List<AdRules.Node> nodes) {
        String root = rootClass == null ? "" : rootClass.toLowerCase(Locale.ROOT);
        if (WECHAT.equals(pkg)) {
            if (root.contains("appbrand") || root.contains("app_brand")) return true;
            return idEvidence(nodes, "appbrand", "app_brand");
        }
        if (ALIPAY.equals(pkg)) {
            if (root.contains("tiny") || root.contains("h5mainproc")) return true;
            return idEvidence(nodes, "tiny", "h5mainproc");
        }
        return false;
    }

    static boolean runtime(String pkg, String activity, String rootClass, List<AdRules.Node> nodes) {
        return context(pkg, activity) || windowEvidence(pkg, rootClass, nodes);
    }

    // A content-changed event can arrive for a new popup window with a framework class name. In that
    // case the last real container activity is still the truth. Only a window-state transition may
    // replace it with another host activity.
    static String activityChange(String pkg, String current, String reported) {
        return activityChange(pkg, current, reported, true);
    }

    static String activityChange(String pkg, String current, String reported, boolean windowState) {
        if (reported == null || reported.isEmpty()) return current == null ? "" : current;
        if (context(pkg, reported)) return reported;
        if (!windowState) return current == null ? "" : current;
        if (current == null || current.isEmpty()) return reported;
        if (reported.startsWith("android.") || reported.startsWith("java.")
                || reported.contains("PopupWindow")) return current;
        return reported;
    }

    static ScopedAdRules.Match choose(String pkg, String activity, List<AdRules.Node> nodes,
                                      AdRules.Box viewport, boolean complete) {
        return choose(pkg, context(pkg, activity), nodes, viewport, complete);
    }

    static ScopedAdRules.Match choose(String pkg, boolean miniContext, List<AdRules.Node> nodes,
                                      AdRules.Box viewport, boolean complete) {
        if (!complete || !miniContext || ScopedAdRules.blockedPage(nodes)) return null;
        int best = -1;
        boolean evidence = ScopedAdRules.adEvidence(nodes, 0);
        for (int i = 1; i < nodes.size(); i++) {
            AdRules.Node node = nodes.get(i);
            String id = ScopedAdRules.entry(node);
            boolean countdownClose = MiniAdText.countdownClose(node.text)
                    || MiniAdText.countdownClose(node.description);
            if (!countdownClose && (MiniAdText.bareClose(node.text) || MiniAdText.bareClose(node.description))) {
                for (AdRules.Node counter : nodes) {
                    String label = counter.text.isEmpty() ? counter.description : counter.text;
                    if (AdRules.isCountdownNumber(label) && AdRules.firstNumber(label) <= 30
                            && counter.box.right <= node.box.left
                            && node.box.left - counter.box.right < viewport.width() * .08
                            && Math.abs(counter.box.top + counter.box.bottom - node.box.top - node.box.bottom)
                            < viewport.height() * .03) countdownClose = true;
                }
            }
            boolean explicit = MiniAdText.explicitClose(node.text) || MiniAdText.explicitClose(node.description);
            boolean bare = MiniAdText.bareClose(node.text) || MiniAdText.bareClose(node.description);
            if (!ScopedAdRules.small(node, viewport) || node.close <= 0 && !countdownClose && !explicit
                    || id.matches(".*(?:capsule|h5_nav|titlebar|toolbar|navigation|back|menu).*")
                    || id.matches(".*(?:appbrand|app_brand).*") && !countdownClose && !explicit) continue;
            boolean corner = node.cornerSkipIn(viewport) || node.topCorner(viewport);
            boolean belowCapsule = node.box.top >= viewport.top + viewport.height() * .09;
            boolean eligible = explicit && corner || evidence && node.countdown && node.skip && corner
                    || evidence && countdownClose && belowCapsule && node.topRight(viewport)
                    // Some cover-ad trees drop the countdown text but keep a labelled close button.
                    // A badge plus a small top-right “关闭/跳过” under the capsule is still actionable.
                    || evidence && bare && belowCapsule && node.topRight(viewport);
            // A generic X/close needs a bounded, local ad group, never just an ad elsewhere on the page.
            for (int at = node.parent, depth = 0; !eligible && at > 0 && depth < 5;
                 at = nodes.get(at).parent, depth++) {
                AdRules.Node scope = nodes.get(at);
                boolean bounded = scope.box.width() * (double) scope.box.height()
                        <= viewport.width() * (double) viewport.height() * .82;
                boolean header = node.box.top < viewport.top + viewport.height() * .08;
                if (scope.box.contains(node.box) && ScopedAdRules.adEvidence(nodes, at)
                        && (explicit || node.skip || bounded && !header)) eligible = true;
            }
            if (!eligible) continue;
            best = ScopedAdRules.unique(nodes, best, i);
            if (best == -2) return null;
        }
        return best < 0 ? null : new ScopedAdRules.Match(best,
                WECHAT.equals(pkg) ? "微信小程序广告" : "支付宝小程序广告", nodes.get(best));
    }

    private static boolean idEvidence(List<AdRules.Node> nodes, String... parts) {
        if (nodes == null) return false;
        for (AdRules.Node node : nodes) {
            String id = node.id.toLowerCase(Locale.ROOT);
            for (String part : parts) {
                if (id.contains(part)) return true;
            }
        }
        return false;
    }

    private MiniProgramRules() {}
}
