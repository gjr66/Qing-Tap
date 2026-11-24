package local.qingtap.adskip;

// Reference images are tightly cropped from the observed cabinet launch and countdown. The launch
// match arms a short window; the live text bounds, never the reference coordinates, determine the tap.
final class LightCubeCover {
    private final int[][] launch;
    private final int[] digits;
    private final int[] title;

    LightCubeCover(int[] cabinet, int[] initializing, int[] digits, int[] title) {
        this.launch = new int[][]{cabinet, initializing};
        this.digits = digits;
        this.title = title;
    }

    boolean launchFrame(ScreenshotFrame f) {
        if (f.width >= f.height) return false;
        if (hasAppTitle(f)) return true;
        for (int[] reference : launch) {
            for (float scale : new float[]{.50f, .46f, .54f}) {
                int size = Math.round(f.width * scale);
                for (float offset : new float[]{0f, -.025f, .025f}) {
                    int left = (f.width - size) / 2;
                    int top = (f.height - size) / 2 + Math.round(f.height * offset);
                    long error = 0;
                    for (int y = 0; y < 28; y++) {
                        for (int x = 0; x < 28; x++) {
                            int color = f.pixels[(top + (2*y+1)*size/56)*f.width + left + (2*x+1)*size/56];
                            int ref = reference[y*28+x];
                            for (int shift = 0; shift <= 16; shift += 8)
                                error += Math.abs(((color >> shift) & 255) - ((ref >> shift) & 255));
                        }
                    }
                    if (error < 28 * 28 * 3 * 14) return true;
                }
            }
        }
        return false;
    }

    // The first WeChat loading screen displays the mini-app's full name on white. Locate
    // its ink before matching so font size and status-bar insets can vary.
    private boolean hasAppTitle(ScreenshotFrame f) {
        int left = f.width, top = f.height, right = -1, bottom = -1;
        int x0 = f.width / 4, x1 = f.width - x0;
        int y0 = f.height * 47 / 100, y1 = f.height * 54 / 100;
        int dark = 0;
        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) {
                if ((f.pixels[y * f.width + x] & 255) >= 125) continue;
                if (++dark > (x1-x0)*(y1-y0)/4) return false;
                left = Math.min(left, x); right = Math.max(right, x);
                top = Math.min(top, y); bottom = Math.max(bottom, y);
            }
        }
        int w = right - left + 1, h = bottom - top + 1;
        if (h < 8 || w < 6.8f*h || w > 8.2f*h) return false;
        int error = 0;
        for (int y = 0; y < 15; y++) {
            int row = (top + (2*y+1)*h/30)*f.width;
            for (int x = 0; x < 112; x++) {
                int value = f.pixels[row + left + (2*x+1)*w/224] & 255;
                error += Math.abs(value - (title[y*112+x] & 255));
            }
        }
        return error < 112*15*49;
    }

    MiniProgramVision.Target countdown(ScreenshotFrame f) {
        if (f.width >= f.height) return null;
        MiniProgramVision.Target onVideo = videoButton(f);
        if (onVideo != null) return onVideo;
        int left = f.width, top = f.height, right = -1, bottom = -1;
        int x0 = f.width * 65 / 100, x1 = f.width * 98 / 100;
        int y0 = f.height * 10 / 100, y1 = f.height * 20 / 100;
        int lit = 0;
        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) {
                int color = f.pixels[y * f.width + x];
                if ((color & 0xffffff) == 0) continue;
                if (++lit > (x1-x0)*(y1-y0)/8) return null;
                if ((color & 255) <= 150 || ((color >> 8) & 255) <= 150 || ((color >> 16) & 255) <= 150) continue;
                left = Math.min(left,x); right = Math.max(right,x);
                top = Math.min(top,y); bottom = Math.max(bottom,y);
            }
        }
        int h = bottom - top + 1, w = right - left + 1;
        if (h < 10 || h > f.height * .035f || w < 3.8f*h || w > 4.8f*h) return null;
        int width = Math.round(h*4.5f), start = right+1-width;
        if (start < x0) return null;
        int[] diff = new int[6];
        int letters = 0;
        for (int y = 0; y < 20; y++) {
            int row = (top+(2*y+1)*h/40)*f.width;
            for (int x = 0; x < 90; x++) {
                int ink = f.pixels[row + start+(2*x+1)*width/180] & 255;
                if (x < 15) {
                    for (int n=0; n<6; n++) diff[n] += Math.abs(ink-(digits[(n*20+y)*90+x]&255));
                } else letters += Math.abs(ink-(digits[y*90+x]&255));
            }
        }
        if (letters > 75*20*48) return null;
        int best=0, second=Integer.MAX_VALUE;
        for (int n=1; n<6; n++) {
            if (diff[n] < diff[best]) { second = diff[best]; best=n; }
            else second = Math.min(second,diff[n]);
        }
        if (diff[best] > 15*20*55 || second-diff[best] < 15*20*3) return null;
        return new MiniProgramVision.Target((start+width*.78f)/f.width, (top+h*.5f)/f.height,
                (start+width*.5f)/f.width, best+"秒｜关闭", best);
    }
    // Both covers place white lettering on a dark button below the capsule. Search for the
    // narrow vertical separator, then verify the digit and the rest of the label independently.
    private MiniProgramVision.Target videoButton(ScreenshotFrame f) {
        int top = f.height * 10 / 100, bottom = f.height * 17 / 100;
        int minHeight = Math.max(12, Math.round(f.width * .022f));
        int maxHeight = Math.round(f.width * .043f);
        MiniProgramVision.Target found = null;
        for (int x = f.width * 81 / 100; x < f.width * 91 / 100; x++) {
            int y = top;
            while (y < bottom) {
                if (white(f, x, y) < 195) { y++; continue; }
                int first = y++;
                while (y < bottom && white(f, x, y) >= 195) y++;
                int height = y - first;
                if (height < minHeight || height > maxHeight) continue;
                int gap = Math.max(3, height / 5);
                int middle = first + height / 2;
                if (white(f, x-gap, middle) > 155 || white(f, x+gap, middle) > 155) continue;
                MiniProgramVision.Target candidate = readButton(f, x, first, height);
                if (candidate == null) continue;
                if (found != null && (!found.near(candidate) || found.lockerSeconds != candidate.lockerSeconds))
                    return null;
                found = candidate;
            }
        }
        return found;
    }

    private static int white(ScreenshotFrame f, int x, int y) {
        int color = f.pixels[y*f.width+x];
        return Math.min(color & 255, Math.min((color >> 8) & 255, (color >> 16) & 255));
    }

    private MiniProgramVision.Target readButton(ScreenshotFrame f, int separator, int top, int height) {
        int bestScore = Integer.MAX_VALUE;
        MiniProgramVision.Target best = null;
        for (int h = height-1; h <= height+2; h++) {
            int w = Math.round(h*4.5f);
            int left = separator - Math.round(w*.47f);
            if (left < 0 || left+w >= f.width) continue;
            for (int dy = -1; dy <= 1; dy++) {
                int letters = 0, ink = 0;
                int[] digitErrors = new int[6];
                for (int y = 0; y < 20; y++) {
                    int sy = top + dy + (2*y+1)*h/40;
                    for (int x = 0; x < 90; x++) {
                        boolean actual = white(f, left+(2*x+1)*w/180, sy) >= 165;
                        if (x >= 15) {
                            boolean expected = (digits[y*90+x] & 255) >= 110;
                            if (actual != expected) letters++;
                            if (expected) ink++;
                        } else {
                            for (int n = 0; n < 6; n++)
                                if (actual != ((digits[(n*20+y)*90+x] & 255) >= 110)) digitErrors[n]++;
                        }
                    }
                }
                if (letters > ink*.42f) continue;
                int digit = 0, second = Integer.MAX_VALUE;
                for (int n = 1; n < 6; n++) {
                    if (digitErrors[n] < digitErrors[digit]) {
                        second = digitErrors[digit];
                        digit = n;
                    } else second = Math.min(second, digitErrors[n]);
                }
                int score = letters + digitErrors[digit];
                if (digitErrors[digit] > 35 || second-digitErrors[digit] < 5 || score >= bestScore) continue;
                bestScore = score;
                best = new MiniProgramVision.Target((left+w*.78f)/f.width, (top+dy+h*.5f)/f.height,
                        (left+w*.5f)/f.width, digit+"秒｜关闭", digit);
            }
        }
        return best;
    }

}
