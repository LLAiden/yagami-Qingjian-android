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
import android.widget.FrameLayout;
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
        void passwordManager();
        void authenticator();
        void selectAll();
        void nextIme();
        void editorAction(EditorCommand command);
        void setting(String name, int value);
    }

    private final KeyboardStyle style;
    private final Actions actions;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final CandidateStrip strip;
    private final FrameLayout header;
    private final LinearLayout toolbar;
    private final ReadingPanel readingPanel;
    private final Paint backdrop = new Paint();
    private final LinearLayout body;
    private final TextView clipboardKey;
    private final int rowHeight;
    private final int preferenceSignature;
    private Runnable repeat;
    private boolean repeated;
    private boolean chinese = true;
    private LetterCase letterCase = LetterCase.LOWER;
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
        header = new FrameLayout(context);
        header.setContentDescription("键盘顶栏");
        addView(header, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, style.dp(style.candidateHeight())));
        strip = new CandidateStrip(context, actions, () -> {
            if (page == 4) { showTypingPage(); }
            else if (candidateItems.length() > 0) { showCandidatePanel(); }
        }, actions::hide);
        header.addView(strip, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        toolbar = menu("工具栏");
        toolbar(toolbar, "全选", KeyIcon.SELECT, actions::selectAll);
        toolbar(toolbar, "编辑", KeyIcon.EDIT, this::showEditing);
        toolbar(toolbar, "双因素验证", KeyIcon.AUTHENTICATOR, actions::authenticator).setTooltipText("Google 验证器");
        toolbar(toolbar, "密码管理器", KeyIcon.PASSWORD, actions::passwordManager).setTooltipText("Bitwarden");
        clipboardKey = toolbar(toolbar, "剪贴板", KeyIcon.CLIPBOARD, actions::clipboard);
        toolbar(toolbar, "收起键盘", KeyIcon.HIDE, actions::hide);
        readingPanel = new ReadingPanel(context, actions, rowHeight);
        body = new LinearLayout(context);
        body.setOrientation(VERTICAL);
        body.setContentDescription("按键区域");
        addView(body, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, style.dp(rowHeight * 4)));
        showLetters();
    }

    @Override protected void onMeasure(int width, int height) {
        // 顶栏只换内容，候选与工具共享高度，输入时窗口和按键都不移动。
        int fixed = style.dp(style.candidateHeight() + rowHeight * 4)
                + getPaddingTop() + getPaddingBottom();
        super.onMeasure(width, MeasureSpec.makeMeasureSpec(fixed, MeasureSpec.EXACTLY));
    }

    @Override protected void onDraw(Canvas canvas) {
        canvas.drawRect(0, visibleTop(), getWidth(), getHeight(), backdrop);
        super.onDraw(canvas);
    }

    int visibleTop() { return 0; }

    void invalidateConfiguration() { configured = false; }

    void configure(boolean chinese, String enterLabel, boolean privateEditor, int inputType) {
        if (configured && this.chinese == chinese && this.enterLabel.equals(enterLabel)
                && configuredPrivate == privateEditor && configuredType == inputType) { return; }
        configured = true; configuredPrivate = privateEditor; configuredType = inputType;
        this.chinese = chinese; this.enterLabel = enterLabel;
        letterCase = LetterCase.LOWER;
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
            String left = r == 0 ? "+" : r == 1 ? phone ? "*" : "-" : ".";
            add(row, left, true, () -> actions.text(left), SIDE);
            for (int c = 0; c < 3; c++) {
                String digit = Integer.toString(r * 3 + c + 1);
                digit(row, digit, "", () -> actions.text(digit), 1);
            }
            if (r == 0) { addDelete(row, SIDE); }
            else if (r == 1) { function(row, "符号", KeyIcon.SYMBOLS, this::showSymbols, SIDE); }
            else {
                String mark = phone ? "#" : "/";
                add(row, mark, true, () -> actions.text(mark), SIDE);
            }
            addRow(row);
        }
        LinearLayout bottom = row();
        TextView space = add(bottom, "空格", false, actions::space, SIDE);
        space.setOnLongClickListener(ignored -> { actions.nextIme(); return true; });
        TextView english = add(bottom, "EN", true, () -> {
            if (chinese) { actions.mode(); } else { showLetters(); }
        }, 1);
        english.setContentDescription("切换英语模式");
        digit(bottom, "0", "", () -> actions.text("0"), 1);
        TextView pinyin = add(bottom, "拼音", true, () -> {
            if (chinese) { showLetters(); } else { actions.mode(); }
        }, 1);
        pinyin.setContentDescription("切换拼音模式");
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
            else if (index == 1) { add(row, set == 0 ? "下一页" : "上一页", true, () -> symbols((set + 1) % sets.length), 1); }
            else {
                String extra = set == 0 ? ":" : "?";
                add(row, extra, false, () -> actions.text(extra), 1.3f);
            }
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
        candidatePanel = new CandidatePanel(getContext(), candidateItems, index -> {
            actions.choose(index);
            showTypingPage();
        });
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
        updateHeader();
        readingPanel.update(snapshot);
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
        LinearLayout upper = row();
        if (readingPanel.getParent() instanceof ViewGroup) { ((ViewGroup) readingPanel.getParent()).removeView(readingPanel); }
        upper.addView(readingPanel, new LayoutParams(style.dp(60), ViewGroup.LayoutParams.MATCH_PARENT));
        LinearLayout grid = new LinearLayout(getContext());
        grid.setOrientation(VERTICAL);
        upper.addView(grid, new LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));
        for (int r = 0; r < 3; r++) {
            LinearLayout row = row();
            for (int c = 0; c < 3; c++) {
                int number = r * 3 + c + 1;
                TextView key = digit(row, Integer.toString(number), captions[number - 1],
                        number == 1 ? this::showSymbols : () -> actions.character((char) ('0' + number)), 1);
                key.setContentDescription("拼音键 " + number + " " + captions[number - 1]);
            }
            if (r == 0) { addDelete(row, SIDE); }
            else if (r == 1) { add(row, ",", true, () -> actions.text(","), SIDE); }
            else { add(row, ".", true, () -> actions.text("."), SIDE); }
            grid.addView(row, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        }
        body.addView(upper, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 3));
        LinearLayout row = row();
        function(row, "切换输入法", KeyIcon.GLOBE, actions::nextIme, SIDE);
        TextView numbers = add(row, "123", true, actions::numbers, 1);
        numbers.setContentDescription("切换数字模式");
        TextView space = add(row, "空格", false, actions::space, 1);
        space.setTextSize(15); space.setTextColor(style.muted);
        space.setOnLongClickListener(ignored -> { actions.nextIme(); return true; });
        TextView english = add(row, "EN", true, actions::mode, 1);
        english.setContentDescription("切换英语模式");
        addEnter(row, SIDE);
        addRow(row);
    }

    private void alphabet() {
        String[] rows = {"qwertyuiop", "asdfghjkl", "zxcvbnm"};
        for (int r = 0; r < rows.length; r++) {
            LinearLayout row = row();
            if (r == 1) { row.addView(new View(getContext()), new LayoutParams(0, 1, 0.5f)); }
            if (r == 2) {
                String label = letterCase == LetterCase.LOWER ? "⇧" : letterCase == LetterCase.SINGLE_UPPER ? "⇧ ON" : "⇧ LOCK";
                KeyIcon icon = letterCase == LetterCase.CAPS_LOCK ? KeyIcon.CAPS_LOCK : KeyIcon.SHIFT;
                TextView shift = function(row, label, icon, () -> {
                    letterCase = letterCase == LetterCase.LOWER ? LetterCase.SINGLE_UPPER
                            : letterCase == LetterCase.SINGLE_UPPER ? LetterCase.CAPS_LOCK : LetterCase.LOWER;
                    showLetters();
                }, 1.5f);
                if (android.os.Build.VERSION.SDK_INT >= 30) {
                    shift.setStateDescription(letterCase == LetterCase.LOWER ? "小写"
                            : letterCase == LetterCase.SINGLE_UPPER ? "单次大写" : "大写锁定");
                }
                if (letterCase != LetterCase.LOWER) { style.primary(shift); style.icon(shift, icon, false, 22); }
            }
            for (char letter : rows[r].toCharArray()) {
                add(row, String.valueOf(letterCase == LetterCase.LOWER ? letter : Character.toUpperCase(letter)), false, () -> {
                    actions.character(letterCase == LetterCase.LOWER ? letter : Character.toUpperCase(letter));
                    if (letterCase == LetterCase.SINGLE_UPPER) { letterCase = LetterCase.LOWER; showLetters(); }
                }, 1);
            }
            if (r == 1) { row.addView(new View(getContext()), new LayoutParams(0, 1, 0.5f)); }
            if (r == 2) { addDelete(row, 1.8f); }
            addRow(row);
        }
        LinearLayout bottom = row();
        function(bottom, "符号", KeyIcon.SYMBOLS, this::showSymbols, SIDE);
        add(bottom, ",", true, () -> actions.text(","), 1);
        add(bottom, "123", true, actions::numbers, 1);
        TextView space = add(bottom, "空格", false, actions::space, 3);
        space.setOnLongClickListener(ignored -> { actions.nextIme(); return true; });
        add(bottom, "中文", true, actions::mode, 1);
        add(bottom, ".", true, () -> actions.text("."), 1);
        addEnter(bottom, SIDE);
        addRow(bottom);
    }

    private void bottom() {
        LinearLayout row = row();
        add(row, typingPage == 1 ? "返回数字" : chinese ? "拼音" : "ABC", true, this::showTypingPage, SIDE);
        TextView space = add(row, "空格", false, actions::space, 3);
        space.setOnLongClickListener(ignored -> { actions.nextIme(); return true; });
        addEnter(row, SIDE);
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
        updateHeader();
    }

    private void updateHeader() {
        boolean candidates = chinese && typingPage == 0 && page == 0 && !lastSnapshot.optString("raw").isEmpty();
        strip.update(lastSnapshot, candidates);
        toolbar.setVisibility(candidates ? GONE : VISIBLE);
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
        header.addView(menu, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return menu;
    }

    private TextView toolbar(LinearLayout row, String label, KeyIcon icon, Runnable action) {
        TextView key = style.key(label, true, action);
        style.circleToolbar(key, icon);
        key.setTooltipText(label);
        row.addView(key, new LayoutParams(style.dp(48), style.dp(style.toolbarHeight())));
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
