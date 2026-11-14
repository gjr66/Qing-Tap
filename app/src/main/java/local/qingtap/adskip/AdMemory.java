package local.qingtap.adskip;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Offline memory of windows where a dismissal actually worked. A signature is package, activity and
// viewport size; the stored value is a relative candidate location. Reuse requires fresh ad evidence
// and a live dismissal control at that location; numbers or ordinary icons are never click authority.
// Failed reuse lowers confidence until the entry stops being used.
final class AdMemory {
    interface Store {
        String read();

        void write(String value);
    }

    static final class Target {
        final String signature;
        final int hits;
        final int misses;
        final int xMilli;
        final int yMilli;
        final long seen;
        final String label;

        Target(String signature, int hits, int misses, int xMilli, int yMilli, long seen, String label) {
            this.signature = signature;
            this.hits = hits;
            this.misses = misses;
            this.xMilli = xMilli;
            this.yMilli = yMilli;
            this.seen = seen;
            this.label = label;
        }

        boolean valid() {
            return xMilli > 0 && yMilli > 0;
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
    private final Map<String, Target> targets = new LinkedHashMap<>();
    private boolean loaded;

    AdMemory(Store store) {
        this.store = store;
    }

    static String signature(String pkg, String activity, int width, int height) {
        return pkg + "/" + activity + "/" + width + "x" + height;
    }

    Target target(String signature) {
        load();
        return targets.get(signature);
    }

    // One confirmed success is enough while nothing has failed. After a failure the entry has to earn
    // its place again: at least two hits and a clear majority.
    boolean reliable(String signature) {
        Target target = target(signature);
        if (target == null || !target.valid()) return false;
        if (target.misses == 0) return target.hits >= 1;
        return target.hits >= 2 && target.misses * 2 < target.hits;
    }

    void succeeded(String signature, double xRatio, double yRatio, String label, long now) {
        load();
        Target previous = targets.get(signature);
        targets.put(signature, new Target(signature,
                previous == null ? 1 : previous.hits + 1,
                previous == null ? 0 : previous.misses,
                (int) Math.round(xRatio * 1000), (int) Math.round(yRatio * 1000), now,
                label == null ? "" : label));
        trim();
        save();
    }

    void missed(String signature) {
        load();
        Target previous = targets.get(signature);
        if (previous == null) return;
        targets.put(signature, new Target(signature, previous.hits, previous.misses + 1,
                previous.xMilli, previous.yMilli, previous.seen, previous.label));
        save();
    }

    java.util.List<Target> all() {
        load();
        return new ArrayList<>(targets.values());
    }

    void removePrefix(String prefix) {
        load();
        targets.keySet().removeIf(key -> key.startsWith(prefix));
        save();
    }

    void remove(String signature) {
        load();
        targets.remove(signature);
        save();
    }

    int size() {
        load();
        return targets.size();
    }

    void clear() {
        load();
        targets.clear();
        save();
    }

    private void trim() {
        while (targets.size() > LIMIT) {
            String oldest = null;
            long seen = Long.MAX_VALUE;
            for (Map.Entry<String, Target> entry : targets.entrySet()) {
                if (entry.getValue().seen < seen) {
                    seen = entry.getValue().seen;
                    oldest = entry.getKey();
                }
            }
            if (oldest == null) return;
            targets.remove(oldest);
        }
    }

    private void load() {
        if (loaded) return;
        loaded = true;
        String raw = store.read();
        if (raw == null || raw.isEmpty()) return;
        for (String line : raw.split("\n")) {
            String[] parts = line.split("\t", -1);
            if (parts.length < 7) continue;
            try {
                Target target = new Target(parts[0], Integer.parseInt(parts[1]), Integer.parseInt(parts[2]),
                        Integer.parseInt(parts[3]), Integer.parseInt(parts[4]), Long.parseLong(parts[5]), parts[6]);
                targets.put(target.signature, target);
            } catch (NumberFormatException ignored) {
                // A half written line is dropped; the next success rewrites the entry.
            }
        }
    }

    private void save() {
        List<String> lines = new ArrayList<>();
        for (Target target : targets.values()) {
            lines.add(target.signature + "\t" + target.hits + "\t" + target.misses + "\t" + target.xMilli
                    + "\t" + target.yMilli + "\t" + target.seen + "\t"
                    + target.label.replace('\t', ' ').replace('\n', ' '));
        }
        store.write(join(lines));
    }

    private static String join(List<String> lines) {
        StringBuilder builder = new StringBuilder();
        for (String line : lines) {
            if (builder.length() > 0) builder.append('\n');
            builder.append(line);
        }
        return builder.toString();
    }
}
