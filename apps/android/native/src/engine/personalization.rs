//! 加密快照的生命周期和私密会话边界；不接入正文日志或云服务。

use super::AndroidEngine;

impl AndroidEngine {
    pub(crate) fn restore_learning(&mut self, snapshot: &str) -> bool {
        let restored = self.learner.restore(snapshot);
        if restored {
            self.saved_revision = self.learner.revision();
        }
        restored
    }

    pub(crate) fn learning_snapshot(&mut self) -> String {
        let revision = self.learner.revision();
        if revision == self.saved_revision {
            return String::new();
        }
        self.saved_revision = revision;
        self.learner.snapshot()
    }

    pub(crate) fn privacy(&mut self, private: bool, learning: bool) {
        if self.engine.is_private() != private {
            self.engine.discard_input();
            self.displayed.clear();
        }
        self.engine.set_private(private);
        self.engine.set_learning(learning);
        self.learning_enabled = learning;
        self.learner.set_enabled(learning && !private);
    }

    pub(crate) fn commit(&mut self, index: usize, explicit: bool) -> String {
        let Some(candidate) = self.candidates().get(index).cloned() else {
            return String::new();
        };
        // 自动结束预编辑不强化默认答案，明确选词才改变个人偏好。
        self.engine.set_learning(explicit && self.learning_enabled);
        let committed = self.engine.commit(&candidate);
        self.engine.set_learning(self.learning_enabled);
        committed
    }
}
