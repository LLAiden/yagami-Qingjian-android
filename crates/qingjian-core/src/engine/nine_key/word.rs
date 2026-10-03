//! 九键索引里的词条，保留拼音供消歧和分段上屏。

use crate::{Candidate, CandidateKind};

pub(super) struct Word {
    pub code: String,

    pub text: String,

    pub syllables: Vec<String>,

    pub frequency: u32,
}

impl Word {
    pub fn candidate(&self) -> Candidate {
        Candidate {
            text: self.text.clone(),
            kind: CandidateKind::Chinese,
            syllables: self.syllables.clone(),
            reading: None,
            translation: None,
            aux_code: None,
        }
    }

    pub fn accepts(&self, locked: &[String]) -> bool {
        self.syllables.iter().zip(locked).all(|(a, b)| a == b)
    }

    /// 未敲完的词只允许补全当前最后一个音节，不能凭空补后面的整词。
    pub fn completes_last(&self, typed: usize) -> bool {
        let before_last: usize = self.syllables.iter().rev().skip(1).map(String::len).sum();
        typed > before_last
    }
}
