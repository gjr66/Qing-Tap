package local.qingtap.adskip;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.accessibility.AccessibilityManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Collections;
import java.util.Set;

public final class MainActivity extends Activity {
    private SharedPreferences prefs;
    private TextView serviceState;
    private TextView appsState;
    private TextView actionCount;
    private Switch active;
    private Button addAppsButton;
    private boolean openingAppSelection;
    private Switch visionSwitch;
    private boolean visionUpdating;
    private static final int REQUEST_CAPTURE = 42;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = AppPrefs.open(this);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Ui.PAPER);
        if (android.os.Build.VERSION.SDK_INT >= 35) Ui.insetRoot(scroll);
        LinearLayout body = Ui.column(this, 22);
        scroll.addView(body);
        setContentView(scroll);

        Ui.add(this, body, Ui.text(this, "清弹", 34, Ui.INK, true), 6);
        Ui.add(this, body, Ui.text(this, "在你选定的 App 里自动跳过广告", 16, Ui.MUTED, false), 2);

        LinearLayout stateCard = card(body, 22);
        serviceState = Ui.text(this, "", 18, Ui.GREEN, true);
        Ui.add(this, stateCard, serviceState, 0);
        Ui.add(this, stateCard, Ui.text(this,
                "Android 无障碍服务启用后，清弹会在设备上识别当前窗口里的广告关闭按钮。", 14, Ui.MUTED, false), 8);
        Ui.add(this, stateCard, Ui.button(this, "打开系统无障碍设置", true, view -> disclose()), 14);

        LinearLayout options = card(body, 14);
        active = new Switch(this);
        active.setText("对选定的 App 启用");
        active.setTextSize(16);
        active.setTextColor(Ui.INK);
        active.setMinHeight(Ui.dp(this, 44));
        active.setChecked(prefs.getBoolean(AppPrefs.ACTIVE, false));
        active.setOnCheckedChangeListener((button, checked) -> {
            prefs.edit().putBoolean(AppPrefs.ACTIVE, checked).apply();
            refresh();
        });
        Ui.add(this, options, active, 0);
        appsState = Ui.text(this, "", 14, Ui.MUTED, false);
        Ui.add(this, options, appsState, 3);
        addAppsButton = Ui.button(this, "一键添加可选 App", true, view -> addNewApps());
        Ui.add(this, options, addAppsButton, 10);
        Ui.add(this, options, Ui.button(this, "选择要处理的 App", false, view -> chooseApps()), 10);

        Switch splash = new Switch(this);
        splash.setText("处理开屏及全屏弹窗广告");
        splash.setTextSize(16);
        splash.setTextColor(Ui.INK);
        splash.setMinHeight(Ui.dp(this, 44));
        splash.setChecked(prefs.getBoolean(AppPrefs.SPLASH_ACTIVE, true));
        splash.setOnCheckedChangeListener((button, checked) ->
                prefs.edit().putBoolean(AppPrefs.SPLASH_ACTIVE, checked).apply());
        Ui.add(this, options, splash, 12);

        Switch inline = new Switch(this);
        inline.setText("处理可关闭的内嵌广告");
        inline.setTextSize(16);
        inline.setTextColor(Ui.INK);
        inline.setMinHeight(Ui.dp(this, 44));
        inline.setChecked(prefs.getBoolean(AppPrefs.INLINE, false));
        inline.setOnCheckedChangeListener((button, checked) ->
                prefs.edit().putBoolean(AppPrefs.INLINE, checked).apply());
        Ui.add(this, options, inline, 12);
        Ui.add(this, options, Ui.text(this,
                "适用于带广告标识和独立关闭入口的部分评论区、信息流和横幅广告。", 14, Ui.MUTED, false), 3);
        TextView repeatSummary = Ui.text(this, repeatSummary(), 13, Ui.MUTED, false);
        Ui.add(this, options, Ui.button(this, "设置重复出现保护", false, view -> configureRepeat(repeatSummary)), 12);
        Ui.add(this, options, repeatSummary, 4);

        LinearLayout closing = card(body, 14);
        Ui.add(this, closing, Ui.text(this, "关闭操作", 18, Ui.INK, true), 0);
        actionCount = Ui.text(this, "", 14, Ui.MUTED, false);
        Ui.add(this, closing, actionCount, 5);
        Ui.add(this, closing, Ui.button(this, "查看关闭记录", false,
                view -> startActivity(new Intent(this, CloseHistoryActivity.class))), 8);

        LinearLayout demo = card(body, 14);
        Ui.add(this, demo, Ui.text(this, "广告测试与学习", 18, Ui.INK, true), 0);
        Ui.add(this, demo, Ui.button(this, "批量测试广告截图", false,
                view -> startActivity(new Intent(this, DemoActivity.class))), 10);
        AdMemory learned = new AdMemory(new AdMemory.Store() {
            @Override public String read() {
                return prefs.getString(AppPrefs.LEARNED, "");
            }

            @Override public void write(String value) {
                prefs.edit().putString(AppPrefs.LEARNED, value).apply();
            }
        });
        Ui.add(this, demo, Ui.text(this, "已记住 " + learned.size() + " 个开屏窗口，第二次打开更快",
                13, Ui.MUTED, false), 6);
        AdHints suspects = new AdHints(new AdHints.Store() {
            @Override public String read() {
                return prefs.getString(AppPrefs.HINTS, "");
            }

            @Override public void write(String value) {
                prefs.edit().putString(AppPrefs.HINTS, value).apply();
            }
        });
        Ui.add(this, demo, Ui.text(this, "已自动学会 " + suspects.size() + " 个没关掉的广告，下次优先试它",
                13, Ui.MUTED, false), 4);

        LinearLayout guard = card(body, 14);
        Ui.add(this, guard, Ui.text(this, "常驻与恢复", 18, Ui.INK, true), 0);
        Ui.add(this, guard, Ui.text(this,
                "系统不允许 App 自己开启无障碍，只能在你点开设置时开启。这里做两件事：用常驻通知让进程更少被清理；一旦无障碍被关掉，立刻发通知，一键跳回设置。",
                14, Ui.MUTED, false), 6);
        Switch keepAlive = new Switch(this);
        keepAlive.setText("常驻后台并提醒我");
        keepAlive.setTextSize(16);
        keepAlive.setTextColor(Ui.INK);
        keepAlive.setMinHeight(Ui.dp(this, 44));
        keepAlive.setChecked(prefs.getBoolean(AppPrefs.KEEP_ALIVE, true));
        keepAlive.setOnCheckedChangeListener((button, checked) -> {
            prefs.edit().putBoolean(AppPrefs.KEEP_ALIVE, checked).apply();
            if (checked) KeepAliveService.start(this);
            else KeepAliveService.stop(this);
        });
        Ui.add(this, guard, keepAlive, 8);
        Ui.add(this, guard, Ui.button(this, "把清弹加入电池白名单", false, view -> requestBatteryExemption()), 8);
        Ui.add(this, guard, Ui.button(this, "各品牌保活设置（重要）", false, view -> showKeepAliveHelp()), 8);

        LinearLayout vision = card(body, 14);
        Ui.add(this, vision, Ui.text(this, "视觉兜底（实验）", 18, Ui.INK, true), 0);
        Ui.add(this, vision, Ui.text(this,
                "无障碍树里没有可用节点时，抓一帧画面在本机判断四角的小按钮并轻触。需要录屏授权，画面只在内存里，不保存不上传。",
                14, Ui.MUTED, false), 6);
        visionSwitch = new Switch(this);
        visionSwitch.setText("开启视觉兜底");
        visionSwitch.setTextSize(16);
        visionSwitch.setTextColor(Ui.INK);
        visionSwitch.setMinHeight(Ui.dp(this, 44));
        visionSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (visionUpdating) return;
            if (checked) requestVisionCapture(); else stopVisionCapture();
        });
        Ui.add(this, vision, visionSwitch, 8);
        Ui.add(this, vision, Ui.text(this,
                "风险：状态栏常驻录屏提示；部分机型每次启动都要重新授权；判错会误点角落控件；耗电略增。",
                13, Ui.MUTED, false), 8);
        Ui.add(this, demo, Ui.button(this, "从截图学习（教它一次）", false,
                view -> startActivity(new Intent(this, TeachActivity.class))), 10);
        Ui.add(this, demo, Ui.button(this, "查看学习记录", false,
                view -> startActivity(new Intent(this, LearningActivity.class))), 10);
        Ui.add(this, body, Ui.text(this, "无障碍模式 · 离线运行 · Android 8.0 及以上 · v2.0 r50",
                13, Ui.MUTED, false), 18);
    }

    private String repeatSummary() {
        return AppPrefs.repeatSeconds(prefs) + " 秒内第 " + AppPrefs.repeatCount(prefs) + " 次出现时暂停并询问";
    }

    private void configureRepeat(TextView summary) {
        LinearLayout fields = Ui.column(this, 16);
        fields.setOrientation(LinearLayout.HORIZONTAL);
        NumberPicker seconds = repeatPicker(fields, "时间窗口（秒）", RepeatGuard.MAX_SECONDS, AppPrefs.repeatSeconds(prefs));
        NumberPicker count = repeatPicker(fields, "出现次数", RepeatGuard.MAX_COUNT, AppPrefs.repeatCount(prefs));
        new AlertDialog.Builder(this)
                .setTitle("重复出现保护")
                .setView(fields)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", (dialog, which) -> {
                    seconds.clearFocus();
                    count.clearFocus();
                    prefs.edit().putInt(AppPrefs.REPEAT_SECONDS, seconds.getValue())
                            .putInt(AppPrefs.REPEAT_COUNT, count.getValue()).apply();
                    summary.setText(repeatSummary());
                })
                .show();
    }

    private NumberPicker repeatPicker(LinearLayout fields, String label, int maximum, int value) {
        LinearLayout column = Ui.column(this, 4);
        fields.addView(column, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        Ui.add(this, column, Ui.text(this, label, 14, Ui.INK, true), 0);
        NumberPicker picker = new NumberPicker(this);
        picker.setMinValue(2);
        picker.setMaxValue(maximum);
        picker.setValue(value);
        picker.setWrapSelectorWheel(false);
        picker.setContentDescription(label);
        Ui.add(this, column, picker, 4);
        return picker;
    }

    private void requestVisionCapture() {
        MediaProjectionManager manager = getSystemService(MediaProjectionManager.class);
        if (manager == null) {
            Toast.makeText(this, "此设备不支持屏幕捕获", Toast.LENGTH_SHORT).show();
            refresh();
            return;
        }
        startActivityForResult(manager.createScreenCaptureIntent(), REQUEST_CAPTURE);
    }

    private void stopVisionCapture() {
        ScreenVisionService.stop(this);
        refresh();
    }




    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == REQUEST_CAPTURE) {
            if (result == RESULT_OK && data != null) {
                ScreenVisionService.start(this, result, data);
                Toast.makeText(this, "视觉兜底已开启", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "没有获得录屏授权，视觉兜底保持关闭", Toast.LENGTH_SHORT).show();
            }
            refresh();
            return;
        }
    }

    private LinearLayout card(LinearLayout body, int margin) {
        LinearLayout card = Ui.column(this, 17);
        card.setBackground(Ui.round(this, Ui.WHITE, 16));
        Ui.add(this, body, card, margin);
        return card;
    }

    private void disclose() {
        new AlertDialog.Builder(this)
                .setTitle("启用前了解无障碍服务")
                .setMessage("服务能读取当前窗口公开的文字、控件和位置，并代点符合规则的广告关闭按钮。清弹只扫描你勾选并启用的 App，以及主动打开的测试样例。识别用的实时画面在内存中处理。你主动导入的教学缩略图、指纹、关闭位置和不操作记录保存在应用数据中。应用不联网，你可以随时在系统设置里关闭服务。")
                .setNegativeButton("取消", null)
                .setPositiveButton("前往系统设置", (dialog, which) ->
                        startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)))
                .show();
    }

    private void addNewApps() {
        addAppsButton.setEnabled(false);
        Context context = getApplicationContext();
        new Thread(() -> {
            Set<String> eligible = AppCatalog.eligibleForBulkAdd(context);
            Set<String> previous = AppCatalog.previousUnchecked(context, prefs, eligible);
            runOnUiThread(() -> {
                AppSelection selection = AppPrefs.appSelection(prefs, previous);
                int added = selection.addNew(eligible);
                if (added > 0) AppPrefs.saveAppSelection(prefs, selection);
                else AppPrefs.initializeBlacklist(prefs, selection);
                if (isFinishing() || isDestroyed()) return;
                addAppsButton.setEnabled(true);
                refresh();
                String message = added == 0 ? "暂无可一键添加的 App" : "已添加 " + added + " 个 App";
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
            });
        }, "qingtap-bulk-add").start();
    }

    private void chooseApps() {
        if (openingAppSelection) return;
        openingAppSelection = true;
        startActivity(new Intent(this, AppSelectionActivity.class));
    }

    private boolean serviceEnabled() {
        return ServiceStatus.accessibilityEnabled(this, AdSkipService.class);
    }

    private void requestBatteryExemption() {
        PowerManager power = getSystemService(PowerManager.class);
        String pkg = getPackageName();
        if (power != null && power.isIgnoringBatteryOptimizations(pkg)) {
            Toast.makeText(this, "已经在电池白名单里了", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    android.net.Uri.parse("package:" + pkg)));
        } catch (Exception missing) {
            try {
                startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
            } catch (Exception again) {
                Toast.makeText(this, "这台设备没有可用的电池优化设置", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void showKeepAliveHelp() {
        new AlertDialog.Builder(this)
                .setTitle("各品牌保活设置")
                .setMessage("国内系统清理后台时，经常顺手把无障碍权限一起关掉。以下设置能显著减少这种情况：\n\n"
                        + "小米 / Redmi：设置 → 应用设置 → 清弹 → 省电策略「无限制」；最近任务里长按清弹 → 加锁。\n\n"
                        + "华为 / 荣耀：设置 → 应用 → 清弹 → 耗电详情 → 允许后台活动；手机管家 → 应用启动管理 → 手动管理，三项全开。\n\n"
                        + "OPPO / 一加 / realme：设置 → 电池 → 应用耗电管理 → 清弹 → 允许后台运行；最近任务里加锁。\n\n"
                        + "vivo / iQOO：设置 → 电池 → 后台耗电管理 → 清弹 → 允许后台高耗电；i管家 → 自启动 → 打开。\n\n"
                        + "三星：设置 → 电池 → 后台使用限制 → 从「休眠应用」里移除。\n\n"
                        + "通用：不要用「一键清理」清掉清弹；清理后如果无障碍掉了，点通知即可回到设置重新开启。")
                .setPositiveButton("知道了", null)
                .show();
    }

    private void refresh() {
        if (actionCount == null) return;
        AppSelection selection = AppPrefs.appSelection(prefs, Collections.emptySet());
        serviceState.setText(serviceEnabled() ? "无障碍服务已开启" : "等待开启无障碍服务");
        appsState.setText("开屏 " + selection.splash.size() + " · 内嵌 " + selection.inline.size()
                + " · 黑名单 " + selection.blocked().size());
        actionCount.setText("系统已接受 " + prefs.getLong(AppPrefs.ACTIONS, 0) + " 次关闭点击");
        visionUpdating = true;
        visionSwitch.setChecked(ScreenVisionService.running);
        visionUpdating = false;
    }

    @Override protected void onResume() {
        super.onResume();
        openingAppSelection = false;
        if (prefs.getBoolean(AppPrefs.KEEP_ALIVE, true) && serviceEnabled()
                && !KeepAliveService.running) {
            KeepAliveService.start(this);
        }
        refresh();
    }
}
