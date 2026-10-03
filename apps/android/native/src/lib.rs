//! Android JNI 壳：持有平台无关的 Engine，并把候选快照序列化给 Java UI。

mod engine;
mod snapshot;

use std::path::Path;

use engine::AndroidEngine;
use jni::JNIEnv;
use jni::objects::{JClass, JString};
use jni::sys::{jboolean, jint, jlong, jstring};

fn from_handle<'a>(handle: jlong) -> Option<&'a mut AndroidEngine> {
    if handle == 0 {
        return None;
    }
    // SAFETY：Java 从 nativeCreate 到 nativeDestroy 独占此指针，所有操作在同一串行引擎队列执行。
    unsafe { (handle as *mut AndroidEngine).as_mut() }
}

fn java_string(env: &mut JNIEnv<'_>, text: &str) -> jstring {
    env.new_string(text)
        .map_or(std::ptr::null_mut(), JString::into_raw)
}

fn path(env: &mut JNIEnv<'_>, value: JString<'_>) -> Option<String> {
    env.get_string(&value).ok().map(|value| value.into())
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_io_github_utyoinog_yagamiime_NativeBridge_nativeCreate(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    dictionary: JString<'_>,
    glossary: JString<'_>,
) -> jlong {
    let (Some(dictionary), Some(glossary)) = (path(&mut env, dictionary), path(&mut env, glossary))
    else {
        return 0;
    };
    AndroidEngine::open(Path::new(&dictionary), Path::new(&glossary))
        .map(|engine| Box::into_raw(Box::new(engine)) as jlong)
        .unwrap_or(0)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_io_github_utyoinog_yagamiime_NativeBridge_nativeDestroy(
    _env: JNIEnv<'_>,
    _class: JClass<'_>,
    handle: jlong,
) {
    if handle != 0 {
        // SAFETY：句柄由 nativeCreate 分配，NativeBridge 只关闭一次。
        unsafe { drop(Box::from_raw(handle as *mut AndroidEngine)) };
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_io_github_utyoinog_yagamiime_NativeBridge_nativePush(
    _env: JNIEnv<'_>,
    _class: JClass<'_>,
    handle: jlong,
    code_point: jint,
) -> jboolean {
    let Some(engine) = from_handle(handle) else {
        return 0;
    };
    let Some(character) = char::from_u32(code_point as u32) else {
        return 0;
    };
    if engine.engine.composition().text().len() >= 64 {
        return 0;
    }
    engine.engine.push(character);
    1
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_io_github_utyoinog_yagamiime_NativeBridge_nativeSetNineKey(
    _env: JNIEnv<'_>,
    _class: JClass<'_>,
    handle: jlong,
    enabled: jboolean,
) {
    if let Some(engine) = from_handle(handle) {
        engine.engine.set_nine_key(enabled != 0);
        engine.displayed.clear();
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_io_github_utyoinog_yagamiime_NativeBridge_nativeLockReading(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    handle: jlong,
    reading: JString<'_>,
) {
    if let (Some(engine), Some(reading)) = (from_handle(handle), path(&mut env, reading)) {
        engine.engine.lock_nine_key_syllable(&reading);
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_io_github_utyoinog_yagamiime_NativeBridge_nativeBackspace(
    _env: JNIEnv<'_>,
    _class: JClass<'_>,
    handle: jlong,
) -> jboolean {
    from_handle(handle).is_some_and(|engine| engine.engine.backspace()) as jboolean
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_io_github_utyoinog_yagamiime_NativeBridge_nativeClear(
    _env: JNIEnv<'_>,
    _class: JClass<'_>,
    handle: jlong,
) {
    if let Some(engine) = from_handle(handle) {
        engine.engine.clear();
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_io_github_utyoinog_yagamiime_NativeBridge_nativeCommit(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    handle: jlong,
    index: jint,
) -> jstring {
    let Some(engine) = from_handle(handle) else {
        return java_string(&mut env, "");
    };
    let Some(candidate) = engine.candidates().get(index.max(0) as usize).cloned() else {
        return java_string(&mut env, "");
    };
    let committed = engine.engine.commit(&candidate);
    java_string(&mut env, &committed)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_io_github_utyoinog_yagamiime_NativeBridge_nativeTakeRaw(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    handle: jlong,
) -> jstring {
    let text = from_handle(handle)
        .map(|engine| engine.engine.take_raw())
        .unwrap_or_default();
    java_string(&mut env, &text)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_io_github_utyoinog_yagamiime_NativeBridge_nativeSnapshot(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    handle: jlong,
) -> jstring {
    let json = from_handle(handle)
        .and_then(|engine| serde_json::to_string(&engine.snapshot()).ok())
        .unwrap_or_else(|| "{\"preedit\":\"\",\"candidates\":[]}".to_owned());
    java_string(&mut env, &json)
}
