package local.qingtap.adskip;

import java.util.List;

// Shared, Android-free checks for app-specific rules. A match carries the observed control,
// so refreshing a numeric badge or another same-position view cannot turn it into a click target.
final class ScopedAdRules {
    static final class Match {
        final int index;
        final String rule;
        final AdRules.Node observed;

        Match(int index, String rule, AdRules.Node observed) {
            this.index = index;
            this.rule = rule;
            this.observed = observed;
        }

        boolean matches(AdRules.Node current) {
            return observed.sameBoxAs(current) && observed.id.equals(current.id)
                    && (observed.sameTarget(current) || observed.text.equals(current.text)
                    && observed.description.equals(current.description)
                    || MiniAdText.countdownClose(observed.text) && MiniAdText.countdownClose(current.text)
                    && observed.description.equals(current.description)
                    || MiniAdText.countdownClose(observed.description) && MiniAdText.countdownClose(current.description)
                    && observed.text.equals(current.text));
        }
    }

    static String entry(AdRules.Node node) {
        return node.id.substring(node.id.lastIndexOf('/') + 1);
    }

    static boolean small(AdRules.Node node, AdRules.Box viewport) {
        return viewport.contains(node.box) && node.box.width() > 0 && node.box.height() > 0
                && node.box.width() <= viewport.width() * .30
                && node.box.height() <= viewport.height() * .09;
    }

    static boolean descendant(List<AdRules.Node> nodes, int child, int ancestor) {
        for (int at = child; at >= 0; at = nodes.get(at).parent) {
            if (at == ancestor) return true;
        }
        return false;
    }

    static boolean sensitive(AdRules.Node node) {
        String text = node.text + " " + node.description;
        String id = entry(node);
        return AdRules.blocked(node) || text.matches(".*(?:确认支付|立即支付|支付密码|支付中|确认付款|验证码|授权登录|登录授权|同意授权|获取手机号|允许访问).*"
                ) || id.matches(".*(?:password|permission|authorize|cashier|payment_confirm).*"
                );
    }

    static boolean blockedPage(List<AdRules.Node> nodes) {
        for (AdRules.Node node : nodes) if (sensitive(node)) return true;
        return false;
    }

    static boolean namedAdScope(AdRules.Node node) {
        String id = entry(node);
        return id.matches("(?:splash_ad|splash_ad_container|splash_ad_view|interstitial_ad|interstitial_ad_container|"
                + "fullscreen_ad|fullscreen_ad_container|ad_dialog|ad_popup|ad_container|native_ad_container|"
                + "tt_splash_container|tt_splash_ad|ksad_splash_container|anythink_myoffer_media_ad_container)");
    }

    static boolean adEvidence(List<AdRules.Node> nodes, int scope) {
        for (int i = scope; i < nodes.size(); i++) {
            if (!descendant(nodes, i, scope)) continue;
            AdRules.Node node = nodes.get(i);
            if (namedAdScope(node) || badge(node.text) || badge(node.description)) return true;
        }
        return false;
    }

    private static boolean badge(String value) {
        return MiniAdText.badge(value) || value.matches("插屏广告|全屏广告|赞助内容|sponsored|互动广告(?: .*)?");
    }

    // Collapse nested copies of one labelled button; refuse distinct competing buttons.
    static int unique(List<AdRules.Node> nodes, int previous, int index) {
        if (previous < 0) return index;
        AdRules.Node before = nodes.get(previous), next = nodes.get(index);
        if (before.box.contains(next.box)) return next.clickable || !before.clickable ? index : previous;
        if (next.box.contains(before.box)) return before.clickable || !next.clickable ? previous : index;
        return -2;
    }

    private ScopedAdRules() {}
}
