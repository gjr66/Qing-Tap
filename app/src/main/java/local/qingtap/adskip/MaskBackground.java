package local.qingtap.adskip;

import java.util.List;

// Samples only OUTSIDE all advertising rectangles. Four edge colours let the renderer continue
// a native flat background or gentle gradient without copying any advertising pixels.
final class MaskBackground {
    static final class Patch {
        final int topLeft, topRight, bottomLeft, bottomRight;
        Patch(int tl, int tr, int bl, int br) { topLeft = tl; topRight = tr; bottomLeft = bl; bottomRight = br; }
        static Patch solid(int colour) { return new Patch(colour, colour, colour, colour); }
        boolean near(Patch other) {
            return other != null && distance(topLeft, other.topLeft) < 15 && distance(topRight, other.topRight) < 15
                    && distance(bottomLeft, other.bottomLeft) < 15 && distance(bottomRight, other.bottomRight) < 15;
        }
    }

    static Patch sample(int[] pixels, int width, int height, AdRules.Box box,
                        List<AdRules.Box> excluded, int fallback) {
        if (pixels == null || width < 16 || height < 16 || pixels.length < (long) width * height) return Patch.solid(fallback);
        int pad = Math.max(3, width / 140), radius = Math.max(2, width / 150);
        int xl = box.left + box.width() / 5, xr = box.right - box.width() / 5;
        int yt = box.top + box.height() / 5, yb = box.bottom - box.height() / 5;
        Integer topLeft = probe(pixels, width, height, xl, box.top - pad - radius, radius, excluded);
        Integer topRight = probe(pixels, width, height, xr, box.top - pad - radius, radius, excluded);
        Integer bottomLeft = probe(pixels, width, height, xl, box.bottom + pad + radius, radius, excluded);
        Integer bottomRight = probe(pixels, width, height, xr, box.bottom + pad + radius, radius, excluded);
        Integer left = probe(pixels, width, height, box.left - pad - radius, yt, radius, excluded);
        Integer right = probe(pixels, width, height, box.right + pad + radius, yb, radius, excluded);
        int tl = choose(topLeft, left, fallback), tr = choose(topRight, right, tl);
        int bl = choose(bottomLeft, left, tl), br = choose(bottomRight, right, tr);
        return new Patch(tl, tr, bl, br);
    }

    private static int choose(Integer primary, Integer side, int fallback) {
        return primary != null ? primary : side != null ? side : fallback;
    }

    private static Integer probe(int[] pixels, int width, int height, int cx, int cy, int radius, List<AdRules.Box> excluded) {
        if (cx - radius < 0 || cx + radius >= width || cy - radius < 0 || cy + radius >= height) return null;
        int[] bins = new int[4096];
        int best = 0, total = 0;
        for (int y = cy - radius; y <= cy + radius; y++) {
            for (int x = cx - radius; x <= cx + radius; x++) {
                if (inside(x, y, excluded)) continue;
                int c = pixels[y * width + x];
                int key = ((c >> 12) & 0xf00) | ((c >> 8) & 0xf0) | ((c >> 4) & 0xf);
                if (++bins[key] > bins[best]) best = key;
                total++;
            }
        }
        if (total < 8 || bins[best] * 3 < total) return null;
        long red = 0, green = 0, blue = 0;
        int count = 0;
        for (int y = cy - radius; y <= cy + radius; y++) {
            for (int x = cx - radius; x <= cx + radius; x++) {
                if (inside(x, y, excluded)) continue;
                int c = pixels[y * width + x];
                int key = ((c >> 12) & 0xf00) | ((c >> 8) & 0xf0) | ((c >> 4) & 0xf);
                if (key != best) continue;
                red += (c >> 16) & 255; green += (c >> 8) & 255; blue += c & 255; count++;
            }
        }
        return count == 0 ? null : 0xff000000 | (int) (red / count) << 16
                | (int) (green / count) << 8 | (int) (blue / count);
    }

    private static boolean inside(int x, int y, List<AdRules.Box> boxes) {
        for (AdRules.Box box : boxes) if (x >= box.left && x < box.right && y >= box.top && y < box.bottom) return true;
        return false;
    }

    private static int distance(int a, int b) {
        return Math.abs((a >> 16 & 255) - (b >> 16 & 255))
                + Math.abs((a >> 8 & 255) - (b >> 8 & 255)) + Math.abs((a & 255) - (b & 255));
    }

    private MaskBackground() {}
}
