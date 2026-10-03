// 展开候选的多列浏览面板，候选顺序与上屏编号由引擎快照决定。
package io.github.utyoinog.yagamiime;

import android.content.Context;
import android.content.res.Configuration;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.function.Consumer;

final class CandidatePanel extends LinearLayout {
    private final KeyboardStyle style;
    private final TextView title;
    private final ScrollView scroll;
    private final LinearLayout rows;
    private final Consumer<Integer> choose;
    private final int columns;

    CandidatePanel(Context context, JSONArray candidates, Consumer<Integer> choose) {
        super(context);
        this.choose = choose;
        style = new KeyboardStyle(context);
        setOrientation(VERTICAL);
        title = new TextView(context);
        title.setTextColor(style.text);
        title.setTextSize(12);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setPadding(style.dp(8), 0, 0, 0);
        addView(title, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, style.dp(26)));
        scroll = new ScrollView(context);
        scroll.setContentDescription("展开候选列表");
        rows = new LinearLayout(context);
        rows.setOrientation(VERTICAL);
        columns = context.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE ? 5 : 3;
        scroll.addView(rows);
        addView(scroll, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        update(candidates);
    }

    void update(JSONArray candidates) {
        title.setText("候选 · 共 " + candidates.length() + " 项 · 长按查看译词");
        rows.removeAllViews();
        for (int offset = 0; offset < candidates.length(); offset += columns) {
            LinearLayout row = new LinearLayout(getContext());
            for (int column = 0; column < columns; column++) {
                final int index = offset + column;
                JSONObject candidate = candidates.optJSONObject(index);
                String text = candidate == null ? "" : candidate.optString("text");
                TextView key = style.key(text, false, () -> choose.accept(index));
                key.setTextSize(17);
                key.setMaxLines(2);
                key.setEllipsize(TextUtils.TruncateAt.END);
                key.setPadding(style.dp(8), 0, style.dp(8), 0);
                key.setContentDescription("展开候选 " + text);
                if (candidate == null) { key.setVisibility(INVISIBLE); }
                else {
                    String gloss = candidate.optString("gloss");
                    if (!gloss.isEmpty()) {
                        key.setOnLongClickListener(ignored -> {
                            Toast.makeText(getContext(), text + "：" + gloss, Toast.LENGTH_LONG).show();
                            return true;
                        });
                    }
                }
                LayoutParams cell = new LayoutParams(0, style.dp(52), 1);
                cell.setMargins(style.dp(2), style.dp(2), style.dp(2), style.dp(2));
                row.addView(key, cell);
            }
            rows.addView(row);
        }
        scroll.post(() -> scroll.scrollTo(0, 0));
    }
}
