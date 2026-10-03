//! 有界的本地选词偏好；壳只保存不透明快照，词语排序仍由 Core 负责。

mod record;
mod state;
#[cfg(test)]
mod tests;

use std::collections::VecDeque;
use std::sync::atomic::{AtomicBool, AtomicU64, Ordering};
use std::sync::{Arc, Mutex};

use qingjian_core::{Candidate, CandidateKind, Learner};

use record::Record;
use state::State;

const LIMIT: usize = 4096;

/// 可克隆的学习句柄，与 Engine 共享聚合偏好，不包含逐键或正文日志。
#[derive(Clone)]
pub struct ChoiceLearner {
    state: Arc<Mutex<State>>,

    enabled: Arc<AtomicBool>,

    revision: Arc<AtomicU64>,
}

impl Default for ChoiceLearner {
    fn default() -> Self {
        Self {
            state: Arc::new(Mutex::new(State::default())),
            enabled: Arc::new(AtomicBool::new(true)),
            revision: Arc::new(AtomicU64::new(0)),
        }
    }
}

impl ChoiceLearner {
    /// 导入快照时限制大小并校验记录，损坏数据不影响基本输入。
    pub fn restore(&self, snapshot: &str) -> bool {
        if snapshot.len() > 2_000_000 {
            return false;
        }
        let Ok(records) = serde_json::from_str::<VecDeque<Record>>(snapshot) else {
            return false;
        };
        if records.len() > LIMIT || records.iter().any(|record| !record.valid()) {
            return false;
        }
        let mut state = self.state.lock().unwrap_or_else(|error| error.into_inner());
        state.records = records;
        state.pending = None;
        state.reindex();
        self.revision.fetch_add(1, Ordering::Relaxed);
        true
    }

    pub fn snapshot(&self) -> String {
        let state = self.state.lock().unwrap_or_else(|error| error.into_inner());
        serde_json::to_string(&state.records).unwrap_or_else(|_| "[]".to_owned())
    }

    pub fn clear(&self) {
        *self.state.lock().unwrap_or_else(|error| error.into_inner()) = State::default();
        self.revision.fetch_add(1, Ordering::Relaxed);
    }

    pub fn set_enabled(&self, enabled: bool) {
        self.enabled.store(enabled, Ordering::Relaxed);
        if !enabled {
            self.state
                .lock()
                .unwrap_or_else(|error| error.into_inner())
                .pending = None;
        }
    }

    pub fn revision(&self) -> u64 {
        self.revision.load(Ordering::Relaxed)
    }
}

impl Learner for ChoiceLearner {
    fn record(&mut self, candidate: &Candidate) {
        if self.enabled.load(Ordering::Relaxed) {
            self.state
                .lock()
                .unwrap_or_else(|error| error.into_inner())
                .pending = Some(candidate.clone());
        }
    }

    fn weight(&self, _text: &str) -> u32 {
        0
    }

    fn record_choice(&mut self, input: &str, text: &str) {
        if !self.enabled.load(Ordering::Relaxed) {
            return;
        }
        let mut record = Record::new(input, text);
        if !record.valid() {
            return;
        }
        let mut state = self.state.lock().unwrap_or_else(|error| error.into_inner());
        if let Some(index) = state
            .records
            .iter()
            .position(|item| item.input == input && item.text == text)
            && let Some(previous) = state.records.remove(index)
        {
            record.count = previous.count.saturating_add(1);
            record.syllables = previous.syllables;
        }
        if let Some(candidate) = state
            .pending
            .take()
            .filter(|candidate| candidate.text == text)
        {
            record.syllables = candidate.syllables;
        }
        if !record.valid() {
            record.syllables.clear();
        }
        state.order = state.order.saturating_add(1);
        record.order = state.order;
        state.update(&record);
        state.records.push_back(record);
        while state.records.len() > LIMIT {
            if let Some(oldest) = state.records.pop_front() {
                state.remove(&oldest.input, &oldest.text);
            }
        }
        self.revision.fetch_add(1, Ordering::Relaxed);
    }

    fn choice_weight(&self, input: &str, text: &str) -> u32 {
        if !self.enabled.load(Ordering::Relaxed) {
            return 0;
        }
        self.state
            .lock()
            .unwrap_or_else(|error| error.into_inner())
            .index
            .get(input)
            .and_then(|words| words.get(text))
            .map_or(0, |record| record.count)
    }

    fn choice_priority(&self, input: &str, text: &str) -> u64 {
        if !self.enabled.load(Ordering::Relaxed) {
            return 0;
        }
        self.state
            .lock()
            .unwrap_or_else(|error| error.into_inner())
            .index
            .get(input)
            .and_then(|words| words.get(text))
            .map_or(0, |record| record.order)
    }

    fn record_sentence_choice(&mut self, input: &str, candidate: &Candidate) {
        self.record(candidate);
        self.record_choice(input, &candidate.text);
    }

    fn learn_word(&mut self, text: &str, syllables: &[String]) {
        if !self.enabled.load(Ordering::Relaxed) {
            return;
        }
        let mut state = self.state.lock().unwrap_or_else(|error| error.into_inner());
        let mut updated = Vec::new();
        for record in &mut state.records {
            if record.text == text {
                record.syllables = syllables.to_vec();
                if record.valid() {
                    updated.push(record.clone());
                } else {
                    record.syllables.clear();
                }
            }
        }
        for record in &updated {
            state.update(record);
        }
        if !updated.is_empty() {
            self.revision.fetch_add(1, Ordering::Relaxed);
        }
    }

    fn recalled_candidates(&self, input: &str) -> Vec<Candidate> {
        if !self.enabled.load(Ordering::Relaxed) {
            return Vec::new();
        }
        let state = self.state.lock().unwrap_or_else(|error| error.into_inner());
        let mut records: Vec<_> = state
            .index
            .get(input)
            .into_iter()
            .flat_map(|words| words.values())
            .filter(|record| record.count >= 2 && !record.syllables.is_empty())
            .collect();
        records.sort_by_key(|record| std::cmp::Reverse(record.order));
        records
            .into_iter()
            .take(24)
            .map(|record| Candidate {
                text: record.text.clone(),
                kind: if record.syllables.len() > 4 {
                    CandidateKind::Sentence
                } else {
                    CandidateKind::Chinese
                },
                syllables: record.syllables.clone(),
                reading: None,
                translation: None,
                aux_code: None,
            })
            .collect()
    }

    fn unrecord_choice(&mut self, input: &str, text: &str) {
        if !self.enabled.load(Ordering::Relaxed) {
            return;
        }
        let mut state = self.state.lock().unwrap_or_else(|error| error.into_inner());
        let records = &mut state.records;
        if let Some(item) = records
            .iter_mut()
            .find(|item| item.input == input && item.text == text)
        {
            item.count = item.count.saturating_sub(1);
        }
        records.retain(|item| item.count > 0);
        state.reindex();
        self.revision.fetch_add(1, Ordering::Relaxed);
    }
}
