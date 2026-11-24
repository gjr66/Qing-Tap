package local.qingtap.adskip;

import android.graphics.Rect;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// Re-query exact ad-slot anchors even when an animated SDK exhausts the generic tree budget.
// Every checked ID is authoritative: missing anchors remove their covers; unfinished IDs retry.
final class CampusMaskAnchors {
    static final class Result {
        final List<AdRules.Node> nodes;
        final Set<String> checked = new HashSet<>();
        Result(List<AdRules.Node> nodes) { this.nodes = new ArrayList<>(nodes); }
    }
    private int offset;

    Result read(AccessibilityNodeInfo root, int window, AdRules.Box viewport, List<AdRules.Node> base,
                boolean complete, Set<String> activeIds) {
        Result result = new Result(base);
        if (complete) {
            for (String id : CampusAdRules.regionIds()) result.checked.add(CampusAdRules.PACKAGE + ":id/" + id);
            return result;
        }
        long deadline = SystemClock.uptimeMillis() + 85;
        if (!complete) {
            for (String id : CampusAdRules.BUSINESS_IDS) {
                if (SystemClock.uptimeMillis() > deadline) break;
                append(root, window, viewport, id, result, false);
            }
        }
        List<String> ids = new ArrayList<>(CampusAdRules.regionIds());
        List<String> ordered = new ArrayList<>();
        for (String id : ids) if (activeIds.contains(CampusAdRules.PACKAGE + ":id/" + id)) ordered.add(id);
        for (int i = 0; i < ids.size(); i++) {
            String id = ids.get((i + offset) % ids.size());
            if (!ordered.contains(id)) ordered.add(id);
        }
        offset = (offset + 5) % ids.size();
        for (String id : ordered) {
            if (SystemClock.uptimeMillis() > deadline) break;
            append(root, window, viewport, id, result, true);
        }
        return result;
    }

    private void append(AccessibilityNodeInfo root, int window, AdRules.Box viewport, String entry,
                        Result result, boolean ad) {
        List<AccessibilityNodeInfo> matches = null;
        String id = CampusAdRules.PACKAGE + ":id/" + entry;
        try {
            matches = root.findAccessibilityNodeInfosByViewId(id);
            if (matches == null || matches.size() > 12) return;
            if (ad) result.checked.add(id);
            // Replace old snapshots of this ID so geometry follows scrolling, without duplicate masks.
            if (ad) {
                for (int i = 1; i < result.nodes.size(); i++) {
                    AdRules.Node previous = result.nodes.get(i);
                    if (id.equals(previous.id)) result.nodes.set(i, new AdRules.Node(previous.parent,
                            new AdRules.Box(0, 0, 0, 0), false, false, false, "", "", ""));
                }
            }
            for (AccessibilityNodeInfo node : matches) {
                if (!eligible(node, window)) continue;
                int parent = result.nodes.size();
                result.nodes.add(snapshot(node, 0));
                if ("home_activity_rv".equals(entry)) {
                    long until = SystemClock.uptimeMillis() + 20;
                    readChildren(node, parent, window, result.nodes, until, 0);
                }
            }
        } catch (RuntimeException unavailable) {
            result.checked.remove(id);
        } finally {
            if (matches != null) for (AccessibilityNodeInfo node : matches) node.recycle();
        }
    }

    private void readChildren(AccessibilityNodeInfo parent, int index, int window, List<AdRules.Node> nodes,
                              long deadline, int depth) {
        if (depth > 6) return;
        for (int i = 0; i < parent.getChildCount() && SystemClock.uptimeMillis() < deadline; i++) {
            AccessibilityNodeInfo child = parent.getChild(i);
            if (child == null) continue;
            try {
                if (!eligible(child, window)) continue;
                int next = nodes.size();
                nodes.add(snapshot(child, index));
                readChildren(child, next, window, nodes, deadline, depth + 1);
            } finally { child.recycle(); }
        }
    }

    private static boolean eligible(AccessibilityNodeInfo node, int window) {
        return WindowIdentity.matches(CampusAdRules.PACKAGE, window, node.getPackageName(), node.getWindowId())
                && node.isVisibleToUser();
    }

    private static AdRules.Node snapshot(AccessibilityNodeInfo node, int parent) {
        Rect rect = new Rect(); node.getBoundsInScreen(rect);
        return new AdRules.Node(parent, new AdRules.Box(rect.left, rect.top, rect.right, rect.bottom),
                node.isClickable(), node.isScrollable(), node.isPassword() || node.isEditable() && node.isFocused(),
                node.getViewIdResourceName(), node.getText(), node.getContentDescription());
    }
}
