package local.qingtap.adskip;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

final class ScreenshotReview {
    private static final Pattern SKIP = Pattern.compile(
            "(?:[0-9]{1,2}(?:秒|s)?)?(?:跳过(?:此)?广告|关闭(?:此|当前)?广告|跳过|skip(?:ad)?)"
                    + "(?:[0-9]{1,2}(?:秒|s)?)?");
    private static final Pattern BADGE = Pattern.compile("广告|开屏广告|互动广告.{0,12}|advertisement|sponsored");
    private static final Pattern CTA = Pattern.compile(
            "(?:点击|上滑|向上滑动|摇一摇|扭一扭|翻转手机).{0,16}(?:跳转|前往|打开|查看|详情).{0,18}");

    final List<String> apps;
    final ScreenshotOcr.Line button;
    final boolean safeToTeach;

    private ScreenshotReview(Set<String> apps, ScreenshotOcr.Line button, boolean safeToTeach) {
        this.apps = new ArrayList<>(apps);
        this.button = button;
        this.safeToTeach = safeToTeach;
    }

    static String clean(String text) {
        return text.toLowerCase(Locale.ROOT).replaceAll("[\\s|丨｜:：·,，()（）]", "");
    }

    static ScreenshotReview inspect(List<ScreenshotOcr.Line> lines, Map<String, Set<String>> brands,
                                    int width, int height) {
        Set<String> apps = new LinkedHashSet<>();
        boolean adContext = false;
        ScreenshotOcr.Line button = null;
        boolean competing = false;
        for (ScreenshotOcr.Line line : lines) {
            ScreenshotOcr.Box box = line.box;
            String label = clean(line.text);
            float x = box.x() / width, y = box.y() / height;
            if (line.confidence >= .84f && ((x < .37f && y < .17f) || (x > .29f && x < .71f && y > .83f))) {
                Set<String> packages = brands.get(label);
                if (packages != null) apps.addAll(packages);
            }
            if (line.confidence >= .78f && (y < .24f && BADGE.matcher(label).matches()
                    || y > .58f && CTA.matcher(label).matches())) adContext = true;
            boolean edge = (y < .20f || y > .78f) && (x < .24f || x > .76f);
            if (line.confidence < .79f || !edge || box.width() > width * .31f || box.height() > height * .075f
                    || !SKIP.matcher(label).matches()) continue;
            if (button != null && (Math.abs(button.box.x() - box.x()) > width * .045f
                    || Math.abs(button.box.y() - box.y()) > height * .022f)) competing = true;
            if (button == null || button.confidence < line.confidence) button = line;
        }
        if (competing) button = null;
        boolean explicit = button != null && (clean(button.text).contains("广告") || clean(button.text).contains("skipad"));
        return new ScreenshotReview(apps, button,
                button != null && button.confidence >= .85f && apps.size() == 1 && (explicit || adContext));
    }
}
