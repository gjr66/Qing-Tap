package local.qingtap.adskip;

final class WindowCloseTrace {
    final String signature;
    final int window;
    private final long startedAt;
    private String id;
    private String reason;
    private String stage;
    private String outcome;
    private AdActionLog.State state;
    private int priority = -1;
    boolean actionRecorded;

    WindowCloseTrace(String signature, int window, long startedAt) {
        this.signature = signature;
        this.window = window;
        this.startedAt = startedAt;
    }

    void note(int priority, String reason, String stage, String outcome, AdActionLog.State state) {
        if (actionRecorded || priority < this.priority) return;
        this.priority = priority;
        this.reason = reason;
        this.stage = stage;
        this.outcome = outcome;
        this.state = state;
    }

    void flush(AdActionLog log) {
        if (!actionRecorded && state != null && log != null) write(log);
    }

    String action(AdActionLog log, String reason, String detail, String outcome, boolean accepted) {
        if (log == null) return null;
        AdActionLog.State state = accepted ? AdActionLog.State.CHECKING : AdActionLog.State.FAILED;
        if (id == null || actionRecorded) {
            id = log.add(signature, reason, detail, System.currentTimeMillis(), outcome, state, "点击");
        } else {
            log.update(id, reason, detail, outcome, state, "点击");
        }
        actionRecorded = accepted;
        if (!accepted) {
            priority = 100;
            this.reason = reason;
            this.outcome = outcome;
            this.state = state;
            stage = "点击";
        }
        return id;
    }

    private void write(AdActionLog log) {
        if (id == null) id = log.add(signature, reason, "", startedAt, outcome, state, stage);
        else log.update(id, reason, null, outcome, state, stage);
    }
}
