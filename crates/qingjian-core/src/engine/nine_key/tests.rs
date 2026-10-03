//! 九键候选、消歧、分段上屏与旧方案隔离的回归测试。

use qingjian_dictionary::Dictionary;

use crate::Engine;

use super::encode;

fn engine() -> Engine {
    let dictionary = Dictionary::parse("你好\tni hao\t9000\n你\tni\t20000\n好\thao\t15000\n米\tmi\t3000\n号\thao\t1000\n我\two\t30000\n喜欢\txi huan\t18000\n编程\tbian cheng\t15000\n女\tnv\t1000\n").unwrap();
    let mut engine = Engine::new(dictionary);
    engine.set_nine_key(true);
    engine
}

#[test]
fn maps_all_standard_keys_and_umlaut() {
    assert_eq!(
        encode("abcdefghijklmnopqrstuvwxyz"),
        "22233344455566677778889999"
    );
    assert_eq!(encode("ni hao"), "64426");
    assert_eq!(encode("nv"), "68");
}

#[test]
fn whole_word_and_incomplete_final_syllable() {
    let mut engine = engine();
    for digit in "64426".chars() {
        engine.push(digit);
    }
    let query = engine.query().unwrap();
    assert_eq!(query.candidates.items[0].text, "你好");
    assert_eq!(query.marked_text(), "ni'hao");
    assert_eq!(engine.commit(&query.candidates.items[0]), "你好");
    assert!(engine.composition().is_empty());
    engine.set_input("6442");
    assert!(
        engine
            .query()
            .unwrap()
            .candidates
            .items
            .iter()
            .any(|c| c.text == "你好")
    );
}

#[test]
fn pinyin_choice_filters_digit_collision_and_backspace_unlocks() {
    let mut engine = engine();
    engine.set_input("64");
    assert!(engine.nine_key_readings().contains(&"mi".to_owned()));
    assert!(engine.lock_nine_key_syllable("mi"));
    assert!(!engine.lock_nine_key_syllable("ni"));
    assert_eq!(engine.query().unwrap().candidates.items[0].text, "米");
    assert!(engine.backspace());
    engine.push('4');
    assert_eq!(engine.query().unwrap().candidates.items[0].text, "你");
    assert!(!engine.lock_nine_key_syllable("invalid"));
}

#[test]
fn selecting_prefix_keeps_remaining_digits() {
    let mut engine = engine();
    engine.set_input("64426");
    let query = engine.query().unwrap();
    let candidate = query
        .candidates
        .items
        .iter()
        .find(|c| c.text == "你")
        .unwrap();
    assert_eq!(engine.commit(candidate), "你");
    assert_eq!(engine.composition().text(), "426");
    assert_eq!(engine.query().unwrap().candidates.items[0].text, "好");
}

#[test]
fn builds_sentence_without_a_whole_sentence_dictionary_entry() {
    let mut engine = engine();
    engine.set_input(&encode("woxihuanbiancheng"));
    let query = engine.query().unwrap();
    assert_eq!(query.candidates.items[0].text, "我喜欢编程");
    assert_eq!(engine.commit(&query.candidates.items[0]), "我喜欢编程");
    assert!(engine.composition().is_empty());
}

#[test]
fn confirmed_syllables_survive_prefix_commit() {
    let mut engine = engine();
    engine.set_input("64426");
    assert!(engine.lock_nine_key_syllable("ni"));
    assert!(engine.lock_nine_key_syllable("hao"));
    let candidate = engine
        .query()
        .unwrap()
        .candidates
        .items
        .into_iter()
        .find(|c| c.text == "你")
        .unwrap();
    engine.commit(&candidate);
    assert!(engine.nine_key_readings().is_empty());
    assert_eq!(engine.query().unwrap().candidates.items[0].text, "好");
}

#[test]
fn empty_invalid_and_long_inputs_are_bounded() {
    let mut engine = engine();
    for text in ["", "1", "nihao", "20", &"6".repeat(65)] {
        engine.set_input(text);
        assert!(engine.query().unwrap().candidates.items.is_empty());
    }
}

#[test]
fn switching_back_preserves_full_pinyin_behavior() {
    let mut engine = engine();
    engine.set_input("64");
    engine.set_nine_key(false);
    assert!(engine.composition().is_empty());
    engine.set_input("nihao");
    assert_eq!(engine.query().unwrap().candidates.items[0].text, "你好");
}
