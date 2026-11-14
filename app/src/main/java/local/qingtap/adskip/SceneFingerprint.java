package local.qingtap.adskip;

import java.util.Base64;

final class SceneFingerprint {
    private static final int WIDTH = 64;
    private static final int HEIGHT = 120;
    private static final int COLUMNS = 4;
    private static final int ROWS = 6;
    private static final int CELLS = COLUMNS * ROWS;
    private static final int BINS = 16;
    private static final int GLOBAL = 4 * BINS;
    private static final int LAB = GLOBAL + CELLS * BINS;
    private static final int LENGTH = LAB + CELLS * 3;
    private static final int[] LINEAR = new int[256];

    static {
        for (int i = 0; i < LINEAR.length; i++) {
            double channel = i / 255.0;
            double linear = channel <= .04045 ? channel / 12.92 : Math.pow((channel + .055) / 1.055, 2.4);
            LINEAR[i] = (int) Math.round(linear * 4096);
        }
    }

    private final byte[] values;

    private SceneFingerprint(byte[] values) {
        this.values = values;
    }

    static SceneFingerprint of(int[] pixels, int width, int height) {
        if (pixels == null || width < 32 || height < 32 || (long) width * height > pixels.length) return null;
        int[] counts = new int[LAB];
        int[] linear = new int[CELLS * 3];
        // The middle region is stable across status/navigation insets. The skip crop still uses the full frame.
        int top = height / 10;
        int contentHeight = height * 93 / 100 - top;
        for (int y = 0; y < HEIGHT; y++) {
            int row = (top + (2 * y + 1) * contentHeight / (2 * HEIGHT)) * width;
            int tileRow = y * ROWS / HEIGHT * COLUMNS;
            for (int x = 0; x < WIDTH; x++) {
                int color = pixels[row + (2 * x + 1) * width / (2 * WIDTH)];
                int red = (color >> 16) & 255;
                int green = (color >> 8) & 255;
                int blue = color & 255;
                int gray = (54 * red + 183 * green + 19 * blue) >> 8;
                int cell = tileRow + x * COLUMNS / WIDTH;
                counts[red >> 4]++;
                counts[BINS + (green >> 4)]++;
                counts[2 * BINS + (blue >> 4)]++;
                counts[3 * BINS + (gray >> 4)]++;
                counts[GLOBAL + cell * BINS + (gray >> 4)]++;
                int offset = cell * 3;
                linear[offset] += LINEAR[red];
                linear[offset + 1] += LINEAR[green];
                linear[offset + 2] += LINEAR[blue];
            }
        }
        int tilePixels = WIDTH * HEIGHT / CELLS;
        byte[] values = new byte[LENGTH];
        for (int i = 0; i < counts.length; i++) {
            int total = i < GLOBAL ? WIDTH * HEIGHT : tilePixels;
            values[i] = (byte) ((counts[i] * 255 + total / 2) / total);
        }
        for (int cell = 0; cell < CELLS; cell++) {
            int index = cell * 3;
            double scale = tilePixels * 4096.0;
            double r = linear[index] / scale, g = linear[index + 1] / scale, b = linear[index + 2] / scale;
            double x = labCurve((.4124564 * r + .3575761 * g + .1804375 * b) / .95047);
            double y = labCurve(.2126729 * r + .7151522 * g + .0721750 * b);
            double z = labCurve((.0193339 * r + .1191920 * g + .9503041 * b) / 1.08883);
            values[LAB + index] = quantize(116 * y - 16);
            values[LAB + index + 1] = quantize(500 * (x - y) + 128);
            values[LAB + index + 2] = quantize(200 * (y - z) + 128);
        }
        return new SceneFingerprint(values);
    }

    private static double labCurve(double t) {
        return t > 216.0 / 24389 ? Math.cbrt(t) : (24389.0 / 27 * t + 16) / 116;
    }

    private static byte quantize(double value) {
        return (byte) Math.max(0, Math.min(255, Math.round(value)));
    }

    boolean matches(SceneFingerprint other) {
        if (other == null || distance(other, 0, GLOBAL, 4) > .14
                || distance(other, GLOBAL, LAB, CELLS) > .18) return false;
        double labDistance = 0;
        for (int i = LAB; i < LENGTH; i += 3) {
            int l = delta(other, i), a = delta(other, i + 1), b = delta(other, i + 2);
            labDistance += Math.sqrt(l * l + a * a + b * b);
        }
        return labDistance / CELLS <= 14;
    }

    private double distance(SceneFingerprint other, int from, int to, int groups) {
        int total = 0;
        for (int i = from; i < to; i++) total += Math.abs(delta(other, i));
        return total / (2.0 * 255 * groups);
    }

    private int delta(SceneFingerprint other, int i) {
        return (values[i] & 255) - (other.values[i] & 255);
    }

    String encode() {
        return Base64.getEncoder().encodeToString(values);
    }

    static SceneFingerprint decode(String encoded) {
        if (encoded == null || encoded.length() != (LENGTH + 2) / 3 * 4) return null;
        try {
            byte[] bytes = Base64.getDecoder().decode(encoded);
            return bytes.length == LENGTH ? new SceneFingerprint(bytes) : null;
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }
}
