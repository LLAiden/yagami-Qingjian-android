# Android 九键日常输入评测

`android-nine-key.tsv` 是本 fork 为 1.0.0 手写的 50 条日常词语 / 短句样例，字段为目标词句、完整拼音、类别。采用仓库 GPL-3.0-or-later 许可，无真实用户输入。

固定样例用于发现回归，不能据此推算全场景准确率。通过 Android JNI 的同一装配与 60 候选限制运行：

```bash
cargo test -p yagami-android-native frozen_daily_corpus -- --nocapture
```

个人偏好另用歧义选词、重建恢复、私密 / 关闭学习与清除回归衡量，不用学习后的结果冒充首次输入的准确率。
