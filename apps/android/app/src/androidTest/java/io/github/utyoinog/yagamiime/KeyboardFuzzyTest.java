// 鼻音设置必须落盘并驱动 JNI；九键模糊选词不能吃掉后续数字。
package io.github.utyoinog.yagamiime;

import android.content.Context;
import android.graphics.Rect;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.MotionEvent;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;

public final class KeyboardFuzzyTest extends KeyboardTestCase {
    public void testNasalSettingsPersistAndEnableFullPinyinCandidates() throws Exception {
        click("编辑"); click("设置");
        click("模糊音 an/ang"); click("模糊音 en/eng"); click("模糊音 in/ing");
        click("返回键盘"); click("收起键盘");
        await(() -> find("收起键盘") == null); showMessage();
        Context context = getInstrumentation().getTargetContext();
        assertEquals(7, new KeyboardPreferences(context).nasal);
        try (NativeBridge bridge = new NativeBridge(new File(context.getFilesDir(), "dict.tsv").getAbsolutePath(),
                new File(context.getFilesDir(), "glossary-en.tsv").getAbsolutePath())) {
            bridge.setFuzzy(new KeyboardPreferences(context).nasal);
            for (String[] sample : new String[][]{{"sang", "三"}, {"feng", "分"}, {"xing", "新"}}) {
                bridge.clear();
                for (char letter : sample[0].toCharArray()) { bridge.push(letter); }
                JSONArray candidates = new JSONObject(bridge.snapshot()).getJSONArray("candidates");
                boolean found = false;
                for (int i = 0; i < candidates.length(); i++) { found |= candidates.getJSONObject(i).getString("text").equals(sample[1]); }
                assertTrue("全拼鼻音应命中 " + sample[1], found);
            }
        }
    }

    public void testNineKeyFuzzyPrefixPreservesRemainingInput() throws Exception {
        click("编辑"); click("设置"); click("模糊音 in/ing"); click("返回键盘");
        typeDigits("946426"); click("选择拼音 xin");
        click("展开候选");
        for (int attempt = 0; attempt < 12 && find("展开候选 星") == null; attempt++) {
            Rect list = bounds("展开候选列表");
            swipeUp(list);
        }
        click("展开候选 星"); click("候选 好");
        await(() -> activity.message.getText().toString().equals("星好"));
        click("编辑"); click("设置"); click("模糊音 in/ing");
        await(() -> new KeyboardPreferences(getInstrumentation().getTargetContext()).nasal == 0);
    }

    private void swipeUp(Rect area) {
        long down = SystemClock.uptimeMillis();
        for (int step = 0; step <= 12; step++) {
            int action = step == 0 ? MotionEvent.ACTION_DOWN : step == 12 ? MotionEvent.ACTION_UP : MotionEvent.ACTION_MOVE;
            MotionEvent event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action,
                    area.centerX(), area.bottom - 20 - (area.height() - 40) * step / 12f, 0);
            event.setSource(InputDevice.SOURCE_TOUCHSCREEN);
            getInstrumentation().getUiAutomation().injectInputEvent(event, true); event.recycle();
            SystemClock.sleep(16);
        }
    }
}
