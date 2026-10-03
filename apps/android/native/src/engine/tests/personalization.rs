//! 聚合偏好跨 Engine 恢复，以及密码 / 关闭学习不读写个人排序。

use std::path::PathBuf;

use crate::engine::AndroidEngine;

fn engine() -> AndroidEngine {
    let assets = PathBuf::from(env!("CARGO_MANIFEST_DIR")).join("../../../assets");
    let mut engine = AndroidEngine::open(
        &assets.join("lexicon/dict.tsv"),
        &assets.join("glossary/glossary-en.tsv"),
    )
    .unwrap();
    engine.engine.set_nine_key(true);
    engine
}

fn first(android: &mut AndroidEngine) -> String {
    android.snapshot();
    android.candidates()[0].text.clone()
}

#[test]
fn explicit_choice_becomes_first_and_survives_recreation() {
    let mut android = engine();
    android.engine.set_input("64");
    let original = first(&mut android);
    let chosen = android
        .candidates()
        .into_iter()
        .find(|item| item.text != original && item.syllables.len() == 1)
        .unwrap();
    android.engine.commit(&chosen);
    let saved = android.learning_snapshot();
    assert!(!saved.is_empty());
    let mut restored = engine();
    assert!(restored.restore_learning(&saved));
    restored.engine.set_input("64");
    assert_eq!(first(&mut restored), chosen.text);
    assert!(restored.learning_snapshot().is_empty());
}

#[test]
fn private_or_disabled_input_neither_learns_nor_exposes_personal_ranking() {
    let mut android = engine();
    android.engine.set_input("64");
    let baseline = first(&mut android);
    let chosen = android
        .candidates()
        .into_iter()
        .find(|item| item.text != baseline && item.syllables.len() == 1)
        .unwrap();
    android.engine.commit(&chosen);
    let snapshot = android.learning_snapshot();
    for (private, enabled) in [(true, true), (false, false)] {
        android.privacy(private, enabled);
        android.engine.set_input("64");
        assert_eq!(first(&mut android), baseline);
        android.engine.commit(&chosen);
        assert!(android.learning_snapshot().is_empty());
    }
    android.privacy(false, true);
    android.engine.set_input("64");
    assert_eq!(first(&mut android), chosen.text);
    assert!(!snapshot.is_empty());
}

#[test]
fn clear_personalization_restores_default_order() {
    let mut android = engine();
    android.engine.set_input("64");
    let baseline = first(&mut android);
    let chosen = android
        .candidates()
        .into_iter()
        .find(|item| item.text != baseline && item.syllables.len() == 1)
        .unwrap();
    android.engine.commit(&chosen);
    android.learner.clear();
    android.engine.set_input("64");
    assert_eq!(first(&mut android), baseline);
    assert_eq!(android.learning_snapshot(), "[]");
}

#[test]
fn explicit_sentence_choice_changes_the_complete_input_order() {
    let mut android = engine();
    let keys = "96944826242624364";
    android.engine.set_input(keys);
    android.snapshot();
    let chosen = android
        .candidates()
        .into_iter()
        .find(|candidate| candidate.text == "我喜欢编程")
        .unwrap();
    assert_eq!(android.engine.commit(&chosen), "我喜欢编程");
    android.engine.set_input(keys);
    assert_eq!(first(&mut android), "我喜欢编程");
}

#[test]
fn automatic_commit_does_not_teach_the_default_answer() {
    let mut android = engine();
    android.engine.set_input("64");
    android.snapshot();
    assert!(!android.commit(0, false).is_empty());
    assert!(android.learning_snapshot().is_empty());
    android.engine.set_input("64");
    android.snapshot();
    assert!(!android.commit(1, true).is_empty());
    assert!(!android.learning_snapshot().is_empty());
}

#[test]
fn repeated_partial_choices_build_a_user_phrase_without_an_added_dictionary() {
    let mut android = engine();
    for _ in 0..2 {
        android.engine.set_input("64626");
        for text in ["米", "猫"] {
            android.snapshot();
            let index = android
                .candidates()
                .iter()
                .position(|candidate| candidate.text == text)
                .unwrap();
            assert_eq!(android.commit(index, true), text);
        }
    }
    let mut restored = engine();
    assert!(restored.restore_learning(&android.learning_snapshot()));
    restored.engine.set_input("64626");
    assert_eq!(first(&mut restored), "米猫");
    assert!(restored.engine.lock_nine_key_syllable("ni"));
    restored.snapshot();
    assert!(
        !restored
            .candidates()
            .iter()
            .any(|candidate| candidate.text == "米猫")
    );
}
