//! 九键候选、消歧、分段上屏与旧方案隔离的回归测试。

use qingjian_dictionary::Dictionary;

use super::encode;
use crate::Engine;

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

#[test]
fn nasal_rules_are_bidirectional_and_switch_off_cleanly() {
    let dictionary = "三\tsan\t9000\n桑\tsang\t8000\n分\tfen\t7000\n风\tfeng\t6000\n新\txin\t5000\n星\txing\t4000\n";
    for (typed, target, name) in [
        ("san", "桑", "an_ang"),
        ("sang", "三", "an_ang"),
        ("fen", "风", "en_eng"),
        ("feng", "分", "en_eng"),
        ("xin", "星", "in_ing"),
        ("xing", "新", "in_ing"),
    ] {
        let mut engine = Engine::new(Dictionary::parse(dictionary).unwrap());
        let mut rules = crate::FuzzyRules::default();
        rules.enable(name);
        engine.set_fuzzy(rules);
        engine.set_nine_key(true);
        engine.set_input(&encode(typed));
        assert!(engine.lock_nine_key_syllable(typed));
        let query = engine.query().unwrap();
        let candidate = query
            .candidates
            .items
            .iter()
            .find(|item| item.text == target)
            .unwrap();
        assert_eq!(candidate.reading.as_deref(), Some(typed));
        assert_eq!(engine.commit(candidate), target);
        assert!(engine.composition().is_empty());
        engine.set_fuzzy(crate::FuzzyRules::default());
        engine.set_input(&encode(typed));
        assert!(engine.lock_nine_key_syllable(typed));
        assert!(
            !engine
                .query()
                .unwrap()
                .candidates
                .items
                .iter()
                .any(|item| item.text == target)
        );
    }
}

#[test]
fn fuzzy_prefix_and_sentence_consume_the_typed_digit_lengths() {
    let mut engine = Engine::new(Dictionary::parse("星\txing\t9000\n好\thao\t8000\n").unwrap());
    engine.set_fuzzy(crate::FuzzyRules {
        in_ing: true,
        ..crate::FuzzyRules::default()
    });
    engine.set_nine_key(true);
    engine.set_input(&encode("xinhao"));
    assert!(engine.lock_nine_key_syllable("xin"));
    let query = engine.query().unwrap();
    let candidate = query
        .candidates
        .items
        .iter()
        .find(|item| item.text == "星")
        .unwrap();
    assert_eq!(engine.commit(candidate), "星");
    assert_eq!(engine.composition().text(), encode("hao"));
    assert_eq!(engine.query().unwrap().candidates.items[0].text, "好");
    engine.set_input(&encode("xinhao"));
    let query = engine.query().unwrap();
    let sentence = query
        .candidates
        .items
        .iter()
        .find(|item| item.text == "星好")
        .unwrap();
    assert_eq!(sentence.reading.as_deref(), Some("xin'hao"));
    engine.commit(sentence);
    assert!(engine.composition().is_empty());
}
