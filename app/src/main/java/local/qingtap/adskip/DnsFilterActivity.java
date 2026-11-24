package local.qingtap.adskip;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.VpnService;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

public final class DnsFilterActivity extends Activity {
    private static final int REQUEST_VPN = 53;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Switch wechat;
    private Switch alipay;
    private Button action;
    private TextView status;
    private boolean starting;
    private long startTime;
    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            updateStatus();
            handler.postDelayed(this, 600);
        }
    };

    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        SharedPreferences prefs = AppPrefs.open(this);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Ui.PAPER);
        if (Build.VERSION.SDK_INT >= 35) Ui.insetRoot(scroll);
        LinearLayout body = Ui.column(this, 22);
        scroll.addView(body);
        setContentView(scroll);
        Ui.add(this, body, Ui.text(this, "小程序域名过滤", 28, Ui.INK, true), 6);
        Ui.add(this, body, Ui.text(this,
                "试验功能 · 仅对所选应用生效，包含它的普通页面和小程序。启用后会占用系统 VPN。", 15, Ui.MUTED, false), 10);
        Ui.add(this, body, Ui.text(this,
                "清弹在本机检查域名，普通查询交给 Android 当前网络的 DNS。界面只显示计数。缓存、应用自带 DNS 和与业务共用域名的广告可能继续显示。",
                14, Ui.MUTED, false), 12);
        wechat = appSwitch("微信", MiniProgramRules.WECHAT, DnsFilterService.WECHAT, prefs);
        alipay = appSwitch("支付宝", MiniProgramRules.ALIPAY, DnsFilterService.ALIPAY, prefs);
        Ui.add(this, body, wechat, 14);
        Ui.add(this, body, alipay, 4);
        status = Ui.text(this, "", 14, Ui.INK, true);
        Ui.add(this, body, status, 14);
        action = Ui.button(this, "开启过滤", true, view -> toggle());
        Ui.add(this, body, action, 12);
        Ui.add(this, body, Ui.button(this, "返回清弹", false, view -> finish()), 10);
        updateStatus();
    }

    private Switch appSwitch(String label, String pkg, String key, SharedPreferences prefs) {
        Switch control = new Switch(this);
        control.setText(label);
        control.setTextColor(Ui.INK);
        control.setMinHeight(Ui.dp(this, 48));
        control.setChecked(installed(pkg) && prefs.getBoolean(key, true));
        control.setOnCheckedChangeListener((button, checked) -> {
            prefs.edit().putBoolean(key, checked).apply();
            updateStatus();
        });
        return control;
    }

    private boolean installed(String pkg) {
        try {
            getPackageManager().getApplicationInfo(pkg, 0);
            return true;
        } catch (PackageManager.NameNotFoundException absent) {
            return false;
        }
    }

    private void updateStatus() {
        if (action == null) return;
        boolean active = DnsFilterService.active();
        if (active || android.os.SystemClock.elapsedRealtime() - startTime > 3000) starting = false;
        boolean supported = Build.VERSION.SDK_INT >= 29;
        wechat.setEnabled(supported && !active && !starting && installed(MiniProgramRules.WECHAT));
        alipay.setEnabled(supported && !active && !starting && installed(MiniProgramRules.ALIPAY));
        status.setText(!supported ? "此功能需要 Android 10 或以上"
                : starting ? "正在启动…" : DnsFilterService.summary());
        action.setText(active ? "关闭过滤" : "开启过滤");
        action.setEnabled(supported && !starting && (active || wechat.isChecked() || alipay.isChecked()));
    }

    private void toggle() {
        if (DnsFilterService.active()) {
            stopService(new Intent(this, DnsFilterService.class));
            return;
        }
        try {
            Intent consent = VpnService.prepare(this);
            if (consent == null) startFilter();
            else startActivityForResult(consent, REQUEST_VPN);
        } catch (RuntimeException unavailable) {
            Toast.makeText(this, "无法获取 VPN 授权", Toast.LENGTH_SHORT).show();
        }
    }

    private void startFilter() {
        if (!wechat.isChecked() && !alipay.isChecked()) return;
        try {
            DnsFilterService.status = "已停止";
            startForegroundService(new Intent(this, DnsFilterService.class)
                    .putExtra(DnsFilterService.WECHAT, wechat.isChecked())
                    .putExtra(DnsFilterService.ALIPAY, alipay.isChecked()));
            startTime = android.os.SystemClock.elapsedRealtime();
            starting = true;
            updateStatus();
        } catch (RuntimeException unavailable) {
            Toast.makeText(this, "无法启动 VPN", Toast.LENGTH_SHORT).show();
        }
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == REQUEST_VPN && result == RESULT_OK) startFilter();
    }

    @Override protected void onResume() {
        super.onResume();
        refresh.run();
    }

    @Override protected void onPause() {
        handler.removeCallbacks(refresh);
        super.onPause();
    }
}
