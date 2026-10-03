// 编辑工具面板，宿主实际执行复制、剪切和光标操作。
package io.github.utyoinog.yagamiime;

import android.content.Context;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.function.Consumer;

final class EditingPanel extends LinearLayout {
    EditingPanel(Context context, Consumer<EditorCommand> action, Runnable back, Runnable settings) {
        super(context);
        setOrientation(VERTICAL);
        KeyboardStyle style = new KeyboardStyle(context);
        TextView title = new TextView(context);
        title.setText("编辑工具");
        title.setTextColor(style.text);
        title.setPadding(style.dp(10), 0, 0, 0);
        LinearLayout header = new LinearLayout(context);
        header.addView(title, new LayoutParams(0, style.dp(40), 1));
        header.addView(style.key("设置", true, settings), new LayoutParams(style.dp(72), style.dp(40)));
        addView(header);
        String[][] labels = {{"全选", "复制", "剪切"}, {"左移", "右移", "粘贴"}, {"开头", "末尾", "返回键盘"}};
        EditorCommand[][] commands = {{EditorCommand.SELECT_ALL, EditorCommand.COPY, EditorCommand.CUT},
                {EditorCommand.LEFT, EditorCommand.RIGHT, EditorCommand.PASTE}, {EditorCommand.HOME, EditorCommand.END, null}};
        for (int r = 0; r < labels.length; r++) {
            LinearLayout row = new LinearLayout(context);
            for (int c = 0; c < labels[r].length; c++) {
                EditorCommand command = commands[r][c];
                TextView key = style.key(labels[r][c], true, command == null ? back : () -> action.accept(command));
                key.setTextSize(16);
                LayoutParams cell = new LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1);
                cell.setMargins(style.dp(3), style.dp(3), style.dp(3), style.dp(3));
                row.addView(key, cell);
            }
            addView(row, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        }
    }
}
