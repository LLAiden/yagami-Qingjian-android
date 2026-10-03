// 验证密文完整性、作用域隔离、旧明文迁移与敏感字段边界。
package io.github.utyoinog.yagamiime;

import android.content.Context;
import android.content.ClipDescription;
import android.os.PersistableBundle;
import android.test.InstrumentationTestCase;
import android.text.InputType;
import android.view.inputmethod.EditorInfo;
import com.tencent.mmkv.MMKV;
import java.nio.charset.StandardCharsets;

@SuppressWarnings("deprecation")
public final class EncryptedStoreTest extends InstrumentationTestCase {
    public void testEncryptionReopensWithoutPlaintextAndUsesFreshNonce() {
        Context context = getInstrumentation().getTargetContext();
        String name = "cipher_" + System.nanoTime();
        EncryptedStore store = new EncryptedStore(context, name);
        MMKV raw = LocalStorage.open(context, name);
        String secret = "合成选词隐私内容";
        assertTrue(store.put("choices", secret));
        String first = raw.decodeString("secure_choices_v1", "");
        assertFalse(first.contains(secret));
        assertFalse(new String(android.util.Base64.decode(first, android.util.Base64.NO_WRAP),
                StandardCharsets.UTF_8).contains(secret));
        assertFalse(raw.containsKey("choices"));
        assertEquals(secret, new EncryptedStore(context, name).get("choices", "missing"));
        assertTrue(store.put("choices", secret));
        assertFalse(first.equals(raw.decodeString("secure_choices_v1", "")));
        store.remove("choices");
        assertEquals("missing", store.get("choices", "missing"));
    }

    public void testCipherCannotBeMovedAcrossScopesAndClearRecovers() {
        Context context = getInstrumentation().getTargetContext();
        String name = "scope_" + System.nanoTime();
        EncryptedStore first = new EncryptedStore(context, name);
        assertTrue(first.put("choices", "合成测试"));
        MMKV from = LocalStorage.open(context, name), to = LocalStorage.open(context, name + "_other");
        to.encode("secure_choices_v1", from.decodeString("secure_choices_v1", ""));
        EncryptedStore other = new EncryptedStore(context, name + "_other");
        assertEquals("fallback", other.get("choices", "fallback"));
        assertFalse(other.available());
        assertFalse("解密失败不得退回明文写入", other.put("choices", "明文"));
        other.remove("choices");
        assertTrue(other.put("choices", "重新学习"));
        assertEquals("重新学习", other.get("choices", "fallback"));
        first.remove("choices"); other.remove("choices");
    }

    public void testDamagedCipherDoesNotCrashOrReturnContent() {
        Context context = getInstrumentation().getTargetContext();
        String name = "damaged_" + System.nanoTime();
        LocalStorage.open(context, name).encode("secure_choices_v1", "broken");
        EncryptedStore store = new EncryptedStore(context, name);
        assertEquals("[]", store.get("choices", "[]"));
        assertFalse(store.available());
        store.remove("choices");
    }

    public void testLegacyClipboardMetadataMigratesAndPlainSlotIsRemoved() {
        Context context = getInstrumentation().getTargetContext();
        String name = "secure_migration_" + System.nanoTime();
        String saved = "[{\"text\":\"迁移保留\",\"pinned\":true,\"time\":123}]";
        MMKV raw = LocalStorage.open(context, name);
        raw.encode("history", saved);
        EncryptedStore store = new EncryptedStore(context, name);
        store.migrate("history");
        assertFalse(raw.containsKey("history"));
        assertEquals(saved, new EncryptedStore(context, name).get("history", "[]"));
        store.remove("history"); store.migrate("history");
        assertEquals("[]", store.get("history", "[]"));
    }

    public void testSensitiveClipboardAndEditorDeclarations() {
        assertTrue(ClipboardPrivacy.sensitive(null, "123456"));
        assertTrue(ClipboardPrivacy.sensitive(null, "1234 5678 1234 5678"));
        assertFalse(ClipboardPrivacy.sensitive(null, "正常文本"));
        ClipDescription description = new ClipDescription("test", new String[]{"text/plain"});
        PersistableBundle extra = new PersistableBundle(); extra.putBoolean("android.content.extra.IS_SENSITIVE", true);
        description.setExtras(extra);
        assertTrue(ClipboardPrivacy.sensitive(description, "任意敏感字符串"));
        EditorInfo info = new EditorInfo(); info.inputType = InputType.TYPE_CLASS_TEXT;
        assertTrue(EditorPrivacy.personalized(info));
        info.imeOptions = EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING;
        assertTrue(EditorPrivacy.privateInput(info)); assertFalse(EditorPrivacy.personalized(info));
        info.imeOptions = 0; info.hintText = "短信验证码";
        assertTrue(EditorPrivacy.privateInput(info));
        info.hintText = null; info.inputType |= InputType.TYPE_TEXT_VARIATION_URI;
        assertFalse(EditorPrivacy.personalized(info));
        info.inputType = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD;
        assertTrue(EditorPrivacy.privateInput(info));
    }
}
