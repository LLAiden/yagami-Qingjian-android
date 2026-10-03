# 来源与修改声明

Yagami 输入法基于 [qingjian-team/qingjian](https://github.com/qingjian-team/qingjian) 修改，起始版本为提交
`c08ae57cb88b6a4a46f4a5e9c1d6d11c5e69222e`。原项目及各贡献者保留其版权。

本衍生项目自 2026-10-03 起由 Utyoin-OG 独立维护，主要修改包括：

- 新增 Android `InputMethodService` 平台壳与 JNI 桥接；
- 新增移动端候选栏与类 iOS 四行键盘布局；
- 新增 Android 构建、签名、安装和升级流程；
- 增加 Android Engine 回归测试和用户文档。

本项目采用 GPL-3.0-or-later，许可证全文见 [LICENSE](LICENSE)。本项目按现状提供，不附带任何担保。

LLAiden 的 0.2.0 预览改造基于 Utyoin-OG/yagami-Qingjian-android 提交
`79d9dac7ce0c156b8dcf76fd885f0f01a4495cbb`：增加 Core 九键拼音、Android 九宫格键盘、剪贴板、
选区删除、收起入口、导航区域适配和系统输入法交互验收。原项目和上游贡献者的版权与许可保持有效。

0.2.1 在同一改造分支增加多列候选面板、模式记忆、剪贴板去重与删除、数字页状态恢复及对应交互回归测试。

0.2.2 在同一分支完成十轮自查，增加编辑工具、高度与主题设置，修复长按删除、剪贴板生命周期、输入动作与输入重启问题，并增加真实 IME 交互回归。

随 APK 分发的词库和译词表具有各自的来源与许可。再分发时必须保留 `assets/lexicon`、
`assets/glossary` 中的来源、版权和许可证说明。

Yagami 输入法与 qingjian-team 不存在隶属、授权或官方发布关系，不使用青简官方 Logo。
