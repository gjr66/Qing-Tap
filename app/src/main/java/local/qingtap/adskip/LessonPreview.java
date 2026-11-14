package local.qingtap.adskip;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.Base64;
import android.view.View;

import java.io.ByteArrayOutputStream;

final class LessonPreview extends View {
    private static final int MAX_JPEG_BYTES = 40 * 1024;
    private static final int[] PREVIEW_EDGES = {720, 540, 360, 256};

    private final Bitmap bitmap;
    private final float xRatio;
    private final float yRatio;
    private final float markerRadius;
    private final RectF imageBounds = new RectF();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

    LessonPreview(Activity activity, Bitmap bitmap, TaughtTargets.Target target, boolean thumbnail) {
        this(activity, bitmap, (float) target.xRatio(), (float) target.yRatio(), thumbnail);
    }

    LessonPreview(Activity activity, Bitmap bitmap, float xRatio, float yRatio, boolean thumbnail) {
        super(activity);
        this.bitmap = bitmap;
        this.xRatio = xRatio;
        this.yRatio = yRatio;
        markerRadius = Ui.dp(activity, thumbnail ? 4 : 10);
        setBackground(Ui.round(activity, Ui.WHITE, 8));
        setClipToOutline(true);
    }

    static String encode(Bitmap original) {
        int width = original.getWidth();
        int height = original.getHeight();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        for (int edge : PREVIEW_EDGES) {
            float scale = Math.min(1f, edge / (float) Math.max(width, height));
            Bitmap reduced = Bitmap.createScaledBitmap(original, Math.max(1, Math.round(width * scale)),
                    Math.max(1, Math.round(height * scale)), true);
            try {
                output.reset();
                if (reduced.compress(Bitmap.CompressFormat.JPEG, 80, output) && output.size() <= MAX_JPEG_BYTES) {
                    return Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP);
                }
            } finally {
                if (reduced != original) reduced.recycle();
            }
        }
        return "";
    }

    static Bitmap decode(String preview, boolean thumbnail) {
        if (preview.isEmpty() || preview.length() > ((MAX_JPEG_BYTES + 2) / 3) * 4) return null;
        try {
            byte[] bytes = Base64.decode(preview, Base64.NO_WRAP);
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = thumbnail ? 2 : 1;
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
        } catch (IllegalArgumentException malformed) {
            return null;
        }
    }

    @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        float scale = Math.min(width / (float) bitmap.getWidth(), height / (float) bitmap.getHeight());
        float horizontalInset = (width - bitmap.getWidth() * scale) / 2;
        float verticalInset = (height - bitmap.getHeight() * scale) / 2;
        imageBounds.set(horizontalInset, verticalInset, width - horizontalInset, height - verticalInset);
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawBitmap(bitmap, null, imageBounds, paint);
        if (xRatio < 0 || yRatio < 0) return;
        float x = imageBounds.left + imageBounds.width() * xRatio;
        float y = imageBounds.top + imageBounds.height() * yRatio;
        int saved = canvas.save();
        canvas.clipRect(imageBounds);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(markerRadius * .46f);
        paint.setColor(Color.WHITE);
        canvas.drawCircle(x, y, markerRadius, paint);
        paint.setStrokeWidth(markerRadius * .23f);
        paint.setColor(Ui.GREEN);
        canvas.drawCircle(x, y, markerRadius, paint);
        canvas.restoreToCount(saved);
    }
}
