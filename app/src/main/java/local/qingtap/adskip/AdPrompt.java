package local.qingtap.adskip;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

// A small card drawn straight onto the screen by the accessibility service (TYPE_ACCESSIBILITY_OVERLAY),
// so the question shows up over the ad itself without starting an activity and without stealing focus.
final class AdPrompt {
    interface Listener {
        void onAnswer(boolean ad);
    }

    private static final long TIMEOUT_MS = 9000;
    private static final Handler HANDLER = new Handler(Looper.getMainLooper());
    private static View current;
    private static Runnable timeout;

    private AdPrompt() {}

    static boolean show(Context context, String title, String detail, Listener listener) {
        return show(context, title, detail, listener, () -> {});
    }

    static boolean show(Context context, String title, String detail, Listener listener, Runnable deferred) {
        WindowManager manager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        if (manager == null || !(context instanceof AccessibilityService)) return false;
        dismiss();

        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(context, 16);
        card.setPadding(pad, pad, pad, pad);
        GradientDrawable background = new GradientDrawable();
        background.setColor(0xf2ffffff);
        background.setCornerRadius(dp(context, 16));
        card.setBackground(background);
        card.setElevation(dp(context, 8));

        card.addView(line(context, title, 16, 0xff162621, true));
        TextView body = line(context, detail, 13, 0xff405850, false);
        LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        bodyParams.topMargin = dp(context, 6);
        card.addView(body, bodyParams);

        LinearLayout buttons = new LinearLayout(context);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams buttonsParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        buttonsParams.topMargin = dp(context, 12);
        card.addView(buttons, buttonsParams);

        buttons.addView(button(context, "是广告，关掉", 0xff156551, 0xffffffff, view -> answer(true, listener)));
        buttons.addView(button(context, "不是，别动这里", 0xffdeefe7, 0xff156551, view -> answer(false, listener)));
        LinearLayout.LayoutParams laterParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        laterParams.leftMargin = dp(context, 8);
        buttons.addView(button(context, "稍后", 0xfff2f6f4, 0xff405850, view -> {
            dismiss();
            deferred.run();
        }), laterParams);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        params.y = dp(context, 140);
        try {
            manager.addView(card, params);
        } catch (Exception blocked) {
            return false;
        }
        current = card;
        timeout = () -> {
            dismiss();
            deferred.run();
        };
        HANDLER.postDelayed(timeout, TIMEOUT_MS);
        return true;
    }

    static void dismiss(Context owner) {
        if (current != null && current.getContext() == owner) dismiss();
    }

    static void dismiss() {
        Runnable pending = timeout;
        timeout = null;
        if (pending != null) HANDLER.removeCallbacks(pending);
        View view = current;
        current = null;
        if (view == null) return;
        try {
            WindowManager manager = (WindowManager) view.getContext().getSystemService(Context.WINDOW_SERVICE);
            if (manager != null) manager.removeView(view);
        } catch (Exception alreadyGone) {
            // The overlay was removed by the system.
        }
    }

    private static void answer(boolean ad, Listener listener) {
        dismiss();
        if (listener != null) listener.onAnswer(ad);
    }

    private static TextView line(Context context, String text, int size, int color, boolean bold) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private static TextView button(Context context, String text, int background, int color,
                                   View.OnClickListener listener) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextSize(14);
        view.setTextColor(color);
        view.setGravity(Gravity.CENTER);
        int horizontal = dp(context, 14);
        int vertical = dp(context, 9);
        view.setPadding(horizontal, vertical, horizontal, vertical);
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(background);
        shape.setCornerRadius(dp(context, 10));
        view.setBackground(shape);
        view.setOnClickListener(listener);
        return view;
    }

    private static int dp(Context context, int value) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                context.getResources().getDisplayMetrics()));
    }
}
