# Keytrace v2.1 —— 安卓中文输入法（Trime/RIME）+ 本地键盘记录

基于同文输入法 **Trime (RIME)** 完整开源项目封装，集成中文拼音输入与 **iOS 风格皮肤**，并内置**本地键盘记录**能力（实时记录、本机存储、无联网）。

## 组成（全部来自成熟开源项目）
- **IME 底座**：tumuyan/trime-without-CMake（同文 Trime 3.2.7 免 NDK 分支，内置预编译 librime_jni.so，librime 1.7.3 + Lua 5.4.2）
- **中文引擎**：标准 RIME 朙月拼音 `luna_pinyin`（默认，必定可用）+ `iDvel/rime-ice` 雾凇拼音（最佳词库，可在输入法设置里切换）
- **iOS 风格皮肤**：eightpigs/trime 的 ios.trime.yaml（在输入法设置→主题里切换为「iOS」）
- **键盘记录**：com.keyrecord.app 无障碍服务（实时记录任意 App 输入，仅写本机）

## 桌面两个入口（一个安装包，两个图标）
1. **Keytrace**（输入法）：打开后可启用输入法、触发 RIME 部署、切换主题与方案。
2. **键盘记录（仅本机）**：查看「打了什么字 + 涉及了几个 App（及具体 App 与次数）」，可导出/清空。

## 构建
```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
./gradlew :app:assembleRelease
# 产物：app/build/outputs/apk/release/trime-2.1-release.apk
```
> 已内置预编译 librime_jni.so(arm64)，无需 NDK / CMake。

## 使用
1. 安装 APK（arm64 设备）。
2. 打开 **Keytrace** 图标 → 在系统设置里启用本输入法并设为默认。
3. 系统设置 → 无障碍 → 开启 **Keytrace** 记录服务（之后任意 App 打字都会被记录到本机）。
4. 打开 **键盘记录（仅本机）** 查看打字内容与涉及 App。
5. 输入法设置里可切换主题（iOS 风格）与输入方案（朙月拼音 / 雾凇拼音等）。

## 隐私
- 应用声明中**不含 INTERNET 权限**，记录仅存本机私有目录，不上传任何服务器。
- 仅供合法靶场 / 本人设备研究使用。
