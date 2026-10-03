// 输入法启用向导，内容避开系统栏并允许小屏和横屏滚动。
package io.github.utyoinog.yagamiime;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Build;
import android.provider.Settings;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.inputmethod.InputMethodManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public final class SetupActivity extends Activity {
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        KeyboardStyle style = new KeyboardStyle(this);
        int padding = dp(24);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setGravity(Gravity.CENTER_HORIZONTAL);
        page.setPadding(padding, padding * 2, padding, padding);
        page.setBackgroundColor(style.background);
        TextView mark = style.key("", true, () -> {});
        style.icon(mark, KeyIcon.KEYBOARD, false, 40);
        mark.setClickable(false); mark.setImportantForAccessibility(android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams emblem = new LinearLayout.LayoutParams(dp(80), dp(80));
        emblem.bottomMargin = dp(24);
        page.addView(mark, emblem);

        TextView title = text(getString(R.string.setup_title), 24);
        title.setTextColor(style.text);
        title.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
        page.addView(title, matchWrap());

        TextView body = text(getString(R.string.setup_body), 16);
        body.setTextColor(style.muted);
        body.setLineSpacing(dp(5), 1);
        body.setPadding(0, dp(24), 0, dp(24));
        page.addView(body, matchWrap());

        TextView enable = style.key(getString(R.string.enable_ime), true,
                () -> startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)));
        style.primary(enable); enable.setTextSize(16);
        style.icon(enable, KeyIcon.SETTINGS, true, 22);
        LinearLayout.LayoutParams button = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56));
        button.bottomMargin = dp(12);
        page.addView(enable, button);

        TextView choose = style.key(getString(R.string.select_ime), false, () -> {
            InputMethodManager manager = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            manager.showInputMethodPicker();
        });
        choose.setTextSize(16); style.icon(choose, KeyIcon.KEYBOARD, true, 22);
        page.addView(choose, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)));
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(page);
        if (Build.VERSION.SDK_INT >= 30) { getWindow().setDecorFitsSystemWindows(false); }
        scroll.setOnApplyWindowInsetsListener((view, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets safe = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                view.setPadding(safe.left, safe.top, safe.right, safe.bottom);
            } else {
                view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                        insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            }
            return insets;
        });
        getWindow().getDecorView().setSystemUiVisibility(style.dark ? 0
                : android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        setContentView(scroll);
        scroll.requestApplyInsets();
    }

    private TextView text(String value, float size) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        return view;
    }

    private ViewGroup.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
