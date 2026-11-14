package local.qingtap.adskip;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import java.io.IOException;
import java.io.InputStream;

final class AdImage {
    static final String SOURCE_APP = "source_app";
    static final String TARGET_X = "suggested_target_x";
    static final String TARGET_Y = "suggested_target_y";

    private AdImage() {}

    static Intent picker() {
        return new Intent(Intent.ACTION_GET_CONTENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE);
    }

    static Intent batchPicker() {
        return new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE)
                .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
    }

    static Intent draft(Context context, Class<?> activity, Uri screenshot, String sourceApp) {
        return new Intent(context, activity).setData(screenshot)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION).putExtra(SOURCE_APP, sourceApp);
    }

    static Bitmap read(Context context, Uri screenshot) throws IOException {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream stream = context.getContentResolver().openInputStream(screenshot)) {
            BitmapFactory.decodeStream(stream, null, bounds);
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = 1;
        while ((long) (bounds.outWidth / options.inSampleSize) * (bounds.outHeight / options.inSampleSize) > 2000000
                || Math.max(bounds.outWidth, bounds.outHeight) / options.inSampleSize > 2400) {
            options.inSampleSize *= 2;
        }
        try (InputStream stream = context.getContentResolver().openInputStream(screenshot)) {
            return BitmapFactory.decodeStream(stream, null, options);
        }
    }
}
