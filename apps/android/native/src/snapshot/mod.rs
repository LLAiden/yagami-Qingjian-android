//! JNI 返回给 Android UI 的候选快照。

mod candidate;
mod state;

pub(crate) use candidate::SnapshotCandidate;
pub(crate) use state::Snapshot;
