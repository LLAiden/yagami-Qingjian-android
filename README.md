# Yagami 输入法

Yagami 是一个面向 Android 的拼音与语言学习输入法，基于
[青简](https://github.com/qingjian-team/qingjian) 开源核心开发。本项目是独立维护的非官方衍生版本，
与 qingjian-team 不存在隶属、授权或官方发布关系。

## 当前功能

- 全拼输入、候选与英语译词
- 点击候选或空格上屏
- 中文 / 英文切换与临时大写
- 短按退格、长按连续删除
- 多行输入框换行，单行输入框执行应用指定的发送、搜索或完成动作
- 系统输入法切换
- `arm64-v8a` 与 `x86_64`，Android 8.0 及以上

当前版本已在 HUAWEI Mate 40 Pro、HarmonyOS 4.2.0 的 Android 兼容环境中完成真机验证。
HarmonyOS NEXT 不支持 Android APK，不在支持范围内。

## 构建

Android 构建、签名和升级说明见 [apps/android/README.md](apps/android/README.md)。

## 上游与修改

平台无关的输入引擎、词库与译词能力来自青简。本项目新增并维护 Android `InputMethodService`、JNI 桥接、
移动键盘界面和 Android 打包配置。详细来源与修改声明见 [NOTICE.md](NOTICE.md)。

## 许可证

代码以 [GPL-3.0-or-later](LICENSE) 发布。发布 APK 时必须同时提供该版本对应的完整源代码和构建脚本。
随包数据有各自的来源、署名和许可，见 [assets/lexicon/README.md](assets/lexicon/README.md)、
[assets/lexicon/QINGJIAN.md](assets/lexicon/QINGJIAN.md) 与 [assets/glossary/README.md](assets/glossary/README.md)。

“青简”名称与其官方 Logo 不属于 GPL 代码授权范围。本项目不使用青简官方 Logo；“青简”仅用于说明上游来源。
