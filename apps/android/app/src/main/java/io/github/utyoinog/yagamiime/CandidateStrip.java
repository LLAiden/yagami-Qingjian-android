// 固定高度的双行候选栏与独立拼音选项，输入更新只刷新内容。
package io.github.utyoinog.yagamiime;

import android.content.Context;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

final class CandidateStrip extends LinearLayout {
    private static final int BATCH_SIZE = 12;
    private final KeyboardStyle style;
    private final KeyboardView.Actions actions;
    private final LinearLayout candidates;
    private final LinearLayout readings;
    private final HorizontalScrollView candidateScroll;
    private final HorizontalScrollView readingScroll;
    private final TextView expand;
    private String candidateContents = "";
    private String readingContents = "";
    private JSONArray candidateItems = new JSONArray();
    private int shown;

    CandidateStrip(Context context, KeyboardView.Actions actions, Runnable toggle) {
        super(context);
        this.actions = actions; style = new KeyboardStyle(context);
        setOrientation(VERTICAL);
        LinearLayout bar = new LinearLayout(context);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        candidateScroll = new HorizontalScrollView(context);
        candidateScroll.setContentDescription("候选列表");
        candidateScroll.setHorizontalScrollBarEnabled(false);
        candidates = new LinearLayout(context);
        candidates.setGravity(Gravity.CENTER_VERTICAL);
        candidateScroll.addView(candidates);
        candidateScroll.setOnScrollChangeListener((view, x, y, oldX, oldY) -> {
            if (x + candidateScroll.getWidth() * 2 >= candidates.getWidth()) { appendCandidates(); }
        });
        candidateScroll.addOnLayoutChangeListener((view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            if (right - left > candidates.getMeasuredWidth()) { appendCandidates(); }
        });
        bar.addView(candidateScroll, new LayoutParams(0, style.dp(style.candidateHeight()), 1));
        expand = style.key("", true, toggle);
        style.toolbar(expand, KeyIcon.EXPAND, false);
        expand.setContentDescription("展开候选");
        expand.setVisibility(INVISIBLE);
        bar.addView(expand, new LayoutParams(style.dp(48), ViewGroup.LayoutParams.MATCH_PARENT));
        addView(bar, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, style.dp(style.candidateHeight())));
        readingScroll = new HorizontalScrollView(context);
        readingScroll.setHorizontalScrollBarEnabled(false);
        readings = new LinearLayout(context);
        readings.setGravity(Gravity.CENTER_VERTICAL);
        readingScroll.addView(readings);
        addView(readingScroll, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, style.dp(style.readingHeight())));
    }

    void expanded(boolean expanded) {
        style.toolbar(expand, expanded ? KeyIcon.COLLAPSE : KeyIcon.EXPAND, false);
        expand.setContentDescription(expanded ? "收回候选" : "展开候选");
    }

    void update(JSONObject snapshot, boolean chinese, boolean nineKey) {
        JSONArray items = snapshot.optJSONArray("candidates");
        boolean hasCandidates = items != null && items.length() > 0;
        String raw = snapshot.optString("raw");
        String contents = hasCandidates ? items.toString() : raw + ":" + chinese;
        if (!contents.equals(candidateContents)) {
            candidateContents = contents;
            candidates.removeAllViews();
            candidateItems = items == null ? new JSONArray() : items;
            shown = 0;
            if (!hasCandidates) {
                candidates.addView(hint(raw.isEmpty() ? (chinese ? "开始输入 · 英译随候选显示" : "English") : raw, 14),
                        new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, style.dp(style.candidateHeight())));
            } else { appendCandidates(); }
            candidateScroll.post(() -> candidateScroll.scrollTo(0, 0));
        }
        expand.setVisibility(hasCandidates ? VISIBLE : INVISIBLE);
        JSONArray options = snapshot.optJSONArray("readings");
        boolean limitReached = raw.length() >= NativeBridge.MAX_INPUT_LENGTH;
        String optionContents = limitReached ? "limit" : chinese + ":" + nineKey + ":" + (options == null ? "" : options.toString());
        if (optionContents.equals(readingContents)) { return; }
        readingContents = optionContents;
        readings.removeAllViews();
        readingScroll.post(() -> readingScroll.scrollTo(0, 0));
        if (limitReached) {
            TextView limit = hint("输入已满 · 请先选词或删除", 13);
            limit.setTextColor(style.accent); limit.setContentDescription("输入长度提示");
            readings.addView(limit);
        } else if (chinese && nineKey && options != null && options.length() > 0) {
            readings.addView(hint("拼音", 12));
            for (int i = 0; i < options.length(); i++) {
                String reading = options.optString(i);
                TextView option = style.key(reading, true, () -> actions.reading(reading));
                option.setTextSize(14); option.setContentDescription("选择拼音 " + reading);
                option.setMinimumWidth(style.dp(48));
                option.setPadding(style.dp(12), 0, style.dp(12), 0);
                LayoutParams cell = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, style.dp(style.readingHeight()));
                cell.setMargins(style.dp(3), 0, style.dp(3), 0);
                readings.addView(option, cell);
            }
        } else {
            readings.addView(hint(chinese ? "左右滑动查看更多候选" : "长按空格切换输入法", 12));
        }
    }

    private void appendCandidates() {
        int end = Math.min(shown + BATCH_SIZE, candidateItems.length());
        while (shown < end) {
            final int chosen = shown++;
            JSONObject item = candidateItems.optJSONObject(chosen);
            if (item == null) { continue; }
            CandidateKey key = new CandidateKey(getContext(), style, item, false, chosen == 0, () -> actions.choose(chosen));
            LayoutParams cell = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, style.dp(style.candidateHeight() - 4));
            cell.setMargins(style.dp(3), style.dp(2), style.dp(3), style.dp(2));
            candidates.addView(key, cell);
        }
    }

    private TextView hint(String text, int size) {
        TextView view = new TextView(getContext());
        view.setText(text); view.setTextColor(style.muted); view.setTextSize(size);
        view.setGravity(Gravity.CENTER_VERTICAL); view.setPadding(style.dp(12), 0, style.dp(8), 0);
        return view;
    }
}
