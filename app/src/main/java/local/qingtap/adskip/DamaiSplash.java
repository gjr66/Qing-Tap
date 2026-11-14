package local.qingtap.adskip;

final class DamaiSplash {
    static final String PACKAGE = "cn.damai";

    // Masks cover only the stable UI lettering. No poster pixels are part of the rule.
    private static final String[] SKIP = {
            "...............................",
            "...............................",
            "...####..#.#......#.......#....",
            "...#..#..#.#......##......#....",
            "...#..##.#.#.......##########..",
            "...#..#.##.#.#............#....",
            "...####.##.###.......#....#....",
            ".....#...#.#....####..#...#....",
            "...#.##.##.###....##...#..#....",
            "...#.#.#.#.#..#...##......#....",
            "...#.#...#.#......##......#....",
            "...#.#...#.#..#...##...###.....",
            "...#.##.#..#..#..###...........",
            "..###..##..####.##..#########..",
            "..............................."
    };
    private static final String[] BRAND = {
            "................#......##########.",
            "............#########......##.....",
            "............##########.##########.",
            "...............###.....##########.",
            "...............#.#.......#........",
            "..............##.##....#########..",
            ".............##...##...#.##...##..",
            "............##.....##.....#####...",
            "............#.......#..#####.####."
    };

    private DamaiSplash() {}

    static boolean startup(String pkg, String activity, long age) {
        return PACKAGE.equals(pkg) && activity.endsWith("SplashMainActivity") && age <= 6000;
    }

    static VisionFinder.Point find(int[] pixels, int width, int height) {
        if (width < 240 || height < width * 1.65 || height > width * 2.55) return null;
        int white = 0;
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 6; x++) {
                int color = pixels[(int) (height * (.86 + .017 * y)) * width
                        + (int) (width * (x < 3 ? .04 + .045 * x : .96 - .045 * (x - 3)))];
                if (Math.min(color & 255, Math.min(color >> 8 & 255, color >> 16 & 255)) >= 236) white++;
            }
        }
        if (white < 43) return null;
        VisionFinder.Point brand = search(pixels, width, height, BRAND, .519, .914,
                width * .008, width * .00769, .016, .022, true);
        if (brand == null || brand.score < 77) return null;
        VisionFinder.Point skip = search(pixels, width, height, SKIP, .916, .0695,
                width * .002, width * .002, .018, .020, false);
        return skip != null && skip.score >= 76 ? skip : null;
    }

    private static VisionFinder.Point search(int[] pixels, int width, int height, String[] mask, double centerX,
                                            double centerY, double dx, double dy, double xRange,
                                            double yRange, boolean brand) {
        int positives = 0, negatives = 0;
        for (String row : mask) {
            for (int x = 0; x < row.length(); x++) {
                if (row.charAt(x) == '#') positives++;
                else negatives++;
            }
        }
        VisionFinder.Point best = null;
        double[] scales = {.90, .96, 1, 1.05, 1.11};
        int step = brand ? Math.max(1, width / 360) : 1;
        for (double scale : scales) {
            for (int y = (int) (height * (centerY - yRange)); y <= height * (centerY + yRange); y += step) {
                for (int x = (int) (width * (centerX - xRange)); x <= width * (centerX + xRange); x += step) {
                    int found = 0, wrong = 0;
                    for (int row = 0; row < mask.length; row++) {
                        int offset = (int) Math.round(y + (row - (mask.length - 1) / 2.0) * dy * scale) * width;
                        String line = mask[row];
                        for (int col = 0; col < line.length(); col++) {
                            int index = (int) Math.round(x + (col - (line.length() - 1) / 2.0) * dx * scale);
                            int rgb = pixels[offset + index];
                            int r = rgb >> 16 & 255, g = rgb >> 8 & 255, b = rgb & 255;
                            boolean ink = brand ? r > 150 && r - g > 50 && b - g > 19
                                    : (77 * r + 150 * g + 29 * b) >> 8 >= 160;
                            if (ink && line.charAt(col) == '#') found++;
                            else if (ink) wrong++;
                        }
                    }
                    if (found * 100 < positives * 61 || wrong * 100 > negatives * 29) continue;
                    int score = 50 + (50 * found / positives) - (50 * wrong / negatives);
                    if (best == null || best.score < score) best = new VisionFinder.Point(x, y, score);
                }
            }
        }
        return best;
    }
}
