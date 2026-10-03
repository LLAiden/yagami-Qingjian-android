//! 快照顺序用于有界淘汰，索引避免候选排序逐次扫描全部个人记录。

use std::collections::{BTreeMap, VecDeque};

use super::record::Record;
use qingjian_core::Candidate;

#[derive(Default)]
pub(super) struct State {
    pub(super) records: VecDeque<Record>,

    pub(super) index: BTreeMap<String, BTreeMap<String, Record>>,

    pub(super) pending: Option<Candidate>,

    pub(super) order: u64,
}

impl State {
    pub(super) fn reindex(&mut self) {
        self.index.clear();
        self.order = self
            .records
            .iter()
            .map(|record| record.order)
            .max()
            .unwrap_or(0);
        for record in &mut self.records {
            if record.order == 0 {
                self.order = self.order.saturating_add(1);
                record.order = self.order;
            }
            self.index
                .entry(record.input.clone())
                .or_default()
                .insert(record.text.clone(), record.clone());
        }
    }

    pub(super) fn update(&mut self, record: &Record) {
        self.index
            .entry(record.input.clone())
            .or_default()
            .insert(record.text.clone(), record.clone());
    }

    pub(super) fn remove(&mut self, input: &str, text: &str) {
        if let Some(words) = self.index.get_mut(input) {
            words.remove(text);
            if words.is_empty() {
                self.index.remove(input);
            }
        }
    }
}
