// 键盘与各面板共用的尺寸、颜色和按键样式。
package io.github.utyoinog.yagamiime;

import android.content.Context;
import android.graphics.Color;
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
    final int pressed;
    final boolean dark;
    private final Context context;

    KeyboardStyle(Context context) {
        this.context = context;
        dark = new KeyboardPreferences(context).dark;
        background = dark ? Color.rgb(29, 36, 33) : Color.rgb(226, 231, 229);
        key = dark ? Color.rgb(43, 52, 47) : Color.WHITE;
        special = dark ? Color.rgb(61, 75, 68) : Color.rgb(202, 214, 208);
        accent = dark ? Color.rgb(169, 213, 184) : Color.rgb(49, 92, 74);
        text = dark ? Color.rgb(237, 245, 239) : Color.rgb(26, 41, 34);
        pressed = dark ? Color.rgb(82, 105, 91) : Color.rgb(169, 192, 181);
    }

    int dp(int value) { return Math.round(value * context.getResources().getDisplayMetrics().density); }

    TextView key(String label, boolean special, Runnable action) {
        TextView view = new TextView(context);
        view.setText(label);
        view.setTextSize(19);
        view.setTextColor(text);
        view.setGravity(Gravity.CENTER);
        view.setContentDescription(label);
        view.setClickable(true);
        view.setFocusable(false);
        StateListDrawable background = new StateListDrawable();
        background.addState(new int[]{android.R.attr.state_pressed}, rounded(pressed));
        background.addState(new int[]{}, rounded(special ? this.special : key));
        view.setBackground(background);
        view.setOnClickListener(ignored -> action.run());
        return view;
    }

    private GradientDrawable rounded(int color) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(dp(7));
        return shape;
    }
}
