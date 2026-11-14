package local.qingtap.adskip;

import android.graphics.Bitmap;

final class ScreenshotFrame {
    final int width;
    final int height;
    final int[] pixels;

    ScreenshotFrame(int width, int height, int[] pixels) {
        this.width = width;
        this.height = height;
        this.pixels = pixels;
    }

    static ScreenshotFrame read(Bitmap bitmap, int maxWidth) {
        int width = Math.min(maxWidth, bitmap.getWidth());
        int height = Math.max(1, Math.round(bitmap.getHeight() * (float) width / bitmap.getWidth()));
        Bitmap scaled = Bitmap.createScaledBitmap(bitmap, width, height, true);
        try {
            int[] pixels = new int[width * height];
            scaled.getPixels(pixels, 0, width, 0, 0, width, height);
            return new ScreenshotFrame(width, height, pixels);
        } finally {
            if (scaled != bitmap) scaled.recycle();
        }
    }

    SceneFingerprint scene() {
        return SceneFingerprint.of(pixels, width, height);
    }
}
