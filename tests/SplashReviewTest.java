package local.qingtap.adskip;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class SplashReviewTest {
    public static void main(String[] args) {
        AdRules.Box screen = new AdRules.Box(0, 0, 1080, 2373);
        AdRules.Node root = new AdRules.Node(-1, screen, false, false, false, "", "", "");
        AdRules.Node skip = node(new AdRules.Box(950, 120, 1030, 210), "跳过");
        AdRules.Node promo = node(new AdRules.Box(150, 1780, 930, 1950), "点击跳转至详情页面");
        check(DamaiSplash.startup("cn.damai", "cn.damai.homepage.ui.SplashMainActivity", 180), "Damai splash");
        check(!DamaiSplash.startup("cn.damai", "cn.damai.HomeActivity", 180), "Damai home");
        check(!DamaiSplash.startup("cn.damai", "SplashMainActivity", 6010), "expired startup");
        check(AdRules.chooseDamaiSkip(Arrays.asList(root, promo, skip), screen) == 2, "choose live skip");
        AdRules.Node glyph = new AdRules.Node(1, new AdRules.Box(962, 137, 1019, 191),
                false, false, false, "", "跳过", "");
        check(AdRules.chooseDamaiSkip(Arrays.asList(root, skip, glyph), screen) == 1,
                "prefer actionable skip control to its label");
        check(AdRules.chooseDamaiSkip(Arrays.asList(root, promo), screen) == -1, "reject CTA");
        AdRules.Node resource = new AdRules.Node(1, new AdRules.Box(950, 120, 1030, 210),
                false, false, false, AdRules.DAMAI_SKIP_ID, "", "");
        AdRules.Node targetParent = node(new AdRules.Box(935, 112, 1048, 219), "");
        check(AdRules.chooseDamaiSkip(Arrays.asList(root, targetParent, resource), screen) == 2,
                "find the exact Damai resource even without a label");
        check(AdRules.damaiSkipParent(resource, targetParent, screen), "allow tight clickable skip parent");
        check(!AdRules.damaiSkipParent(resource, node(screen, ""), screen), "reject full-screen parent");
        check(!AdRules.damaiSkipParent(resource, node(targetParent.box, "点击跳转至详情页面"), screen),
                "reject detail CTA parent");
        AdRules.Node payment = node(new AdRules.Box(200, 500, 700, 570), "确认支付");
        check(AdRules.chooseDamaiSkip(Arrays.asList(root, skip, payment), screen) == -1, "reject payment");

        Map<String, Set<String>> brands = new HashMap<>();
        brands.put("腾讯视频", Collections.singleton("com.tencent.qqlive"));
        brands.put("大麦", Collections.singleton("cn.damai"));
        ScreenshotOcr.Line video = line(50, 140, 230, 205, "腾讯视频");
        ScreenshotOcr.Line badge = line(666, 155, 870, 207, "互动广告 京东");
        ScreenshotOcr.Line right = line(925, 156, 1030, 203, "跳过");
        ScreenshotOcr.Line cta = line(250, 1820, 850, 1920, "点击跳转至详情页面");
        ScreenshotReview result = review(brands, video, badge, right, cta);
        check(result.safeToTeach && result.apps.equals(Collections.singletonList("com.tencent.qqlive"))
                && result.button == right, "auto teach using host and ad context");
        result = review(brands, badge, right, cta);
        check(!result.safeToTeach && result.apps.isEmpty() && result.button == right, "retain marker without host");
        check(!review(brands, video, right).safeToTeach, "plain skip without ad evidence");
        check(!review(brands, video, badge, cta).safeToTeach, "CTA never a close control");
        ScreenshotOcr.Line bottom = line(440, 2050, 630, 2140, "大麦");
        check(!review(brands, video, bottom, badge, right).safeToTeach, "conflicting host evidence");
        ScreenshotOcr.Line left = line(35, 151, 124, 202, "跳过");
        check(review(brands, video, badge, right, left).button == null, "conflicting close locations");
        checkLowContrastLesson();
        System.out.println("PASS Damai window safeguards and screenshot OCR evidence checks");
    }

    private static void checkLowContrastLesson() {
        int width = 360, height = 792;
        int[] pixels = new int[width * height];
        Arrays.fill(pixels, 0x202020);
        for (int y = 35; y < 100; y++) {
            for (int x = 296; x < 350; x++) {
                pixels[y * width + x] = x < 323 ? 0x505050 : 0x707070;
            }
        }
        int[] template = VisionFinder.templateAt(pixels, width, height, .899, .087);
        check(VisionFinder.usableTemplate(template), "low contrast can be taught with scene evidence");
        SceneFingerprint scene = SceneFingerprint.of(pixels, width, height);
        TaughtTargets.Target target = new TaughtTargets.Target("com.tencent.qqlive", "*", 899, 87,
                455, template, "", "", scene, 0, 0);
        check(VisionFinder.matchTaughtQuick(pixels, width, height, target, scene) != null,
                "same scene accepts low contrast skip");
        check(VisionFinder.matchTaught(pixels, width, height, target) == null,
                "button alone requires greater contrast");
        int[] different = pixels.clone();
        Arrays.fill(different, width * 110, different.length, 0xb0e000);
        check(VisionFinder.matchTaughtQuick(different, width, height, target,
                SceneFingerprint.of(different, width, height)) == null, "other scene rejects low contrast skip");
    }

    private static AdRules.Node node(AdRules.Box box, String label) {
        return new AdRules.Node(0, box, true, false, false, "", label, "");
    }

    private static ScreenshotOcr.Line line(int x, int y, int right, int bottom, String text) {
        return new ScreenshotOcr.Line(new ScreenshotOcr.Box(x, y, right, bottom), text, .98f);
    }

    private static ScreenshotReview review(Map<String, Set<String>> brands, ScreenshotOcr.Line... lines) {
        List<ScreenshotOcr.Line> all = Arrays.asList(lines);
        return ScreenshotReview.inspect(all, brands, 1080, 2373);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
