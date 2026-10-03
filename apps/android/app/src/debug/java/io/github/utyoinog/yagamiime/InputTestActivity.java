// 调试安装包的输入验收页，正式安装包不包含此页面。
package io.github.utyoinog.yagamiime;

import android.app.Activity;
import android.os.Bundle;
import android.text.InputType;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class InputTestActivity extends Activity {
    static volatile InputTestActivity current;
    EditText message;
    EditText number;
    EditText password;
    int lastEditorAction;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        current = this;
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(24, 32, 24, 16);
        TextView title = new TextView(this);
        title.setText("Yagami 输入验收");
        title.setTextSize(22);
        page.addView(title);
        message = field(page, "消息", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        message.setMinLines(2);
        number = field(page, "数字", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        number.setImeOptions(EditorInfo.IME_ACTION_DONE);
        number.setOnEditorActionListener((view, action, event) -> { lastEditorAction = action; return true; });
        password = field(page, "密码", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        setContentView(page);
        getWindow().getDecorView().setSystemUiVisibility(android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        page.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(24 + insets.getSystemWindowInsetLeft(), 32 + insets.getSystemWindowInsetTop(),
                    24 + insets.getSystemWindowInsetRight(), 16);
            return insets;
        });
        message.requestFocus();
    }

    private EditText field(LinearLayout page, String label, int type) {
        EditText edit = new EditText(this);
        edit.setHint(label);
        edit.setContentDescription(label);
        edit.setInputType(type);
        page.addView(edit, new LinearLayout.LayoutParams(-1, -2));
        return edit;
    }
}
