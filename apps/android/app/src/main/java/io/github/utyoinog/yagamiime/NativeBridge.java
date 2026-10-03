package io.github.utyoinog.yagamiime;

final class NativeBridge implements AutoCloseable {
    // 与 nativePush 的 ASCII 拼音长度限制同步，用于显示输入上限。
    static final int MAX_INPUT_LENGTH = 64;
    static {
        System.loadLibrary("yagami_android_native");
    }

    private long handle;

    NativeBridge(String dictionary, String glossary) {
        handle = nativeCreate(dictionary, glossary);
        if (handle == 0) {
            throw new IllegalStateException("无法加载输入法词库");
        }
    }

    boolean push(char character) {
        return nativePush(handle, character);
    }

    boolean backspace() {
        return nativeBackspace(handle);
    }

    void clear() {
        nativeClear(handle);
    }

    String commit(int index) {
        return nativeCommit(handle, index);
    }

    String takeRaw() {
        return nativeTakeRaw(handle);
    }

    String snapshot() {
        return nativeSnapshot(handle);
    }

    void setNineKey(boolean enabled) {
        nativeSetNineKey(handle, enabled);
    }

    void setFuzzy(int mask) { nativeSetFuzzy(handle, mask); }

    void lockReading(String reading) {
        nativeLockReading(handle, reading);
    }

    @Override
    public void close() {
        if (handle != 0) {
            nativeDestroy(handle);
            handle = 0;
        }
    }

    private static native long nativeCreate(String dictionary, String glossary);
    private static native void nativeDestroy(long handle);
    private static native boolean nativePush(long handle, int codePoint);
    private static native boolean nativeBackspace(long handle);
    private static native void nativeClear(long handle);
    private static native String nativeCommit(long handle, int index);
    private static native String nativeTakeRaw(long handle);
    private static native String nativeSnapshot(long handle);
    private static native void nativeSetNineKey(long handle, boolean enabled);
    private static native void nativeSetFuzzy(long handle, int mask);
    private static native void nativeLockReading(long handle, String reading);
}
