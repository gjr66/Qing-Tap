package local.qingtap.adskip;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

// A display title may describe a page instead of a mini-app. Require the host's app-name
// control as corroboration. Unidentified windows get a volatile scope even if the OS reuses an ID.
final class MiniScope {
    static final String NAMED = "mp:";
    static final String WINDOW = "mp-window:";
    static final String LOCKER = NAMED + "光启立方智能柜";
    private long visit;
    private String lastTitle = "";
    private String verifiedScope;
    private final Set<String> localDenials = new HashSet<>();

    void leave() {
        visit++;
        lastTitle = "";
        verifiedScope = null;
        localDenials.clear();
    }

    String identify(String pkg, CharSequence title, List<AdRules.Node> nodes) {
        String name = title == null ? "" : title.toString().trim();
        if (!name.equals(lastTitle)) {
            leave();
            lastTitle = name;
        }
        if (verifiedScope != null) return verifiedScope;
        if (validName(name)) {
            String normalized = AdRules.clean(name);
            for (AdRules.Node node : nodes) {
                if (!nameControl(pkg, node.id)) continue;
                if (node.text.equals(normalized) || node.description.equals(normalized)) {
                    verifiedScope = NAMED + name;
                    return verifiedScope;
                }
            }
        }
        return WINDOW + visit;
    }

    private static boolean validName(String name) {
        return !name.isEmpty() && name.length() <= 64 && name.matches("[\\p{L}\\p{M}\\p{N} ().·_-]+")
                && !name.matches("(?i)微信|wechat|支付宝|alipay|小程序|首页|主页|加载中|广告|关闭|mini program");
    }

    private static boolean nameControl(String pkg, String id) {
        if (MiniProgramRules.WECHAT.equals(pkg))
            return id.equals("com.tencent.mm:id/app_brand_launching_name")
                    || id.equals("com.tencent.mm:id/app_brand_name");
        return MiniProgramRules.ALIPAY.equals(pkg) && id.equals("com.alipay.mobile.nebula:id/h5_app_name");
    }

    static String segment(String signature) {
        int start = signature.indexOf('/') + 1;
        int end = signature.indexOf('/', start);
        return start > 0 && end > start ? signature.substring(start, end) : "";
    }

    static boolean scoped(String signature) {
        String scope = segment(signature);
        return scope.startsWith(NAMED) || scope.startsWith(WINDOW);
    }

    static RepeatGuard.Page page(RepeatGuard.Page base, String signature) {
        if (!scoped(signature)) return base;
        return new RepeatGuard.Page(base.pkg, segment(signature) + "/" + base.layout, base.scene);
    }

    boolean denyLocally(RepeatGuard.Page page) {
        if (!page.layout.startsWith(WINDOW)) return false;
        localDenials.add(page.encode());
        return true;
    }

    boolean locallyDenied(RepeatGuard.Page page) {
        return page.denied(localDenials);
    }
}
