// 在真实 IME 窗口验证敏感预览、即时粘贴与历史清理边界。
package io.github.utyoinog.yagamiime;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.view.accessibility.AccessibilityNodeInfo;

public final class KeyboardPrivacyTest extends KeyboardTestCase {
    public void testSensitiveCopyIsMaskedButCanPaste() throws Exception {
        Context context = getInstrumentation().getTargetContext();
        getInstrumentation().runOnMainSync(() -> {
            new ClipboardStore(context).clear();
            ((ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE))
                    .setPrimaryClip(ClipData.newPlainText("synthetic otp", "123456"));
        });
        click("剪贴板");
        assertNull(find("粘贴 当前复制 123456"));
        click("粘贴当前敏感内容");
        await(() -> activity.message.getText().toString().equals("123456"));
        assertTrue(new ClipboardStore(context).entries().isEmpty());
    }

    public void testClearHistoryPreservesCurrentAndPreventsRecapture() throws Exception {
        Context context = getInstrumentation().getTargetContext();
        getInstrumentation().runOnMainSync(() -> {
            ClipboardStore store = new ClipboardStore(context); store.clear();
            store.manager().setPrimaryClip(ClipData.newPlainText("test", "清除前内容"));
            store.capture(false); assertEquals(1, store.entries().size());
            store.clearHistory(); store.capture(false);
            assertEquals("清除前内容", store.current()); assertTrue(store.entries().isEmpty());
            ClipboardStore reopened = new ClipboardStore(context); reopened.capture(false);
            assertTrue(reopened.entries().isEmpty());
            reopened.manager().setPrimaryClip(ClipData.newPlainText("test", "新的复制"));
            reopened.capture(false); assertEquals("新的复制", reopened.entries().get(0).text);
        });
    }

    public void testPrivacySettingsDisableAndClearHistory() throws Exception {
        Context context = getInstrumentation().getTargetContext();
        getInstrumentation().runOnMainSync(() -> {
            ClipboardStore store = new ClipboardStore(context); store.clear();
            store.manager().setPrimaryClip(ClipData.newPlainText("test", "历史开关验收")); store.capture(false);
        });
        click("编辑"); click("设置"); scrollTo("剪贴板历史"); click("剪贴板历史");
        await(() -> LocalStorage.open(context, "keyboard").getInt("clipboard_history", 1) == 0);
        assertTrue(new ClipboardStore(context).entries().isEmpty());
        assertEquals("历史开关验收", new ClipboardStore(context).current());
        scrollTo("本地选词学习"); click("本地选词学习");
        await(() -> LocalStorage.open(context, "keyboard").getInt("learning", 0) == 1);
        scrollTo("清除学习数据"); click("清除学习数据");
        click("返回键盘"); typeDigits("64426"); click("候选 你好");
        await(() -> activity.message.getText().toString().equals("你好"));
    }

    private void scrollTo(String description) throws Exception {
        for (int i = 0; i < 8 && find(description) == null; i++) {
            node("键盘设置列表").performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);
            android.os.SystemClock.sleep(150);
        }
        node(description);
    }
}
