//! Android 侧持有的 Core 实例；装配与查询分别放在子模块。

mod querying;
mod setup;
#[cfg(test)]
mod tests;

use qingjian_core::Engine;

pub(super) struct AndroidEngine {
    pub(super) engine: Engine,
}
