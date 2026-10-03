// 只保存带完整性校验的密文；密钥由系统 Keystore 保管，加密不可用时不退回明文。
package io.github.utyoinog.yagamiime;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import com.tencent.mmkv.MMKV;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

final class EncryptedStore {
    private static final String ALIAS = "yagami.local-data.v1";
    private static SecretKey cachedKey;
    private final MMKV store;
    private final String scope;
    private SecretKey key;
    private volatile boolean available = true;

    EncryptedStore(Context context, String name) {
        store = LocalStorage.open(context, name);
        scope = context.getPackageName() + ":" + name + ":";
        try { key = key(); }
        catch (Exception error) { available = false; }
    }

    boolean available() { return available; }

    String get(String name, String fallback) {
        String saved = store.decodeString("secure_" + name + "_v1", "");
        if (saved.isEmpty() || key == null) { return fallback; }
        try {
            byte[] payload = Base64.decode(saved, Base64.NO_WRAP);
            if (payload.length < 29 || payload[0] != 1) { throw new IllegalArgumentException(); }
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, payload, 1, 12));
            cipher.updateAAD((scope + name).getBytes(StandardCharsets.UTF_8));
            return new String(cipher.doFinal(payload, 13, payload.length - 13), StandardCharsets.UTF_8);
        } catch (Exception error) { available = false; return fallback; }
    }

    boolean put(String name, String value) {
        if (key == null || !available) { return false; }
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key);
            cipher.updateAAD((scope + name).getBytes(StandardCharsets.UTF_8));
            byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            byte[] payload = new byte[13 + encrypted.length];
            payload[0] = 1;
            System.arraycopy(cipher.getIV(), 0, payload, 1, 12);
            System.arraycopy(encrypted, 0, payload, 13, encrypted.length);
            boolean saved = store.encode("secure_" + name + "_v1", Base64.encodeToString(payload, Base64.NO_WRAP));
            if (!saved) { available = false; }
            return saved;
        } catch (Exception error) { available = false; return false; }
    }

    void migrate(String name) {
        if (!store.containsKey(name)) { return; }
        String old = store.decodeString(name, "[]");
        // 没有密钥时删除旧明文，继续提供本次会话的即时粘贴，不重新写明文。
        boolean migrated = put(name, old);
        if (migrated) { store.sync(); }
        store.removeValueForKey(name);
        store.trim(); store.sync();
    }

    void remove(String name) {
        store.removeValueForKey("secure_" + name + "_v1");
        store.removeValueForKey(name);
        store.trim(); store.sync();
        available = key != null;
    }

    private static synchronized SecretKey key() throws Exception {
        if (cachedKey != null) { return cachedKey; }
        KeyStore keys = KeyStore.getInstance("AndroidKeyStore");
        keys.load(null);
        if (!keys.containsAlias(ALIAS)) {
            KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            generator.init(new KeyGenParameterSpec.Builder(ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256).build());
            generator.generateKey();
        }
        cachedKey = (SecretKey) keys.getKey(ALIAS, null);
        if (cachedKey == null) { throw new IllegalStateException("本地密钥不可用"); }
        return cachedKey;
    }
}
