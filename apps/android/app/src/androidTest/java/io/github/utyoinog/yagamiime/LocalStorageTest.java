// 验证旧设置与剪贴板安全迁移，清空后不能再次导入旧副本。
package io.github.utyoinog.yagamiime;

import android.content.Context;
import android.content.SharedPreferences;
import android.test.InstrumentationTestCase;

import com.tencent.mmkv.MMKV;

@SuppressWarnings("deprecation")
public final class LocalStorageTest extends InstrumentationTestCase {
    public void testMigrationPreservesSettingsAndClipboardMetadata() {
        Context context = getInstrumentation().getTargetContext();
        String name = "migration_" + System.nanoTime();
        SharedPreferences old = context.getSharedPreferences(name, Context.MODE_PRIVATE);
        old.edit().putBoolean("nine_key", false).putInt("theme", 2).putInt("height", 1)
                .putLong("private_copy_time", 123456789)
                .putString("history", "[{\"text\":\"固定内容\",\"pinned\":true,\"time\":123}]").commit();
        MMKV store = LocalStorage.open(context, name);
        store.clearMemoryCache();
        assertFalse(store.getBoolean("nine_key", true));
        assertEquals(2, store.getInt("theme", 0));
        assertEquals(1, store.getInt("height", 0));
        assertEquals(123456789, store.getLong("private_copy_time", 0));
        assertTrue(store.getString("history", "").contains("固定内容"));
        assertTrue("迁移完成后清除旧副本", old.getAll().isEmpty());
        store.putString("history", "[]");
        store.sync(); store.clearMemoryCache();
        assertEquals("[]", MMKV.mmkvWithID("yagami_" + name).getString("history", "error"));
        store.clearAll();
    }
}
