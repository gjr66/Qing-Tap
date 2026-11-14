package local.qingtap.adskip;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ForkJoinPool;

@SuppressWarnings("deprecation")
public final class DemoActivity extends Activity {
    private static final int REQUEST_IMAGES = 1;
    private static final int REQUEST_LEARNING = 3;
    private static DemoActivity activeTest;

    private AdTestBatch batch;
    private Uri waitingToTeach;
    private final Map<AdTestBatch.Item, LinearLayout> cards = new HashMap<>();
    private LinearLayout results;
    private TextView summary;
    private TextView status;
    private ProgressBar progress;
    private Button selectImages;
    private Button quickButton;
    private Button detailButton;
    private Button clearButton;
    private boolean showResolved;
    private boolean running;
    private volatile boolean destroyed;
    private int completed;
    private int total;
    private int quickMatches;
    private int detailMatches;
    private int autoLearned;
    private int needsManual;
    private int unreadable;
    private String operation = "";
    private String message = "";
    private ImageView trialImage;
    private TextView trialStatus;

    static boolean ownsTest(View view) {
        return activeTest != null && activeTest.trialImage == view;
    }

    static boolean isTesting() {
        return activeTest != null;
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        batch = new AdTestBatch(this);
        if (state != null) {
            waitingToTeach = state.getParcelable("teaching_image");
            showResolved = state.getBoolean("show_resolved");
        }
        showBatch();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putParcelable("teaching_image", waitingToTeach);
        state.putBoolean("show_resolved", showResolved);
        batch.save();
    }

    @Override protected void onPause() {
        if (trialImage != null) {
            endTrial();
            message = "点击测试已暂停";
            showBatch();
        }
        super.onPause();
    }

    @Override protected void onDestroy() {
        destroyed = true;
        if (running) batch.save();
        endTrial();
        super.onDestroy();
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == REQUEST_LEARNING) {
            if (result == RESULT_OK && data != null && waitingToTeach != null) {
                String pkg = data.getStringExtra(AppPickerActivity.EXTRA_RESULT);
                if (pkg != null) {
                    for (AdTestBatch.Item item : batch.items) {
                        if (waitingToTeach.equals(item.image)) item.savedApp = pkg;
                    }
                    batch.save();
                    message = "已保存到「" + AppCatalog.label(this, pkg) + "」的学习记录";
                    rebuildRows();
                    updateSummary();
                }
            }
            waitingToTeach = null;
            return;
        }
        if (request != REQUEST_IMAGES || result != RESULT_OK || data == null) return;
        Set<Uri> selected = new LinkedHashSet<>();
        ClipData clip = data.getClipData();
        if (clip != null) {
            for (int i = 0; i < clip.getItemCount(); i++) {
                Uri uri = clip.getItemAt(i).getUri();
                if (uri != null) selected.add(uri);
            }
        }
        if (data.getData() != null) selected.add(data.getData());
        Map<Uri, AdTestBatch.Item> existing = new HashMap<>();
        for (AdTestBatch.Item item : batch.items) existing.put(item.image, item);
        List<AdTestBatch.Item> queue = new ArrayList<>();
        for (Uri uri : selected) {
            if ((data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0) {
                try {
                    getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                } catch (SecurityException unsupported) {
                    // Some providers grant access only for the current task.
                }
            }
            AdTestBatch.Item item = existing.get(uri);
            if (item == null) {
                item = new AdTestBatch.Item(uri);
                batch.items.add(item);
            }
            queue.add(item);
        }
        batch.save();
        rebuildRows();
        if (queue.isEmpty()) updateSummary();
        else startScan(queue, false);
    }

    private void showBatch() {
        LinearLayout page = Ui.column(this, 14);
        page.setBackgroundColor(Ui.PAPER);
        if (android.os.Build.VERSION.SDK_INT >= 35) Ui.insetRoot(page);
        setContentView(page);
        Ui.add(this, page, Ui.text(this, "批量识别与学习", 24, Ui.INK, true), 0);
        summary = Ui.text(this, "", 13, Ui.MUTED, false);
        Ui.add(this, page, summary, 4);
        selectImages = Ui.button(this, "选择多张广告截图", true, view -> {
            try {
                startActivityForResult(AdImage.batchPicker(), REQUEST_IMAGES);
            } catch (RuntimeException unavailable) {
                Toast.makeText(this, "无法打开图片选择器", Toast.LENGTH_SHORT).show();
            }
        });
        Ui.add(this, page, selectImages, 8);

        LinearLayout actions = new LinearLayout(this);
        Ui.add(this, page, actions, 6);
        quickButton = Ui.button(this, "一键识别并学习", false, view -> startScan(pending(), false));
        detailButton = Ui.button(this, "精细复查", false, view -> startScan(pending(), true));
        actions.addView(quickButton, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        LinearLayout.LayoutParams detailParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        detailParams.leftMargin = Ui.dp(this, 8);
        actions.addView(detailButton, detailParams);

        Switch filter = new Switch(this);
        filter.setText("显示已处理记录");
        filter.setTextColor(Ui.INK);
        filter.setChecked(showResolved);
        filter.setOnCheckedChangeListener((button, checked) -> {
            showResolved = checked;
            rebuildRows();
        });
        Ui.add(this, page, filter, 4);
        clearButton = Ui.button(this, "一键清理已处理", false, view -> confirmCleanup());
        Ui.add(this, page, clearButton, 4);
        status = Ui.text(this, "", 13, Ui.GREEN, true);
        Ui.add(this, page, status, 2);
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        Ui.add(this, page, progress, 2);

        ScrollView scroll = new ScrollView(this);
        page.addView(scroll, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
        results = Ui.column(this, 0);
        scroll.addView(results);
        rebuildRows();
        updateSummary();
    }

    private void confirmCleanup() {
        int count = batch.items.size() - pending().size();
        if (running || count == 0) return;
        new AlertDialog.Builder(this).setTitle("清理已处理记录")
                .setMessage("清理 " + count + " 条批量记录。学习记录、待处理项和相册原图会保留。")
                .setNegativeButton("取消", null)
                .setPositiveButton("清理", (dialog, which) -> {
                    batch.clearProcessed();
                    message = "已清理 " + count + " 条已处理记录";
                    rebuildRows();
                    updateSummary();
                }).show();
    }

    private List<AdTestBatch.Item> pending() {
        List<AdTestBatch.Item> queue = new ArrayList<>();
        for (AdTestBatch.Item item : batch.items) {
            if (!item.resolved()) queue.add(item);
        }
        return queue;
    }

    private void startScan(List<AdTestBatch.Item> queue, boolean detailed) {
        if (running || queue.isEmpty()) return;
        List<TaughtTargets.Target> lessons = AppPrefs.taught(AppPrefs.open(this)).all();
        running = true;
        quickMatches = detailMatches = autoLearned = needsManual = unreadable = 0;
        message = "";
        beginPhase(detailed, queue.size());
        ForkJoinPool.commonPool().execute(() -> {
            List<AdTestBatch.Item> review = detailed ? queue : new ArrayList<>();
            if (!detailed) {
                for (AdTestBatch.Item item : queue) {
                    if (destroyed) return;
                    AdTestBatch.Scan scan = AdTestBatch.scan(getApplicationContext(), item.image, lessons,
                            null, item.preview.isEmpty());
                    if (scan.result.status == AdTestBatch.Status.REVIEW) review.add(item);
                    runOnUiThread(() -> applyScan(item, scan, false));
                }
            }
            if (!review.isEmpty() && !destroyed) {
                if (!detailed) runOnUiThread(() -> beginPhase(true, review.size()));
                try (AdTestBatch.DetailedReader reader = new AdTestBatch.DetailedReader(getApplicationContext())) {
                    for (AdTestBatch.Item item : review) {
                        if (destroyed) return;
                        AdTestBatch.Scan scan = AdTestBatch.scan(getApplicationContext(), item.image, lessons,
                                reader, detailed && item.preview.isEmpty());
                        runOnUiThread(() -> applyScan(item, scan, true));
                    }
                }
            }
            runOnUiThread(this::finishBatch);
        });
    }

    private void beginPhase(boolean detailed, int size) {
        if (destroyed) return;
        operation = detailed ? "精细复查与学习" : "快速识别";
        completed = 0;
        total = size;
        updateSummary();
    }

    private void applyScan(AdTestBatch.Item item, AdTestBatch.Scan scan, boolean detailed) {
        if (destroyed) return;
        if (batch.apply(this, item, scan, detailed)) autoLearned++;
        if (scan.result.status == AdTestBatch.Status.ERROR) unreadable++;
        if (detailed) {
            if (scan.result.status == AdTestBatch.Status.MATCH) detailMatches++;
            else if (scan.result.status == AdTestBatch.Status.REVIEW) needsManual++;
        } else if (scan.result.status == AdTestBatch.Status.MATCH) quickMatches++;
        completed++;
        LinearLayout card = cards.get(item);
        if (card != null) {
            if (!showResolved && item.resolved()) {
                results.removeView(card);
                cards.remove(item);
            } else renderRow(card, item, batch.items.indexOf(item) + 1);
        }
        updateSummary();
    }

    private void finishBatch() {
        if (destroyed) return;
        running = false;
        batch.save();
        message = "快速匹配 " + quickMatches + " · 复查匹配 " + detailMatches + " · 自动学习 " + autoLearned
                + " · 待人工处理 " + needsManual + (unreadable == 0 ? "" : " · 读取失败 " + unreadable);
        rebuildRows();
        updateSummary();
        if (!isFinishing() && hasWindowFocus()) {
            new AlertDialog.Builder(this).setTitle("一键识别与学习完成")
                    .setMessage(message).setPositiveButton("完成", null).show();
        } else if (!isFinishing()) {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        }
    }

    private void updateSummary() {
        int pending = 0;
        for (AdTestBatch.Item item : batch.items) {
            if (!item.resolved()) pending++;
        }
        int processed = batch.items.size() - pending;
        summary.setText("待处理 " + pending + " 张 · 已保留 " + processed + " 条已处理记录");
        status.setText(running ? operation + " " + completed + " / " + total : message);
        progress.setVisibility(running ? View.VISIBLE : View.GONE);
        progress.setMax(Math.max(1, total));
        progress.setProgress(completed);
        selectImages.setEnabled(!running);
        quickButton.setEnabled(!running && pending > 0);
        detailButton.setEnabled(!running && pending > 0);
        clearButton.setEnabled(!running && processed > 0);
    }

    private void rebuildRows() {
        results.removeAllViews();
        cards.clear();
        for (AdTestBatch.Item item : pending()) addRow(item);
        if (showResolved) {
            for (AdTestBatch.Item item : batch.items) {
                if (item.resolved()) addRow(item);
            }
        }
    }

    private void addRow(AdTestBatch.Item item) {
        LinearLayout card = Ui.column(this, 12);
        card.setBackground(Ui.round(this, Ui.WHITE, 14));
        Ui.add(this, results, card, 8);
        cards.put(item, card);
        renderRow(card, item, batch.items.indexOf(item) + 1);
    }

    private void renderRow(LinearLayout card, AdTestBatch.Item item, int position) {
        card.removeAllViews();
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        Ui.add(this, card, top, 0);
        AdTestBatch.Result best = item.best();
        AdTestBatch.Result marker = item.marker();
        if (item.thumbnail == null && !item.preview.isEmpty()) item.thumbnail = LessonPreview.decode(item.preview, false);
        if (item.thumbnail != null) {
            LessonPreview thumbnail = new LessonPreview(this, item.thumbnail,
                    marker == null ? -1 : marker.x, marker == null ? -1 : marker.y, true);
            thumbnail.setContentDescription("打开截图 " + position + " 大图");
            thumbnail.setOnClickListener(view -> showPreview(item));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(Ui.dp(this, 62), Ui.dp(this, 96));
            params.rightMargin = Ui.dp(this, 10);
            top.addView(thumbnail, params);
        }
        LinearLayout text = Ui.column(this, 0);
        top.addView(text, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        Ui.add(this, text, Ui.text(this, "截图 " + position, 15, Ui.INK, true), 0);
        Ui.add(this, text, Ui.text(this, describe("快速", item.quick), 13, Ui.MUTED, false), 4);
        if (item.detail != null) {
            Ui.add(this, text, Ui.text(this, describe("复查", item.detail), 13, Ui.GREEN, true), 4);
        }
        if (!item.savedApp.isEmpty()) {
            Ui.add(this, text, Ui.text(this, "已保存到 " + AppCatalog.label(this, item.savedApp), 13, Ui.GREEN, true), 4);
        }
        if (item.detail != null && item.detail.status == AdTestBatch.Status.REVIEW) {
            String hint = item.detail.pkg.isEmpty() ? "需要选择所属 App" : "已找到所属 App，需核对关闭位置";
            Ui.add(this, text, Ui.text(this, hint, 13, Ui.MUTED, false), 4);
        }
        if (item.quick != null || item.detail != null) {
            LinearLayout actions = new LinearLayout(this);
            Ui.add(this, card, actions, 5);
            if (!item.resolved() && (item.detail == null
                    ? item.quick.status != AdTestBatch.Status.ERROR : item.detail.status != AdTestBatch.Status.ERROR)) {
                String action = item.detail != null && item.detail.buttonRead && item.detail.pkg.isEmpty()
                        ? "选择所属 App" : "核对并学习";
                actions.addView(Ui.button(this, action, false, view -> openLearning(item)),
                        new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            }
            if (best != null && item.thumbnail != null) {
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
                params.leftMargin = Ui.dp(this, 8);
                actions.addView(Ui.button(this, "验证点击", false, view -> startTrial(item, best)), params);
            }
        }
    }

    private String describe(String stage, AdTestBatch.Result result) {
        if (result == null) return "等待快速测试";
        String duration = (result.evidence.isEmpty() ? "" : " · " + result.evidence) + " · " + result.millis + " ms";
        if (result.status == AdTestBatch.Status.ERROR) return stage + " · 无法读取原图";
        if (result.status == AdTestBatch.Status.MATCH) {
            return stage + " · 匹配 " + AppCatalog.label(this, result.pkg) + duration;
        }
        if (result.candidates.isEmpty()) {
            return stage + (result.x >= 0 ? " · 标出疑似关闭位置" : " · 未匹配") + duration;
        }
        List<String> labels = new ArrayList<>();
        for (int i = 0; i < Math.min(3, result.candidates.size()); i++) {
            labels.add(AppCatalog.label(this, result.candidates.get(i)));
        }
        if (!result.pkg.isEmpty()) return stage + " · 发现 " + AppCatalog.label(this, result.pkg) + duration;
        String names = String.join("、", labels);
        if (result.candidates.size() > labels.size()) names += " 等";
        return stage + " · 多个候选 " + names + duration;
    }

    private void openLearning(AdTestBatch.Item item) {
        if (running) return;
        waitingToTeach = item.image;
        AdTestBatch.Result detail = item.detail;
        Intent draft = AdImage.draft(this, TeachActivity.class, item.image, detail == null ? null : detail.pkg);
        if (detail != null && detail.buttonRead) {
            draft.putExtra(AdImage.TARGET_X, detail.x).putExtra(AdImage.TARGET_Y, detail.y);
        }
        startActivityForResult(draft, REQUEST_LEARNING);
    }

    private void showPreview(AdTestBatch.Item item) {
        Bitmap bitmap = item.thumbnail;
        try {
            Bitmap original = AdImage.read(this, item.image);
            if (original != null) bitmap = original;
        } catch (Exception unavailable) {
            // The cached thumbnail still shows the stored result.
        }
        AdTestBatch.Result marker = item.marker();
        LessonPreview image = new LessonPreview(this, bitmap, marker == null ? -1 : marker.x,
                marker == null ? -1 : marker.y, false);
        LinearLayout body = Ui.column(this, 12);
        body.addView(image, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                Math.min(Ui.dp(this, 520), (int) (getResources().getDisplayMetrics().heightPixels * .58f))));
        new AlertDialog.Builder(this)
                .setTitle(marker != null && marker.status == AdTestBatch.Status.REVIEW
                        ? "截图 · 疑似关闭位置" : "截图")
                .setView(body)
                .setPositiveButton("关闭", null)
                .show();
    }

    private void startTrial(AdTestBatch.Item item, AdTestBatch.Result match) {
        if (running) return;
        Bitmap bitmap = item.thumbnail;
        LinearLayout body = Ui.column(this, 14);
        body.setBackgroundColor(Ui.PAPER);
        if (android.os.Build.VERSION.SDK_INT >= 35) Ui.insetRoot(body);
        trialStatus = Ui.text(this, "正在验证测试点击", 15, Ui.INK, true);
        Ui.add(this, body, trialStatus, 4);
        ImageView view = new ImageView(this);
        view.setScaleType(ImageView.ScaleType.FIT_CENTER);
        view.setImageBitmap(bitmap);
        view.setContentDescription("点击测试截图");
        view.setOnTouchListener((image, event) -> true);
        body.addView(view, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
        Ui.add(this, body, Ui.button(this, "返回批量结果", false, button -> {
            endTrial();
            showBatch();
        }), 6);
        setContentView(body);
        trialImage = view;
        activeTest = this;
        AdSkipService.resetImportedTest();

        int width = bitmap.getWidth(), height = bitmap.getHeight();
        int[] pixels = new int[width * height];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
        SceneFingerprint scene = SceneFingerprint.of(pixels, width, height);
        view.post(() -> {
            if (!ownsTest(view)) return;
            AdSkipService.clickImportedTest(view, match.pkg, match.x, match.y, scene, outcome -> {
                if (!ownsTest(view)) return;
                if (outcome == AdSkipService.TestClick.PAUSED) {
                    trialStatus.setText("防误触保护已暂停点击");
                    return;
                }
                endTrial();
                if (outcome == AdSkipService.TestClick.COMPLETED) message = "已完成测试点击";
                else if (outcome == AdSkipService.TestClick.DISABLED) {
                    message = "勾选清弹并开启保护总开关和无障碍后，可以验证点击";
                } else message = "此次测试点击未完成";
                showBatch();
            });
        });
    }

    private void endTrial() {
        trialImage = null;
        trialStatus = null;
        if (activeTest == this) {
            activeTest = null;
            AdSkipService.resetImportedTest();
        }
    }

    @Override public void onBackPressed() {
        if (trialImage == null) super.onBackPressed();
        else {
            endTrial();
            showBatch();
        }
    }
}
