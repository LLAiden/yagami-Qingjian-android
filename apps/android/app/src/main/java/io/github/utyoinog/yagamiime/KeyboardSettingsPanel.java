// 键盘内的可滚动设置面板，偏好更改由服务保存并刷新视图。
package io.github.utyoinog.yagamiime;

import android.content.Context;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.function.BiConsumer;

final class KeyboardSettingsPanel extends LinearLayout {
    KeyboardSettingsPanel(Context context, BiConsumer<String, Integer> setting) {
        super(context);
        setOrientation(VERTICAL);
        KeyboardStyle style = new KeyboardStyle(context);
        KeyboardPreferences preferences = new KeyboardPreferences(context);
        ScrollView scroll = new ScrollView(context);
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(VERTICAL);
        TextView label = new TextView(context);
        label.setText("键盘高度");
        label.setTextColor(style.text);
        label.setTextSize(13);
        label.setPadding(style.dp(8), 0, 0, 0);
        content.addView(label, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, style.dp(24)));
        LinearLayout choices = new LinearLayout(context);
        String[] labels = {"矮", "标准", "高"};
        for (int i = 0; i < labels.length; i++) {
            final int height = i - 1;
            TextView choice = style.key((preferences.height == height ? "✓ " : "") + labels[i], true,
                    () -> setting.accept("height", height));
            choice.setContentDescription("高度 " + labels[i]);
            choice.setTextSize(16);
            LayoutParams cell = new LayoutParams(0, style.dp(48), 1);
            cell.setMargins(style.dp(3), 0, style.dp(3), 0);
            choices.addView(choice, cell);
        }
        content.addView(choices);
        TextView themeLabel = new TextView(context);
        themeLabel.setText("键盘主题");
        themeLabel.setTextColor(style.text);
        themeLabel.setTextSize(13);
        themeLabel.setPadding(style.dp(8), 0, 0, 0);
        content.addView(themeLabel, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, style.dp(24)));
        LinearLayout themes = new LinearLayout(context);
        String[] themeLabels = {"跟随系统", "浅色", "深色"};
        for (int i = 0; i < themeLabels.length; i++) {
            final int theme = i;
            TextView choice = style.key((preferences.theme == theme ? "✓ " : "") + themeLabels[i], true,
                    () -> setting.accept("theme", theme));
            choice.setContentDescription("主题 " + themeLabels[i]);
            choice.setTextSize(16);
            LayoutParams cell = new LayoutParams(0, style.dp(48), 1);
            cell.setMargins(style.dp(3), 0, style.dp(3), 0);
            themes.addView(choice, cell);
        }
        content.addView(themes);
        scroll.addView(content);
        addView(scroll, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
    }
}
