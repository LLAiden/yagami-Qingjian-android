// 在系统输入法窗口实际点击按键，验收输入、编辑、面板和导航区域。
package io.github.utyoinog.yagamiime;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.test.InstrumentationTestCase;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.view.inputmethod.InputMethodManager;

import java.io.FileInputStream;
import java.util.List;
import java.util.function.BooleanSupplier;

@SuppressWarnings("deprecation")
public final class KeyboardAcceptanceTest extends KeyboardTestCase {
    public void testNineKeyWordAndPinyinChoice() throws Exception {
        typeDigits("64426");
        click("候选 你好");
        await(() -> activity.message.getText().toString().equals("你好"));
        typeDigits("64");
        click("选择拼音 mi");
        click("候选 米");
        await(() -> activity.message.getText().toString().equals("你好米"));
    }

    public void testSelectionDeleteAndEmoji() throws Exception {
        edit("保留删除尾巴", 2, 4);
        click("删除文字");
        await(() -> activity.message.getText().toString().equals("保留尾巴"));
        edit("全选删除", 4, 4);
        click("全选");
        await(() -> activity.message.getSelectionStart() != activity.message.getSelectionEnd());
        click("删除文字");
        await(() -> activity.message.getText().length() == 0);
        String emoji = "A👨‍👩‍👧‍👦";
        edit(emoji, emoji.length(), emoji.length());
        click("删除文字");
        await(() -> activity.message.getText().toString().equals("A"));
    }

    public void testNineKeyNumberEditor() throws Exception {
        getInstrumentation().runOnMainSync(() -> {
            activity.number.requestFocus();
            ((InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE)).showSoftInput(activity.number, InputMethodManager.SHOW_IMPLICIT);
        });
        for (String digit : new String[]{"1", "2", "3", "0"}) { click(digit); }
        await(() -> activity.number.getText().toString().equals("1230"));
        Rect one = bounds("1"), two = bounds("2"), three = bounds("3"), four = bounds("4");
        assertEquals(one.centerY(), two.centerY());
        assertEquals(two.centerY(), three.centerY());
        assertTrue(one.centerX() < two.centerX() && two.centerX() < three.centerX());
        assertEquals(one.centerX(), four.centerX());
        assertTrue(four.centerY() > one.centerY());
    }

    public void testNumericClipboardReturnsToDecimalKeypad() throws Exception {
        getInstrumentation().runOnMainSync(() -> {
            activity.number.requestFocus();
            ((InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE)).showSoftInput(activity.number, InputMethodManager.SHOW_IMPLICIT);
        });
        click("1");
        click("剪贴板");
        click("返回键盘");
        node("1"); node(".");
        click("."); click("2");
        await(() -> activity.number.getText().toString().equals("1.2"));
        click("符号");
        click("返回数字");
        click("3");
        await(() -> activity.number.getText().toString().equals("1.23"));
    }

    public void testEnglishChoiceSurvivesEditorSwitch() throws Exception {
        click("EN");
        click("h");
        getInstrumentation().runOnMainSync(() -> activity.number.requestFocus());
        await(() -> find("1") != null);
        showMessage();
        assertNotNull("返回普通输入框应保留英文模式", find("中文"));
        click("i");
        await(() -> activity.message.getText().toString().equals("hi"));
    }

    public void testClipboardCurrentAppearsOnce() throws Exception {
        Context context = getInstrumentation().getTargetContext();
        getInstrumentation().runOnMainSync(() -> {
            new ClipboardStore(context).clear();
            ((ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE))
                    .setPrimaryClip(ClipData.newPlainText("test", "去重验收"));
        });
        SystemClock.sleep(200);
        click("剪贴板");
        int copies = 0;
        for (AccessibilityWindowInfo window : getInstrumentation().getUiAutomation().getWindows()) {
            copies += countClipboardText(window.getRoot(), "去重验收");
        }
        assertEquals("当前复制与历史不能显示为两份", 1, copies);
        click("固定");
        assertNotNull(find("取消固定"));
        click("粘贴 当前复制 去重验收");
        await(() -> activity.message.getText().toString().equals("去重验收"));
    }

    public void testDeletingCurrentClipboardDoesNotReturn() throws Exception {
        Context context = getInstrumentation().getTargetContext();
        getInstrumentation().runOnMainSync(() -> {
            new ClipboardStore(context).clear();
            ((ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE))
                    .setPrimaryClip(ClipData.newPlainText("test", "删除剪贴板验收"));
        });
        SystemClock.sleep(200);
        click("剪贴板"); click("删除");
        await(() -> find("粘贴 当前复制 删除剪贴板验收") == null);
        click("返回键盘"); click("剪贴板");
        assertNull(find("粘贴 当前复制 删除剪贴板验收"));
        assertTrue(new ClipboardStore(context).entries().isEmpty());
    }

    public void testTemporaryEnglishEditorDoesNotChangePreference() throws Exception {
        getInstrumentation().runOnMainSync(() -> {
            activity.password.requestFocus();
            ((InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE)).showSoftInput(activity.password, InputMethodManager.SHOW_IMPLICIT);
        });
        await(() -> find("中文") != null);
        click("h");
        showMessage();
        await(() -> find("拼音键 2 ABC") != null);
        typeDigits("64426"); click("候选 你好");
        await(() -> activity.message.getText().toString().equals("你好"));
        assertEquals("h", activity.password.getText().toString());
    }

    public void testExpandedCandidatesChooseAndReturn() throws Exception {
        typeDigits("64"); click("展开候选");
        Rect ni = bounds("展开候选 你"), mi = bounds("展开候选 米");
        assertEquals(ni.centerY(), mi.centerY());
        assertTrue(ni.centerX() < mi.centerX());
        click("展开候选 米");
        await(() -> activity.message.getText().toString().equals("米"));
        await(() -> find("拼音键 2 ABC") != null);
        typeDigits("64426"); click("展开候选"); click("返回键盘");
        click("候选 你好");
        await(() -> activity.message.getText().toString().equals("米你好"));
    }

    public void testExpandedCandidatesHidePreservesComposition() throws Exception {
        typeDigits("64426"); click("展开候选"); click("收起键盘");
        await(() -> find("收起键盘") == null);
        await(() -> activity.message.getText().toString().equals("你好"));
        showMessage();
        assertNotNull(find("拼音键 2 ABC"));
        assertEquals("你好", activity.message.getText().toString());
    }

    public void testSystemHidePreservesComposition() throws Exception {
        typeDigits("64426");
        getInstrumentation().runOnMainSync(() -> ((InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE))
                .hideSoftInputFromWindow(activity.message.getWindowToken(), 0));
        await(() -> find("收起键盘") == null);
        await(() -> activity.message.getText().toString().equals("你好"));
        showMessage();
        typeDigits("64426"); click("候选 你好");
        await(() -> activity.message.getText().toString().equals("你好你好"));
    }

    public void testPhoneClipboardPasteKeepsPhoneKeys() throws Exception {
        Context context = getInstrumentation().getTargetContext();
        getInstrumentation().runOnMainSync(() -> {
            activity.number.setInputType(android.text.InputType.TYPE_CLASS_PHONE);
            activity.number.requestFocus();
            ((InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE)).showSoftInput(activity.number, InputMethodManager.SHOW_IMPLICIT);
            new ClipboardStore(context).clear();
            ((ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE))
                    .setPrimaryClip(ClipData.newPlainText("test", "123"));
        });
        click("剪贴板"); click("粘贴 当前复制 123");
        await(() -> activity.number.getText().toString().equals("123"));
        node("*"); node("#");
        click("#");
        await(() -> activity.number.getText().toString().equals("123#"));
    }

    public void testCandidateStripReturnsToStartOnNewInput() throws Exception {
        typeDigits("64");
        Rect strip = bounds("候选列表");
        swipe(strip.right - 30, strip.centerY(), strip.left + 30, strip.centerY());
        typeDigits("426");
        Rect first = bounds("候选 你好");
        assertTrue("新候选应从首项开始显示", first.left >= strip.left && first.right <= strip.right);
    }

    private void swipe(int x1, int y1, int x2, int y2) {
        long time = SystemClock.uptimeMillis();
        for (int step = 0; step <= 12; step++) {
            int action = step == 0 ? MotionEvent.ACTION_DOWN : step == 12 ? MotionEvent.ACTION_UP : MotionEvent.ACTION_MOVE;
            MotionEvent event = MotionEvent.obtain(time, time + step * 25L, action,
                    x1 + (x2 - x1) * step / 12f, y1 + (y2 - y1) * step / 12f, 0);
            event.setSource(InputDevice.SOURCE_TOUCHSCREEN);
            getInstrumentation().getUiAutomation().injectInputEvent(event, true);
            event.recycle();
            SystemClock.sleep(25);
        }
        SystemClock.sleep(150);
    }

    private int countClipboardText(AccessibilityNodeInfo node, String text) {
        if (node == null) { return 0; }
        CharSequence description = node.getContentDescription();
        int count = node.isVisibleToUser() && description != null && description.toString().startsWith("粘贴 ")
                && description.toString().endsWith(" " + text) ? 1 : 0;
        for (int i = 0; i < node.getChildCount(); i++) { count += countClipboardText(node.getChild(i), text); }
        return count;
    }

    public void testClipboardHistoryAndPastePreservesComposition() throws Exception {
        Context context = getInstrumentation().getTargetContext();
        ClipboardManager manager = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        getInstrumentation().runOnMainSync(() -> {
            new ClipboardStore(context).clear();
            manager.setPrimaryClip(ClipData.newPlainText("test", "剪贴板验收"));
        });
        SystemClock.sleep(200);
        typeDigits("64426");
        click("剪贴板");
        click("粘贴 当前复制 剪贴板验收");
        await(() -> activity.message.getText().toString().equals("你好剪贴板验收"));
        click("剪贴板");
        click("固定");
        assertNotNull(find("取消固定"));
        click("清空");
        await(() -> new ClipboardStore(context).entries().isEmpty());
        click("收起键盘");
        await(() -> find("收起键盘") == null);
    }

    public void testHideAndReopenWithoutDuplicateText() throws Exception {
        typeDigits("64426");
        click("收起键盘");
        await(() -> find("收起键盘") == null);
        await(() -> activity.message.getText().toString().equals("你好"));
        showMessage();
        typeDigits("64426");
        click("候选 你好");
        await(() -> activity.message.getText().toString().equals("你好你好"));
    }

    public void testRapidTypingAndImmediateHide() throws Exception {
        for (int repeat = 1; repeat <= 3; repeat++) {
            for (char digit : "64426".toCharArray()) {
                AccessibilityNodeInfo key = null;
                for (AccessibilityWindowInfo window : getInstrumentation().getUiAutomation().getWindows()) {
                    key = findPrefix(window.getRoot(), "拼音键 " + digit + " ");
                    if (key != null) { break; }
                }
                assertNotNull(key);
                tap(key, 5);
            }
            click("收起键盘");
            final String expected = "你好".repeat(repeat);
            await(() -> activity.message.getText().toString().equals(expected));
            await(() -> find("收起键盘") == null);
            showMessage();
        }
    }

    public void testEnterCommitsBeforeNewlineOrAction() throws Exception {
        typeDigits("64426");
        click("换行");
        await(() -> activity.message.getText().toString().equals("你好\n"));
        getInstrumentation().runOnMainSync(() -> {
            activity.number.requestFocus();
            ((InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE)).showSoftInput(activity.number, InputMethodManager.SHOW_IMPLICIT);
        });
        click("1"); click("2"); click("完成");
        await(() -> activity.lastEditorAction == android.view.inputmethod.EditorInfo.IME_ACTION_DONE);
        assertEquals("12", activity.number.getText().toString());
    }

    public void testHideFromNumbersSymbolsAndClipboard() throws Exception {
        for (String panel : new String[]{"123", "符号", "剪贴板"}) {
            click(panel);
            click("收起键盘");
            await(() -> find("收起键盘") == null);
            showMessage();
        }
    }

    public void testNineKeyPinyinAndEnglishStillWork() throws Exception {
        typeDigits("64426");
        click("候选 你好");
        await(() -> activity.message.getText().toString().equals("你好"));
        click("EN");
        for (String letter : new String[]{"h", "i"}) { click(letter); }
        await(() -> activity.message.getText().toString().equals("你好hi"));
        click("中文");
        typeDigits("64"); click("候选 你");
        await(() -> activity.message.getText().toString().equals("你好hi你"));
    }

    public void testLongPressDeleteStopsOnRelease() throws Exception {
        String text = "abcdefghijklmnopqrstuvwxyz";
        edit(text, text.length(), text.length());
        Rect bounds = bounds("删除文字");
        long time = SystemClock.uptimeMillis();
        MotionEvent down = MotionEvent.obtain(time, time, MotionEvent.ACTION_DOWN, bounds.centerX(), bounds.centerY(), 0);
        down.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        getInstrumentation().getUiAutomation().injectInputEvent(down, true);
        SystemClock.sleep(850);
        MotionEvent up = MotionEvent.obtain(time, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, bounds.centerX(), bounds.centerY(), 0);
        up.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        getInstrumentation().getUiAutomation().injectInputEvent(up, true);
        down.recycle(); up.recycle();
        SystemClock.sleep(200);
        int remaining = activity.message.getText().length();
        assertTrue(remaining < text.length() - 1 && remaining > 0);
        SystemClock.sleep(250);
        assertEquals(remaining, activity.message.getText().length());
    }

    public void testLandscapeAndReturnKeepText() throws Exception {
        edit("横屏验收", 4, 4);
        getInstrumentation().runOnMainSync(() -> activity.setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE));
        await(() -> activity.getResources().getConfiguration().orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE);
        showMessage();
        assertAboveNavigation("空格");
        click("收起键盘");
        await(() -> find("收起键盘") == null);
        getInstrumentation().runOnMainSync(() -> activity.setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT));
        await(() -> activity.getResources().getConfiguration().orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT);
        showMessage();
        assertEquals("横屏验收", activity.message.getText().toString());
    }

    public void testPasswordDoesNotExposeOrRecordHistory() throws Exception {
        Context context = getInstrumentation().getTargetContext();
        ClipboardManager manager = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        getInstrumentation().runOnMainSync(() -> {
            new ClipboardStore(context).clear();
            manager.setPrimaryClip(ClipData.newPlainText("ordinary", "普通历史"));
        });
        click("剪贴板");
        click("返回键盘");
        getInstrumentation().runOnMainSync(() -> {
            activity.password.requestFocus();
            ((InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE)).showSoftInput(activity.password, InputMethodManager.SHOW_IMPLICIT);
        });
        await(() -> find("粘贴") != null);
        getInstrumentation().runOnMainSync(() -> manager.setPrimaryClip(ClipData.newPlainText("private", "私密内容")));
        SystemClock.sleep(100);
        click("粘贴");
        assertNull(find("粘贴 历史 普通历史"));
        assertNull(find("固定"));
        assertTrue(new ClipboardStore(context).entries().stream().noneMatch(entry -> entry.text.equals("私密内容")));
        showMessage();
        await(() -> find("剪贴板") != null);
        assertTrue("离开密码框后仍不能自动记录该次私密复制", new ClipboardStore(context).entries().stream().noneMatch(entry -> entry.text.equals("私密内容")));
    }

    public void testGestureAndThreeButtonInsets() throws Exception {
        shell("cmd overlay enable-exclusive --category com.android.internal.systemui.navbar.gestural");
        showMessage();
        assertAboveNavigation("空格");
        Rect hide = bounds("收起键盘"), space = bounds("空格");
        assertTrue(hide.top < space.top);
        shell("cmd overlay enable-exclusive --category com.android.internal.systemui.navbar.threebutton");
        showMessage();
        assertAboveNavigation("空格");
    }

}
