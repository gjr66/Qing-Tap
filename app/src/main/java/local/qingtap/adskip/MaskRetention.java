package local.qingtap.adskip;

import java.util.HashMap;
import java.util.Map;

// Partial reads get a bounded retry; confirmed absence removes a cover immediately.
final class MaskRetention {
    private final Map<String, Long> seen = new HashMap<>();
    void observe(String id, long now) { seen.put(id, now); }
    boolean keep(String id, boolean checked, long now) {
        Long at = seen.get(id);
        return !checked && at != null && now - at <= 900;
    }
    void clear() { seen.clear(); }
}
