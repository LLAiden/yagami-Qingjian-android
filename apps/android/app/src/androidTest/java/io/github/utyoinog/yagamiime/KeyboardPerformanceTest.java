// 用产品词库测 JNI 查询与原生视图刷新，结果仅代表此设备上的分段耗时。
package io.github.utyoinog.yagamiime;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;
import android.view.ViewGroup;

import org.json.JSONObject;

import java.io.File;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class KeyboardPerformanceTest extends KeyboardTestCase {
    public void testCandidatePipelineTimings() throws Exception {
        Context context = getInstrumentation().getTargetContext();
        List<Long> queries = new ArrayList<>(), frames = new ArrayList<>();
        int width = context.getResources().getDisplayMetrics().widthPixels;
        KeyboardView[] keyboard = new KeyboardView[1];
        getInstrumentation().runOnMainSync(() -> keyboard[0] = new KeyboardView(context, actions()));
        Bitmap image = Bitmap.createBitmap(width, Math.min(2400, context.getResources().getDisplayMetrics().heightPixels), Bitmap.Config.ARGB_8888);
        try (NativeBridge bridge = bridge(context)) {
            bridge.setNineKey(true);
            // 首轮用于预热；其余 80 个逐键样本包含短词与日常短句。
            for (int repeat = 0; repeat < 5; repeat++) {
                for (String input : new String[]{"64426", "966498264", "968437"}) {
                    bridge.clear();
                    for (char digit : input.toCharArray()) {
                        long started = System.nanoTime();
                        bridge.push(digit);
                        JSONObject snapshot = new JSONObject(bridge.snapshot());
                        long query = System.nanoTime() - started;
                        long[] frame = {0};
                        getInstrumentation().runOnMainSync(() -> {
                            long uiStart = System.nanoTime();
                            keyboard[0].snapshot(snapshot);
                            keyboard[0].measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                                    View.MeasureSpec.makeMeasureSpec(image.getHeight(), View.MeasureSpec.AT_MOST));
                            keyboard[0].layout(0, 0, width, keyboard[0].getMeasuredHeight());
                            keyboard[0].draw(new Canvas(image));
                            frame[0] = System.nanoTime() - uiStart;
                        });
                        if (repeat > 0) { queries.add(query); frames.add(frame[0]); }
                    }
                }
            }
        } finally { image.recycle(); }
        System.out.println("PERF native+JSON " + summary(queries));
        System.out.println("PERF update+layout+draw " + summary(frames));
        assertEquals(80, queries.size());
    }

    public void testUnchangedSnapshotKeepsCandidateViews() throws Exception {
        Context context = getInstrumentation().getTargetContext();
        JSONObject state;
        try (NativeBridge bridge = bridge(context)) {
            bridge.setNineKey(true); bridge.push('6'); bridge.push('4');
            state = new JSONObject(bridge.snapshot());
        }
        CandidateKey[] keys = new CandidateKey[2];
        getInstrumentation().runOnMainSync(() -> {
            KeyboardView view = new KeyboardView(context, actions());
            view.snapshot(state);
            keys[0] = firstCandidate(view);
            view.snapshot(state);
            keys[1] = firstCandidate(view);
        });
        assertNotNull(keys[0]);
        assertSame("相同候选不能反复销毁和重建视图", keys[0], keys[1]);
    }

    private NativeBridge bridge(Context context) {
        return new NativeBridge(new File(context.getFilesDir(), "dict.tsv").getAbsolutePath(),
                new File(context.getFilesDir(), "glossary-en.tsv").getAbsolutePath());
    }

    private KeyboardView.Actions actions() {
        return (KeyboardView.Actions) Proxy.newProxyInstance(KeyboardView.Actions.class.getClassLoader(),
                new Class<?>[]{KeyboardView.Actions.class}, (proxy, method, args) -> null);
    }

    private CandidateKey firstCandidate(View view) {
        if (view instanceof CandidateKey) { return (CandidateKey) view; }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                CandidateKey candidate = firstCandidate(group.getChildAt(i));
                if (candidate != null) { return candidate; }
            }
        }
        return null;
    }

    private String summary(List<Long> values) {
        Collections.sort(values);
        return "samples=" + values.size() + " p50_ms=" + values.get(values.size() / 2) / 1_000_000.0
                + " p95_ms=" + values.get((int) Math.ceil(values.size() * .95) - 1) / 1_000_000.0
                + " max_ms=" + values.get(values.size() - 1) / 1_000_000.0;
    }
}
