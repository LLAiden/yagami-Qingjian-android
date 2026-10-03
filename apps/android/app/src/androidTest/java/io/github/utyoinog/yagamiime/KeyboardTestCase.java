// 真实 IME 交互测试共用的启动、触摸和等待方法。
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
public abstract class KeyboardTestCase extends InstrumentationTestCase {
    protected InputTestActivity activity;

    @Override protected void setUp() throws Exception {
        super.setUp();
        AccessibilityServiceInfo service = getInstrumentation().getUiAutomation().getServiceInfo();
        service.flags |= AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
        getInstrumentation().getUiAutomation().setServiceInfo(service);
        LocalStorage.open(getInstrumentation().getTargetContext(), "keyboard").edit()
                .clear().putBoolean("nine_key", true).putBoolean("chinese", true).commit();
        String component = getInstrumentation().getTargetContext().getPackageName() + "/io.github.utyoinog.yagamiime.YagamiInputMethodService";
        shell("ime enable " + component);
        shell("ime set " + component);
        Intent intent = new Intent(getInstrumentation().getTargetContext(), InputTestActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        activity = (InputTestActivity) getInstrumentation().startActivitySync(intent);
        showMessage();
    }

    @Override protected void tearDown() throws Exception {
        if (activity != null) { getInstrumentation().runOnMainSync(() -> activity.finish()); }
        super.tearDown();
    }

    @android.annotation.TargetApi(30)
    protected void assertAboveNavigation(String description) throws Exception {
        int[] boundary = {-1};
        Rect key = new Rect();
        long until = SystemClock.uptimeMillis() + 10000;
        do {
            getInstrumentation().runOnMainSync(() -> {
                activity = InputTestActivity.current;
                android.view.WindowInsets insets = activity.getWindow().getDecorView().getRootWindowInsets();
                if (insets == null) { return; }
                android.graphics.Insets bars = insets.getInsets(android.view.WindowInsets.Type.navigationBars());
                android.graphics.Point display = new android.graphics.Point();
                activity.getDisplay().getRealSize(display);
                boundary[0] = display.y - bars.bottom;
            });
            AccessibilityNodeInfo node = find(description);
            if (node != null) { node.getBoundsInScreen(key); }
            if (key.bottom > 0 && boundary[0] > 0 && key.bottom <= boundary[0]) { break; }
            SystemClock.sleep(50);
        } while (SystemClock.uptimeMillis() < until);
        assertTrue("按键进入导航区域：" + key + "，安全底边=" + boundary[0], key.bottom > 0 && key.bottom <= boundary[0]);
    }

    protected void edit(String text, int start, int end) throws Exception {
        getInstrumentation().runOnMainSync(() -> { activity.message.setText(text); activity.message.setSelection(start, end); });
        getInstrumentation().waitForIdleSync();
        SystemClock.sleep(150);
    }

    protected void showMessage() throws Exception {
        getInstrumentation().waitForIdleSync();
        if (InputTestActivity.current != null) { activity = InputTestActivity.current; }
        await(() -> {
            if (InputTestActivity.current != null) { activity = InputTestActivity.current; }
            return activity.getWindow().getDecorView().hasWindowFocus();
        });
        getInstrumentation().runOnMainSync(() -> {
            activity.message.requestFocus();
            ((InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE)).showSoftInput(activity.message, InputMethodManager.SHOW_IMPLICIT);
        });
        long until = SystemClock.uptimeMillis() + 10000;
        while (find("收起键盘") == null && SystemClock.uptimeMillis() < until) {
            getInstrumentation().runOnMainSync(() -> {
                // 导航模式切换可能在窗口等待期间重建 Activity，不能继续请求旧输入框。
                if (InputTestActivity.current != null) { activity = InputTestActivity.current; }
                activity.message.requestFocus();
                ((InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE))
                        .showSoftInput(activity.message, InputMethodManager.SHOW_IMPLICIT);
            });
            SystemClock.sleep(100);
        }
        assertNotNull("软键盘未显示", find("收起键盘"));
        Rect previous = new Rect();
        int stable = 0;
        long settleUntil = SystemClock.uptimeMillis() + 3000;
        while (stable < 3 && SystemClock.uptimeMillis() < settleUntil) {
            AccessibilityNodeInfo hide = find("收起键盘");
            Rect current = new Rect();
            if (hide != null) { hide.getBoundsInScreen(current); }
            stable = !current.isEmpty() && current.equals(previous) ? stable + 1 : 0;
            previous = current;
            SystemClock.sleep(50);
        }
    }

    protected void typeDigits(String text) throws Exception {
        for (char digit : text.toCharArray()) {
            AccessibilityNodeInfo key = null;
            long until = SystemClock.uptimeMillis() + 10000;
            while (key == null && SystemClock.uptimeMillis() < until) {
                for (AccessibilityWindowInfo window : getInstrumentation().getUiAutomation().getWindows()) {
                    key = findPrefix(window.getRoot(), "拼音键 " + digit + " ");
                    if (key != null) { break; }
                }
                if (key == null) { SystemClock.sleep(50); }
            }
            assertNotNull("找不到九键 " + digit, key);
            tap(key);
        }
        SystemClock.sleep(250);
    }

    protected void click(String description) throws Exception {
        // 设置更新会改变 IME 窗口高度；等布局稳定后使用当前节点的坐标。
        node(description);
        Rect previous = new Rect();
        int stable = 0;
        long until = SystemClock.uptimeMillis() + 3000;
        while (stable < 3 && SystemClock.uptimeMillis() < until) {
            AccessibilityNodeInfo current = find(description);
            Rect bounds = new Rect();
            if (current != null) { current.getBoundsInScreen(bounds); }
            stable = !bounds.isEmpty() && bounds.equals(previous) ? stable + 1 : 0;
            previous = bounds;
            SystemClock.sleep(50);
        }
        tap(node(description));
    }

    protected AccessibilityNodeInfo node(String description) throws Exception {
        try { await(() -> find(description) != null); }
        catch (AssertionError error) {
            android.graphics.Bitmap screenshot = getInstrumentation().getUiAutomation().takeScreenshot();
            if (screenshot != null) {
                try (java.io.FileOutputStream output = new java.io.FileOutputStream(
                        new java.io.File(activity.getExternalCacheDir(), "audit-failure.png"))) {
                    screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output);
                } finally { screenshot.recycle(); }
            }
            for (AccessibilityWindowInfo window : getInstrumentation().getUiAutomation().getWindows()) { dump(window.getRoot()); }
            throw error;
        }
        AccessibilityNodeInfo node = find(description);
        assertNotNull(description, node);
        return node;
    }

    protected void dump(AccessibilityNodeInfo node) {
        if (node == null) { return; }
        if (node.isVisibleToUser() && node.getContentDescription() != null) {
            System.out.println("可见节点=" + node.getContentDescription());
        }
        for (int i = 0; i < node.getChildCount(); i++) { dump(node.getChild(i)); }
    }

    protected Rect bounds(String description) throws Exception {
        Rect bounds = new Rect(); node(description).getBoundsInScreen(bounds); return bounds;
    }

    protected void tap(AccessibilityNodeInfo node) { tap(node, 100); }

    protected void tap(AccessibilityNodeInfo node, long delay) {
        Rect bounds = new Rect(); node.getBoundsInScreen(bounds);
        long time = SystemClock.uptimeMillis();
        MotionEvent down = MotionEvent.obtain(time, time, MotionEvent.ACTION_DOWN, bounds.centerX(), bounds.centerY(), 0);
        MotionEvent up = MotionEvent.obtain(time, time + 40, MotionEvent.ACTION_UP, bounds.centerX(), bounds.centerY(), 0);
        down.setSource(InputDevice.SOURCE_TOUCHSCREEN); up.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        getInstrumentation().getUiAutomation().injectInputEvent(down, true);
        getInstrumentation().getUiAutomation().injectInputEvent(up, true);
        down.recycle(); up.recycle();
        SystemClock.sleep(delay);
    }

    protected AccessibilityNodeInfo find(String description) {
        for (AccessibilityWindowInfo window : getInstrumentation().getUiAutomation().getWindows()) {
            AccessibilityNodeInfo root = window.getRoot();
            AccessibilityNodeInfo match = search(root, description, false);
            if (match != null) { return match; }
        }
        return null;
    }

    protected AccessibilityNodeInfo findPrefix(AccessibilityNodeInfo node, String prefix) { return search(node, prefix, true); }

    protected AccessibilityNodeInfo search(AccessibilityNodeInfo node, String text, boolean prefix) {
        if (node == null) { return null; }
        String description = node.getContentDescription() == null ? "" : node.getContentDescription().toString();
        if (node.isVisibleToUser() && (prefix ? description.startsWith(text) : description.equals(text))) { return node; }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo result = search(node.getChild(i), text, prefix);
            if (result != null) { return result; }
        }
        return null;
    }

    protected void await(BooleanSupplier condition) throws Exception {
        long until = SystemClock.uptimeMillis() + 10000;
        while (!condition.getAsBoolean() && SystemClock.uptimeMillis() < until) { SystemClock.sleep(50); }
        assertTrue("等待输入状态超时，消息=" + activity.message.getText() + "，数字=" + activity.number.getText(), condition.getAsBoolean());
    }

    protected void shell(String command) throws Exception {
        try (ParcelFileDescriptor descriptor = getInstrumentation().getUiAutomation().executeShellCommand(command);
             FileInputStream stream = new FileInputStream(descriptor.getFileDescriptor())) {
            byte[] buffer = new byte[1024]; while (stream.read(buffer) != -1) { /* 等待 shell 操作完成。 */ }
        }
    }
}
