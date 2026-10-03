//! 偏好恢复、隐私开关、损坏快照与有界内存的回归。

use qingjian_core::Learner;

use super::{ChoiceLearner, LIMIT};

#[test]
fn restored_choices_have_the_same_weights() {
    let mut learner = ChoiceLearner::default();
    learner.record_choice("64", "米");
    learner.record_choice("64", "米");
    let restored = ChoiceLearner::default();
    assert!(restored.restore(&learner.snapshot()));
    assert_eq!(restored.choice_weight("64", "米"), 2);
    assert_eq!(restored.choice_weight("64426", "米"), 0);
}

#[test]
fn disabled_profile_neither_reads_nor_records_choices() {
    let mut learner = ChoiceLearner::default();
    learner.record_choice("64", "米");
    let before = learner.snapshot();
    learner.set_enabled(false);
    learner.record_choice("64", "你");
    assert_eq!(learner.choice_weight("64", "米"), 0);
    assert_eq!(learner.snapshot(), before);
    learner.set_enabled(true);
    assert_eq!(learner.choice_weight("64", "米"), 1);
    learner.clear();
    assert_eq!(learner.snapshot(), "[]");
}

#[test]
fn invalid_or_sensitive_records_do_not_enter_the_snapshot() {
    let mut learner = ChoiceLearner::default();
    for (input, text) in [
        ("123456", "123456"),
        ("pwd", "Password"),
        ("64", "米\n秘密"),
        ("", "米"),
    ] {
        learner.record_choice(input, text);
    }
    assert_eq!(learner.snapshot(), "[]");
    assert!(!learner.restore("not json"));
    assert!(!learner.restore("[{\"input\":\"64\",\"text\":\"秘密\",\"count\":0}]"));
}

#[test]
fn bounded_profile_discards_the_oldest_choices() {
    let mut learner = ChoiceLearner::default();
    for index in 0..=LIMIT {
        let text = char::from_u32(0x4e00 + index as u32).unwrap().to_string();
        learner.record_choice("64", &text);
    }
    let json: serde_json::Value = serde_json::from_str(&learner.snapshot()).unwrap();
    assert_eq!(json.as_array().unwrap().len(), LIMIT);
    assert_eq!(learner.choice_weight("64", "一"), 0);
}

#[test]
fn recent_correction_outweighs_frequent_old_choices_and_survives_restore() {
    let mut learner = ChoiceLearner::default();
    for _ in 0..20 {
        learner.record_choice("64", "你");
    }
    learner.record_choice("64", "米");
    assert!(learner.choice_priority("64", "米") > learner.choice_priority("64", "你"));
    let restored = ChoiceLearner::default();
    assert!(restored.restore(&learner.snapshot()));
    assert!(restored.choice_priority("64", "米") > restored.choice_priority("64", "你"));
}

#[test]
fn user_chosen_phrase_recall_requires_repetition_and_is_private() {
    let mut learner = ChoiceLearner::default();
    let candidate = qingjian_core::Candidate {
        text: "米猫".to_owned(),
        kind: qingjian_core::CandidateKind::Chinese,
        syllables: vec!["mi".to_owned(), "mao".to_owned()],
        reading: None,
        translation: None,
        aux_code: None,
    };
    learner.record(&candidate);
    learner.record_choice("64626", "米猫");
    assert!(learner.recalled_candidates("64626").is_empty());
    learner.record(&candidate);
    learner.record_choice("64626", "米猫");
    let restored = ChoiceLearner::default();
    assert!(restored.restore(&learner.snapshot()));
    assert_eq!(restored.recalled_candidates("64626")[0].text, "米猫");
    restored.set_enabled(false);
    assert!(restored.recalled_candidates("64626").is_empty());
}
