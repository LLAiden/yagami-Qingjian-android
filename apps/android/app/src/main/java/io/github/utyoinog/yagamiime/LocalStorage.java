// 按用途隔离的 MMKV；首次打开时迁移旧设置，完成落盘后清除旧副本。
package io.github.utyoinog.yagamiime;

import android.content.Context;
import android.content.SharedPreferences;

import com.tencent.mmkv.MMKV;

import java.util.HashMap;
import java.util.Map;

final class LocalStorage {
    private static final String MIGRATED = "_sp_migrated_v1";
    private static final Map<String, MMKV> stores = new HashMap<>();
    private static boolean initialized;

    static synchronized MMKV open(Context context, String name) {
        MMKV existing = stores.get(name);
        if (existing != null) { return existing; }
        if (!initialized) {
            MMKV.initialize(context.getApplicationContext());
            initialized = true;
        }
        MMKV store = MMKV.mmkvWithID("yagami_" + name);
        if (store == null) { throw new IllegalStateException("无法打开本地设置"); }
        SharedPreferences legacy = context.getSharedPreferences(name, Context.MODE_PRIVATE);
        if (!store.decodeBool(MIGRATED, false)) {
            int count = legacy.getAll().size();
            if (store.importFromSharedPreferences(legacy) != count) {
                throw new IllegalStateException("本地设置迁移未完成，旧数据已保留");
            }
            store.sync();
            store.encode(MIGRATED, true);
            store.sync();
        }
        // 中断后再打开也会清理旧副本；已迁移标记避免清空历史后重新导入。
        if (!legacy.getAll().isEmpty()) { legacy.edit().clear().commit(); }
        stores.put(name, store);
        return store;
    }
}
