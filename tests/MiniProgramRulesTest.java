package local.qingtap.adskip;

import java.util.Arrays;
import java.util.List;

public final class MiniProgramRulesTest {
    private static final AdRules.Box SCREEN = new AdRules.Box(0, 0, 1080, 1920);
    private static final AdRules.Box SKIP = new AdRules.Box(860, 100, 1040, 170);
    private static final AdRules.Box POPUP = new AdRules.Box(120, 430, 960, 1450);
    private static final AdRules.Box CLOSE = new AdRules.Box(860, 460, 930, 530);
    private static final String WX = "com.tencent.mm.plugin.appbrand.ui.AppBrandUI1";
    private static final String ALI = "com.alipay.mobile.nebulax.integration.mpaas.activity.NebulaActivity$Lite1";

    public static void main(String[] args) {
        scopeIsolation();
        String coverUi = "com.tencent.mm.plugin.appbrand.ui.AppBrandAdUI";
        check(coverUi.equals(MiniProgramRules.activityChange(MiniProgramRules.WECHAT, WX, coverUi)),
                "recognised cover activity survives a same-window switch");
        check("com.alipay.mobile.nebulaappproxy.activity.TinyTransActivity1".equals(MiniProgramRules.activityChange(MiniProgramRules.ALIPAY, ALI, "com.alipay.mobile.nebulaappproxy.activity.TinyTransActivity1")),
                "Alipay Tiny window class is retained");
        check(WX.equals(MiniProgramRules.activityChange(MiniProgramRules.WECHAT, WX,
                        "android.widget.FrameLayout", false)),
                "a content-changed popup class does not erase the mini container");
        check(WX.equals(MiniProgramRules.activityChange(MiniProgramRules.WECHAT, WX,
                        "android.widget.FrameLayout", true)),
                "a framework class does not replace the mini container");
        check("com.tencent.mm.ui.LauncherUI".equals(MiniProgramRules.activityChange(MiniProgramRules.WECHAT, WX,
                        "com.tencent.mm.ui.LauncherUI", true)),
                "leaving the mini app replaces the container");
        check(MiniProgramRules.context(MiniProgramRules.ALIPAY,
                        "com.alipay.mobile.nebulacore.ui.H5MainProcTinyActivity"),
                "newer Alipay Tiny container");
        check(!MiniProgramRules.context(MiniProgramRules.ALIPAY,
                        "com.alipay.mobile.nebulax.integration.mpaas.activity.NebulaActivity$Main"),
                "a non-lite Nebula activity is not a Tiny container");
        check(!MiniProgramRules.context(MiniProgramRules.ALIPAY,
                        "com.alipay.mobile.nebulacore.ui.H5Activity"),
                "ordinary Alipay H5 page is still excluded");
        check(MiniProgramRules.windowEvidence(MiniProgramRules.WECHAT, "android.widget.FrameLayout",
                        Arrays.asList(root(), node(0, SCREEN, "com.tencent.mm:id/appbrand_page", "", false))),
                "appbrand root id is mini-runtime evidence");
        check(MiniProgramRules.windowEvidence(MiniProgramRules.ALIPAY, "android.widget.FrameLayout",
                        Arrays.asList(root(), node(0, SCREEN, "com.alipay.mobile:id/tiny_webview", "", false))),
                "tiny root id is mini-runtime evidence");
        check(!MiniProgramRules.windowEvidence(MiniProgramRules.WECHAT, "android.widget.FrameLayout",
                        Arrays.asList(root(), node(0, SCREEN, "com.tencent.mm:id/chat_list", "", false))),
                "ordinary WeChat ids are not mini-runtime evidence");

        List<AdRules.Node> splash = Arrays.asList(root(), node(0, SCREEN, "splash_ad_container", "", false),
                node(1, SKIP, "", "跳过广告 5", true));
        check(MiniProgramRules.choose(MiniProgramRules.WECHAT, WX, splash, SCREEN, true).index == 2, "WeChat ad skip");
        check(MiniProgramRules.choose(MiniProgramRules.ALIPAY, ALI, splash, SCREEN, true).index == 2, "Alipay ad skip");
        check(MiniProgramRules.choose(MiniProgramRules.ALIPAY,
                        "com.alipay.mobile.nebulacore.ui.H5MainProcTinyActivity", splash, SCREEN, true).index == 2,
                "newer Alipay Tiny window ad skip");
        check(MiniProgramRules.choose(MiniProgramRules.WECHAT, "com.tencent.mm.ui.LauncherUI", splash, SCREEN, true) == null,
                "a normal WeChat window is never mini-app context");
        check(MiniProgramRules.choose(MiniProgramRules.ALIPAY, "com.alipay.mobile.quinox.LauncherActivity", splash, SCREEN, true) == null,
                "normal Alipay window is never mini-app context");
        check(MiniProgramRules.choose(MiniProgramRules.ALIPAY, "com.alipay.mobile.nebula.activity.H5Activity", splash, SCREEN, true) == null,
                "ordinary legacy H5 pages are not sufficient mini-app proof");
        check(MiniProgramRules.choose(MiniProgramRules.WECHAT, WX, splash, SCREEN, false) == null, "incomplete tree");
        check(MiniProgramRules.choose("other", WX, splash, SCREEN, true) == null, "package scope");
        check(MiniProgramRules.choose(MiniProgramRules.WECHAT, false, splash, SCREEN, true) == null,
                "a caller may withhold runtime context");

        List<AdRules.Node> countdown = Arrays.asList(root(), node(0, SKIP, "", "跳过 4", false),
                node(0, new AdRules.Box(20, 1700, 100, 1760), "", "广告", false));
        check(MiniProgramRules.choose(MiniProgramRules.WECHAT, WX, countdown, SCREEN, true).index == 1,
                "a live labelled countdown and ad badge support custom-rendered skip");
        AdRules.Box coverClose = new AdRules.Box(860,230,1040,280);
        List<AdRules.Node> cover = Arrays.asList(root(), node(0,coverClose,"appbrand_ad_close","4秒 | 关闭",true),
                node(0,new AdRules.Box(150,360,240,400),"","广告⌄",false));
        ScopedAdRules.Match coverMatch=MiniProgramRules.choose(MiniProgramRules.WECHAT,WX,cover,SCREEN,true);
        check(coverMatch!=null && coverMatch.index==1,"cover countdown close beneath capsule");
        check(coverMatch.matches(node(0,coverClose,"appbrand_ad_close","3秒 | 关闭",true)),"cover countdown refresh");
        List<AdRules.Node> split=Arrays.asList(root(),node(0,coverClose,"","关闭",true),
                node(0,new AdRules.Box(790,230,850,280),"","4秒",false),
                node(0,new AdRules.Box(150,360,240,400),"","广告⌄",false));
        check(MiniProgramRules.choose(MiniProgramRules.WECHAT,WX,split,SCREEN,true)!=null,"split native counter and close");
        List<AdRules.Node> bareCover=Arrays.asList(root(),node(0,coverClose,"","关闭",true),
                node(0,new AdRules.Box(150,360,240,400),"","广告⌄",false));
        check(MiniProgramRules.choose(MiniProgramRules.WECHAT,WX,bareCover,SCREEN,true).index==1,
                "cover tree with only a labelled close under the capsule");
        List<AdRules.Node> capsuleBand=Arrays.asList(root(),node(0,new AdRules.Box(920,30,1040,80),"","关闭",true),
                node(0,new AdRules.Box(150,360,240,400),"","广告⌄",false));
        check(MiniProgramRules.choose(MiniProgramRules.WECHAT,WX,capsuleBand,SCREEN,true)==null,
                "capsule-band close is not a cover-ad target");
        List<AdRules.Node> popup = Arrays.asList(root(), node(0, POPUP, "ad_popup", "", false),
                node(1, CLOSE, "", "×", true));
        check(MiniProgramRules.choose(MiniProgramRules.ALIPAY, ALI, popup, SCREEN, true).index == 2, "scoped popup X");
        List<AdRules.Node> ordinary = Arrays.asList(root(), node(0, POPUP, "", "", false),
                node(1, CLOSE, "", "×", true));
        check(MiniProgramRules.choose(MiniProgramRules.ALIPAY, ALI, ordinary, SCREEN, true) == null, "ordinary popup");

        List<AdRules.Node> capsule = Arrays.asList(root(), node(0, new AdRules.Box(850, 20, 1060, 95), "capsule_close", "关闭", true),
                node(0, POPUP, "ad_popup", "", false), node(2, CLOSE, "", "×", true),
                node(0, new AdRules.Box(820, 20, 1060, 95), "", "关闭小程序", true));
        check(MiniProgramRules.choose(MiniProgramRules.WECHAT, WX, capsule, SCREEN, true).index == 3,
                "close the ad, preserve mini-app capsule and its accessible label");

        for (String label : new String[]{"确认支付", "获取手机号", "授权登录", "观看广告领取奖励", "支付中"}) {
            List<AdRules.Node> sensitive = Arrays.asList(root(), node(0, POPUP, "ad_popup", "", false),
                    node(1, CLOSE, "", "关闭广告", true), node(0, POPUP, "", label, false));
            check(MiniProgramRules.choose(MiniProgramRules.WECHAT, WX, sensitive, SCREEN, true) == null, label);
        }
        List<AdRules.Node> ambiguity = Arrays.asList(root(), node(0, POPUP, "ad_popup", "", false),
                node(1, CLOSE, "", "×", true), node(1, new AdRules.Box(160, 460, 230, 530), "", "×", true));
        check(MiniProgramRules.choose(MiniProgramRules.ALIPAY, ALI, ambiguity, SCREEN, true) == null, "competing targets");
        ScopedAdRules.Match match = MiniProgramRules.choose(MiniProgramRules.WECHAT, WX, splash, SCREEN, true);
        check(!match.matches(node(1, SKIP, "", "评论 5", true)), "same-position comment must fail refresh");
        check(match.matches(node(1, SKIP, "", "跳过广告 4", true)), "skip countdown may tick");
        System.out.println("PASS mini-program runtime scope, activity retention, Tiny containers, ad dismissal, capsule, payment, authorization and reward safeguards");
    }

    private static AdRules.Node root() { return node(-1, SCREEN, "", "", false); }
    private static AdRules.Node node(int parent, AdRules.Box box, String id, String text, boolean click) {
        return new AdRules.Node(parent, box, click, false, false, id.isEmpty() ? "" : "host:id/" + id, text, "");
    }
    private static void scopeIsolation() {
        MiniScope scopes = new MiniScope();
        String host = MiniProgramRules.WECHAT;
        String nameId = "com.tencent.mm:id/app_brand_launching_name";
        String a = scopes.identify(host, "小程序甲", Arrays.asList(new AdRules.Node(0, SCREEN, false, false, false, nameId, "小程序甲", "")));
        check(a.equals(scopes.identify(host, "小程序甲", Arrays.asList(root()))), "same titled window retains verified name");
        String b = scopes.identify(host, "小程序乙", Arrays.asList(new AdRules.Node(0, SCREEN, false, false, false, nameId, "小程序乙", "")));
        check(a.startsWith(MiniScope.NAMED) && !a.equals(b), "two named apps in one activity");
        String aKey = AdMemory.signature(host, a, 1080, 1920);
        String bKey = AdMemory.signature(host, b, 1080, 1920);
        final String[] saved = {host + "/*\t2\n" + AdMemory.signature(host, WX, 1080, 1920) + "\t2"};
        AdFeedback feedback = new AdFeedback(new AdFeedback.Store() {
            public String read() { return saved[0]; }
            public void write(String value) { saved[0] = value; }
        });
        check(!feedback.denied(aKey) && !feedback.denied(bKey) && feedback.size() == 2,
                "preserve but never inherit ambiguous old feedback");
        feedback.deny(aKey);
        check(feedback.denied(aKey) && !feedback.denied(bKey), "verdict isolation");
        RepeatGuard.Page base = new RepeatGuard.Page(host, "identical-layout", null);
        RepeatGuard.Page pa = MiniScope.page(base, aKey);
        RepeatGuard.Page pb = MiniScope.page(base, bKey);
        java.util.Set<String> denied = new java.util.HashSet<>();
        denied.add(base.encode());
        check(!pa.denied(denied), "legacy page denial cannot cross into a mini-app");
        denied.add(pa.encode());
        check(pa.denied(denied) && !pb.denied(denied) && !scopes.denyLocally(pa), "named page denial");
        String unknown = scopes.identify(host, "微信", Arrays.asList(root()));
        check(unknown.startsWith(MiniScope.WINDOW), "host title alone is inconclusive");
        RepeatGuard.Page temporary = MiniScope.page(base, AdMemory.signature(host, unknown, 1080, 1920));
        check(scopes.denyLocally(temporary) && scopes.locallyDenied(temporary), "denied for this visit");
        scopes.leave();
        check(!scopes.locallyDenied(temporary) && !unknown.equals(scopes.identify(host,"微信",Arrays.asList(root()))),
                "same window id on later visit cannot inherit temporary decision");
        check(scopes.identify(host, "小程序甲", Arrays.asList(node(0, SCREEN, "page_title","小程序甲", false)))
                .startsWith(MiniScope.WINDOW), "ordinary page content cannot certify identity");
        check(MiniScope.page(base, "example.app/Main/1080x1920") == base, "ordinary apps keep previous keys");
    }

    private static void check(boolean condition, String label) { if (!condition) throw new AssertionError(label); }
}
