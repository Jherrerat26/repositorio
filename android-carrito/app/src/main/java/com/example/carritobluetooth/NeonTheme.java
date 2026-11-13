package com.example.carritobluetooth;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.TextView;

/** Shared native styling for the controller and the Firebase history. */
final class NeonTheme {
    static final int BACKGROUND = Color.rgb(3, 10, 25);
    static final int PANEL = Color.rgb(8, 25, 48);
    static final int CYAN = Color.rgb(108, 222, 255);
    static final int TEXT = Color.rgb(180, 236, 255);
    static final int MUTED = Color.rgb(127, 180, 207);
    static final int BORDER = Color.rgb(65, 153, 200);

    static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    static GradientDrawable panel(Context context, int fill, int border, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(context, radius));
        drawable.setStroke(dp(context, 1), border);
        return drawable;
    }

    static TextView text(Context context, String value, int size, int color) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setFontFeatureSettings("tnum");
        return view;
    }

    static Button button(Context context, String title, boolean prominent) {
        Button button = new Button(context);
        button.setText(title);
        button.setTextSize(13);
        button.setAllCaps(false);
        button.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        button.setTextColor(new ColorStateList(new int[][] {
                new int[] {-android.R.attr.state_enabled}, new int[] {}
        }, new int[] {MUTED, CYAN}));
        button.setMinHeight(dp(context, 48));
        button.setMinimumHeight(dp(context, 48));
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setPadding(dp(context, 8), dp(context, 4), dp(context, 8), dp(context, 4));
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(0x335BDCFF),
                panel(context, prominent ? 0xFF10365B : PANEL, prominent ? CYAN : BORDER, 14), null));
        return button;
    }

    static ArrayAdapter<String> spinnerAdapter(Context context, String[] values) {
        return new ArrayAdapter<String>(context, android.R.layout.simple_spinner_dropdown_item, values) {
            private View style(View view, boolean dropdown) {
                TextView text = (TextView) view;
                text.setTextColor(TEXT);
                text.setTextSize(14);
                text.setSingleLine(false);
                text.setPadding(dp(context, 12), dp(context, 10), dp(context, 12), dp(context, 10));
                text.setMinHeight(dp(context, 48));
                if (dropdown) text.setBackgroundColor(PANEL);
                return text;
            }
            @Override public View getView(int position, View recycled, ViewGroup parent) {
                return style(super.getView(position, recycled, parent), false);
            }
            @Override public View getDropDownView(int position, View recycled, ViewGroup parent) {
                return style(super.getDropDownView(position, recycled, parent), true);
            }
        };
    }

    static void window(Activity activity, View root) {
        activity.getWindow().setStatusBarColor(BACKGROUND);
        activity.getWindow().setNavigationBarColor(BACKGROUND);
        activity.getWindow().getDecorView().setSystemUiVisibility(0);
        if (Build.VERSION.SDK_INT >= 29) activity.getWindow().setNavigationBarContrastEnforced(false);
        int padding = dp(activity, 16);
        root.setPadding(padding, dp(activity, 8), padding, dp(activity, 12));
        if (Build.VERSION.SDK_INT >= 30) {
            activity.getWindow().setDecorFitsSystemWindows(false);
            root.setOnApplyWindowInsetsListener((view, insets) -> {
                android.graphics.Insets bars = insets.getInsets(
                        WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                view.setPadding(padding + bars.left, dp(activity, 8) + bars.top,
                        padding + bars.right, dp(activity, 12) + bars.bottom);
                return insets;
            });
        }
        activity.setContentView(root);
        root.requestApplyInsets();
    }

    private NeonTheme() {}
}
