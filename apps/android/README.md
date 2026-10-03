# Yagami Android 输入法

Yagami 使用原生 Android `InputMethodService`，通过 JNI 复用青简的 GPL 开源 Core。

## 构建

需要 JDK 17、Android SDK 35、NDK 27、Rust 与 `cargo-ndk`：

```bash
rustup target add aarch64-linux-android x86_64-linux-android
cargo ndk -t arm64-v8a -t x86_64 -o apps/android/app/src/main/jniLibs build -p yagami-android-native --release --locked
cd apps/android
./gradlew clean lintRelease assembleRelease
```

未配置签名环境变量时，`assembleRelease` 生成未签名 APK，适合 CI 验证但不能直接安装。

可安装的预览包：`./gradlew lintDebug assembleDebug`，产物为 `app/build/outputs/apk/debug/app-debug.apk`。
Debug 包使用独立包名 `io.github.utyoinog.yagamiime.preview`、名称「Yagami 输入法预览」，可以与原版并存。
正式包不包含调试输入验收页，也不使用系统生成的 debug 签名。

## 正式签名

正式发布前生成并离线备份自己的 keystore。不要把 keystore 或密码提交到 Git。
构建时设置以下环境变量：

```text
YAGAMI_KEYSTORE_FILE       keystore 的绝对路径
YAGAMI_KEYSTORE_PASSWORD   keystore 密码
YAGAMI_KEY_ALIAS           签名密钥别名
YAGAMI_KEY_PASSWORD        签名密钥密码
```

配置完成后再次运行 `./gradlew clean lintRelease assembleRelease`，输出位于
`app/build/outputs/apk/release/app-release.apk`。

## 安装与升级

应用包名是 `io.github.utyoinog.yagamiime`。Android 使用包名、签名证书和 `versionCode` 判断能否覆盖升级。
正式发布后必须永久保留同一签名证书，每次升级递增 `versionCode`；不要先卸载旧版，否则应用私有数据会被清除。

0.2.0 增加默认九键拼音、音节消歧、九宫格数字、剪贴板历史、选区删除和各页常驻的收起入口，保留全拼与英文。
适配手势导航、三键导航、屏幕缺口与横屏。剪贴板最多 30 条，未固定条目 24 小时过期；密码框和敏感标记内容不自动存历史。
滑动输入、语音和用户词频持久化尚未提供。用户按键说明见 `docs/user/getting-started/keys.md`。

0.2.1 增加展开候选、中英文选择记忆、剪贴板去重，以及符号 / 剪贴板返回数字页的状态恢复。
版本号为 0.2.1 / versionCode 8，可以覆盖升级同签名的 0.2.0 预览包。

0.2.2 完成十轮自查，增加编辑工具、三档高度和深浅主题；修复长按滑出误删、展开候选长按中断、复制时间续期、私密历史重建、输入框动作和重启丢候选。
版本号为 0.2.2 / versionCode 9，可以覆盖升级同签名的 0.2.1 预览包。

0.2.3 更新键盘与启用向导的深浅配色、功能图标和触摸反馈；候选下方直接显示英语释义，长按查看释义提示。
拼音选项独立分隔，九宫格左右对称且数字居中，全拼删除键加宽。动作键统一在最右下角。
三组鼻音可独立开启，九键和全拼均生效；设置与剪贴板首次使用自动迁移到 MMKV 2.4.2。
修复按键间隙、输入框重启时漏数字及分段上屏后候选丢失；候选按需构建并复用未变化视图。
版本号为 0.2.3 / versionCode 10，可覆盖升级同签名的 0.2.2 预览包。
0.2.4 按用户要求将页面调整为候选区、剪贴板栏、编辑菜单、输入区；候选空闲时隐藏，菜单图标靠右。
仅保留拼音九键、英语全键盘、数字九键；所有模式使用英文半角标点，符号仍为临时面板。
版本号为 0.2.4 / versionCode 11，可覆盖升级同签名的 0.2.0–0.2.3 预览包。
本轮验收见 `docs/notes/android-layout-validation.md`；已确认需求见 `docs/plan/android-product-optimization.md`。

0.2.5 根据补充要求，合并为同一靠右图标工具栏，图标按全选、编辑、验证器、密码管理器、剪贴板、收起排列，页面顺序为候选区、工具栏、输入区。
拼音底行中间为 `123 | 空格 | EN`，各占一个键位。
英语空格居中，Shift 单次大写 / 两次锁定 / 再次解除；同栏新增 Bitwarden 与 Google 验证器入口，图标在圆形底中心。
版本号为 0.2.5 / versionCode 12，可覆盖升级同签名的 0.2.0–0.2.4 预览包。

0.2.6 将英语底行数字与中文切换键分别紧邻空格左侧和右侧，保留居中空格与英文标点。
数字底行中央为 `EN | 0 | 拼音`，空格位于左侧功能列；圆底图标缩为 18dp；密码管理器优先打开 Bitwarden 保险库。
版本号为 0.2.6 / versionCode 13，可覆盖升级同签名的 0.2.0–0.2.5 预览包。

## 自测

安装已启动的 API 30+ 测试设备；验收会切换测试设备的导航模式和当前输入法。

```bash
cargo test -p qingjian-core -p yagami-android-native --locked
cargo clippy -p yagami-android-native -p qingjian-cli --all-targets --locked -- -D warnings
cd apps/android
./gradlew lintDebug lintRelease assembleDebug assembleDebugAndroidTest assembleRelease
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w io.github.utyoinog.yagamiime.preview.test/android.test.InstrumentationTestRunner
```

交互验收在真实系统 IME 窗口注入触摸事件，覆盖九键候选与消歧、数字布局、英文全键盘、选区与 emoji 删除、长按删除、
剪贴板去重与删除、密码框、展开候选、中英文记忆、数字页恢复、各面板及系统收起、快速输入、回车动作、导航模式及横屏。测试环境和结果见
`docs/notes/android-layout-validation.md`（0.2.4–0.2.5）、`docs/notes/android-visual-validation.md`（0.2.3）、`docs/notes/android-ten-round-audit.md`（0.2.2）与 `docs/notes/android-ux-validation.md`（0.2.1）；模拟器验证与厂商真机验证分别记录。

## 许可证与来源

代码采用 GPL-3.0-or-later。上游来源、修改范围和数据许可见仓库根目录的 `NOTICE.md` 与 `README.md`。
