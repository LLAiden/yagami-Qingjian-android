//! 九键拼音：数字词索引、音节消歧与有界整句搜索，由 Engine 统一对外提供。

mod index;
mod path;
mod word;

#[cfg(test)]
mod tests;

use std::collections::HashSet;
use std::time::Instant;

use crate::{Candidate, CandidateKind, CandidateList, Engine, Learner, Query, parser, sentence};

pub(super) use index::Index;
use path::Path;

const BEAM: usize = 5;
const MAX_INPUT: usize = 64;

/// 拼音 ü 采用 v，数字编码与标准电话键盘一致。
pub(super) fn encode(pinyin: &str) -> String {
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

impl Engine {
    /// 改变输入方案时结束旧组句；首次开启按词库构建索引。
    pub fn set_nine_key(&mut self, enabled: bool) {
        if enabled == self.nine_key.is_some() {
            return;
        }
        self.clear();
        self.nine_key = enabled.then(|| Index::new(&self.all_dictionaries()));
    }

    /// 可确认的下一个完整音节；已锁定的音节不再显示。
    pub fn nine_key_readings(&self) -> Vec<String> {
        if self.nine_key.is_none() {
            return Vec::new();
        }
        let offset: usize = self.nine_key_locked.iter().map(String::len).sum();
        let Some(rest) = self.composition.scope().get(offset..) else {
            return Vec::new();
        };
        if rest.is_empty() {
            return Vec::new();
        }
        let mut readings: Vec<String> = parser::SYLLABLES
            .iter()
            .filter(|reading| rest.starts_with(&encode(reading)))
            .map(|reading| (*reading).to_owned())
            .collect();
        readings.sort_by(|a, b| b.len().cmp(&a.len()).then_with(|| a.cmp(b)));
        readings
    }

    pub fn lock_nine_key_syllable(&mut self, reading: &str) -> bool {
        if !self.nine_key_readings().iter().any(|item| item == reading) {
            return false;
        }
        self.nine_key_locked.push(reading.to_owned());
        true
    }

    pub(super) fn query_nine_key(&self, started: Instant) -> Query {
        let keys = self.composition.scope();
        let mut query = Query {
            text: self.composition.text().to_owned(),
            cursor: self.composition.cursor(),
            typed_display: Some(keys.to_owned()),
            rest: self.composition.rest().to_owned(),
            decoded_keys: true,
            ..Query::default()
        };
        if keys.is_empty()
            || keys.len() > MAX_INPUT
            || !keys.bytes().all(|c| (b'2'..=b'9').contains(&c))
        {
            return query;
        }
        let Some(index) = &self.nine_key else {
            return query;
        };
        let mut hits = Vec::new();
        // 完整输入、末音节补全与可分段上屏的前缀词共用一套排序。
        for word in index.prefix(keys) {
            if word.accepts(&self.nine_key_locked) && word.completes_last(keys.len()) {
                hits.push((word, keys.len(), word.code == keys));
            }
        }
        for end in 1..keys.len() {
            for word in index.exact(&keys[..end]) {
                if word.accepts(&self.nine_key_locked) {
                    hits.push((word, end, true));
                }
            }
        }
        hits.sort_by(|a, b| {
            b.1.cmp(&a.1)
                .then_with(|| b.2.cmp(&a.2))
                .then_with(|| {
                    self.learner
                        .choice_weight(&keys[..b.1], &b.0.text)
                        .cmp(&self.learner.choice_weight(&keys[..a.1], &a.0.text))
                })
                .then_with(|| b.0.frequency.cmp(&a.0.frequency))
                .then_with(|| a.0.text.cmp(&b.0.text))
        });
        let mut items: Vec<Candidate> = hits
            .iter()
            .take(500)
            .map(|(word, _, _)| word.candidate())
            .collect();
        let paths = self.nine_key_paths(keys, index);
        let sentence_paths: Vec<&Path> = paths
            .iter()
            .filter(|path| paths.first().is_some_and(|first| first.words > 1) && path.words > 1)
            .take(3)
            .collect();
        for path in sentence_paths.into_iter().rev() {
            items.insert(
                0,
                Candidate {
                    text: path.text.clone(),
                    kind: CandidateKind::Sentence,
                    syllables: path.syllables.clone(),
                    reading: None,
                    translation: None,
                    aux_code: None,
                },
            );
        }
        let mut seen = HashSet::new();
        items
            .retain(|candidate| seen.insert((candidate.text.clone(), candidate.syllables.clone())));
        if let Some(first) = items.first() {
            let code = encode(&first.syllables.join(""));
            let display = if code.len() >= keys.len() {
                first.syllables.join("'")
            } else {
                format!("{}'{}", first.syllables.join("'"), &keys[code.len()..])
            };
            query.typed_display = Some(display);
        }
        query.candidates = CandidateList { items };
        query.timings.lookup = started.elapsed();
        query
    }

    fn nine_key_paths(&self, keys: &str, index: &Index) -> Vec<Path> {
        let mut best: Vec<Vec<Path>> = vec![Vec::new(); keys.len() + 1];
        best[0].push(Path::default());
        let log_total = (self.total_frequency() as f64).max(1.0).ln();
        for start in 0..keys.len() {
            let bases = best[start].clone();
            if bases.is_empty() {
                continue;
            }
            for end in start + 1..=keys.len() {
                for word in index.exact(&keys[start..end]).iter().take(24) {
                    for base in &bases {
                        let mut syllables = base.syllables.clone();
                        syllables.extend(word.syllables.iter().cloned());
                        if !syllables
                            .iter()
                            .zip(&self.nine_key_locked)
                            .all(|(a, b)| a == b)
                        {
                            continue;
                        }
                        let context = base
                            .last
                            .as_deref()
                            .map(sentence::Context::after)
                            .unwrap_or(self.chain.context());
                        let score = base.score
                            + sentence::transition_log_prob(
                                &*self.language_model,
                                self.personal(),
                                context,
                                &word.text,
                                sentence::fallback_log_prob(word.frequency, log_total),
                            );
                        best[end].push(Path {
                            text: format!("{}{}", base.text, word.text),
                            syllables,
                            score,
                            words: base.words + 1,
                            last: Some(word.text.clone()),
                        });
                    }
                }
                best[end].sort_by(|a, b| b.score.total_cmp(&a.score));
                best[end].dedup_by(|a, b| a.text == b.text && a.syllables == b.syllables);
                best[end].truncate(BEAM);
            }
        }
        std::mem::take(&mut best[keys.len()])
    }
}
