// 输入法服务协调宿主编辑器、串行引擎队列和键盘视图。
package io.github.utyoinog.yagamiime;

import android.content.ClipboardManager;
import android.inputmethodservice.InputMethodService;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class YagamiInputMethodService extends InputMethodService implements KeyboardView.Actions {
    private interface Command { String run(NativeBridge bridge); }

    private final ExecutorService engineQueue = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private NativeBridge bridge;
    private KeyboardView keyboard;
    private ClipboardStore clipboard;
    private final ClipboardManager.OnPrimaryClipChangedListener clipListener = this::captureClipboard;
    private boolean chinese = true;
    private boolean nineKey = true;
    private boolean privateEditor;
    private boolean selected;
    private boolean composing;
    private boolean visible;
    private boolean destroyed;
    private int session;
    private long sequence;
    private int renderedSession;
    private long renderedSequence;
    private JSONObject snapshot = new JSONObject();

    @Override public void onCreate() {
        super.onCreate();
        nineKey = getSharedPreferences("keyboard", MODE_PRIVATE).getBoolean("nine_key", true);
        clipboard = new ClipboardStore(this);
        clipboard.manager().addPrimaryClipChangedListener(clipListener);
        engineQueue.execute(() -> {
            try {
                bridge = new NativeBridge(copyAsset("dict.tsv").getAbsolutePath(), copyAsset("glossary-en.tsv").getAbsolutePath());
            } catch (Exception | LinkageError error) {
                main.post(() -> Toast.makeText(this, "词库加载失败，请重新打开输入法", Toast.LENGTH_LONG).show());
            }
        });
    }

    @Override public View onCreateInputView() {
        keyboard = new KeyboardView(this, this);
        applyInsets();
        configure();
        keyboard.snapshot(snapshot);
        return keyboard;
    }

    @Override public boolean onEvaluateFullscreenMode() { return false; }

    @Override public void onStartInput(EditorInfo info, boolean restarting) {
        super.onStartInput(info, restarting);
        if (restarting && composing) { return; }
        session++;
        selected = info.initialSelStart != info.initialSelEnd;
        composing = false;
        privateEditor = isPrivate(info);
        int variation = info.inputType & InputType.TYPE_MASK_VARIATION;
        chinese = getSharedPreferences("keyboard", MODE_PRIVATE).getBoolean("chinese", true)
                && !privateEditor && variation != InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                && variation != InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS
                && variation != InputType.TYPE_TEXT_VARIATION_URI;
        final boolean target = chinese && nineKey;
        enqueue(engine -> { engine.clear(); engine.setNineKey(target); return null; });
    }

    @Override public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        visible = true;
        configure();
        captureClipboard();
    }

    @Override public void onFinishInputView(boolean finishingInput) {
        visible = false;
        if (keyboard != null) { keyboard.cancelRepeat(); }
        // 系统已在关闭或重建窗口，不再次请求隐藏，避免导航模式切换后把新窗口收起。
        // 基类会立即结束预编辑，异步转换随后只能追加文字；由 finishView 完成提交和结束。
        if (!finishingInput) { finishView(false); }
    }

    @Override public void onFinishInput() {
        session++;
        if (keyboard != null) { keyboard.cancelRepeat(); }
        InputConnection connection = getCurrentInputConnection();
        if (connection != null) { connection.finishComposingText(); }
        composing = false;
        selected = false;
        snapshot = new JSONObject();
        enqueue(engine -> { engine.clear(); return null; });
        super.onFinishInput();
    }

    @Override public void onUpdateSelection(int oldStart, int oldEnd, int newStart, int newEnd, int composingStart, int composingEnd) {
        super.onUpdateSelection(oldStart, oldEnd, newStart, newEnd, composingStart, composingEnd);
        selected = newStart != newEnd;
        if (composing && (selected || composingEnd < 0 || newEnd != composingEnd)) {
            // 用户移动了光标或选择文字：结束旧组句，后续输入不能覆盖原来的位置。
            composing = false;
            InputConnection connection = getCurrentInputConnection();
            if (connection != null) { connection.finishComposingText(); }
            enqueue(engine -> { engine.clear(); return null; });
        }
    }

    @Override public void onDestroy() {
        destroyed = true;
        clipboard.manager().removePrimaryClipChangedListener(clipListener);
        if (keyboard != null) { keyboard.cancelRepeat(); }
        main.removeCallbacksAndMessages(null);
        engineQueue.execute(() -> { if (bridge != null) { bridge.close(); bridge = null; } });
        engineQueue.shutdown();
        super.onDestroy();
    }

    @Override public void character(char character) {
        if (chinese && (Character.isLowerCase(character) || character >= '2' && character <= '9')) {
            enqueue(engine -> { engine.push(character); return null; });
        } else {
            text(String.valueOf(character));
        }
    }

    @Override public void text(String text) {
        enqueue(engine -> { String prefix = finish(engine); return prefix + text; });
    }

    @Override public void delete() {
        InputConnection connection = getCurrentInputConnection();
        if (connection == null) { return; }
        if (EditorController.deleteSelection(connection, selected)) {
            selected = false; composing = false;
            enqueue(engine -> { engine.clear(); return null; });
            return;
        }
        final int currentSession = session;
        final long operation = ++sequence;
        engineQueue.execute(() -> {
            if (bridge == null) { return; }
            boolean removed = bridge.backspace();
            JSONObject state = state();
            main.post(() -> {
                if (destroyed || currentSession != session) { return; }
                if (!removed) { EditorController.backspace(connection); }
                apply(state, null, connection, currentSession, operation);
            });
        });
    }

    @Override public void space() {
        enqueue(engine -> {
            String state = engine.snapshot();
            try {
                if (new JSONObject(state).optString("raw").isEmpty()) { return " "; }
            } catch (Exception ignored) { return " "; }
            String text = engine.commit(0);
            return text.isEmpty() ? engine.takeRaw() : text;
        });
    }

    @Override public void enter() {
        final EditorInfo info = getCurrentInputEditorInfo();
        final InputConnection connection = getCurrentInputConnection();
        if (connection == null || info == null) { return; }
        final int currentSession = session;
        enqueue(engine -> {
            String committed = finish(engine);
            main.post(() -> {
                if (destroyed || currentSession != session) { return; }
                // 提交文本与动作在同一个主线程事务里完成，发送不能抢在文字上屏之前。
                if (!committed.isEmpty()) { connection.commitText(committed, 1); }
                connection.finishComposingText();
                int action = info.imeOptions & EditorInfo.IME_MASK_ACTION;
                if (multiline(info) || action == EditorInfo.IME_ACTION_NONE || action == EditorInfo.IME_ACTION_UNSPECIFIED) {
                    connection.commitText("\n", 1);
                } else { connection.performEditorAction(action); }
            });
            return null;
        });
    }

    @Override public void hide() {
        finishView(true);
    }

    private void finishView(boolean requestHide) {
        final InputConnection connection = getCurrentInputConnection();
        final int currentSession = session;
        enqueue(engine -> {
            String committed = finish(engine);
            main.post(() -> {
                if (destroyed || currentSession != session) { return; }
                if (connection != null) {
                    if (!committed.isEmpty()) { connection.commitText(committed, 1); }
                    connection.finishComposingText();
                }
                composing = false;
                if (requestHide) { requestHideSelf(0); }
            });
            return null;
        });
    }

    @Override public void mode() {
        chinese = !chinese;
        getSharedPreferences("keyboard", MODE_PRIVATE).edit().putBoolean("chinese", chinese).apply();
        boolean target = chinese && nineKey;
        enqueue(engine -> { String text = finish(engine); engine.setNineKey(target); return text.isEmpty() ? null : text; });
        configure();
    }

    @Override public void scheme() {
        nineKey = !nineKey;
        getSharedPreferences("keyboard", MODE_PRIVATE).edit().putBoolean("nine_key", nineKey).apply();
        boolean target = chinese && nineKey;
        enqueue(engine -> { String text = finish(engine); engine.setNineKey(target); return text.isEmpty() ? null : text; });
        configure();
    }

    @Override public void choose(int index) {
        if (renderedSequence != sequence || renderedSession != session) { return; }
        enqueue(engine -> engine.commit(index));
    }

    @Override public void reading(String reading) {
        enqueue(engine -> { engine.lockReading(reading); return null; });
    }

    @Override public void clipboard() {
        captureClipboard();
        if (keyboard != null) { keyboard.showClipboard(clipboard, privateEditor); }
    }

    @Override public void selectAll() {
        final InputConnection connection = getCurrentInputConnection();
        final int currentSession = session;
        enqueue(engine -> {
            String committed = finish(engine);
            main.post(() -> {
                if (destroyed || currentSession != session || connection == null) { return; }
                if (!committed.isEmpty()) { connection.commitText(committed, 1); }
                connection.finishComposingText();
                composing = false;
                connection.performContextMenuAction(android.R.id.selectAll);
            });
            return null;
        });
    }

    @Override public void nextIme() {
        InputMethodManager manager = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (manager != null) { manager.showInputMethodPicker(); }
    }

    private void enqueue(Command command) {
        if (destroyed) { return; }
        final int currentSession = session;
        final long operation = ++sequence;
        final InputConnection connection = getCurrentInputConnection();
        engineQueue.execute(() -> {
            if (bridge == null) { return; }
            String committed = command.run(bridge);
            JSONObject state = state();
            main.post(() -> {
                if (!destroyed && currentSession == session) { apply(state, committed, connection, currentSession, operation); }
            });
        });
    }

    private JSONObject state() {
        try { return new JSONObject(bridge.snapshot()); }
        catch (Exception ignored) { return new JSONObject(); }
    }

    private void apply(JSONObject state, String committed, InputConnection connection, int inputSession, long operation) {
        if (connection != null && committed != null && !committed.isEmpty()) { connection.commitText(committed, 1); }
        if (operation != sequence) { return; }
        snapshot = state;
        renderedSequence = operation;
        renderedSession = inputSession;
        if (connection != null) {
            connection.beginBatchEdit();
            try {
                String preedit = state.optString("preedit");
                if (!state.optString("raw").isEmpty()) {
                    composing = true;
                    connection.setComposingText(preedit, 1);
                } else {
                    if (composing && committed == null) { connection.setComposingText("", 1); }
                    composing = false;
                    connection.finishComposingText();
                }
            } finally { connection.endBatchEdit(); }
        }
        if (keyboard != null) { keyboard.snapshot(state); }
    }

    private String finish(NativeBridge engine) {
        StringBuilder text = new StringBuilder();
        for (int attempt = 0; attempt < 64; attempt++) {
            try { if (new JSONObject(engine.snapshot()).optString("raw").isEmpty()) { break; } }
            catch (Exception ignored) { break; }
            String part = engine.commit(0);
            if (part.isEmpty()) { part = engine.takeRaw(); }
            text.append(part);
        }
        return text.toString();
    }

    private void configure() {
        if (keyboard == null) { return; }
        EditorInfo info = getCurrentInputEditorInfo();
        keyboard.configure(chinese, nineKey, enterLabel(info), privateEditor);
        if (info != null) {
            int type = info.inputType & InputType.TYPE_MASK_CLASS;
            if (type == InputType.TYPE_CLASS_NUMBER || type == InputType.TYPE_CLASS_PHONE || type == InputType.TYPE_CLASS_DATETIME) {
                keyboard.showNumbers(type == InputType.TYPE_CLASS_PHONE,
                        (info.inputType & InputType.TYPE_NUMBER_FLAG_SIGNED) != 0,
                        (info.inputType & InputType.TYPE_NUMBER_FLAG_DECIMAL) != 0);
            }
        }
    }

    private void captureClipboard() {
        if (!visible || destroyed) { return; }
        clipboard.capture(privateEditor);
        if (keyboard != null && keyboard.page() == 3) { keyboard.showClipboard(clipboard, privateEditor); }
    }

    private void applyInsets() {
        Window window = getWindow().getWindow();
        if (window != null) {
            window.setNavigationBarColor(KeyboardStyle.BACKGROUND);
            window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
            if (Build.VERSION.SDK_INT >= 29) { window.setNavigationBarContrastEnforced(false); }
            if (Build.VERSION.SDK_INT >= 30) { window.setDecorFitsSystemWindows(false); }
        }
        KeyboardStyle style = new KeyboardStyle(this);
        keyboard.setOnApplyWindowInsetsListener((view, insets) -> {
            int left, right, bottom;
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets safe = insets.getInsets(WindowInsets.Type.navigationBars() | WindowInsets.Type.displayCutout());
                left = safe.left; right = safe.right; bottom = safe.bottom;
            } else {
                left = insets.getSystemWindowInsetLeft(); right = insets.getSystemWindowInsetRight(); bottom = insets.getSystemWindowInsetBottom();
            }
            view.setPadding(style.dp(3) + left, style.dp(2), style.dp(3) + right, style.dp(4) + bottom);
            return insets;
        });
        keyboard.requestApplyInsets();
    }

    private File copyAsset(String name) throws Exception {
        File file = new File(getFilesDir(), name);
        String version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        String key = "asset_" + name;
        if (file.isFile() && version.equals(getSharedPreferences("assets", MODE_PRIVATE).getString(key, ""))) { return file; }
        File temp = new File(getFilesDir(), name + ".tmp");
        try (InputStream source = getAssets().open(name); FileOutputStream target = new FileOutputStream(temp)) {
            byte[] buffer = new byte[65536];
            int count;
            while ((count = source.read(buffer)) != -1) { target.write(buffer, 0, count); }
        }
        if (!temp.renameTo(file)) { throw new IllegalStateException("无法安装词库"); }
        getSharedPreferences("assets", MODE_PRIVATE).edit().putString(key, version).apply();
        return file;
    }

    private static boolean isPrivate(EditorInfo info) {
        int type = info.inputType & InputType.TYPE_MASK_CLASS;
        int variation = info.inputType & InputType.TYPE_MASK_VARIATION;
        return (info.imeOptions & EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) != 0
                || type == InputType.TYPE_CLASS_NUMBER && variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD
                || type == InputType.TYPE_CLASS_TEXT && (variation == InputType.TYPE_TEXT_VARIATION_PASSWORD
                    || variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD || variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD);
    }

    private static boolean multiline(EditorInfo info) {
        return info != null && (info.inputType & InputType.TYPE_MASK_CLASS) == InputType.TYPE_CLASS_TEXT
                && ((info.inputType & (InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_IME_MULTI_LINE)) != 0
                    || (info.imeOptions & EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0);
    }

    private static String enterLabel(EditorInfo info) {
        if (info == null || multiline(info)) { return "换行"; }
        switch (info.imeOptions & EditorInfo.IME_MASK_ACTION) {
            case EditorInfo.IME_ACTION_SEND: return "发送";
            case EditorInfo.IME_ACTION_SEARCH: return "搜索";
            case EditorInfo.IME_ACTION_GO: return "前往";
            case EditorInfo.IME_ACTION_NEXT: return "下一项";
            case EditorInfo.IME_ACTION_DONE: return "完成";
            default: return "换行";
        }
    }
}
