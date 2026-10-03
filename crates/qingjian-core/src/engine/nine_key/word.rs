//! 九键索引里的词条，保留拼音供消歧和分段上屏。

pub(super) struct Word {
    pub text: String,

    pub syllables: Vec<String>,

    pub frequency: u32,

    pub alternatives: Vec<Option<String>>,
}
