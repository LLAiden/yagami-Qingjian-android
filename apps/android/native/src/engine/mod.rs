//! Android 侧持有的 Core 实例；装配与查询分别放在子模块。

mod personalization;
mod querying;
mod setup;
#[cfg(test)]
mod tests;

use qingjian_core::{Candidate, Engine};
use qingjian_learning::ChoiceLearner;

pub(super) struct AndroidEngine {
    pub(super) engine: Engine,

    pub(super) displayed: Vec<Candidate>,

    pub(super) learner: ChoiceLearner,

    pub(super) saved_revision: u64,

    pub(super) learning_enabled: bool,
}
