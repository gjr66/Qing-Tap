package local.qingtap.adskip;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

final class RepeatGuard {
    static final int DEFAULT_COUNT = 2;
    static final int DEFAULT_SECONDS = 10;
    static final int MAX_COUNT = 8;
    static final int MAX_SECONDS = 60;
    static final long ABSENCE_MS = 150;
    private static final int MAX_HISTORIES = 64;
    private static final Pattern DIGITS = Pattern.compile("\\p{Nd}+");

    enum Decision { ALLOW, ASK, STOP }

    static final class Page {
        final String pkg;
        final String layout;
        final SceneFingerprint scene;

        Page(String pkg, String layout, SceneFingerprint scene) {
            this.pkg = pkg;
            this.layout = layout;
            this.scene = scene;
        }

        boolean same(Page other) {
            return pkg.equals(other.pkg) && layout.equals(other.layout)
                    && (scene == null || other.scene == null || scene.matches(other.scene));
        }

        String encode() {
            return pkg + "|" + layout + "|" + (scene == null ? "" : scene.encode());
        }

        static Page decode(String raw) {
            String[] fields = raw.split("\\|", 3);
            if (fields.length != 3 || fields[0].isEmpty() || fields[1].isEmpty()) return null;
            SceneFingerprint scene = SceneFingerprint.decode(fields[2]);
            if (!fields[2].isEmpty() && scene == null) return null;
            return new Page(fields[0], fields[1], scene);
        }

        boolean denied(Set<String> records) {
            String prefix = pkg + "|" + layout + "|";
            if (records.contains(prefix)) return true;
            for (String record : records) {
                if (!record.startsWith(prefix)) continue;
                Page saved = decode(record);
                if (saved != null && same(saved)) return true;
            }
            return false;
        }
    }

    static Page pageOf(String pkg, String rootClass, List<AdRules.Node> nodes,
                       AdRules.Box viewport, SceneFingerprint scene) {
        TreeSet<String> anchors = new TreeSet<>();
        for (int i = 1; i < nodes.size() && anchors.size() < 48; i++) {
            AdRules.Node node = nodes.get(i);
            if (node.skip || node.bareCount || !viewport.contains(node.box)) continue;
            String label = node.text.isEmpty() ? node.description : node.text;
            if (label.length() > 28) label = "";
            if (!label.isEmpty()) label = DIGITS.matcher(label).replaceAll("#");
            if (node.id.isEmpty() && (label.isEmpty() || "#".equals(label))) continue;
            int x = (node.box.left + node.box.right - 2 * viewport.left) * 4 / (2 * viewport.width());
            int y = (node.box.top + node.box.bottom - 2 * viewport.top) * 6 / (2 * viewport.height());
            anchors.add(x + ":" + y + ":" + node.id + ":" + label);
        }
        String shape = rootClass + "/" + (viewport.height() >= viewport.width()) + "/" + anchors;
        // Hash the layout so the saved decision does not contain visible labels.
        long hash = 0xcbf29ce484222325L;
        for (int i = 0; i < shape.length(); i++) hash = (hash ^ shape.charAt(i)) * 0x100000001b3L;
        return new Page(pkg, Long.toHexString(hash), scene);
    }

    private static final class History {
        final Page page;
        final ArrayDeque<Long> times = new ArrayDeque<>();

        History(Page page) {
            this.page = page;
        }
    }

    static final class Turn {
        Page page;
        final int window;
        final History history;
        boolean asked;
        boolean approved;
        boolean clicked;
        long missingSince = -1;

        Turn(Page page, int window, History history) {
            this.page = page;
            this.window = window;
            this.history = history;
        }
    }

    private final List<History> histories = new ArrayList<>();
    private Turn active;

    Turn active() {
        return active;
    }

    void leave() {
        active = null;
    }

    void observe(Page page, int window, long now) {
        if (active != null && active.window == window && active.page.pkg.equals(page.pkg)
                && active.page.layout.equals(page.layout)) {
            if (active.page.scene == null && page.scene != null) active.page = page;
            active.missingSince = -1;
            return;
        }
        History history = null;
        for (int i = histories.size() - 1; i >= 0; i--) {
            if (histories.get(i).page.same(page)) {
                history = histories.remove(i);
                break;
            }
        }
        if (history == null) history = new History(page);
        history.times.addLast(now);
        while (history.times.size() > MAX_COUNT) history.times.removeFirst();
        histories.add(history);
        if (histories.size() > MAX_HISTORIES) histories.remove(0);
        active = new Turn(page, window, history);
    }

    boolean missing(int window, long now) {
        if (active == null || active.window != window) return false;
        if (active.missingSince < 0) active.missingSince = now;
        if (now - active.missingSince < ABSENCE_MS) return false;
        leave();
        return true;
    }

    Decision decision(long now, int count, int seconds, Set<String> denied) {
        if (active == null || active.page.denied(denied) || active.clicked) return Decision.STOP;
        if (active.approved) return Decision.ALLOW;
        if (active.asked) return Decision.STOP;
        ArrayDeque<Long> times = active.history.times;
        while (!times.isEmpty() && now - times.peekFirst() > seconds * 1000L) times.removeFirst();
        if (times.size() < count) return Decision.ALLOW;
        active.asked = true;
        return Decision.ASK;
    }

    boolean approve(Turn turn) {
        if (active != turn || !turn.asked || turn.clicked || turn.missingSince >= 0) return false;
        turn.approved = true;
        return true;
    }

    void finishAttempt(boolean accepted) {
        if (active == null) return;
        active.clicked = accepted;
        active.approved = false;
    }
}
