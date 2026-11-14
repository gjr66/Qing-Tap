package local.qingtap.adskip;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

// Nothing but a bare counter in the bottom right corner: no skip wording, no ad badge, no cue.
// The structural rule closes it first, and the second launch reuses the remembered point.
public final class UnknownSplashActivity extends Activity {
    static boolean showing;
    static long openedAt;

    private final Runnable finishSample = this::finish;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        FrameLayout page = new FrameLayout(this);
        page.setBackgroundColor(Ui.TINT);
        if (Build.VERSION.SDK_INT >= 35) Ui.insetRoot(page);
        setContentView(page);
        openedAt = SystemClock.uptimeMillis();

        TextView counter = new TextView(this) {
            @Override public boolean onTouchEvent(MotionEvent event) {
                if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                    report();
                }
                return true;
            }
        };
        counter.setText("5");
        counter.setTextSize(16);
        counter.setTextColor(Ui.WHITE);
        counter.setGravity(Gravity.CENTER);
        counter.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        counter.setBackground(Ui.round(this, Ui.INK, 8));
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(Ui.dp(this, 72), Ui.dp(this, 40),
                Gravity.BOTTOM | Gravity.RIGHT);
        int margin = Ui.dp(this, 16);
        params.setMargins(margin, margin, margin, margin);
        page.addView(counter, params);

        page.addView(Ui.text(this, "无文案开屏样例", 22, Ui.INK, true),
                new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
    }

    private void report() {
        if (isFinishing()) return;
        long elapsed = SystemClock.uptimeMillis() - openedAt;
        Toast.makeText(this, String.format(Locale.US, "已被清弹关闭，用时 %d ms", elapsed),
                Toast.LENGTH_SHORT).show();
        page().postDelayed(finishSample, 260);
    }

    private View page() {
        return getWindow().getDecorView();
    }

    @Override protected void onResume() {
        super.onResume();
        showing = true;
    }

    @Override protected void onPause() {
        showing = false;
        super.onPause();
    }
}
