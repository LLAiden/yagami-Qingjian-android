// 拼音消歧放在九键左侧，不增加键盘高度；空闲时提供常用英文标点。
package io.github.utyoinog.yagamiime;

import android.content.Context;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

final class ReadingPanel extends ScrollView {
    private final KeyboardStyle style;
    private final KeyboardView.Actions actions;
    private final LinearLayout content;
    private final int rowHeight;
    private String signature = "";

    ReadingPanel(Context context, KeyboardView.Actions actions, int rowHeight) {
        super(context);
        this.actions = actions; this.rowHeight = rowHeight;
        style = new KeyboardStyle(context);
        setFillViewport(true); setVerticalScrollBarEnabled(false);
        setContentDescription("拼音选项与标点");
        content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        addView(content, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        update(new JSONObject());
    }

    void update(JSONObject snapshot) {
        String raw = snapshot.optString("raw");
        JSONArray options = snapshot.optJSONArray("readings");
        boolean limit = raw.length() >= NativeBridge.MAX_INPUT_LENGTH;
        boolean choosing = !raw.isEmpty() && options != null && options.length() > 0;
        String next = limit ? "limit" : choosing ? options.toString() : "marks";
        if (next.equals(signature)) { return; }
        signature = next; content.removeAllViews(); scrollTo(0, 0);
        if (limit) {
            TextView hint = new TextView(getContext());
            hint.setText("请先选词\n或删除"); hint.setTextSize(12); hint.setTextColor(style.accent);
            hint.setGravity(Gravity.CENTER); hint.setContentDescription("输入长度提示");
            content.addView(hint, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, style.dp(rowHeight * 3)));
        } else if (choosing) {
            for (int index = 0; index < options.length(); index++) {
                String reading = options.optString(index);
                TextView key = style.key(reading, true, () -> actions.reading(reading));
                key.setTextSize(14); key.setContentDescription("选择拼音 " + reading);
                LinearLayout.LayoutParams cell = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        style.dp(style.landscape ? 36 : 48));
                cell.setMargins(style.dp(3), style.dp(3), style.dp(3), style.dp(3));
                content.addView(key, cell);
            }
        } else {
            for (String mark : new String[]{"?", "!", "'"}) {
                TextView key = style.key(mark, true, () -> actions.text(mark));
                key.setBackground(new android.graphics.drawable.InsetDrawable(key.getBackground(), style.dp(2)));
                content.addView(key, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, style.dp(rowHeight)));
            }
        }
    }
}
