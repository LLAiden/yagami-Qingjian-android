# 开发约定

> Yagami 是青简的独立 Android 衍生项目。青简上游关于暂不接受 Android 贴图路径与渲染器外部 PR 的限制
> 不适用于本仓库；平台无关 Core 的约定仍然适用。Yagami 当前 Android 键盘使用原生 View，后续是否迁移到
> `qingjian-render` 由本项目单独评估。

改代码前先看这一页：架构上不能越的线、代码怎么组织、版本号与提交信息怎么写、改了行为要同步哪些文档。
设计的来龙去脉在 [design/architecture.md](design/architecture.md)，各 crate 的实现要点在 [notes/crate-notes.md](notes/crate-notes.md)。

## 架构约束（不要违反）

长版与理由见 [design/architecture.md](design/architecture.md)。

- **Core 与平台层严格解耦。** `qingjian-core` 及其兄弟 crate 必须平台无关：词库、拼音解析、候选生成、排序、学习、翻译、文本变换全部属于 Core。
  平台层（IMK / TSF / IBus-Fcitx）只做两件事：把系统输入事件翻译成 Core 的输入，把 Core 返回的帧画到候选窗口。
  **平台层里不允许出现排序逻辑、词库访问、翻译调用或文本变换。** 判断标准：把 IMK 换成 TSF，不应该需要改 Core 的任何一行。
- **一个候选词只显示一种辅助语言。** 用户配置 Primary Language + 单个 Learning Language。不要设计成 `translations: Vec<Translation>` 或
  `HashMap<Lang, String>` 这类多语言并列的数据结构，那会在 API 层面把「一次只学一种语言」这条产品原则给破坏掉。翻译是候选词的 annotation（可选、单条）。
- **输入优先于学习。** 任何为学习功能增加的延迟、弹窗、UI 干扰都是设计错误。翻译查询不能阻塞候选生成，Core 必须能在翻译尚未就绪时先返回候选。
- **输入方案是配置项，不是模式。** 双拼、注音这类键盘方案放 `[general]` 里当设置，中 / 英切换始终是布尔；新方案不能改变别的方案的既定按键行为（[user/getting-started/keys.md](user/getting-started/keys.md)）。
- **桌面显示面自绘、控件面原生。** 桌面候选窗、拼音行、状态条由渲染器出位图各平台贴图；偏好设置、菜单、安装器用各平台原生控件。
  Yagami Android 当前使用原生 View 绘制键盘与候选栏。上游方案背景见 [design/rendering.md](design/rendering.md)。

## 代码组织

- **一个类型一个文件。** 一个 struct / enum / trait 及其 impl 单独一个文件；模块文件只做 `mod` 声明、re-export 与自由函数，不把一个 crate 平铺在 `lib.rs` 里。
- **子模块用目录。** 有子模块的模块用 `foo/mod.rs`，**不用** `foo.rs` + `foo/` 并列。
- **同词干的兄弟文件收进目录，绝不用文件名前缀分组。** `key_event.rs` + `key_outcome.rs` → `key/mod.rs` + `key/{event,outcome}.rs`，哪怕没有 `key.rs` 这个共同父文件；
  `query/` + `querying.rs` 这种也不行。判断：两个及以上文件名共享一段前缀且同属一个概念，就收进以那段前缀命名的目录。
- **一个职责连带它专用的类型收进一个目录。** `engine/commit/mod.rs` 放上屏部分，`chain.rs` / `last.rs` 放只有它用的类型。
- **大类型的 `impl` 按职责拆成子模块。** 每个文件一个 `impl Foo { … }`（`host/settings.rs` 这样），结构体与构造留在 `mod.rs`，跨文件用到的私有方法标 `pub(super)`。
- **文件长度。** 单文件不超过 800 行，目标 500 行以内；测试超过 200 行搬到 `tests.rs`（多时 `tests/` 按主题分文件）。
- **导入写精确路径。** 不用 `use super::*` / `use foo::*`（`#[cfg(test)] mod tests` 里的 `use super::*` 除外）。
  子文件从父模块拿的类型写 `use super::{Engine, Candidate};`，父模块没定义的直接 `use crate::…` 或 `use <crate>::…`，不绕 `super::` 转手。
  存量的 glob 改到那个文件时顺手换掉，不专门清扫。

## 命名与注释

- 代码标识符一律英文，注释与文档用中文；`thiserror` 的 `#[error]` 文案用英文，日志与 UI 文案用中文。
- 新文件都要有 `//!` 文件头；结构体 / 枚举字段之间空一行，字段名说不清的加 `///`（`r` / `g` / `b`、`width` 这类不用，`a`「255 为不透明」这类要）。
- 注释只写维护时用得上的：文件头一两句说它是什么、怎么用；代码里只解释读代码看不出来的约束与原因（平台怪癖、协议约定、踩过的坑），一两句说完。
  不写复述代码的教学性注释，不写改动经过、调试过程这类日志型注释；较长的设计理由写进 `docs/`，注释里留一句指过去。
- 不写装饰性分隔注释（`// ====`），提交钩子会拦。

## 依赖与配置

- 依赖用 `cargo add` 加，共用包提到根 `[workspace.dependencies]`；错误用 `thiserror` 不用 `anyhow`；日志用 `tracing` 门面。
- 快捷键一律进 `[shortcut]` 可配置，不写死键码。

## 版本号

- Yagami Android 使用 `apps/android/app/build.gradle` 中的 `versionName` 与 `versionCode`；正式发布时
  `versionName` 与标签 `v<版本>` 一致，每次可安装升级都必须递增 `versionCode`。
- `apps/android/native/Cargo.toml` 的版本与 Android `versionName` 保持一致。
- `crates/*` 用 `version.workspace = true`；**`apps/*` 各壳是独立发布的产品，写死自己的 `version`**（Windows 读 `server/Cargo.toml`）。
- 青简上游桌面壳的 `-dev` 版本与多平台标签规则仅作为保留源码的历史约定，不用于 Yagami Android 发版。

## 提交信息

- [Conventional Commits](https://www.conventionalcommits.org/zh-hans/)：第一行 `<类型>(<范围>): <说明>`，类型与范围英文小写，说明用中文，例如
  `fix(core): 修自绘输入框吞数字`、`feat(windows): 三进程日志统一到 %LOCALAPPDATA%\Qingjian\logs`、`docs(changelog): 补 0.1.3 条目`。
  - 类型：`feat` 新功能 / `fix` 修 bug / `docs` 只改文档 / `refactor` 不改行为的整理 / `perf` 性能 / `test` 只改测试 /
    `build` 打包与构建脚本 / `ci` 工作流 / `chore` 版本号、依赖、仓库杂务 / `style` 只改格式 / `revert` 还原。
  - 范围：crate 或壳的名字——`core` `platform` `render` `dictionary` `translate` `learning` `predict` `lm` `neural` `format` `cli`
    `macos` `windows`（Server / DLL / 设置程序细分时用 `server` `tsf` `settings`）`installer` `linux` `tools` `docs` `ci` `deps` `release`；
    跨好几处的可以省略。不兼容的改动在范围后加 `!`。
  - 正文写「为什么」与取舍，一行一条；不加 AI 署名。`.githooks/commit-msg` 会拦第一行不合格式的提交。
  - 2026-09-16 之前的历史是「`macOS：……` / `Core：……`」的中文冒号格式，不重写。

## 文档同步

- 实现与规划分歧时以代码为准并改文档。技术方向写 `docs/design/`，计划写 `docs/plan/`，工程记录写 `docs/notes/`；不往 README 里加技术内容。
- **用户能感知的行为改了（按键、菜单、偏好设置、配置文件、数据文件），同一个提交里改 `docs/user/` 对应的页**，按键改动同时改 `keys.md`。
- 改了某个 crate 的实现要点（数据文件、常数、生成命令），同步 `docs/notes/crate-notes.md`。

## 提交前检查

- 钩子：`.githooks/pre-commit`（禁装饰性分隔注释 + fmt + Android JNI clippy）、`.githooks/commit-msg`（提交信息格式）、
  `.githooks/pre-push`（Android JNI 测试）；`git config core.hooksPath .githooks` 启用一次。完整 Android NDK 与 Gradle 检查由
  [android.yml](../.github/workflows/android.yml) 执行。
- 排序 / 整句 / 纠错的改动先跑 `apps/cli` 再合。

## CI 与发版

- `android.yml` 在 `main` 推送和 PR 上检查 Rust 格式、Android JNI 测试与 clippy，并构建双 ABI JNI 和未签名 Release APK；依赖均使用锁文件。
- `audit.yml` 每周检查 Rust 依赖安全公告；Dependabot 负责更新 GitHub Actions。
- 当前不由 CI 发布或签名 APK。维护者使用离线保管的正式 keystore 本地签名，完成真机验证后再手动创建
  `v<版本>` Release，并同时发布该提交的源代码。签名环境变量和升级约束见
  [Android 构建说明](../apps/android/README.md)。
- 上游桌面端的 `release.yml`、多平台标签与 Secrets 流程不适用于本仓库。

## 外部 PR

- 从 main 开分支，一个 PR 只做一件事、只碰一个平台（Core 改动单独一个）。
- 维护者对着 main 审，squash 合并保留作者署名。PR 模板里的合并前清单就是审核标准。
- **修 bug、改壳里行为（按键、上屏、候选窗位置）的 PR 必须真机验过**：先在自己机器上复现问题，改完在同一个应用里确认修好，
  PR 的「怎么验证的」写明系统版本、应用与操作步骤。编译与 CI 通过不算验证。没验过的修复发出去，报 issue 的人升级后还得再报一次。
  复现不了的（没有那个应用或系统）不提修复：把分析写在 issue 里，或者提只加日志、不改行为的 PR。
  不改行为的改动（日志、注释、文档）与有测试 / 回放兜底的 Core 逻辑不受此限。
- Android 壳、构建、文档与测试均接受 PR；涉及平台无关 Core 的改动应与 Android 平台改动分开提交并说明同步上游的计划。
