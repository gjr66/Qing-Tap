package local.qingtap.adskip;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// A short ring buffer of what the service actually did and why: the same idea as a filtering log, one
// line per action. Without it there is no way to tell a rule hit from a remembered point from a user
// confirmation, which is exactly what makes an ad skipper feel unpredictable.
final class AdActionLog {
    interface Store {
        String read();

        void write(String value);
    }

    enum State {
        LEGACY("历史记录"), CHECKING("待核对"), CHANGED("界面已变化"), MASKED("已遮挡"),
        FAILED("未关闭"), BLOCKED("已暂停"), UNKNOWN("未确认"), SKIPPED("未发起跳转");

        final String label;

        State(String label) {
            this.label = label;
        }

        boolean needsReview() {
            return this == FAILED || this == BLOCKED || this == UNKNOWN || this == CHECKING;
        }

        static State read(String value) {
            try {
                return valueOf(value);
            } catch (IllegalArgumentException invalid) {
                return LEGACY;
            }
        }
    }

    static final class Entry {
        final long at;
        final String signature;
        final String reason;
        final String detail;
        final String id;
        final String outcome;
        final State state;
        final String stage;

        Entry(long at, String signature, String reason, String detail, String id, String outcome,
              State state, String stage) {
            this.at = at;
            this.signature = signature;
            this.reason = reason;
            this.detail = detail == null ? "" : detail;
            this.id = id;
            this.outcome = outcome;
            this.state = state;
            this.stage = stage;
        }

        boolean hasClockTime() {
            return at >= 946684800000L; // Old releases stored device uptime.
        }
    }

    private static final int LIMIT = 40;
    private final Store store;
    private final List<Entry> entries = new ArrayList<>();

    AdActionLog(Store store) {
        this.store = store;
    }

    String add(String signature, String reason, String detail, long now) {
        return add(signature, reason, detail, now, "系统已接收点击请求");
    }

    String add(String signature, String reason, String detail, long now, String outcome) {
        return add(signature, reason, detail, now, outcome, State.LEGACY, "");
    }

    String add(String signature, String reason, String detail, long now, String outcome, State state, String stage) {
        load();
        String id = UUID.randomUUID().toString();
        entries.add(0, new Entry(now, signature == null ? "" : signature,
                reason == null ? "" : reason, detail, id, outcome, state, stage));
        while (entries.size() > LIMIT) entries.remove(entries.size() - 1);
        save();
        return id;
    }

    void update(String id, String outcome) {
        update(id, null, null, outcome, null, null);
    }

    void update(String id, String reason, String detail, String outcome, State state, String stage) {
        if (id == null || id.isEmpty()) return;
        load();
        for (int index = 0; index < entries.size(); index++) {
            Entry entry = entries.get(index);
            if (!id.equals(entry.id)) continue;
            entries.set(index, new Entry(entry.at, entry.signature, reason == null ? entry.reason : reason,
                    detail == null ? entry.detail : detail, id, outcome, state == null ? entry.state : state,
                    stage == null ? entry.stage : stage));
            save();
            return;
        }
    }

    List<Entry> recent() {
        load();
        return new ArrayList<>(entries);
    }

    int size() {
        load();
        return entries.size();
    }

    void clear() {
        load();
        entries.clear();
        save();
    }

    private void load() {
        entries.clear();
        String raw = store.read();
        if (raw == null || raw.isEmpty()) return;
        for (String line : raw.split("\n")) {
            String[] parts = line.split("\t", -1);
            if (parts.length < 4) continue;
            try {
                entries.add(new Entry(Long.parseLong(parts[0]), parts[1], parts[2], parts[3],
                        parts.length > 4 ? parts[4] : "", parts.length > 5 ? parts[5] : "",
                        parts.length > 6 ? State.read(parts[6]) : State.LEGACY, parts.length > 7 ? parts[7] : ""));
            } catch (NumberFormatException ignored) {
                // Half written line.
            }
        }
    }

    private void save() {
        StringBuilder builder = new StringBuilder();
        for (Entry entry : entries) {
            if (builder.length() > 0) builder.append('\n');
            builder.append(entry.at).append('\t')
                    .append(clean(entry.signature)).append('\t')
                    .append(clean(entry.reason)).append('\t')
                    .append(clean(entry.detail)).append('\t')
                    .append(entry.id).append('\t')
                    .append(clean(entry.outcome)).append('\t')
                    .append(entry.state.name()).append('\t')
                    .append(clean(entry.stage));
        }
        store.write(builder.toString());
    }

    private static String clean(String value) {
        return value == null ? "" : value.replace('\t', ' ').replace('\n', ' ');
    }
}
