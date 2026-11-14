package local.qingtap.adskip;

final class ClickGate<T> {
    private T target;
    private int attempts;
    private long retryAt;

    T target() {
        return target;
    }

    // Zero allows a click, positive delays a retry, negative exhausts this target's retries.
    long waitTime(T candidate, long now) {
        if (!candidate.equals(target)) return 0;
        if (attempts >= 2) return -1;
        return Math.max(0, retryAt - now);
    }

    void record(T candidate, long now, boolean accepted) {
        attempts = candidate.equals(target) ? attempts + 1 : 1;
        target = candidate;
        retryAt = now + (accepted ? 260 : 95);
    }

    void clear() {
        target = null;
        attempts = 0;
        retryAt = 0;
    }
}
