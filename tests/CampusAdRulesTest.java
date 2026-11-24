package local.qingtap.adskip;

import java.util.Arrays;
import java.util.List;

public final class CampusAdRulesTest {
    private static final AdRules.Box SCREEN = new AdRules.Box(0, 0, 1080, 1920);
    private static final AdRules.Box SLOT = new AdRules.Box(20, 400, 1060, 610);
    private static final AdRules.Box CLOSE = new AdRules.Box(1000, 410, 1040, 450);
    private static final String PKG = CampusAdRules.PACKAGE;

    public static void main(String[] args) {
        for (String id : new String[]{"home_hz_close_iv", "ju_tui_text_link_close_iv", "channel_native_close_iv",
                "layout_native_ad_pop_close_iv_bottom", "anythink_myoffer_banner_close"}) {
            List<AdRules.Node> nodes = Arrays.asList(root(), node(0, SLOT, "ju_tui_text_link_adv_layout", "", false),
                    new AdRules.Node(1, CLOSE, true, false, false, PKG + ":id/" + id, "", "趣智校园"));
            ScopedAdRules.Match found = CampusAdRules.choose(PKG, "DeviceUsingFinishActivity", nodes, SCREEN, true);
            check(found != null && found.index == 2, "verified close resource " + id);
            check(found.matches(nodes.get(2)), "observed target identity");
            check(!found.matches(node(1, CLOSE, id, "开始打水", true)), "changed target label");
            check(CampusAdRules.choose("other", "MainActivity", nodes, SCREEN, true) == null, "package guard");
            check(CampusAdRules.choose(PKG, "MainActivity", nodes, SCREEN, false) == null, "partial tree");
            check(CampusAdRules.choose(PKG, "RewardVideoActivity", nodes, SCREEN, true) == null, "reward page");
        }
        for (String id : new String[]{"bath_order_close_btn", "dialog_recharge_close", "code_detail_open_close_iv", "iv_close"}) {
            check(CampusAdRules.choose(PKG, "MainActivity", Arrays.asList(root(), node(0, CLOSE, id, "", true)),
                    SCREEN, true) == null, "normal close must not become an ad close " + id);
        }
        List<AdRules.Node> foreign = Arrays.asList(root(), new AdRules.Node(0, CLOSE, true, false, false,
                "other:id/home_hz_close_iv", "", ""));
        check(CampusAdRules.choose(PKG, "MainActivity", foreign, SCREEN, true) == null, "resource namespace");
        List<AdRules.Node> multiple = Arrays.asList(root(), node(0, CLOSE, "home_hz_close_iv", "", true),
                node(0, new AdRules.Box(480, 1380, 550, 1450), "layout_native_ad_pop_close_iv_bottom", "", true));
        check(CampusAdRules.choose(PKG, "MainActivity", multiple, SCREEN, true).index == 2,
                "dismiss verified modal before floating ad when several ads coexist");

        for (String id : new String[]{"home_title_jt_adv_layout", "drink_chose_adv_layout", "device_title_adv_layout",
                "device_finish_title_adv_rv", "tian_mu_adv_layout", "ju_tui_text_link_adv_layout"}) {
            List<AdRules.Node> ad = Arrays.asList(root(), node(0, SLOT, id, "", false));
            check(CampusAdRules.regions(PKG, ad, SCREEN, true).size() == 1, "ad-only slot " + id);
            check(CampusAdRules.regions("other", ad, SCREEN, true).isEmpty(), "mask package guard");
            check(CampusAdRules.regions(PKG, ad, SCREEN, false).isEmpty(), "mask partial tree");
        }
        List<AdRules.Node> business = Arrays.asList(root(), node(0, SLOT, "drink_chose_adv_layout", "", false),
                node(1, CLOSE, "drinking_btn_operation", "开始打水", true));
        check(CampusAdRules.regions(PKG, business, SCREEN, true).isEmpty(), "never cover water controls inside a slot");
        List<AdRules.Node> overlap = Arrays.asList(root(), node(0, SLOT, "drink_chose_adv_layout", "", false),
                node(0, CLOSE, "drinking_tv_balance", "余额", false));
        check(CampusAdRules.regions(PKG, overlap, SCREEN, true).isEmpty(), "protect overlapping business sibling");
        List<AdRules.Node> normal = Arrays.asList(root(), node(0, SLOT, "home_banner_view", "", false));
        check(CampusAdRules.regions(PKG, normal, SCREEN, true).size() == 1, "home banner confirmed in supplied screenshot");
        List<AdRules.Node> promotion = Arrays.asList(root(), node(0, SLOT, "home_task_adv_layout", "", false),
                node(1, CLOSE, "", "看视频赚现金", false));
        check(CampusAdRules.regions(PKG, promotion, SCREEN, true).size() == 1, "reward promotion tile can be masked without claiming a reward");
        List<AdRules.Node> activities = Arrays.asList(root(), node(0, SLOT, "home_activity_rv", "", false),
                node(1, CLOSE, "item_activity_title_tv", "四六级考前冲刺班", false));
        check(CampusAdRules.regions(PKG, activities, SCREEN, true).size() == 1, "lower commercial cards");
        activities = Arrays.asList(root(), node(0, SLOT, "home_activity_rv", "", false),
                node(1, CLOSE, "item_activity_title_tv", "校园通知", false));
        check(CampusAdRules.regions(PKG, activities, SCREEN, true).isEmpty(), "legitimate activity list preserved");
        List<AdRules.Node> clipped = Arrays.asList(root(), node(0,new AdRules.Box(20,-30,1060,150),"home_banner_view","",false));
        check(CampusAdRules.regions(PKG,clipped,SCREEN,true).get(0).box.top == 0,"partly scrolled banner clipped");
        List<AdRules.Node> navigation = Arrays.asList(root(), node(0,SLOT,"home_banner_view","",false),
                node(0,CLOSE,"main_shopping","购物",true));
        check(CampusAdRules.regions(PKG,navigation,SCREEN,true).isEmpty(),"fixed navigation overlap protected");
        List<AdRules.Node> large = Arrays.asList(root(), node(0, SCREEN, "drink_chose_adv_layout", "", false));
        check(CampusAdRules.regions(PKG, large, SCREEN, true).isEmpty(), "never cover a whole screen");
        List<AdRules.Node> nested = Arrays.asList(root(), node(0, SLOT, "device_title_adv_layout", "", false),
                node(1, new AdRules.Box(30, 410, 1050, 600), "device_title_adv_rv", "", false));
        check(CampusAdRules.regions(PKG, nested, SCREEN, true).size() == 1, "coalesce nested masks");
        System.out.println("PASS verified campus close IDs and ad slots; protected water, balance, recharge, foreign IDs and mask bounds");
    }

    private static AdRules.Node root() { return node(-1, SCREEN, "", "", false); }
    private static AdRules.Node node(int parent, AdRules.Box box, String id, String text, boolean clickable) {
        return new AdRules.Node(parent, box, clickable, false, false, id.isEmpty() ? "" : PKG + ":id/" + id, text, "");
    }
    private static void check(boolean condition, String label) { if (!condition) throw new AssertionError(label); }
}
