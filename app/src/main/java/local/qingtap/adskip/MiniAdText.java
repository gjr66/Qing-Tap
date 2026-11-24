package local.qingtap.adskip;

import java.util.regex.Pattern;

// WeChat cover ads use “4秒 | 关闭”, not just “跳过 4”. Keep this vocabulary local to mini-app ads.
final class MiniAdText {
    private static final String NUMBER = "[0-9]{1,2}(?:秒|s)?";
    private static final String CLOSE = "(?:关闭(?:此|当前)?(?:广告)?|跳过(?:此)?(?:广告)?|skip(?:ad)?|close(?:ad)?)";
    private static final Pattern COUNTDOWN = Pattern.compile("(?:" + NUMBER + CLOSE + "|" + CLOSE + NUMBER + ")");
    private static final Pattern EXPLICIT = Pattern.compile(
            "(?:关闭(?:此|当前)?广告|跳过(?:此)?广告|隐藏(?:此)?广告|关闭广告位|点击跳过|"
                    + "skipad|closead|hidead)");
    private static final Pattern ICON = Pattern.compile("[×✕✖✗╳]");

    static String clean(CharSequence text) {
        return AdRules.clean(text).replaceAll("[\\s|丨｜:：·()（）]", "");
    }

    static boolean countdownClose(CharSequence text) {
        String value = clean(text);
        int number = AdRules.firstNumber(value);
        return number >= 0 && number <= 30 && COUNTDOWN.matcher(value).matches();
    }

    static boolean bareClose(CharSequence text) {
        return clean(text).matches(CLOSE);
    }

    static boolean explicitClose(CharSequence text) {
        String value = clean(text);
        return EXPLICIT.matcher(value).matches();
    }

    static boolean iconClose(CharSequence text) {
        return ICON.matcher(clean(text)).matches();
    }

    static float closeFraction(CharSequence text) {
        if (!countdownClose(text)) return .5f;
        return Character.isDigit(clean(text).charAt(0)) ? .75f : .25f;
    }

    static boolean badge(CharSequence text) {
        return clean(text).matches("(?:广告|开屏广告|封面广告|ad|advertisement)[vV∨⌄▼▽﹀>›]*");
    }

    private MiniAdText() {}
}
