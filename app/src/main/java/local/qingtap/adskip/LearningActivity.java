package local.qingtap.adskip;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

// Learning records grouped by app, collapsible, with a search box. Tap an app to unfold its records;
// tap again to fold it back.
public final class LearningActivity extends Activity {
    private static final int REQUEST_TEACH = 61;
    private static final int REQUEST_MOVE = 62;
    private static final String STATE_DRAFT = "pending_screenshot";
    private static final class Group {
        final String pkg;
        final List<AdMemory.Target> memory = new ArrayList<>();
        final List<AdHints.Hint> hints = new ArrayList<>();
        final List<TaughtTargets.Target> taught = new ArrayList<>();
        final List<String> allowed = new ArrayList<>();
        final List<String> denied = new ArrayList<>();
        final List<String> protectedPages = new ArrayList<>();

        Group(String pkg) {
            this.pkg = pkg;
        }

        int total() {
            return memory.size() + hints.size() + taught.size() + allowed.size() + denied.size() + protectedPages.size();
        }
    }

    private LinearLayout body;
    private final Set<String> expanded = new HashSet<>();
    private String query = "";
    private Uri pendingScreenshot;
    private String sourceApp;
    private String movingLesson;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        pendingScreenshot = state == null ? getIntent().getData() : state.getParcelable(STATE_DRAFT);
        sourceApp = getIntent().getStringExtra(AdImage.SOURCE_APP);
        if (state != null) {
            movingLesson = state.getString("moving_lesson");
            ArrayList<String> previous = state.getStringArrayList("expanded_apps");
            if (previous != null) expanded.addAll(previous);
        }
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.PAPER);
        if (android.os.Build.VERSION.SDK_INT >= 35) Ui.insetRoot(root);
        setContentView(root);

        LinearLayout header = Ui.column(this, 16);
        root.addView(header);
        Ui.add(this, header, Ui.text(this, "学习记录", 24, Ui.INK, true), 0);
        Ui.add(this, header, Ui.text(this, "点应用展开记录，点缩略图查看关闭位置", 13, Ui.MUTED, false), 4);

        EditText search = new EditText(this);
        search.setHint("搜索 App 名称或包名");
        search.setTextSize(15);
        search.setSingleLine(true);
        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        int pad = Ui.dp(this, 16);
        searchParams.setMargins(pad, Ui.dp(this, 6), pad, Ui.dp(this, 6));
        root.addView(search, searchParams);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence text, int start, int count, int after) {
            }

            @Override public void onTextChanged(CharSequence text, int start, int before, int count) {
                query = text == null ? "" : text.toString().trim().toLowerCase(Locale.ROOT);
                render();
            }

            @Override public void afterTextChanged(Editable editable) {
            }
        });

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        root.addView(scroll, scrollParams);
        body = Ui.column(this, 16);
        scroll.addView(body);
        render();
    }

    @Override protected void onResume() {
        super.onResume();
        if (body != null) render();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putParcelable(STATE_DRAFT, pendingScreenshot);
        state.putString("moving_lesson", movingLesson);
        state.putStringArrayList("expanded_apps", new ArrayList<>(expanded));
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == REQUEST_MOVE) {
            String key = movingLesson;
            movingLesson = null;
            if (result != RESULT_OK || data == null || key == null) return;
            String pkg = data.getStringExtra(AppPickerActivity.EXTRA_RESULT);
            if (pkg != null && !pkg.isEmpty() && AppPrefs.taught(AppPrefs.open(this)).move(key, pkg)) {
                AdSkipService.reloadTaught();
                expanded.add(pkg);
                render();
            }
            return;
        }
        if (request != REQUEST_TEACH || result != RESULT_OK || data == null) return;
        pendingScreenshot = null;
        String pkg = data.getStringExtra(AppPickerActivity.EXTRA_RESULT);
        if (pkg != null) expanded.add(pkg);
        setResult(RESULT_OK, data);
        render();
    }

    private void render() {
        body.removeAllViews();
        if (pendingScreenshot != null) {
            LinearLayout draft = Ui.column(this, 16);
            draft.setBackground(Ui.round(this, Ui.WHITE, 16));
            Ui.add(this, body, draft, 0);
            Ui.add(this, draft, Ui.text(this, "未识别的广告截图", 17, Ui.INK, true), 0);
            if (sourceApp != null) {
                Ui.add(this, draft, Ui.text(this, AppCatalog.label(this, sourceApp), 13, Ui.MUTED, false), 4);
            }
            Ui.add(this, draft, Ui.button(this, "标记截图上的关闭位置", true, view ->
                    startActivityForResult(AdImage.draft(this, TeachActivity.class, pendingScreenshot, sourceApp),
                            REQUEST_TEACH)), 8);
        }
        AdMemory memory = new AdMemory(memoryStore());
        AdHints hints = new AdHints(hintStore());
        AdFeedback feedback = new AdFeedback(feedbackStore());
        TaughtTargets targets = AppPrefs.taught(AppPrefs.open(this));

        Map<String, Group> groups = new LinkedHashMap<>();
        for (AdMemory.Target target : memory.all()) group(groups, packageOf(target.signature)).memory.add(target);
        for (AdHints.Hint hint : hints.all()) group(groups, packageOf(hint.signature)).hints.add(hint);
        for (TaughtTargets.Target target : targets.all()) group(groups, target.pkg).taught.add(target);
        for (String signature : feedback.of(AdFeedback.ALLOW)) {
            group(groups, packageOf(signature)).allowed.add(signature);
        }
        for (String signature : feedback.of(AdFeedback.DENY)) {
            group(groups, packageOf(signature)).denied.add(signature);
        }
        for (String record : AppPrefs.deniedPages(AppPrefs.open(this))) {
            RepeatGuard.Page saved = RepeatGuard.Page.decode(record);
            if (saved != null) group(groups, saved.pkg).protectedPages.add(record);
        }

        List<Group> sorted = new ArrayList<>();
        for (Group group : groups.values()) {
            String label = AppCatalog.label(this, group.pkg);
            if (query.isEmpty() || label.toLowerCase(Locale.ROOT).contains(query)
                    || group.pkg.toLowerCase(Locale.ROOT).contains(query)) {
                sorted.add(group);
            }
        }
        sorted.sort((left, right) -> AppCatalog.label(this, left.pkg)
                .compareToIgnoreCase(AppCatalog.label(this, right.pkg)));

        if (sorted.isEmpty()) {
            Ui.add(this, body, Ui.text(this, query.isEmpty() ? "还没有任何学习记录。"
                    : "没有匹配「" + query + "」的 App。", 14, Ui.MUTED, false), 12);
        }
        for (Group group : sorted) renderGroup(group);

        Ui.add(this, body, Ui.button(this, "清空全部学习记录", false, view -> confirmClear()), 18);
        Ui.add(this, body, Ui.button(this, "返回", false, view -> finish()), 8);
    }

    private void renderGroup(Group group) {
        boolean open = expanded.contains(group.pkg);
        LinearLayout card = Ui.column(this, 16);
        card.setBackground(Ui.round(this, Ui.WHITE, 16));
        Ui.add(this, body, card, 12);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        ImageView icon = new ImageView(this);
        icon.setImageDrawable(AppCatalog.icon(this, group.pkg));
        int size = Ui.dp(this, 30);
        header.addView(icon, new LinearLayout.LayoutParams(size, size));

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        textParams.leftMargin = Ui.dp(this, 10);
        header.addView(texts, textParams);
        texts.addView(Ui.text(this, AppCatalog.label(this, group.pkg), 17, Ui.INK, true));
        texts.addView(Ui.text(this, group.pkg, 11, Ui.MUTED, false));

        TextView arrow = Ui.text(this, open ? "▾" : "▸", 16, Ui.GREEN, true);
        header.addView(arrow);
        header.setOnClickListener(view -> {
            if (open) expanded.remove(group.pkg);
            else expanded.add(group.pkg);
            render();
        });
        Ui.add(this, card, header, 0);

        Ui.add(this, card, Ui.text(this, "关过 " + group.memory.size()
                + " · 嫌疑 " + group.hints.size()
                + " · 教学 " + group.taught.size()
                + " · 判断 " + (group.allowed.size() + group.denied.size())
                + (group.protectedPages.isEmpty() ? "" : " · 不操作 " + group.protectedPages.size())
                + "　（点这一行" + (open ? "收起" : "展开") + "）", 12, Ui.MUTED, false), 4);

        if (!open) return;

        for (AdMemory.Target target : group.memory) {
            row(card, "关过", shortName(activityOf(target.signature)),
                    "位置 " + percent(target.xRatio()) + " / " + percent(target.yRatio())
                            + "　命中 " + target.hits + "　失败 " + target.misses,
                    target.label, () -> {
                        AdSkipService.forgetAppActivity(packageOf(target.signature),
                                activityOf(target.signature));
                        render();
                    });
        }
        for (AdHints.Hint hint : group.hints) {
            row(card, "嫌疑", shortName(activityOf(hint.signature)),
                    "位置 " + percent(hint.xRatio()) + " / " + percent(hint.yRatio())
                            + "　命中 " + hint.hits + "　失败 " + hint.misses
                            + "　" + (hint.strong ? "强" : "弱"),
                    hint.label, () -> {
                        AdSkipService.forgetAppActivity(packageOf(hint.signature),
                                activityOf(hint.signature));
                        render();
                    });
        }
        for (TaughtTargets.Target target : group.taught) {
            taughtRow(card, target);
        }
        for (String signature : group.allowed) {
            row(card, "判断", shortName(activityOf(signature)), "你确认是广告", "", () -> {
                AdSkipService.forgetAppActivity(packageOf(signature), activityOf(signature));
                render();
            });
        }
        for (String signature : group.denied) {
            row(card, "判断", shortName(activityOf(signature)), "你判定不是广告", "", () -> {
                AdSkipService.forgetAppActivity(packageOf(signature), activityOf(signature));
                render();
            });
        }
        for (String record : group.protectedPages) {
            RepeatGuard.Page saved = RepeatGuard.Page.decode(record);
            String page = saved.layout.startsWith("test:") ? "自选广告测试"
                    : "页面 " + saved.layout.substring(0, Math.min(6, saved.layout.length()));
            row(card, "防误触", page, "你选择了「不是广告」，此页面不自动点击。", "", () -> {
                AppPrefs.setPageDenied(AppPrefs.open(this), record, false);
                render();
            });
        }
    }

    private void taughtRow(LinearLayout card, TaughtTargets.Target target) {
        LinearLayout row = Ui.column(this, 12);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(Ui.round(this, Ui.TINT, 12));
        Ui.add(this, card, row, 8);

        Bitmap thumbnail = LessonPreview.decode(target.preview, true);
        if (thumbnail != null) {
            LessonPreview image = new LessonPreview(this, thumbnail, target, true);
            image.setContentDescription("查看 " + AppCatalog.label(this, target.pkg) + " 的教学截图");
            image.setOnClickListener(view -> showPreview(target));
            LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(Ui.dp(this, 60), Ui.dp(this, 96));
            size.setMarginEnd(Ui.dp(this, 12));
            row.addView(image, size);
        }

        LinearLayout details = Ui.column(this, 0);
        row.addView(details, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        String where = "*".equals(target.activity) ? "全部页面" : shortName(target.activity);
        Ui.add(this, details, Ui.text(this, "截图教学 · " + where, 14, Ui.INK, true), 0);
        Ui.add(this, details, Ui.text(this, "位置 " + percent(target.xRatio()) + " / " + percent(target.yRatio())
                + "\n命中 " + target.hits + "　失败 " + target.misses, 12, Ui.MUTED, false), 3);
        Ui.add(this, details, Ui.text(this, thumbnail != null ? "点缩略图放大" : "暂无截图预览",
                12, Ui.MUTED, false), 3);
        TextView move = Ui.text(this, "更改所属应用", 13, Ui.GREEN, true);
        move.setOnClickListener(view -> {
            movingLesson = target.key();
            startActivityForResult(new Intent(this, AppPickerActivity.class), REQUEST_MOVE);
        });
        Ui.add(this, details, move, 6);
        TextView forget = Ui.text(this, "忘掉这一条", 13, Ui.GREEN, true);
        forget.setOnClickListener(view -> {
            AppPrefs.taught(AppPrefs.open(this)).remove(target.key());
            AdSkipService.reloadTaught();
            render();
        });
        Ui.add(this, details, forget, 6);
    }

    private void showPreview(TaughtTargets.Target target) {
        Bitmap bitmap = LessonPreview.decode(target.preview, false);
        if (bitmap == null) return;
        LinearLayout content = Ui.column(this, 16);
        LessonPreview image = new LessonPreview(this, bitmap, target, false);
        image.setContentDescription("教学截图，关闭点在横向 " + percent(target.xRatio())
                + "，纵向 " + percent(target.yRatio()));
        int availableHeight = getResources().getDisplayMetrics().heightPixels;
        content.addView(image, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                Math.min(Ui.dp(this, 520), (int) (availableHeight * .56f))));
        Ui.add(this, content, Ui.text(this, "圆圈标记的是教学时选中的位置", 13, Ui.MUTED, false), 8);
        new AlertDialog.Builder(this)
                .setTitle(AppCatalog.label(this, target.pkg) + " · 教学截图")
                .setView(content)
                .setPositiveButton("关闭", null)
                .show();
    }

    private void row(LinearLayout card, String kind, String where, String detail, String label,
                     Runnable forget) {
        LinearLayout row = Ui.column(this, 12);
        row.setBackground(Ui.round(this, Ui.TINT, 12));
        Ui.add(this, card, row, 8);
        Ui.add(this, row, Ui.text(this, kind + "　" + where, 14, Ui.INK, true), 0);
        String text = detail + (label == null || label.isEmpty() ? "" : "　「" + label + "」");
        Ui.add(this, row, Ui.text(this, text, 12, Ui.MUTED, false), 2);
        TextView link = Ui.text(this, "忘掉这一条", 13, Ui.GREEN, true);
        link.setOnClickListener(view -> forget.run());
        Ui.add(this, row, link, 6);
    }

    private void confirmClear() {
        new AlertDialog.Builder(this)
                .setTitle("清空全部学习记录")
                .setMessage("成功记忆、嫌疑点、截图教学和你的判断都会删除，之后从零学习。")
                .setNegativeButton("取消", null)
                .setPositiveButton("清空", (dialog, which) -> {
                    AdSkipService.clearLearned();
                    AdSkipService.clearFeedback();
                    AdSkipService.clearTaught();
                    AppPrefs.open(this).edit().remove(AppPrefs.REPEAT_DENIED).apply();
                    render();
                })
                .show();
    }

    private static Group group(Map<String, Group> groups, String pkg) {
        Group group = groups.get(pkg);
        if (group == null) {
            group = new Group(pkg);
            groups.put(pkg, group);
        }
        return group;
    }

    private static String packageOf(String signature) {
        if (signature == null) return "";
        int slash = signature.indexOf('/');
        return slash > 0 ? signature.substring(0, slash) : signature;
    }

    private static String activityOf(String signature) {
        if (signature == null) return "";
        String[] parts = signature.split("/");
        return parts.length > 1 ? parts[1] : "";
    }

    private static String shortName(String activity) {
        if (activity == null || activity.isEmpty()) return "";
        int dot = activity.lastIndexOf('.');
        return dot >= 0 ? activity.substring(dot + 1) : activity;
    }

    private static String percent(double ratio) {
        return Math.round(ratio * 100) + "%";
    }

    private AdMemory.Store memoryStore() {
        return new AdMemory.Store() {
            @Override public String read() {
                return AppPrefs.open(LearningActivity.this).getString(AppPrefs.LEARNED, "");
            }

            @Override public void write(String value) {
                AppPrefs.open(LearningActivity.this).edit().putString(AppPrefs.LEARNED, value).apply();
            }
        };
    }

    private AdHints.Store hintStore() {
        return new AdHints.Store() {
            @Override public String read() {
                return AppPrefs.open(LearningActivity.this).getString(AppPrefs.HINTS, "");
            }

            @Override public void write(String value) {
                AppPrefs.open(LearningActivity.this).edit().putString(AppPrefs.HINTS, value).apply();
            }
        };
    }

    private AdFeedback.Store feedbackStore() {
        return new AdFeedback.Store() {
            @Override public String read() {
                return AppPrefs.open(LearningActivity.this).getString(AppPrefs.FEEDBACK, "");
            }

            @Override public void write(String value) {
                AppPrefs.open(LearningActivity.this).edit().putString(AppPrefs.FEEDBACK, value).apply();
            }
        };
    }

}
