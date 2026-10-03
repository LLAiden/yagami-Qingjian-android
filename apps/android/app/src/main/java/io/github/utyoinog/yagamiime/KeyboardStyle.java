// 所有键盘与面板共用的颜色、文字、圆角及功能图标。
package io.github.utyoinog.yagamiime;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.Gravity;
import android.widget.TextView;

final class KeyboardStyle {
    final int background;
    final int key;
    final int special;
    final int accent;
    final int text;
    final int muted;
    final int border;
    final int pressed;
    final boolean dark;
    final boolean landscape;
    private final Context context;

    KeyboardStyle(Context context) {
        this.context = context;
        dark = new KeyboardPreferences(context).dark;
        landscape = context.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        background = Color.parseColor(dark ? "#161B24" : "#F1F3F7");
        key = Color.parseColor(dark ? "#262E3B" : "#FFFFFF");
        special = Color.parseColor(dark ? "#303B50" : "#E5EBF6");
        accent = Color.parseColor(dark ? "#B6C8FF" : "#315FCB");
        text = Color.parseColor(dark ? "#EDF2FC" : "#252D40");
        muted = Color.parseColor(dark ? "#ADB9CF" : "#606B80");
        border = Color.parseColor(dark ? "#3A4558" : "#DFE4EE");
        pressed = Color.parseColor(dark ? "#465B80" : "#CCDDFC");
    }

    int dp(int value) { return Math.round(value * context.getResources().getDisplayMetrics().density); }
    float sp(int value) { return value * context.getResources().getDisplayMetrics().scaledDensity; }
    int toolbarHeight() { return landscape ? 40 : 48; }
    int candidateHeight() { return landscape ? 48 : 60; }
    int readingHeight() { return landscape ? 32 : 48; }

    TextView key(String label, boolean special, Runnable action) {
        TextView view = new IconKey(context);
        view.setText(label); view.setTextSize(19); view.setTextColor(text);
        view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        view.setGravity(Gravity.CENTER); view.setIncludeFontPadding(false);
        view.setContentDescription(label); view.setClickable(true); view.setFocusable(false);
        view.setBackground(background(special));
        view.setOnClickListener(ignored -> action.run());
        return view;
    }

    void icon(TextView view, KeyIcon icon, boolean keepLabel, int size) {
        KeyIconDrawable drawable = new KeyIconDrawable(icon, view.getCurrentTextColor());
        drawable.setBounds(0, 0, dp(size), dp(size));
        view.setCompoundDrawablePadding(dp(4));
        ((IconKey) view).icon(drawable, false);
        if (!keepLabel) { view.setText(""); }
    }

    void toolbar(TextView view, KeyIcon icon, boolean keepLabel) {
        view.setTextSize(12); view.setTextColor(muted);
        view.setBackground(states(Color.TRANSPARENT, false));
        icon(view, icon, keepLabel, keepLabel ? 18 : 24);
    }

    void circleToolbar(TextView view, KeyIcon icon) {
        toolbar(view, icon, false);
        icon(view, icon, false, 18);
        StateListDrawable circles = new StateListDrawable();
        circles.addState(new int[]{android.R.attr.state_pressed}, circle(pressed));
        circles.addState(new int[]{}, circle(special));
        int vertical = dp((toolbarHeight() - 36) / 2);
        view.setBackground(new android.graphics.drawable.InsetDrawable(circles, dp(6), vertical, dp(6), vertical));
    }

    private GradientDrawable circle(int color) {
        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.OVAL); shape.setColor(color);
        shape.setStroke(dp(1), border);
        return shape;
    }

    void iconAbove(TextView view, KeyIcon icon, int size) {
        KeyIconDrawable drawable = new KeyIconDrawable(icon, view.getCurrentTextColor());
        drawable.setBounds(0, 0, dp(size), dp(size));
        view.setCompoundDrawablePadding(dp(2));
        ((IconKey) view).icon(drawable, true);
    }

    void primary(TextView view) {
        view.setTextColor(dark ? Color.rgb(22, 34, 60) : Color.WHITE);
        view.setBackground(states(accent, false));
    }

    StateListDrawable background(boolean function) { return states(function ? special : key, true); }
    StateListDrawable candidateBackground(boolean first, boolean expanded) {
        return states(first ? special : expanded ? key : Color.TRANSPARENT, expanded);
    }

    private StateListDrawable states(int normal, boolean outlined) {
        StateListDrawable drawable = new StateListDrawable();
        drawable.addState(new int[]{android.R.attr.state_pressed}, rounded(pressed, false));
        drawable.addState(new int[]{}, rounded(normal, outlined));
        return drawable;
    }

    private GradientDrawable rounded(int color, boolean outlined) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color); shape.setCornerRadius(dp(10));
        if (outlined) { shape.setStroke(Math.max(1, dp(1)), border); }
        return shape;
    }
}
