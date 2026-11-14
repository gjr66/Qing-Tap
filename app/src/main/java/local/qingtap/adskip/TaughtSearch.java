package local.qingtap.adskip;

import java.util.ArrayList;
import java.util.List;

final class TaughtSearch {
    static final class Match {
        final TaughtTargets.Target lesson;
        final VisionFinder.Point point;

        Match(TaughtTargets.Target lesson, VisionFinder.Point point) {
            this.lesson = lesson;
            this.point = point;
        }
    }

    private TaughtSearch() {}

    static List<Match> search(int[] pixels, int width, int height, List<TaughtTargets.Target> lessons,
                              SceneFingerprint scene, boolean detailed) {
        List<Match> hits = new ArrayList<>();
        for (TaughtTargets.Target lesson : lessons) {
            VisionFinder.Point point = detailed ? VisionFinder.matchTaught(pixels, width, height, lesson)
                    : VisionFinder.matchTaughtQuick(pixels, width, height, lesson, scene);
            if (point != null) hits.add(new Match(lesson, point));
        }
        hits.sort((a, b) -> Integer.compare(b.point.score, a.point.score));
        return hits;
    }

    static Match forLiveFrame(int[] pixels, int width, int height, List<TaughtTargets.Target> lessons,
                              SceneFingerprint scene) {
        List<Match> hits = search(pixels, width, height, lessons, scene, false);
        if (hits.isEmpty()) hits = search(pixels, width, height, lessons, scene, true);
        return unambiguous(hits, width, height);
    }

    static Match unambiguous(List<Match> hits, int width, int height) {
        if (hits.isEmpty()) return null;
        Match best = hits.get(0);
        for (Match hit : hits) {
            if (!hit.lesson.pkg.equals(best.lesson.pkg)
                    || Math.abs(hit.point.x - best.point.x) > width * .03
                    || Math.abs(hit.point.y - best.point.y) > height * .025) return null;
        }
        return best;
    }
}
