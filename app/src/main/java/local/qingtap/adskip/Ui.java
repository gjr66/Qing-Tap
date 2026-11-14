package local.qingtap.adskip;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

final class Ui {
    static final int PAPER = Color.rgb(242, 246, 244);
    static final int INK = Color.rgb(22, 38, 33);
    static final int MUTED = Color.rgb(64, 88, 80);
    static final int GREEN = Color.rgb(21, 101, 81);
    static final int TINT = Color.rgb(222, 239, 231);
    static final int WHITE = Color.WHITE;

    private Ui() {}

    static int dp(Activity activity, int value) {
        return Math.round(activity.getResources().getDisplayMetrics().density * value);
    }

    static GradientDrawable round(Activity activity, int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(activity, radius));
        return drawable;
    }

    static void insetRoot(View root) {
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets safe = insets.getInsets(
                        WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                view.setPadding(safe.left, safe.top, safe.right, safe.bottom);
            }
            return insets;
        });
    }

    static TextView text(Activity activity, String value, int size, int color, boolean bold) {
        TextView view = new TextView(activity);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setLineSpacing(dp(activity, 2), 1f);
        return view;
    }

    static Button button(Activity activity, String label, boolean primary, View.OnClickListener listener) {
        Button button = new Button(activity);
        button.setAllCaps(false);
        button.setText(label);
        button.setTextSize(15);
        button.setTextColor(primary ? WHITE : GREEN);
        button.setMinHeight(dp(activity, 48));
        button.setBackground(round(activity, primary ? GREEN : TINT, 12));
        button.setPadding(dp(activity, 14), dp(activity, 10), dp(activity, 14), dp(activity, 10));
        button.setOnClickListener(listener);
        return button;
    }

    static void add(Activity activity, LinearLayout parent, View child, int top) {
        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(activity, top);
        parent.addView(child, params);
    }

    static LinearLayout column(Activity activity, int padding) {
        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        int space = dp(activity, padding);
        layout.setPadding(space, space, space, space);
        return layout;
    }
}
