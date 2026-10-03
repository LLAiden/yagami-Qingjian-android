// 键盘与各面板共用的尺寸、颜色和按键样式。
package io.github.utyoinog.yagamiime;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.Gravity;
import android.widget.TextView;

final class KeyboardStyle {
    static final int BACKGROUND = Color.rgb(226, 231, 229);
    static final int KEY = Color.rgb(255, 255, 255);
    static final int SPECIAL = Color.rgb(202, 214, 208);
    static final int ACCENT = Color.rgb(49, 92, 74);
    static final int TEXT = Color.rgb(26, 41, 34);

    private final Context context;

    KeyboardStyle(Context context) { this.context = context; }

    int dp(int value) { return Math.round(value * context.getResources().getDisplayMetrics().density); }

    TextView key(String label, boolean special, Runnable action) {
        TextView view = new TextView(context);
        view.setText(label);
        view.setTextSize(19);
        view.setTextColor(TEXT);
        view.setGravity(Gravity.CENTER);
        view.setContentDescription(label);
        view.setClickable(true);
        view.setFocusable(false);
        StateListDrawable background = new StateListDrawable();
        background.addState(new int[]{android.R.attr.state_pressed}, rounded(Color.rgb(169, 192, 181)));
        background.addState(new int[]{}, rounded(special ? SPECIAL : KEY));
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
