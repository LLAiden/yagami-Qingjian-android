//! 选词快照中的聚合记录，拒绝原文、数字口令和过长输入。

use serde::{Deserialize, Serialize};

#[derive(Clone, Serialize, Deserialize)]
pub(super) struct Record {
    pub(super) input: String,

    pub(super) text: String,

    pub(super) count: u32,

    #[serde(default)]
    pub(super) syllables: Vec<String>,

    #[serde(default)]
    pub(super) order: u64,
}

impl Record {
    pub(super) fn new(input: &str, text: &str) -> Self {
        Self {
            input: input.to_owned(),
            text: text.to_owned(),
            count: 1,
            syllables: Vec::new(),
            order: 0,
        }
    }

    pub(super) fn valid(&self) -> bool {
        !self.input.is_empty()
            && self.input.len() <= 64
            && self.input.bytes().all(|letter| {
                letter.is_ascii_lowercase() || (b'2'..=b'9').contains(&letter) || letter == b'\''
            })
            && !self.text.is_empty()
            && self.text.chars().count() <= 64
            && self.count > 0
            && (self.syllables.is_empty()
                || self.syllables.len() == self.text.chars().count()
                    && self.syllables.iter().all(|syllable| {
                        qingjian_core::parser::SYLLABLES.contains(&syllable.as_str())
                    }))
            && self
                .text
                .chars()
                .all(|letter| ('\u{3400}'..='\u{9fff}').contains(&letter))
    }
}
