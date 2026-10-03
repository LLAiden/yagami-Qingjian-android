// 剪贴板面板复用键盘工具栏，点击内容粘贴，固定和删除不触碰编辑器。
package io.github.utyoinog.yagamiime;

import android.content.Context;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.function.Consumer;
import java.util.List;

final class ClipboardPanel extends LinearLayout {
    private final ClipboardStore store;
    private final KeyboardStyle style;
    private final Consumer<String> paste;
    private final boolean privateEditor;

    ClipboardPanel(Context context, ClipboardStore store, boolean privateEditor, Consumer<String> paste) {
        super(context);
        this.store = store; this.paste = paste; this.privateEditor = privateEditor;
        style = new KeyboardStyle(context);
        setOrientation(VERTICAL);
        refresh();
    }

    private void refresh() {
        removeAllViews();
        LinearLayout header = new LinearLayout(getContext());
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(getContext());
        title.setText(privateEditor ? "剪贴板 · 隐私输入" : "剪贴板 · 普通记录保留 24 小时");
        title.setTextColor(style.text);
        title.setTextSize(13);
        header.addView(title, new LayoutParams(0, style.dp(40), 1));
        header.addView(style.key("清空", true, () -> { store.clear(); refresh(); }), new LayoutParams(style.dp(64), style.dp(36)));
        addView(header);
        ScrollView scroll = new ScrollView(getContext());
        LinearLayout items = new LinearLayout(getContext());
        items.setOrientation(VERTICAL);
        scroll.addView(items);
        String current = store.current();
        List<ClipboardStore.Entry> entries = privateEditor ? java.util.Collections.emptyList() : store.entries();
        ClipboardStore.Entry currentEntry = null;
        for (ClipboardStore.Entry entry : entries) {
            if (entry.text.equals(current)) { currentEntry = entry; break; }
        }
        if (!current.isEmpty()) {
            addItem(items, current, "当前复制", currentEntry);
        }
        if (!privateEditor) {
            for (ClipboardStore.Entry entry : entries) {
                if (entry.text.equals(current)) { continue; }
                addItem(items, entry.text, entry.pinned ? "已固定" : "历史", entry);
            }
        }
        if (items.getChildCount() == 0) {
            TextView empty = new TextView(getContext());
            empty.setText("复制文字后，可在这里点击粘贴");
            empty.setTextColor(style.text);
            empty.setGravity(Gravity.CENTER);
            items.addView(empty, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, style.dp(100)));
        }
        addView(scroll, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
    }

    private void addItem(LinearLayout items, String text, String label, ClipboardStore.Entry entry) {
        LinearLayout row = new LinearLayout(getContext());
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView content = style.key(text, false, () -> paste.accept(text));
        content.setTextSize(15);
        content.setGravity(Gravity.CENTER_VERTICAL);
        content.setMaxLines(2);
        content.setPadding(style.dp(10), 0, style.dp(10), 0);
        content.setContentDescription("粘贴 " + label + " " + text);
        row.addView(content, new LayoutParams(0, style.dp(60), 1));
        if (entry != null) {
            TextView pin = style.key(entry.pinned ? "取消固定" : "固定", true, () -> { store.pin(entry); refresh(); });
            pin.setTextSize(12);
            row.addView(pin, new LayoutParams(style.dp(60), style.dp(48)));
            TextView delete = style.key("删除", true, () -> { store.remove(entry); refresh(); });
            delete.setTextSize(12);
            row.addView(delete, new LayoutParams(style.dp(48), style.dp(48)));
        }
        LayoutParams params = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, style.dp(66));
        params.setMargins(0, style.dp(2), 0, style.dp(2));
        items.addView(row, params);
    }
}
