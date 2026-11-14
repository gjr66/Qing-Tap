package local.qingtap.adskip;

// Local, model free visual fallback. Given one downscaled screen frame it looks in the four corner
// bands for a small solid blob that stands out from its surroundings, which is what a floating skip
// button or close cross looks like. No network, no model file: the whole decision is arithmetic over
// the pixel buffer, so it costs a few milliseconds on one frame.
final class VisionFinder {
    static final class Point {
        final int x;
        final int y;
        final int score;

        Point(int x, int y, int score) {
            this.x = x;
            this.y = y;
            this.score = score;
        }
    }

    static final int LESSON_FRAME_WIDTH = 360;
    private static final double BAND_WIDTH = .30;
    private static final double BAND_HEIGHT = .16;
    private static final int MIN_AREA = 24;
    private static final int MIN_CONTRAST = 24;
    private static final double MIN_SOLIDITY = .58;
    private static final int MAX_AREA_PERMILLE = 30;
    private static final double CROP_WIDTH = .15;
    private static final double CROP_HEIGHT = .08;
    private static final int MAX_TAUGHT_DISTANCE = 18;
    private static final int MIN_TAUGHT_CONTRAST = 18;
    private static final int MIN_SCENE_CONFIRMED_CONTRAST = 14;
    private static final double MIN_TAUGHT_CORRELATION = .88;

    private VisionFinder() {}

    static Point find(int[] argb, int width, int height) {
        if (argb == null || width < 32 || height < 32 || argb.length < width * height) return null;
        int[] luma = new int[width * height];
        for (int i = 0; i < luma.length; i++) luma[i] = luminance(argb[i]);

        Point best = null;
        for (int corner = 0; corner < 4; corner++) {
            int x0 = (corner == 1 || corner == 3) ? (int) (width * (1 - BAND_WIDTH)) : 0;
            int x1 = x0 + (int) (width * BAND_WIDTH);
            int y0 = corner >= 2 ? (int) (height * (1 - BAND_HEIGHT)) : 0;
            int y1 = y0 + (int) (height * BAND_HEIGHT);
            Point point = scan(luma, width, height, x0, y0, x1, y1);
            if (point != null && (best == null || point.score > best.score)) best = point;
        }
        return best;
    }

    private static Point scan(int[] luma, int width, int height, int x0, int y0, int x1, int y1) {
        int bandW = x1 - x0;
        int bandH = y1 - y0;
        if (bandW < 8 || bandH < 8) return null;

        long sum = 0;
        for (int y = y0; y < y1; y++) {
            int row = y * width;
            for (int x = x0; x < x1; x++) sum += luma[row + x];
        }
        int mean = (int) (sum / (bandW * bandH));

        boolean[] mask = new boolean[bandW * bandH];
        for (int y = 0; y < bandH; y++) {
            int row = (y0 + y) * width + x0;
            int local = y * bandW;
            for (int x = 0; x < bandW; x++) {
                mask[local + x] = Math.abs(luma[row + x] - mean) >= MIN_CONTRAST;
            }
        }

        int maxArea = Math.max(MIN_AREA + 1, width * height * MAX_AREA_PERMILLE / 1000);
        boolean[] seen = new boolean[mask.length];
        int[] stack = new int[mask.length];
        Point best = null;

        for (int start = 0; start < mask.length; start++) {
            if (!mask[start] || seen[start]) continue;
            int top = 0;
            stack[top++] = start;
            seen[start] = true;
            int area = 0;
            int minX = bandW;
            int maxX = -1;
            int minY = bandH;
            int maxY = -1;
            boolean tooBig = false;
            while (top > 0) {
                int index = stack[--top];
                int x = index % bandW;
                int y = index / bandW;
                area++;
                if (x < minX) minX = x;
                if (x > maxX) maxX = x;
                if (y < minY) minY = y;
                if (y > maxY) maxY = y;
                if (area > maxArea) {
                    tooBig = true;
                    break;
                }
                if (x > 0) top = push(seen, stack, top, mask, index - 1);
                if (x + 1 < bandW) top = push(seen, stack, top, mask, index + 1);
                if (y > 0) top = push(seen, stack, top, mask, index - bandW);
                if (y + 1 < bandH) top = push(seen, stack, top, mask, index + bandW);
            }
            if (tooBig || area < MIN_AREA || area > maxArea) continue;

            int boxW = maxX - minX + 1;
            int boxH = maxY - minY + 1;
            if (boxW > bandW - 2 || boxH > bandH - 2) continue;
            double solidity = area / (double) (boxW * boxH);
            if (solidity < MIN_SOLIDITY) continue;
            double aspect = boxW / (double) boxH;
            if (aspect < .55 || aspect > 6.5) continue;

            long inside = 0;
            for (int y = minY; y <= maxY; y++) {
                int row = (y0 + y) * width + x0;
                for (int x = minX; x <= maxX; x++) inside += luma[row + x];
            }
            int contrast = Math.abs((int) (inside / (boxW * boxH)) - mean);
            if (contrast < MIN_CONTRAST) continue;

            int score = contrast + (int) (solidity * 40) - (int) (Math.abs(aspect - 1.4) * 6);
            if (best == null || score > best.score) {
                best = new Point(x0 + (minX + maxX) / 2, y0 + (minY + maxY) / 2, score);
            }
        }
        return best;
    }

    private static int push(boolean[] seen, int[] stack, int top, boolean[] mask, int index) {
        if (index < 0 || index >= mask.length || !mask[index] || seen[index]) return top;
        seen[index] = true;
        stack[top] = index;
        return top + 1;
    }

    // A 16x16 grayscale fingerprint of the crop the user pointed at in a screenshot. The live frame is
    // cropped with the same relative box, so the two are comparable at any resolution.
    static int[] templateAt(int[] argb, int width, int height, double xRatio, double yRatio) {
        if (argb == null || width < 32 || height < 32 || argb.length < (long) width * height) return null;
        int cropW = Math.max(8, (int) (width * CROP_WIDTH));
        int cropH = Math.max(8, (int) (height * CROP_HEIGHT));
        int left = (int) (width * xRatio) - cropW / 2;
        int top = (int) (height * yRatio) - cropH / 2;
        if (left < 0) left = 0;
        if (top < 0) top = 0;
        if (left + cropW > width) left = width - cropW;
        if (top + cropH > height) top = height - cropH;
        if (left < 0 || top < 0) return null;
        int size = TaughtTargets.TEMPLATE_SIZE;
        int[] template = new int[size * size];
        for (int row = 0; row < size; row++) {
            int rowStart = top + row * cropH / size;
            int rowEnd = top + (row + 1) * cropH / size;
            if (rowEnd <= rowStart) rowEnd = rowStart + 1;
            for (int column = 0; column < size; column++) {
                int colStart = left + column * cropW / size;
                int colEnd = left + (column + 1) * cropW / size;
                if (colEnd <= colStart) colEnd = colStart + 1;
                long sum = 0;
                int count = 0;
                for (int y = rowStart; y < rowEnd && y < height; y++) {
                    int base = y * width;
                    for (int x = colStart; x < colEnd && x < width; x++) {
                        sum += luminance(argb[base + x]);
                        count++;
                    }
                }
                template[row * size + column] = count == 0 ? 0 : (int) (sum / count);
            }
        }
        return template;
    }

    static Point matchTaught(int[] argb, int width, int height, TaughtTargets.Target target) {
        return matchTaught(argb, width, height, target, null);
    }

    static Point matchTaught(int[] argb, int width, int height, TaughtTargets.Target target,
                             SceneFingerprint liveScene) {
        if (!compatibleFrame(width, height, target)) return null;
        if (target.hasFingerprint() && !AdFingerprint.same(AdFingerprint.of(argb, width, height), target.fingerprint)) {
            return null;
        }
        if (liveScene != null && liveScene.matches(target.scene)) {
            Point center = matchPoint(argb, width, height, target, target.xRatio(), target.yRatio(),
                    MIN_SCENE_CONFIRMED_CONTRAST);
            if (center != null) return center;
        }
        Point best = null;
        for (int row = -2; row <= 2; row++) {
            for (int column = -1; column <= 1; column++) {
                double x = target.xRatio() + column * .009;
                double y = target.yRatio() + row * .008;
                if (x <= 0 || x >= 1 || y <= 0 || y >= 1) continue;
                Point point = matchPoint(argb, width, height, target, x, y, MIN_TAUGHT_CONTRAST);
                if (point != null && (best == null || point.score > best.score)) best = point;
            }
        }
        return best;
    }

    static Point matchTaughtQuick(int[] pixels, int width, int height, TaughtTargets.Target target,
                                  SceneFingerprint scene) {
        if (!compatibleFrame(width, height, target) || scene == null || !scene.matches(target.scene)) return null;
        return matchPoint(pixels, width, height, target, target.xRatio(), target.yRatio(),
                MIN_SCENE_CONFIRMED_CONTRAST);
    }

    private static boolean compatibleFrame(int width, int height, TaughtTargets.Target target) {
        return target.hasTemplate() && height >= 32 && width >= 32 && target.aspectMilli > 0
                && Math.abs(width * 1000.0 / height - target.aspectMilli) <= target.aspectMilli * .06;
    }

    private static Point matchPoint(int[] argb, int width, int height, TaughtTargets.Target target,
                                    double x, double y, int minContrast) {
        int[] live = templateAt(argb, width, height, x, y);
        if (live == null) return null;
        int score = taughtScore(target.template, live, minContrast);
        return score < 0 ? null : new Point((int) (x * width), (int) (y * height), score);
    }

    static boolean usableTemplate(int[] template) {
        return template != null && taughtScore(template, template, MIN_SCENE_CONFIRMED_CONTRAST) >= 0;
    }

    private static int taughtScore(int[] saved, int[] live, int minContrast) {
        int count = saved.length;
        int difference = 0;
        long savedSum = 0, liveSum = 0, savedSquares = 0, liveSquares = 0, cross = 0;
        for (int i = 0; i < count; i++) {
            int a = saved[i];
            int b = live[i];
            difference += Math.abs(a - b);
            savedSum += a;
            liveSum += b;
            savedSquares += a * a;
            liveSquares += b * b;
            cross += a * b;
        }
        if (difference > MAX_TAUGHT_DISTANCE * count) return -1;
        double savedVariance = savedSquares - (double) savedSum * savedSum / count;
        double liveVariance = liveSquares - (double) liveSum * liveSum / count;
        double minVariance = minContrast * minContrast * count;
        if (savedVariance < minVariance || liveVariance < minVariance) return -1;
        double correlation = (cross - (double) savedSum * liveSum / count)
                / Math.sqrt(savedVariance * liveVariance);
        if (correlation < MIN_TAUGHT_CORRELATION) return -1;
        return (int) (correlation * 1000) - difference / count;
    }

    private static int luminance(int pixel) {
        int red = (pixel >> 16) & 0xff;
        int green = (pixel >> 8) & 0xff;
        int blue = pixel & 0xff;
        return (red * 77 + green * 150 + blue * 29) >> 8;
    }
}
