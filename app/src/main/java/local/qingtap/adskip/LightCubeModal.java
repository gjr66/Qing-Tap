package local.qingtap.adskip;

// The product modal floats above the dimmed three-tab locker page. Locate the live X
// independently of the creative, then verify the ad badge directly below and to its left.
final class LightCubeModal {
    private final int[] badge, tab;

    LightCubeModal(int[] badge, int[] tab) {
        this.badge = badge;
        this.tab = tab;
    }

    private static int scale(int n, int width) { return Math.round(n * width / 1080f); }
    private static int light(int pixel) {
        return ((pixel >> 16 & 255) + (pixel >> 8 & 255) + (pixel & 255)) / 3;
    }
    private static int sample(ScreenshotFrame f, int x, int y) {
        return f.pixels[y * f.width + x];
    }

    private boolean dimmedLockerTabs(ScreenshotFrame f) {
        int w = f.width;
        int left = scale(488,w), width = scale(104,w), height = scale(109,w);
        for (int bottom = f.height-scale(24,w); bottom >= f.height-scale(150,w); bottom--) {
            int top = bottom-height, error = 0;
            for (int y=0; y<27 && error < 26*27*5; y++) {
                for (int x=0; x<26; x++) {
                    int color = sample(f,left+(2*x+1)*width/52,top+(2*y+1)*height/54);
                    error += Math.abs(light(color)-light(tab[y*26+x]));
                }
            }
            if (error < 26*27*5) return true;
        }
        return false;
    }

    private boolean hasBadge(ScreenshotFrame f, int left, int top) {
        int w=scale(103,f.width), h=scale(40,f.width);
        int ink=0, paper=0, inkSum=0, paperSum=0, bright=0;
        for (int y=0; y<20; y++) for (int x=0; x<52; x++) {
            int ref=badge[y*52+x]&255;
            int color=light(sample(f,left+(2*x+1)*w/104,top+(2*y+1)*h/40));
            if(ref>230) {
                ink++; inkSum+=color;
                if(color>207) bright++;
            } else if(ref<182) { paper++; paperSum+=color; }
        }
        return ink>0 && paper>0 && bright*5>=ink*4 && inkSum/ink-paperSum/paper>32;
    }

    private static boolean cross(ScreenshotFrame f, int x, int y) {
        // Both diagonals must be bright inside a dark circle, with dark pixels between arms.
        int d=scale(8,f.width), gap=scale(13,f.width);
        for (int sx : new int[]{-1,1}) for (int sy : new int[]{-1,1}) {
            if (light(sample(f,x+sx*d,y+sy*d))<185) return false;
        }
        return light(sample(f,x-gap,y))<105 && light(sample(f,x+gap,y))<105
                && light(sample(f,x,y-gap))<105 && light(sample(f,x,y+gap))<105;
    }

    private static boolean productButton(ScreenshotFrame f) {
        int w=f.width;
        for (int y=f.height/2; y<f.height-scale(200,w); y+=Math.max(1,scale(12,w))) {
            if (green(sample(f,w/4,y)) && green(sample(f,3*w/4,y))
                    && light(sample(f,w/12,y))>245 && light(sample(f,11*w/12,y))>245) return true;
        }
        return false;
    }

    private static boolean green(int c) {
        return (c>>16&255)<35 && (c>>8&255)>165 && (c>>8&255)<218 && (c&255)>70 && (c&255)<125;
    }

    MiniProgramVision.Target locate(ScreenshotFrame f) {
        int w=f.width, h=f.height;
        if (w<300 || h<w || !productButton(f) || !dimmedLockerTabs(f)) return null;
        for (int y=h/8; y<h/2; y++) for (int x=scale(950,w); x<=scale(978,w); x++) {
            if (light(sample(f,x,y))<195 || !cross(f,x,y)) continue;
            int left=x-scale(869,w), top=y+scale(91,w);
            for (int dy=-2; dy<=2; dy++) for (int dx=-2; dx<=2; dx++) {
                if (hasBadge(f,left+dx,top+dy))
                    return MiniProgramVision.Target.lockerModal(x/(float)w,y/(float)h);
            }
        }
        return null;
    }
}
