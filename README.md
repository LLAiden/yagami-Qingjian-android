# Yagami_Qingjian 输入法
> 基于青简开源核心开发的Android 拼音输入法，支持 Android 8.0+ 与 HarmonyOS Android 兼容环境。
[原版正式 APK](https://github.com/Utyoin-OG/yagami-Qingjian-android/releases/latest)

本 fork 的 `feat/android-nine-key-ux` 分支提供 **0.2.1 九键预览版**，预览包名称为「Yagami 输入法预览」，
可与原版并存。已进行 Android 15 模拟器自测，厂商真机尚待验证；构建与验收步骤见
[Android 说明](apps/android/README.md) 和 [自测记录](docs/notes/android-ux-validation.md)。下面原版正式包的下载与覆盖升级说明不适用于预览包。

Yagami 是一个面向 Android 的拼音与语言学习输入法，基于Qingjian
[青简](https://github.com/qingjian-team/qingjian) 开源核心开发。
本项目是独立维护的非官方衍生版本，与 qingjian-team 不存在隶属、授权或官方发布关系。
在青简Qingjian基础上开发Android移动端，增加系列手机适配功能，提供安装手机直装apk。

## 新增功能

- 默认九键拼音、拼音消歧，可切换全拼；长按候选查看英语译词
- 点击候选或空格上屏，候选可展开为多列列表
- 中文 / 英文切换与临时大写，中英文选择会保存
- `123` 九宫格数字页与常用符号页
- 剪贴板历史、固定、删除和清空
- 全选 / 局部选区删除、完整 emoji 退格，长按连续删除
- 各页常驻收起键盘入口，手势导航与三键导航底部适配
- 多行输入框换行，单行输入框执行应用指定的发送、搜索或完成动作
- 长按空格切换至其他系统输入法
- `arm64-v8a` 与 `x86_64`，Android 8.0 及以上

关于鸿蒙：上游 0.1.5 曾在 HUAWEI Mate 40 Pro、HarmonyOS 4.2.0 的 Android 兼容环境中验证；本 fork 的 0.2.1 预览版尚未完成该设备验证。

## 下载、安装与使用

Yagami 输入法支持 **Android 8.0 及以上版本**。带有 Android 兼容环境的 HarmonyOS 也可以安装，
但 **HarmonyOS NEXT 不支持 Android APK**，因此无法使用。

### 第一步：下载 APK

1. 使用手机浏览器打开 [Yagami 输入法发布页面](https://github.com/Utyoin-OG/yagami-Qingjian-android/releases/latest)。
2. 找到页面中的 **Assets**（发布文件）区域。如果文件列表没有展开，点击 `Assets` 将其展开。
3. 点击名称类似下面这样的文件：

   ```text
   yagami-ime-android-0.1.5.apk
   ```

   以后发布新版本时，中间的版本号可能会变化，请优先下载版本号最新的 `.apk` 文件。

4. 等待浏览器完成下载。

> `.apk` 是可以在 Android 手机上安装的应用文件。
>
> `.sha256` 是供校验文件完整性使用的校验文件，不是安装包。
>
> `Source code (zip)` 和 `Source code (tar.gz)` 是源代码压缩包，也不能直接安装到手机。

请只从本仓库的 [GitHub Releases](https://github.com/Utyoin-OG/yagami-Qingjian-android/releases) 页面下载安装包，
不要从不明网盘、群聊文件或第三方下载站下载安装，以免安装到被修改过的版本。

### 第二步：允许安装 APK

1. 下载完成后，点击浏览器的下载通知，或者在手机的“文件管理”应用中找到刚刚下载的 APK。
2. 点击 APK 开始安装。
3. 如果系统提示“禁止安装未知应用”“不允许安装此来源的应用”或类似内容：
   - 点击提示中的“设置”；
   - 允许当前使用的浏览器或文件管理器“安装未知应用”；
   - 返回上一页，再次点击 APK 安装。
4. 如果系统提示这是来自应用商店以外的应用，请先确认下载地址确实属于
   `github.com/Utyoin-OG/yagami-Qingjian-android`，确认无误后再继续安装。
5. 出现“应用已安装”后，点击“打开”。

不同品牌手机的设置名称可能略有不同。如果没有自动出现设置入口，可以在系统设置中搜索
“安装未知应用”，然后为下载 APK 时使用的浏览器或文件管理器临时开启安装权限。
安装完成后可以关闭这项权限。

### 第三步：启用 Yagami 输入法

首次打开 Yagami 输入法后，按照页面上的提示操作：

1. 点击 **“启用输入法”**。
2. 系统会打开输入法管理页面，在列表中找到 **“Yagami 输入法”** 并打开开关。
3. Android 可能提示第三方输入法可以读取输入内容。这是启用任何第三方输入法时都会出现的系统提示。
   当前版本的拼音转换、候选和译词均在本机完成。
4. 返回 Yagami 输入法应用。
5. 点击 **“选择 Yagami 输入法”**。
6. 在系统弹出的输入法列表中选择 **“Yagami 输入法”**。

如果点击按钮后没有返回应用，可以手动重新打开桌面上的“Yagami 输入法”。

### 第四步：开始使用

1. 打开微信、浏览器、记事本或其他可以输入文字的应用。
2. 点击文本框，等待 Yagami 键盘出现。
3. 预览版默认九键，输入 `64426` 后候选应出现“你好”；切至全拼后输入 `nihao`。
4. 点击候选，或者按空格键，将第一候选输入到文本框。

常用按键：

- 点击顶栏 `九键` / `全拼`：切换拼音布局；点击 `中文` / `EN` 切换输入语言；
- 点击 `⇧`：临时输入一个大写英文字母；
- 点击 `123`：进入九宫格数字页；点击 `符号`：打开符号页；
- 点击 `拼音` / `ABC`：返回文字键盘；
- 点击顶栏 `剪贴板`：粘贴、固定或删除历史；
- 点击顶栏 `⌄`：收起键盘；
- 点击 `⌫`：删除选区，没有选区时删除一个拼音键或完整字符；
- 长按 `⌫`：连续删除字符，松手后停止；
- 短按空格：选择第一候选；没有候选时输入空格；
- **长按空格：打开系统输入法选择器，切换到其他输入法；**
- 点击换行键：在多行文本框中换行；在单行文本框中执行应用指定的发送、搜索或完成操作。

更完整的按键说明见 [Android 按键说明](docs/user/getting-started/keys.md#android)。

### 更新到新版本

发布新版本后，仍然从 [GitHub Releases](https://github.com/Utyoin-OG/yagami-Qingjian-android/releases/latest)
下载最新的 APK，然后直接点击安装即可覆盖升级。

升级时请注意：

- **不要先卸载旧版本**；
- 直接安装版本号更高的正式 APK；
- 正常覆盖安装不会要求重新启用输入法；
- 卸载旧版本可能会清除应用数据，并需要重新启用输入法；
- 如果系统提示“签名不一致”或“与现有应用冲突”，请确认新旧 APK 是否都来自本仓库，不要贸然卸载旧版。

### 常见问题

**下载后得到的是 ZIP 文件，无法安装怎么办？**

你下载的是源代码。请返回 Releases 页面，在 `Assets` 中下载文件名以 `.apk` 结尾的安装包。

**点击 APK 后提示“不允许安装未知应用”怎么办？**

按照系统提示，为当前浏览器或文件管理器开启“安装未知应用”权限，然后返回重新安装。
安装完成后可以关闭该权限。

**安装时提示“解析软件包时出现问题”怎么办？**

请确认手机系统为 Android 8.0 或更高版本，并重新下载 APK。下载不完整也可能导致这个提示。

**安装完成后没有出现 Yagami 键盘怎么办？**

重新打开“Yagami 输入法”，依次点击“启用输入法”和“选择 Yagami 输入法”。
也可以进入系统设置，搜索“输入法”或“键盘”，确认 Yagami 输入法已经启用并被选中。

**如何临时切换回手机原来的输入法？**

在 Yagami 键盘上长按空格，系统会显示已经启用的输入法列表，然后选择需要使用的输入法。

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
