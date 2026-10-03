// 候选显隐和三种输入布局必须符合页面顺序，并保持按键的屏幕位置。
package io.github.utyoinog.yagamiime;

import android.graphics.Rect;

public final class KeyboardLayoutTest extends KeyboardTestCase {
    public void testIdleCandidatesAreHiddenAndKeysStayInPlace() throws Exception {
        assertNull("空闲时不显示候选区域", find("候选列表"));
        Rect before = bounds("拼音键 5 JKL");
        typeDigits("64");
        Rect after = bounds("拼音键 5 JKL");
        assertTrue("候选显示不能移动按键", Math.abs(before.centerY() - after.centerY()) <= 3);
        click("候选 你");
        await(() -> activity.message.getText().toString().equals("你"));
        await(() -> find("候选列表") == null);
        assertEquals(before.centerY(), bounds("拼音键 5 JKL").centerY());
    }

    public void testPageOrderIsCandidatesClipboardMenuThenKeys() throws Exception {
        typeDigits("64426");
        Rect candidates = bounds("候选区域"), clipboard = bounds("剪贴板栏");
        Rect menu = bounds("编辑菜单"), keys = bounds("按键区域");
        assertTrue(candidates.bottom <= clipboard.top);
        assertTrue(clipboard.bottom <= menu.top);
        assertTrue(menu.bottom <= keys.top);
        assertTrue(node("候选释义 你好").isVisibleToUser());
    }

    public void testThreeModesAreNineKeyPinyinEnglishAndNumbers() throws Exception {
        assertNull(find("九键")); assertNull(find("全拼"));
        click("EN"); click("n"); click("i");
        await(() -> activity.message.getText().toString().equals("ni"));
        assertNull(find("候选列表"));
        click("123"); click("1"); click("5"); click("9");
        await(() -> activity.message.getText().toString().equals("ni159"));
        assertNull(find("候选列表"));
        click("ABC"); node("q"); click("中文"); node("拼音键 5 JKL");
        typeDigits("64426"); click("候选 你好");
        await(() -> activity.message.getText().toString().equals("ni159你好"));
    }

    public void testSwitchingToNumbersCommitsAndHidesPinyin() throws Exception {
        typeDigits("64426"); click("123");
        await(() -> activity.message.getText().toString().equals("你好"));
        assertNull(find("候选列表"));
        click("1"); click("拼音");
        await(() -> activity.message.getText().toString().equals("你好1"));
        node("拼音键 5 JKL");
    }

    public void testMenuIconsAreCompactAndAlignedRight() throws Exception {
        Rect keyboard = bounds("输入键盘"), clipboard = bounds("剪贴板");
        Rect edit = bounds("编辑"), select = bounds("全选"), hide = bounds("收起键盘");
        int size = Math.round(48 * activity.getResources().getDisplayMetrics().density);
        for (String name : new String[]{"剪贴板", "编辑", "全选", "收起键盘"}) {
            assertTrue("菜单只显示图标", android.text.TextUtils.isEmpty(node(name).getText()));
            assertTrue(bounds(name).width() >= size);
            assertTrue(bounds(name).width() <= size + 1);
        }
        assertTrue(keyboard.right - clipboard.right <= 10);
        assertTrue(keyboard.right - hide.right <= 10);
        assertEquals(edit.right, select.left); assertEquals(select.right, hide.left);
    }

    public void testAllModesUseEnglishPunctuation() throws Exception {
        typeDigits("64"); click("选择拼音 ni"); click(",");
        await(() -> activity.message.getText().toString().equals("你,"));
        click("符号"); click("?"); click("拼音");
        click("EN"); click("."); click("符号"); click("!"); click("ABC");
        click("123"); click("符号"); click("更多"); click(";"); click("返回数字");
        click("1");
        await(() -> activity.message.getText().toString().equals("你,?.!;1"));
    }

    public void testNineKeyTouchesStayValidWhileCandidatesAppear() throws Exception {
        Rect[] digits = {bounds("拼音键 6 MNO"), bounds("拼音键 4 GHI"),
                bounds("拼音键 4 GHI"), bounds("拼音键 2 ABC"), bounds("拼音键 6 MNO")};
        for (int round = 0; round < 10; round++) {
            for (Rect digit : digits) {
                long time = android.os.SystemClock.uptimeMillis();
                for (int action : new int[]{android.view.MotionEvent.ACTION_DOWN, android.view.MotionEvent.ACTION_UP}) {
                    android.view.MotionEvent event = android.view.MotionEvent.obtain(time, time, action, digit.centerX(), digit.centerY(), 0);
                    event.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);
                    getInstrumentation().getUiAutomation().injectInputEvent(event, true); event.recycle();
                }
                android.os.SystemClock.sleep(50);
            }
            click("候选 你好");
            final int count = round + 1;
            await(() -> activity.message.getText().length() == count * 2);
        }
        assertEquals("你好你好你好你好你好你好你好你好你好你好", activity.message.getText().toString());
    }
}
