//! 一次查询的拼音显示与候选集合。

use serde::Serialize;

use super::SnapshotCandidate;

#[derive(Serialize)]
pub(crate) struct Snapshot {
    preedit: String,

    candidates: Vec<SnapshotCandidate>,

    raw: String,

    readings: Vec<String>,
}

impl Snapshot {
    pub(crate) fn new(
        preedit: String,
        candidates: Vec<SnapshotCandidate>,
        raw: String,
        readings: Vec<String>,
    ) -> Self {
        Self {
            preedit,
            candidates,
            raw,
            readings,
        }
    }
}
