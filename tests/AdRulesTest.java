package local.qingtap.adskip;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;

public final class AdRulesTest {
    private static final AdRules.Box SCREEN = new AdRules.Box(0, 0, 1080, 1920);
    private static final AdRules.Box CORNER = new AdRules.Box(862, 56, 1050, 158);
    private static final AdRules.Box DIALOG = new AdRules.Box(140, 600, 940, 1200);
    private static final AdRules.Box DIALOG_CLOSE = new AdRules.Box(690, 1060, 880, 1180);
    private static final AdRules.Box CARD = new AdRules.Box(30, 700, 1050, 1060);
    private static final AdRules.Box CARD_CLOSE = new AdRules.Box(780, 719, 1010, 817);
    private static final AdRules.Box OFFER_CROSS = new AdRules.Box(500, 1470, 580, 1550);
    private static final AdRules.Box OFFER_BADGE = new AdRules.Box(912, 345, 970, 381);
    private static final AdRules.Box OFFER_CTA = new AdRules.Box(260, 1190, 820, 1320);
    private static int count;

    public static void main(String[] args) {
        verifyIndependentAdModes();
        expect("shake splash with countdown", 1, false, root(),
                button(0, CORNER, "", "跳过 ５ｓ", ""),
                node(0, DIALOG, "", "摇一摇查看详情"),
                node(0, DIALOG, "", "广告"));
        expect("splash with ad and promotional call to action", 1, false, root(),
                button(0, CORNER, "", "Skip (5s)", ""),
                node(0, DIALOG, "", "Sponsored"),
                node(0, DIALOG, "", "Learn more"));
        expect("explicit top skip on ad splash", 1, false, root(),
                button(0, CORNER, "", "跳过广告", ""),
                node(0, DIALOG, "", "开屏广告"));
        expect("plain onboarding countdown", -1, false, root(),
                button(0, CORNER, "", "跳过 5s", ""),
                node(0, DIALOG, "", "欢迎使用"));
        expect("ordinary shake feature with skip", -1, false, root(),
                button(0, CORNER, "", "跳过", ""),
                node(0, DIALOG, "", "摇一摇手机试试"));
        expect("named interstitial with icon resource", 2, false, root(),
                node(0, DIALOG, "test:id/interstitial_ad", ""),
                button(1, DIALOG_CLOSE, "test:id/ad_close", "", ""));
        expect("unnamed interstitial with local markers", 2, false, root(),
                node(0, DIALOG, "", ""),
                button(1, DIALOG_CLOSE, "", "关闭", ""),
                node(1, CARD, "", "插屏广告"));
        expect("conflicting call to action label", -1, false, root(),
                node(0, DIALOG, "test:id/interstitial_ad", ""),
                button(1, DIALOG_CLOSE, "test:id/ad_close", "立即下载", "关闭广告"));
        expect("giant invisible target", -1, false, root(),
                node(0, SCREEN, "test:id/splash_ad", ""),
                button(1, SCREEN, "test:id/ad_close", "", ""));
        expect("partially offscreen target", -1, false, root(),
                node(0, SCREEN, "test:id/splash_ad", ""),
                button(1, new AdRules.Box(1000, 56, 1120, 158), "test:id/ad_close", "", ""));
        expect("non-clickable close label", -1, false, root(),
                node(0, DIALOG, "test:id/interstitial_ad", ""),
                node(1, DIALOG_CLOSE, "", "关闭广告"));
        expect("rewarded flow", -1, false, root(),
                node(0, DIALOG, "test:id/interstitial_ad", "观看广告领取奖励"),
                button(1, DIALOG_CLOSE, "test:id/ad_close", "关闭广告", ""));
        expect("payment flow", -1, false, root(),
                node(0, DIALOG, "test:id/ad_popup", "确认支付"),
                button(1, DIALOG_CLOSE, "test:id/ad_close", "关闭广告", ""));
        expect("focused input", -1, false, root(),
                node(0, DIALOG, "test:id/ad_dialog", "广告"),
                button(1, DIALOG_CLOSE, "test:id/ad_close", "关闭广告", ""),
                new AdRules.Node(0, CARD, false, false, true, "", null, null));
        expect("comment sponsor card enabled", 3, true, root(),
                node(0, SCREEN, "test:id/comment_panel", ""),
                node(1, CARD, "test:id/comment_ad_card", ""),
                button(2, CARD_CLOSE, "test:id/comment_ad_close", "×", ""),
                node(2, CARD, "", "赞助内容"));
        expect("comment sponsor card disabled", -1, false, root(),
                node(0, SCREEN, "test:id/comment_panel", ""),
                node(1, CARD, "test:id/comment_ad_card", ""),
                button(2, CARD_CLOSE, "test:id/comment_ad_close", "关闭广告", ""),
                node(2, CARD, "", "赞助内容"));
        expect("comment panel close is unrelated to sponsor card", -1, true, root(),
                node(0, DIALOG, "", ""),
                button(1, DIALOG_CLOSE, "", "关闭", ""),
                node(1, CARD, "", "评论区"),
                node(1, CARD, "", "广告"),
                node(1, CARD, "", "查看详情"));
        expect("comment with promotional link and no close", -1, true, root(),
                node(0, SCREEN, "test:id/comment_panel", ""),
                node(1, CARD, "test:id/comment_ad_card", ""),
                button(2, CARD_CLOSE, "", "立即下载", ""),
                node(2, CARD, "", "赞助内容"));
        expect("embedded ad without badge", -1, true, root(),
                node(0, CARD, "test:id/comment_ad_card", ""),
                button(1, CARD_CLOSE, "", "关闭广告", ""));
        expect("ordinary close in neighboring row", -1, true, root(),
                new AdRules.Node(0, SCREEN, false, true, false, "", null, null),
                node(1, CARD, "", "广告"),
                button(1, CARD_CLOSE, "", "关闭", ""));
        expect("equal candidate ambiguity", -1, false, root(),
                node(0, DIALOG, "test:id/interstitial_ad", ""),
                button(1, DIALOG_CLOSE, "", "关闭广告", ""),
                button(1, new AdRules.Box(170, 630, 350, 740), "", "关闭广告", ""));
        expect("explicit action wins over generic one", 2, false, root(),
                node(0, DIALOG, "test:id/interstitial_ad", ""),
                button(1, DIALOG_CLOSE, "", "关闭广告", ""),
                button(1, new AdRules.Box(170, 630, 350, 740), "", "关闭", ""));
        expect("screenshot countdown before ad skip", 1, false, root(),
                button(0, CORNER, "", "2 跳过广告", ""));
        expect("screenshot countdown after ad skip", 1, false, root(),
                button(0, CORNER, "", "", "跳过广告（2秒）"));
        expect("screenshot skip with a divider before the countdown", 1, false, root(),
                button(0, CORNER, "", "跳过广告｜5", ""));
        expect("onboarding divider and swipe-to-view text", -1, false, root(),
                button(0, CORNER, "", "跳过｜5", ""),
                node(0, OFFER_CTA, "", "上滑或点击查看照片"));
        expect("countdown and accessible description on separate properties", 1, false, root(),
                button(0, CORNER, "", "2", "跳过广告"));
        expect("no direct shortcut for bare onboarding skip", -1, false, root(),
                button(0, CORNER, "", "2 跳过", ""));
        expect("explicit skip on a small control is location constrained", -1, false, root(),
                button(0, OFFER_CROSS, "", "2 跳过广告", ""));
        expect("screenshot interactive video splash", 1, false, root(),
                button(0, CORNER, "", "跳过", ""),
                node(0, new AdRules.Box(680, 60, 835, 135), "", "互动广告 京东"),
                node(0, OFFER_CTA, "", "翻转手机，跳转至京东"));
        expect("interactive video splash with flip cue only", 1, false, root(),
                button(0, CORNER, "", "跳过", ""),
                node(0, OFFER_CTA, "", "翻转手机，跳转至京东"));
        expect("non-ad orientation instruction", -1, false, root(),
                button(0, CORNER, "", "跳过", ""),
                node(0, OFFER_CTA, "", "翻转手机查看照片"));
        expect("rewarded countdown skip stays untouched", -1, false, root(),
                button(0, CORNER, "", "2 跳过广告", ""),
                node(0, DIALOG, "", "跳过将失去奖励"));
        expect("screenshot offer popup with bottom cross", 1, false, root(),
                button(0, OFFER_CROSS, "", "×", ""),
                node(0, OFFER_BADGE, "", "广告"),
                button(0, OFFER_CTA, "", "登录领取", ""));
        expect("offer popup with named unlabelled native cross", 1, false, root(),
                button(0, OFFER_CROSS, "test:id/iv_close", "", ""),
                node(0, OFFER_BADGE, "", "广告"),
                button(0, OFFER_CTA, "", "登录领取", ""));
        expect("offer popup without an ad badge", -1, false, root(),
                button(0, OFFER_CROSS, "test:id/iv_close", "", ""),
                button(0, OFFER_CTA, "", "登录领取", ""));
        expect("offer popup with promotional CTA disguised as a cross", -1, false, root(),
                button(0, OFFER_CROSS, "test:id/iv_close", "登录领取", "×"),
                node(0, OFFER_BADGE, "", "广告"),
                button(0, OFFER_CTA, "", "登录领取", ""));
        expect("offer popup never accepts unlabeled arbitrary images", -1, false, root(),
                button(0, OFFER_CROSS, "", "", ""),
                node(0, OFFER_BADGE, "", "广告"),
                button(0, OFFER_CTA, "", "登录领取", ""));
        expect("explicit skip inside comment subtree is not a splash", -1, false, root(),
                node(0, SCREEN, "test:id/comment_panel", ""),
                button(1, CORNER, "", "2 跳过广告", ""));
        expectWithCoverage("bounded scan can use explicit skip", 1, false, false, root(),
                button(0, CORNER, "", "2 跳过广告", ""));
        expectWithCoverage("bounded scan keeps distinct offer and ad evidence", 1, false, false, root(),
                button(0, OFFER_CROSS, "", "×", ""),
                node(0, OFFER_BADGE, "", "广告"),
                button(0, OFFER_CTA, "", "登录领取", ""));
        expectWithCoverage("bounded scan rejects weak candidate", -1, false, false, root(),
                button(0, OFFER_CROSS, "", "×", ""),
                node(0, OFFER_BADGE, "", "广告"));
        expectWithCoverage("bounded scan keeps interactive splash evidence", 1, false, false, root(),
                button(0, CORNER, "", "跳过", ""),
                node(0, OFFER_CTA, "", "翻转手机，跳转至京东"));
        if (!button(0, CORNER, "", "跳过 ５ｓ", "").sameControl(button(0, CORNER, "", "跳过 5s", ""))
                || button(0, CORNER, "", "跳过 5s", "").sameControl(button(0, CORNER, "", "立即体验", ""))) {
            throw new AssertionError("changed control revalidation");
        }
        count++;
        if (!button(0, CORNER, "", "2 跳过广告", "").sameControl(button(0, CORNER, "", "1 跳过广告", ""))) {
            throw new AssertionError("countdown tick does not invalidate the same button");
        }
        count++;
        System.out.println("PASS " + count + " rule checks");
        verifyClickGate();
        verifyTencentSplash();
        verifyCornerSkip();
        verifyStructure();
        verifyNormalPageSafety();
        verifyMemory();
        verifyVision();
        verifyHints();
        verifyFeedback();
        verifyActionLog();
        verifyTaughtDismissControl();
        verifyTaughtLiveRule();
        verifyTaught();
        verifyRepeatGuard();
        verifySceneFingerprint();
        verifyMultipleLessons();
    }

    private static void verifyIndependentAdModes() {
        java.util.List<AdRules.Node> mixed = Arrays.asList(root(),
                button(0, CORNER, "", "跳过广告", ""),
                node(0, CARD, "test:id/comment_ad_card", ""),
                button(2, CARD_CLOSE, "test:id/comment_ad_close", "关闭广告", ""),
                node(2, CARD, "", "赞助内容"));
        if (AdRules.choose(mixed, SCREEN, true, false, true) != 1
                || AdRules.choose(mixed, SCREEN, false, true, true) != 3
                || AdRules.choose(mixed, SCREEN, false, false, true) != -1
                || AdRules.choose(mixed, SCREEN, true, true, true) != 1) {
            throw new AssertionError("Splash and inline selection must independently control candidates");
        }
        System.out.println("PASS independent splash and inline candidate rules");
    }

    private static void expect(String name, int expected, boolean inline, AdRules.Node... nodes) {
        expectWithCoverage(name, expected, inline, true, nodes);
    }

    private static void expectWithCoverage(String name, int expected, boolean inline, boolean complete,
                                           AdRules.Node... nodes) {
        int actual = AdRules.choose(Arrays.asList(nodes), SCREEN, inline, complete);
        if (actual != expected) throw new AssertionError(name + " expected " + expected + ", got " + actual);
        count++;
    }

    private static void verifyClickGate() {
        ClickGate<String> gate = new ClickGate<>();
        for (int appearance = 0; appearance < 5; appearance++) {
            String control = "same window and position, distinct view " + appearance;
            long now = 1000 + appearance * 140L;
            if (gate.waitTime(control, now) != 0) throw new AssertionError("new appearance must click immediately");
            gate.record(control, now, true);
            if (gate.waitTime(control, now + 1) <= 0) throw new AssertionError("same view needs a retry delay");
        }
        gate.clear();
        gate.record("failed", 2000, false);
        if (gate.waitTime("failed", 2094) != 1 || gate.waitTime("failed", 2095) != 0) {
            throw new AssertionError("failed action gets a short retry");
        }
        gate.record("failed", 2095, true);
        if (gate.waitTime("failed", 2500) != -1) throw new AssertionError("duplicate retries must be bounded");
        gate.clear();
        if (gate.waitTime("failed", 2500) != 0) throw new AssertionError("view can reappear after dismissal");
        System.out.println("PASS repeat-click scheduling checks");
    }

    private static void verifyTencentSplash() {
        AdRules.Node textOnlySkip = node(0, CORNER, "", "跳过");
        AdRules.Node flipToTaobao = node(0, OFFER_CTA, "", "翻转手机，前往淘宝");
        expectTencent("screenshot with a non-clickable skip TextView", true, 1, false,
                root(), textOnlySkip, flipToTaobao);
        expectTencent("badge alone can establish an interactive ad", true, 1, false,
                root(), textOnlySkip, node(0, OFFER_BADGE, "", "互动广告 淘宝"));
        expectTencent("trusted Tencent entry with no exposed ad label", true, 1, true, root(), textOnlySkip);
        expectTencent("same skip without a launch or ad marker", false, -1, false, root(), textOnlySkip);
        expectTencent("a video intro skip is not an ad skip", true, -1, false,
                root(), node(0, CORNER, "", "跳过片头"), flipToTaobao);
        expectTencent("ad link cannot be the touch target", true, -1, false,
                root(), node(0, CORNER, "", "前往淘宝"), flipToTaobao);
        expectTencent("the entire ad cannot be the touch target", true, -1, false,
                root(), node(0, SCREEN, "", "跳过"), flipToTaobao);
        expectTencent("only the upper right skip is eligible", true, -1, false,
                root(), node(0, OFFER_CROSS, "", "跳过"), flipToTaobao);
        expectTencent("reward context blocks the gesture", false, -1, false,
                root(), textOnlySkip, flipToTaobao, node(0, DIALOG, "", "激励广告"));
        expectTencent("comment subtree is excluded", true, -1, false,
                root(), node(0, SCREEN, "test:id/comment_panel", ""),
                node(1, CORNER, "", "跳过"), flipToTaobao);
        expectTencent("distinct competing skip controls are ambiguous", true, -2, false,
                root(), textOnlySkip,
                node(0, new AdRules.Box(839, 215, 1022, 301), "", "跳过"), flipToTaobao);
        expectTencent("the tighter duplicate is selected", true, 2, false,
                root(), textOnlySkip,
                node(1, new AdRules.Box(910, 73, 1010, 145), "", "跳过"), flipToTaobao);
        if (!textOnlySkip.sameTarget(node(0, CORNER, "", "跳过"))
                || textOnlySkip.sameTarget(node(0, CORNER, "", "前往淘宝"))
                || textOnlySkip.sameControl(node(0, CORNER, "", "跳过"))) {
            throw new AssertionError("Tencent label revalidation");
        }
        System.out.println("PASS 13 Tencent splash checks");
    }

    private static void expectTencent(String name, boolean expectedContext, int expectedIndex,
                                      boolean startup, AdRules.Node... nodes) {
        boolean context = AdRules.tencentSplashContext(Arrays.asList(nodes), startup);
        int index = context ? AdRules.chooseTencentSkip(Arrays.asList(nodes), SCREEN) : -1;
        if (context != expectedContext || index != expectedIndex) {
            throw new AssertionError(name + " expected " + expectedContext + "/" + expectedIndex
                    + ", got " + context + "/" + index);
        }
    }

    private static void verifyCornerSkip() {
        AdRules.Box badge = new AdRules.Box(918, 120, 1002, 168);
        AdRules.Box bottomRight = new AdRules.Box(772, 1780, 1036, 1890);
        AdRules.Box bottomCenter = new AdRules.Box(480, 1780, 600, 1890);
        AdRules.Box cue = new AdRules.Box(330, 1560, 750, 1620);
        AdRules.Box commentPanel = new AdRules.Box(0, 0, 1080, 1920);

        expectCorner("Bilibili splash with badge and twist cue", 2, root(),
                node(0, badge, "", "广告"),
                node(0, bottomRight, "", "跳过 5"),
                node(0, cue, "", "扭一扭进入京东"));
        expectCorner("badge plus countdown is enough", 2, root(),
                node(0, badge, "", "广告"),
                node(0, bottomRight, "", "跳过 5"));
        expectCorner("badge alone cannot skip a bare label", -1, root(),
                node(0, badge, "", "广告"),
                node(0, bottomRight, "", "跳过"));
        expectCorner("twist cue alone can skip a bare label", 1, root(),
                node(0, bottomRight, "", "跳过"),
                node(0, cue, "", "扭一扭进入京东"));
        expectCorner("screenshot divider with a non-clickable skip and third-party link", 1, root(),
                node(0, CORNER, "", "跳过广告丨5"),
                node(0, cue, "", "上滑或点击 跳转至详情页或第三方应用"));
        expectCorner("no ad evidence at all", -1, root(),
                node(0, bottomRight, "", "跳过 5"));
        expectCorner("corner band rejects a centered bottom control", -1, root(),
                node(0, badge, "", "广告"),
                node(0, bottomCenter, "", "跳过 5"));
        expectCorner("comment subtree is excluded", -1, root(),
                node(0, commentPanel, "test:id/comment_panel", ""),
                node(1, badge, "", "广告"),
                node(1, bottomRight, "", "跳过 5"));
        expectCorner("payment context blocks the gesture", -1, root(),
                node(0, badge, "", "广告"),
                node(0, cue, "", "扭一扭进入京东"),
                node(0, new AdRules.Box(300, 900, 780, 1000), "", "确认支付"),
                node(0, bottomRight, "", "跳过 5"));
        expectCorner("two distinct corner controls are ambiguous", -1, root(),
                node(0, badge, "", "广告"),
                node(0, bottomRight, "", "跳过 5"),
                node(0, new AdRules.Box(30, 40, 200, 140), "", "跳过 5"));
        expectCorner("the tighter duplicate is selected", 3, root(),
                node(0, badge, "", "广告"),
                node(0, new AdRules.Box(760, 1770, 1046, 1900), "", "广告 跳过 5"),
                node(1, bottomRight, "", "跳过 5"));
        expectCorner("a full screen ad surface is not a control", -1, root(),
                node(0, badge, "", "广告"),
                node(0, commentPanel, "", "跳过 5"));
        expectCornerSplash("bare skip next to a badge inside a splash", 2, root(),
                node(0, badge, "", "广告"),
                node(0, bottomRight, "", "跳过"));
        expectCornerSplash("bare skip with no marker stays untouched", -1, root(),
                node(0, bottomRight, "", "跳过"));
        System.out.println("PASS 14 corner skip checks");
    }

    private static void expectCornerSplash(String name, int expected, AdRules.Node... nodes) {
        int actual = AdRules.chooseCornerSkip(Arrays.asList(nodes), SCREEN, false, true);
        if (actual != expected) throw new AssertionError(name + " expected " + expected + ", got " + actual);
        count++;
    }

    private static void expectCorner(String name, int expected, AdRules.Node... nodes) {
        int actual = AdRules.chooseCornerSkip(Arrays.asList(nodes), SCREEN);
        if (actual != expected) throw new AssertionError(name + " expected " + expected + ", got " + actual);
        count++;
    }

    private static void verifyStructure() {
        AdRules.Box bottomRight = new AdRules.Box(880, 1780, 990, 1880);
        AdRules.Box topRight = new AdRules.Box(900, 60, 1010, 150);

        expectStructure("bare counter at the bottom edge of a full bleed window", -1, root(),
                node(0, SCREEN, "", ""), node(0, bottomRight, "", "5"));
        expectStructure("bare counter at the top edge", -1, root(),
                node(0, SCREEN, "", ""), node(0, topRight, "", "3s"));
        expectStructure("countdown skip wording without ad evidence is insufficient", -1, root(),
                node(0, SCREEN, "", ""), node(0, bottomRight, "", "跳过 5"));
        expectStructure("no counter anywhere", -1, root(),
                node(0, SCREEN, "", ""), node(0, bottomRight, "", "关闭"));
        expectStructure("ad badge and labelled countdown qualify", 2, root(),
                node(0, SCREEN, "", ""), node(0, bottomRight, "", "跳过 5"),
                node(0, DIALOG, "", "广告"));
        expectStructure("a scrolling page is not a splash", -1,
                node(-1, SCREEN, "", ""),
                node(0, SCREEN, "", "商品列表"),
                new AdRules.Node(0, SCREEN, false, true, false, "", null, null),
                node(0, bottomRight, "", "5"));
        expectStructure("a partial surface is not full bleed", -1,
                node(-1, new AdRules.Box(0, 0, 1080, 900), "", ""),
                node(0, new AdRules.Box(880, 820, 990, 895), "", "5"));
        expectStructure("two separate counters are ambiguous", -1, root(),
                node(0, SCREEN, "", ""),
                node(0, bottomRight, "", "5"),
                node(0, new AdRules.Box(40, 60, 150, 150), "", "5"));
        expectStructure("a counter inside the comment panel is excluded", -1, root(),
                node(0, SCREEN, "", ""),
                node(0, new AdRules.Box(0, 0, 1080, 1920), "test:id/comment_panel", ""),
                node(1, bottomRight, "", "5"));

        expectTick("5 to 4", true, "5", "4");
        expectTick("countdown wording ticks", true, "跳过 5", "跳过 4");
        expectTick("unchanged number", false, "5", "5");
        expectTick("increasing number", false, "4", "5");
        expectTick("no number", false, "跳过", "4");
        System.out.println("PASS structure safety checks");
        System.out.println("PASS 5 countdown tick checks");
    }

    private static void expectStructure(String name, int expected, AdRules.Node... nodes) {
        int actual = AdRules.structureTarget(Arrays.asList(nodes), SCREEN);
        if (actual != expected) throw new AssertionError(name + " expected " + expected + ", got " + actual);
        count++;
    }

    private static void verifyNormalPageSafety() {
        AdRules.Box spot = new AdRules.Box(880, 1780, 990, 1880);
        for (String label : new String[]{"5", "评论 5", "5条评论", "全部评论", "消息", "购物车"}) {
            java.util.List<AdRules.Node> nodes = Arrays.asList(root(), button(0, spot, "", label, ""));
            if (AdRules.structureTarget(nodes, SCREEN) != -1
                    || AdRules.matchHint(nodes, SCREEN, .865, .953, "5") != -1
                    || AdRules.visualFallbackAllowed(nodes, SCREEN, 935, 1830)) {
                throw new AssertionError("normal control must never become a dismissal: " + label);
            }
        }
        for (String id : new String[]{"comment_count", "iv_comment", "btn_reply", "message_tab", "cart_tab"}) {
            java.util.List<AdRules.Node> nodes = Arrays.asList(root(),
                    button(0, spot, "test:id/" + id, "5", ""), node(0, DIALOG, "", "广告"));
            if (AdRules.splashShape(nodes, SCREEN) || !AdRules.pageLike(nodes)
                    || AdRules.structureTarget(nodes, SCREEN) != -1
                    || AdRules.matchHint(nodes, SCREEN, .865, .953, "5") != -1) {
                throw new AssertionError("normal resource id must protect sparse native pages: " + id);
            }
        }
        java.util.List<AdRules.Node> navigation = Arrays.asList(root(),
                node(0, new AdRules.Box(0, 1740, 1080, 1920), "test:id/bottom_navigation", ""),
                button(1, spot, "", "跳过广告", ""), node(0, DIALOG, "", "广告"));
        if (AdRules.choose(navigation, SCREEN, true) != -1
                || AdRules.matchHint(navigation, SCREEN, .865, .953, "跳过广告") != -1
                || AdRules.visualFallbackAllowed(navigation, SCREEN, 935, 1830)) {
            throw new AssertionError("a navigation child cannot be clicked through any fallback");
        }
        java.util.List<AdRules.Node> unknown = Arrays.asList(root(),
                button(0, spot, "", "", ""), node(0, DIALOG, "", "广告"));
        if (AdRules.visualFallbackAllowed(unknown, SCREEN, 935, 1830)) {
            throw new AssertionError("ad context and a contrasting unlabelled icon are insufficient");
        }
        java.util.List<AdRules.Node> ad = Arrays.asList(root(),
                button(0, spot, "", "跳过广告", ""), node(0, DIALOG, "", "广告"));
        if (!AdRules.visualFallbackAllowed(ad, SCREEN, 935, 1830)
                || AdRules.visualFallbackAllowed(ad, SCREEN, 80, 1830)) {
            throw new AssertionError("visual fallback requires a live dismissal at the proposed position");
        }
        AdRules.Node skip = button(0, spot, "", "跳过 5", "");
        if (skip.sameTarget(button(0, spot, "", "5", ""))
                || skip.sameTarget(button(0, spot, "test:id/comment_count", "5", ""))
                || !skip.sameTarget(button(0, spot, "", "跳过 4", ""))) {
            throw new AssertionError("same coordinates cannot replace dismissal identity during revalidation");
        }
        System.out.println("PASS normal comments, unread badges, navigation and visual fallback safety");
    }

    private static void expectTick(String name, boolean expected, String before, String after) {
        boolean actual = AdRules.countdownTick(before, after);
        if (actual != expected) throw new AssertionError(name + " expected " + expected + ", got " + actual);
        count++;
    }

    private static void verifyMemory() {
        MemoryStore store = new MemoryStore();
        AdMemory memory = new AdMemory(store);
        String first = AdMemory.signature("tv.danmaku.bili", "SplashActivity", 1080, 2373);
        String other = AdMemory.signature("tv.danmaku.bili", "MainActivity", 1080, 2373);
        if (memory.reliable(first)) throw new AssertionError("empty memory must not be trusted");
        memory.succeeded(first, .87, .96, "5", 1000);
        if (!memory.reliable(first)) throw new AssertionError("first confirmed success must be usable");
        if (memory.reliable(other)) throw new AssertionError("signature must include the activity");
        AdMemory.Target target = memory.target(first);
        if (target == null || Math.abs(target.xRatio() - .87) > .01 || Math.abs(target.yRatio() - .96) > .01) {
            throw new AssertionError("stored ratio drifted");
        }
        memory.missed(first);
        if (memory.reliable(first)) throw new AssertionError("one failure must drop a single hit");
        memory.succeeded(first, .87, .96, "5", 1200);
        if (memory.reliable(first)) throw new AssertionError("two hits against one miss is still not a majority");
        memory.succeeded(first, .87, .96, "5", 1400);
        if (!memory.reliable(first)) throw new AssertionError("three hits against one miss must be trusted");
        if (memory.target(first).hits != 3) throw new AssertionError("hits must accumulate");

        AdMemory reloaded = new AdMemory(store);
        if (!reloaded.reliable(first)) throw new AssertionError("memory must survive a reload");
        for (int index = 0; index < 60; index++) {
            reloaded.succeeded(AdMemory.signature("p" + index, "A", 1080, 1920), .5, .5, "5", 2000 + index);
        }
        if (reloaded.size() != 40) throw new AssertionError("memory must stay bounded, got " + reloaded.size());
        reloaded.clear();
        if (reloaded.size() != 0 || new AdMemory(store).size() != 0) throw new AssertionError("clear must persist");
        System.out.println("PASS memory learning checks");
    }

    private static final class MemoryStore implements AdMemory.Store {
        private String value = "";

        @Override public String read() {
            return value;
        }

        @Override public void write(String next) {
            value = next;
        }
    }

    private static void verifyHints() {
        HintStore store = new HintStore();
        AdHints hints = new AdHints(store);
        String signature = AdMemory.signature("tv.danmaku.bili", "Splash", 1080, 2373);
        if (hints.find(signature) != null) throw new AssertionError("empty hints");
        hints.remember(signature, .12, .94, "跳过", true);
        AdHints.Hint hint = hints.find(signature);
        if (hint == null) throw new AssertionError("a countdown backed sighting must be usable");
        if (Math.abs(hint.xRatio() - .12) > .01 || Math.abs(hint.yRatio() - .94) > .01) {
            throw new AssertionError("hint ratio drifted");
        }
        hints.missed(signature);
        if (hints.find(signature) == null) throw new AssertionError("one miss must not drop a hint");
        hints.missed(signature);
        if (hints.find(signature) != null) throw new AssertionError("hint must drop once misses exceed hits");
        hints.remember(signature, .12, .94, "跳过", true);
        hints.confirmed(signature);
        if (hints.find(signature) == null) throw new AssertionError("confirmed hint must stay");
        if (new AdHints(store).find(signature) == null) throw new AssertionError("hints must survive a reload");

        String weak = AdMemory.signature("com.example.app", "Launch", 1080, 1920);
        hints.remember(weak, .5, .1, "", false);
        if (hints.find(weak) != null) throw new AssertionError("a weak sighting must only watch");
        hints.remember(weak, .5, .1, "", false);
        if (hints.find(weak) == null) throw new AssertionError("a second identical sighting must enable it");

        AdRules.Box spot = new AdRules.Box(40, 1760, 160, 1860);
        if (AdRules.matchHint(Arrays.asList(root(), node(0, spot, "", "跳过")), SCREEN, .09, .95, "跳过") != 1) {
            throw new AssertionError("hint must match the same control");
        }
        if (AdRules.matchHint(Arrays.asList(root(), node(0, spot, "", "4")), SCREEN, .09, .95, "跳过") != -1) {
            throw new AssertionError("different wording must not match");
        }
        if (AdRules.matchHint(Arrays.asList(root(), node(0, spot, "", "4")), SCREEN, .09, .95, "5") != -1) {
            throw new AssertionError("stored countdown must never match a comment count or unread badge");
        }
        if (AdRules.matchHint(Arrays.asList(root(), node(0, spot, "", "跳过 4")), SCREEN, .09, .95, "跳过 5") != 1) {
            throw new AssertionError("labelled skip countdown may change numbers");
        }
        AdRules.Box far = new AdRules.Box(700, 1200, 800, 1300);
        if (AdRules.matchHint(Arrays.asList(root(), node(0, far, "", "跳过")), SCREEN, .09, .95, "跳过") != -1) {
            throw new AssertionError("hint must not match a different position");
        }
        System.out.println("PASS hint learning checks");
    }

    private static final class HintStore implements AdHints.Store {
        private String value = "";

        @Override public String read() {
            return value;
        }

        @Override public void write(String next) {
            value = next;
        }
    }

    private static void verifyFeedback() {
        FeedbackStore store = new FeedbackStore();
        AdFeedback feedback = new AdFeedback(store);
        String signature = AdMemory.signature("tv.danmaku.bili", "VideoDetail", 1080, 1920);
        if (feedback.allowed(signature) || feedback.denied(signature)) {
            throw new AssertionError("empty feedback must say nothing");
        }
        feedback.deny(signature);
        if (!feedback.denied(signature)) throw new AssertionError("a refusal must stick");
        feedback.allow(signature);
        if (!feedback.allowed(signature) || feedback.denied(signature)) {
            throw new AssertionError("confirmation must replace an earlier refusal");
        }
        if (!new AdFeedback(store).allowed(signature)) throw new AssertionError("feedback must persist");
        feedback.clear();
        if (feedback.size() != 0) throw new AssertionError("clear must empty the store");

        AdRules.Box skipBox = new AdRules.Box(40, 1760, 160, 1860);
        if (!AdRules.pageLike(Arrays.asList(root(), new AdRules.Node(0, SCREEN, false, true, false, "", null, null)))) {
            throw new AssertionError("scrollable window must count as a page");
        }
        if (AdRules.pageLike(Arrays.asList(root(), node(0, skipBox, "", "跳过 5")))) {
            throw new AssertionError("a splash without scroll must not count as a page");
        }
        if (AdRules.cornerSkipCandidate(Arrays.asList(root(), node(0, skipBox, "", "跳过")), SCREEN) != 1) {
            throw new AssertionError("a corner skip must be detected without ad evidence");
        }
        if (AdRules.cornerSkipCandidate(Arrays.asList(root(), node(0, new AdRules.Box(480, 900, 600, 1000), "", "跳过")), SCREEN) != -1) {
            throw new AssertionError("a centred skip is not a corner candidate");
        }
        if (AdRules.matchHint(Arrays.asList(root(), node(0, skipBox, "", "更多")), SCREEN, .09, .95, "") != -1) {
            throw new AssertionError("an unlabeled hint must not match an arbitrary icon");
        }
        if (AdRules.matchHint(Arrays.asList(root(), node(0, skipBox, "", "关闭")), SCREEN, .09, .95, "") != 1) {
            throw new AssertionError("an unlabeled hint must still match a dismiss control");
        }
        System.out.println("PASS confirmation feedback checks");
    }

    private static final class FeedbackStore implements AdFeedback.Store {
        private String value = "";

        @Override public String read() {
            return value;
        }

        @Override public void write(String next) {
            value = next;
        }
    }

    private static void verifyActionLog() {
        ActionStore store = new ActionStore();
        AdActionLog log = new AdActionLog(store);
        for (int index = 0; index < 45; index++) {
            log.add("pkg/Act/1080x1920", "reason" + index, "detail", 1000 + index);
        }
        if (log.recent().size() != 40) throw new AssertionError("log must stay bounded, got " + log.recent().size());
        if (!"reason44".equals(log.recent().get(0).reason)) throw new AssertionError("newest action must come first");
        if (new AdActionLog(store).recent().size() != 40) throw new AssertionError("log must survive a reload");
        log.clear();
        if (log.recent().size() != 0) throw new AssertionError("clear must empty the log");
        store.value = "60400000\tpkg/Act/1080x1920\t截图教学\t画面匹配";
        if (log.recent().get(0).hasClockTime()) throw new AssertionError("legacy uptime is not a wall clock timestamp");
        long localClock = 1794380047000L;
        String id = log.add("pkg/Act/1080x1920", "截图教学", "画面匹配", localClock, "手势请求已提交");
        new AdActionLog(store).update(id, "手势已执行 · 广告画面仍在");
        AdActionLog.Entry updated = log.recent().get(0);
        if (!updated.hasClockTime() || updated.at != localClock || !"手势已执行 · 广告画面仍在".equals(updated.outcome)
                || log.recent().size() != 2) {
            throw new AssertionError("an action must retain its time when its status changes");
        }
        new AdActionLog(store).clear();
        log.update(id, "原开屏窗口已离开");
        if (!log.recent().isEmpty()) throw new AssertionError("a late callback must not resurrect a cleared record");
        System.out.println("PASS action log checks");
    }

    private static void verifyTaughtDismissControl() {
        AdRules.Box skip = new AdRules.Box(970, 100, 1030, 158);
        AdRules.Box outside = new AdRules.Box(780, 100, 846, 158);
        if (AdRules.taughtDismissNear(Arrays.asList(root(), button(0, skip, "", "立即购买", ""),
                button(0, outside, "", "跳过", ""), button(0, skip, "", "跳过", "")), SCREEN, 994, 134) != 3) {
            throw new AssertionError("the live control at the taught Damai point must be preferred");
        }
        if (AdRules.taughtDismissNear(Arrays.asList(root(), button(0, outside, "", "跳过", ""),
                button(0, skip, "", "更多", "")), SCREEN, 994, 134) != -1) {
            throw new AssertionError("an unrelated control or far-away skip must not replace the taught point");
        }
        System.out.println("PASS taught close control alignment");
    }

    private static void verifyTaughtLiveRule() {
        AdRules.Box display = new AdRules.Box(0, 0, 1080, 2400);
        AdRules.Box viewport = new AdRules.Box(0, 60, 1080, 2340);
        AdRules.Box skipBox = new AdRules.Box(965, 144, 1025, 192);
        AdRules.Box bottom = new AdRules.Box(55, 2190, 150, 2240);
        AdRules.Node root = node(-1, viewport, "", "");
        AdRules.Node skip = button(0, skipBox, "", "跳过", "");
        AdRules.Node details = node(0, new AdRules.Box(180, 1940, 900, 2060), "", "点击跳转详情页 ＞");
        TaughtTargets.Target damai = new TaughtTargets.Target("cn.damai", "*", 920, 70, 450, null, 0, 0);
        TaughtTargets.Target otherAd = new TaughtTargets.Target("cn.damai", "*", 95, 925, 450, null, 0, 0);
        java.util.List<TaughtTargets.Target> lessons = Arrays.asList(otherAd, damai);
        java.util.List<AdRules.Node> splash = Arrays.asList(root, skip, details);
        AdRules.TaughtControl found = AdRules.chooseTaughtControl(splash, viewport, display, lessons, false);
        if (AdRules.choose(splash, viewport, false) != -1
                || found == null || found.index != 1 || found.lesson != damai) {
            throw new AssertionError("teaching should add the current Damai skip control to direct recognition");
        }
        if (AdRules.chooseTaughtControl(Arrays.asList(root, skip), viewport, display, lessons, false) != null) {
            throw new AssertionError("a plain onboarding skip without ad context must not be clicked");
        }
        if (AdRules.chooseTaughtControl(Arrays.asList(root, details, button(0, skipBox, "", "立即购买", "")),
                viewport, display, lessons, true) != null) {
            throw new AssertionError("a taught point must never turn a purchase control into a skip");
        }
        if (AdRules.chooseTaughtControl(Arrays.asList(root, skip, details,
                node(0, new AdRules.Box(80, 600, 990, 1480), "", "确认支付")),
                viewport, display, lessons, true) != null) {
            throw new AssertionError("protected UI must still block a taught candidate");
        }
        if (AdRules.chooseTaughtControl(Arrays.asList(root, skip, details, button(0, bottom, "", "跳过", "")),
                viewport, display, lessons, false) != null) {
            throw new AssertionError("different live controls matched by different lessons are ambiguous");
        }
        AdRules.Node paintedSkip = node(0, skipBox, "", "跳过");
        found = AdRules.chooseTaughtControl(Arrays.asList(root, paintedSkip, details), viewport, display, lessons, false);
        if (found == null || found.index != 1 || paintedSkip.clickable) {
            throw new AssertionError("a live labelled skip can provide current touch bounds");
        }
        System.out.println("PASS taught records feed the live skip rule with context and ambiguity checks");
    }

    private static final class ActionStore implements AdActionLog.Store {
        private String value = "";

        @Override public String read() {
            return value;
        }

        @Override public void write(String next) {
            value = next;
        }
    }

    private static void verifyTaught() {
        TaughtStore store = new TaughtStore();
        TaughtTargets taught = new TaughtTargets(store);
        int[] template = new int[TaughtTargets.TEMPLATE_SIZE * TaughtTargets.TEMPLATE_SIZE];
        for (int index = 0; index < template.length; index++) template[index] = (index * 7) % 256;
        taught.save("tv.danmaku.bili", "*", .92, .05, 1080 / 1920.0, template);
        TaughtTargets.Target found = taught.find("tv.danmaku.bili", "AnyActivity");
        if (found == null) throw new AssertionError("wildcard entry must match any activity");
        if (Math.abs(found.xRatio() - .92) > .01 || Math.abs(found.yRatio() - .05) > .01) {
            throw new AssertionError("taught ratios drifted");
        }
        if (!found.hasTemplate()) throw new AssertionError("template must survive the encode round trip");
        taught.save("tv.danmaku.bili", "Splash", .5, .5, 1, template);
        if (taught.find("tv.danmaku.bili", "Splash").xRatio() > .6) throw new AssertionError("activity entry must win");
        if (taught.find("other.pkg", "Splash") != null) throw new AssertionError("package must be part of the key");
        if (new TaughtTargets(store).size() != 2) throw new AssertionError("taught targets must persist");
        taught.remove(found.key());
        if (taught.find("tv.danmaku.bili", "Another") != null) throw new AssertionError("remove must drop the entry");

        int width = 240;
        int height = 520;
        int[] frame = new int[width * height];
        java.util.Arrays.fill(frame, 0xff101010);
        for (int y = 470; y < 500; y++) {
            for (int x = 200; x < 230; x++) frame[y * width + x] = 0xfff0f0f0;
        }
        int[] live = VisionFinder.templateAt(frame, width, height, .9, .93);
        if (live == null) throw new AssertionError("template must be readable from a frame");
        taught.save("example.visual", "*", .9, .93, width / (double) height, live);
        TaughtTargets.Target visual = taught.find("example.visual", "Splash");
        if (VisionFinder.matchTaught(frame, width, height, visual) == null) {
            throw new AssertionError("previously saved taught crop must match");
        }
        int[] wholeFrame = AdFingerprint.of(frame, width, height);
        taught.save("example.withfingerprint", "*", .9, .93, width / (double) height, live, wholeFrame);
        TaughtTargets reopened = new TaughtTargets(store);
        TaughtTargets.Target withFingerprint = reopened.find("example.withfingerprint", "Splash");
        reopened.confirmed(reopened.find("example.visual", "Splash"));
        if (!Arrays.equals(new TaughtTargets(store).find("example.withfingerprint", "Splash").fingerprint, wholeFrame)
                || VisionFinder.matchTaught(frame, width, height, withFingerprint) == null) {
            throw new AssertionError("an existing whole-screen fingerprint must survive a lesson update and match");
        }
        int[] changedBackground = Arrays.copyOf(frame, frame.length);
        Arrays.fill(changedBackground, 0, width * 400, 0xffeeeeee);
        if (VisionFinder.matchTaught(changedBackground, width, height, withFingerprint) != null) {
            throw new AssertionError("stored whole-screen constraint must still be respected");
        }
        if (VisionFinder.matchTaught(frame, height, width, visual) != null) {
            throw new AssertionError("rotated display must not match");
        }
        Arrays.fill(frame, 0xff101010);
        if (VisionFinder.matchTaught(frame, width, height, visual) != null) {
            throw new AssertionError("plain or blank live crop must not match");
        }
        TaughtStore previewStore = new TaughtStore();
        String layout = AdFingerprint.encode(wholeFrame);
        String newerFingerprint = layout + "|0000|40,130,140";
        previewStore.value = "example.older\t*\t900\t930\t462\t0\t0\t" + TaughtTargets.encodeTemplate(live)
                + "\nexample.parallel\t*\t900\t930\t462\t0\t0\t" + TaughtTargets.encodeTemplate(live)
                + "\t" + newerFingerprint;
        TaughtTargets withPreview = new TaughtTargets(previewStore);
        if (!withPreview.find("example.older", "Splash").preview.isEmpty()
                || !withPreview.find("example.parallel", "Splash").hasFingerprint()) {
            throw new AssertionError("older lessons must load without thumbnails");
        }
        withPreview.save("example.new", "*", .9, .93, width / (double) height, live, null, "dGh1bWI=");
        TaughtTargets reloaded = new TaughtTargets(previewStore);
        TaughtTargets.Target lesson = reloaded.find("example.new", "Splash");
        reloaded.confirmed(lesson);
        if (!"dGh1bWI=".equals(new TaughtTargets(previewStore).find("example.new", "Splash").preview)
                || !previewStore.value.contains(newerFingerprint)) {
            throw new AssertionError("preview and stored fingerprint metadata must survive stats updates");
        }
        reloaded.remove(lesson.key());
        if (previewStore.value.contains("dGh1bWI=")) {
            throw new AssertionError("the thumbnail must be removed with its lesson");
        }
        System.out.println("PASS taught target checks");
    }

    private static final class TaughtStore implements TaughtTargets.Store {
        private String value = "";

        @Override public String read() {
            return value;
        }

        @Override public void write(String next) {
            value = next;
        }
    }

    private static void verifyRepeatGuard() {
        AdRules.Box heading = new AdRules.Box(30, 300, 480, 410);
        RepeatGuard.Page first = RepeatGuard.pageOf("example.video", "Frame", Arrays.asList(root(),
                node(0, heading, "test:id/title", "我的影片"), button(0, CORNER, "", "跳过广告丨5", "")), SCREEN, null);
        RepeatGuard.Page tick = RepeatGuard.pageOf("example.video", "Frame", Arrays.asList(root(),
                button(0, CORNER, "", "跳过广告丨4", ""), node(0, heading, "test:id/title", "我的影片")), SCREEN, null);
        RepeatGuard.Page other = RepeatGuard.pageOf("example.video", "Frame", Arrays.asList(root(),
                node(0, heading, "test:id/title", "下载管理")), SCREEN, null);
        if (!first.same(tick) || first.same(other)) {
            throw new AssertionError("page identity must ignore countdown and traversal order, but separate distinct content");
        }
        Set<String> empty = Collections.emptySet();
        RepeatGuard guard = new RepeatGuard();
        guard.observe(first, 4, 1000);
        guard.observe(tick, 4, 1040);
        if (guard.decision(1040, 2, 10, empty) != RepeatGuard.Decision.ALLOW) {
            throw new AssertionError("scans of the same appearance do not count again");
        }
        guard.finishAttempt(true);
        if (guard.decision(1050, 2, 10, empty) != RepeatGuard.Decision.STOP) {
            throw new AssertionError("one accepted click per appearance");
        }
        if (guard.missing(4, 1100)) throw new AssertionError("a single empty scan is not an exit");
        guard.observe(tick, 4, 1120);
        if (guard.missing(4, 1200) || !guard.missing(4, 1370)) {
            throw new AssertionError("confirmed disappearance should end the turn, even on the same window");
        }
        guard.observe(first, 4, 1400);
        if (guard.decision(1400, 2, 10, empty) != RepeatGuard.Decision.ASK
                || guard.decision(1500, 2, 10, empty) != RepeatGuard.Decision.STOP) {
            throw new AssertionError("second appearance must ask once and stay paused");
        }
        RepeatGuard.Turn pending = guard.active();
        if (!guard.approve(pending) || guard.decision(1510, 2, 10, empty) != RepeatGuard.Decision.ALLOW) {
            throw new AssertionError("yes allows revalidated current turn");
        }
        guard.finishAttempt(true);
        if (guard.decision(1550, 2, 10, empty) != RepeatGuard.Decision.STOP) {
            throw new AssertionError("approval can never allow a second click");
        }
        guard.observe(other, 5, 1800);
        if (guard.approve(pending)) throw new AssertionError("a stale answer must not approve a different page");
        Set<String> denied = Collections.singleton(first.encode());
        if (guard.decision(1810, 2, 10, denied) != RepeatGuard.Decision.ALLOW
                || !tick.denied(denied) || RepeatGuard.Page.decode(first.encode()) == null
                || new RepeatGuard.Page("example.other", first.layout, null).denied(denied)) {
            throw new AssertionError("persistent no applies to the same page, independently of the app and timer");
        }
        guard.observe(first, 6, 1900);
        if (guard.decision(1900, 3, 60, denied) != RepeatGuard.Decision.STOP) {
            throw new AssertionError("persistent no overrides a configurable count");
        }
        guard.leave();
        guard.observe(first, 7, 13000);
        if (guard.decision(13000, 2, 10, empty) != RepeatGuard.Decision.ALLOW) {
            throw new AssertionError("old appearances expire and removed denial no longer applies");
        }
        RepeatGuard configured = new RepeatGuard();
        for (int i = 1; i <= 3; i++) {
            long time = 2000 + i * 800;
            configured.observe(first, i, time);
            RepeatGuard.Decision expected = i < 3 ? RepeatGuard.Decision.ALLOW : RepeatGuard.Decision.ASK;
            if (configured.decision(time, 3, 4, empty) != expected) {
                throw new AssertionError("the configured threshold is applied before the click");
            }
        }
        System.out.println("PASS repeat appearance protection, saved denial and one-use approval");
    }

    private static void verifySceneFingerprint() {
        int width = 120, height = 240;
        int[] frame = new int[width * height];
        int[] flipped = new int[frame.length];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int color = x < width / 2 ? 0xff2855cd : 0xffca3b20;
                frame[y * width + x] = color;
                flipped[y * width + width - 1 - x] = color;
            }
        }
        SceneFingerprint scene = SceneFingerprint.of(frame, width, height);
        SceneFingerprint rearranged = SceneFingerprint.of(flipped, width, height);
        if (!scene.matches(SceneFingerprint.decode(scene.encode())) || scene.matches(rearranged)
                || SceneFingerprint.decode("broken") != null) {
            throw new AssertionError("spatial color/gray fingerprints must distinguish a reordered palette");
        }
        RepeatGuard.Page original = new RepeatGuard.Page("example.visual", "same-layout", scene);
        RepeatGuard.Page different = new RepeatGuard.Page("example.visual", "same-layout", rearranged);
        if (!original.denied(Collections.singleton(original.encode()))
                || different.denied(Collections.singleton(original.encode()))) {
            throw new AssertionError("a visually identified no must not block another image of the same layout");
        }
        TaughtStore store = new TaughtStore();
        TaughtTargets taught = new TaughtTargets(store);
        taught.save("example.visual", "*", .8, .09, .5, new int[256], null, "preview", scene);
        TaughtTargets.Target loaded = new TaughtTargets(store).find("example.visual", "Splash");
        if (!scene.matches(loaded.scene) || !"preview".equals(loaded.preview)) {
            throw new AssertionError("new fingerprints and existing thumbnails must round trip together");
        }
        System.out.println("PASS RGB, grayscale and Lab spatial fingerprint checks");
    }

    private static void verifyMultipleLessons() {
        int width = 120, height = 240;
        int[] posterOne = new int[width * height], posterTwo = new int[posterOne.length];
        Arrays.fill(posterOne, 0xff093b92);
        Arrays.fill(posterTwo, 0xff226130);
        for (int y = 16; y < 27; y++) {
            for (int x = 100; x < 110; x++) posterOne[y * width + x] = 0xffffffff;
        }
        for (int y = 199; y < 211; y++) {
            for (int x = 22; x < 31; x++) posterTwo[y * width + x] = 0xffffffff;
        }
        double x1 = .84, y1 = .09, x2 = .20, y2 = .85;
        int[] firstCrop = VisionFinder.templateAt(posterOne, width, height, x1, y1);
        int[] secondCrop = VisionFinder.templateAt(posterTwo, width, height, x2, y2);
        SceneFingerprint firstScene = SceneFingerprint.of(posterOne, width, height);
        SceneFingerprint secondScene = SceneFingerprint.of(posterTwo, width, height);

        TaughtStore store = new TaughtStore();
        TaughtTargets targets = new TaughtTargets(store);
        targets.save("example.ticket", "*", x1, y1, .5, firstCrop, null, "first", firstScene);
        targets.save("example.ticket", "*", x2, y2, .5, secondCrop, null, "second", secondScene);
        targets = new TaughtTargets(store);
        if (targets.forWindow("example.ticket", "Home").size() != 2) {
            throw new AssertionError("the second lesson must preserve the first");
        }
        TaughtSearch.Match first = TaughtSearch.unambiguous(
                TaughtSearch.search(posterOne, width, height, targets.all(), firstScene, false), width, height);
        TaughtSearch.Match second = TaughtSearch.unambiguous(
                TaughtSearch.search(posterTwo, width, height, targets.all(), secondScene, false), width, height);
        if (first == null || second == null || !"first".equals(first.lesson.preview)
                || !"second".equals(second.lesson.preview) || first.lesson.key().equals(second.lesson.key())) {
            throw new AssertionError("both posters must match their own stored position and identity");
        }
        targets.save("example.other", "*", x1, y1, .5, firstCrop, null, "same-picture", firstScene);
        java.util.List<TaughtSearch.Match> ambiguous =
                TaughtSearch.search(posterOne, width, height, targets.all(), firstScene, false);
        if (ambiguous.size() != 2 || TaughtSearch.unambiguous(ambiguous, width, height) != null) {
            throw new AssertionError("cross-app ambiguity must not silently choose a package");
        }
        if (!targets.move(first.lesson.key(), "example.corrected")
                || targets.forWindow("example.ticket", "Home").size() != 1
                || targets.find("example.corrected", "Home") == null) {
            throw new AssertionError("moving one lesson must retain the other poster");
        }
        String movedKey = targets.find("example.corrected", "Home").key();
        targets.save("example.corrected", "*", x1, y1, .5, firstCrop, null, "first", firstScene);
        if (!movedKey.equals(targets.find("example.corrected", "Home").key()) || targets.size() != 3) {
            throw new AssertionError("teaching the same photo again must update its own record");
        }
        for (int i = 0; i < 32; i++) {
            targets.save("example.additional", "*", .4, .6, .5, null, null, "extra-" + i);
        }
        if (new TaughtTargets(store).find("example.corrected", "Home") == null) {
            throw new AssertionError("older lessons must not be evicted when more photos are added");
        }
        targets.remove(movedKey);
        if (targets.find("example.corrected", "Home") != null || targets.find("example.ticket", "Home") == null) {
            throw new AssertionError("forgetting one lesson must not remove another");
        }
        System.out.println("PASS multiple lessons, independent matching, global ambiguity, reassign and forget");
    }

    private static void verifyVision() {
        int width = 240;
        int height = 520;
        int[] blank = new int[width * height];
        java.util.Arrays.fill(blank, 0xff1e1e1e);
        if (VisionFinder.find(blank, width, height) != null) throw new AssertionError("blank frame");

        int[] corner = blank.clone();
        for (int y = 470; y < 492; y++) {
            for (int x = 14; x < 44; x++) corner[y * width + x] = 0xfff2f2f2;
        }
        VisionFinder.Point point = VisionFinder.find(corner, width, height);
        if (point == null) throw new AssertionError("corner button must be found");
        if (Math.abs(point.x - 29) > 6 || Math.abs(point.y - 481) > 6) {
            throw new AssertionError("wrong corner point " + point.x + "," + point.y);
        }

        int[] strip = blank.clone();
        for (int y = 500; y < height; y++) {
            for (int x = 0; x < width; x++) strip[y * width + x] = 0xfff2f2f2;
        }
        if (VisionFinder.find(strip, width, height) != null) throw new AssertionError("full width strip accepted");

        int[] centre = blank.clone();
        for (int y = 240; y < 262; y++) {
            for (int x = 100; x < 130; x++) centre[y * width + x] = 0xfff2f2f2;
        }
        if (VisionFinder.find(centre, width, height) != null) throw new AssertionError("centre button accepted");
        System.out.println("PASS vision corner checks");
    }

    private static AdRules.Node root() {
        return node(-1, SCREEN, "", "");
    }

    private static AdRules.Node node(int parent, AdRules.Box box, String id, String text) {
        return new AdRules.Node(parent, box, false, false, false, id, text, "");
    }

    private static AdRules.Node button(int parent, AdRules.Box box, String id, String text, String description) {
        return new AdRules.Node(parent, box, true, false, false, id, text, description);
    }
}
