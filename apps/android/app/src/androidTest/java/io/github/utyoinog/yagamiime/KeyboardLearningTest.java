// 复用真实 JNI 与后台加密存储，验证个人排序、禁用与恢复。
package io.github.utyoinog.yagamiime;

import android.content.Context;
import android.content.ClipData;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;

public final class KeyboardLearningTest extends KeyboardTestCase {
    public void testExplicitChoicePersistsEncryptedAndPrivateInputIgnoresIt() throws Exception {
        Context context = getInstrumentation().getTargetContext();
        EncryptedStore saved = new EncryptedStore(context, "learning"); saved.remove("choices");
        try (NativeBridge bridge = bridge(context); LearningStore storage = new LearningStore(context)) {
            bridge.startSession(false, true); bridge.setNineKey(true); push(bridge, "64");
            String baseline = first(bridge);
            JSONArray candidates = new JSONObject(bridge.snapshot()).getJSONArray("candidates");
            String chosen = candidates.getJSONObject(1).getString("text");
            assertFalse(baseline.equals(chosen)); assertEquals(chosen, bridge.commit(1));
            String snapshot = bridge.learningSnapshot(); assertFalse(snapshot.isEmpty());
            storage.save(snapshot);
            await(() -> saved.get("choices", "[]").equals(snapshot));
            assertFalse(LocalStorage.open(context, "learning").containsKey("choices"));
            try (NativeBridge restored = bridge(context)) {
                assertTrue(restored.restoreLearning(storage.load()));
                restored.setNineKey(true); push(restored, "64"); assertEquals(chosen, first(restored));
                restored.startSession(true, false); push(restored, "64"); assertEquals(baseline, first(restored));
                restored.commit(1); assertEquals("", restored.learningSnapshot());
                restored.startSession(false, true); push(restored, "64"); assertEquals(chosen, first(restored));
                restored.clearLearning(); push(restored, "64"); assertEquals(baseline, first(restored));
            }
        } finally { saved.remove("choices"); }
    }

    public void testAutomaticCommitDoesNotLearnAndInvalidSnapshotDoesNotStopInput() throws Exception {
        try (NativeBridge bridge = bridge(getInstrumentation().getTargetContext())) {
            bridge.setNineKey(true); assertFalse(bridge.restoreLearning("broken"));
            push(bridge, "64426"); assertEquals("你好", first(bridge)); assertEquals("你好", bridge.commitFirst());
            assertEquals("", bridge.learningSnapshot());
            push(bridge, "64426"); assertEquals("你好", first(bridge)); assertEquals("你好", bridge.commit(0));
            assertFalse(bridge.learningSnapshot().isEmpty());
        }
    }

    public void testFullPersonalProfileCandidateTimings() throws Exception {
        JSONArray records = new JSONArray();
        for (int i = 0; i < 4096; i++) {
            JSONObject record = new JSONObject();
            record.put("input", "64"); record.put("text", String.valueOf((char) (0x4e00 + i)));
            record.put("count", 2); record.put("order", i + 1);
            record.put("syllables", new JSONArray().put("mi")); records.put(record);
        }
        java.util.List<Long> samples = new java.util.ArrayList<>();
        try (NativeBridge bridge = bridge(getInstrumentation().getTargetContext())) {
            assertTrue(bridge.restoreLearning(records.toString())); bridge.setNineKey(true);
            for (int repeat = 0; repeat < 5; repeat++) {
                for (String input : new String[]{"64426", "966498264", "968437"}) {
                    bridge.clear();
                    for (char digit : input.toCharArray()) {
                        long start = System.nanoTime(); bridge.push(digit); new JSONObject(bridge.snapshot());
                        if (repeat > 0) { samples.add(System.nanoTime() - start); }
                    }
                }
            }
            assertEquals("未修改的完整偏好不重复序列化", "", bridge.learningSnapshot());
            java.util.List<Long> collision = new java.util.ArrayList<>();
            for (int i = 0; i < 32; i++) {
                bridge.clear(); bridge.push('6'); bridge.push('4');
                long started = System.nanoTime(); new JSONObject(bridge.snapshot());
                collision.add(System.nanoTime() - started);
            }
            java.util.Collections.sort(collision);
            System.out.println("PERF profile4096 samecode native+JSON samples=32 p95ms="
                    + collision.get(30)/1_000_000.0 + " maxms=" + collision.get(31)/1_000_000.0);
        }
        java.util.Collections.sort(samples);
        System.out.println("PERF profile4096 native+JSON samples=" + samples.size() + " p50ms="
                + samples.get(samples.size()/2)/1_000_000.0 + " p95ms="
                + samples.get((int) Math.ceil(samples.size()*.95)-1)/1_000_000.0);
        assertEquals(80, samples.size());
    }

    public void prepareReleaseUpgrade() throws Exception {
        Context context = getInstrumentation().getTargetContext();
        try (NativeBridge bridge = bridge(context); LearningStore storage = new LearningStore(context)) {
            bridge.setNineKey(true); push(bridge, "64"); bridge.snapshot();
            JSONArray candidates = new JSONObject(bridge.snapshot()).getJSONArray("candidates");
            int chosen = -1;
            for (int i = 0; i < candidates.length(); i++) {
                if (candidates.getJSONObject(i).getString("text").equals("米")) { chosen = i; break; }
            }
            assertTrue(chosen >= 0); assertEquals("米", bridge.commit(chosen));
            String snapshot = bridge.learningSnapshot(); storage.save(snapshot);
            await(() -> storage.load().equals(snapshot));
        }
        getInstrumentation().runOnMainSync(() -> {
            ClipboardStore clipboard = new ClipboardStore(context); clipboard.clear();
            clipboard.manager().setPrimaryClip(ClipData.newPlainText("upgrade", "升级保留验收"));
            clipboard.capture(false); clipboard.pin(clipboard.entries().get(0));
        });
        LocalStorage.open(context, "keyboard").edit().putInt("learning", 1).putInt("theme", 2)
                .putInt("height", 1).putBoolean("chinese", true).commit();
        LocalStorage.open(context, "keyboard").sync();
        LocalStorage.open(context, "clipboard").sync(); LocalStorage.open(context, "learning").sync();
        android.os.SystemClock.sleep(1000);
    }

    private NativeBridge bridge(Context context) {
        return new NativeBridge(new File(context.getFilesDir(), "dict.tsv").getAbsolutePath(),
                new File(context.getFilesDir(), "glossary-en.tsv").getAbsolutePath());
    }
    private void push(NativeBridge bridge, String keys) { for (char digit : keys.toCharArray()) { assertTrue(bridge.push(digit)); } }
    private String first(NativeBridge bridge) throws Exception {
        return new JSONObject(bridge.snapshot()).getJSONArray("candidates").getJSONObject(0).getString("text");
    }
}
