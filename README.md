# 靶场键盘记录器（授权测试用）

> ⚠️ **法律与伦理声明（必读）**
> 本项目为**教育 / 授权安全测试**用途，仅限在**你本人设备**或**已获书面授权的封闭靶场**内使用。
> 严禁在**未经所有者明确同意**的任何设备上安装、运行或分发。
> 未经同意记录他人键盘输入，可能触犯《中华人民共和国刑法》第 285 条（非法获取计算机信息系统数据罪）等规定，后果由使用者自行承担。

## 合规设计要点

- **无网络权限**：`AndroidManifest.xml` 不声明 `INTERNET` / `ACCESS_NETWORK_STATE` 等任何联网权限，所有记录**仅保存在本机存储**，无法被远程窃取。
- **显式授权**：App 首屏强制展示《合法使用声明》，必须勾选确认后才能前往开启服务，杜绝"隐形"记录。
- **手动开启**：基于 Android `AccessibilityService`，需用户在系统"无障碍"设置中**手动开启**，无需 root。
- **密码保护**：标记为密码框的 `EditText` 在 Android 14+ 受系统保护，文本内容不会被暴露——这是预期的安全行为。

## 技术参数

| 项 | 值 |
|----|----|
| 包名 | `com.example.keylogger` |
| compileSdk / targetSdk | 36 |
| minSdk | 24（Android 7.0+） |
| Gradle | 8.14.6（wrapper 已锁定，镜像 `mirrors.cloud.tencent.com`） |
| Android SDK | `platforms;android-36` + `build-tools;36.0.0` + `platform-tools` |
| 实现方式 | `AccessibilityService` 监听各应用文本输入 |

## 目录结构

```
KeyLoggerApp/
├── app/
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/example/keylogger/
│       │   ├── MainActivity.java        # 首屏：合法声明 + 授权勾选 + 日志管理
│       │   ├── KeyLoggerService.java     # 无障碍键盘记录服务
│       │   └── LogManager.java           # 本地日志读写
│       └── res/                          # 布局 / 字符串 / 主题 / accessibility 配置
├── gradle/wrapper/                       # Gradle 8.14.6 wrapper
├── build.gradle / settings.gradle        # 工程与依赖（含腾讯镜像）
├── gradlew / gradlew.bat                 # 一键构建脚本
└── local.properties                      # 本机 SDK 路径（不纳入版本控制）
```

## 本机构建

```bash
# 前置：JDK 17+、Android SDK（android-36 / build-tools;36.0.0）
export ANDROID_HOME=/path/to/android-sdk

# 一键构建 release APK
./gradlew :app:assembleRelease

# 产物位于：
# app/build/outputs/apk/release/app-release.apk
```

> 若无法访问官方 Gradle 源，可修改 `gradle/wrapper/gradle-wrapper.properties` 的
> `distributionUrl` 为腾讯镜像：`https://mirrors.cloud.tencent.com/gradle/gradle-8.14.6-bin.zip`

## 手机使用步骤

1. 安装 `app-release.apk`（允许"未知来源"安装）。
2. 打开 App → 阅读并**勾选**《合法使用声明》 → 点"前往开启键盘记录服务"。
3. 在系统"无障碍"设置中手动开启本服务。
4. 回 App，可"刷新查看本地日志" / "导出日志" / "清空日志"。
   - 导出目录：`Android/data/com.example.keylogger/files/export/`，可用 `adb pull` 取出。

## 隐私与数据

- 所有记录文件保存在 App 私有目录，不会上传任何服务器。
- 卸载 App 即删除全部本地日志。
