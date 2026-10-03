//! 九键整句搜索中有界保留的候选路径。

#[derive(Clone, Default)]
pub(super) struct Path {
    pub text: String,

    pub syllables: Vec<String>,

    pub typed_syllables: Vec<String>,

    pub score: f64,

    pub words: usize,

    pub last: Option<String>,
}
