package local.qingtap.adskip;

import android.graphics.Bitmap;

import java.util.List;

final class LiveScreenshotMatch {
    final SceneFingerprint scene;
    final VisionFinder.Point point;
    final TaughtTargets.Target lesson;
    final boolean damai;
    final boolean hasLessons;

    private LiveScreenshotMatch(SceneFingerprint scene, VisionFinder.Point point, TaughtTargets.Target lesson,
                 boolean damai, boolean hasLessons) {
        this.scene = scene;
        this.point = point;
        this.lesson = lesson;
        this.damai = damai;
        this.hasLessons = hasLessons;
    }

    static LiveScreenshotMatch find(Bitmap bitmap, int screenWidth, int screenHeight,
                                             List<TaughtTargets.Target> lessons, boolean damai) {
        ScreenshotFrame canonical = ScreenshotFrame.read(bitmap, VisionFinder.LESSON_FRAME_WIDTH);
        SceneFingerprint scene = canonical.scene();
        TaughtSearch.Match match = TaughtSearch.forLiveFrame(canonical.pixels, canonical.width,
                canonical.height, lessons, scene);
        ScreenshotFrame detailed = canonical;
        if ((damai || match == null && !lessons.isEmpty()) && bitmap.getWidth() > canonical.width) {
            detailed = ScreenshotFrame.read(bitmap, 540);
        }
        ScreenshotFrame matchedFrame = canonical;
        if (match == null && detailed != canonical && !lessons.isEmpty()) {
            match = TaughtSearch.forLiveFrame(detailed.pixels, detailed.width, detailed.height, lessons, detailed.scene());
            matchedFrame = detailed;
        }
        VisionFinder.Point skip = damai ? DamaiSplash.find(detailed.pixels, detailed.width, detailed.height) : null;
        VisionFinder.Point taughtPoint = match == null ? null : scalePoint(match.point, matchedFrame, screenWidth, screenHeight);
        if (skip != null) {
            VisionFinder.Point actual = scalePoint(skip, detailed, screenWidth, screenHeight);
            boolean sameTarget = taughtPoint != null && Math.abs(actual.x - taughtPoint.x) <= screenWidth * .028
                    && Math.abs(actual.y - taughtPoint.y) <= screenHeight * .018;
            return new LiveScreenshotMatch(scene, actual, sameTarget ? match.lesson : null, true, !lessons.isEmpty());
        }
        return new LiveScreenshotMatch(scene, taughtPoint, match == null ? null : match.lesson, false, !lessons.isEmpty());
    }

    private static VisionFinder.Point scalePoint(VisionFinder.Point point, ScreenshotFrame frame,
                                                  int screenWidth, int screenHeight) {
        return new VisionFinder.Point(Math.round(point.x * (float) screenWidth / frame.width),
                Math.round(point.y * (float) screenHeight / frame.height), point.score);
    }
}
