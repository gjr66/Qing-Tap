package local.qingtap.adskip;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Learning from ads that were NOT closed. When a splash shaped window disappears on its own without
// any action from us, the small control we had seen inside it becomes a suspect: the ad ended by
// itself, so that control was very likely its skip button. The next time the same window appears the
// suspect is tried early, and every attempt either confirms or demotes it.
final class AdHints {
    interface Store {
        String read();

        void write(String value);
    }

    static final class Hint {
        final String signature;
        final int hits;
        final int misses;
        final int xMilli;
        final int yMilli;
        final String label;
        final boolean strong;

        Hint(String signature, int hits, int misses, int xMilli, int yMilli, String label, boolean strong) {
            this.signature = signature;
            this.hits = hits;
            this.misses = misses;
            this.xMilli = xMilli;
            this.yMilli = yMilli;
            this.label = label;
            this.strong = strong;
        }

        // A weak sighting only watches: it must be seen twice at the same spot before it may act. A
        // sighting backed by an observed countdown may act the first time.
        boolean usable() {
            if (xMilli <= 0 || yMilli <= 0 || misses > hits) return false;
            return hits >= 2 || (strong && hits >= 1);
        }

        double xRatio() {
            return xMilli / 1000.0;
        }

        double yRatio() {
            return yMilli / 1000.0;
        }
    }

    private static final int LIMIT = 40;
    private final Store store;
    private final Map<String, Hint> hints = new LinkedHashMap<>();
    private boolean loaded;

    AdHints(Store store) {
        this.store = store;
    }

    Hint find(String signature) {
        load();
        Hint hint = hints.get(signature);
        return hint != null && hint.usable() ? hint : null;
    }

    // Called when a splash shaped window ended by itself while we did nothing. Same position and same
    // wording means the same button, so the evidence accumulates instead of replacing.
    void remember(String signature, double xRatio, double yRatio, String label, boolean strong) {
        load();
        Hint previous = hints.get(signature);
        int hits = previous == null ? 1 : previous.hits + 1;
        int misses = previous == null ? 0 : previous.misses;
        boolean sticky = strong || (previous != null && previous.strong);
        hints.put(signature, new Hint(signature, hits, misses,
                (int) Math.round(xRatio * 1000), (int) Math.round(yRatio * 1000),
                label == null ? "" : label, sticky));
        trim();
        save();
    }

    void confirmed(String signature) {
        load();
        Hint previous = hints.get(signature);
        if (previous == null) return;
        hints.put(signature, new Hint(signature, previous.hits + 1, previous.misses,
                previous.xMilli, previous.yMilli, previous.label, previous.strong));
        save();
    }

    void missed(String signature) {
        load();
        Hint previous = hints.get(signature);
        if (previous == null) return;
        if (previous.misses + 1 > previous.hits) {
            hints.remove(signature);
        } else {
            hints.put(signature, new Hint(signature, previous.hits, previous.misses + 1,
                    previous.xMilli, previous.yMilli, previous.label, previous.strong));
        }
        save();
    }

    java.util.List<Hint> all() {
        load();
        return new ArrayList<>(hints.values());
    }

    void removePrefix(String prefix) {
        load();
        hints.keySet().removeIf(key -> key.startsWith(prefix));
        save();
    }

    void remove(String signature) {
        load();
        hints.remove(signature);
        save();
    }

    int size() {
        load();
        return hints.size();
    }

    void clear() {
        load();
        hints.clear();
        save();
    }

    private void trim() {
        while (hints.size() > LIMIT) {
            String oldest = null;
            int best = Integer.MAX_VALUE;
            for (Map.Entry<String, Hint> entry : hints.entrySet()) {
                int score = entry.getValue().hits - entry.getValue().misses;
                if (score < best) {
                    best = score;
                    oldest = entry.getKey();
                }
            }
            if (oldest == null) return;
            hints.remove(oldest);
        }
    }

    private void load() {
        if (loaded) return;
        loaded = true;
        String raw = store.read();
        if (raw == null || raw.isEmpty()) return;
        for (String line : raw.split("\n")) {
            String[] parts = line.split("\t", -1);
            if (parts.length < 6) continue;
            try {
                boolean strong = parts.length > 6 && "1".equals(parts[6]);
                Hint hint = new Hint(parts[0], Integer.parseInt(parts[1]), Integer.parseInt(parts[2]),
                        Integer.parseInt(parts[3]), Integer.parseInt(parts[4]), parts[5], strong);
                hints.put(hint.signature, hint);
            } catch (NumberFormatException ignored) {
                // Half written line; the next observation rewrites it.
            }
        }
    }

    private void save() {
        List<String> lines = new ArrayList<>();
        for (Hint hint : hints.values()) {
            lines.add(hint.signature + "\t" + hint.hits + "\t" + hint.misses + "\t" + hint.xMilli
                    + "\t" + hint.yMilli + "\t" + hint.label.replace('\t', ' ').replace('\n', ' ')
                    + "\t" + (hint.strong ? "1" : "0"));
        }
        StringBuilder builder = new StringBuilder();
        for (String line : lines) {
            if (builder.length() > 0) builder.append('\n');
            builder.append(line);
        }
        store.write(builder.toString());
    }
}
