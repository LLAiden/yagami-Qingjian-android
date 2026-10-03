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

    public void testPageOrderIsCandidatesToolbarThenKeys() throws Exception {
        Rect header = bounds("键盘顶栏"), before = bounds("按键区域"), keyboard = bounds("输入键盘");
        typeDigits("64426");
        Rect candidates = bounds("候选区域"), keys = bounds("按键区域");
        assertEquals(header, bounds("键盘顶栏")); assertEquals(before, keys);
        assertEquals("输入不能改变键盘总高度", keyboard, bounds("输入键盘"));
        assertTrue(candidates.bottom <= keys.top);
        assertNull("输入时其余工具隐藏", find("工具栏"));
        assertNull(find("剪贴板")); assertNull(find("编辑")); assertNull(find("全选"));
        node("收起键盘");
        assertTrue(node("候选释义 你好").isVisibleToUser());
    }

    public void testThreeModesAreNineKeyPinyinEnglishAndNumbers() throws Exception {
        assertNull(find("九键")); assertNull(find("全拼"));
        click("切换英语模式"); click("n"); click("i");
        await(() -> activity.message.getText().toString().equals("ni"));
        assertNull(find("候选列表"));
        click(find("切换数字模式") != null ? "切换数字模式" : "123"); click("1"); click("5"); click("9");
        await(() -> activity.message.getText().toString().equals("ni159"));
        assertNull(find("候选列表"));
        click(find("切换英语模式") != null ? "切换英语模式" : "ABC"); node("q"); click("中文"); node("拼音键 5 JKL");
        typeDigits("64426"); click("候选 你好");
        await(() -> activity.message.getText().toString().equals("ni159你好"));
    }

    public void testSwitchingToNumbersCommitsAndHidesPinyin() throws Exception {
        typeDigits("64426"); click(find("切换数字模式") != null ? "切换数字模式" : "123");
        await(() -> activity.message.getText().toString().equals("你好"));
        assertNull(find("候选列表"));
        click("1"); click(find("切换拼音模式") != null ? "切换拼音模式" : "拼音");
        await(() -> activity.message.getText().toString().equals("你好1"));
        node("拼音键 5 JKL");
    }

    public void testMenuIconsAreCompactAndAlignedRight() throws Exception {
        Rect keyboard = bounds("输入键盘"), clipboard = bounds("剪贴板");
        Rect edit = bounds("编辑"), select = bounds("全选"), manager = bounds("密码管理器"), hide = bounds("收起键盘");
        Rect authenticator = bounds("双因素验证");
        int size = Math.round(48 * activity.getResources().getDisplayMetrics().density);
        for (String name : new String[]{"全选", "编辑", "双因素验证", "密码管理器", "剪贴板", "收起键盘"}) {
            assertTrue("菜单只显示图标", android.text.TextUtils.isEmpty(node(name).getText()));
            assertTrue(bounds(name).width() >= size);
            assertTrue(bounds(name).width() <= size + 1);
        }
        assertTrue(keyboard.right - hide.right <= 10);
        assertEquals(select.right, edit.left);
        assertEquals(edit.right, authenticator.left); assertEquals(authenticator.right, manager.left);
        assertEquals(manager.right, clipboard.left); assertEquals(clipboard.right, hide.left);
        assertEquals(clipboard.centerY(), edit.centerY());
        assertEquals(edit.centerY(), select.centerY()); assertEquals(select.centerY(), manager.centerY());
        assertEquals(manager.centerY(), authenticator.centerY()); assertEquals(authenticator.centerY(), hide.centerY());
        assertEquals("所有工具共用固定顶栏", bounds("键盘顶栏"), bounds("工具栏"));
    }

    public void testAllModesUseEnglishPunctuation() throws Exception {
        typeDigits("64"); click("选择拼音 ni"); click(",");
        await(() -> activity.message.getText().toString().equals("你,"));
        click("符号"); click("?"); click(find("切换拼音模式") != null ? "切换拼音模式" : "拼音");
        click("切换英语模式"); click("."); click("符号"); click("!"); click(find("切换英语模式") != null ? "切换英语模式" : "ABC");
        click(find("切换数字模式") != null ? "切换数字模式" : "123"); click("符号"); click("下一页"); click(";"); click("返回数字");
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

    public void testPinyinBottomHasNumbersSpaceAndEnglishInOneCellEach() throws Exception {
        Rect numbers = bounds("切换数字模式"), space = bounds("空格"), english = bounds("切换英语模式");
        assertEquals(numbers.centerY(), space.centerY()); assertEquals(space.centerY(), english.centerY());
        assertEquals(numbers.right, space.left); assertEquals(space.right, english.left);
        assertTrue(Math.abs(space.width() - bounds("拼音键 5 JKL").width()) <= 1);
        assertTrue(Math.abs(numbers.width() - space.width()) <= 1);
        assertTrue(Math.abs(english.width() - space.width()) <= 1);
        click("切换数字模式");
        Rect pinyin = bounds("切换拼音模式"), zero = bounds("0"), numericEnglish = bounds("切换英语模式");
        assertEquals(numericEnglish.right, zero.left); assertEquals(zero.right, pinyin.left);
        assertTrue(Math.abs(zero.centerX() - bounds("输入键盘").centerX()) <= 3);
        assertTrue(bounds("空格").right <= numericEnglish.left);
        click("1"); click("0"); click("切换英语模式"); click("a"); click(find("切换数字模式") != null ? "切换数字模式" : "123"); click("切换拼音模式");
        click("空格"); click("切换英语模式"); click("h"); click("i");
        await(() -> activity.message.getText().toString().equals("10a hi"));
    }

    public void testNumericEditorAllowsExplicitLanguageButtons() throws Exception {
        getInstrumentation().runOnMainSync(() -> {
            activity.number.requestFocus();
            ((android.view.inputmethod.InputMethodManager) activity.getSystemService(android.content.Context.INPUT_METHOD_SERVICE))
                    .showSoftInput(activity.number, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
        });
        node("完成"); node("5");
        click("切换英语模式"); node("q");
        click(find("切换数字模式") != null ? "切换数字模式" : "123"); click("切换拼音模式"); node("拼音键 5 JKL");
        click("切换数字模式"); click("1"); click("0");
        await(() -> activity.number.getText().toString().equals("10"));
    }
}
