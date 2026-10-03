// 针对实际候选释义、拼音分隔和按键触控区域的交互验收。
package io.github.utyoinog.yagamiime;

import android.graphics.Rect;
import android.view.accessibility.AccessibilityNodeInfo;

public final class KeyboardVisualTest extends KeyboardTestCase {
    public void testBottomRightActionHasVisibleIconAndLabel() throws Exception {
        Rect action = bounds("换行");
        android.graphics.Bitmap screenshot = getInstrumentation().getUiAutomation().takeScreenshot();
        assertNotNull(screenshot);
        int foreground = 0;
        boolean dark = new KeyboardPreferences(activity).dark;
        int inset = Math.round(8 * activity.getResources().getDisplayMetrics().density);
        try {
            for (int y = action.top + inset; y < action.bottom - inset; y++) {
                for (int x = action.left + inset; x < action.right - inset; x++) {
                    int color = screenshot.getPixel(x, y);
                    boolean visible = dark
                            ? android.graphics.Color.red(color) < 100 && android.graphics.Color.green(color) < 100
                                    && android.graphics.Color.blue(color) < 100
                            : android.graphics.Color.red(color) > 220 && android.graphics.Color.green(color) > 220
                                    && android.graphics.Color.blue(color) > 220;
                    if (visible) { foreground++; }
                }
            }
        } finally { screenshot.recycle(); }
        assertTrue("右下角动作图标与文字必须实际可见，前景像素=" + foreground, foreground > 150);
    }

    public void testEditorActionStaysAtBottomRightAcrossLayouts() throws Exception {
        assertBottomRight();
        click("123"); assertBottomRight();
        click("符号"); assertBottomRight();
        click("返回数字"); click("拼音"); click("EN"); assertBottomRight();
    }

    private void assertBottomRight() throws Exception {
        Rect action = bounds("换行"), keyboard = bounds("按键区域");
        int inset = Math.round(10 * activity.getResources().getDisplayMetrics().density);
        assertTrue("动作键应在最右列", keyboard.right - action.right <= inset);
        assertTrue("动作键应在最底行", keyboard.bottom - action.bottom <= inset);
    }

    public void testEnglishIsVisibleAndOnlyChineseIsCommitted() throws Exception {
        typeDigits("64426");
        AccessibilityNodeInfo gloss = node("候选释义 你好");
        assertTrue(gloss.getText().toString().contains("hello"));
        assertTrue(gloss.isVisibleToUser());
        click("展开候选");
        assertTrue(node("展开释义 你好").getText().toString().contains("hello"));
        click("展开候选 你好");
        await(() -> activity.message.getText().toString().equals("你好"));
    }

    public void testPinyinChoicesHaveSeparateTouchAreas() throws Exception {
        typeDigits("64");
        Rect mi = bounds("选择拼音 mi"), ni = bounds("选择拼音 ni");
        int gap = Math.round(4 * activity.getResources().getDisplayMetrics().density);
        assertTrue("拼音选项之间需要可见间距", ni.left - mi.right >= gap);
        click("选择拼音 ni"); click("候选 你");
        await(() -> activity.message.getText().toString().equals("你"));
    }

    public void testFullKeyboardDeleteHasUsableTouchArea() throws Exception {
        click("EN");
        Rect delete = bounds("删除文字"), letter = bounds("m");
        int minimum = Math.round(48 * activity.getResources().getDisplayMetrics().density);
        assertTrue("删除键不能小于 48dp", delete.width() >= minimum && delete.height() >= minimum);
        assertTrue("删除键应宽于普通字母键", delete.width() > letter.width() * 1.4f);
        click("n"); click("i"); click("删除文字");
        await(() -> !activity.message.getText().toString().contains("ni"));
    }

    public void testNineKeyGridIsCenteredAndAllDigitsAreVisible() throws Exception {
        Rect keyboard = bounds("输入键盘"), five = bounds("拼音键 5 JKL");
        assertTrue("九键主区域需要居中", Math.abs(keyboard.centerX() - five.centerX()) <= 3);
        assertTrue(node("拼音键 1 符号").getText().toString().startsWith("1"));
        assertEquals(five.centerX(), bounds("拼音键 2 ABC").centerX());
        assertEquals(five.centerX(), bounds("拼音键 8 TUV").centerX());
        click("123");
        assertTrue("数字九宫格也需要居中", Math.abs(bounds("输入键盘").centerX() - bounds("5").centerX()) <= 3);
        click("1"); click("5"); click("9"); click("0");
        await(() -> activity.message.getText().toString().equals("1590"));
    }
}
