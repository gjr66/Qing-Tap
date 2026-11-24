package local.qingtap.adskip;

import java.text.Normalizer;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

final class AdRules {
    private static final int BADGE = 1;
    private static final int SHAKE = 2;
    private static final int PROMO = 4;
    private static final int MODAL = 8;
    private static final int INLINE = 16;
    private static final int SCROLL = 32;
    private static final int BLOCK = 64;
    private static final int MODAL_LABEL = 128;
    private static final int COMMENT = 256;
    private static final int INTERACTIVE = 512;
    private static final int OFFER = 1024;
    private static final int PAGE_CONTROL = 2048;
    private static final Pattern COMMENT_CONTROL = Pattern.compile(
            "(?:评论|全部评论|查看评论|展开评论|打开评论|回复|comments?|replies)(?:[ :：·]*[0-9万千.k+]+)?"
                    + "|[0-9万千.k+]+(?:条)?(?:评论|回复)");
    private static final Pattern PAGE_LABEL = Pattern.compile(
            "(?:消息|购物车|首页|我的|收藏|点赞|分享|messages?|cart|home|profile|likes?|share)"
                    + "(?:[ :：·]*[0-9万千.k+]+)?");

    private static final String SECONDS = "\\(?\\s*\\d{1,2}\\s*(?:秒|s)?\\s*\\)?";
    private static final String SKIP = "(?:跳过(?:\\s*(?:此\\s*)?广告)?|skip(?:\\s+ad)?)";
    private static final String COUNTDOWN_SEPARATOR = "\\s*(?:[|丨·:]\\s*)?";
    private static final Pattern COUNTDOWN = Pattern.compile(
            SKIP + COUNTDOWN_SEPARATOR + SECONDS + "|" + SECONDS + COUNTDOWN_SEPARATOR + SKIP);
    private static final Pattern COUNTDOWN_NUMBER = Pattern.compile(SECONDS);
    private static final Pattern AD_SKIP = Pattern.compile("跳过\\s*(?:此\\s*)?广告|skip\\s+ad");
    // Interactive splash cues: gesture verb plus destination verb. Deliberately narrow, because
    // "翻转手机查看照片" is a plain instruction rather than an ad link.
    private static final Pattern INTERACTIVE_CUE = Pattern.compile(
            "(?:翻转|转动|旋转|摇一摇|摇动|扭一扭|扭动|晃一晃|晃动|甩一甩|甩动)\\s*(?:手机)?\\s*[,、]?\\s*"
                    + "(?:跳转至|跳转到|前往|进入|打开|访问|浏览).{1,30}");
    private static final Pattern SWIPE_AD_LINK = Pattern.compile(
            "(?:上滑|向上滑动)(?:屏幕)?\\s*(?:或点击)?\\s*[,、]?\\s*(?:跳转至|跳转到|前往|打开)"
                    + "\\s*(?:详情页(?:或)?\\s*)?第三方应用[。.]?");
    private static final Pattern TAP_AD_DETAILS = Pattern.compile(
            "(?:点击|轻触)\\s*(?:跳转(?:到|至)?|前往)\\s*(?:广告)?详情页\\s*[>›→]?");
    private static final Pattern SPACE = Pattern.compile("[\\s\\p{Z}]+");
    private static final Set<String> AD_LABELS = words(
            "广告|赞助|赞助内容|推广|商业推广|品牌推广|ad|ads|advertisement|sponsored");
    private static final Set<String> MODAL_LABELS = words(
            "开屏广告|插屏广告|全屏广告|视频广告|暂停广告|app open ad|interstitial ad");
    private static final Set<String> MODAL_IDS = words(
            "splash_ad|splash_ad_container|splash_ad_view|interstitial_ad|interstitial_ad_container|"
                    + "fullscreen_ad|fullscreen_ad_container|video_ad_overlay|ad_dialog|ad_popup");
    private static final Set<String> INLINE_IDS = words(
            "sponsored_card|native_ad|native_ad_container|feed_ad|feed_ad_card|comment_ad|"
                    + "comment_ad_card|comment_sponsor_card|ad_banner|banner_ad|search_ad_card");
    private static final Set<String> CLOSE_IDS = words(
            "ad_close|ad_close_button|close_ad|ad_skip|ad_skip_button|skip_ad|splash_skip|"
                    + "splash_skip_btn|splash_skip_button|splash_ad_skip|tt_splash_skip_btn|"
                    + "ksad_splash_skip_view|interstitial_ad_close|banner_ad_close|"
                    + "native_ad_close|comment_ad_close");
    private static final Set<String> EXPLICIT_CLOSE = words(
            "关闭广告|跳过广告|隐藏广告|关闭此广告|跳过此广告|不再显示此广告|close ad|skip ad|hide ad|dismiss ad");
    private static final Set<String> GENERIC_CLOSE = words("关闭|跳过|×|✕|✖|✗|❌|x|close|skip|dismiss");
    private static final Set<String> POPUP_CLOSE_IDS = words(
            "close|iv_close|img_close|image_close|btn_close|button_close|close_btn|close_button|"
                    + "dialog_close|dialog_close_btn|popup_close|popup_close_btn|iv_dialog_close|close_icon");
    private static final Set<String> PROMO_LABELS = words(
            "查看详情|点击查看详情|点击了解详情|了解更多|立即下载|立即体验|立即打开|立即购买|"
                    + "打开第三方应用|跳转第三方应用|learn more|install now|shop now");
    private static final Set<String> BLOCK_LABELS = words(
            "收银台|确认支付|立即支付|确认付款|付款|支付密码|登录|登录账号|账号登录|确认登录|"
                    + "手机号登录|输入验证码|密码|权限申请|授予权限|允许访问|确认授权|确认订单|"
                    + "sign in|log in|confirm payment|checkout|password|allow access");
    private static final String[] REWARD_PHRASES = {
            "激励广告", "观看广告获得", "观看广告领取", "观看广告解锁", "看广告得", "看广告领取",
            "看广告解锁", "看视频领取", "看视频获得", "看视频解锁", "观看视频领取", "观看视频获得",
            "跳过将失去", "跳过将无法", "退出将失去", "可获得奖励", "领取奖励", "奖励将在",
            "rewarded ad", "watch to earn", "watch ad to", "skip and lose", "you will lose your reward"
    };

    private AdRules() {}

    static boolean blocked(Node node) {
        return (node.flags & BLOCK) != 0;
    }

    static int choose(List<Node> nodes, Box viewport, boolean inlineEnabled) {
        return choose(nodes, viewport, inlineEnabled, true);
    }

    static int choose(List<Node> nodes, Box viewport, boolean inlineEnabled, boolean complete) {
        return choose(nodes, viewport, true, inlineEnabled, complete);
    }

    static int choose(List<Node> nodes, Box viewport, boolean splashEnabled, boolean inlineEnabled,
                      boolean complete) {
        int[] evidence = new int[nodes.size()];
        for (int i = nodes.size() - 1; i >= 0; i--) {
            Node node = nodes.get(i);
            evidence[i] |= node.flags;
            if ((node.flags & BLOCK) != 0) return -1;
            if (node.parent >= 0) evidence[node.parent] |= evidence[i];
        }

        int best = -1;
        int highest = 0;
        for (int i = 1; i < nodes.size(); i++) {
            Node node = nodes.get(i);
            if (!node.clickable || node.close == 0 || !node.box.isControlIn(viewport)) continue;
            int score = score(nodes, i, evidence, viewport, splashEnabled, inlineEnabled, complete);
            if (score > highest) {
                best = i;
                highest = score;
            } else if (score != 0 && score == highest) {
                best = -1;
            }
        }
        return best;
    }

    static boolean tencentSplashContext(List<Node> nodes, boolean startupWindow) {
        boolean interactive = false;
        for (Node node : nodes) {
            if ((node.flags & BLOCK) != 0) return false;
            if ((node.flags & INTERACTIVE) != 0) interactive = true;
        }
        return startupWindow || interactive;
    }

    static int chooseTencentSkip(List<Node> nodes, Box viewport) {
        int result = -1;
        for (int index = 1; index < nodes.size(); index++) {
            Node node = nodes.get(index);
            if (!node.tencentSkipIn(viewport)) continue;
            boolean inline = false;
            for (int parent = node.parent; parent >= 0; parent = nodes.get(parent).parent) {
                if (nodes.get(parent).isInlineOrComment()) {
                    inline = true;
                    break;
                }
            }
            if (inline) continue;
            if (result >= 0) {
                Node previous = nodes.get(result);
                // A container may inherit the same label from its child. Use the tighter bounds.
                if (previous.box.contains(node.box)) result = index;
                else if (!node.box.contains(previous.box)) return -2;
            } else {
                result = index;
            }
        }
        return result;
    }

    // Corner skip controls (bottom-right on Bilibili-style full screen splashes) are only reachable
    // with a touch gesture, so this path accepts non-clickable nodes. It stays conservative: the
    // control must be small, live in a screen corner, sit outside feeds and comments, and the window
    // must carry an interactive cue, or an ad badge plus a countdown or explicit label.
    static int chooseCornerSkip(List<Node> nodes, Box viewport) {
        return chooseCornerSkip(nodes, viewport, false, false);
    }

    static int chooseCornerSkip(List<Node> nodes, Box viewport, boolean runningCountdown) {
        return chooseCornerSkip(nodes, viewport, runningCountdown, false);
    }

    // splashWindow is true when the caller already established this is a freshly opened, full bleed,
    // non scrolling window. In that shape a bare "跳过" next to an ad badge is a real skip control,
    // which is the Bilibili case with no countdown and no interactive wording.
    static int chooseCornerSkip(List<Node> nodes, Box viewport, boolean runningCountdown,
                                boolean splashWindow) {
        int[] evidence = new int[nodes.size()];
        for (int i = nodes.size() - 1; i >= 0; i--) {
            Node node = nodes.get(i);
            evidence[i] |= node.flags;
            if ((node.flags & BLOCK) != 0) return -1;
            if (node.parent >= 0) evidence[node.parent] |= evidence[i];
        }

        int best = -1;
        for (int i = 1; i < nodes.size(); i++) {
            Node node = nodes.get(i);
            if (node.close == 0 || !node.cornerSkipIn(viewport)) continue;
            if (insideInlineOrComment(nodes, i)) continue;
            int markers = 0;
            for (int scope = node.parent, depth = 0; scope >= 0 && depth < 5;
                 depth++, scope = nodes.get(scope).parent) {
                markers |= evidence[scope];
                if ((markers & (INTERACTIVE | SHAKE)) != 0) break;
            }
            boolean interactive = (markers & (INTERACTIVE | SHAKE)) != 0;
            boolean labelled = (markers & (BADGE | MODAL_LABEL)) != 0 && node.close >= 2;
            boolean badgeInSplash = splashWindow && (markers & (BADGE | PROMO)) != 0;
            if (!interactive && !labelled && !badgeInSplash && !(runningCountdown && node.close >= 2)) continue;
            if (best < 0) {
                best = i;
            } else if (nodes.get(best).box.contains(node.box)) {
                best = i;
            } else if (!node.box.contains(nodes.get(best).box)) {
                return -1;
            }
        }
        return best;
    }

    // Vocabulary-free splash shape: one surface fills the window, nothing scrolls, and the tree stays
    // shallow. Ordinary pages fail this because they carry a list or comment subtree.
    static final String DAMAI_SKIP_ID = "cn.damai:id/homepage_advert_pb";

    static int chooseDamaiSkip(List<Node> nodes, Box viewport) {
        if (!splashShape(nodes, viewport)) return -1;
        int best = -1;
        for (int i = 1; i < nodes.size(); i++) {
            Node node = nodes.get(i);
            if (!damaiSkipAt(node, viewport) || insideInlineOrComment(nodes, i)) continue;
            if (best < 0) {
                best = i;
                continue;
            }
            Node previous = nodes.get(best);
            boolean byId = DAMAI_SKIP_ID.equals(node.id);
            boolean previousById = DAMAI_SKIP_ID.equals(previous.id);
            if (byId != previousById) {
                if (byId) best = i;
                continue;
            }
            if (!previous.box.contains(node.box) && !node.box.contains(previous.box)) return -1;
            if ((node.clickable && !previous.clickable)
                    || (node.clickable == previous.clickable && previous.box.contains(node.box))) best = i;
        }
        return best;
    }

    static boolean damaiSkipAt(Node node, Box viewport) {
        double x = ((node.box.left + node.box.right) / 2.0 - viewport.left) / viewport.width();
        double y = ((node.box.top + node.box.bottom) / 2.0 - viewport.top) / viewport.height();
        return (node.tencentSkipIn(viewport) || damaiResourceControl(node, viewport))
                && x >= .85 && x <= .97 && y >= .035 && y <= .12;
    }

    private static boolean damaiResourceControl(Node node, Box viewport) {
        return DAMAI_SKIP_ID.equals(node.id) && viewport.height() > viewport.width()
                && node.box.isControlIn(viewport) && node.box.width() <= viewport.width() * .19
                && node.box.height() <= viewport.height() * .085 && damaiSafeLabel(node);
    }

    static boolean damaiSkipParent(Node child, Node parent, Box viewport) {
        return DAMAI_SKIP_ID.equals(child.id) && parent.clickable && parent.box.contains(child.box)
                && parent.box.isSmallControl(viewport) && parent.box.width() <= child.box.width() * 1.8
                && parent.box.height() <= child.box.height() * 1.8
                && Math.abs((parent.box.left + parent.box.right) - (child.box.left + child.box.right))
                        <= viewport.width() * .045
                && Math.abs((parent.box.top + parent.box.bottom) - (child.box.top + child.box.bottom))
                        <= viewport.height() * .035 && damaiSafeLabel(parent);
    }

    private static boolean damaiSafeLabel(Node node) {
        return (node.flags & (BLOCK | INLINE | COMMENT | SCROLL | SHAKE | INTERACTIVE | OFFER)) == 0
                && (node.text.isEmpty() || node.skip || node.bareCount || node.countdown)
                && (node.description.isEmpty() || node.skip || node.bareCount || node.countdown)
                && !TAP_AD_DETAILS.matcher(node.text).matches()
                && !TAP_AD_DETAILS.matcher(node.description).matches();
    }

    static boolean splashAdEvidence(List<Node> nodes) {
        for (Node node : nodes) {
            if (node.explicitSkip || (node.flags & (BADGE | MODAL | INTERACTIVE | SHAKE)) != 0) return true;
        }
        return false;
    }

    static boolean splashShape(List<Node> nodes, Box viewport) {
        boolean fullBleed = false;
        for (Node node : nodes) {
            if ((node.flags & BLOCK) != 0) return false;
            if ((node.flags & (SCROLL | COMMENT | PAGE_CONTROL)) != 0) return false;
            if (node.box.width() >= viewport.width() * .85 && node.box.height() >= viewport.height() * .85) {
                fullBleed = true;
            }
        }
        return fullBleed && nodes.size() <= 120;
    }

    // A full-screen root and a small number also describe comment counts and unread badges.
    // Structure can only supplement live ad evidence and a labelled dismissal control.
    static int structureTarget(List<Node> nodes, Box viewport) {
        if (!splashShape(nodes, viewport) || !splashAdEvidence(nodes)) return -1;
        int best = -1;
        for (int i = 1; i < nodes.size(); i++) {
            Node node = nodes.get(i);
            if (!node.splashEdgeIn(viewport)) continue;
            if (node.close < 2) continue;
            if (insideInlineOrComment(nodes, i)) continue;
            if (best < 0) {
                best = i;
            } else if (nodes.get(best).box.contains(node.box)) {
                best = i;
            } else if (!node.box.contains(nodes.get(best).box)) {
                return -1;
            }
        }
        return best;
    }

    // A page (list, comments, article) rather than a splash surface. Blind coordinate work and
    // structural guessing are never allowed here: that is what clicked a comment row's overflow button.
    static boolean pageLike(List<Node> nodes) {
        for (Node node : nodes) {
            if ((node.flags & (SCROLL | COMMENT | PAGE_CONTROL)) != 0) return true;
        }
        return false;
    }

    // Any skip-shaped control parked in a corner, regardless of ad evidence. Used only to decide
    // whether to ask the user, never to act on its own.
    static int cornerSkipCandidate(List<Node> nodes, Box viewport) {
        for (int i = 1; i < nodes.size(); i++) {
            Node node = nodes.get(i);
            if (node.close == 0 || !node.cornerSkipIn(viewport)) continue;
            if (insideInlineOrComment(nodes, i)) continue;
            return i;
        }
        return -1;
    }

    static final class TaughtControl {
        final int index;
        final TaughtTargets.Target lesson;

        TaughtControl(int index, TaughtTargets.Target lesson) {
            this.index = index;
            this.lesson = lesson;
        }
    }

    static TaughtControl chooseTaughtControl(List<Node> nodes, Box viewport, Box display,
                                             List<TaughtTargets.Target> lessons, boolean confirmed) {
        if (lessons.isEmpty() || !splashShape(nodes, viewport)) return null;
        boolean adContext = confirmed;
        for (Node node : nodes) {
            if ((node.flags & (BADGE | MODAL | INTERACTIVE | SHAKE)) != 0
                    || TAP_AD_DETAILS.matcher(node.text).matches()
                    || TAP_AD_DETAILS.matcher(node.description).matches()) {
                adContext = true;
                break;
            }
        }
        TaughtControl best = null;
        for (int i = 1; i < nodes.size(); i++) {
            Node node = nodes.get(i);
            if (!(node.cornerSkipIn(viewport) || node.close == 3 && node.splashEdgeIn(viewport))
                    || node.idOnlyClose || node.isInlineOrComment() || insideInlineOrComment(nodes, i)
                    || (!adContext && node.close != 3)) continue;
            TaughtTargets.Target lesson = lessonAtControl(node, viewport, display, lessons);
            if (lesson == null) continue;
            if (best == null) {
                best = new TaughtControl(i, lesson);
                continue;
            }
            Node previous = nodes.get(best.index);
            if (!previous.box.contains(node.box) && !node.box.contains(previous.box)) return null;
            if ((node.clickable && !previous.clickable)
                    || (node.clickable == previous.clickable && previous.box.contains(node.box))) {
                best = new TaughtControl(i, lesson);
            }
        }
        return best;
    }

    private static TaughtTargets.Target lessonAtControl(Node node, Box viewport, Box display,
                                                       List<TaughtTargets.Target> lessons) {
        double bestDistance = Double.POSITIVE_INFINITY;
        TaughtTargets.Target best = null;
        for (TaughtTargets.Target lesson : lessons) {
            Box reference = viewport;
            double aspect = lesson.aspectMilli / 1000.0;
            double difference = Math.abs(aspect - viewport.width() / (double) viewport.height());
            if (display != null) {
                double displayDifference = Math.abs(aspect - display.width() / (double) display.height());
                if (displayDifference <= difference) {
                    reference = display;
                    difference = displayDifference;
                }
            }
            if (aspect <= 0 || difference > aspect * .06) continue;
            float x = (float) (reference.left + reference.width() * lesson.xRatio());
            float y = (float) (reference.top + reference.height() * lesson.yRatio());
            if (!taughtDismissAt(node, viewport, x, y)) continue;
            double distance = Math.hypot((node.box.left + node.box.right) / 2.0 - x,
                    (node.box.top + node.box.bottom) / 2.0 - y);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = lesson;
            }
        }
        return best;
    }

    static int taughtDismissNear(List<Node> nodes, Box viewport, float x, float y) {
        int best = -1;
        float bestDistance = Float.MAX_VALUE;
        for (int i = 1; i < nodes.size(); i++) {
            Node node = nodes.get(i);
            if (!taughtDismissAt(node, viewport, x, y) || insideInlineOrComment(nodes, i)) continue;
            float dx = (node.box.left + node.box.right) / 2f - x;
            float dy = (node.box.top + node.box.bottom) / 2f - y;
            float distance = dx * dx + dy * dy;
            if (best < 0 || (node.clickable && !nodes.get(best).clickable)
                    || (node.clickable == nodes.get(best).clickable && distance < bestDistance)) {
                best = i;
                bestDistance = distance;
            }
        }
        return best;
    }

    static boolean taughtDismissAt(Node node, Box viewport, float x, float y) {
        if (node.close <= 0 || node.idOnlyClose || (node.flags & (BLOCK | COMMENT | PAGE_CONTROL)) != 0
                || !viewport.contains(node.box)
                || !node.box.isSmallControl(viewport)) return false;
        float xPadding = viewport.width() * .012f;
        float yPadding = viewport.height() * .008f;
        return x >= node.box.left - xPadding && x <= node.box.right + xPadding
                && y >= node.box.top - yPadding && y <= node.box.bottom + yPadding;
    }

    // A remembered position is only a hint: the live node must still be a dismissal control.
    // Only labelled skip countdowns may vary their number; ordinary counters never qualify.
    static int matchHint(List<Node> nodes, Box viewport, double xRatio, double yRatio, String label) {
        for (int i = 1; i < nodes.size(); i++) {
            Node node = nodes.get(i);
            if (node.close <= 0 || insideInlineOrComment(nodes, i)
                    || (node.flags & (BLOCK | PAGE_CONTROL | SCROLL)) != 0
                    || !viewport.contains(node.box) || !node.box.isSmallControl(viewport)) continue;
            double centerX = ((node.box.left + node.box.right) / 2.0 - viewport.left) / viewport.width();
            double centerY = ((node.box.top + node.box.bottom) / 2.0 - viewport.top) / viewport.height();
            if (Math.abs(centerX - xRatio) > .05 || Math.abs(centerY - yRatio) > .05) continue;
            String text = node.text.isEmpty() ? node.description : node.text;
            if (label == null) label = "";
            if (!label.isEmpty() && !text.equals(label)) {
                if (!COUNTDOWN.matcher(text).matches() || !COUNTDOWN.matcher(label).matches()) continue;
            }
            return i;
        }
        return -1;
    }

    // Pixel contrast alone cannot distinguish an ad close icon from a comment or navigation icon.
    static boolean visualFallbackAllowed(List<Node> nodes, Box viewport, float x, float y) {
        return splashShape(nodes, viewport) && splashAdEvidence(nodes)
                && taughtDismissNear(nodes, viewport, x, y) >= 0;
    }

    // A splash counter is a short number that keeps getting smaller. Comparing two sightings of the
    // same window is enough; no copy from any ad vendor is involved.
    static boolean countdownTick(CharSequence before, CharSequence after) {
        int first = firstNumber(before);
        int second = firstNumber(after);
        return first > 0 && second >= 0 && second < first && first <= 30;
    }

    static int firstNumber(CharSequence value) {
        if (value == null) return -1;
        String cleaned = clean(value);
        for (int i = 0; i < cleaned.length(); i++) {
            char c = cleaned.charAt(i);
            if (c < '0' || c > '9') continue;
            int number = c - '0';
            int digits = 1;
            while (i + 1 < cleaned.length() && digits < 2
                    && cleaned.charAt(i + 1) >= '0' && cleaned.charAt(i + 1) <= '9') {
                number = number * 10 + (cleaned.charAt(i + 1) - '0');
                digits++;
                i++;
            }
            return number;
        }
        return -1;
    }

    private static boolean insideInlineOrComment(List<Node> nodes, int index) {
        for (int parent = nodes.get(index).parent; parent >= 0; parent = nodes.get(parent).parent) {
            if (nodes.get(parent).isInlineOrComment()
                    || (nodes.get(parent).flags & (SCROLL | PAGE_CONTROL)) != 0) return true;
        }
        return false;
    }

    private static int score(List<Node> nodes, int index, int[] evidence, Box viewport,
                             boolean splashEnabled, boolean inlineEnabled, boolean complete) {
        Node button = nodes.get(index);
        boolean embedded = false;
        for (int ancestor = button.parent; ancestor >= 0; ancestor = nodes.get(ancestor).parent) {
            if ((nodes.get(ancestor).flags & PAGE_CONTROL) != 0) return 0;
            if ((nodes.get(ancestor).flags & (INLINE | SCROLL | COMMENT)) != 0) {
                embedded = true;
            }
        }
        if (splashEnabled && !embedded && button.explicitSkip && button.topRight(viewport)
                && button.box.isSmallControl(viewport)) {
            return 44;
        }
        int scope = button.parent;
        for (int depth = 0; scope >= 0 && depth < 5; depth++, scope = nodes.get(scope).parent) {
            Node group = nodes.get(scope);
            int markers = evidence[scope];
            boolean badge = (markers & BADGE) != 0;
            boolean namedInline = (group.flags & INLINE) != 0;
            boolean dedicatedModal = (group.flags & MODAL) != 0;
            if (namedInline || (embedded && !dedicatedModal)) {
                boolean inlineClose = button.close == 3 || (namedInline && button.close == 1
                        && !button.skip && button.box.nearEdgeOf(group.box));
                if (complete && inlineEnabled && scope != 0 && (namedInline || (markers & (SCROLL | COMMENT)) == 0)
                        && badge && inlineClose && group.box.isCompactIn(viewport) && group.box.contains(button.box)) {
                    return 10 + button.close;
                }
                continue;
            }
            if (!splashEnabled) continue;
            if (!button.idOnlyClose && button.topRight(viewport) && button.skip && button.box.isSmallControl(viewport)
                    && (markers & INTERACTIVE) != 0 && (markers & INLINE) == 0) {
                return 33;
            }
            if ((markers & OFFER) != 0 && badge && !button.skip && button.bottomCenter(viewport)
                    && button.box.isSmallControl(viewport)
                    && hasOfferLayout(nodes, scope, button, viewport)) {
                return 34 + button.close;
            }
            if (!complete) continue;
            if (dedicatedModal && group.box.contains(button.box) && (!button.idOnlyClose
                    || (badge && button.box.isSmallControl(viewport)))) return 30 + button.close;
            if (button.idOnlyClose) continue;
            if ((markers & (INLINE | SCROLL | COMMENT)) != 0) continue;
            boolean promo = (markers & PROMO) != 0;
            boolean shake = (markers & SHAKE) != 0;
            if (button.topCorner(viewport) && button.skip && ((shake && (badge || promo))
                    || (button.countdown && badge && promo) || (button.close == 3 && badge))) {
                return 30 + button.close;
            }
            if (scope != 0 && badge && (markers & (MODAL_LABEL | PROMO)) != 0
                    && group.box.isDialogIn(viewport) && group.box.contains(button.box)
                    && button.box.nearEdgeOf(group.box)) {
                return 20 + button.close;
            }
        }
        return 0;
    }

    private static boolean hasOfferLayout(List<Node> nodes, int scope, Node button, Box viewport) {
        boolean offerAbove = false;
        boolean adLabelAbove = false;
        for (int i = scope + 1; i < nodes.size(); i++) {
            Node node = nodes.get(i);
            if ((node.flags & (OFFER | BADGE)) == 0 || node.box.bottom > button.box.top) continue;
            int ancestor = node.parent;
            while (ancestor > scope) ancestor = nodes.get(ancestor).parent;
            if (ancestor != scope) continue;
            if ((node.flags & OFFER) != 0 && node.box.right >= button.box.left
                    && node.box.left <= button.box.right
                    && button.box.top - node.box.bottom <= viewport.height() * .46) offerAbove = true;
            if ((node.flags & BADGE) != 0 && node.box.left >= viewport.left + viewport.width() * .63
                    && node.box.width() <= viewport.width() * .25) adLabelAbove = true;
            if (offerAbove && adLabelAbove) return true;
        }
        return false;
    }

    private static Set<String> words(String values) {
        Set<String> result = new HashSet<>();
        for (String value : values.split("\\|")) result.add(value);
        return result;
    }

    static String clean(CharSequence text) {
        if (text == null || text.length() == 0) return "";
        CharSequence bounded = text.subSequence(0, Math.min(220, text.length()));
        String value = Normalizer.normalize(bounded, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        return SPACE.matcher(value).replaceAll(" ").trim();
    }

    private static int labels(String value) {
        if (value.isEmpty()) return 0;
        int flags = 0;
        if (AD_LABELS.contains(value)) flags |= BADGE;
        if (MODAL_LABELS.contains(value)) flags |= BADGE | MODAL_LABEL;
        if (PROMO_LABELS.contains(value)) flags |= PROMO;
        if (value.equals("互动广告") || value.startsWith("互动广告 ") || value.startsWith("互动广告:")
                || INTERACTIVE_CUE.matcher(value).matches() || SWIPE_AD_LINK.matcher(value).matches()) {
            flags |= BADGE | INTERACTIVE;
        }
        if (value.equals("登录领取") || value.equals("立即领取") || value.equals("新机权益限时加赠")
                || value.equals("新机专属红包")) flags |= OFFER;
        if (value.equals("摇一摇") || value.startsWith("摇一摇手机")
                || value.startsWith("摇一摇跳转") || value.startsWith("摇一摇查看")
                || value.startsWith("摇一摇或点击") || value.startsWith("摇一摇打开")
                || value.startsWith("shake to open")) {
            flags |= SHAKE;
        }
        if (value.equals("评论区") || COMMENT_CONTROL.matcher(value).matches()) flags |= COMMENT;
        if (PAGE_LABEL.matcher(value).matches()) flags |= PAGE_CONTROL;
        if (BLOCK_LABELS.contains(value)) flags |= BLOCK;
        for (String phrase : REWARD_PHRASES) {
            if (value.contains(phrase)) return flags | BLOCK;
        }
        return flags;
    }

    private static int closeLabel(String value) {
        if (value.isEmpty()) return 0;
        if (EXPLICIT_CLOSE.contains(value)) return 3;
        if (COUNTDOWN.matcher(value).matches()) return AD_SKIP.matcher(value).find() ? 3 : 2;
        return GENERIC_CLOSE.contains(value) ? 1 : -1;
    }

    static boolean isCountdownNumber(CharSequence value) {
        return COUNTDOWN_NUMBER.matcher(clean(value)).matches();
    }

    static boolean isCloseLabel(CharSequence value) {
        return closeLabel(clean(value)) > 0;
    }

    static final class Node {
        final int parent;
        final Box box;
        final boolean clickable;
        final String id;
        final String text;
        final String description;
        final int flags;
        final int close;
        final boolean skip;
        final boolean countdown;
        final boolean bareCount;
        final boolean explicitSkip;
        final boolean idOnlyClose;

        Node(int parent, Box box, boolean clickable, boolean scrollable, boolean inputActive,
             String id, CharSequence text, CharSequence description) {
            this.parent = parent;
            this.box = box;
            this.clickable = clickable;
            this.id = clean(id);
            this.text = clean(text);
            this.description = clean(description);
            String entry = this.id.substring(this.id.lastIndexOf('/') + 1);
            int markers = labels(this.text) | labels(this.description);
            if (MODAL_IDS.contains(entry)) markers |= MODAL;
            if (INLINE_IDS.contains(entry)) markers |= INLINE;
            if (entry.equals("comments") || entry.equals("comment_list") || entry.equals("comment_panel")) {
                markers |= COMMENT;
            }
            if (!CLOSE_IDS.contains(entry) && !INLINE_IDS.contains(entry)
                    && entry.matches(".*(?:^|_)(?:comment|comments|reply|replies)(?:_.*|$)")) {
                markers |= COMMENT;
            }
            if (entry.matches(".*(?:^|_)(?:tab|tabs|navigation|navbar|cart|message|messages)(?:_.*|$)")) {
                markers |= PAGE_CONTROL;
            }
            if (scrollable) markers |= SCROLL;
            if (inputActive) markers |= BLOCK;
            flags = markers;

            int textClose = closeLabel(this.text);
            int descClose = closeLabel(this.description);
            if (descClose > 0 && COUNTDOWN_NUMBER.matcher(this.text).matches()) textClose = 0;
            if (textClose > 0 && COUNTDOWN_NUMBER.matcher(this.description).matches()) descClose = 0;
            idOnlyClose = POPUP_CLOSE_IDS.contains(entry) && this.text.isEmpty() && this.description.isEmpty();
            int idClose = CLOSE_IDS.contains(entry) ? 3 : idOnlyClose ? 1 : 0;
            close = (flags & (COMMENT | PAGE_CONTROL)) != 0 || textClose < 0 || descClose < 0
                    ? 0 : Math.max(idClose, Math.max(textClose, descClose));
            explicitSkip = close > 0 && (AD_SKIP.matcher(this.text).find() || AD_SKIP.matcher(this.description).find());
            countdown = close > 0 && (COUNTDOWN.matcher(this.text).matches() || COUNTDOWN.matcher(this.description).matches());
            bareCount = COUNTDOWN_NUMBER.matcher(this.text).matches()
                    || COUNTDOWN_NUMBER.matcher(this.description).matches();
            skip = close > 0 && (explicitSkip || countdown || this.text.equals("跳过") || this.description.equals("跳过")
                    || this.text.equals("skip") || this.description.equals("skip") || (idClose > 0 && entry.contains("skip")));
        }

        boolean sameControl(Node other) {
            return other.clickable && sameTarget(other);
        }

        boolean sameTarget(Node other) {
            return close > 0 && other.close > 0 && skip == other.skip
                    && id.equals(other.id) && (explicitSkip && other.explicitSkip
                    || close == other.close && sameLabel(text, other.text) && sameLabel(description, other.description))
                    && sameBox(other);
        }

        // A bare counter has no close value, so target identity falls back to the same id, box and
        // numeric value instead of the labelled-skip comparison.
        boolean sameSplashTarget(Node other) {
            if (bareCount && other.bareCount) {
                return id.equals(other.id) && sameBox(other)
                        && (text.equals(other.text) || firstNumber(text) == firstNumber(other.text));
            }
            return sameTarget(other);
        }

        boolean sameBoxAs(Node other) {
            return sameBox(other);
        }

        private boolean sameBox(Node other) {
            return box.left == other.box.left && box.top == other.box.top
                    && box.right == other.box.right && box.bottom == other.box.bottom;
        }

        boolean isInlineOrComment() {
            return (flags & (INLINE | COMMENT)) != 0;
        }

        boolean tencentSkipIn(Box viewport) {
            boolean labelledSkip = explicitSkip || countdown || text.equals("跳过") || description.equals("跳过");
            double centerX = (box.left + box.right) / 2.0;
            return skip && labelledSkip && viewport.height() > viewport.width()
                    && box.isControlIn(viewport) && box.width() <= viewport.width() * .26
                    && box.width() >= viewport.width() * .035 && box.height() >= viewport.height() * .012
                    && box.height() <= viewport.height() * .09
                    && centerX >= viewport.left + viewport.width() * .78
                    && box.top - viewport.top <= viewport.height() * .18;
        }

        // Any screen corner, both orientations: top or bottom edge within the edge band, and the
        // horizontal center inside the outer fifth of the width.
        boolean cornerSkipIn(Box viewport) {
            if (!skip || !viewport.contains(box) || !box.isSmallControl(viewport)) return false;
            double centerX = (box.left + box.right) / 2.0 - viewport.left;
            boolean right = centerX >= viewport.width() * .78;
            boolean left = centerX <= viewport.width() * .22;
            boolean bottom = viewport.bottom - box.bottom <= viewport.height() * .10;
            boolean top = box.top - viewport.top <= viewport.height() * .18;
            return (right || left) && (bottom || top);
        }

        // A small control parked in the top or bottom band of the window, any horizontal position.
        boolean splashEdgeIn(Box viewport) {
            if (!viewport.contains(box) || !box.isSmallControl(viewport)) return false;
            double top = box.top - viewport.top;
            double bottom = viewport.bottom - box.bottom;
            return top <= viewport.height() * .20 || bottom <= viewport.height() * .12;
        }

        private static boolean sameLabel(String before, String after) {            return before.equals(after) || (COUNTDOWN.matcher(before).matches() && COUNTDOWN.matcher(after).matches());
        }

        boolean topRight(Box viewport) {
            return box.top - viewport.top <= viewport.height() * .22
                    && box.left - viewport.left >= viewport.width() * .58
                    && box.right - viewport.left >= viewport.width() * .76;
        }

        boolean bottomCenter(Box viewport) {
            double center = (box.left + box.right) / 2.0 - viewport.left;
            return center >= viewport.width() * .36 && center <= viewport.width() * .64
                    && box.top - viewport.top >= viewport.height() * .50
                    && box.bottom - viewport.top <= viewport.height() * .93
                    && box.width() <= viewport.width() * .20;
        }

        boolean topCorner(Box viewport) {
            return box.top - viewport.top <= viewport.height() * .22
                    && (box.right - viewport.left >= viewport.width() * .78
                        || box.left - viewport.left <= viewport.width() * .09);
        }
    }

    static final class Box {
        final int left;
        final int top;
        final int right;
        final int bottom;

        Box(int left, int top, int right, int bottom) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
        }

        int width() { return right - left; }
        int height() { return bottom - top; }

        boolean contains(Box other) {
            return left <= other.left && right >= other.right && top <= other.top && bottom >= other.bottom;
        }

        boolean isControlIn(Box viewport) {
            return width() > 0 && height() > 0 && viewport.contains(this)
                    && width() <= viewport.width() * .65 && height() <= viewport.height() * .22;
        }

        boolean isSmallControl(Box viewport) {
            return width() <= viewport.width() * .40 && height() <= viewport.height() * .11;
        }

        boolean isCompactIn(Box viewport) {
            return width() > 0 && height() > 0 && height() <= viewport.height() * .50;
        }

        boolean isDialogIn(Box viewport) {
            return width() >= viewport.width() * .32 && height() >= viewport.height() * .12
                    && (width() <= viewport.width() * .95 || height() <= viewport.height() * .85);
        }

        boolean nearEdgeOf(Box group) {
            return top - group.top <= group.height() * .28
                    || group.bottom - bottom <= group.height() * .20;
        }
    }
}
