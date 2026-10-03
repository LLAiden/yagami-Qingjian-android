// 固定高度的候选与英语释义；拼音消歧由九键侧栏承载。
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
    private final HorizontalScrollView candidateScroll;
    private final TextView expand;
    private String candidateContents = "";
    private JSONArray candidateItems = new JSONArray();
    private int shown;

    CandidateStrip(Context context, KeyboardView.Actions actions, Runnable toggle, Runnable hide) {
        super(context);
        this.actions = actions; style = new KeyboardStyle(context);
        setOrientation(VERTICAL);
        setContentDescription("候选区域");
        setVisibility(GONE);
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
        style.toolbar(expand, KeyIcon.MORE, false);
        expand.setContentDescription("展开候选");
        expand.setTooltipText("展开候选");
        expand.setVisibility(INVISIBLE);
        bar.addView(expand, new LayoutParams(style.dp(40), ViewGroup.LayoutParams.MATCH_PARENT));
        TextView hideKey = style.key("", true, hide);
        style.circleToolbar(hideKey, KeyIcon.HIDE);
        hideKey.setContentDescription("收起键盘"); hideKey.setTooltipText("收起键盘");
        bar.addView(hideKey, new LayoutParams(style.dp(48), style.dp(style.toolbarHeight())));
        addView(bar, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, style.dp(style.candidateHeight())));
    }

    void expanded(boolean expanded) {
        style.toolbar(expand, expanded ? KeyIcon.COLLAPSE : KeyIcon.MORE, false);
        expand.setContentDescription(expanded ? "收回候选" : "展开候选");
    }

    void update(JSONObject snapshot, boolean active) {
        String raw = snapshot.optString("raw");
        if (!active || raw.isEmpty()) {
            setVisibility(GONE);
            if (!candidateContents.isEmpty()) {
                candidateContents = "";
                candidateItems = new JSONArray(); shown = 0;
                candidates.removeAllViews();
            }
            return;
        }
        setVisibility(VISIBLE);
        JSONArray items = snapshot.optJSONArray("candidates");
        boolean hasCandidates = items != null && items.length() > 0;
        String contents = hasCandidates ? items.toString() : raw;
        if (!contents.equals(candidateContents)) {
            candidateContents = contents;
            candidates.removeAllViews();
            candidateItems = items == null ? new JSONArray() : items;
            shown = 0;
            if (!hasCandidates) {
                candidates.addView(hint(raw, 14),
                        new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, style.dp(style.candidateHeight())));
            } else { appendCandidates(); }
            candidateScroll.post(() -> candidateScroll.scrollTo(0, 0));
        }
        expand.setVisibility(hasCandidates ? VISIBLE : INVISIBLE);
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
