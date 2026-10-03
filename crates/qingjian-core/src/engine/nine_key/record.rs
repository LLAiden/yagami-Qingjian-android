//! 数字索引项只存词条编号和模糊音掩码，不复制词的正文与音节。

pub(super) struct Record {
    pub code: String,

    pub word: usize,

    pub variants: u64,
}
