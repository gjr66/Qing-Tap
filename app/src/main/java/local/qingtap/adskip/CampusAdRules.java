package local.qingtap.adskip;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// IDs verified in the supplied 趣智校园 6.5.29 APK (com.klcxkj.zqxy, versionCode 679).
// No substring-based guessing of business views; scopes are restricted to this exact package.
final class CampusAdRules {
    static final String PACKAGE = "com.klcxkj.zqxy";
    private static final Set<String> CLOSE_IDS = ids(
            "home_hz_close_iv", "ju_tui_adv_close_iv", "ju_tui_text_link_close_iv",
            "channel_banner_close_iv", "channel_insert_close_iv", "channel_native_close_iv",
            "layout_native_ad_pop_close_iv", "layout_native_ad_pop_close_iv_bottom",
            "anythink_myoffer_banner_close", "anythink_myoffer_btn_close_id",
            "anythink_myoffer_splash_skip", "beizi_close", "beizi_close_view", "ad_close");
    private static final Set<String> REGIONS = ids(
            "home_title_jg_adv_layout", "home_title_jt_adv_layout", "home_tian_mu_layout",
            "home_task_adv_layout", "home_adv_channel_vp", "home_hz_parent_layout",
            "my_title_jg_adv_layout", "my_title_jt_adv_layout", "device_title_adv_layout",
            "device_title_adv_rv", "device_finish_title_adv_rv", "drink_chose_adv_layout",
            "tian_mu_adv_layout", "ju_tui_adv_layout", "ju_tui_text_link_adv_layout",
            "yf_text_link_adv_layout", "charging_adv_layout", "native_adv_layout", "native_adv_view",
            "jt_adv_layout", "home_banner_view", "home_activity_rv");

    static Set<String> regionIds() { return new java.util.TreeSet<>(REGIONS); }
    static final String[] BUSINESS_IDS = {"drinking_btn_operation", "drink_bind_btn_connect", "drink_unbind_btn_connect",
            "drinking_tv_balance", "device_finish_check_bill_tv", "main_tab", "main_scan", "main_wallet", "home_menu_vp"};

    static final class Region {
        final String id;
        final AdRules.Box box;
        Region(String id, AdRules.Box box) { this.id = id; this.box = box; }
    }

    static ScopedAdRules.Match choose(String pkg, String activity, List<AdRules.Node> nodes,
                                       AdRules.Box viewport, boolean complete) {
        if (!PACKAGE.equals(pkg) || !complete || activity != null
                && activity.toLowerCase(java.util.Locale.ROOT).contains("reward")) return null;
        for (AdRules.Node node : nodes) if (AdRules.blocked(node)) return null;
        int best = -1;
        for (int i = 1; i < nodes.size(); i++) {
            AdRules.Node node = nodes.get(i);
            if (!exactId(node, CLOSE_IDS) || !ScopedAdRules.small(node, viewport)
                    || !safeCloseLabel(node.text) || !safeCloseLabel(node.description)) continue;
            if (best < 0 || priority(node) > priority(nodes.get(best))) best = i;
            else if (priority(node) == priority(nodes.get(best))) {
                int nested = ScopedAdRules.unique(nodes, best, i);
                // Multiple verified ad close resources can coexist. Close one, then rescan the live tree.
                if (nested >= 0) best = nested;
            }
        }
        return best < 0 ? null : new ScopedAdRules.Match(best, "趣智校园广告关闭", nodes.get(best));
    }

    static List<Region> regions(String pkg, List<AdRules.Node> nodes, AdRules.Box viewport, boolean complete) {
        List<Region> result = new ArrayList<>();
        if (!PACKAGE.equals(pkg) || !complete) return result;
        for (int i = 1; i < nodes.size(); i++) {
            AdRules.Node node = nodes.get(i);
            if (!exactId(node, REGIONS) || node.box.width() < 10 || node.box.height() < 10
                    || node.box.height() > viewport.height() * .44
                    || node.box.width() * (double) node.box.height()
                    > viewport.width() * (double) viewport.height() * .40) continue;
            if ("home_activity_rv".equals(ScopedAdRules.entry(node)) && !promotionalList(nodes, i)) continue;
            AdRules.Box clipped = new AdRules.Box(Math.max(viewport.left, node.box.left), Math.max(viewport.top, node.box.top),
                    Math.min(viewport.right, node.box.right), Math.min(viewport.bottom, node.box.bottom));
            if (clipped.width() < 10 || clipped.height() < 10) continue;
            boolean safe = true;
            for (int j = 1; j < nodes.size(); j++) {
                AdRules.Node other = nodes.get(j);
                if (business(other) && intersects(clipped, other.box)) { safe = false; break; }
            }
            if (!safe) continue;
            boolean contained = false;
            for (Region before : result) if (before.box.contains(clipped)) { contained = true; break; }
            if (contained) continue;
            result.removeIf(before -> clipped.contains(before.box));
            result.add(new Region(node.id, clipped));
        }
        if (result.size() > 6) return new ArrayList<>(result.subList(0, 6));
        return result;
    }

    private static boolean exactId(AdRules.Node node, Set<String> entries) {
        return node.id.startsWith(PACKAGE + ":id/") && entries.contains(ScopedAdRules.entry(node));
    }

    private static boolean safeCloseLabel(String label) {
        return label.isEmpty() || "趣智校园".equals(label) || AdRules.isCloseLabel(label);
    }

    private static int priority(AdRules.Node node) {
        String id = ScopedAdRules.entry(node);
        if (id.startsWith("layout_native_ad_pop_close") || id.equals("channel_insert_close_iv")) return 100;
        if (id.equals("home_hz_close_iv")) return 10;
        return 50;
    }

    private static boolean business(AdRules.Node node) {
        String id = ScopedAdRules.entry(node);
        return AdRules.blocked(node) && node.text.isEmpty() && node.description.isEmpty()
                || id.matches("(?:drinking_btn_operation|drink_(?:un)?bind_btn_.*|home_menu_vp|main_tab|main_(?:scan|home|mine|wallet|service|shopping)|"
                + "drinking_tv_.*|device_finish_check_bill_tv|.*(?:scan|qrcode|recharge|balance|payment|pay_btn|operation).*)")
                || (node.text + " " + node.description).matches(".*(?:开始打水|停止打水|结束打水|开始用水|结束用水|扫一扫|扫码打水|确认充值|确认支付|支付密码).*"
                );
    }

    private static boolean promotionalList(List<AdRules.Node> nodes, int scope) {
        for (int i = scope + 1; i < nodes.size(); i++) {
            if (!ScopedAdRules.descendant(nodes, i, scope)) continue;
            String text = nodes.get(i).text + " " + nodes.get(i).description;
            if (text.matches(".*(?:广告|抖音直播|考前刷题|冲刺班|领优惠券|最高.*补贴|立即购买).*")) return true;
        }
        return false;
    }

    private static boolean intersects(AdRules.Box a, AdRules.Box b) {
        return a.left < b.right && b.left < a.right && a.top < b.bottom && b.top < a.bottom;
    }

    private static Set<String> ids(String... names) { return new HashSet<>(Arrays.asList(names)); }
    private CampusAdRules() {}
}
