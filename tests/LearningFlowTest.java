package local.qingtap.adskip;

import java.util.Arrays;
import java.util.List;

final class LearningFlowTest {
    public static void main(String[] args) {
        checkLessonRoundTrip();
        checkDiagnosticRoundTrip();
        System.out.println("PASS saved screenshot reaches live matcher and close diagnostics survive reload");
    }

    private static void checkLessonRoundTrip() {
        int width = 360, height = 790;
        int[] pixels = new int[width * height];
        Arrays.fill(pixels, 0x333333);
        for (int y = 44; y < 70; y++) {
            for (int x = 305; x < 339; x++) {
                pixels[y * width + x] = (x + y) % 6 < 3 ? 0xf4f4f4 : 0x101010;
            }
        }
        TaughtTargets.Sample sample = TaughtTargets.Sample.from(pixels, width, height, .90, .072);
        check(sample != null, "matching patch must create a reusable sample");
        TaughtTargets.Store store = new TaughtTargets.Store() {
            String value = "";
            @Override public String read() { return value; }
            @Override public void write(String saved) { value = saved; }
        };
        TaughtTargets teaching = new TaughtTargets(store);
        check(teaching.saveSample("cn.damai", sample.withPreview("thumbnail")), "write batch sample");
        check(teaching.saveSample("cn.damai", sample.withPreview("thumbnail")), "reimport sample");
        TaughtTargets live = new TaughtTargets(store);
        List<TaughtTargets.Target> loaded = live.forWindow("cn.damai", "SplashMainActivity");
        check(loaded.size() == 1 && "thumbnail".equals(loaded.get(0).preview), "retain a single preview");
        check(live.forWindow("com.other.app", "SplashMainActivity").isEmpty(), "keep package scope");
        TaughtSearch.Match match = TaughtSearch.forLiveFrame(pixels, width, height, loaded,
                SceneFingerprint.of(pixels, width, height));
        check(match != null && match.lesson.key().equals(loaded.get(0).key()), "use saved sample at runtime");
    }

    private static void checkDiagnosticRoundTrip() {
        AdActionLog.Store store = new AdActionLog.Store() {
            String value = "1777777777777\tcom.legacy/Main/360x790\t通用规则\t跳过";
            @Override public String read() { return value; }
            @Override public void write(String saved) { value = saved; }
        };
        AdActionLog log = new AdActionLog(store);
        WindowCloseTrace trace = new WindowCloseTrace("cn.damai/SplashMainActivity/360x790", 4, 1777777777778L);
        trace.note(2, "截图教学", "识别", "学习截图未匹配", AdActionLog.State.FAILED);
        trace.note(0, "开屏", "识别", "普通候选", AdActionLog.State.UNKNOWN);
        trace.flush(log);
        check(log.recent().get(0).outcome.equals("学习截图未匹配"), "preserve specific failure");
        String id = trace.action(log, "大麦规则", "跳过", "已提交手势", true);
        trace.flush(log);
        log.update(id, null, null, "手势已执行 · 仍看到广告", AdActionLog.State.FAILED, "点击后核对");
        List<AdActionLog.Entry> entries = new AdActionLog(store).recent();
        check(entries.size() == 2, "replace provisional failure in the same window");
        check(entries.get(0).state.needsReview() && "点击后核对".equals(entries.get(0).stage),
                "persist no-response stage");
        check(entries.get(1).state == AdActionLog.State.LEGACY, "read pre-upgrade log");
    }

    private static void check(boolean passed, String label) {
        if (!passed) throw new AssertionError(label);
    }
}
