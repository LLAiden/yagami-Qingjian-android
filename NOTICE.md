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

0.2.3 重新设计 Android 键盘样式、功能图标和启用向导；直接展示候选英语释义、分隔拼音选项、加宽全拼删除键并居中九宫格，增加对应交互验收与产品需求文档；修复快速输入与分段提交，优化候选刷新，并接入 MMKV 和 Core 九键鼻音模糊。

随 APK 分发的词库和译词表具有各自的来源与许可。再分发时必须保留 `assets/lexicon`、
`assets/glossary` 中的来源、版权和许可证说明。

Yagami 输入法与 qingjian-team 不存在隶属、授权或官方发布关系，不使用青简官方 Logo。

0.2.4 按用户要求调整候选、剪贴板、编辑菜单和输入区顺序；候选空闲时隐藏，菜单使用靠右的图标；输入精简为拼音九键、英语全键盘、数字九键，标点统一英文半角，并增加布局交互回归。

0.2.5 按用户补充要求使用同一图标工具栏，从左到右为全选、编辑、验证器、密码管理器、剪贴板、收起；拼音底行 `123 | 空格 | EN` 等宽，继续保留候选显隐与连续输入回归。
同时增加英语锁定大写与居中空格、Bitwarden / Google 验证器启动入口，以及圆形背景菜单图标。

Android 使用腾讯 [MMKV 2.4.2](https://github.com/Tencent/MMKV/tree/v2.4.2)，许可为 BSD-3-Clause，版权归腾讯及对应贡献者。
其完整许可随 APK 打包在 `assets/licenses/mmkv.txt`，源码副本见 [许可文件](apps/android/app/src/main/assets/licenses/mmkv.txt)。
