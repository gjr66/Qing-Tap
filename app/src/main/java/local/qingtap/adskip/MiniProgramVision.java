package local.qingtap.adskip;

import java.util.List;

// OCR of the screen around a mini-app ad. A separate ad badge and live dismissal wording are both
// required. Coordinates come from live OCR, never from a fixed tap position.
final class MiniProgramVision {
    static final class Target {
        final float x, y, centerX;
        final String label;
        final int lockerSeconds;
        final boolean lockerModal;

        Target(float x, float y, float centerX, String label) {
            this(x, y, centerX, label, -1);
        }

        Target(float x, float y, float centerX, String label, int lockerSeconds) {
            this(x, y, centerX, label, lockerSeconds, false);
        }

        static Target lockerModal(float x, float y) {
            return new Target(x, y, x, "商品广告关闭按钮", -1, true);
        }

        private Target(float x, float y, float centerX, String label, int lockerSeconds, boolean lockerModal) {
            this.lockerModal = lockerModal;
            this.lockerSeconds = lockerSeconds;
            this.x = x;
            this.y = y;
            this.centerX = centerX;
            this.label = label;
        }

        // Compare the text box, not the tap fraction: “4秒|关闭” and “关闭|4秒” put the tap on
        // opposite sides of the same button, but must still confirm as the same control.
        boolean confirms(Target previous) {
            if (!near(previous) || lockerModal != previous.lockerModal) return false;
            if (lockerSeconds < 0) return previous.lockerSeconds < 0;
            return lockerSeconds > 0 && previous.lockerSeconds > lockerSeconds;
        }

        boolean near(Target other) {
            return other != null && Math.abs(centerX - other.centerX) < .045f
                    && Math.abs(y - other.y) < .022f;
        }
    }

    private static final float BADGE_CONFIDENCE = .78f;
    private static final float CLOSE_CONFIDENCE = .82f;
    private static final float ICON_CONFIDENCE = .88f;

    static Target inspect(List<ScreenshotOcr.Line> lines, int width, int height) {
        if (sensitive(lines) || !hasBadge(lines, width, height)) return null;
        return target(lines, width, height, false);
    }

    // Bottom-strip pass, used only after the top pass found an ad badge. Alipay interstitials often
    // put “关闭广告” at the bottom edge while the badge stays at the top.
    static Target inspectBottom(List<ScreenshotOcr.Line> lines, int width, int height) {
        if (sensitive(lines)) return null;
        return target(lines, width, height, true);
    }

    static boolean hasBadge(List<ScreenshotOcr.Line> lines, int width, int height) {
        for (ScreenshotOcr.Line line : lines) {
            if (line.confidence < BADGE_CONFIDENCE || !MiniAdText.badge(line.text)) continue;
            if (line.box.width() > width * .30f || line.box.height() > height * .055f) continue;
            float y = line.box.y() / height;
            if (y >= .05f && y <= .95f) return true;
        }
        return false;
    }

    private static Target target(List<ScreenshotOcr.Line> lines, int width, int height, boolean bottomOnly) {
        Target result = null;
        for (ScreenshotOcr.Line line : lines) {
            if (!closeLine(line, width, height, bottomOnly)) continue;
            float x = (line.box.left + line.box.width() * MiniAdText.closeFraction(line.text)) / width;
            float y = line.box.y() / height;
            float centerX = line.box.x() / width;
            Target candidate = new Target(x, y, centerX, line.text);
            if (result != null && !result.near(candidate)) return null;
            result = candidate;
        }
        return result;
    }

    private static boolean closeLine(ScreenshotOcr.Line line, int width, int height, boolean bottomOnly) {
        if (line.confidence < CLOSE_CONFIDENCE) return false;
        if (line.box.width() > width * .32f || line.box.height() > height * .07f
                || line.box.width() < 4 || line.box.height() < 4) return false;
        boolean countdown = MiniAdText.countdownClose(line.text);
        boolean explicit = MiniAdText.explicitClose(line.text);
        boolean bare = MiniAdText.bareClose(line.text);
        boolean icon = MiniAdText.iconClose(line.text);
        if (!countdown && !explicit && !bare && !icon) return false;
        if (icon && line.confidence < ICON_CONFIDENCE) return false;

        float left = line.box.left / (float) width;
        float right = line.box.right / (float) width;
        float top = line.box.top / (float) height;
        float bottom = line.box.bottom / (float) height;
        float center = (left + right) / 2f;
        boolean topZone = top >= .080f && top <= .30f;
        boolean bottomZone = top >= .72f && bottom <= .97f;
        boolean side = center >= .62f || center <= .38f;
        boolean middle = center > .38f && center < .62f;

        if (bottomOnly) return bottomZone && (side || explicit);
        if (explicit) return (topZone || bottomZone) && (side || middle);
        if (countdown) return (topZone || bottomZone) && side;
        if (bare) return (topZone || bottomZone) && side;
        return (topZone || bottomZone) && side;
    }

    // A payment, authorization or operator-login overlay is not treated as a dismissible ad even
    // when an ad badge is also visible. This covers the China Mobile login overlay seen in samples.
    static boolean sensitive(List<ScreenshotOcr.Line> lines) {
        for (ScreenshotOcr.Line line : lines) {
            String text = MiniAdText.clean(line.text);
            if (text.matches(".*(?:授权|验证码|确认支付|立即支付|支付密码|支付中|同意协议|领取奖励|激励广告|"
                    + "手机号登入|手机号登录|运营商会员|确认登录).*")) return true;
        }
        return false;
    }

    private MiniProgramVision() {}
}
