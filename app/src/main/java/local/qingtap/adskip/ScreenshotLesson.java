package local.qingtap.adskip;

import android.content.Context;
import android.graphics.Bitmap;

final class ScreenshotLesson {
    private final TaughtTargets.Sample sample;

    private ScreenshotLesson(TaughtTargets.Sample sample) {
        this.sample = sample;
    }

    static ScreenshotLesson create(Bitmap original, ScreenshotFrame frame, double x, double y) {
        TaughtTargets.Sample sample = TaughtTargets.Sample.from(frame.pixels, frame.width, frame.height, x, y);
        return sample == null ? null : new ScreenshotLesson(sample.withPreview(LessonPreview.encode(original)));
    }

    boolean save(Context context, String pkg) {
        if (pkg == null || pkg.isEmpty()) return false;
        boolean saved = AppPrefs.taught(AppPrefs.open(context)).saveSample(pkg, sample);
        if (saved) AdSkipService.reloadTaught();
        return saved;
    }
}
