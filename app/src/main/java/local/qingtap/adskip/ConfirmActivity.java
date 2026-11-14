package local.qingtap.adskip;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

// One question, asked in context: "is this page an ad?". It appears over whatever is on screen, is
// excluded from recents, and gives up after a few seconds instead of blocking the phone.
public final class ConfirmActivity extends Activity {
    interface Answer {
        void onAnswer(boolean ad);
    }

    private static volatile Answer answer;
    private static volatile String appLabel = "";
    private static volatile String detail = "";
    private static long shownAt;
    private static final long TIMEOUT_MS = 7000;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable timeout = () -> dismiss(false);

    static boolean ask(Context context, String app, String detailText, Answer callback) {
        appLabel = app == null ? "" : app;
        detail = detailText == null ? "" : detailText;
        answer = callback;
        shownAt = SystemClock.uptimeMillis();
        Intent intent = new Intent(context, ConfirmActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION
                | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
        try {
            context.startActivity(intent);
            return true;
        } catch (Exception blocked) {
            answer = null;
            return false;
        }
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (SystemClock.uptimeMillis() - shownAt > TIMEOUT_MS) {
            finish();
            return;
        }
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.argb(140, 0, 0, 0));
        if (Build.VERSION.SDK_INT >= 35) Ui.insetRoot(root);
        setContentView(root);

        LinearLayout card = Ui.column(this, 22);
        card.setBackground(Ui.round(this, Ui.WHITE, 18));
        FrameLayout.LayoutParams cardParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        int margin = Ui.dp(this, 22);
        cardParams.setMargins(margin, margin, margin, margin);
        root.addView(card, cardParams);

        Ui.add(this, card, Ui.text(this, "这个页面是广告吗？", 21, Ui.INK, true), 0);
        Ui.add(this, card, Ui.text(this, appLabel + " · " + detail, 14, Ui.MUTED, false), 8);
        Ui.add(this, card, Ui.text(this,
                "选「是」：现在就关掉，并记住这个页面，下次自动关闭。选「不是」：以后不再动这个页面的角落按钮。",
                14, Ui.MUTED, false), 10);
        Ui.add(this, card, Ui.button(this, "是，是广告，现在关掉", true, view -> dismiss(true)), 16);
        Ui.add(this, card, Ui.button(this, "不是广告，以后别动", false, view -> dismiss(false)), 8);
        Ui.add(this, card, Ui.button(this, "稍后再说", false, view -> later()), 8);
        handler.postDelayed(timeout, TIMEOUT_MS);
    }

    private void dismiss(boolean ad) {
        handler.removeCallbacks(timeout);
        Answer pending = answer;
        answer = null;
        finish();
        if (pending != null) pending.onAnswer(ad);
    }

    private void later() {
        handler.removeCallbacks(timeout);
        answer = null;
        finish();
    }

    @Override public void onBackPressed() {
        later();
    }

    @Override protected void onDestroy() {
        handler.removeCallbacks(timeout);
        super.onDestroy();
    }
}
