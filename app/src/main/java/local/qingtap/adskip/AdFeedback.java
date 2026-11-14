package local.qingtap.adskip;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// User verdicts for one window signature. "Yes, this page is an ad" makes the corner/target trustworthy
// from now on; "No" is a hard stop, so a false positive cannot come back on the next visit.
final class AdFeedback {
    interface Store {
        String read();

        void write(String value);
    }

    static final int ALLOW = 1;
    static final int DENY = 2;
    private static final int LIMIT = 60;

    private final Store store;
    private final Map<String, Integer> verdicts = new LinkedHashMap<>();
    private boolean loaded;

    AdFeedback(Store store) {
        this.store = store;
    }

    boolean allowed(String signature) {
        load();
        Integer verdict = lookup(signature);
        return verdict != null && verdict == ALLOW;
    }

    boolean denied(String signature) {
        load();
        Integer verdict = lookup(signature);
        return verdict != null && verdict == DENY;
    }

    // A lesson taught from a screenshot is stored as "pkg/*" so it covers every screen of that app; an
    // exact signature always wins over the app wide entry.
    private Integer lookup(String signature) {
        Integer exact = verdicts.get(signature);
        if (exact != null) return exact;
        if (signature == null) return null;
        int slash = signature.indexOf('/');
        if (slash <= 0) return null;
        return verdicts.get(signature.substring(0, slash) + "/*");
    }

    void allow(String signature) {
        set(signature, ALLOW);
        // A user confirmation replaces any earlier refusal.
        save();
    }

    void deny(String signature) {
        set(signature, DENY);
        save();
    }

    int allowedCount() {
        load();
        int count = 0;
        for (Integer verdict : verdicts.values()) {
            if (verdict == ALLOW) count++;
        }
        return count;
    }

    int deniedCount() {
        load();
        int count = 0;
        for (Integer verdict : verdicts.values()) {
            if (verdict == DENY) count++;
        }
        return count;
    }

    java.util.List<String> of(int verdict) {
        load();
        java.util.List<String> result = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : verdicts.entrySet()) {
            if (entry.getValue() == verdict) result.add(entry.getKey());
        }
        return result;
    }

    void forgetPrefix(String prefix) {
        load();
        verdicts.keySet().removeIf(key -> key.startsWith(prefix));
        save();
    }

    void forget(String signature) {
        load();
        verdicts.remove(signature);
        save();
    }

    int size() {
        load();
        return verdicts.size();
    }

    void clear() {
        load();
        verdicts.clear();
        save();
    }

    private void set(String signature, int verdict) {
        load();
        verdicts.remove(signature);
        verdicts.put(signature, verdict);
        while (verdicts.size() > LIMIT) {
            String oldest = verdicts.keySet().iterator().next();
            verdicts.remove(oldest);
        }
    }

    private void load() {
        if (loaded) return;
        loaded = true;
        String raw = store.read();
        if (raw == null || raw.isEmpty()) return;
        for (String line : raw.split("\n")) {
            String[] parts = line.split("\t", -1);
            if (parts.length < 2) continue;
            try {
                verdicts.put(parts[0], Integer.parseInt(parts[1]));
            } catch (NumberFormatException ignored) {
                // Half written line.
            }
        }
    }

    private void save() {
        List<String> lines = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : verdicts.entrySet()) {
            lines.add(entry.getKey() + "\t" + entry.getValue());
        }
        StringBuilder builder = new StringBuilder();
        for (String line : lines) {
            if (builder.length() > 0) builder.append('\n');
            builder.append(line);
        }
        store.write(builder.toString());
    }
}
