//! 候选快照中的单项文本与可选译词。

use serde::Serialize;

#[derive(Serialize)]
pub(crate) struct SnapshotCandidate {
    text: String,

    gloss: Option<String>,
}

impl SnapshotCandidate {
    pub(crate) fn new(text: String, gloss: Option<String>) -> Self {
        Self { text, gloss }
    }
}
