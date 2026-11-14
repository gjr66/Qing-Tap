package local.qingtap.adskip;

// Cheap whole screen fingerprint: an 8x8 grid of average R/G/B over each cell (192 bytes, 384 hex
// characters). It answers one question only — "is this the same picture I learned" — and is used as a
// gate before trusting a learned click point, never as evidence that a point is a button.
//
// A luminance-only grid was tried first and rejected: a uniform blue screen and a grey checkerboard
// land on the same average brightness, so colour has to be part of the signature.
final class AdFingerprint {
    static final int GRID = 8;
    static final int CHANNELS = 3;
    static final int LENGTH = GRID * GRID * CHANNELS;
    // Mean absolute difference per channel on a 0..255 scale. The same creative with a different status
    // bar stays well under this; a different creative goes far above it.
    static final int MATCH_DISTANCE = 18;

    private AdFingerprint() {}

    static int[] of(int[] argb, int width, int height) {
        if (argb == null || width < GRID || height < GRID || argb.length < width * height) return null;
        int[] grid = new int[LENGTH];
        for (int row = 0; row < GRID; row++) {
            int top = row * height / GRID;
            int bottom = Math.max(top + 1, (row + 1) * height / GRID);
            for (int column = 0; column < GRID; column++) {
                int left = column * width / GRID;
                int right = Math.max(left + 1, (column + 1) * width / GRID);
                long red = 0;
                long green = 0;
                long blue = 0;
                int count = 0;
                for (int y = top; y < bottom; y += 2) {
                    for (int x = left; x < right; x += 2) {
                        int pixel = argb[y * width + x];
                        red += (pixel >> 16) & 0xff;
                        green += (pixel >> 8) & 0xff;
                        blue += pixel & 0xff;
                        count++;
                    }
                }
                int base = (row * GRID + column) * CHANNELS;
                if (count == 0) {
                    grid[base] = 0;
                    grid[base + 1] = 0;
                    grid[base + 2] = 0;
                } else {
                    grid[base] = (int) (red / count);
                    grid[base + 1] = (int) (green / count);
                    grid[base + 2] = (int) (blue / count);
                }
            }
        }
        return grid;
    }

    // -1 when the two fingerprints are not comparable at all.
    static int distance(int[] left, int[] right) {
        if (left == null || right == null || left.length != LENGTH || right.length != LENGTH) return -1;
        long sum = 0;
        for (int index = 0; index < LENGTH; index++) sum += Math.abs(left[index] - right[index]);
        return (int) (sum / LENGTH);
    }

    static boolean same(int[] left, int[] right) {
        int value = distance(left, right);
        return value >= 0 && value <= MATCH_DISTANCE;
    }

    static String encode(int[] grid) {
        if (grid == null || grid.length != LENGTH) return "";
        StringBuilder builder = new StringBuilder(LENGTH * 2);
        for (int value : grid) {
            int clamped = Math.max(0, Math.min(255, value));
            builder.append(Character.forDigit(clamped >> 4, 16));
            builder.append(Character.forDigit(clamped & 0xf, 16));
        }
        return builder.toString();
    }

    static int[] decode(String text) {
        if (text == null || text.length() != LENGTH * 2) return null;
        int[] grid = new int[LENGTH];
        for (int index = 0; index < LENGTH; index++) {
            int high = Character.digit(text.charAt(index * 2), 16);
            int low = Character.digit(text.charAt(index * 2 + 1), 16);
            if (high < 0 || low < 0) return null;
            grid[index] = (high << 4) | low;
        }
        return grid;
    }
}
