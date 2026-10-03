// 中文候选与英语释义共用一个点击区域，只由候选编号决定上屏内容。
package io.github.utyoinog.yagamiime;

import android.content.Context;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

final class CandidateKey extends LinearLayout {
    private final KeyboardStyle style;

    CandidateKey(Context context, KeyboardStyle style, JSONObject item, boolean expanded, boolean first, Runnable choose) {
        super(context);
        this.style = style;
        setOrientation(VERTICAL); setGravity(Gravity.CENTER);
        setPadding(style.dp(10), style.dp(3), style.dp(10), style.dp(3));
        setMinimumWidth(style.dp(64));
        setBackground(style.candidateBackground(first, expanded));
        setClickable(true); setFocusable(false);
        String text = item.optString("text"), gloss = item.optString("gloss");
        setContentDescription((expanded ? "展开候选 " : "候选 ") + text);
        setOnClickListener(ignored -> choose.run());
        TextView word = line(text, first ? style.accent : style.text, 19);
        word.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        addView(word, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        TextView english = line(gloss.isEmpty() ? "暂无释义" : gloss, style.muted, 11);
        english.setContentDescription((expanded ? "展开释义 " : "候选释义 ") + text);
        LayoutParams annotation = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        annotation.topMargin = style.dp(3);
        addView(english, annotation);
        setOnLongClickListener(ignored -> {
            Toast.makeText(context, text + "：" + (gloss.isEmpty() ? "暂无英语释义" : gloss), Toast.LENGTH_LONG).show();
            return true;
        });
    }

    private TextView line(String text, int color, int size) {
        TextView view = new TextView(getContext());
        view.setText(text); view.setTextColor(color); view.setTextSize(size);
        view.setGravity(Gravity.CENTER); view.setIncludeFontPadding(false);
        view.setSingleLine(true); view.setEllipsize(TextUtils.TruncateAt.END);
        return view;
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        if (MeasureSpec.getMode(widthSpec) != MeasureSpec.EXACTLY) {
            int limit = style.dp(132);
            if (MeasureSpec.getMode(widthSpec) == MeasureSpec.AT_MOST) { limit = Math.min(limit, MeasureSpec.getSize(widthSpec)); }
            widthSpec = MeasureSpec.makeMeasureSpec(limit, MeasureSpec.AT_MOST);
        }
        super.onMeasure(widthSpec, heightSpec);
    }
}
