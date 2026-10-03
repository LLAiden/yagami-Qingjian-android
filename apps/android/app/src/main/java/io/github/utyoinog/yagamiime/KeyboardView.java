// 移动键盘布局与面板；所有编辑和引擎操作通过回调交给输入法服务。
package io.github.utyoinog.yagamiime;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

final class KeyboardView extends LinearLayout {
    private static final float SIDE = 0.65f;
    interface Actions {
        void character(char letter);
        void text(String text);
        void delete();
        void space();
        void enter();
        void hide();
        void mode();
        void numbers();
        void choose(int index);
        void reading(String reading);
        void clipboard();
        void selectAll();
        void nextIme();
        void editorAction(EditorCommand command);
        void setting(String name, int value);
    }

    private final KeyboardStyle style;
    private final Actions actions;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final CandidateStrip strip;
    private final View topSpace;
    private final Paint backdrop = new Paint();
    private final LinearLayout body;
    private final TextView clipboardKey;
    private final int rowHeight;
    private final int preferenceSignature;
    private Runnable repeat;
    private boolean repeated;
    private boolean chinese = true;
    private boolean shifted;
    private int page;
    private int typingPage;
    private boolean phoneNumbers;
    private boolean signedNumbers;
    private boolean decimalNumbers;
    private JSONArray candidateItems = new JSONArray();
    private CandidatePanel candidatePanel;
    private String enterLabel = "换行";
    private boolean configured;
    private boolean configuredPrivate;
    private int configuredType;
    private JSONObject lastSnapshot = new JSONObject();

    KeyboardView(Context context, Actions actions) {
        super(context);
        this.actions = actions;
        style = new KeyboardStyle(context);
        KeyboardPreferences preferences = new KeyboardPreferences(context);
        rowHeight = preferences.rowHeight();
        preferenceSignature = preferences.signature();
        setOrientation(VERTICAL);
        setContentDescription("输入键盘");
        setBackgroundColor(Color.TRANSPARENT);
        backdrop.setColor(style.background);
        setPadding(style.dp(3), style.dp(2), style.dp(3), style.dp(4));
        topSpace = new View(context);
        topSpace.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        addView(topSpace, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        strip = new CandidateStrip(context, actions, () -> {
            if (page == 4) { showTypingPage(); }
            else if (candidateItems.length() > 0) { showCandidatePanel(); }
        });
        addView(strip, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout clipboardBar = menu("剪贴板栏");
        clipboardKey = toolbar(clipboardBar, "剪贴板", KeyIcon.CLIPBOARD, actions::clipboard);
        LinearLayout editingBar = menu("编辑菜单");
        toolbar(editingBar, "编辑", KeyIcon.EDIT, this::showEditing);
        toolbar(editingBar, "全选", KeyIcon.SELECT, actions::selectAll);
        toolbar(editingBar, "收起键盘", KeyIcon.HIDE, actions::hide);
        body = new LinearLayout(context);
        body.setOrientation(VERTICAL);
        body.setContentDescription("按键区域");
        addView(body, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, style.dp(rowHeight * 4)));
        showLetters();
    }

    @Override protected void onMeasure(int width, int height) {
        // 固定窗口尺寸，透明顶部用 insets 归还宿主；候选显隐不会改变触摸坐标。
        int fixed = style.dp(style.toolbarHeight() * 2 + style.candidateHeight() + style.readingHeight() + rowHeight * 4)
                + getPaddingTop() + getPaddingBottom();
        super.onMeasure(width, MeasureSpec.makeMeasureSpec(fixed, MeasureSpec.EXACTLY));
    }

    @Override protected void onDraw(Canvas canvas) {
        canvas.drawRect(0, visibleTop(), getWidth(), getHeight(), backdrop);
        super.onDraw(canvas);
    }

    int visibleTop() { return topSpace.getBottom(); }

    void invalidateConfiguration() { configured = false; }

    void configure(boolean chinese, String enterLabel, boolean privateEditor, int inputType) {
        if (configured && this.chinese == chinese && this.enterLabel.equals(enterLabel)
                && configuredPrivate == privateEditor && configuredType == inputType) { return; }
        configured = true; configuredPrivate = privateEditor; configuredType = inputType;
        this.chinese = chinese; this.enterLabel = enterLabel;
        shifted = false;
        phoneNumbers = false; signedNumbers = false; decimalNumbers = false;
        clipboardKey.setContentDescription(privateEditor ? "粘贴" : "剪贴板");
        int type = inputType & android.text.InputType.TYPE_MASK_CLASS;
        if (type == android.text.InputType.TYPE_CLASS_NUMBER || type == android.text.InputType.TYPE_CLASS_PHONE
                || type == android.text.InputType.TYPE_CLASS_DATETIME) {
            showNumbers(type == android.text.InputType.TYPE_CLASS_PHONE,
                    (inputType & android.text.InputType.TYPE_NUMBER_FLAG_SIGNED) != 0,
                    (inputType & android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL) != 0);
        } else { showLetters(); }
    }

    void showLetters() {
        typingPage = 0;
        reset(0);
        if (chinese) { nineLetters(); }
        else { alphabet(); }
    }

    void showNumbers(boolean phone, boolean signed, boolean decimal) {
        typingPage = 1;
        phoneNumbers = phone; signedNumbers = signed; decimalNumbers = decimal;
        reset(1);
        for (int r = 0; r < 3; r++) {
            LinearLayout row = row();
            if (r == 0) { add(row, chinese ? "拼音" : "ABC", true, this::showLetters, SIDE); }
            else if (r == 1) { function(row, "符号", KeyIcon.SYMBOLS, this::showSymbols, SIDE); }
            else { add(row, phone ? "*" : signed ? "−" : "空格", true,
                    () -> { if (phone || signed) { actions.text(phone ? "*" : "-"); } else { actions.space(); } }, SIDE); }
            for (int c = 0; c < 3; c++) {
                String digit = Integer.toString(r * 3 + c + 1);
                digit(row, digit, "", () -> actions.text(digit), 1);
            }
            if (r == 0) { addDelete(row, SIDE); }
            else if (r == 1) { function(row, "剪贴板", KeyIcon.CLIPBOARD, actions::clipboard, SIDE); }
            else { function(row, "收起键盘", KeyIcon.HIDE, actions::hide, SIDE); }
            addRow(row);
        }
        LinearLayout bottom = row();
        add(bottom, chinese ? "拼音" : "ABC", true, this::showLetters, SIDE);
        String mark = phone ? "*" : signed ? "−" : "空格";
        add(bottom, mark, true, () -> {
            if (mark.equals("空格")) { actions.space(); }
            else { actions.text(mark.equals("−") ? "-" : mark); }
        }, 1);
        digit(bottom, "0", "", () -> actions.text("0"), 1);
        String last = phone ? "#" : decimal ? "." : "空格";
        add(bottom, last, true, () -> { if (last.equals("空格")) { actions.space(); } else { actions.text(last); } }, 1);
        addEnter(bottom, SIDE);
        addRow(bottom);
    }

    private void showNumbers() { showNumbers(phoneNumbers, signedNumbers, decimalNumbers); }

    private void showTypingPage() {
        if (typingPage == 1) { showNumbers(); }
        else { showLetters(); }
    }

    void showSymbols() { symbols(0); }

    private void symbols(int set) {
        reset(2);
        String[][][] sets = {
                {{".", ",", "?", "!", "'", "\""}, {"(", ")", "[", "]", "{", "}"}, {"@", "#", "/", "_", "-", "+"}},
                {{":", ";", "$", "&", "\\", "|"}, {"=", "%", "^", "*", "~", "`"}, {"<", ">", "-", "+", "@", "#"}}
        };
        String[][] marks = sets[set];
        for (int index = 0; index < marks.length; index++) {
            LinearLayout row = row();
            for (String mark : marks[index]) { add(row, mark, false, () -> actions.text(mark), 1); }
            if (index == 0) { addDelete(row); }
            else if (index == 1) { add(row, "更多", true, () -> symbols((set + 1) % sets.length), 1); }
            else { function(row, "收起键盘", KeyIcon.HIDE, actions::hide, 1.3f); }
            addRow(row);
        }
        bottom();
    }

    void showClipboard(ClipboardStore store, boolean privateEditor) {
        reset(3);
        body.addView(new ClipboardPanel(getContext(), store, privateEditor, text -> {
            actions.text(text); showTypingPage();
        }), new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        TextView back = style.key("返回键盘", true, this::showTypingPage);
        body.addView(back, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, style.dp(36)));
    }

    private void showCandidatePanel() {
        reset(4);
        strip.expanded(true);
        candidatePanel = new CandidatePanel(getContext(), candidateItems, actions::choose);
        body.addView(candidatePanel,
                new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        LinearLayout footer = row();
        add(footer, "返回键盘", true, this::showTypingPage, 3);
        addDelete(footer);
        body.addView(footer, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, style.dp(40)));
    }

    int page() { return page; }

    private void showEditing() {
        reset(5);
        body.addView(new EditingPanel(getContext(), actions::editorAction, this::showTypingPage, this::showSettings),
                new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    void showSettings() {
        reset(6);
        body.addView(new KeyboardSettingsPanel(getContext(), actions::setting),
                new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        TextView back = style.key("返回键盘", true, this::showTypingPage);
        body.addView(back,
                new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, style.dp(40)));
    }

    boolean preferencesChanged() { return preferenceSignature != new KeyboardPreferences(getContext()).signature(); }

    void restoreSettings(KeyboardView previous) {
        typingPage = previous.typingPage;
        phoneNumbers = previous.phoneNumbers;
        signedNumbers = previous.signedNumbers;
        decimalNumbers = previous.decimalNumbers;
        showSettings();
    }

    void snapshot(JSONObject snapshot) {
        lastSnapshot = snapshot;
        JSONArray items = snapshot.optJSONArray("candidates");
        candidateItems = items == null ? new JSONArray() : items;
        strip.update(snapshot, chinese && typingPage == 0);
        if (page == 4) {
            if (candidateItems.length() == 0) { showTypingPage(); }
            else if (candidatePanel != null) { candidatePanel.update(candidateItems); }
        }
    }

    void cancelRepeat() {
        if (repeat != null) { handler.removeCallbacks(repeat); }
        repeated = false;
    }

    @Override protected void onDetachedFromWindow() { cancelRepeat(); super.onDetachedFromWindow(); }

    private void nineLetters() {
        String[] captions = {"符号", "ABC", "DEF", "GHI", "JKL", "MNO", "PQRS", "TUV", "WXYZ"};
        for (int r = 0; r < 3; r++) {
            LinearLayout row = row();
            if (r == 0) { function(row, "符号", KeyIcon.SYMBOLS, this::showSymbols, SIDE); }
            else if (r == 1) { add(row, "EN", true, actions::mode, SIDE); }
            else { add(row, ",", true, () -> actions.text(","), SIDE); }
            for (int c = 0; c < 3; c++) {
                int number = r * 3 + c + 1;
                TextView key = digit(row, Integer.toString(number), captions[number - 1],
                        number == 1 ? this::showSymbols : () -> actions.character((char) ('0' + number)), 1);
                key.setContentDescription("拼音键 " + number + " " + captions[number - 1]);
            }
            if (r == 0) { addDelete(row, SIDE); }
            else if (r == 1) { add(row, "123", true, actions::numbers, SIDE); }
            else { add(row, ".", true, () -> actions.text("."), SIDE); }
            addRow(row);
        }
        LinearLayout row = row();
        function(row, "切换输入法", KeyIcon.GLOBE, actions::nextIme, SIDE);
        TextView space = add(row, "空格", false, actions::space, 3);
        space.setTextSize(15); space.setTextColor(style.muted);
        space.setOnLongClickListener(ignored -> { actions.nextIme(); return true; });
        addEnter(row, SIDE);
        addRow(row);
    }

    private void alphabet() {
        String[] rows = {"qwertyuiop", "asdfghjkl", "zxcvbnm"};
        for (int r = 0; r < rows.length; r++) {
            LinearLayout row = row();
            if (r == 1) { row.addView(new View(getContext()), new LayoutParams(0, 1, 0.5f)); }
            if (r == 2) {
                TextView shift = function(row, shifted ? "⇧ ON" : "⇧", KeyIcon.SHIFT,
                        () -> { shifted = !shifted; showLetters(); }, 1.5f);
                if (shifted) { style.primary(shift); style.icon(shift, KeyIcon.SHIFT, false, 22); }
            }
            for (char letter : rows[r].toCharArray()) {
                add(row, String.valueOf(shifted ? Character.toUpperCase(letter) : letter), false, () -> {
                    actions.character(shifted ? Character.toUpperCase(letter) : letter);
                    if (shifted) { shifted = false; showLetters(); }
                }, 1);
            }
            if (r == 1) { row.addView(new View(getContext()), new LayoutParams(0, 1, 0.5f)); }
            if (r == 2) { addDelete(row, 1.8f); }
            addRow(row);
        }
        bottom();
    }

    private void bottom() {
        LinearLayout row = row();
        if (page == 2) {
            add(row, typingPage == 1 ? "返回数字" : chinese ? "拼音" : "ABC", true, this::showTypingPage, 1.1f);
        } else { add(row, chinese ? "EN" : "中文", true, actions::mode, 1.1f); }
        add(row, "123", true, actions::numbers, 1);
        if (page != 2) { function(row, "符号", KeyIcon.SYMBOLS, this::showSymbols, 0.8f); }
        add(row, ",", true, () -> actions.text(","), 0.8f);
        TextView space = add(row, "空格", false, actions::space, 2.5f);
        space.setOnLongClickListener(ignored -> { actions.nextIme(); return true; });
        add(row, ".", true, () -> actions.text("."), 0.8f);
        addEnter(row);
        addRow(row);
    }

    private void addEnter(LinearLayout row) { addEnter(row, 1.3f); }

    private void addEnter(LinearLayout row, float weight) {
        TextView enter = add(row, enterLabel, true, actions::enter, weight);
        style.primary(enter);
        enter.setTextSize(style.landscape ? 10 : 12);
        enter.setSingleLine(true);
        enter.setEllipsize(android.text.TextUtils.TruncateAt.END);
        style.iconAbove(enter, KeyIcon.ENTER, style.landscape ? 16 : 18);
        enter.setOnLongClickListener(ignored -> {
            android.widget.Toast.makeText(getContext(), enterLabel, android.widget.Toast.LENGTH_SHORT).show(); return true;
        });
    }

    private void addDelete(LinearLayout row) { addDelete(row, 1.3f); }

    @SuppressLint("ClickableViewAccessibility")
    private void addDelete(LinearLayout row, float weight) {
        TextView delete = function(row, "删除文字", KeyIcon.DELETE, actions::delete, weight);
        delete.setContentDescription("删除文字");
        final boolean[] cancelled = {false};
        final int slop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
        delete.setOnTouchListener((view, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    cancelled[0] = false;
                    cancelRepeat(); view.setPressed(true);
                    repeat = new Runnable() {
                        @Override public void run() {
                            repeated = true; actions.delete(); handler.postDelayed(this, 65);
                        }
                    };
                    handler.postDelayed(repeat, ViewConfiguration.getLongPressTimeout());
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (event.getX() < -slop || event.getY() < -slop
                            || event.getX() > view.getWidth() + slop || event.getY() > view.getHeight() + slop) {
                        cancelled[0] = true;
                        cancelRepeat(); view.setPressed(false);
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    boolean wasRepeated = repeated;
                    cancelRepeat(); view.setPressed(false);
                    if (!wasRepeated && !cancelled[0]) { view.performClick(); }
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    cancelRepeat(); view.setPressed(false); return true;
                default: return true;
            }
        });
    }

    private void reset(int page) {
        cancelRepeat(); this.page = page; body.removeAllViews(); candidatePanel = null;
        strip.expanded(false);
        strip.update(lastSnapshot, chinese && typingPage == 0);
    }

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(HORIZONTAL); row.setGravity(Gravity.CENTER_VERTICAL);
        return row;
    }

    private TextView add(LinearLayout row, String label, boolean special, Runnable action, float weight) {
        TextView key = style.key(label, special, action);
        key.setTextSize(label.length() > 2 ? 14 : 19);
        cell(row, key, weight);
        return key;
    }

    private LinearLayout menu(String description) {
        LinearLayout menu = row();
        menu.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        menu.setContentDescription(description);
        addView(menu, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, style.dp(style.toolbarHeight())));
        return menu;
    }

    private TextView toolbar(LinearLayout row, String label, KeyIcon icon, Runnable action) {
        TextView key = style.key(label, true, action);
        style.toolbar(key, icon, false);
        row.addView(key, new LayoutParams(style.dp(48), ViewGroup.LayoutParams.MATCH_PARENT));
        return key;
    }

    private TextView function(LinearLayout row, String description, KeyIcon icon, Runnable action, float weight) {
        TextView key = add(row, description, true, action, weight);
        style.icon(key, icon, false, 22);
        return key;
    }

    private TextView digit(LinearLayout row, String digit, String caption, Runnable action, float weight) {
        TextView key = new DigitKey(getContext(), digit, caption, action);
        cell(row, key, weight);
        return key;
    }

    private void cell(LinearLayout row, View key, float weight) {
        // 两侧功能列固定宽度，保证窄屏可点并让中间九宫格左右对称。
        LayoutParams params = weight == SIDE
                ? new LayoutParams(style.dp(60), ViewGroup.LayoutParams.MATCH_PARENT)
                : new LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, weight);
        // 视觉留白放在背景内侧，整格都接收触摸，避免快速点击落入键间死区。
        key.setBackground(new android.graphics.drawable.InsetDrawable(key.getBackground(), style.dp(2)));
        row.addView(key, params);
    }

    private void addRow(LinearLayout row) {
        body.addView(row, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
    }
}
