package local.qingtap.adskip;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

// "Teach it once": the user picks a screenshot of the ad, taps the skip button on it, chooses which app
// that screenshot belongs to, and the point plus a small pixel fingerprint are stored for later use.
public final class TeachActivity extends Activity {
    private static final int REQUEST_PICK = 51;
    private static final int REQUEST_APP = 52;
    private static final String STATE_SCREENSHOT = "screenshot";
    private static final String STATE_PENDING = "pending";
    private static final String STATE_X = "point_x";
    private static final String STATE_Y = "point_y";

    private ImageView image;
    private TextView status;
    private Button selectApp;
    private Bitmap bitmap;
    private Uri screenshot;
    private String suggestedApp;
    private double pendingX;
    private double pendingY;
    private boolean pending;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Ui.PAPER);
        if (android.os.Build.VERSION.SDK_INT >= 35) Ui.insetRoot(scroll);
        LinearLayout body = Ui.column(this, 20);
        scroll.addView(body);
        setContentView(scroll);

        Ui.add(this, body, Ui.text(this, "从截图学习", 28, Ui.INK, true), 6);
        Ui.add(this, body, Ui.text(this,
                "选一张完整的广告截图，在图上点一下「跳过」或关闭按钮，再选所属应用。已保护的应用再次开屏时，清弹可在本机比对这个按钮的画面。Android 11 及以上通过无障碍截取单帧，旧版系统需要开启视觉兜底。",
                14, Ui.MUTED, false), 4);

        status = Ui.text(this, "请选择一张广告截图", 13, Ui.MUTED, false);
        Ui.add(this, body, status, 6);
        selectApp = Ui.button(this, "确认位置并保存", true, view -> chooseApp());
        selectApp.setVisibility(View.GONE);
        Ui.add(this, body, selectApp, 8);

        image = new ImageView(this);
        image.setAdjustViewBounds(true);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setOnTouchListener((view, event) -> {
            // The surrounding ScrollView would otherwise steal the gesture before ACTION_UP arrives.
            if (view.getParent() != null) {
                view.getParent().requestDisallowInterceptTouchEvent(true);
            }
            if (event.getAction() == MotionEvent.ACTION_UP) {
                handleTap(event);
                if (view.getParent() != null) view.getParent().requestDisallowInterceptTouchEvent(false);
            }
            return true;
        });
        Ui.add(this, body, image, 12);

        Ui.add(this, body, Ui.button(this, "重新选择截图", false, view -> pick()), 12);
        Ui.add(this, body, Ui.button(this, "返回", false, view -> finish()), 8);

        Uri initial = state == null ? getIntent().getData() : state.getParcelable(STATE_SCREENSHOT);
        suggestedApp = state == null ? getIntent().getStringExtra(AdImage.SOURCE_APP) : state.getString(AdImage.SOURCE_APP);
        if (initial != null) load(initial);
        else if (state == null) {
            pick();
            return;
        }
        if (bitmap == null) return;
        if (state != null && state.getBoolean(STATE_PENDING)) {
            pendingX = state.getDouble(STATE_X);
            pendingY = state.getDouble(STATE_Y);
            showPendingPoint();
        } else if (state == null) {
            pendingX = getIntent().getFloatExtra(AdImage.TARGET_X, -1);
            pendingY = getIntent().getFloatExtra(AdImage.TARGET_Y, -1);
            if (pendingX >= 0 && pendingX < 1 && pendingY >= 0 && pendingY < 1) showPendingPoint();
        }
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putParcelable(STATE_SCREENSHOT, screenshot);
        state.putString(AdImage.SOURCE_APP, suggestedApp);
        state.putBoolean(STATE_PENDING, pending);
        state.putDouble(STATE_X, pendingX);
        state.putDouble(STATE_Y, pendingY);
    }

    private void pick() {
        try {
            startActivityForResult(AdImage.picker(), REQUEST_PICK);
        } catch (Exception missing) {
            Toast.makeText(this, "没有可用的图片选择器", Toast.LENGTH_SHORT).show();
        }
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == REQUEST_APP) {
            if (result == RESULT_OK && data != null) {
                save(data.getStringExtra(AppPickerActivity.EXTRA_RESULT), false);
            }
            return;
        }
        if (request != REQUEST_PICK) return;
        if (result != RESULT_OK || data == null || data.getData() == null) {
            Toast.makeText(this, "没有选择截图", Toast.LENGTH_SHORT).show();
            return;
        }
        if (load(data.getData())) suggestedApp = null;
    }

    private boolean load(Uri uri) {
        try {
            Bitmap decoded = AdImage.read(this, uri);
            if (decoded == null) {
                Toast.makeText(this, "这张图读不出来", Toast.LENGTH_SHORT).show();
                return false;
            }
            bitmap = decoded;
            screenshot = uri;
            image.setImageBitmap(bitmap);
            pending = false;
            selectApp.setVisibility(View.GONE);
            status.setText("在图上点一下那个「跳过」按钮（图片 " + bitmap.getWidth() + "×"
                    + bitmap.getHeight() + "）");
            return true;
        } catch (Exception failed) {
            Toast.makeText(this, "读取截图失败：" + failed.getClass().getSimpleName(), Toast.LENGTH_SHORT).show();
            return false;
        }
    }

    private void handleTap(MotionEvent event) {
        if (bitmap == null) return;
        // FIT_CENTER draws the bitmap into a centred rect inside the view; getImageMatrix() does not
        // carry that scale, so the displayed rect is computed here instead.
        float viewWidth = image.getWidth();
        float viewHeight = image.getHeight();
        if (viewWidth <= 0 || viewHeight <= 0) return;
        float scale = Math.min(viewWidth / bitmap.getWidth(), viewHeight / bitmap.getHeight());
        float left = (viewWidth - bitmap.getWidth() * scale) / 2f;
        float top = (viewHeight - bitmap.getHeight() * scale) / 2f;
        int x = (int) ((event.getX() - left) / scale);
        int y = (int) ((event.getY() - top) / scale);
        if (x < 0 || y < 0 || x >= bitmap.getWidth() || y >= bitmap.getHeight()) {
            Toast.makeText(this, "点到图片外面了，再点一次", Toast.LENGTH_SHORT).show();
            return;
        }
        pendingX = x / (double) bitmap.getWidth();
        pendingY = y / (double) bitmap.getHeight();
        showPendingPoint();
        chooseApp();
    }

    private void showPendingPoint() {
        pending = true;
        selectApp.setVisibility(View.VISIBLE);
        status.setText("关闭位置 " + Math.round(pendingX * 100) + "% / " + Math.round(pendingY * 100)
                + "%。点图可调整，确认后选择所属 App。");
    }

    private void chooseApp() {
        if (suggestedApp != null && !suggestedApp.isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle("保存到「" + appLabel(suggestedApp) + "」？")
                    .setPositiveButton("保存", (dialog, which) -> save(suggestedApp, false))
                    .setNegativeButton("选择其他应用", (dialog, which) -> pickApp())
                    .setNeutralButton("取消", null)
                    .show();
            return;
        }
        if (getIntent().getData() != null) {
            pickApp();
            return;
        }
        String recent = AppPrefs.open(this).getString(AppPrefs.RECENT_AD, "");
        if (recent != null && !recent.isEmpty() && !recent.equals(getPackageName())) {
            new AlertDialog.Builder(this)
                    .setTitle("用这个 App 吗？")
                    .setMessage("最近检测到广告的是「" + appLabel(recent) + "」")
                    .setPositiveButton("用它", (dialog, which) -> save(recent, true))
                    .setNegativeButton("选择其他应用", (dialog, which) -> pickApp())
                    .setNeutralButton("取消", null)
                    .show();
            return;
        }
        pickApp();
    }

    private void pickApp() {
        startActivityForResult(new Intent(this, AppPickerActivity.class), REQUEST_APP);
    }

    private void save(String pkg, boolean confirmRecentAd) {
        if (pkg == null || pkg.isEmpty()) {
            Toast.makeText(this, "请选择截图所属的应用", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!pending || bitmap == null) {
            Toast.makeText(this, "先点一下截图里的位置", Toast.LENGTH_SHORT).show();
            return;
        }
        ScreenshotFrame frame = ScreenshotFrame.read(bitmap, VisionFinder.LESSON_FRAME_WIDTH);
        ScreenshotLesson lesson = ScreenshotLesson.create(bitmap, frame, pendingX, pendingY);
        if (lesson == null && bitmap.getWidth() > VisionFinder.LESSON_FRAME_WIDTH) {
            lesson = ScreenshotLesson.create(bitmap, ScreenshotFrame.read(bitmap, 540), pendingX, pendingY);
        }
        if (lesson == null || !lesson.save(this, pkg)) {
            Toast.makeText(this, "这处画面特征不足，请点按钮中央或换一张清晰截图", Toast.LENGTH_LONG).show();
            return;
        }
        if (confirmRecentAd) confirmApp(pkg);
        pending = false;
        selectApp.setVisibility(View.GONE);
        String saved = "已保存「" + appLabel(pkg) + "」的关闭位置（"
                + Math.round(pendingX * 100) + "% / " + Math.round(pendingY * 100) + "%）";
        if (!AppPrefs.selected(AppPrefs.open(this)).contains(pkg)) {
            saved += "\n请在首页的保护名单中勾选该应用。";
        }
        Toast.makeText(this, saved, Toast.LENGTH_LONG).show();
        setResult(RESULT_OK, new Intent().putExtra(AppPickerActivity.EXTRA_RESULT, pkg));
        finish();
    }

    private void confirmApp(String pkg) {
        new AdFeedback(new AdFeedback.Store() {
            @Override public String read() {
                return AppPrefs.open(TeachActivity.this).getString(AppPrefs.FEEDBACK, "");
            }

            @Override public void write(String value) {
                AppPrefs.open(TeachActivity.this).edit().putString(AppPrefs.FEEDBACK, value).apply();
            }
        }).allow(pkg + "/*");
    }

    private String appLabel(String packageName) {
        try {
            return getPackageManager().getApplicationLabel(
                    getPackageManager().getApplicationInfo(packageName, 0)).toString();
        } catch (Exception missing) {
            return packageName;
        }
    }
}
