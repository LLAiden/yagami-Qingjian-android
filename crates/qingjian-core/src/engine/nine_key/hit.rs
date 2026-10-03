//! 查询命中的借用视图，保留原读音与实际匹配的鼻音写法。

use std::ops::Deref;

use crate::{Candidate, CandidateKind};

use super::word::Word;

#[derive(Clone, Copy)]
pub(super) struct Hit<'a> {
    pub code: &'a str,

    pub word: &'a Word,

    pub variants: u64,
}

impl Deref for Hit<'_> {
    type Target = Word;

    fn deref(&self) -> &Self::Target {
        self.word
    }
}

impl Hit<'_> {
    pub fn typed_syllables(&self) -> impl Iterator<Item = &str> {
        self.syllables.iter().enumerate().map(|(index, syllable)| {
            if index < 64 && self.variants & (1 << index) != 0 {
                self.alternatives[index].as_deref().unwrap_or(syllable)
            } else {
                syllable.as_str()
            }
        })
    }

    pub fn candidate(&self) -> Candidate {
        Candidate {
            text: self.text.clone(),
            kind: CandidateKind::Chinese,
            syllables: self.syllables.clone(),
            reading: (self.variants != 0)
                .then(|| self.typed_syllables().collect::<Vec<_>>().join("'")),
            translation: None,
            aux_code: None,
        }
    }

    pub fn accepts(&self, locked: &[String]) -> bool {
        self.typed_syllables().zip(locked).all(|(a, b)| a == b)
    }

    pub fn completes_last(&self, typed: usize) -> bool {
        let before_last: usize = self
            .typed_syllables()
            .take(self.syllables.len().saturating_sub(1))
            .map(str::len)
            .sum();
        typed > before_last
    }
}
