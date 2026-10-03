//! Android Engine 装配、查询与上屏的回归测试。

use std::path::PathBuf;

use crate::engine::AndroidEngine;

fn sample(name: &str) -> PathBuf {
    PathBuf::from(env!("CARGO_MANIFEST_DIR"))
        .join("../../../assets/sample")
        .join(name)
}

#[test]
fn queries_and_commits_with_translation() {
    let mut android = AndroidEngine::open(&sample("dict.tsv"), &sample("glossary-en.tsv"))
        .expect("样例数据应该能加载");
    android.engine.push('n');
    android.engine.push('i');

    let json = serde_json::to_value(android.snapshot()).expect("候选快照应该能序列化");
    assert_eq!(json["preedit"], "ni");
    assert_eq!(json["candidates"][0]["text"], "你");
    assert_eq!(json["candidates"][0]["gloss"], "you");

    let candidate = android.candidates().remove(0);
    assert_eq!(android.engine.commit(&candidate), "你");
    assert!(android.engine.composition().is_empty());
}

#[test]
fn nine_key_snapshot_preserves_selection_and_remaining_input() {
    let mut android = AndroidEngine::open(&sample("dict.tsv"), &sample("glossary-en.tsv"))
        .expect("样例数据应该能加载");
    android.engine.set_nine_key(true);
    android.engine.set_input("64426");
    let json = serde_json::to_value(android.snapshot()).expect("快照可序列化");
    assert_eq!(json["raw"], "64426");
    assert_eq!(json["candidates"][0]["text"], "你好");
    let selected = android
        .candidates()
        .into_iter()
        .find(|candidate| candidate.text == "你")
        .expect("允许先选择短候选");
    assert_eq!(android.engine.commit(&selected), "你");
    let next = serde_json::to_value(android.snapshot()).expect("快照可序列化");
    assert_eq!(next["raw"], "426");
    assert_eq!(next["candidates"][0]["text"], "好");
}
