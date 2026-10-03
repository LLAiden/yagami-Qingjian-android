// 输入法服务协调宿主编辑器、串行引擎队列和键盘视图。
package io.github.utyoinog.yagamiime;

import android.content.ClipboardManager;
import android.inputmethodservice.InputMethodService;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.TextUtils;
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
import java.util.function.Consumer;

public final class YagamiInputMethodService extends InputMethodService implements KeyboardView.Actions {
    private interface Command { String run(NativeBridge bridge); }

    private final ExecutorService engineQueue = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private NativeBridge bridge;
    private KeyboardView keyboard;
    private ClipboardStore clipboard;
    private final ClipboardManager.OnPrimaryClipChangedListener clipListener = this::captureClipboard;
    private boolean chinese = true;
    private boolean privateEditor;
    private boolean selected;
    private boolean composing;
    private boolean visible;
    private boolean destroyed;
    private int session;
    private volatile long sequence;
    private int renderedSession;
    private long renderedSequence;
    private long lastLimitNotice;
    private int editorInputType;
    private Runnable pendingCompositionEnd;
    private JSONObject snapshot = new JSONObject();

    @Override public void onCreate() {
        super.onCreate();
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

    @Override public void onComputeInsets(Insets insets) {
        super.onComputeInsets(insets);
        if (keyboard == null || !keyboard.isShown()) { return; }
        int[] location = new int[2];
        keyboard.getLocationInWindow(location);
        int top = location[1] + keyboard.visibleTop();
        insets.contentTopInsets = top;
        insets.visibleTopInsets = top;
        insets.touchableInsets = Insets.TOUCHABLE_INSETS_REGION;
        insets.touchableRegion.set(location[0], top, location[0] + keyboard.getWidth(), location[1] + keyboard.getHeight());
    }

    @Override public void onStartInput(EditorInfo info, boolean restarting) {
        super.onStartInput(info, restarting);
        cancelPendingCompositionEnd();
        boolean nextPrivate = isPrivate(info);
        if (restarting && !composing && editorInputType == info.inputType && privateEditor == nextPrivate) {
            // 同一输入框重启不能取消已接收的直输按键或正在按下的视图。
            selected = info.initialSelStart != info.initialSelEnd;
            enqueue(engine -> null);
            return;
        }
        if (restarting && composing && editorInputType == info.inputType && privateEditor == nextPrivate) {
            String preedit = snapshot.optString("preedit");
            InputConnection connection = getCurrentInputConnection();
            CharSequence before = connection == null ? null : connection.getTextBeforeCursor(preedit.length(), 0);
            if (connection != null && !preedit.isEmpty() && info.initialSelStart == info.initialSelEnd
                    && info.initialSelEnd >= preedit.length() && before != null && preedit.contentEquals(before)) {
                connection.setComposingRegion(info.initialSelEnd - preedit.length(), info.initialSelEnd);
                enqueue(engine -> null);
                return;
            }
        }
        InputConnection connection = getCurrentInputConnection();
        if (connection != null) { connection.finishComposingText(); }
        editorInputType = info.inputType;
        if (keyboard != null) { keyboard.invalidateConfiguration(); }
        session++;
        selected = info.initialSelStart != info.initialSelEnd;
        composing = false;
        privateEditor = nextPrivate;
        int variation = info.inputType & InputType.TYPE_MASK_VARIATION;
        chinese = LocalStorage.open(this, "keyboard").getBoolean("chinese", true)
                && !privateEditor && variation != InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                && variation != InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS
                && variation != InputType.TYPE_TEXT_VARIATION_URI;
        final boolean target = chinese;
        final int fuzzy = new KeyboardPreferences(this).nasal;
        enqueue(engine -> { engine.clear(); engine.setNineKey(target); engine.setFuzzy(fuzzy); return null; });
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
        cancelPendingCompositionEnd();
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
            cancelPendingCompositionEnd();
            if (!selected && newEnd == oldEnd && composingEnd < 0) {
                // restartInput 先移除 composing 标记，下一回调才通知重启；保留短暂恢复机会。
                final int currentSession = session;
                pendingCompositionEnd = () -> {
                    if (currentSession == session && composing) { clearComposition(); }
                };
                main.postDelayed(pendingCompositionEnd, 100);
            } else { clearComposition(); }
        }
    }

    private void cancelPendingCompositionEnd() {
        if (pendingCompositionEnd != null) { main.removeCallbacks(pendingCompositionEnd); pendingCompositionEnd = null; }
    }

    private void clearComposition() {
        cancelPendingCompositionEnd();
        // 光标移动、选区或宿主修改文字后，旧组句不能覆盖新内容。
        composing = false;
        InputConnection connection = getCurrentInputConnection();
        if (connection != null) { connection.finishComposingText(); }
        enqueue(engine -> { engine.clear(); return null; });
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
            final int currentSession = session;
            enqueue(engine -> {
                if (!engine.push(character)) {
                    main.post(() -> {
                        long now = android.os.SystemClock.uptimeMillis();
                        if (!destroyed && currentSession == session && now - lastLimitNotice > 2000) {
                            lastLimitNotice = now;
                            Toast.makeText(this, "输入已满，请先选词或删除", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
                return null;
            });
        } else {
            text(String.valueOf(character));
        }
    }

    @Override public void text(String text) {
        enqueue(engine -> { String prefix = finish(engine); return prefix + text; });
    }

    @Override public void delete() {
        if (pendingCompositionEnd != null) { clearComposition(); }
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
                if (customAction(info)) { connection.performEditorAction(info.actionId); }
                else if (numericWithoutAction(info)) {
                    connection.performEditorAction(EditorInfo.IME_ACTION_DONE);
                    requestHideSelf(0);
                } else if (multiline(info) || action == EditorInfo.IME_ACTION_NONE || action == EditorInfo.IME_ACTION_UNSPECIFIED) {
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
        LocalStorage.open(this, "keyboard").edit().putBoolean("chinese", chinese).apply();
        boolean target = chinese;
        enqueue(engine -> { String text = finish(engine); engine.setNineKey(target); return text.isEmpty() ? null : text; });
        configure();
    }

    @Override public void numbers() {
        enqueue(engine -> finish(engine));
        EditorInfo info = getCurrentInputEditorInfo();
        int type = info == null ? 0 : info.inputType;
        if (keyboard != null) {
            keyboard.showNumbers((type & android.text.InputType.TYPE_MASK_CLASS) == android.text.InputType.TYPE_CLASS_PHONE,
                    (type & android.text.InputType.TYPE_NUMBER_FLAG_SIGNED) != 0,
                    (type & android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL) != 0);
        }
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

    @Override public void passwordManager() {
        openExternalApp("com.x8bit.bitwarden", "Bitwarden");
    }

    @Override public void authenticator() {
        openExternalApp("com.google.android.apps.authenticator2", "Google 验证器");
    }

    private void openExternalApp(String packageName, String label) {
        android.content.Intent launch = getPackageManager().getLaunchIntentForPackage(packageName);
        if (launch == null) {
            Toast.makeText(this, "未安装 " + label, Toast.LENGTH_SHORT).show();
            return;
        }
        launch.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
        enqueue(engine -> finish(engine), connection -> {
            try { startActivity(launch); }
            catch (android.content.ActivityNotFoundException | SecurityException error) {
                Toast.makeText(this, "无法打开 " + label, Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override public void selectAll() {
        editorAction(EditorCommand.SELECT_ALL);
    }

    @Override public void editorAction(EditorCommand command) {
        final InputConnection connection = getCurrentInputConnection();
        final int currentSession = session;
        enqueue(engine -> {
            String committed = finish(engine);
            main.post(() -> {
                if (destroyed || currentSession != session || connection == null) { return; }
                if (!committed.isEmpty()) { connection.commitText(committed, 1); }
                connection.finishComposingText();
                composing = false;
                EditorController.execute(connection, command);
            });
            return null;
        });
    }

    @Override public void nextIme() {
        InputMethodManager manager = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (manager != null) { manager.showInputMethodPicker(); }
    }

    @Override public void setting(String name, int value) {
        final int bit = KeyboardPreferences.nasalBit(name);
        if (name.equals("height")) { if (value < -1 || value > 1) { return; } }
        else if (name.equals("theme")) { if (value < 0 || value > 2) { return; } }
        else if (bit != 0) { if (value < 0 || value > 1) { return; } }
        else { return; }
        final int fuzzy = (new KeyboardPreferences(this).nasal & ~bit) | (value == 1 ? bit : 0);
        enqueue(engine -> {
            String text = finish(engine);
            if (bit != 0) { engine.setFuzzy(fuzzy); }
            return text;
        }, connection -> {
            LocalStorage.open(this, "keyboard").edit().putInt(name, value).apply();
            replaceKeyboard(true);
        });
    }

    private void enqueue(Command command) {
        enqueue(command, null);
    }

    private void enqueue(Command command, Consumer<InputConnection> after) {
        if (destroyed) { return; }
        if (pendingCompositionEnd != null) { clearComposition(); }
        final int currentSession = session;
        final long operation = ++sequence;
        final InputConnection connection = getCurrentInputConnection();
        engineQueue.execute(() -> {
            if (bridge == null) { return; }
            String committed = command.run(bridge);
            // 每个按键仍按序执行；只省略已被新操作取代且没有提交或后续动作的查询。
            if (operation != sequence && committed == null && after == null) { return; }
            JSONObject state = state();
            main.post(() -> {
                if (!destroyed && currentSession == session) {
                    apply(state, committed, connection, currentSession, operation);
                    if (after != null) { after.accept(connection); }
                }
            });
        });
    }

    private JSONObject state() {
        try { return new JSONObject(bridge.snapshot()); }
        catch (Exception ignored) { return new JSONObject(); }
    }

    private void apply(JSONObject state, String committed, InputConnection connection, int inputSession, long operation) {
        boolean latest = operation == sequence;
        if (latest) {
            snapshot = state;
            renderedSequence = operation;
            renderedSession = inputSession;
        }
        if (connection != null) {
            connection.beginBatchEdit();
            try {
                // 分段选词的提交与剩余预编辑必须在同一批次，避免中途选区通知清空后半句。
                if (committed != null && !committed.isEmpty()) { connection.commitText(committed, 1); }
                if (!latest) { return; }
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
        if (latest && keyboard != null) { keyboard.snapshot(state); }
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
        if (keyboard.preferencesChanged()) { replaceKeyboard(false); return; }
        EditorInfo info = getCurrentInputEditorInfo();
        keyboard.configure(chinese, enterLabel(info), privateEditor, info == null ? 0 : info.inputType);
    }

    private void replaceKeyboard(boolean settings) {
        KeyboardView previous = keyboard;
        View replacement = onCreateInputView();
        // 新视图首次接收 insets 前沿用当前安全区，防止返回键先贴底再上移。
        replacement.setPadding(previous.getPaddingLeft(), previous.getPaddingTop(), previous.getPaddingRight(), previous.getPaddingBottom());
        setInputView(replacement);
        replacement.requestApplyInsets();
        if (settings) { keyboard.restoreSettings(previous); }
    }

    private void captureClipboard() {
        if (!visible || destroyed) { return; }
        clipboard.capture(privateEditor);
        if (keyboard != null && keyboard.page() == 3) { keyboard.showClipboard(clipboard, privateEditor); }
    }

    private void applyInsets() {
        KeyboardStyle style = new KeyboardStyle(this);
        Window window = getWindow().getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
            window.setNavigationBarColor(style.background);
            window.getDecorView().setSystemUiVisibility(style.dark ? 0 : View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
            if (Build.VERSION.SDK_INT >= 29) { window.setNavigationBarContrastEnforced(false); }
            if (Build.VERSION.SDK_INT >= 30) { window.setDecorFitsSystemWindows(false); }
        }
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
        if (file.isFile() && version.equals(LocalStorage.open(this, "assets").getString(key, ""))) { return file; }
        File temp = new File(getFilesDir(), name + ".tmp");
        try (InputStream source = getAssets().open(name); FileOutputStream target = new FileOutputStream(temp)) {
            byte[] buffer = new byte[65536];
            int count;
            while ((count = source.read(buffer)) != -1) { target.write(buffer, 0, count); }
        }
        if (!temp.renameTo(file)) { throw new IllegalStateException("无法安装词库"); }
        LocalStorage.open(this, "assets").edit().putString(key, version).apply();
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
        if (customAction(info)) { return info.actionLabel.toString(); }
        if (numericWithoutAction(info)) { return "完成"; }
        switch (info.imeOptions & EditorInfo.IME_MASK_ACTION) {
            case EditorInfo.IME_ACTION_SEND: return "发送";
            case EditorInfo.IME_ACTION_SEARCH: return "搜索";
            case EditorInfo.IME_ACTION_GO: return "前往";
            case EditorInfo.IME_ACTION_NEXT: return "下一项";
            case EditorInfo.IME_ACTION_PREVIOUS: return "上一项";
            case EditorInfo.IME_ACTION_DONE: return "完成";
            default: return "换行";
        }
    }

    private static boolean customAction(EditorInfo info) {
        return info != null && !multiline(info) && !TextUtils.isEmpty(info.actionLabel);
    }

    private static boolean numericWithoutAction(EditorInfo info) {
        if (info == null) { return false; }
        int type = info.inputType & InputType.TYPE_MASK_CLASS;
        int action = info.imeOptions & EditorInfo.IME_MASK_ACTION;
        return (type == InputType.TYPE_CLASS_NUMBER || type == InputType.TYPE_CLASS_PHONE || type == InputType.TYPE_CLASS_DATETIME)
                && (action == EditorInfo.IME_ACTION_NONE || action == EditorInfo.IME_ACTION_UNSPECIFIED);
    }
}
