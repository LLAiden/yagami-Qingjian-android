// 快速数字与双指交替点击必须逐字提交，不能遗漏跨行按键。
package io.github.utyoinog.yagamiime;

import android.content.Context;
import android.graphics.Rect;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.inputmethod.InputMethodManager;

public final class KeyboardRapidInputTest extends KeyboardTestCase {
    public void testRestartWhileDigitIsPressedDoesNotLoseTheDigit() throws Exception {
        Rect[] keys = numberKeys();
        for (int repeat = 0; repeat < 20; repeat++) {
            restartSequence(keys, repeat);
        }
    }

    private void restartSequence(Rect[] keys, int repeat) throws Exception {
        for (int index = 0; index < 3; index++) { press(keys[index]); }
        await(() -> activity.number.getText().toString().equals("123456".repeat(repeat) + "123"));
        long down = SystemClock.uptimeMillis();
        event(down, MotionEvent.ACTION_DOWN, new Rect[]{keys[3]}, new int[]{0});
        getInstrumentation().runOnMainSync(() -> ((InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE))
                .restartInput(activity.number));
        SystemClock.sleep(200);
        event(down, MotionEvent.ACTION_UP, new Rect[]{keys[3]}, new int[]{0});
        press(keys[4]); press(keys[5]);
        await(() -> activity.number.getText().toString().equals("123456".repeat(repeat + 1)));
    }

    private void press(Rect key) {
        long down = SystemClock.uptimeMillis();
        event(down, MotionEvent.ACTION_DOWN, new Rect[]{key}, new int[]{0});
        SystemClock.sleep(10);
        event(down, MotionEvent.ACTION_UP, new Rect[]{key}, new int[]{0});
        SystemClock.sleep(10);
    }

    public void testTapsBetweenKeyBackgroundsDoNotDisappear() throws Exception {
        Rect[] keys = numberKeys();
        Rect horizontal = new Rect((keys[0].right + keys[1].left) / 2 + 1, keys[1].centerY(),
                (keys[0].right + keys[1].left) / 2 + 3, keys[1].centerY() + 2);
        Rect vertical = new Rect(keys[3].centerX(), (keys[0].bottom + keys[3].top) / 2 + 1,
                keys[3].centerX() + 2, (keys[0].bottom + keys[3].top) / 2 + 3);
        for (Rect key : new Rect[]{keys[0], horizontal, vertical}) {
            long down = SystemClock.uptimeMillis();
            event(down, MotionEvent.ACTION_DOWN, new Rect[]{key}, new int[]{0});
            SystemClock.sleep(10);
            event(down, MotionEvent.ACTION_UP, new Rect[]{key}, new int[]{0});
        }
        await(() -> activity.number.getText().toString().equals("124"));
    }

    public void testRapidNumericTapsPreserveEveryDigit() throws Exception {
        Rect[] keys = numberKeys();
        for (int repeat = 0; repeat < 100; repeat++) {
            for (Rect key : keys) {
                long down = SystemClock.uptimeMillis();
                event(down, MotionEvent.ACTION_DOWN, new Rect[]{key}, new int[]{0});
                SystemClock.sleep(10);
                event(down, MotionEvent.ACTION_UP, new Rect[]{key}, new int[]{0});
                SystemClock.sleep(10);
            }
        }
        await(() -> activity.number.getText().toString().equals("123456".repeat(100)));
    }

    public void testOverlappingTwoFingerNumericTapsPreserveEveryDigit() throws Exception {
        Rect[] keys = numberKeys();
        for (int repeat = 0; repeat < 100; repeat++) {
            for (int index = 0; index < keys.length; index += 2) {
                long down = SystemClock.uptimeMillis();
                Rect[] pair = {keys[index], keys[index + 1]};
                event(down, MotionEvent.ACTION_DOWN, new Rect[]{pair[0]}, new int[]{0});
                SystemClock.sleep(10);
                event(down, MotionEvent.ACTION_POINTER_DOWN | (1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT), pair, new int[]{0, 1});
                SystemClock.sleep(10);
                event(down, MotionEvent.ACTION_POINTER_UP, pair, new int[]{0, 1});
                SystemClock.sleep(10);
                event(down, MotionEvent.ACTION_UP, new Rect[]{pair[1]}, new int[]{1});
            }
        }
        await(() -> activity.number.getText().toString().equals("123456".repeat(100)));
    }

    private Rect[] numberKeys() throws Exception {
        getInstrumentation().runOnMainSync(() -> {
            activity.number.requestFocus();
            ((InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE))
                    .showSoftInput(activity.number, InputMethodManager.SHOW_IMPLICIT);
        });
        Rect[] keys = new Rect[6];
        for (int i = 0; i < keys.length; i++) { keys[i] = bounds(Integer.toString(i + 1)); }
        return keys;
    }

    private void event(long down, int action, Rect[] keys, int[] ids) {
        MotionEvent.PointerProperties[] properties = new MotionEvent.PointerProperties[keys.length];
        MotionEvent.PointerCoords[] coordinates = new MotionEvent.PointerCoords[keys.length];
        for (int i = 0; i < keys.length; i++) {
            properties[i] = new MotionEvent.PointerProperties();
            properties[i].id = ids[i]; properties[i].toolType = MotionEvent.TOOL_TYPE_FINGER;
            coordinates[i] = new MotionEvent.PointerCoords();
            coordinates[i].x = keys[i].centerX(); coordinates[i].y = keys[i].centerY();
            coordinates[i].pressure = 1; coordinates[i].size = .1f;
        }
        MotionEvent event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, keys.length,
                properties, coordinates, 0, 0, 1, 1, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0);
        getInstrumentation().getUiAutomation().injectInputEvent(event, true);
        event.recycle();
    }
}
