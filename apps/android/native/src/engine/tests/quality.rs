//! 固定日常词句集只衡量本地九键候选，不能代表任意文本的整体准确率。

use std::path::PathBuf;

use crate::engine::AndroidEngine;

fn digits(pinyin: &str) -> String {
    pinyin
        .bytes()
        .filter_map(|letter| match letter {
            b'a'..=b'c' => Some('2'),
            b'd'..=b'f' => Some('3'),
            b'g'..=b'i' => Some('4'),
            b'j'..=b'l' => Some('5'),
            b'm'..=b'o' => Some('6'),
            b'p'..=b's' => Some('7'),
            b't'..=b'v' => Some('8'),
            b'w'..=b'z' => Some('9'),
            _ => None,
        })
        .collect()
}

#[test]
fn frozen_daily_corpus_reports_candidate_quality() {
    let assets = PathBuf::from(env!("CARGO_MANIFEST_DIR")).join("../../../assets");
    let cases = std::fs::read_to_string(assets.join("evaluation/android-nine-key.tsv")).unwrap();
    let mut android = AndroidEngine::open(
        &assets.join("lexicon/dict.tsv"),
        &assets.join("glossary/glossary-en.tsv"),
    )
    .unwrap();
    android.engine.set_nine_key(true);
    let (mut total, mut first, mut five, mut covered) = (0, 0, 0, 0);
    for row in cases.lines().skip(1) {
        let fields: Vec<&str> = row.split('\t').collect();
        if fields.len() != 3 {
            continue;
        }
        android.engine.set_input(&digits(fields[1]));
        android.snapshot();
        let candidates = android.candidates();
        let rank = candidates
            .iter()
            .position(|candidate| candidate.text == fields[0]);
        total += 1;
        first += usize::from(rank == Some(0));
        five += usize::from(rank.is_some_and(|position| position < 5));
        covered += usize::from(rank.is_some());
        println!(
            "DAILY\t{}\t{}\t{:?}\t{}",
            fields[0],
            digits(fields[1]),
            rank.map(|position| position + 1),
            candidates
                .first()
                .map_or("", |candidate| candidate.text.as_str())
        );
    }
    println!("DAILY total={total} first={first} top5={five} top60={covered}");
    assert_eq!(total, 50);
    assert!(
        first >= 45 && five >= 49 && covered == 50,
        "日常词句候选质量明显退化"
    );
}
