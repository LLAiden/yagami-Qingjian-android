// 加密保存不透明的 Core 选词快照，后台串行写入不阻塞按键与编辑器更新。
package io.github.utyoinog.yagamiime;

import android.content.Context;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class LearningStore implements AutoCloseable {
    private final EncryptedStore encrypted;
    private final ExecutorService writer = Executors.newSingleThreadExecutor();

    LearningStore(Context context) { encrypted = new EncryptedStore(context, "learning"); }

    String load() { return encrypted.get("choices", "[]"); }

    void save(String snapshot) {
        if (!snapshot.isEmpty()) { writer.execute(() -> encrypted.put("choices", snapshot)); }
    }

    void clear() { writer.execute(() -> encrypted.remove("choices")); }

    @Override public void close() { writer.shutdown(); }
}
