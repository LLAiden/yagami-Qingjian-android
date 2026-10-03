// 展开候选的多列浏览面板，候选顺序与上屏编号由引擎快照决定。
package io.github.utyoinog.yagamiime;

import android.content.Context;
import android.content.res.Configuration;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

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
    private String contents = "";

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
        String next = candidates.toString();
        if (next.equals(contents)) { return; }
        contents = next;
        title.setText("候选 · 共 " + candidates.length() + " 项 · 英语释义在下方");
        rows.removeAllViews();
        for (int offset = 0; offset < candidates.length(); offset += columns) {
            LinearLayout row = new LinearLayout(getContext());
            for (int column = 0; column < columns; column++) {
                final int index = offset + column;
                JSONObject candidate = candidates.optJSONObject(index);
                CandidateKey key = new CandidateKey(getContext(), style, candidate == null ? new JSONObject() : candidate,
                        true, index == 0, () -> choose.accept(index));
                if (candidate == null) { key.setVisibility(INVISIBLE); }
                LayoutParams cell = new LayoutParams(0, style.dp(style.candidateHeight() + 4), 1);
                cell.setMargins(style.dp(2), style.dp(2), style.dp(2), style.dp(2));
                row.addView(key, cell);
            }
            rows.addView(row);
        }
        scroll.post(() -> scroll.scrollTo(0, 0));
    }
}
