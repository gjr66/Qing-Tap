package local.qingtap.adskip;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class CloseHistoryActivity extends Activity {
    private SharedPreferences prefs;
    private AdActionLog log;
    private LinearLayout body;
    private boolean failuresOnly;
    private final SharedPreferences.OnSharedPreferenceChangeListener refreshLog = (changed, key) -> {
        if (AppPrefs.ACTION_LOG.equals(key)) render();
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = AppPrefs.open(this);
        failuresOnly = state != null && state.getBoolean("failures_only");
        log = AppPrefs.actionLog(prefs);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Ui.PAPER);
        if (Build.VERSION.SDK_INT >= 35) Ui.insetRoot(scroll);
        body = Ui.column(this, 16);
        scroll.addView(body);
        setContentView(scroll);
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putBoolean("failures_only", failuresOnly);
    }

    @Override protected void onStart() {
        super.onStart();
        prefs.registerOnSharedPreferenceChangeListener(refreshLog);
        render();
    }

    @Override protected void onStop() {
        prefs.unregisterOnSharedPreferenceChangeListener(refreshLog);
        super.onStop();
    }

    private void render() {
        body.removeAllViews();
        List<AdActionLog.Entry> entries = log.recent();
        Ui.add(this, body, Ui.text(this, "关闭记录", 25, Ui.INK, true), 0);
        Ui.add(this, body, Ui.text(this, entries.size() + " 条 · 识别、点击、首页跳转与结果核对",
                13, Ui.MUTED, false), 4);
        Switch filter = new Switch(this);
        filter.setText("只看未关闭与待核对");
        filter.setTextColor(Ui.INK);
        filter.setChecked(failuresOnly);
        filter.setOnCheckedChangeListener((button, checked) -> {
            failuresOnly = checked;
            render();
        });
        Ui.add(this, body, filter, 6);
        if (entries.isEmpty()) {
            Ui.add(this, body, Ui.text(this, "暂无关闭记录", 14, Ui.MUTED, false), 16);
            return;
        }
        SimpleDateFormat format = new SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault());
        int shown = 0;
        for (AdActionLog.Entry entry : entries) {
            if (failuresOnly && !entry.state.needsReview()) continue;
            shown++;
            String[] signature = entry.signature.split("/", 3);
            String app = AppCatalog.label(this, signature[0]);
            String page = signature.length > 1 ? signature[1] : "";
            if (!page.isEmpty()) app += " · " + page.substring(page.lastIndexOf('.') + 1);
            LinearLayout row = Ui.column(this, 13);
            row.setBackground(Ui.round(this, Ui.WHITE, 12));
            Ui.add(this, body, row, 8);
            Ui.add(this, row, Ui.text(this, app, 15, Ui.INK, true), 0);
            String when = entry.hasClockTime() ? format.format(new Date(entry.at)) : "旧记录 · 时间未知";
            Ui.add(this, row, Ui.text(this, when + " · " + entry.reason
                    + (entry.detail.isEmpty() ? "" : "　「" + entry.detail + "」"), 12, Ui.MUTED, false), 3);
            if (entry.state != AdActionLog.State.LEGACY) {
                Ui.add(this, row, Ui.text(this, entry.state.label + (entry.stage.isEmpty() ? "" : " · " + entry.stage),
                        12, entry.state.needsReview() ? Ui.INK : Ui.GREEN, true), 4);
            }
            if (!entry.outcome.isEmpty()) Ui.add(this, row, Ui.text(this, entry.outcome, 12, Ui.MUTED, false), 3);
        }
        if (shown == 0) Ui.add(this, body, Ui.text(this, "没有未关闭或待核对的记录", 14, Ui.MUTED, false), 16);
        Ui.add(this, body, Ui.button(this, "清空关闭记录", false, view -> new AlertDialog.Builder(this)
                .setTitle("清空关闭记录")
                .setMessage("删除当前的 " + log.size() + " 条关闭记录？")
                .setNegativeButton("取消", null)
                .setPositiveButton("清空", (dialog, which) -> log.clear())
                .show()), 12);
    }
}
