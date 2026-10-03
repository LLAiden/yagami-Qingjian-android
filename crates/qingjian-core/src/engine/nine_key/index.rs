//! 数字索引通过二分查询；鼻音别名只增加紧凑索引项，共用原词条。

use qingjian_dictionary::{Dictionary, SyllablePattern};

use crate::fuzzy::FuzzyRules;

use super::{encode, hit::Hit, record::Record, word::Word};

const MAX_ALIASES: usize = 64;

pub(in crate::engine) struct Index {
    words: Vec<Word>,

    records: Vec<Record>,
}

impl Index {
    pub fn new(dictionaries: &[&Dictionary], rules: FuzzyRules) -> Self {
        // 九键目前提供三组鼻音；全拼继续使用完整的 FuzzyRules。
        let nasal = FuzzyRules {
            an_ang: rules.an_ang,
            en_eng: rules.en_eng,
            in_ing: rules.in_ing,
            ..FuzzyRules::default()
        };
        let mut words = Vec::new();
        let mut records = Vec::new();
        for hit in dictionaries
            .iter()
            .flat_map(|dictionary| dictionary.entries())
        {
            let syllables: Vec<String> = hit.syllables().map(str::to_owned).collect();
            if encode(&syllables.join("")).is_empty() {
                continue;
            }
            let alternatives: Vec<Option<String>> = if !nasal.any() {
                Vec::new()
            } else {
                syllables
                    .iter()
                    .map(|syllable| {
                        let expanded = nasal.expand(&[SyllablePattern::complete(syllable)]);
                        expanded.positions()[0]
                            .get(1)
                            .map(|pattern| pattern.text.to_owned())
                    })
                    .collect()
            };
            let mut masks = vec![0_u64];
            let positions: Vec<usize> = alternatives
                .iter()
                .enumerate()
                .take(64)
                .filter_map(|(index, alternative)| alternative.is_some().then_some(index))
                .collect();
            // 先保留各位置单独模糊的写法，再增加组合，长词后面的音节也能命中。
            for &index in &positions {
                if masks.len() < MAX_ALIASES {
                    masks.push(1 << index);
                }
            }
            let mut cursor = 1;
            while cursor < masks.len() && masks.len() < MAX_ALIASES {
                for &index in &positions {
                    let mask = masks[cursor] | (1 << index);
                    if !masks.contains(&mask) {
                        masks.push(mask);
                    }
                    if masks.len() == MAX_ALIASES {
                        break;
                    }
                }
                cursor += 1;
            }
            let word = words.len();
            for variants in masks {
                let reading = syllables
                    .iter()
                    .enumerate()
                    .map(|(index, syllable)| {
                        if index < 64 && variants & (1 << index) != 0 {
                            alternatives[index].as_deref().unwrap_or(syllable)
                        } else {
                            syllable.as_str()
                        }
                    })
                    .collect::<Vec<_>>()
                    .join("");
                records.push(Record {
                    code: encode(&reading),
                    word,
                    variants,
                });
            }
            words.push(Word {
                text: hit.text.to_owned(),
                syllables,
                frequency: hit.frequency,
                alternatives,
            });
        }
        records.sort_by(|a, b| {
            a.code
                .cmp(&b.code)
                .then_with(|| a.variants.count_ones().cmp(&b.variants.count_ones()))
                .then_with(|| words[b.word].frequency.cmp(&words[a.word].frequency))
                .then_with(|| words[a.word].text.cmp(&words[b.word].text))
        });
        Self { words, records }
    }

    fn range(&self, code: &str) -> &[Record] {
        let start = self
            .records
            .partition_point(|record| record.code.as_str() < code);
        let count = self.records[start..].partition_point(|record| record.code.starts_with(code));
        &self.records[start..start + count]
    }

    fn hit<'a>(&'a self, record: &'a Record) -> Hit<'a> {
        Hit {
            code: &record.code,
            word: &self.words[record.word],
            variants: record.variants,
        }
    }

    pub(super) fn prefix(&self, code: &str) -> impl Iterator<Item = Hit<'_>> {
        self.range(code).iter().map(|record| self.hit(record))
    }

    pub(super) fn exact(&self, code: &str) -> impl Iterator<Item = Hit<'_>> {
        let prefix = self.range(code);
        let count = prefix.partition_point(|record| record.code == code);
        prefix[..count].iter().map(|record| self.hit(record))
    }
}
