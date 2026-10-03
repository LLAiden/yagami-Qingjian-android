// 本地剪贴板历史，固定条目优先，敏感内容只允许即时粘贴。
package io.github.utyoinog.yagamiime;

import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

final class ClipboardStore {
    static final class Entry {
        final String text;
        boolean pinned;
        long time;

        Entry(String text, boolean pinned, long time) {
            this.text = text; this.pinned = pinned; this.time = time;
        }
    }

    private static final int LIMIT = 30;
    private static final long EXPIRY = 24 * 60 * 60 * 1000L;
    private final SharedPreferences preferences;
    private final ClipboardManager clipboard;
    private final List<Entry> entries = new ArrayList<>();
    private String privateCopy = "";

    ClipboardStore(Context context) {
        preferences = context.getSharedPreferences("clipboard", Context.MODE_PRIVATE);
        clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        try {
            JSONArray saved = new JSONArray(preferences.getString("history", "[]"));
            for (int i = 0; i < saved.length(); i++) {
                JSONObject item = saved.getJSONObject(i);
                entries.add(new Entry(item.getString("text"), item.optBoolean("pinned"), item.optLong("time")));
            }
        } catch (Exception ignored) { entries.clear(); }
        prune();
    }

    ClipboardManager manager() { return clipboard; }

    String current() {
        try {
            ClipData data = clipboard.getPrimaryClip();
            if (data == null || data.getItemCount() == 0) { return ""; }
            CharSequence text = data.getItemAt(0).getText();
            return text == null ? "" : text.toString();
        } catch (SecurityException ignored) { return ""; }
    }

    void capture(boolean privateEditor) {
        String text = current();
        if (privateEditor) { privateCopy = text; return; }
        // 密码框里复制的内容离开密码框后也不自动写入历史，直到复制了另一段内容。
        if (!privateCopy.isEmpty() && privateCopy.equals(text)) { return; }
        privateCopy = "";
        ClipDescription description = clipboard.getPrimaryClipDescription();
        if (description != null && description.getExtras() != null
                && description.getExtras().getBoolean("android.content.extra.IS_SENSITIVE", false)) { return; }
        if (TextUtils.isEmpty(text) || text.length() > 10000) { return; }
        Entry existing = null;
        for (Entry entry : entries) { if (entry.text.equals(text)) { existing = entry; break; } }
        boolean pinned = existing != null && existing.pinned;
        if (existing != null) { entries.remove(existing); }
        entries.add(0, new Entry(text, pinned, System.currentTimeMillis()));
        prune();
        save();
    }

    List<Entry> entries() { prune(); return new ArrayList<>(entries); }

    void pin(Entry entry) { entry.pinned = !entry.pinned; prune(); save(); }

    void remove(Entry entry) { entries.remove(entry); save(); }

    void clear() {
        entries.clear();
        privateCopy = "";
        if (Build.VERSION.SDK_INT >= 28) { clipboard.clearPrimaryClip(); }
        else { clipboard.setPrimaryClip(ClipData.newPlainText("", "")); }
        save();
    }

    private void prune() {
        long oldest = System.currentTimeMillis() - EXPIRY;
        entries.removeIf(entry -> !entry.pinned && entry.time < oldest);
        entries.sort((a, b) -> a.pinned == b.pinned ? Long.compare(b.time, a.time) : (a.pinned ? -1 : 1));
        while (entries.size() > LIMIT) { entries.remove(entries.size() - 1); }
    }

    private void save() {
        JSONArray saved = new JSONArray();
        for (Entry entry : entries) {
            try {
                JSONObject item = new JSONObject();
                item.put("text", entry.text); item.put("pinned", entry.pinned); item.put("time", entry.time);
                saved.put(item);
            } catch (Exception ignored) { /* 仅保存可序列化的文本条目。 */ }
        }
        preferences.edit().putString("history", saved.toString()).apply();
    }
}
