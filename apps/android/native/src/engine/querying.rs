//! 把 Core 查询结果转换成 Android UI 使用的只读快照。

use qingjian_core::Candidate;

use crate::snapshot::{Snapshot, SnapshotCandidate};

use super::AndroidEngine;

impl AndroidEngine {
    pub(crate) fn candidates(&self) -> Vec<Candidate> {
        self.engine
            .query()
            .map(|query| query.candidates.items)
            .unwrap_or_default()
    }

    pub(crate) fn snapshot(&self) -> Snapshot {
        let Ok(mut query) = self.engine.query() else {
            return Snapshot::new(self.engine.composition().typed_text(), Vec::new());
        };
        self.engine.annotate(&mut query.candidates);
        let preedit = query.marked_text();
        let candidates = query
            .candidates
            .items
            .into_iter()
            .take(12)
            .map(|candidate| {
                let gloss = candidate.translation.as_ref().and_then(|translation| {
                    let joined = translation
                        .senses()
                        .iter()
                        .map(|sense| sense.text.as_str())
                        .collect::<Vec<_>>()
                        .join(" · ");
                    (!joined.is_empty()).then_some(joined)
                });
                SnapshotCandidate::new(candidate.text, gloss)
            })
            .collect();
        Snapshot::new(preedit, candidates)
    }
}
