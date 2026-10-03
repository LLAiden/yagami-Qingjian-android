// 十轮自查新增的真实输入法交互回归测试。
package io.github.utyoinog.yagamiime;

import android.graphics.Rect;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.text.InputType;

public final class KeyboardAuditTest extends KeyboardTestCase {
    public void testFollowingSystemThemeUpdatesOnReopen() throws Exception {
        try {
            shell("cmd uimode night yes"); showMessage();
            await(() -> keyIsDark());
            shell("cmd uimode night no"); showMessage();
            await(() -> !keyIsDark());
            typeDigits("64426"); click("候选 你好");
            await(() -> activity.message.getText().toString().equals("你好"));
        } finally { shell("cmd uimode night no"); }
    }

    public void testRestartAfterHostTextChangeDoesNotRestoreOldComposition() throws Exception {
        typeDigits("64426");
        getInstrumentation().runOnMainSync(() -> {
            activity.message.setText("应用新文字！");
            activity.message.setSelection(6);
            ((InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE)).restartInput(activity.message);
        });
        await(() -> find("候选 你好") == null);
        typeDigits("64426"); click("候选 你好");
        await(() -> activity.message.getText().toString().equals("应用新文字！你好"));
    }

    public void testRestartRechecksPrivacyDuringComposition() throws Exception {
        typeDigits("64426");
        getInstrumentation().runOnMainSync(() -> {
            activity.message.setImeOptions(activity.message.getImeOptions() | EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);
            ((InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE)).restartInput(activity.message);
        });
        await(() -> find("粘贴") != null);
        assertNotNull("隐私模式临时使用英文", find("q"));
        getInstrumentation().runOnMainSync(() -> ((ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE))
                .setPrimaryClip(ClipData.newPlainText("private", "切换隐私后的复制")));
        SystemClock.sleep(150);
        assertTrue(new ClipboardStore(getInstrumentation().getTargetContext()).entries().stream()
                .noneMatch(entry -> entry.text.equals("切换隐私后的复制")));
        getInstrumentation().runOnMainSync(() -> {
            activity.message.setInputType(InputType.TYPE_CLASS_NUMBER);
            ((InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE)).restartInput(activity.message);
        });
        assertNotNull("同一输入框改为数字应更新布局", node("0"));
    }

    public void testRestartWithSameTypeKeepsComposition() throws Exception {
        typeDigits("64426");
        getInstrumentation().runOnMainSync(() -> ((InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE))
                .restartInput(activity.message));
        click("候选 你好");
        await(() -> activity.message.getText().toString().equals("你好"));
    }

    public void testInputLimitIsVisibleAndCanRecover() throws Exception {
        typeDigits("64".repeat(32));
        assertNotNull("达到上限应有可见提示", node("输入长度提示"));
        String full = activity.message.getText().toString();
        typeDigits("2");
        assertEquals("上限外不能改变预编辑", full, activity.message.getText().toString());
        click("删除文字"); await(() -> find("输入长度提示") == null);
        typeDigits("2");
        assertNotNull("删除后可以继续输入", node("输入长度提示"));
    }

    public void testDeleteStopsWhenFingerLeavesKey() throws Exception {
        edit("a".repeat(60), 60, 60);
        Rect key = bounds("删除文字");
        Rect outside = bounds("收起键盘");
        long time = SystemClock.uptimeMillis();
        touch(time, MotionEvent.ACTION_DOWN, key.centerX(), key.centerY());
        try {
            SystemClock.sleep(750);
            touch(time, MotionEvent.ACTION_MOVE, outside.centerX(), outside.centerY());
            SystemClock.sleep(200);
            int afterMove = activity.message.getText().length();
            SystemClock.sleep(300);
            assertEquals("手指滑出删除键后应停止连续删除", afterMove, activity.message.getText().length());
        } finally { touch(time, MotionEvent.ACTION_UP, outside.centerX(), outside.centerY()); }
    }

    public void testThemeChangesActualKeysAndPersists() throws Exception {
        click("编辑"); click("设置"); click("主题 深色");
        await(() -> keyIsDark());
        click("返回键盘"); click("收起键盘");
        await(() -> find("收起键盘") == null); showMessage();
        assertTrue("重开应保持深色主题", keyIsDark());
        click("编辑"); click("设置"); click("主题 浅色");
        await(() -> !keyIsDark());
        click("返回键盘"); typeDigits("64426"); click("候选 你好");
        await(() -> activity.message.getText().toString().equals("你好"));
    }

    private boolean keyIsDark() {
        android.view.accessibility.AccessibilityNodeInfo node = find("收起键盘");
        if (node == null) { return false; }
        Rect key = new Rect(); node.getBoundsInScreen(key);
        android.graphics.Bitmap image = getInstrumentation().getUiAutomation().takeScreenshot();
        if (image == null) { return false; }
        try { return android.graphics.Color.red(image.getPixel(key.left + 15, key.top + 15)) < 100; }
        finally { image.recycle(); }
    }

    public void testKeyboardHeightChangesAndPersists() throws Exception {
        int normal = bounds("空格").height();
        click("编辑"); click("设置"); click("高度 矮");
        await(() -> getInstrumentation().getTargetContext().getSharedPreferences("keyboard", Context.MODE_PRIVATE).getInt("height", 0) == -1);
        click("返回键盘");
        assertTrue(bounds("空格").height() < normal);
        click("编辑"); click("设置"); click("高度 高");
        await(() -> getInstrumentation().getTargetContext().getSharedPreferences("keyboard", Context.MODE_PRIVATE).getInt("height", 0) == 1);
        click("返回键盘");
        assertTrue(bounds("空格").height() > normal);
        click("收起键盘"); await(() -> find("收起键盘") == null); showMessage();
        assertTrue("重开键盘应保持高度", bounds("空格").height() > normal);
    }

    public void testHeightSettingKeepsNumericPage() throws Exception {
        click("123"); click("1");
        click("编辑"); click("设置"); click("高度 高");
        await(() -> getInstrumentation().getTargetContext().getSharedPreferences("keyboard", Context.MODE_PRIVATE).getInt("height", 0) == 1);
        click("返回键盘"); click("2");
        await(() -> activity.message.getText().toString().equals("12"));
        assertNotNull(find("0"));
    }

    public void testEditingPanelCopyCutPasteAndCursor() throws Exception {
        edit("编辑验收", 4, 4);
        click("编辑"); click("全选");
        await(() -> activity.message.getSelectionStart() != activity.message.getSelectionEnd());
        click("复制");
        await(() -> new ClipboardStore(getInstrumentation().getTargetContext()).current().equals("编辑验收"));
        click("剪切");
        await(() -> activity.message.getText().length() == 0);
        click("粘贴");
        await(() -> activity.message.getText().toString().equals("编辑验收"));
        click("开头");
        await(() -> activity.message.getSelectionStart() == 0);
        click("右移");
        await(() -> activity.message.getSelectionStart() == 1);
        click("末尾"); click("左移");
        await(() -> activity.message.getSelectionStart() == 3);
        click("返回键盘");
        typeDigits("64426"); click("候选 你好");
        await(() -> activity.message.getText().toString().equals("编辑验你好收"));
    }

    public void testPhoneWithoutActionUsesDone() throws Exception {
        getInstrumentation().runOnMainSync(() -> {
            activity.number.setInputType(InputType.TYPE_CLASS_PHONE);
            activity.number.setImeOptions(EditorInfo.IME_ACTION_NONE);
            activity.number.requestFocus();
            ((InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE)).restartInput(activity.number);
            ((InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE)).showSoftInput(activity.number, 0);
        });
        click("1"); click("2"); click("完成");
        await(() -> find("收起键盘") == null);
        assertEquals("12", activity.number.getText().toString());
    }

    public void testCustomEditorActionLabelAndId() throws Exception {
        getInstrumentation().runOnMainSync(() -> {
            activity.number.setInputType(InputType.TYPE_CLASS_TEXT);
            activity.number.setImeActionLabel("查找", 4242);
            activity.number.setImeOptions(EditorInfo.IME_ACTION_UNSPECIFIED);
            activity.number.requestFocus();
            ((InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE)).restartInput(activity.number);
            ((InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE)).showSoftInput(activity.number, 0);
        });
        typeDigits("64426"); click("查找");
        await(() -> activity.lastEditorAction == 4242);
        assertEquals("你好", activity.number.getText().toString());
    }

    public void testPrivateClipboardBlockSurvivesStoreRecreation() throws Exception {
        Context context = getInstrumentation().getTargetContext();
        boolean[] blocked = {false};
        String[] normal = {""};
        getInstrumentation().runOnMainSync(() -> {
            new ClipboardStore(context).clear();
            ClipboardManager manager = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            manager.setPrimaryClip(ClipData.newPlainText("private", "重建后仍私密"));
            new ClipboardStore(context).capture(true);
            ClipboardStore recreated = new ClipboardStore(context);
            recreated.capture(false);
            blocked[0] = recreated.entries().isEmpty();
            manager.setPrimaryClip(ClipData.newPlainText("ordinary", "正常新复制"));
            recreated.capture(false);
            normal[0] = recreated.entries().isEmpty() ? "" : recreated.entries().get(0).text;
        });
        assertTrue("重建服务后私密复制不能进入历史", blocked[0]);
        assertEquals("正常新复制", normal[0]);
    }

    public void testClipboardOpeningDoesNotRenewCopyTime() throws Exception {
        Context context = getInstrumentation().getTargetContext();
        ClipboardStore[] store = new ClipboardStore[1];
        long[] times = new long[3];
        getInstrumentation().runOnMainSync(() -> {
            ClipboardManager manager = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            new ClipboardStore(context).clear();
            manager.setPrimaryClip(ClipData.newPlainText("audit", "复制时间验收"));
            store[0] = new ClipboardStore(context);
            store[0].capture(false);
            times[0] = store[0].entries().get(0).time;
        });
        SystemClock.sleep(150);
        getInstrumentation().runOnMainSync(() -> {
            store[0].capture(false);
            times[1] = store[0].entries().get(0).time;
            ((ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE))
                    .setPrimaryClip(ClipData.newPlainText("audit", "复制时间验收"));
            store[0].capture(false);
            times[2] = store[0].entries().get(0).time;
        });
        assertEquals("打开面板不能延长原复制内容的保留时间", times[0], times[1]);
        assertTrue("再次复制应更新复制时间", times[2] > times[0]);
    }

    public void testExpandedDeleteRepeatsAcrossCandidateUpdates() throws Exception {
        typeDigits("6442664426");
        click("展开候选");
        int initial = activity.message.getText().length();
        Rect key = bounds("删除文字");
        long time = SystemClock.uptimeMillis();
        touch(time, MotionEvent.ACTION_DOWN, key.centerX(), key.centerY());
        SystemClock.sleep(950);
        touch(time, MotionEvent.ACTION_UP, key.centerX(), key.centerY());
        SystemClock.sleep(200);
        assertTrue("候选更新不能打断长按删除", activity.message.getText().length() < initial - 2);
    }

    private void touch(long time, int action, int x, int y) {
        MotionEvent event = MotionEvent.obtain(time, SystemClock.uptimeMillis(), action, x, y, 0);
        event.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        getInstrumentation().getUiAutomation().injectInputEvent(event, true);
        event.recycle();
    }
}
