# Keytrace —— 最像苹果的安卓中文输入法 + 键盘记录

基于 **同文输入法 Trime (RIME)** 完整开源项目二次封装，集成最佳开源词库与 iOS 皮肤，
并保留**本地键盘记录**能力（实时记录、本机存储、无任何联网权限）。

## 组成（全部来自他人成熟开源项目，未自研引擎）
- **IME 底座**：[tumuyan/trime-without-CMake](https://github.com/tumuyan/trime-without-CMake)（同文 Trime 3.2.7 免 NDK 分支，内置预编译 `librime_jni.so`，librime 1.7.3 + Lua 5.4.2）
- **中文引擎 / 词库（"最好"）**：[iDvel/rime-ice](https://github.com/iDvel/rime-ice)（雾凇拼音：`rime_ice` 全拼 + `cn_dicts` 大词库，含英文 / 拆字 / Emoji / 农历 / 计算器等 Lua 功能）
- **iOS 皮肤**：[eightpigs/trime](https://github.com/eightpigs/trime) 的 `ios.trime.yaml`，已设为**默认主题**
- **键盘记录**：`com.keyrecord.app` 无障碍服务（实时记录任意 App 输入，仅写本机私有存储，无 INTERNET 权限）

## 特性
- 像素级还原 iOS 键盘外观（圆角、键高、工具栏布局）
- 全拼 / 各类双拼 / 九宫格，云候选无关、纯本地
- 中文联想、长句、符号、Emoji、简繁切换
- 实时键盘记录：记录包名 + 输入文本到本机；提供「键盘记录」App 入口查看 / 导出 / 清空

## 构建
```bash
# 需要 JDK 17 + Android SDK (platform-31, build-tools 31.0.0)
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
./gradlew :app:assembleRelease
# 产物：app/build/outputs/apk/release/trime-2.0-release.apk
```
> 已内置预编译 `librime_jni.so`(arm64)，无需 NDK / CMake。

## 安装使用
1. 安装 `Keytrace-*.apk`（arm64 设备）。
2. 系统设置 → 语言与输入法 → 启用 **Keytrace** 并勾选。
3. 系统设置 → 无障碍 → 开启 **Keytrace** 记录服务（之后任意 App 打字都会被记录到本机）。
4. 桌面打开「键盘记录（仅本机）」查看 / 导出 / 清空记录。

## 隐私
- 应用声明中**不含 INTERNET 权限**，所有记录仅存于本机私有目录，不上传任何服务器。
- 仅供合法靶场 / 本人设备研究使用。

## 许可
本仓库聚合了多个上游项目的代码与数据，其各自许可证见 `LICENSE` 与上游仓库。
