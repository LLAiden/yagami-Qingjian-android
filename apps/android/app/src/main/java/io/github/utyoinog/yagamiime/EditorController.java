// 宿主编辑器的选区删除和 Unicode 退格；不参与拼音解析。
package io.github.utyoinog.yagamiime;

import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.inputmethod.InputConnection;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class EditorController {
    private static final Pattern GRAPHEME = Pattern.compile("\\X");

    static boolean deleteSelection(InputConnection connection, boolean selected) {
        CharSequence text = connection.getSelectedText(0);
        if (!TextUtils.isEmpty(text)) {
            connection.beginBatchEdit();
            try {
                connection.finishComposingText();
                return connection.commitText("", 1);
            } finally {
                connection.endBatchEdit();
            }
        }
        // 密码框和部分自定义编辑器不返回选中文字，交由宿主执行标准删除。
        if (selected) {
            sendDelete(connection);
            return true;
        }
        return false;
    }

    static void backspace(InputConnection connection) {
        CharSequence before = connection.getTextBeforeCursor(128, 0);
        if (TextUtils.isEmpty(before)) {
            sendDelete(connection);
            return;
        }
        Matcher matcher = GRAPHEME.matcher(before);
        int length = 1;
        while (matcher.find()) { length = matcher.end() - matcher.start(); }
        if (!connection.deleteSurroundingText(length, 0)) { sendDelete(connection); }
    }

    private static void sendDelete(InputConnection connection) {
        connection.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL));
        connection.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL));
    }
}
