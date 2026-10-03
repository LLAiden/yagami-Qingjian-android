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
    private final SharedPreferences settings;
    private final EncryptedStore encrypted;
    private final ClipboardManager clipboard;
    private final List<Entry> entries = new ArrayList<>();
    private String savedHistory;

    ClipboardStore(Context context) {
        preferences = LocalStorage.open(context, "clipboard");
        settings = LocalStorage.open(context, "keyboard");
        encrypted = new EncryptedStore(context, "clipboard");
        encrypted.migrate("history");
        clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        reload();
        prune();
    }

    private void reload() {
        if (savedHistory != null && !encrypted.available()) { return; }
        String saved = encrypted.get("history", "[]");
        if (saved.equals(savedHistory)) { return; }
        savedHistory = saved;
        entries.clear();
        try {
            JSONArray items = new JSONArray(saved);
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.getJSONObject(i);
                entries.add(new Entry(item.getString("text"), item.optBoolean("pinned"), item.optLong("time")));
            }
        } catch (Exception ignored) { entries.clear(); }
    }

    ClipboardManager manager() { return clipboard; }

    boolean persistent() { return encrypted.available(); }

    boolean historyEnabled() { return settings.getInt("clipboard_history", 1) == 1; }

    boolean currentSensitive() {
        ClipData data = currentClip();
        return data != null && ClipboardPrivacy.sensitive(data.getDescription(), plainText(data));
    }

    String current() {
        return plainText(currentClip());
    }

    private ClipData currentClip() {
        try {
            return clipboard.getPrimaryClip();
        } catch (SecurityException ignored) { return null; }
    }

    private static String plainText(ClipData data) {
        if (data == null || data.getItemCount() == 0) { return ""; }
        CharSequence text = data.getItemAt(0).getText();
        return text == null ? "" : text.toString();
    }

    void capture(boolean privateEditor) {
        reload();
        ClipData data = currentClip();
        String text = plainText(data);
        ClipDescription description = data == null ? null : data.getDescription();
        long copiedAt = description == null ? 0 : description.getTimestamp();
        // 仅保留复制事件的时间戳，不落盘私密文本；服务重建后继续拦截同一次复制。
        if (privateEditor) {
            if (copiedAt > 0) { preferences.edit().putLong("private_copy_time", copiedAt).apply(); }
            return;
        }
        if (copiedAt > 0 && copiedAt == preferences.getLong("private_copy_time", -1)) { return; }
        if (copiedAt > 0 && copiedAt == preferences.getLong("suppressed_copy_time", -1)) { return; }
        if (copiedAt > 0) { preferences.edit().remove("private_copy_time").apply(); }
        if (!historyEnabled() || ClipboardPrivacy.sensitive(description, text)) { return; }
        if (TextUtils.isEmpty(text) || text.length() > 10000) { return; }
        Entry existing = null;
        for (Entry entry : entries) { if (entry.text.equals(text)) { existing = entry; break; } }
        boolean pinned = existing != null && existing.pinned;
        if (existing != null) { entries.remove(existing); }
        // 读取和打开面板不能给旧内容续期；系统时间戳只在真正复制时更新。
        if (copiedAt <= 0) { copiedAt = existing == null ? System.currentTimeMillis() : existing.time; }
        entries.add(0, new Entry(text, pinned, copiedAt));
        prune();
        save();
    }

    List<Entry> entries() { reload(); prune(); return new ArrayList<>(entries); }

    void pin(Entry entry) {
        reload();
        for (Entry stored : entries) {
            if (stored.text.equals(entry.text)) { stored.pinned = !stored.pinned; break; }
        }
        prune(); save();
    }

    void remove(Entry entry) {
        reload();
        entries.removeIf(stored -> stored.text.equals(entry.text));
        // 当前复制也是历史条目时，一并删除系统副本，避免返回面板后又被记录。
        if (entry.text.equals(current())) { clearCurrent(); }
        save();
    }

    void clear() {
        entries.clear();
        preferences.edit().remove("private_copy_time").apply();
        clearCurrent();
        save();
    }

    void clearHistory() {
        entries.clear(); savedHistory = "[]";
        ClipData current = currentClip();
        if (current != null && current.getDescription().getTimestamp() > 0) {
            preferences.edit().putLong("suppressed_copy_time", current.getDescription().getTimestamp()).apply();
        }
        encrypted.remove("history");
    }

    private void clearCurrent() {
        if (Build.VERSION.SDK_INT >= 28) { clipboard.clearPrimaryClip(); }
        else { clipboard.setPrimaryClip(ClipData.newPlainText("", "")); }
    }

    private void prune() {
        int previousSize = entries.size();
        long oldest = System.currentTimeMillis() - EXPIRY;
        entries.removeIf(entry -> !entry.pinned && entry.time < oldest || ClipboardPrivacy.sensitive(null, entry.text));
        entries.sort((a, b) -> a.pinned == b.pinned ? Long.compare(b.time, a.time) : (a.pinned ? -1 : 1));
        while (entries.size() > LIMIT) { entries.remove(entries.size() - 1); }
        if (entries.size() != previousSize) { save(); }
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
        savedHistory = saved.toString();
        encrypted.put("history", savedHistory);
    }
}
