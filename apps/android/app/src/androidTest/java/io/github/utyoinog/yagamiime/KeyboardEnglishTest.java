// 英语单次大写、锁定大写与居中空格的真实 IME 回归。
package io.github.utyoinog.yagamiime;

import android.graphics.Rect;

public final class KeyboardEnglishTest extends KeyboardTestCase {
    public void testShiftOnceThenTwiceLocksUppercaseUntilReleased() throws Exception {
        click("切换英语模式"); click("⇧"); click("H"); click("i");
        await(() -> activity.message.getText().toString().equals("Hi"));
        click("⇧"); click("⇧ ON");
        assertEquals("大写锁定", node("⇧ LOCK").getStateDescription().toString());
        click("T"); click("H"); click("E"); click("空格"); click("R"); click("E");
        await(() -> activity.message.getText().toString().equals("HiTHE RE"));
        click("⇧ LOCK"); click("n");
        await(() -> activity.message.getText().toString().equals("HiTHE REn"));
    }

    public void testEnglishSpaceIsCenteredAndCapsLockSurvivesTemporaryPanels() throws Exception {
        click("切换英语模式");
        Rect keyboard = bounds("输入键盘"), space = bounds("空格");
        assertTrue("英语空格必须居中", Math.abs(space.centerX() - keyboard.centerX()) <= 3);
        assertEquals(bounds("123").centerY(), space.centerY());
        assertEquals("数字切换紧邻空格左侧", bounds("123").right, space.left);
        assertEquals("拼音切换紧邻空格右侧", space.right, bounds("中文").left);
        click("⇧"); click("⇧ ON"); click(find("切换数字模式") != null ? "切换数字模式" : "123"); click("1"); click(find("切换英语模式") != null ? "切换英语模式" : "ABC");
        click("A"); click("符号"); click("?"); click(find("切换英语模式") != null ? "切换英语模式" : "ABC"); click("B");
        await(() -> activity.message.getText().toString().equals("1A?B"));
        click("中文"); click("切换英语模式"); node("a"); node("⇧");
    }
}
