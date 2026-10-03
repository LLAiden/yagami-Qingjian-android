//! 从应用私有目录的数据文件装配 Android Engine。

use std::path::Path;

use qingjian_core::{Engine, Language};
use qingjian_dictionary::Dictionary;
use qingjian_learning::ChoiceLearner;
use qingjian_translate::Glossary;

use super::AndroidEngine;

impl AndroidEngine {
    pub(crate) fn open(dictionary: &Path, glossary: &Path) -> Result<Self, String> {
        let dictionary = Dictionary::from_path(dictionary).map_err(|error| error.to_string())?;
        let glossary =
            Glossary::from_path(Language::English, glossary).map_err(|error| error.to_string())?;
        let learner = ChoiceLearner::default();
        Ok(Self {
            engine: Engine::new(dictionary)
                .with_translator(Box::new(glossary))
                .with_learner(Box::new(learner.clone())),
            displayed: Vec::new(),
            learner,
            saved_revision: 0,
            learning_enabled: true,
        })
    }
}
