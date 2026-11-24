package local.qingtap.adskip;

// The card fills the slot between the two pickup-page buttons and the tab bar.
// Inspect the underlying application window while covered so an incoming login panel stays usable.
final class LightCubeCard {
    static final String ID = "lightcube_pickup_ad";
    private final int[] pickup, badge;

    LightCubeCard(int[] pickup, int[] badge) {
        this.pickup = pickup;
        this.badge = badge;
    }

    AdRules.Box locate(ScreenshotFrame f) {
        int w = f.width, h = f.height;
        if (w < 240 || h < w) return null;
        int blackTop = -1, blackEnd = -1;
        for (int y = h / 4; y < h * 4 / 5; y++) {
            boolean black = light(f, w / 3, y) < 24 && light(f, w * 2 / 3, y) < 24;
            if (black) {
                if (blackTop < 0) blackTop = y;
            } else if (blackTop >= 0) {
                if (y - blackTop > w * .09 && y - blackTop < w * .17) {
                    blackEnd = y;
                    break;
                }
                blackTop = -1;
            }
        }
        if (blackEnd < 0 || light(f, w / 10, blackTop) < 245) return null;
        int center = (blackEnd + blackTop) / 2;
        if (!matches(f, w * 400 / 1080, center - w * 31 / 1080,
                w * 280 / 1080, w * 63 / 1080, pickup, 84, 19, false)) return null;

        int redEnd = -1;
        for (int y = blackEnd; y < Math.min(h, blackEnd + w / 3); y++) {
            if (red(f, w/3, y) && red(f, 2*w/3, y)) redEnd = y;
            else if (redEnd >= 0) break;
        }
        if (redEnd < blackEnd + w * .13 || redEnd > blackEnd + w * .27) return null;
        int top = redEnd + w * 52 / 1000;
        int bottom = navTop(f, top);
        if (bottom < top + w / 8 || bottom > top + w * .65) return null;
        AdRules.Box box = new AdRules.Box(w * 45 / 1000, top, w * 955 / 1000, bottom);
        // Live creatives and the loading player have the same small badge. Search below the
        // buttons, independently of the artwork or whether a progress bar is currently shown.
        int bw = w * 62 / 1080, bh = w * 34 / 1080;
        for (int y = redEnd + w * 85 / 1000; y <= Math.min(bottom-bh, redEnd+w*135/1000); y++) {
            for (int x = w * 86 / 1000; x <= w * 97 / 1000; x++) {
                if (matches(f, x, y, bw, bh, badge, 42, 23, true)) return box;
            }
        }
        return null;
    }

    private static int navTop(ScreenshotFrame f, int top) {
        int w = f.width, h = f.height;
        for (int y = Math.max(top+2, h-w/3); y < h-w/12; y++) {
            boolean edge = true;
            for (int i = 1; i <= 9; i++) {
                int x = w*i/10;
                int value = light(f,x,y);
                if (value < 208 || value > 249 || light(f,x,y-2) < 250 || light(f,x,y+4) < 250) {
                    edge = false;
                    break;
                }
            }
            if (edge) return y;
        }
        return -1;
    }

    private static boolean matches(ScreenshotFrame f, int left, int top, int w, int h,
                                   int[] ref, int rw, int rh, boolean translucent) {
        if (top < 0 || left < 0 || top+h > f.height || left+w > f.width) return false;
        int ink = 0, background = 0, lit = 0, inkSum = 0, bgSum = 0;
        for (int y = 0; y < rh; y++) {
            int sy = top + (2*y+1)*h/(2*rh);
            for (int x = 0; x < rw; x++) {
                int expected = ref[y*rw+x] & 255;
                int value = light(f, left + (2*x+1)*w/(2*rw), sy);
                if (expected > 195) {
                    ink++;
                    inkSum += value;
                    if (value > (translucent ? 219 : 155)) lit++;
                } else if (expected < 40) {
                    background++;
                    bgSum += value;
                }
            }
        }
        return ink > 0 && background > 0 && lit >= ink * .78
                && inkSum / ink - bgSum / background >= (translucent ? 25 : 135);
    }

    private static int light(ScreenshotFrame f, int x, int y) {
        int c = f.pixels[y*f.width+x];
        return Math.min(c & 255, Math.min((c >>> 8) & 255, (c >>> 16) & 255));
    }

    private static boolean red(ScreenshotFrame f, int x, int y) {
        int c = f.pixels[y*f.width+x];
        return ((c >>> 16) & 255) > 205 && ((c >>> 8) & 255) < 115 && (c & 255) < 115;
    }
}
