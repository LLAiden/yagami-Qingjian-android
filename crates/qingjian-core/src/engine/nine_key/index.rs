//! 按数字编码排序的紧凑词索引，查询通过二分定位而非枚举字母组合。

use qingjian_dictionary::Dictionary;

use super::{encode, word::Word};

pub(in crate::engine) struct Index {
    words: Vec<Word>,
}

impl Index {
    pub fn new(dictionaries: &[&Dictionary]) -> Self {
        let mut words: Vec<Word> = dictionaries
            .iter()
            .flat_map(|dictionary| dictionary.entries())
            .filter_map(|hit| {
                let syllables: Vec<String> = hit.syllables().map(str::to_owned).collect();
                let code = encode(&syllables.join(""));
                (!code.is_empty()).then(|| Word {
                    code,
                    text: hit.text.to_owned(),
                    syllables,
                    frequency: hit.frequency,
                })
            })
            .collect();
        words.sort_by(|a, b| {
            a.code
                .cmp(&b.code)
                .then_with(|| b.frequency.cmp(&a.frequency))
                .then_with(|| a.text.cmp(&b.text))
        });
        Self { words }
    }

    pub(super) fn prefix(&self, code: &str) -> &[Word] {
        let start = self.words.partition_point(|word| word.code.as_str() < code);
        let count = self.words[start..].partition_point(|word| word.code.starts_with(code));
        &self.words[start..start + count]
    }

    pub(super) fn exact(&self, code: &str) -> &[Word] {
        let prefix = self.prefix(code);
        let count = prefix.partition_point(|word| word.code == code);
        &prefix[..count]
    }
}
