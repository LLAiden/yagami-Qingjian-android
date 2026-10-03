// 移动键盘布局与面板；所有编辑和引擎操作通过回调交给输入法服务。
package io.github.utyoinog.yagamiime;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

final class KeyboardView extends LinearLayout {
    interface Actions {
        void character(char letter);
        void text(String text);
        void delete();
        void space();
        void enter();
        void hide();
        void mode();
        void scheme();
        void choose(int index);
        void reading(String reading);
        void clipboard();
        void selectAll();
        void nextIme();
    }

    private final KeyboardStyle style;
    private final Actions actions;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final LinearLayout candidates;
    private final LinearLayout readings;
    private final HorizontalScrollView readingScroll;
    private final LinearLayout body;
    private final TextView schemeKey;
    private final TextView clipboardKey;
    private final int rowHeight;
    private Runnable repeat;
    private boolean repeated;
    private boolean chinese = true;
    private boolean nineKey = true;
    private boolean shifted;
    private int page;
    private String enterLabel = "换行";

    KeyboardView(Context context, Actions actions) {
        super(context);
        this.actions = actions;
        style = new KeyboardStyle(context);
        rowHeight = context.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE ? 40 : 57;
        setOrientation(VERTICAL);
        setBackgroundColor(KeyboardStyle.BACKGROUND);
        setPadding(style.dp(3), style.dp(2), style.dp(3), style.dp(4));
        LinearLayout toolbar = row();
        schemeKey = add(toolbar, "九键", true, actions::scheme, 1);
        clipboardKey = add(toolbar, "剪贴板", true, actions::clipboard, 1.3f);
        add(toolbar, "全选", true, actions::selectAll, 1);
        TextView hide = add(toolbar, "⌄", true, actions::hide, 0.8f);
        hide.setContentDescription("收起键盘");
        addView(toolbar, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, style.dp(40)));
        HorizontalScrollView candidateScroll = new HorizontalScrollView(context);
        candidateScroll.setHorizontalScrollBarEnabled(false);
        candidates = row();
        candidateScroll.addView(candidates);
        addView(candidateScroll, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, style.dp(48)));
        readingScroll = new HorizontalScrollView(context);
        readingScroll.setHorizontalScrollBarEnabled(false);
        readings = row();
        readingScroll.addView(readings);
        addView(readingScroll, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, style.dp(32)));
        body = new LinearLayout(context);
        body.setOrientation(VERTICAL);
        addView(body, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, style.dp(rowHeight * 4)));
        showLetters();
    }

    void configure(boolean chinese, boolean nineKey, String enterLabel, boolean privateEditor) {
        this.chinese = chinese; this.nineKey = nineKey; this.enterLabel = enterLabel;
        schemeKey.setText(nineKey ? "九键" : "全拼");
        schemeKey.setContentDescription(nineKey ? "九键" : "全拼");
        schemeKey.setVisibility(chinese ? VISIBLE : INVISIBLE);
        clipboardKey.setText(privateEditor ? "粘贴" : "剪贴板");
        clipboardKey.setContentDescription(privateEditor ? "粘贴" : "剪贴板");
        showLetters();
    }

    void showLetters() {
        reset(0);
        if (chinese && nineKey) { nineLetters(); }
        else { alphabet(); }
    }

    void showNumbers(boolean phone, boolean signed, boolean decimal) {
        reset(1);
        for (int rowIndex = 0; rowIndex < 3; rowIndex++) {
            LinearLayout row = row();
            for (int col = 0; col < 3; col++) {
                String digit = Integer.toString(rowIndex * 3 + col + 1);
                add(row, digit, false, () -> actions.text(digit), 1);
            }
            if (rowIndex == 0) { addDelete(row); }
            if (rowIndex == 1) { add(row, "符号", true, this::showSymbols, 0.7f); }
            if (rowIndex == 2) { addEnter(row); }
            addRow(row);
        }
        LinearLayout bottom = row();
        add(bottom, chinese ? "拼音" : "ABC", true, this::showLetters, 1);
        String mark = phone ? "*" : signed ? "−" : "空格";
        add(bottom, mark, true, () -> {
            if (mark.equals("空格")) { actions.space(); }
            else { actions.text(mark.equals("−") ? "-" : mark); }
        }, 1);
        add(bottom, "0", false, () -> actions.text("0"), 1);
        add(bottom, decimal || phone ? (phone ? "#" : ".") : "空格", true, () -> {
            if (phone) { actions.text("#"); }
            else if (decimal) { actions.text("."); }
            else { actions.space(); }
        }, 0.7f);
        addRow(bottom);
    }

    void showSymbols() { symbols(0); }

    private void symbols(int set) {
        reset(2);
        String[][][] sets = {
                {{"，", "。", "？", "！", "、", "："}, {"（", "）", "《", "》", "“", "”"}, {"@", "#", "/", "_", "-", "+"}},
                {{".", ",", "?", "!", "'", "\""}, {"(", ")", "[", "]", "{", "}"}, {":", ";", "$", "&", "\\", "|"}},
                {{"=", "%", "^", "*", "~", "`"}, {"<", ">", "€", "£", "¥", "•"}, {"…", "·", "—", "±", "×", "÷"}}
        };
        String[][] marks = sets[set];
        for (int index = 0; index < marks.length; index++) {
            LinearLayout row = row();
            for (String mark : marks[index]) { add(row, mark, false, () -> actions.text(mark), 1); }
            if (index == 0) { addDelete(row); }
            else if (index == 1) { add(row, "更多", true, () -> symbols((set + 1) % sets.length), 1); }
            else { addEnter(row); }
            addRow(row);
        }
        bottom();
    }

    void showClipboard(ClipboardStore store, boolean privateEditor) {
        reset(3);
        body.addView(new ClipboardPanel(getContext(), store, privateEditor, text -> {
            actions.text(text); showLetters();
        }), new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        TextView back = style.key("返回键盘", true, this::showLetters);
        body.addView(back, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, style.dp(36)));
    }

    int page() { return page; }

    void snapshot(JSONObject snapshot) {
        candidates.removeAllViews();
        JSONArray items = snapshot.optJSONArray("candidates");
        if (items == null || items.length() == 0) {
            TextView hint = new TextView(getContext());
            hint.setText(snapshot.optString("raw").isEmpty() ? (chinese ? (nineKey ? "九键拼音 · 点击候选上屏" : "全拼 · 点击候选上屏") : "English") : snapshot.optString("raw"));
            hint.setTextSize(14);
            hint.setGravity(Gravity.CENTER_VERTICAL);
            hint.setPadding(style.dp(12), 0, 0, 0);
            candidates.addView(hint, new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, style.dp(48)));
        } else {
            for (int index = 0; index < items.length(); index++) {
                JSONObject item = items.optJSONObject(index);
                if (item == null) { continue; }
                final int chosen = index;
                String text = item.optString("text");
                TextView candidate = style.key(text, false, () -> actions.choose(chosen));
                candidate.setContentDescription("候选 " + text);
                String gloss = item.optString("gloss");
                if (!gloss.isEmpty()) {
                    candidate.setOnLongClickListener(ignored -> {
                        Toast.makeText(getContext(), text + "：" + gloss, Toast.LENGTH_LONG).show();
                        return true;
                    });
                }
                candidate.setPadding(style.dp(14), 0, style.dp(14), 0);
                LayoutParams params = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, style.dp(44));
                params.setMargins(style.dp(2), style.dp(2), style.dp(2), style.dp(2));
                candidates.addView(candidate, params);
            }
        }
        readings.removeAllViews();
        JSONArray options = snapshot.optJSONArray("readings");
        boolean visible = chinese && nineKey && options != null && options.length() > 0;
        // 输入中保持窗口高度不变，避免系统重布局时按键位置跳动。
        readingScroll.setVisibility(VISIBLE);
        if (visible) {
            TextView label = new TextView(getContext());
            label.setText("选拼音 ");
            readings.addView(label);
            for (int i = 0; i < options.length(); i++) {
                String reading = options.optString(i);
                TextView option = style.key(reading, true, () -> actions.reading(reading));
                option.setTextSize(14);
                option.setContentDescription("选择拼音 " + reading);
                option.setPadding(style.dp(10), 0, style.dp(10), 0);
                readings.addView(option, new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, style.dp(30)));
            }
        } else {
            TextView hint = new TextView(getContext());
            hint.setText(chinese && nineKey ? "按 2–9 输入拼音 · 长按候选查看译词" : "长按空格切换输入法");
            hint.setTextSize(12);
            hint.setPadding(style.dp(12), 0, 0, 0);
            readings.addView(hint);
        }
    }

    void cancelRepeat() {
        if (repeat != null) { handler.removeCallbacks(repeat); }
        repeated = false;
    }

    @Override protected void onDetachedFromWindow() { cancelRepeat(); super.onDetachedFromWindow(); }

    private void nineLetters() {
        String[][] labels = {{"符号", "ABC", "DEF"}, {"GHI", "JKL", "MNO"}, {"PQRS", "TUV", "WXYZ"}};
        for (int r = 0; r < 3; r++) {
            LinearLayout row = row();
            for (int c = 0; c < 3; c++) {
                int digit = r * 3 + c + 1;
                if (digit == 1) { add(row, "符号", true, this::showSymbols, 1); }
                else {
                    TextView key = add(row, digit + "\n" + labels[r][c], false, () -> actions.character((char) ('0' + digit)), 1);
                    key.setTextSize(17);
                    key.setContentDescription("拼音键 " + digit + " " + labels[r][c]);
                }
            }
            if (r == 0) { addDelete(row); }
            else if (r == 1) { add(row, "123", true, () -> showNumbers(false, false, false), 0.7f); }
            else { addEnter(row); }
            addRow(row);
        }
        bottom();
    }

    private void alphabet() {
        String[] rows = {"qwertyuiop", "asdfghjkl", "zxcvbnm"};
        for (int r = 0; r < rows.length; r++) {
            LinearLayout row = row();
            if (r == 2) { add(row, shifted ? "⇧ ON" : "⇧", true, () -> { shifted = !shifted; showLetters(); }, 1.3f); }
            for (char letter : rows[r].toCharArray()) {
                add(row, String.valueOf(shifted ? Character.toUpperCase(letter) : letter), false, () -> {
                    actions.character(shifted ? Character.toUpperCase(letter) : letter);
                    if (shifted) { shifted = false; showLetters(); }
                }, 1);
            }
            if (r == 2) { addDelete(row); }
            addRow(row);
        }
        bottom();
    }

    private void bottom() {
        LinearLayout row = row();
        add(row, chinese ? "EN" : "中文", true, actions::mode, 1.1f);
        add(row, "123", true, () -> showNumbers(false, false, false), 1);
        add(row, chinese ? "，" : ",", true, () -> actions.text(chinese ? "，" : ","), 0.8f);
        TextView space = add(row, "空格", false, actions::space, 2.5f);
        space.setOnLongClickListener(ignored -> { actions.nextIme(); return true; });
        add(row, chinese ? "。" : ".", true, () -> actions.text(chinese ? "。" : "."), 0.8f);
        if (!chinese || !nineKey || page == 2) { addEnter(row); }
        addRow(row);
    }

    private void addEnter(LinearLayout row) {
        TextView enter = add(row, enterLabel, true, actions::enter, 0.7f);
        enter.setTextColor(KeyboardStyle.ACCENT);
        enter.setTextSize(15);
    }

    @SuppressLint("ClickableViewAccessibility")
    private void addDelete(LinearLayout row) {
        TextView delete = add(row, "⌫", true, actions::delete, 0.7f);
        delete.setContentDescription("删除文字");
        delete.setOnTouchListener((view, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    cancelRepeat(); view.setPressed(true);
                    repeat = new Runnable() {
                        @Override public void run() {
                            repeated = true; actions.delete(); handler.postDelayed(this, 65);
                        }
                    };
                    handler.postDelayed(repeat, ViewConfiguration.getLongPressTimeout());
                    return true;
                case MotionEvent.ACTION_UP:
                    boolean wasRepeated = repeated;
                    cancelRepeat(); view.setPressed(false);
                    if (!wasRepeated) { view.performClick(); }
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    cancelRepeat(); view.setPressed(false); return true;
                default: return true;
            }
        });
    }

    private void reset(int page) {
        cancelRepeat(); this.page = page; body.removeAllViews();
    }

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(HORIZONTAL); row.setGravity(Gravity.CENTER_VERTICAL);
        return row;
    }

    private TextView add(LinearLayout row, String label, boolean special, Runnable action, float weight) {
        TextView key = style.key(label, special, action);
        key.setTextSize(label.length() > 2 ? 14 : 19);
        LayoutParams params = new LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, weight);
        params.setMargins(style.dp(3), style.dp(3), style.dp(3), style.dp(3));
        row.addView(key, params);
        return key;
    }

    private void addRow(LinearLayout row) {
        body.addView(row, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
    }
}
