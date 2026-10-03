// 英语单次大写、锁定大写与居中空格的真实 IME 回归。
package io.github.utyoinog.yagamiime;

import android.graphics.Rect;

public final class KeyboardEnglishTest extends KeyboardTestCase {
    public void testShiftOnceThenTwiceLocksUppercaseUntilReleased() throws Exception {
        click("EN"); click("⇧"); click("H"); click("i");
        await(() -> activity.message.getText().toString().equals("Hi"));
        click("⇧"); click("⇧ ON");
        assertEquals("大写锁定", node("⇧ LOCK").getStateDescription().toString());
        click("T"); click("H"); click("E"); click("空格"); click("R"); click("E");
        await(() -> activity.message.getText().toString().equals("HiTHE RE"));
        click("⇧ LOCK"); click("n");
        await(() -> activity.message.getText().toString().equals("HiTHE REn"));
    }

    public void testEnglishSpaceIsCenteredAndCapsLockSurvivesTemporaryPanels() throws Exception {
        click("EN");
        Rect keyboard = bounds("输入键盘"), space = bounds("空格");
        assertTrue("英语空格必须居中", Math.abs(space.centerX() - keyboard.centerX()) <= 3);
        assertEquals(bounds("123").centerY(), space.centerY());
        click("⇧"); click("⇧ ON"); click("123"); click("1"); click("ABC");
        click("A"); click("符号"); click("?"); click("ABC"); click("B");
        await(() -> activity.message.getText().toString().equals("1A?B"));
        click("中文"); click("EN"); node("a"); node("⇧");
    }
}
